#!/system/bin/sh

MODDIR=${0%/*}
STATE_DIR="$MODDIR/state"
LOG_FILE="$STATE_DIR/direct-bind.log"
PLAN="$STATE_DIR/mount-plan.txt"
PROFILE="$STATE_DIR/profile.env"
STAGED_SOUNDFX="$MODDIR/direct-bind/soundfx"
IMPL_UUID="2f6e8c10-8d44-4b42-b110-16f3a729ef01"

[ -r "$PROFILE" ] && . "$PROFILE"

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

unmount_all() {
    if [ -r "$PLAN" ]; then
        while IFS='|' read -r target relative; do
            [ -n "$target" ] || continue
            is_exact_mountpoint "$target" && in_init_mount_ns umount "$target" >/dev/null 2>&1
        done <"$PLAN"
    fi
    [ -n "$SOUNDFX_TARGET" ] && is_exact_mountpoint "$SOUNDFX_TARGET" &&
        in_init_mount_ns umount "$SOUNDFX_TARGET" >/dev/null 2>&1
}

mount_all() {
    : >"$LOG_FILE"
    log "mode=universal-direct-bind"
    [ -r "$PROFILE" ] || { log "error=profile-missing"; return 1; }
    [ -r "$PLAN" ] || { log "error=mount-plan-missing"; return 1; }
    [ -d "$STAGED_SOUNDFX" ] || { log "error=staged-soundfx-missing"; return 1; }
    [ -s "$STAGED_SOUNDFX/$LIBRARY_NAME" ] || { log "error=library-missing"; return 1; }
    library_hash=$(sha256sum "$STAGED_SOUNDFX/$LIBRARY_NAME" 2>/dev/null | awk '{print $1}')
    [ "$library_hash" = "$EXPECTED_LIBRARY_HASH" ] || {
        log "error=library-hash-mismatch"
        return 1
    }

    if is_exact_mountpoint "$SOUNDFX_TARGET"; then
        log "error=soundfx-target-already-mounted"
        return 1
    fi
    while IFS='|' read -r target relative; do
        [ -n "$target" ] || continue
        if is_exact_mountpoint "$target"; then
            log "error=config-target-already-mounted:$target"
            return 1
        fi
        payload="$MODDIR/$relative"
        [ -s "$payload" ] || { log "error=config-payload-missing:$relative"; return 1; }
        grep -q "$IMPL_UUID" "$payload" || {
            log "error=config-uuid-missing:$relative"
            return 1
        }
    done <"$PLAN"

    if ! in_init_mount_ns mount -o bind "$STAGED_SOUNDFX" "$SOUNDFX_TARGET"; then
        log "error=soundfx-bind-failed"
        return 1
    fi

    mounted_count=0
    while IFS='|' read -r target relative; do
        [ -n "$target" ] || continue
        payload="$MODDIR/$relative"
        if ! in_init_mount_ns mount -o bind "$payload" "$target"; then
            log "error=config-bind-failed:$target"
            unmount_all
            return 1
        fi
        mounted_count=$((mounted_count + 1))
    done <"$PLAN"

    [ "$mounted_count" -eq "$CONFIG_COUNT" ] || {
        log "error=config-mount-count:$mounted_count/$CONFIG_COUNT"
        unmount_all
        return 1
    }
    [ -r "$SOUNDFX_TARGET/$LIBRARY_NAME" ] || {
        log "error=mounted-library-unavailable"
        unmount_all
        return 1
    }
    while IFS='|' read -r target relative; do
        [ -n "$target" ] || continue
        grep -q "$IMPL_UUID" "$target" 2>/dev/null || {
            log "error=mounted-config-verification:$target"
            unmount_all
            return 1
        }
    done <"$PLAN"

    log "backend=$BACKEND"
    log "config_mounts=$mounted_count"
    log "result=active"
    return 0
}

status() {
    [ -r "$PROFILE" ] || { echo "profile=missing"; return 1; }
    expected=$((CONFIG_COUNT + 1))
    mounted=0
    is_exact_mountpoint "$SOUNDFX_TARGET" && mounted=$((mounted + 1))
    while IFS='|' read -r target relative; do
        [ -n "$target" ] || continue
        is_exact_mountpoint "$target" && mounted=$((mounted + 1))
    done <"$PLAN"
    echo "backend=$BACKEND"
    echo "factory_service=$FACTORY_INTERFACE"
    echo "direct_bind_mounts=$mounted/$expected"
    if [ "$mounted" -eq "$expected" ] && [ -r "$SOUNDFX_TARGET/$LIBRARY_NAME" ]; then
        echo "direct_bind=active"
    else
        echo "direct_bind=inactive"
    fi
}

case "$1" in
    mount) mount_all ;;
    status) status ;;
    unmount) unmount_all ;;
    *) echo "usage: $0 {mount|status|unmount}"; exit 2 ;;
esac
