#!/system/bin/sh

EXPECTED_CONFIG_HASH="fe7c66c4a94dbb4555a4bee819b44505b8e6b871c77535b50a36af7d31d87f5c"
PREVIOUS_CONFIG_HASH_1="00263eddfc24145a3ff1ebae928a6b5290ddaeb9337b6e522cd9e2fb062cc8fe"
PREVIOUS_CONFIG_HASH_2="66e0a7c1700f3a3aff163049c25c46431faf9b84659bf76ef6f147d61e85af89"
EXPECTED_LEGACY_CONFIG_HASH="1a8c6e2b33abc9237bec744b2c5142a7007ef91ed6dba4a25435d0e3cd5ffab4"
PREVIOUS_LEGACY_CONFIG_HASH="15367daf7122559fede8e1b8e82832eb6312cf6755c5a8cfd8dafcd55f97f754"
CONFIG="/vendor/etc/audio/sku_sun/audio_effects_config.xml"
LEGACY_CONFIG="/vendor/etc/audio/sku_sun/audio_effects.xml"
PAYLOAD_CONFIG="$MODPATH/system/vendor/etc/audio/sku_sun/audio_effects_config.xml"
PAYLOAD_LEGACY_CONFIG="$MODPATH/system/vendor/etc/audio/sku_sun/audio_effects.xml"
PAYLOAD_LIBRARY="$MODPATH/system/vendor/lib64/soundfx/libaudiofreedomfx.so"
IMPL_UUID="2f6e8c10-8d44-4b42-b110-16f3a729ef01"

fail() {
    abort "! AudioFreedom: $1"
}

ui_print "- AudioFreedom Android 16 AIDL V3 shared-control proof"

[ "$(getprop ro.product.manufacturer)" = "Xiaomi" ] || fail "unsupported manufacturer"
[ "$(getprop ro.product.name)" = "xuanyuan_eea" ] || fail "unsupported product"
[ "$(getprop ro.product.device)" = "xuanyuan" ] || fail "unsupported device"
[ "$(getprop ro.build.version.sdk)" = "36" ] || fail "this proof requires Android API 36"
[ "$(getprop ro.product.cpu.abi)" = "arm64-v8a" ] || fail "this proof requires arm64-v8a"
[ -r "$CONFIG" ] || fail "active effects configuration is missing"
[ -r "$LEGACY_CONFIG" ] || fail "active music-chain configuration is missing"

current_hash=$(sha256sum "$CONFIG" 2>/dev/null | awk '{print $1}')
case "$current_hash" in
    "$EXPECTED_CONFIG_HASH"|"$PREVIOUS_CONFIG_HASH_1"|"$PREVIOUS_CONFIG_HASH_2") ;;
    *) fail "vendor effects configuration changed; refusing an unsafe overlay" ;;
esac

legacy_hash=$(sha256sum "$LEGACY_CONFIG" 2>/dev/null | awk '{print $1}')
case "$legacy_hash" in
    "$EXPECTED_LEGACY_CONFIG_HASH"|"$PREVIOUS_LEGACY_CONFIG_HASH") ;;
    *) fail "vendor music-chain configuration changed; refusing an unsafe overlay" ;;
esac

service list 2>/dev/null | grep -q "android.hardware.audio.effect.IFactory/default" ||
    fail "AIDL Effects Factory service was not found"

[ -s "$PAYLOAD_LIBRARY" ] || fail "effect library is missing from the package"
[ -s "$PAYLOAD_CONFIG" ] || fail "patched effects configuration is missing from the package"
[ -s "$PAYLOAD_LEGACY_CONFIG" ] || fail "patched music-chain configuration is missing"
grep -q "$IMPL_UUID" "$PAYLOAD_CONFIG" || fail "patched effect descriptor is invalid"
grep -q "$IMPL_UUID" "$PAYLOAD_LEGACY_CONFIG" || fail "patched music chain is invalid"

if [ -n "${KSU:-}" ]; then
    [ -L /data/adb/metamodule ] || fail "KernelSU requires an active mounting metamodule"
    metamodule_prop="/data/adb/metamodule/module.prop"
    grep -Eq '^metamodule=(1|true)$' "$metamodule_prop" 2>/dev/null ||
        fail "KernelSU metamodule registration is invalid"
fi

set_perm_recursive "$MODPATH/system" 0 0 0755 0644
set_perm_recursive "$MODPATH/system/vendor" 0 0 0755 0644 u:object_r:vendor_file:s0
set_perm_recursive "$MODPATH/system/vendor/etc/audio/sku_sun" 0 0 0755 0644 \
    u:object_r:vendor_configs_file:s0
set_perm "$PAYLOAD_LIBRARY" 0 0 0644 u:object_r:vendor_file:s0
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
if [ -f "$MODPATH/system/bin/audiofreedom-controller" ]; then
    set_perm "$MODPATH/system/bin/audiofreedom-controller" 0 0 0755
fi
if [ -d "$MODPATH/runtime" ]; then
    set_perm_recursive "$MODPATH/runtime" 0 0 0755 0755
fi

ui_print "- Device and vendor configuration verified"
ui_print "- Reboot is required; disable the module to roll back"
