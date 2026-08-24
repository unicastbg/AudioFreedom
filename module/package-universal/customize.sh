#!/system/bin/sh

PATCHER_HASH="@PATCHER_HASH@"
AIDL64_LIBRARY_HASH="@AIDL64_LIBRARY_HASH@"
AIDL64_PROBE_HASH="@AIDL64_PROBE_HASH@"
LEGACY64_LIBRARY_HASH="@LEGACY64_LIBRARY_HASH@"
LEGACY64_PROBE_HASH="@LEGACY64_PROBE_HASH@"
LEGACY32_LIBRARY_HASH="@LEGACY32_LIBRARY_HASH@"
LEGACY32_PROBE_HASH="@LEGACY32_PROBE_HASH@"
PATCHER="$MODPATH/payload/tools/audiofreedom-config-patcher.jar"
STATE_DIR="$MODPATH/state"
CONFIG_DIR="$MODPATH/payload/configs"
STAGED_SOUNDFX="$MODPATH/direct-bind/soundfx"
PLAN="$STATE_DIR/mount-plan.txt"
PROFILE="$STATE_DIR/profile.env"
PATCH_LOG="$STATE_DIR/config-patcher.log"
IMPL_UUID="2f6e8c10-8d44-4b42-b110-16f3a729ef01"

fail() {
    abort "! AudioFreedom: $1"
}

file_hash() {
    sha256sum "$1" 2>/dev/null | awk '{print $1}'
}

choose_hidl_backend() {
    factory_line=$(lshal 2>/dev/null |
        grep -m 1 'android.hardware.audio.effect@[0-9][.][0-9]::IEffectsFactory/default')
    [ -n "$factory_line" ] || return 1
    factory_pid=$(echo "$factory_line" | awk '{
        for (field = NF; field >= 1; field--) {
            if ($field ~ /^[0-9]+$/) { print $field; exit }
        }
    }')
    [ -r "/proc/$factory_pid/maps" ] || fail "HIDL factory process map is unavailable"
    if grep -q ' /vendor/lib64/' "/proc/$factory_pid/maps"; then
        BACKEND="legacy64"
        PATCH_MODE="legacy"
        LIBRARY_NAME="libaudiofreedomfx_legacy.so"
        LIBRARY_SOURCE="$MODPATH/payload/backends/legacy64/$LIBRARY_NAME"
        LIBRARY_HASH="$LEGACY64_LIBRARY_HASH"
        PROBE_SOURCE="$MODPATH/payload/backends/legacy64/audiofreedom_legacy_probe"
        PROBE_HASH="$LEGACY64_PROBE_HASH"
        SOUNDFX_TARGET="/vendor/lib64/soundfx"
    elif grep -q ' /vendor/lib/' "/proc/$factory_pid/maps"; then
        BACKEND="legacy32"
        PATCH_MODE="legacy"
        LIBRARY_NAME="libaudiofreedomfx_legacy.so"
        LIBRARY_SOURCE="$MODPATH/payload/backends/legacy32/$LIBRARY_NAME"
        LIBRARY_HASH="$LEGACY32_LIBRARY_HASH"
        PROBE_SOURCE="$MODPATH/payload/backends/legacy32/audiofreedom_legacy_probe"
        PROBE_HASH="$LEGACY32_PROBE_HASH"
        SOUNDFX_TARGET="/vendor/lib/soundfx"
    else
        fail "could not determine the HIDL Effects Factory process ABI"
    fi
    FACTORY_INTERFACE=$(echo "$factory_line" | awk '{
        for (field = 1; field <= NF; field++) {
            if ($field ~ /IEffectsFactory\/default/) { print $field; exit }
        }
    }')
    return 0
}

ui_print "- AudioFreedom universal audio-stack installer"

command -v app_process >/dev/null 2>&1 || fail "Android app_process is unavailable"
command -v nsenter >/dev/null 2>&1 || fail "Android nsenter is unavailable"
command -v mount >/dev/null 2>&1 || fail "Android mount is unavailable"
[ "$(file_hash "$PATCHER")" = "$PATCHER_HASH" ] || fail "config patcher hash mismatch"

if service list 2>/dev/null |
        grep -q 'android.hardware.audio.effect.IFactory/default'; then
    BACKEND="aidl64"
    PATCH_MODE="aidl"
    LIBRARY_NAME="libaudiofreedomfx.so"
    LIBRARY_SOURCE="$MODPATH/payload/backends/aidl64/$LIBRARY_NAME"
    LIBRARY_HASH="$AIDL64_LIBRARY_HASH"
    PROBE_SOURCE="$MODPATH/payload/backends/aidl64/audiofreedom_dlopen_probe"
    PROBE_HASH="$AIDL64_PROBE_HASH"
    SOUNDFX_TARGET="/vendor/lib64/soundfx"
    FACTORY_INTERFACE="android.hardware.audio.effect.IFactory/default"
else
    command -v lshal >/dev/null 2>&1 || fail "neither AIDL nor HIDL factory discovery is available"
    choose_hidl_backend || fail "supported AIDL or classic HIDL Effects Factory was not found"
fi

[ -d "$SOUNDFX_TARGET" ] || fail "selected soundfx directory is missing: $SOUNDFX_TARGET"
[ "$(file_hash "$LIBRARY_SOURCE")" = "$LIBRARY_HASH" ] || fail "$BACKEND library hash mismatch"
[ "$(file_hash "$PROBE_SOURCE")" = "$PROBE_HASH" ] || fail "$BACKEND probe hash mismatch"

TEMP_PROBE="/data/local/tmp/audiofreedom-installer-probe-$$"
trap 'rm -f "$TEMP_PROBE"' 0
cp -f "$PROBE_SOURCE" "$TEMP_PROBE" || fail "could not stage the backend probe"
chmod 0755 "$TEMP_PROBE" || fail "could not make the backend probe executable"
if [ "$BACKEND" = "aidl64" ]; then
    "$TEMP_PROBE" "$LIBRARY_SOURCE" >/dev/null 2>&1 || fail "AIDL64 library probe failed"
else
    "$TEMP_PROBE" "$LIBRARY_SOURCE" >/dev/null 2>&1 || fail "$BACKEND library probe failed"
fi
rm -f "$TEMP_PROBE"
trap - 0

mkdir -p "$STATE_DIR" "$CONFIG_DIR" "$STAGED_SOUNDFX" || fail "could not create module staging"
: >"$PLAN"
: >"$PATCH_LOG"
candidate_list="$STATE_DIR/config-candidates.txt"
: >"$candidate_list"
for search_root in /vendor/etc /odm/etc; do
    [ -d "$search_root" ] || continue
    find "$search_root" -type f \( -name audio_effects.xml -o -name audio_effects_config.xml \) \
        2>/dev/null >>"$candidate_list"
done
sort -u "$candidate_list" >"$candidate_list.sorted"

config_count=0
while IFS= read -r source_config; do
    [ -r "$source_config" ] || continue
    config_count=$((config_count + 1))
    [ "$config_count" -le 48 ] || fail "too many effects configurations; refusing broad mounts"
    relative_config="payload/configs/config-$config_count.xml"
    output_config="$MODPATH/$relative_config"
    if ! CLASSPATH="$PATCHER" app_process /system/bin \
            com.svetlio.audiofreedom.tools.AudioEffectsConfigPatcher \
            "$source_config" "$output_config" "$PATCH_MODE" "$LIBRARY_NAME" \
            >>"$PATCH_LOG" 2>&1; then
        fail "could not safely patch $source_config"
    fi
    grep -q "$IMPL_UUID" "$output_config" || fail "patch validation failed for $source_config"
    config_context=$(ls -Zd "$source_config" 2>/dev/null | awk '{print $1}')
    case "$config_context" in
        u:object_r:*:s0) ;;
        *) config_context="u:object_r:vendor_configs_file:s0" ;;
    esac
    set_perm "$output_config" 0 0 0644 "$config_context"
    echo "$source_config|$relative_config" >>"$PLAN"
done <"$candidate_list.sorted"
[ "$config_count" -gt 0 ] || fail "no Android audio effects XML configuration was found"

cp -af "$SOUNDFX_TARGET/." "$STAGED_SOUNDFX/" || fail "could not preserve OEM soundfx libraries"
cp -f "$LIBRARY_SOURCE" "$STAGED_SOUNDFX/$LIBRARY_NAME" || fail "could not stage AudioFreedom"
[ "$(file_hash "$STAGED_SOUNDFX/$LIBRARY_NAME")" = "$LIBRARY_HASH" ] ||
    fail "staged AudioFreedom library hash mismatch"
soundfx_context=$(ls -Zd "$SOUNDFX_TARGET" 2>/dev/null | awk '{print $1}')
case "$soundfx_context" in
    u:object_r:*:s0) ;;
    *) soundfx_context="u:object_r:vendor_file:s0" ;;
esac
set_perm_recursive "$MODPATH/direct-bind" 0 0 0755 0644 "$soundfx_context"

cat >"$PROFILE" <<EOF
BACKEND=$BACKEND
PATCH_MODE=$PATCH_MODE
FACTORY_INTERFACE=$FACTORY_INTERFACE
SOUNDFX_TARGET=$SOUNDFX_TARGET
LIBRARY_NAME=$LIBRARY_NAME
EXPECTED_LIBRARY_HASH=$LIBRARY_HASH
CONFIG_COUNT=$config_count
EOF

set_perm_recursive "$MODPATH/payload/tools" 0 0 0755 0644
set_perm_recursive "$MODPATH/payload/backends" 0 0 0755 0644
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/direct-bind.sh" 0 0 0755
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755

staged_count=$(find "$STAGED_SOUNDFX" -maxdepth 1 -type f 2>/dev/null | wc -l)
ui_print "- Backend: $BACKEND ($FACTORY_INTERFACE)"
ui_print "- Patched $config_count device-owned effects configuration(s)"
ui_print "- Preserved $staged_count OEM soundfx libraries"
ui_print "- No manufacturer, model, product, or serial restriction is used"
ui_print "- Reboot is required; disabling the module and rebooting rolls back all mounts"
