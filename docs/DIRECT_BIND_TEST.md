# Confirmed Meta-OverlayFS-free installation

Confirmed on the Xiaomi 15 Ultra EEA reference device on July 17, 2026. After
Meta-OverlayFS was removed and `directbind1` installed, the phone booted normally and
the existing companion APK continued to control audible AudioFreedom processing.

The `directbind1` Xiaomi package keeps the proven dev9 driver and replaces the generic
Meta-OverlayFS dependency with three device-specific bind mounts:

- a complete staged copy of `/vendor/lib64/soundfx`, including every OEM library and
  `libaudiofreedomfx.so`;
- `/vendor/etc/audio/sku_sun/audio_effects_config.xml`;
- `/vendor/etc/audio/sku_sun/audio_effects.xml`.

The installer verifies the Xiaomi `xuanyuan_eea` Android 16 profile, both vendor config
hashes, the AIDL Effects Factory, and the exact known-good driver hash. It copies the
phone's existing `soundfx` directory during installation so no Xiaomi library is removed.
The module has `skip_mount`; KernelSU's normal module mounting and a mounting metamodule
are not used.

## Installation sequence

1. Keep the known-good APK installed.
2. In KernelSU, disable or uninstall Meta-OverlayFS, but do not reboot yet. Its mounts
   remain available for the rest of the current boot.
3. Install `AudioFreedom-xuanyuan-eea-0.1.0-dev9-directbind1.zip` over the existing
   AudioFreedom module. It uses the same module ID and replaces that package.
4. Confirm the installer reports preserved soundfx libraries and KernelSU shows the
   direct-bind AudioFreedom package enabled, then reboot once.
5. Open the module action/status screen. It must report `direct_bind_mounts=3/3`,
   `direct_bind=active`, and `effect=available`.
6. Start media and repeat the companion app ON/OFF test.

If audio or boot behavior is wrong, disable AudioFreedom in KernelSU and reboot. The
three mounts exist only for the current boot and disappear on reboot. Uninstalling the
module also requires a reboot before considering rollback complete.

This removes the Meta-OverlayFS module, but it is not a root-hiding feature. A banking
app can theoretically detect KernelSU, AudioFreedom's module files, or the three bind
mounts. Revolut behavior must be tested independently from audio operation.
