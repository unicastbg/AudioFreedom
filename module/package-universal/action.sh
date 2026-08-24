#!/system/bin/sh

MODDIR=${0%/*}
STATUS_FILE="$MODDIR/state/status.txt"
LOG_FILE="$MODDIR/state/direct-bind.log"
PATCH_LOG="$MODDIR/state/config-patcher.log"

echo "AudioFreedom universal audio-stack module"
if [ -r "$STATUS_FILE" ]; then
    cat "$STATUS_FILE"
else
    "$MODDIR/direct-bind.sh" status
fi
if [ -s "$LOG_FILE" ]; then
    echo "--- mount log ---"
    cat "$LOG_FILE"
fi
if [ -s "$PATCH_LOG" ]; then
    echo "--- config patcher ---"
    cat "$PATCH_LOG"
fi
echo "rollback=disable AudioFreedom and reboot"
