# Volume-button voice acceptance checks

This is an app-only local test build. Keep the existing AudioFreedom driver module.
The display version remains `0.9.4-beta`; nothing has been published to GitHub.

## Preparation

1. Install the local APK over the existing app.
2. In Settings > Local assistant, enable the assistant and installed voice pack.
3. Use Test offline voice to verify an offline system TTS voice is available.
4. Select Volume button and grant microphone permission.
5. Choose Volume Up or Volume Down and a hold duration. Tap Enable button shortcut
   and explicitly enable AudioFreedom volume shortcut in Android Accessibility settings.

## Conversation

1. Play music at moderate volume, preferably through headphones initially.
2. Hold the selected button. Wait for I'm listening and playback to pause.
3. Say Add bass, then pause for the configured end-of-command interval.
4. Verify spoken readback and the confirmation question.
5. Say Yes and pause. Verify one change, microphone shutdown, and playback resumption
   if the player supports it.
6. Repeat with Reduce treble and No: settings must not change.
7. Repeat with Undo the last change and Yes: previous settings must return.
8. Test Load profile followed by a saved profile name.

## Timing, privacy, and cancellation

- A short press adjusts volume once. A long hold activates once, without repeated triggers.
- The unselected volume button behaves normally.
- Try 0.5-second and 3-second hold durations independently of speech timing.
- End-of-command pause, wait for speech, and wait for confirmation remain adjustable.
- No speech, unclear confirmation, or timeout must cancel without applying changes.
- Manually change a DSP setting during confirmation: the stale proposal must be rejected.
- Start widget/manual voice capture during a button session: it must cancel the old session.
- Receive/start a call during capture, readback, or confirmation: microphone and speech stop,
  no pending change applies, and volume controls remain available for the call.
- Cancel from the notification. The microphone notification and privacy indicator disappear.
- Between commands, no microphone recording or speech recognition runs.
- Disable the shortcut in Accessibility settings: subsequent holds behave normally.
- Test locked/screen-off operation and other Accessibility services that filter hardware keys.
- Test Bulgarian with an offline Bulgarian system voice and the Bulgarian language selected.
- Test speaker, wired, Bluetooth, background noise, and which microphone Android selects.

Unit tests cover tap/hold timing, one-shot activation, cancellation, unrelated devices,
confirmation parsing, and speech endpoint detection. Real hardware button delivery,
microphone routing, lock-screen, playback, and call checks require a phone.
