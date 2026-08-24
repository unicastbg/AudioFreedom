#!/system/bin/sh

MODDIR=${0%/*}
STATE_DIR="$MODDIR/state"
STATUS_FILE="$STATE_DIR/status.txt"
IMPL_UUID="2f6e8c10-8d44-4b42-b110-16f3a729ef01"
CONFIG="/vendor/etc/audio/sku_sun/audio_effects_config.xml"
LEGACY_CONFIG="/vendor/etc/audio/sku_sun/audio_effects.xml"
LIBRARY="/vendor/lib64/soundfx/libaudiofreedomfx.so"

mkdir -p "$STATE_DIR"

waited=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$waited" -lt 120 ]; do
    sleep 2
    waited=$((waited + 2))
done

{
    echo "mount_mode=direct-bind"
    "$MODDIR/direct-bind.sh" status
    if [ -r "$LIBRARY" ] && grep -q "$IMPL_UUID" "$CONFIG" 2>/dev/null &&
       grep -q "$IMPL_UUID" "$LEGACY_CONFIG" 2>/dev/null; then
        echo "effect=available"
        echo "dsp=preamp,10-band-eq,bass-foundation,detail-recovery,immersive-field,linked-limiter"
    else
        echo "effect=unavailable"
    fi
    if service list 2>/dev/null | grep -q "android.hardware.audio.effect.IFactory/default"; then
        echo "factory_service=available"
    else
        echo "factory_service=missing"
    fi
} >"$STATUS_FILE"
