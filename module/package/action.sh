#!/system/bin/sh

MODDIR=${0%/*}
STATUS_FILE="$MODDIR/state/status.txt"

echo "AudioFreedom DSP Proof"
if [ -r "$STATUS_FILE" ]; then
    cat "$STATUS_FILE"
else
    echo "status=waiting-for-reboot"
fi

echo "factory_service=$(service list 2>/dev/null | grep -c 'android.hardware.audio.effect.IFactory/default')"

CONTROLLER_STATUS="$MODDIR/state/controller.status"
CONTROLLER_LOG="$MODDIR/state/controller.log"
if [ -r "$CONTROLLER_STATUS" ]; then
    echo "controller_status=$(cat "$CONTROLLER_STATUS")"
fi
if [ -s "$CONTROLLER_LOG" ]; then
    echo "--- controller log ---"
    cat "$CONTROLLER_LOG"
fi
