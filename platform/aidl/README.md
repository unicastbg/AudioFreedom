# Android 16 AIDL effect adapter

This directory contains the Stable AIDL V3 backend for AudioFreedom. It exports the
three dynamic effect-library entry points used by Android's AIDL Effects Factory and
delegates float32 PCM processing to `libaudiofreedom_dsp`.

The EQ1 build starts with the proven `-12 dB` preamp and a flat, disabled equalizer until
the companion restores saved settings. The vendor-extension parameter channel supports
protocol discovery, enable/bypass, global preamp control, ten per-band gains, equalizer
enable, and driver status. It accepts both direct AIDL extension bytes and Android's
legacy `effect_param_t` wrapper used by `AudioEffect.setParameter`.

## AOSP and LineageOS integration

Place this repository at `vendor/audiofreedom` in an Android 16 source checkout, then
stage the adapter from the root of that checkout:

```sh
sh vendor/audiofreedom/tools/stage-aosp.sh "$PWD"
```

The staging step is required because AOSP limits `effectCommonFile` visibility to
subpackages of `hardware/interfaces/audio/aidl/default`. Add `libaudiofreedomfx` to the
device product packages, then merge the nodes from `audio_effects_config.fragment.xml`
into the device's active AIDL effects configuration:

```make
PRODUCT_PACKAGES += libaudiofreedomfx
```

Build with the normal Android source environment:

```sh
m libaudiofreedomfx
```

The fragment is merge input, not a replacement effects configuration. Its `library`
node belongs in `libraries`, its `effect` node belongs in `effects`, and its post-process
instruction means adding `<apply effect="audiofreedom"/>` to the existing `music` stream.
Replacing an OEM file would discard Dolby, Dirac, MiSound, or other vendor effects.

## Rooted stock ROMs

Rooted OEM releases need the same binary but a separate installer profile. The module
must discover the active effects XML, overlay the library in the matching vendor path,
merge only the AudioFreedom entries, account for SELinux, and restore the original audio
stack on uninstall. The guarded Xiaomi profile has verified library loading, effect
registration, session-0 attachment, persistent foreground-service ownership, and
audible -12 dB speaker processing. The companion service releases the effect outside
normal audio mode.
