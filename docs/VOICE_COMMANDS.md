# AudioFreedom Command Guide

AudioFreedom accepts the same command language by typing or push-to-talk. Commands are
case-insensitive, and punctuation is optional. These standard phrases use the built-in
deterministic controller and do not depend on the language model guessing an intent.

With **Pause media while listening** enabled, AudioFreedom asks the current player to pause for
voice capture. With **Apply after recognition** and **Speak recognized command** enabled, it
reads back the validated DSP action using an installed offline system voice, applies the safe
change, and then returns audio focus to the player.

The **AudioFreedom voice** home-screen widget opens a compact voice panel. It listens until you
finish speaking, shows the interpreted action, and offers Apply or Undo without opening the main
app.

## Adjust Sound

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

Say the saved profile name exactly as it appears in AudioFreedom:

```text
Switch profile to <profile name>
Load profile <profile name>
Use <profile name>
Undo
Restore previous settings
```

Profile changes always present a preview before replacing the current DSP settings.

## Bulgarian

For short Bulgarian voice commands, select **Bulgarian** as the command language for the
most consistent Cyrillic transcription. Automatic mode retries unsupported phrases in
Bulgarian, but may take longer.

Standard Bulgarian actions include:

```text
Добави <цел>
Увеличи <цел>
Засили <цел>
Намали <цел>
Махни <цел>
Включи <ефект>
Изключи <ефект>
Зареди профил <име>
Отмени
```

Common targets include `бас`, `високи`, `среди`, `вокали`, `детайл`, `ехо`, and
`реверберация`.
