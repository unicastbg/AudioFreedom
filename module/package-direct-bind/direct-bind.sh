#!/system/bin/sh

MODDIR=${0%/*}
STATE_DIR="$MODDIR/state"
LOG_FILE="$STATE_DIR/direct-bind.log"
PAYLOAD_CONFIG="$MODDIR/payload/audio_effects_config.xml"
PAYLOAD_LEGACY_CONFIG="$MODDIR/payload/audio_effects.xml"
STAGED_SOUNDFX="$MODDIR/direct-bind/soundfx"
TARGET_SOUNDFX="/vendor/lib64/soundfx"
TARGET_CONFIG="/vendor/etc/audio/sku_sun/audio_effects_config.xml"
TARGET_LEGACY_CONFIG="/vendor/etc/audio/sku_sun/audio_effects.xml"
IMPL_UUID="2f6e8c10-8d44-4b42-b110-16f3a729ef01"
EXPECTED_LIBRARY_HASH="e27b334f81c1dd8405894f5852757eaaedb65690d98697222b45140cd598b30d"

mkdir -p "$STATE_DIR"

log() {
    echo "$1" >>"$LOG_FILE"
}

in_init_mount_ns() {
    nsenter -t 1 -m -- "$@"
}

is_exact_mountpoint() {
    awk -v target="$1" '$5 == target { found = 1 } END { exit !found }' /proc/1/mountinfo
}

unmount_partial() {
    [ "$mounted_legacy" = "1" ] && in_init_mount_ns umount "$TARGET_LEGACY_CONFIG"
    [ "$mounted_config" = "1" ] && in_init_mount_ns umount "$TARGET_CONFIG"
    [ "$mounted_soundfx" = "1" ] && in_init_mount_ns umount "$TARGET_SOUNDFX"
}

mount_all() {
    : >"$LOG_FILE"
    log "mode=direct-bind"

    [ -d "$STAGED_SOUNDFX" ] || { log "error=staged-soundfx-missing"; return 1; }
    [ -s "$STAGED_SOUNDFX/libaudiofreedomfx.so" ] || { log "error=library-missing"; return 1; }
    [ -s "$PAYLOAD_CONFIG" ] || { log "error=config-missing"; return 1; }
    [ -s "$PAYLOAD_LEGACY_CONFIG" ] || { log "error=music-chain-missing"; return 1; }

    library_hash=$(sha256sum "$STAGED_SOUNDFX/libaudiofreedomfx.so" 2>/dev/null | awk '{print $1}')
    [ "$library_hash" = "$EXPECTED_LIBRARY_HASH" ] || { log "error=library-hash-mismatch"; return 1; }
    grep -q "$IMPL_UUID" "$PAYLOAD_CONFIG" || { log "error=config-uuid-missing"; return 1; }
    grep -q "$IMPL_UUID" "$PAYLOAD_LEGACY_CONFIG" || { log "error=music-chain-uuid-missing"; return 1; }

    if is_exact_mountpoint "$TARGET_SOUNDFX" ||
       is_exact_mountpoint "$TARGET_CONFIG" ||
       is_exact_mountpoint "$TARGET_LEGACY_CONFIG"; then
        log "error=target-already-mounted"
        return 1
    fi

    mounted_soundfx=0
    mounted_config=0
    mounted_legacy=0

    if ! in_init_mount_ns mount -o bind "$STAGED_SOUNDFX" "$TARGET_SOUNDFX"; then
        log "error=soundfx-bind-failed"
        return 1
    fi
    mounted_soundfx=1

    if ! in_init_mount_ns mount -o bind "$PAYLOAD_CONFIG" "$TARGET_CONFIG"; then
        log "error=config-bind-failed"
        unmount_partial
        return 1
    fi
    mounted_config=1

    if ! in_init_mount_ns mount -o bind "$PAYLOAD_LEGACY_CONFIG" "$TARGET_LEGACY_CONFIG"; then
        log "error=music-chain-bind-failed"
        unmount_partial
        return 1
    fi
    mounted_legacy=1

    if [ ! -r "$TARGET_SOUNDFX/libaudiofreedomfx.so" ] ||
       ! grep -q "$IMPL_UUID" "$TARGET_CONFIG" 2>/dev/null ||
       ! grep -q "$IMPL_UUID" "$TARGET_LEGACY_CONFIG" 2>/dev/null; then
        log "error=post-mount-verification-failed"
        unmount_partial
        return 1
    fi

    log "result=active"
    return 0
}

status() {
    exact=0
    is_exact_mountpoint "$TARGET_SOUNDFX" && exact=$((exact + 1))
    is_exact_mountpoint "$TARGET_CONFIG" && exact=$((exact + 1))
    is_exact_mountpoint "$TARGET_LEGACY_CONFIG" && exact=$((exact + 1))
    echo "direct_bind_mounts=$exact/3"
    if [ -r "$TARGET_SOUNDFX/libaudiofreedomfx.so" ] &&
       grep -q "$IMPL_UUID" "$TARGET_CONFIG" 2>/dev/null &&
       grep -q "$IMPL_UUID" "$TARGET_LEGACY_CONFIG" 2>/dev/null; then
        echo "direct_bind=active"
    else
        echo "direct_bind=inactive"
    fi
}

case "$1" in
    mount) mount_all ;;
    status) status ;;
    *) echo "usage: $0 {mount|status}"; exit 2 ;;
esac
