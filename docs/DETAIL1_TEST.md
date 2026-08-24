# Detail1 device test

`0.5.0-detail1` adds a clean-room Detail Recovery stage after Dynamic Bass and before
the optional limiter. It separates a high-frequency detail band, compares fast and
slow envelopes to detect transients, makes one gain decision for all channels, and
reduces enhancement near the noise floor.

It does not reconstruct information deleted by lossy encoding. It selectively
emphasizes surviving high-frequency and transient cues, which can increase perceived
clarity without claiming to restore the original master.

## Installation

1. Leave Meta-OverlayFS uninstalled.
2. Install `AudioFreedom-xuanyuan-eea-0.5.0-detail1-directbind.zip` over the current
   AudioFreedom module and reboot.
3. Install `AudioFreedom-0.5.0-detail1-debug.apk`.
4. Confirm module status includes
   `dsp=preamp,10-band-eq,dynamic-bass,detail-recovery,linked-limiter`.

## Listening sequence

1. Enable Processing. Disable Equalizer, Dynamic bass, and Output protection.
2. Expand Detail recovery, choose `Clear`, and toggle the section while a familiar
   recording with cymbals, strings, or room ambience plays.
3. Compare `Gentle`, `Clear`, `Crisp`, and `Soft recordings`. Crisp should be the most
   obvious; Gentle should remain subtle.
4. Set Amount to `0%`. Toggling Detail recovery should become effectively neutral.
5. Set Amount to `70%`. Move Focus from `3000 Hz` to `10000 Hz`; the low setting should
   affect a broader presence/detail region, while the high setting should concentrate
   on air and upper harmonics.
6. Compare Transients at `0%` and `100%`. The higher setting should emphasize attacks
   more strongly relative to sustained brightness.
7. Re-enable the preferred EQ and Dynamic Bass presets one at a time. Keep Detail
   Recovery moderate when EQ already boosts 4-16 kHz.
8. Enable Output protection only after the tonal comparison if the combined processing
   creates peaks that need containment.

Listen for harsh consonants, brittle cymbals, hiss, and fatigue. Those indicate Amount
is too high or Focus is too low for that recording. The confirmed `directbind1` module
remains the rollback.
