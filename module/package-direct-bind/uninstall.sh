#!/system/bin/sh

# The bind mounts belong to this boot's mount namespace. Leaving them in place until
# reboot avoids invalidating libraries already loaded by the running audio service.
rm -f /data/local/tmp/audiofreedom-factory-probe
