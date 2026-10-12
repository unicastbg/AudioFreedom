# AudioFreedom Command Guide

AudioFreedom accepts the same command language by typing or push-to-talk. Commands are
case-insensitive, and punctuation is optional. These standard phrases use the built-in
deterministic controller and do not depend on the language model guessing an intent.

The supported assistant language is **English only**. Recognition, spoken replies, help,
and conversational-tuning examples use English. Other languages are not supported in this
version following inconsistent device testing. Previous Automatic/Bulgarian selections
are migrated to English. Existing model packs can stay installed; no new download is needed.

With **Pause media while listening** enabled, AudioFreedom asks the current player to pause for
voice capture. With **Apply after recognition** and **Speak recognized command** enabled, it
reads back the validated DSP action using an installed offline system voice, applies the safe
change, and then returns audio focus to the player.

The **AudioFreedom voice** home-screen widget opens a compact voice panel. It listens until you
finish speaking, shows the interpreted action, and offers Apply or Undo without opening the main
app. Once the command or spoken reply finishes, tap the microphone circle to issue another
command in the same panel. Starting a new command from the review screen discards the
unconfirmed proposal without applying it. Recording, interpretation, readback, and undo
cannot overlap a new command.

## Hands-free voice (local test build)

In Settings > Local assistant, enable the assistant and installed voice pack, then select
Volume button. Enable AudioFreedom volume shortcut in Android Accessibility settings.
Choose Volume Up or Volume Down and a hold duration from 0.5 to 3 seconds. Hold that
button and wait for `I'm listening` before giving a command. A short press still adjusts volume.

The app requests an audio-focus pause from the player, captures the command until the
configured speech pause, reads back the proposed change, and asks `Do you confirm?`.
Only a clear `yes` applies it. `No`, an unclear response, or a timeout cancels it. Audio
focus is returned to the player after either outcome; automatic playback resumption
depends on the player. `Undo` follows the same confirmation flow.

An installed offline English system TTS voice is required for readback. Recognition is
forced to English, with no automatic language detection or alternate-language retry.

End-of-command pause defaults to one second and can be set from 0.5 to 3 seconds. Waiting
for the start of speech and waiting for confirmation both default to eight seconds and
can be set from 3 to 15 seconds. A recording is capped at 20 seconds and a conversation
at two minutes. Silero VAD identifies speech activity; it is not speaker identification
and cannot reliably separate your voice from other nearby voices or sung vocals.

The microphone stays off between commands. During a command, a microphone foreground
notification offers Cancel command. Calls interrupt the session, and manual voice input
also cancels it. No continuous wake-phrase recognition runs. Recorded speech stays in
memory and is not saved or uploaded. Hardware button delivery, screen-off activation,
Bluetooth microphone routing, and conflicts with other key-filter services require device testing.

## Help and Cancellation

Say `Cancel`, `Stop listening`, or `Never mind` while giving a command. AudioFreedom
acknowledges and ends the volume-button session without changing DSP settings or undo history.
`Undo` still means restore the last assistant sound change; it is not cancellation.
Cancellation phrases also work when answering the confirmation question.

Say `Help`, `What can you do?`, `Bass help`, `Equalizer help`, `Profiles help`, `Effects help`,
`Reverb help`, `Immersive field help`, `Detail recovery help`, or `Undo help`.
Help is brief, English-only, deterministic, and does not use the LLM
or change any settings. Say `Music help` for the optional Folder Player handoff.

After a volume-button help reply, the app asks for a command or cancellation and waits using
the configured speech timeout. Up to three help replies are allowed in one session; the
existing two-minute total session limit and call interruption remain in force. During
speech playback the microphone is not recording, so use the button to interrupt a reply.

Press the configured volume button again to stop an active volume-button session immediately,
including speech playback, recording, or command interpretation. The interrupting press,
its repeats, and its release are consumed without changing volume or starting another
session. The original held key's repeats/release do not cancel the session it just started.
While idle, short presses still adjust volume and holds still activate the assistant.

Typed commands and the home-screen voice panel also accept help and cancel. The panel shows
the information without an Apply action; its Close button stops playback. Manual readback
respects the Speak recognized command setting.

## Folder Player Music Commands

Enable **Pass music commands to Folder Player** in Local assistant settings. This is a
one-way handoff: AudioFreedom recognizes speech or accepts typed commands and sends music
requests to Folder Player. Folder Player cannot invoke AudioFreedom's microphone.

Examples: `Play trance`, `Play The Prodigy`, `Shuffle drum and bass`, `Next track`,
`Previous track`, `Pause music`, `Resume`, and `Stop music`. Say `Music help` for a short
summary. `Cancel`, bare `Stop`, and `Stop listening` still cancel the assistant session;
use `Stop music` to stop playback.

Explicit music requests bypass the DSP planner and Qwen, even if an artist or song name
contains words such as Muddy, Surround, or Clearer. No library index or media metadata is
sent to a language model. Music commands execute immediately rather than asking for DSP
confirmation, and do not create a DSP undo snapshot. Sound and music requests cannot be
combined in one sentence in this version.

Both apps need compatible bridge versions and matching signing certificates. An installed
but incompatible or differently signed player is reported explicitly. APKs signed by a
private repository may not match GitHub/development builds; do not uninstall an app merely
to resolve this without first backing up its data and checking the certificate.

Listening focus is released before forwarding. When spoken replies are enabled, readback
briefly requests ducking focus and releases it afterward. An offline English Android TTS
voice is required. Missing voices are reported rather than silently ignored.

Requests have an elapsed-realtime deadline and unique ID. The receiver checks the deadline
after its library lookup and rejects duplicates. If a reply is lost, AudioFreedom reports
that playback is unconfirmed, not that execution definitely failed. Check playback before
retrying. No cloud service or continuous microphone is used.

## Offline Spoken Replies

Settings > Local assistant > Spoken replies lists English offline voices.
Automatic voice prefers an installed offline English voice, then higher
engine-reported quality and lower latency. A manual choice overrides that ranking. If a saved
voice disappears or the engine changes, the app uses an available offline voice in the same
language. It never substitutes an online or foreign-language voice.

Use Test voice to hear the selected English voice without installing the recognition model or
changing DSP settings. Android speech settings opens the system engine/voice installation
screen; return to the app or use Refresh voices after installing a voice. These system TTS
voices are separate from AudioFreedom's speech-recognition language pack and LLM.

The app separately checks whether any enabled Android TTS engine is installed. Missing
engines, engine initialization failures, and missing offline voices have distinct warnings.
If no matching offline voice is installed, the settings show a warning. A volume-button
session reports the missing language, displays a toast, and cancels before recording or
applying a command that it cannot read back. Manual/widget commands report readback failures
on screen. Selecting a higher-rated voice cannot guarantee better pronunciation: if the
engine only supplies one poor English voice, install a better offline-capable engine.

## Adjust Sound

Simple commands can be combined: `Increase bass and treble`, `Higher bass and lower highs`, or `Add bass and turn on
reverb`. The deterministic controller evaluates the complete compound request before
proposing one change. Unknown clauses reject the request rather than partially applying it.
Bare known targets inherit the most recent explicit increase/decrease direction:
`Increase bass and lower mids and treble` increases bass and reduces both mids and treble.

Optional descriptive requests are under Settings > Local assistant > Experimental features.
Install a language model and enable Conversational tuning. Expand Tuning examples and limits
for supported goals, preservation, contextual undo, and features that are not available yet.
Every model proposal requires review. See [the experimental guide](CONVERSATIONAL_TUNING.md).

Use an action followed by a sound target:

```text
Add <target>
Increase <target>
Boost <target>
Reduce <target>
Lower <target>
Remove <target>
```

Supported targets:

| Target | What AudioFreedom changes |
| --- | --- |
| Bass | Bass Foundation and the 31, 62, and 125 Hz EQ bands |
| Treble | The 4, 8, and 16 kHz EQ bands |
| Mids / Midrange | The 500 Hz, 1 kHz, and 2 kHz EQ bands |
| Vocals | Vocal presence around 1, 2, and 4 kHz |
| Detail / Clarity | Detail Recovery amount and transients |
| Width / Stage | Immersive Field amount and width |
| Reverb / Echo | Reverb amount, space, and decay |

Examples:

```text
Add bass
Increase mids
Boost treble
Clear vocals
Add detail
Increase width
Add echo
Reduce reverb
```

Every adjustment remains bounded by the Conservative or Balanced safety setting. Positive
EQ changes reserve preamp headroom automatically.

## Toggle Effects

Use `Turn on`, `Enable`, `Turn off`, or `Disable` with one of these effect names:

```text
Equalizer / EQ
Bass Foundation / Dynamic Bass / Bass Boost
Detail Recovery / Crystalizer
Immersive Field / Surround / Spatial Sound
Reverb / Echo
Output Protection / Limiter
```

Examples:

```text
Turn on reverb
Disable equalizer
Enable immersive field
Turn off output protection
```

## Profiles And Undo

In the local Volume Leveler development build, **Turn on volume leveler**, **Turn off
volume leveler**, and **Volume leveler help** are also supported. Gentle, Balanced, and
Night presets are selected in the app. Install the matching new module before enabling it.

Say the saved profile name exactly as it appears in AudioFreedom:

```text
Switch profile to <profile name>
Load profile <profile name>
Use <profile name>
Undo
Restore previous settings
```

Profile changes always present a preview before replacing the current DSP settings.
English profile names are recommended for voice commands. Creating or saving a new
profile by voice is not supported; save it in the app first.
