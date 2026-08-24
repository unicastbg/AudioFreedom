# Immersive1 device test

`0.6.0-immersive1` adds a clean-room static stereo headphone renderer. It combines
frequency-dependent mid/side staging, center anchoring, delayed low-passed crossfeed,
and asymmetric early reflections. The immediate dry path is not delayed, and the stage
bypasses non-stereo processing contexts.

This is not Dolby Atmos, object-audio decoding, Android Spatializer integration, or
head tracking. It is a system-wide enhancement for ordinary stereo playback.

## Installation

1. Leave Meta-OverlayFS uninstalled.
2. Install `AudioFreedom-xuanyuan-eea-0.6.0-immersive1-directbind.zip` over the current
   AudioFreedom module and reboot.
3. Install `AudioFreedom-0.6.0-immersive1-debug.apk`.
4. Confirm module status includes
   `dsp=preamp,10-band-eq,dynamic-bass,detail-recovery,immersive-field,linked-limiter`.

## Listening sequence

1. Enable Processing. Disable Equalizer, Dynamic bass, Detail recovery, and Output
   protection so Immersive field is isolated.
2. Use headphones and a familiar stereo recording with a stable center vocal. Choose
   `Natural stage`, then toggle Immersive field.
3. Compare `Natural stage`, `Wide music`, `Cinema`, and `Front stage`. Listen for width,
   vocal location, perceived distance, and whether the sound moves outside the head.
4. Set Amount to `0%`. Toggling Immersive field should become exactly neutral.
5. Set Amount to `70%`, Room to `0%`, and move Stage width from `0%` to `100%`.
   High-frequency stereo ambience should expand more than bass.
6. Move Center from `0%` to `100%`. The center vocal should become progressively more
   anchored without shifting left or right.
7. Set Stage width to `50%` and compare Room at `0%` and `100%`. Higher Room should add
   distance and externalization; reject settings that sound echoey or hollow.
8. Test a mono recording. Left and right must remain balanced; Room may still create a
   small stereo ambience when enabled.
9. Re-enable preferred EQ, bass, and detail settings one at a time. Enable Output
   protection last only if the combined graph creates peaks that need containment.

The most useful feedback is whether vocals remain solid, bass remains centered, the
stage extends beyond the earcups, and any preset causes comb-filtered, phasey, hollow,
or fatiguing sound. The confirmed `directbind1` module remains the rollback.
