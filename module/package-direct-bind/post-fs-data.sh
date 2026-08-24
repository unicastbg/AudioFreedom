#!/system/bin/sh

MODDIR=${0%/*}
STATE_DIR="$MODDIR/state"
mkdir -p "$STATE_DIR"

if "$MODDIR/direct-bind.sh" mount; then
    echo "post_fs_data=active" >"$STATE_DIR/post-fs-data.status"
else
    echo "post_fs_data=failed" >"$STATE_DIR/post-fs-data.status"
fi
