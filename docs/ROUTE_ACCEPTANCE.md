# Route and Lifecycle Acceptance

## Automated Coverage

Native suites cover gain, spatial-layout gating, mono reverb, multichannel front-pair
processing, Detail Recovery across quiet/loud levels and 44.1/48/96/192 kHz, silence,
block boundaries, and concurrent stream-publication consistency.

App tests cover profile grouping/search, old-profile compatibility, same-name groups,
route-default persistence/deletion, unsaved-edit switching, all theme selectors, driver
payload identity, staged/reboot state, optional stream-protocol validation and expiry,
mode bypass policy, and automatic-profile opt-in/route-change conditions.

These tests do not prove that a vendor routes speaker, wired, USB, Bluetooth, or call PCM
through the effect. The emulator is not a substitute for that check.

## Physical Device Checklist

Record device, ROM/build, Android version, selected backend, driver build ID, source app,
and output route. Use safe playback volume; start with a cut rather than a boost.

| Scenario | Procedure and expected result | Current local build |
| --- | --- | --- |
| Speaker | Enable EQ, preamp -12 dB. Toggle and adjust repeatedly without restarting playback. | Not tested |
| Wired headphones | Repeat gain test, reconnect, check route and chosen default. | Not tested |
| USB DAC | Repeat at device-supported 44.1/48/96 kHz; compare displayed DSP rate with diagnostics. | Not tested |
| Bluetooth | Test available codecs; verify audible gain changes, not only a Processing label. | Not tested |
| Idle/resume | Stop music, lock for 5 minutes, resume; live controls work without app restart. | Not tested |
| Route changes | Speaker to Bluetooth to USB/wired and back during playback; stale format disappears, correct default applies once. | Not tested |
| Manual edits | Change EQ after a default loads. Refresh/apply-settings must not restore the saved snapshot. | Not tested |
| Calls | Incoming/outgoing phone call: DSP detaches, microphone assistant suspends, voice audio remains normal; playback DSP returns afterward. | Not tested |
| VoIP | Repeat with a communication-mode VoIP call, including headset connect/disconnect. | Not tested |
| Source switching | Local player, browser, streaming app; parameters remain live. | Not tested |
| Offload | Compare software playback with compressed/Bluetooth hardware offload where selectable; report bypass explicitly. | Not tested |
| Root loss | Deny root, refresh/install: clear error, no silent installation success. Restore and retry. | Not tested |
| Module update | Install matching bundled build, observe reboot offer, reboot, confirm matching ID and no repeated update notice. | Not tested |

Do not change security/attestation settings or force global offload changes just to make
an acceptance result pass. Capture diagnostic evidence for bypassed routes and keep the
existing known-good module available for rollback.
