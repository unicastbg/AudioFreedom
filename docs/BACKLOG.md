# AudioFreedom Backlog

## LOS 24 Compatibility Fix

- Xperia 1 III / Android 17: recreating the session-0 effect during an existing music
  session can leave it framework-suspended while the app still says processing is on.
  Root, installation, and library hash were verified healthy. Closing the player,
  enabling AudioFreedom, then reopening the player restored sustained processing.
- The 1.0.0 app automatically switches to active media sessions when its global
  handle remains suspended. It disables effects before releasing them, so an engine
  retained by Android does not keep processing after the toggle is off.
- Xperia speaker acceptance passed three toggles during continuous playback and live
  -12/-6 dB gain changes. Real calls, Bluetooth, other ROMs, and offload remain unverified.
- See [LOS 24 suspension diagnosis](LOS24_SUSPENSION.md) and [1.0.0 notes](releases/1.0.0.md).

## Local DSP Fixes Awaiting Phone Validation

- Verify matching positional front-left/right channel layouts in AIDL and legacy adapters.
  Unknown/indexed layouts bypass stereo spatial stages, not the other DSP controls.
- Make Detail Recovery transient detection relative to programme level, with quiet-volume,
  sample-rate, silence, and block-boundary regression coverage.
- Test the updated native module on speaker and Bluetooth routes before publishing it.

## Implemented Locally, Awaiting Phone Acceptance

1. Searchable, device-grouped profile sheet in every theme, active/modified status, guarded
   switching, optional per-output default, existing-profile preservation, and cleanup of
   all route assignments when deleting a profile. See [Profile browser](PROFILE_BROWSER.md).
2. Actual callback-rate/internal float format display, optional protocol 1.10 telemetry,
   coherent shared publication, stale-data expiry, and call/route/service resets. See
   [DSP stream information](STREAM_FORMAT.md). This does not resample or infer source quality.
3. Content-based driver build ID, same-version update detection, staged-module reboot
   detection, no automatic downgrade, and one-time notices keyed to payload identity.
   See [App and driver updates](APP_UPDATES.md).
4. Expanded native, unit, persistence, and all-theme UI regression coverage. The physical
   matrix is in [Route acceptance](ROUTE_ACCEPTANCE.md). Wired, USB, call/VoIP, idle/resume,
   output changes, and offload checks remain pending on actual phones.

No GitHub publishing is part of this local backlog pass. Favorites/Recent shortcuts are
optional future polish; the searchable list handles large collections without them.

## Later Experiments

- Measurement-assisted tuning and explanations grounded in fresh, route-labelled playback
  analysis. An electrical spectrum does not measure headphone response or physical distortion.
- Optional loudness-matched A/B audition and route-scoped preference feedback. Manual A/B
  snapshots already exist; this is an enhancement, not a replacement.
- Profile creation from intent with explicit create/overwrite confirmation.
- Track-end/deferred profile changes through a media-session-aware integration.
- Revisit non-English assistant support only after device acceptance tests are reliable.

The LLM remains optional and local. No always-listening wake phrase is planned. See
[Conversational tuning](CONVERSATIONAL_TUNING.md) for experimental assistant boundaries.
