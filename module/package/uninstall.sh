#!/system/bin/sh

MODDIR=${0%/*}
STOP_CONTROLLER="$MODDIR/runtime/stop-controller.sh"

if [ -x "$STOP_CONTROLLER" ]; then
    "$STOP_CONTROLLER" >/dev/null 2>&1 || true
fi

rm -f /data/local/tmp/audiofreedom-factory-probe
