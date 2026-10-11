# Volume Leveler

An optional causal RMS leveler reduces differences between quiet and loud playback
passages. It is not offline whole-track normalization, ReplayGain, LUFS calibration,
noise removal, or a guarantee of distortion-free headphones.

The detector measures the signal after EQ, bass, detail, stereo, and reverb processing.
One gain is applied to all channels, followed by linked peak protection. No PCM buffer,
lookahead latency, allocation, or locking is added to the processing callback. Gain goals
update every 10 ms, using a 100 ms energy envelope and smoothed gain transitions.

Controls:

- Target RMS: -30 to -12 dBFS, default -18. This is digital level, not volume at the ears.
- Strength: 0 to 100 percent. Lower values retain more dynamics.
- Maximum boost: 0 to 12 dB. Attenuation is bounded at 36 dB.
- Response: 0.2 to 5 seconds for gain increases; reductions respond four times faster.
- Presets: Gentle (35%, +3 dB, 3 s), Balanced (65%, +6 dB, 1.5 s),
  Night (100%, +12 dB, 0.5 s). Every preset targets -18 dBFS RMS.

A 250 ms warmup avoids immediately boosting a new stream. Sustained input below
-80 dBFS peak resets the detector after 250 ms and prevents continued boost of near-silence.
The energy gate also avoids normalizing signals below approximately -60 dBFS RMS.
Audible noise in an active recording can still be amplified; this is not a denoiser.

Peak protection remains active while the leveler is enabled, even if the separate output
protection toggle is off. It uses the selected output ceiling when protection is on,
otherwise -1 dBFS. This bounds sample peaks, not reconstructed intersample true peaks.
The stored preamp and limiter preferences are never modified by leveler presets.
Because leveling follows the preamp, it can partly compensate for a manual preamp cut.
Use Target RMS to change the leveled average, or disable leveling to hear preamp changes directly.

Settings persist in profiles and assistant undo snapshots. Old profiles default to off.
Protocol 1.7 adds parameter 0x1700 without changing old messages. Both AIDL64 and legacy
backends validate ranges; AIDL output-mix control propagates changes to active instances.
Use the new module ZIP and reboot before testing the effect. Older drivers can continue
processing old effects with Volume Leveler off.

## Acceptance checks

1. Update the module, reboot, then install the APK. Start at moderate device volume.
2. Confirm old profiles still sound unchanged with Leveler off.
3. Try Balanced with quiet and loud recordings. Allow several seconds for settling.
4. Compare Gentle and Night on a track with alternating quiet and loud passages.
5. Test pauses and quiet backgrounds: there should be no growing hiss during silence.
6. Verify stereo position, EQ/preamp preservation, profile save/load, and assistant undo.
7. Test speaker, wired, Bluetooth, and USB routes; live setting changes should work.
8. Stop/restart playback and verify a fresh attack without an abrupt retained boost.
9. Verify call bypass and volume-button voice capture retain their existing behavior.

Native tests cover quiet/loud adaptation, silence, linked gain, bounded peaks with the
limiter preference off, disable/reset, different sample rates/channel counts, block-size
invariance, configuration bounds, and wire round trips. Listening tests still require a phone.
