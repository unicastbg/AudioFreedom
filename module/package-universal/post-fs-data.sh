#!/system/bin/sh

MODDIR=${0%/*}
audio_server_state=$(getprop init.svc.audioserver)
count=0
while [ ! -r "$MODDIR/state/profile.env" ] && [ "$count" -lt 20 ]; do
    sleep 1
    count=$((count + 1))
done
"$MODDIR/direct-bind.sh" mount || exit 1

. "$MODDIR/state/profile.env"
if [ "$BACKEND" = "aidl64" ] && [ "$audio_server_state" = "running" ]; then
    echo "boot_refresh=audioserver" >>"$MODDIR/state/direct-bind.log"
    setprop ctl.restart audioserver
fi
