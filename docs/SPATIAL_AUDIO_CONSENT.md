# Temporary System Spatial-Audio Control

Stereo routing needs a verified two-channel DSP stream. Some Android spatializers
provide a multichannel bus even for stereo music played through phone speakers.
For example, Xiaomi 15 Ultra / HyperOS Android 16 used a 12-channel bus; disabling
system spatial audio temporarily returned the live DSP path to two channels.

## Consent

When non-neutral stereo routing is enabled, processing is active, live playback
has more than two channels, and system spatial audio is enabled, the main screen
offers a confirmation. It explicitly warns that this changes a system-wide setting
and affects other apps. Cancel does not change the system setting; routing stays
enabled but bypassed for the unsupported path. Use stereo playback reopens the prompt.

Consent lasts only for the current output and running routing session. It is not
remembered as permission for future app processes or different devices. Neutral
Normal mode with centered balance does not request spatial-audio changes.

## Ownership and Restoration

An app-bundled Java helper uses the existing root grant and AudioService's typed
spatializer API. It records an ownership journal before changing the setting. It
has no socket, exported service, PCM access, cloud connection, or arbitrary command
interface. Inputs are fixed operation names and the package's private no-backup path.

The helper restores the original enabled state when its parent closes the input
pipe, and has a heartbeat deadline as a second guard against app-process loss.
Processing off, stereo routing off or neutral, output changes and call-mode bypass
release control. A user re-enabling spatial audio ends the lease without re-disabling it.
If spatial audio was already off, the helper changes nothing and must not enable it later.

Recovery after a killed helper uses the private journal on the next service/app
startup. A failed restoration is surfaced explicitly, with a manual retry action.
Root or AudioService being unavailable can prevent immediate recovery; the UI does
not promise unconditional restoration under those conditions. Device boot or a hard
kill of every involved process may require reopening AudioFreedom to recover.

The controller checks that the actual PCM becomes stereo; turning off system spatial
audio does not guarantee a particular vendor's physical speaker mapping. Unsupported
layouts remain bypassed rather than having their channels guessed or discarded.
If no fresh stereo playback appears within ten seconds of acquiring control, the
controller releases it and restores system spatial audio instead of keeping an
ineffective global change.

This is an app-side feature. The matching 1.1.0 DSP module is still needed for
per-band Q and routing, but this helper itself does not require another module update.
