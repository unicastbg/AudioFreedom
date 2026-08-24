#!/system/bin/sh

# Bind mounts remain valid for already loaded audio processes until reboot.
rm -f /data/local/tmp/audiofreedom-legacy-probe
rm -f /data/local/tmp/libaudiofreedomfx_legacy.so
