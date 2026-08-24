# Architecture

AudioFreedom is one product with three independently buildable deliverables:

- the companion Android app;
- the platform-independent DSP library;
- a root module that installs the appropriate Android effect adapter and DSP binary.

## Product boundary

This is an independently implemented audio effect, not a continuation or binary patch
of ViPER4Android. The current source uses its own UUIDs, protocol, UI, name, DSP
implementation, and presets.

## Components

```text
Companion app
    |
    | versioned control protocol (non-real-time)
    v
AudioFreedom root module
    |
    +---- Android-versioned controller ----> AudioFlinger output mix / media usage
    |
    | atomic parameter snapshots
    v
Android effect adapter ----> platform-independent DSP core
    |                              |
    + legacy AudioFlinger ABI      + equalizer and limiter
    + AIDL Effects HAL adapter     + bass, detail, and spatial DSP
```

The app is optional while audio is processing. Parameters are copied into atomic state;
the audio callback never performs Binder calls, file I/O, allocation, locking, or logging.

The preferred rooted-stock attachment order is:

1. A persistent controller-owned `AUDIO_SESSION_OUTPUT_MIX` effect, matching ViPER's
   legacy/session-0 model.
2. A controller registration as the default effect for `AUDIO_USAGE_MEDIA`.
3. A device-profile XML post-process attachment when dynamic attachment is rejected.

Only the controller is Android-version-specific. It uses the private platform
`libaudioclient` API and must never be linked into the companion APK or vendor DSP
library. The DSP, AIDL effect, protocol, and UI remain independent of manufacturer audio
libraries.

## Why a new companion app

The existing AndroidAudioMods APK source is not published. Depending on that APK would
couple this project to an undocumented protocol and an artifact we cannot audit, modify,
or commercially redistribute. A separate app also gives us explicit compatibility:
protocol major versions break compatibility, while minor versions only add optional data.

## Distribution units

The companion APK and root-module archive are versioned together, but remain separate
artifacts. The universal module selects AIDL64, legacy64, or legacy32 from the active
factory and process ABI. Installer-specific compatibility code stays outside the DSP
library. Installation, rollback, backend probes, effect registration, and device-owned
configuration patching are handled by the module package.

## Integration strategy

The shared DSP is the stable part. Device integration is selected after diagnostics:

1. Legacy effect library for devices whose active factory still loads the classic effect
   ABI from an effects configuration.
2. AIDL effect library for vendor factories compatible with the current AOSP dynamic
   effect interface.
3. ROM-integrated AIDL service build for custom ROM support. Replacing a stock vendor
   Effects HAL service is not treated as a universal root installation technique.

ROM integration is the mount-free path. The effect library and its XML registration are
built into the vendor image, while the optional controller is built as a platform
`system_ext` binary against that ROM's exact `libaudioclient`. Stock-ROM installation
still needs a systemless mount or an unsafe physical partition modification; a root
daemon by itself cannot register a new dynamic effect library with the Stable AIDL
factory.

Registration and attachment are separate operations. Every backend still requires the
effect UUID and library to be registered with the active Effects Factory. Registration
does not imply dependence on an OEM DSP; it makes the AudioFreedom implementation known
to Android. The controller then requests global attachment through AudioFlinger.

On-device acceptance begins with an unmistakable gain change, then checks live DSP
updates through speaker, wired/USB, Bluetooth, and common local/streaming sources.
Offloaded paths are reported separately; we do not claim processing where the audio
stream bypasses the software effect chain.

## UUIDs

- Effect type: `a7e03c90-7c3d-4f48-9c8d-497c8f1b1201`
- Effect implementation: `2f6e8c10-8d44-4b42-b110-16f3a729ef01`

These identifiers belong to this project and must remain stable after the first public
release.

## Licensing boundary

The current Bass Foundation source does not include the experimental ViPER-derived
Dynamic System implementation that appeared in the private 0.8 test build. Historical
0.8 artifacts remain personal-use only and must not be distributed. Third-party DSP
dependencies must be approved individually and recorded with their license and
attribution obligations. AudioFreedom's original public source is currently all rights
reserved and is not offered for external redistribution or contribution.
