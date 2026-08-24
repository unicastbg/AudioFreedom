# ROM integration

This is the mount-free AudioFreedom path for Android 16 AOSP and LineageOS builds. It
installs the effect as part of the ROM rather than using KernelSU, Magisk, OverlayFS, or
bind mounts.

1. Place this repository at `vendor/audiofreedom`.
2. Run `sh vendor/audiofreedom/tools/stage-aosp.sh "$ANDROID_BUILD_TOP"`.
3. Inherit `vendor/audiofreedom/platform/rom/audiofreedom_product.mk` from the device
   product makefile.
4. Merge the AudioFreedom library, effect, and music `apply` nodes from
   `platform/aidl/audio_effects_config.fragment.xml` into the device tree's active AIDL
   effects configuration. Preserve every existing vendor effect.
5. Build `libaudiofreedomfx` and `audiofreedom-controller` with the Android 16 platform.

The XML music post-process entry is the first attachment mechanism. The controller is a
platform-versioned fallback and is not started automatically by this product fragment;
a production ROM must give it a dedicated init service and SELinux domain rather than
running an unrestricted root daemon.

The acceptance test is deliberately obvious: AudioFreedom starts enabled with a
`-12 dB` preamp, then speaker, USB, and Bluetooth software paths are checked separately.
Offloaded paths may bypass the effect and must be reported as unsupported rather than
treated as successful processing.
