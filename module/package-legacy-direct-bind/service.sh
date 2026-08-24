#!/system/bin/sh

MODDIR=${0%/*}
STATE_DIR="$MODDIR/state"
STATUS_FILE="$STATE_DIR/status.txt"
IMPL_UUID="2f6e8c10-8d44-4b42-b110-16f3a729ef01"
CONFIG="/vendor/etc/audio_effects.xml"
LIBRARY="/vendor/lib/soundfx/libaudiofreedomfx_legacy.so"

mkdir -p "$STATE_DIR"
waited=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$waited" -lt 120 ]; do
    sleep 2
    waited=$((waited + 2))
done

{
    echo "mount_mode=legacy-direct-bind"
    "$MODDIR/direct-bind.sh" status
    if [ -r "$LIBRARY" ] && grep -q "$IMPL_UUID" "$CONFIG" 2>/dev/null; then
        echo "effect=available"
        echo "dsp=preamp,10-band-eq,bass-foundation,detail-recovery,immersive-field,linked-limiter"
    else
        echo "effect=unavailable"
    fi
    factory_line=$(lshal 2>/dev/null |
        grep -m 1 'android.hardware.audio.effect@[0-9][.][0-9]::IEffectsFactory/default')
    if [ -n "$factory_line" ]; then
        factory_service=$(echo "$factory_line" | awk '{
            for (field = 1; field <= NF; field++) {
                if ($field ~ /IEffectsFactory\/default/) { print $field; exit }
            }
        }')
        echo "factory_service=$factory_service"
    else
        echo "factory_service=unknown"
    fi
} >"$STATUS_FILE"
