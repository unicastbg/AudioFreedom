#!/system/bin/sh

MODDIR=${0%/*}
[ -x "$MODDIR/direct-bind.sh" ] && "$MODDIR/direct-bind.sh" unmount >/dev/null 2>&1
exit 0
