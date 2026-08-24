# AudioFreedom root module

The current root package is built from `package-universal`. It contains independent
AIDL64, legacy64, and legacy32 AudioFreedom effect backends and selects one from the
active Android Effects Factory and its process ABI during installation.

## Compatibility selection

The installer checks for these stacks in order:

1. Stable AIDL `android.hardware.audio.effect.IFactory/default`, using AIDL64.
2. Classic HIDL `IEffectsFactory/default` in a 64-bit process, using legacy64.
3. Classic HIDL `IEffectsFactory/default` in a 32-bit process, using legacy32.

Selection does not use a manufacturer, model, product, device, or serial-number
allowlist. Unsupported factory interfaces or library probes cause installation to stop
without mounting a partial configuration.

## Systemless integration

The installer discovers readable `audio_effects.xml` and `audio_effects_config.xml`
files under `/vendor/etc` and `/odm/etc`. A structured XML patcher registers the selected
AudioFreedom library and descriptor in device-owned copies. The module also preserves
the complete active OEM `soundfx` directory before adding its own library.

At boot, direct bind mounts expose the staged sound-effect directory and patched
configurations. The original partitions are not modified. The AIDL path restarts an
already-running audio service after mounts so the Effects Factory does not retain a
pre-mount descriptor cache. Disabling or uninstalling the module and rebooting removes
the mounts.

The module does not contain or call Xiaomi, Sony, Qualcomm, Dolby, or other OEM DSP
algorithms. Manufacturer files are preserved only because the device's existing effects
must continue to load alongside AudioFreedom.

## Build

After building all native backends and probes, package the module from PowerShell:

```powershell
.\tools\build-universal-module.ps1
```

The output is written under `module/build-universal`, which is excluded from source
control. File hashes are embedded into the package and verified on the phone before any
mount plan is accepted.

## Earlier packages

The `package`, `package-direct-bind`, and `package-legacy-direct-bind` directories and
their matching build scripts are retained as development history. New device testing
should use the universal package unless a backend-specific regression needs isolation.
