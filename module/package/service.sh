#!/system/bin/sh

MODDIR=${0%/*}
STATE_DIR="$MODDIR/state"
STATUS_FILE="$STATE_DIR/status.txt"
CONFIG="/vendor/etc/audio/sku_sun/audio_effects_config.xml"
LEGACY_CONFIG="/vendor/etc/audio/sku_sun/audio_effects.xml"
LIBRARY="/vendor/lib64/soundfx/libaudiofreedomfx.so"
IMPL_UUID="2f6e8c10-8d44-4b42-b110-16f3a729ef01"

mkdir -p "$STATE_DIR"

waited=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$waited" -lt 120 ]; do
    sleep 2
    waited=$((waited + 2))
done

if [ -r "$LIBRARY" ] && grep -q "$IMPL_UUID" "$CONFIG" 2>/dev/null &&
   grep -q "$IMPL_UUID" "$LEGACY_CONFIG" 2>/dev/null; then
    {
        echo "overlay=active"
        echo "library=$LIBRARY"
        echo "config=$CONFIG"
        echo "music_chain=$LEGACY_CONFIG"
        echo "proof_gain_db=-12"
    } >"$STATUS_FILE"

    CONTROLLER="$MODDIR/system/bin/audiofreedom-controller"
    START_CONTROLLER="$MODDIR/runtime/start-controller.sh"
    if [ -x "$CONTROLLER" ] && [ -x "$START_CONTROLLER" ]; then
        if "$START_CONTROLLER"; then
            controller_mode=$(cat "$STATE_DIR/controller.mode" 2>/dev/null || echo unknown)
            echo "controller=$controller_mode" >>"$STATUS_FILE"
        else
            controller_status=$(cat "$STATE_DIR/controller.status" 2>/dev/null || echo failed)
            echo "controller=$controller_status" >>"$STATUS_FILE"
        fi
    else
        echo "controller=not-packaged" >>"$STATUS_FILE"
    fi
else
    echo "overlay=inactive" >"$STATUS_FILE"
fi
