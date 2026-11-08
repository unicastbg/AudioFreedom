# EQ Bandwidth and Stereo Routing

Local 1.1.0 build; not published. These controls require the bundled 1.1.0 driver.
Install the APK, update the bundled driver under Settings > Updates, then reboot.
The app disables the new controls when the installed module is older or staged.

## EQ Bandwidth

Each of the ten fixed-frequency peaking bands has an independent Q, from 0.30 to
5.00. Q 1.40 preserves the existing EQ bandwidth. Lower Q affects a broader range
of frequencies; higher Q is narrower. Q does not change the gain at that band's
center, but overlapping bands contribute to the combined response.

Choose a band, then adjust the bandwidth slider or tap the Q value for numeric
entry. The reset icon resets all bandwidths to 1.40 without resetting gains.
Applying a factory EQ preset restores its original bandwidths. EQ presets do
not change routing. The response graph models the combined filters at a 48 kHz
reference rate, without preamp or other effects; it is not a measured spectrum.
Old profiles default to 1.40. Saved profiles, live settings, assistant undo, and
A/B snapshots include the new controls.

## Stereo Routing

- Normal: original stereo, with optional left/right balance.
- Swap L/R: exchange channels, then apply balance.
- Mono: send (L + R) / 2 to both channels.
- Single output: send (L + R) / 2 to Left or Right, with the other side silent.

Balance attenuates the opposite channel; it never boosts either channel. Mono
mixing uses half gain on each input to avoid a 6 dB rise on centered material.
The mono sum can cancel out-of-phase content, as any mono downmix can.

Routing runs after EQ, bass, detail, immersive field and reverb, and before level
matching and the limiter. Spatial effects therefore cannot refill the muted
side. Routing is active only on verified front-left/front-right stereo streams;
mono, unknown layouts and multichannel PCM pass through routing unchanged.
This is software channel mapping, not a choice of physical speaker or BT device.
The app's output-device detection and automatic profiles work as before.

On the Xiaomi 15 Ultra / Android 16, system spatial audio was observed using a
12-channel spatializer stream even for stereo music on the phone speaker. The
stereo-only routing stage bypasses that stream. A temporary system spatial-audio-off
test changed live processing to two channels; enabling it again returned to twelve.
This does not claim verified physical speaker isolation through Xiaomi's renderer.
The app reports the non-stereo bypass when fresh stream telemetry is available.

Activity recreation (including rotation) resumes requested processing without
reapplying the output's saved profile, so unsaved manual edits are retained.

Turning off Stereo routing restores normal stereo and centered balance without
forgetting the selected mode. Hiding its section disables it, like other effects.

## Listening Check

Use stereo headphones at low volume. With a left/right identification track,
verify Normal and Swap. Select Mono, then Single output / Left and Right.
Test balance in Normal and Swap. Compare Q 0.30 and 5.00 with a single band raised
moderately, then reset bandwidth. Broad boosts can overlap strongly: lower the
preamp and retain output protection to manage headroom.
