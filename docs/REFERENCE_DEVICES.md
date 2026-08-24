# Reference devices

## Xiaomi 15 Ultra EEA

Validated on July 17, 2026.

| Property | Observed value |
|---|---|
| Model | 25010PN30G (`xuanyuan_eea`) |
| OS | Android 16, HyperOS 3.0.302.0 EEA |
| SoC | Qualcomm SM8750 |
| Hardware SKU | `xuanyuan`; vendor SKU `sun` |
| Core Audio HAL | Stable AIDL |
| Effects HAL | `android.hardware.audio.effect.IFactory/default` |
| Effect library ABI | Stable AIDL Effects V3 |
| Primary processing | Float32 stereo; 44.1 and 48 kHz observed |
| Root solution | Available during diagnostics |

### Active vendor stack

- Dynamic AIDL effect libraries are loaded from `/vendor/lib64/soundfx`.
- The active effect configuration is under `/vendor/etc/audio/sku_sun`.
- Effect libraries expose `createEffect`, `queryEffect`, and `destroyEffect` entry points.
- Dolby DAP is enabled on the global music chain; MiSound is registered but disabled.
- Qualcomm compressed offload and Bluetooth A2DP/LE offload are enabled.
- A dedicated Bluetooth spatializer output is present.

### Backend decision

The working backend is a self-contained AIDL Effects V3 dynamic library registered
through direct bind mounts of an OEM-preserving `soundfx` directory and patched copies of
the device-owned active effect configurations. It does not use Meta-OverlayFS or replace
Xiaomi's audio factory service. The companion app retains an enabled session-0 effect
through a foreground service; its switch creates or releases that effect independently
of Activity visibility. The universal installer discovers this stack by capability and
does not use a Xiaomi model or serial restriction.

### Verification

The AudioFreedom ARM64 native test executable ran successfully on the device. Tests
covered bypass integrity, the -12 dB proof gain, parameter clamping, invalid stream
rejection, frame accounting, protocol identity, wire-format round trips, and malformed
wire-message rejection. The temporary executable was removed after the test.

Repeated speaker tests audibly confirmed processing, including live equalizer, limiter,
Bass Foundation, Detail Recovery, and Immersive Field parameter changes. AudioFlinger
reported AudioFreedom enabled on session 0 with the companion service as its client. The
service bypasses processing whenever Android leaves `MODE_NORMAL`, protecting cellular
and VoIP call modes without modifying the driver descriptor.

The `0.9.0` universal package was confirmed to boot and process audio without
Meta-OverlayFS. Its AIDL boot-order recovery is the preferred installation baseline for
this profile.

## Sony Xperia 1 III / LineageOS 23

Inspected and integrated in August 2026.

| Property | Observed value |
|---|---|
| Model | `Xperia 1 III`; product/device `XQ-BC72` |
| Framework / vendor API | LineageOS 23 / API 36 |
| First API level | 30 |
| ABI | arm64-v8a |
| Core Audio HAL | HIDL 6.0 |
| Effects HAL | `android.hardware.audio.effect@6.0::IEffectsFactory/default` |
| Effect library ABI | Classic 32-bit ARMv7 `audio_effect_library_t` (`AELI`) |
| Primary processing | 48 kHz, stereo, AudioFlinger float |
| Active vendor config | `/vendor/etc/audio_effects.xml` |

### Portability class

This device is the first reference for the legacy/HIDL backend. Its registration path is
standard Android XML plus the classic effect-library ABI; Qualcomm effects coexist in the
same factory but are not dependencies of AudioFreedom. The same adapter should therefore
cover many devices launched with Android 11-era vendor partitions and later upgraded by
LineageOS or another system image, subject to config-path, linker, SELinux, and offload
checks.

The universal installer selects this backend from the observed HIDL service, 32-bit
factory process maps, and standard vendor paths. It does not check
`ro.product.manufacturer`, model, product, device, or serial. Device-owned XML is patched
at installation time instead of replacing it with a configuration copied from another
phone.

The HIDL effects service is a 32-bit process and loads implementations from
`/vendor/lib/soundfx`; the phone's ARM64 application ABI does not determine the effect
library ABI. The module must therefore package the ARMv7 adapter even though the
companion APK is a normal ARM64-capable Android application.

The vendor stack advertises extensive Qualcomm compressed and Bluetooth offload routes.
Those paths may bypass software AudioFlinger effects and must be reported separately from
the primary mixer. `org.lineageos.audiofx` should be disabled while comparing DSP output
to avoid stacking unrelated effects.

### Verification

The module installs and the companion app detects the driver on LineageOS 23. Audible
processing and live setting changes are confirmed through the built-in speaker and
Bluetooth output. Recreated playback sessions receive the controlled effect, and the
Immersive Field is clearly audible over Bluetooth on this reference device.
