#!/system/bin/sh

EXPECTED_LIBRARY_HASH="@EXPECTED_LIBRARY_HASH@"
CONFIG="/vendor/etc/audio_effects.xml"
VENDOR_SOUNDFX="/vendor/lib/soundfx"
PAYLOAD_CONFIG="$MODPATH/payload/audio_effects.xml"
PAYLOAD_LIBRARY="$MODPATH/payload/libaudiofreedomfx_legacy.so"
STAGED_SOUNDFX="$MODPATH/direct-bind/soundfx"
IMPL_UUID="2f6e8c10-8d44-4b42-b110-16f3a729ef01"

fail() {
    abort "! AudioFreedom: $1"
}

ui_print "- AudioFreedom portable 32-bit legacy/HIDL profile"

. "$MODPATH/compatibility.sh" || fail "compatibility checker could not be loaded"
audiofreedom_check_legacy32 || fail "$AUDIOFREEDOM_COMPATIBILITY_ERROR"
ui_print "- Factory: $AUDIOFREEDOM_FACTORY_INTERFACE (PID $AUDIOFREEDOM_FACTORY_PID)"

[ -s "$PAYLOAD_LIBRARY" ] || fail "legacy effect library is missing"
[ -s "$PAYLOAD_CONFIG" ] || fail "patched effects configuration is missing"
library_hash=$(sha256sum "$PAYLOAD_LIBRARY" 2>/dev/null | awk '{print $1}')
[ "$library_hash" = "$EXPECTED_LIBRARY_HASH" ] || fail "effect library hash mismatch"
grep -q "$IMPL_UUID" "$PAYLOAD_CONFIG" || fail "patched effect descriptor is invalid"

mkdir -p "$STAGED_SOUNDFX" || fail "could not create the soundfx staging directory"
cp -af "$VENDOR_SOUNDFX/." "$STAGED_SOUNDFX/" ||
    fail "could not preserve the OEM soundfx libraries"
cp -f "$PAYLOAD_LIBRARY" "$STAGED_SOUNDFX/libaudiofreedomfx_legacy.so" ||
    fail "could not stage the AudioFreedom library"

for source in "$VENDOR_SOUNDFX"/*; do
    [ -e "$source" ] || continue
    name=${source##*/}
    [ -e "$STAGED_SOUNDFX/$name" ] || fail "OEM soundfx staging is incomplete: $name"
done

set_perm_recursive "$MODPATH/payload" 0 0 0755 0644 u:object_r:vendor_file:s0
set_perm "$PAYLOAD_CONFIG" 0 0 0644 u:object_r:vendor_configs_file:s0
set_perm_recursive "$MODPATH/direct-bind" 0 0 0755 0644 u:object_r:vendor_file:s0
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/compatibility.sh" 0 0 0755
set_perm "$MODPATH/direct-bind.sh" 0 0 0755
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755

staged_count=$(find "$STAGED_SOUNDFX" -maxdepth 1 -type f 2>/dev/null | wc -l)
ui_print "- Preserved $staged_count OEM 32-bit soundfx libraries"
ui_print "- Matched a compatible 32-bit HIDL stack and signed XML configuration"
ui_print "- Reboot is required; disabling AudioFreedom and rebooting removes both mounts"
