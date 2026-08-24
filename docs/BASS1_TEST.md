# Bass1 device test

`0.4.0-bass1` adds AudioFreedom's first clean-room Dynamic Bass stage. It runs after
the equalizer and before the optional limiter. A linked bass envelope applies more
lift to quieter low-frequency detail and backs off when bass is already strong. The
same dynamics decision is used for every channel to preserve stereo balance.

The app now presents primary sound controls first:

1. Equalizer: preset, preset headroom preamp, and ten bands.
2. Dynamic bass: preset, strength, bass range, and dynamics.
3. Output protection: optional limiter and meters.

All three sections start collapsed. Disabling Equalizer now bypasses both its bands
and its preset preamp. Output protection no longer contains or owns Preamp.

## Installation

1. Leave Meta-OverlayFS uninstalled.
2. Install `AudioFreedom-xuanyuan-eea-0.4.0-bass1-directbind.zip` over the current
   AudioFreedom module and reboot.
3. Install `AudioFreedom-0.4.0-bass1-debug.apk`.
4. Confirm module status reports `direct_bind_mounts=3/3`, `direct_bind=active`,
   `effect=available`, and `dsp=preamp,10-band-eq,dynamic-bass,linked-limiter`.

## Listening sequence

1. Enable Processing. Disable Equalizer and Output protection.
2. Expand Dynamic bass, choose `Full-size headphones`, and toggle Dynamic bass while
   a familiar bass-rich track plays. Bass weight should change without a matching
   jump in upper midrange or treble.
3. Compare `Natural`, `Full-size headphones`, `Deep extension`, and
   `Compact headphones`. Compact should reach higher into upper bass; Deep extension
   should concentrate lower.
4. With a preset selected, move Strength from `0.0 dB` to `+12.0 dB`. At `0.0 dB`,
   toggling Dynamic bass should be effectively neutral.
5. Set Strength to `+6.0 dB`. Move Bass range from `40 Hz` to `160 Hz`; the higher
   setting should affect substantially more upper-bass content.
6. Compare Dynamics at `0%` and `100%`. At `0%`, boost is fixed. At `100%`, strong
   bass hits should receive less additional boost than quieter bass detail.
7. Disable Dynamic bass, enable a non-flat EQ preset, then toggle Equalizer. Turning
   EQ off should now restore unity gain rather than retaining its negative preamp.
8. Leave Output protection off for the first comparisons. Enable it afterward only
   to check that very strong combined boosts are contained and meters still respond.

Bass1 has no harmonic synthesis or sub-bass reconstruction; those are separate future
experiments. Bluetooth, USB DAC, offloaded playback, calls, and other OEM devices remain
separate acceptance tests. The confirmed `directbind1` module remains the rollback.
