#!/system/bin/sh
set -u

RUNTIME_DIR=${0%/*}
MODULE_DIR=${RUNTIME_DIR%/*}
CONTROLLER="$MODULE_DIR/system/bin/audiofreedom-controller"
STATE_DIR="$MODULE_DIR/state"
PID_FILE="$STATE_DIR/controller.pid"
MODE_FILE="$STATE_DIR/controller.mode"
STATUS_FILE="$STATE_DIR/controller.status"
LOG_FILE="$STATE_DIR/controller.log"

mkdir -p "$STATE_DIR"
: >"$LOG_FILE"

if [ ! -x "$CONTROLLER" ]; then
    echo "controller-binary-missing" >"$STATUS_FILE"
    exit 2
fi

if [ -f "$PID_FILE" ]; then
    old_pid=$(cat "$PID_FILE" 2>/dev/null || true)
    if [ -n "$old_pid" ] && [ -r "/proc/$old_pid/cmdline" ] &&
       grep -aq "audiofreedom-controller" "/proc/$old_pid/cmdline"; then
        echo "already-running" >"$STATUS_FILE"
        exit 0
    fi
    rm -f "$PID_FILE"
fi

mode=
if "$CONTROLLER" --probe output-mix >>"$LOG_FILE" 2>&1; then
    mode=output-mix
elif "$CONTROLLER" --probe stream-default-media >>"$LOG_FILE" 2>&1; then
    mode=stream-default-media
else
    echo "xml-fallback-required" >"$STATUS_FILE"
    exit 10
fi

"$CONTROLLER" "$mode" >>"$LOG_FILE" 2>&1 &
controller_pid=$!
sleep 1
if ! kill -0 "$controller_pid" 2>/dev/null; then
    echo "controller-start-failed" >"$STATUS_FILE"
    exit 3
fi

echo "$controller_pid" >"$PID_FILE"
echo "$mode" >"$MODE_FILE"
echo "running" >"$STATUS_FILE"
