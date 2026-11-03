# Xperia 1 III / LOS 24 Global-Effect Suspension

Observed on 2026-11-03, Xperia 1 III XQ-BC72, LineageOS
24.0-20261103-NIGHTLY-pdx215, Android 17 / SDK 37.

## Installation Was Healthy

- App 0.9.8-beta, version code 43.
- Driver build 762fe5ee88aa221b8cbe7fb73a7c08a94fa2bb0f2522f561ee896cba36382b9a.
- Correct legacy32 backend, HIDL Effects Factory 6.0, /vendor/lib/soundfx.
- Root available; module direct-bind status active, 2/2 mounts.
- Loaded library SHA-256 matched the installed module's expected hash.
- AudioFreedom's session-0 effect was present on the active media mixer.

## Failure Evidence

After processing was turned off/on during an existing Media Player session:

- App preferences still requested processing_enabled=true.
- AudioFlinger listed the controlling app handle as enabled, but the actual effect
  was suspended (state IDLE, Suspended=y).
- A low-priority read-only client reported getEnabled=false, DSP enabled=0,
  processed_frames=0, silent meters, and an unchanged stream-publication sequence.
- The same mixer had an enabled Qualcomm Volume listener for Music effect on the
  player's non-global session. It was owned by audioserver, not by the AudioFX app.

This supports a global-effect suspension/attachment-order problem, not a missing driver,
root denial, unsupported PCM format, or proof of an AudioFX conflict. Do not disable
AudioFX or remove vendor volume/speaker-protection components based on this report.

The LineageOS framework's ThreadBase::checkSuspendOnEffectEnabled suspends eligible
output-mix effects when effects on non-global sessions are enabled. Its
checkSuspendOnAddEffectChain_l reapplies stored suspension state to a newly added chain.
Source: https://raw.githubusercontent.com/LineageOS/android_frameworks_av/lineage-24.0/services/audioflinger/Threads.cpp

The framework rule is not necessarily new to LOS 24. This device/build's observed
regression must not be generalized to every LOS 24 device or attributed to a specific
framework commit without additional comparison.

## Confirmed Temporary Recovery

1. Stop playback and fully close Media Player, releasing its audio session.
2. Turn AudioFreedom processing off, then on.
3. Reopen Media Player and start playback.

User confirmed that the bars returned and remained active. AudioFlinger showed the
new AudioFreedom effect active and not suspended. The live diagnostic then reported:

```text
enabled=true, DSP state=Processing, rate=48000, channels=2
processed_frames: 3002880 -> 3050880 -> 3098880 (one-second samples)
input/output meters changed; stream sequence advanced: 7666 -> 7716 -> 7766
```

No app/module update or persistent phone configuration change was performed during
diagnosis. The temporary read-only diagnostic JAR was removed after each invocation.
The diagnostic is tools/diagnostics/AudioFreedomReadStatus.java; it attaches at priority
-1000, never sets parameters/enabled state, and releases its handle on exit.

## 1.0.0 Recovery

The app detects a disabled native control instance during active playback and switches
to the affected media sessions. The global framework handle is disabled before any
per-session inserts are enabled. Existing AIDL shared DSP control remains compatible;
the root discovery helper sends only numeric active media/game session IDs to the app.
Normal global playback does not start the helper. No vendor component is disabled.

Android can retain an effect engine on a live session after its last client releases
it. The controller explicitly disables every owned handle before releasing it. This
also prevents an older instance's shared stream telemetry from masking suspension.
The helper is tied to the foreground service and exits on pipe closure. Off and non-normal
audio modes stop discovery and release effects. Missing root or unsupported attachment
is shown as suspended rather than presented as a successful recovery.

Physical Xperia speaker test: three off/on cycles during uninterrupted music playback
recovered fresh PCM, and live preamp changes produced the expected -12 and -6 dB
meter differences. The matching installed 0.9.8-beta driver was not replaced.

## Remaining Validation

- Validate physical call/VoIP transitions, Bluetooth, wired/USB, offload, and additional ROMs.
- Test coexistence with active AudioFX effects; the diagnosis does not require uninstalling AudioFX.
