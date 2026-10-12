# One-way Music Command Bridge

Available in AudioFreedom 0.9.7-beta with a compatible Media Player build. AudioFreedom sends recognized or typed music
requests to Media Player's MediaSession. It does not expose a microphone API to the
player. Media Player retains its own optional recognition for standalone use.

## Routing and Replies

Explicit play/shuffle requests and supported transport phrases are routed before DSP
interpretation. Neither the deterministic DSP planner nor Qwen receives these music
requests. When forwarding is disabled or unavailable, report that state without falling
through to a sound adjustment. Bare Stop remains assistant cancellation; Stop music is
a transport command. Music requests execute immediately, unlike confirmed DSP changes.

The shared handler releases exclusive listening focus before forwarding but retains the
manual voice-session guard until the caller finishes. Optional readback requests brief
ducking focus and releases it in a finally block. All three entry points use the same
handler: main screen, widget, and volume-button service.

## Session Protocol

Player package: `com.svetlio.mediaplayer`.
Service: `com.svetlio.mediaplayer.playback.PlaybackService`.

Custom commands:

- `com.svetlio.mediaplayer.VOICE_COMMAND`
- `com.svetlio.mediaplayer.CANCEL_VOICE_COMMAND`

The receiver advertises `protocol_version=1` in the voice command's custom extras.
The sender requires this capability before sending any transcript to an older receiver.
The request contains `text`, `protocol_version`, a UUID `request_id`, and
`deadline_elapsed_ms` measured by SystemClock.elapsedRealtime on the same device.
The client deadline is five seconds; its response timeout is six seconds.

The reply echoes `request_id` and contains `handled` and `reply` on success, or an `error`
such as expired, duplicate, invalid, or failed. Check the SessionResult result code as
well as the extras. An uncertain response timeout must not claim that execution never
occurred; playback may already have changed before a reply was lost.

The receiver reserves IDs before library lookup, retains them for a bounded time, and
rejects duplicate IDs rather than repeating side effects. It checks expiry after lookup
and again before applying a prepared music queue. Cancellation attempts to cancel the
receiver job before releasing the controller. Cancellation cannot undo an action already
executed. The sender retains the controller construction future and always releases it
using MediaController.releaseFuture, including cancellation/connection races.

## Authentication

Both APKs must be signed with the same certificate. AudioFreedom checks the installed
player certificate before sending text. Media Player verifies the session caller UID's
signer both when granting and when executing the custom commands. No package-name-only
authorization, root installation, or cloud service is used.

The local test pair matches AudioFreedom's existing release certificate. A private
repository may re-sign either APK with a different key; validate both final distributed
APKs rather than assuming that using the same repository guarantees a match. Do not
uninstall an existing app to bypass a signature conflict without first protecting its data.

## Validation

AudioFreedom unit tests cover routing collisions, local cancellation, disabled forwarding,
distinct failure descriptions, and help. MediaPlayer unit tests cover transport aliases,
request admission, duplicate IDs, expiry after a slow lookup, cancellation, and capacity.

The Android instrumentation suite connects to the real receiver and tests unavailable
player handling, certificate matching, transport replies, DSP-like music names, unsupported
requests, malformed/expired requests, duplicate rejection, cancellation/reconnection,
disabled forwarding, and real Android focus return with retained voice ownership.
Run the missing-player case before installing the matching player test APK, then run the
remaining cases with it installed. Scope adb commands to a dedicated emulator serial.

Device testing is still needed for recognition of actual artist names, audible TTS while
music plays, Bluetooth routing, volume-button activation, and playback from a populated
library when Media Player has no UI open. The emulator suite does not replace those tests.
