#!/system/bin/sh
set -u

RUNTIME_DIR=${0%/*}
MODULE_DIR=${RUNTIME_DIR%/*}
STATE_DIR="$MODULE_DIR/state"
PID_FILE="$STATE_DIR/controller.pid"
STATUS_FILE="$STATE_DIR/controller.status"

if [ ! -f "$PID_FILE" ]; then
    echo "stopped" >"$STATUS_FILE"
    exit 0
fi

controller_pid=$(cat "$PID_FILE" 2>/dev/null || true)
if [ -n "$controller_pid" ] && [ -r "/proc/$controller_pid/cmdline" ] &&
   grep -aq "audiofreedom-controller" "/proc/$controller_pid/cmdline"; then
    kill "$controller_pid"
    wait_count=0
    while kill -0 "$controller_pid" 2>/dev/null && [ "$wait_count" -lt 20 ]; do
        sleep 0.1
        wait_count=$((wait_count + 1))
    done
fi

rm -f "$PID_FILE"
echo "stopped" >"$STATUS_FILE"
