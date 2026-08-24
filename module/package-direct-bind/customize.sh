#!/system/bin/sh

EXPECTED_CONFIG_HASH="fe7c66c4a94dbb4555a4bee819b44505b8e6b871c77535b50a36af7d31d87f5c"
PATCHED_CONFIG_HASH="66e0a7c1700f3a3aff163049c25c46431faf9b84659bf76ef6f147d61e85af89"
EXPECTED_LEGACY_CONFIG_HASH="1a8c6e2b33abc9237bec744b2c5142a7007ef91ed6dba4a25435d0e3cd5ffab4"
PATCHED_LEGACY_CONFIG_HASH="15367daf7122559fede8e1b8e82832eb6312cf6755c5a8cfd8dafcd55f97f754"
CONFIG="/vendor/etc/audio/sku_sun/audio_effects_config.xml"
LEGACY_CONFIG="/vendor/etc/audio/sku_sun/audio_effects.xml"
VENDOR_SOUNDFX="/vendor/lib64/soundfx"
PAYLOAD_CONFIG="$MODPATH/payload/audio_effects_config.xml"
PAYLOAD_LEGACY_CONFIG="$MODPATH/payload/audio_effects.xml"
PAYLOAD_LIBRARY="$MODPATH/payload/libaudiofreedomfx.so"
STAGED_SOUNDFX="$MODPATH/direct-bind/soundfx"
IMPL_UUID="2f6e8c10-8d44-4b42-b110-16f3a729ef01"

fail() {
    abort "! AudioFreedom: $1"
}

ui_print "- AudioFreedom Android 16 10-band EQ (direct bind)"

[ "$(getprop ro.product.manufacturer)" = "Xiaomi" ] || fail "unsupported manufacturer"
[ "$(getprop ro.product.name)" = "xuanyuan_eea" ] || fail "unsupported product"
[ "$(getprop ro.product.device)" = "xuanyuan" ] || fail "unsupported device"
[ "$(getprop ro.build.version.sdk)" = "36" ] || fail "this proof requires Android API 36"
[ "$(getprop ro.product.cpu.abi)" = "arm64-v8a" ] || fail "this proof requires arm64-v8a"
[ -d "$VENDOR_SOUNDFX" ] || fail "vendor soundfx directory is missing"
[ -r "$CONFIG" ] || fail "active effects configuration is missing"
[ -r "$LEGACY_CONFIG" ] || fail "active music-chain configuration is missing"
command -v nsenter >/dev/null 2>&1 || fail "Android nsenter is unavailable"
command -v mount >/dev/null 2>&1 || fail "Android mount is unavailable"

current_hash=$(sha256sum "$CONFIG" 2>/dev/null | awk '{print $1}')
case "$current_hash" in
    "$EXPECTED_CONFIG_HASH"|"$PATCHED_CONFIG_HASH") ;;
    *) fail "vendor effects configuration changed; refusing unsafe bind mounts" ;;
esac

legacy_hash=$(sha256sum "$LEGACY_CONFIG" 2>/dev/null | awk '{print $1}')
case "$legacy_hash" in
    "$EXPECTED_LEGACY_CONFIG_HASH"|"$PATCHED_LEGACY_CONFIG_HASH") ;;
    *) fail "vendor music-chain configuration changed; refusing unsafe bind mounts" ;;
esac

service list 2>/dev/null | grep -q "android.hardware.audio.effect.IFactory/default" ||
    fail "AIDL Effects Factory service was not found"

[ -s "$PAYLOAD_LIBRARY" ] || fail "effect library is missing from the package"
[ -s "$PAYLOAD_CONFIG" ] || fail "patched effects configuration is missing"
[ -s "$PAYLOAD_LEGACY_CONFIG" ] || fail "patched music-chain configuration is missing"
grep -q "$IMPL_UUID" "$PAYLOAD_CONFIG" || fail "patched effect descriptor is invalid"
grep -q "$IMPL_UUID" "$PAYLOAD_LEGACY_CONFIG" || fail "patched music chain is invalid"

mkdir -p "$STAGED_SOUNDFX" || fail "could not create the direct-bind staging directory"
cp -af "$VENDOR_SOUNDFX/." "$STAGED_SOUNDFX/" ||
    fail "could not preserve the OEM soundfx libraries"
cp -f "$PAYLOAD_LIBRARY" "$STAGED_SOUNDFX/libaudiofreedomfx.so" ||
    fail "could not stage the AudioFreedom library"

for source in "$VENDOR_SOUNDFX"/*; do
    [ -e "$source" ] || continue
    name=${source##*/}
    [ -e "$STAGED_SOUNDFX/$name" ] || fail "OEM soundfx staging is incomplete: $name"
done

set_perm_recursive "$MODPATH/payload" 0 0 0755 0644 u:object_r:vendor_file:s0
set_perm "$PAYLOAD_CONFIG" 0 0 0644 u:object_r:vendor_configs_file:s0
set_perm "$PAYLOAD_LEGACY_CONFIG" 0 0 0644 u:object_r:vendor_configs_file:s0
set_perm_recursive "$MODPATH/direct-bind" 0 0 0755 0644 u:object_r:vendor_file:s0
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/direct-bind.sh" 0 0 0755
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755

staged_count=$(find "$STAGED_SOUNDFX" -maxdepth 1 -type f 2>/dev/null | wc -l)
ui_print "- Preserved $staged_count soundfx libraries in the guarded staging directory"
if [ -L /data/adb/metamodule ]; then
    ui_print "- Disable or uninstall Meta-OverlayFS before the test reboot"
fi
ui_print "- Reboot is required; disabling AudioFreedom and rebooting rolls back all mounts"
