#!/system/bin/sh

MODDIR=${0%/*}
STATE_DIR="$MODDIR/state"
STATUS_FILE="$STATE_DIR/status.txt"
PROFILE="$STATE_DIR/profile.env"
PLAN="$STATE_DIR/mount-plan.txt"
IMPL_UUID="2f6e8c10-8d44-4b42-b110-16f3a729ef01"

mkdir -p "$STATE_DIR"
waited=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$waited" -lt 120 ]; do
    sleep 2
    waited=$((waited + 2))
done

{
    "$MODDIR/direct-bind.sh" status
    if [ -r "$PROFILE" ]; then
        . "$PROFILE"
        echo "config_count=$CONFIG_COUNT"
        available=1
        [ -r "$SOUNDFX_TARGET/$LIBRARY_NAME" ] || available=0
        while IFS='|' read -r target relative; do
            [ -n "$target" ] || continue
            grep -q "$IMPL_UUID" "$target" 2>/dev/null || available=0
        done <"$PLAN"
        if [ "$available" = "1" ]; then
            echo "effect=available"
            echo "dsp=preamp,10-band-eq,bass-foundation,detail-recovery,immersive-field,linked-limiter"
        else
            echo "effect=unavailable"
        fi
    fi
} >"$STATUS_FILE"
