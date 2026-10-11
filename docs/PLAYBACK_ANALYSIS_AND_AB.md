# Playback Analysis and A/B Comparison

Available in AudioFreedom 0.9.6-beta with the matching driver module.

## A/B listening

1. Open A/B comparison on the main screen and select Store A for your current sound.
2. Adjust the effects, or load another saved profile, then select Store B.
3. Select A and B while listening to switch between the stored settings.
4. Use your normal profile save controls to keep the preferred result as a named profile.

Store A/B explicitly replaces that slot. Editing the current sound never silently updates
a snapshot. The subtitle shows A, B, or Modified settings according to the current settings.
An empty selector is labeled Store A or Store B and captures current settings on the first
tap. Once populated, it switches to that snapshot. If A and B contain identical settings,
the selected slot still highlights correctly. Use the lower Store buttons to replace either slot.
Clearing snapshots does not change the sound. Snapshots persist independently for each
output-route ID, including all effects and Volume Leveler, but not the master-processing
switch. They never change profile assignments. Route detection has the same limitations
as existing automatic profiles, including device IDs that a ROM may not distinguish.

This first implementation is a manual settings comparison, not blind ABX testing,
whole-track replay, automatic level matching, or simultaneous processing through two DSP
chains. Compare the same passage and avoid assuming louder necessarily sounds better.
Switching uses the existing live settings transport; effects such as reverb and leveling
may need time to settle. A/B does not replay or record a music segment.

## Analysis readings

Expand Playback analysis while system processing is enabled. The analyzer observes the
PCM entering and leaving AudioFreedom without modifying it. Values update over fixed
approximately 250 ms windows, independent of callback block size.

- Input RMS: average digital signal level before AudioFreedom effects.
- Output RMS: average digital signal level after effects and output protection.
- Output crest factor: window sample peak minus output RMS. This describes peak-to-average
  contrast, not a score of recording quality.
- Stereo correlation: normalized left/right cross energy from -1 to +1. Opposing channels
  approach -1; identical channels approach +1. Unavailable for silence and non-stereo streams.
- Near full scale: fraction of output samples with magnitude at least 0.999. This is a
  headroom indicator, not proof of clipping, distortion detection, or true-peak metering.
- Output spectrum: ten constant-peak bandpass RMS levels, at the EQ center frequencies.
  In multichannel streams the spectrum uses the first two channels; broadband RMS and
  near-full-scale counts include all channels. Unsupported high-frequency bands show --.

Spectrum bars span -60 to 0 dBFS; numerical values may fall outside that display range.
The bandpass bank uses the standard RBJ formula with Q = sqrt(2), documented in the
[W3C Audio EQ Cookbook](https://www.w3.org/TR/audio-eq-cookbook/).
These overlapping filters are an approximate spectrum display, not a calibrated FFT
or a set of nonoverlapping energy bins.

These readings do not measure LUFS, acoustical SPL, headphone frequency response, room
response, codec quality, or all playback that bypasses this effect. They cannot establish
that an EQ setting will be distortion-free at a physical speaker. On AIDL, telemetry
comes from the most recently published active effect instance, as with existing meters,
not a sum of every simultaneous stream. No automatic DSP adjustment or LLM diagnosis is
performed by this feature.

## Lifecycle and Privacy

Analysis starts only when its section is expanded on the visible main screen. It stops
when collapsed or hidden, the app is backgrounded, processing is stopped, or audio is bypassed for
a call. A renewed three-second native lease prevents continued analysis after unexpected
app loss. The normal DSP and meters do not depend on this lease.

No microphone access, recording, PCM retention, upload, or analysis history is added.
Only derived measurements are exposed. Missing or stale data is shown as Waiting for audio
after 1.5 seconds without a new window. Older drivers show Driver update required rather
than fabricated readings. Existing effects can continue working with analysis unavailable.

Protocol 1.8 adds analysis enable 0x1800, summary 0x2002, and spectrum 0x2003. Both read
responses fit the existing 24-byte payload; a sequence number binds summary and spectrum
to the same window. The controller retries a mismatched pair once. Native publication is
fixed-size and nonblocking: contended writes are discarded, and readers retry at most
three times. The DSP callback allocates no analyzer storage or PCM buffers.

## Phone checks

1. Install the matching driver ZIP, reboot, then install the APK.
2. Confirm all existing effects still work with analysis collapsed.
3. Play audio and expand analysis. Change preamp with EQ enabled: input RMS should stay
   similar, while output RMS changes unless leveling compensates for the change.
4. Pause playback: readings should become unavailable rather than frozen as Live.
5. Resume playback, switch routes, and background/reopen the app; readings should recover.
6. Capture two distinct A/B settings, switch repeatedly, and confirm profile assignments
   are unchanged. Edit settings afterward and verify the snapshots remain unchanged.
7. Restart the app and verify route-specific snapshots persist.
8. Check call bypass and volume-button voice capture alongside existing behavior.

Automated tests cover RMS, crest factor, correlated/anti-phase/silent signals, headroom
counts, dominant spectrum band, nonfinite samples, reset/disable, block-size independence,
concurrent publication, telemetry validation/freshness, isolated A/B snapshots, and shared
settings serialization. Device listening and UI layout checks remain necessary.
