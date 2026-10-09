# Conversational tuning: experimental

The supported assistant language is English only in the current local build. Other
languages are not offered after inconsistent phone testing. Recognition and spoken
replies are forced to English; multilingual acceptance work is deferred.

## Current local implementation

- Standard compound commands are evaluated as one atomic proposal. Higher bass and lower
  highs, or add bass and turn on reverb, no longer lose an understood clause.
- Unknown clauses reject the complete deterministic request; nothing is partially applied.
- The optional model is disabled by default. Enable it under Settings > Local assistant >
  Experimental features after installing a language model. Whisper voice recognition remains
  separately installable and does not need the LLM.
- The model emits up to four semantic goals and preservation groups. Application code
  computes bounded DSP changes and locks preserved controls. The LLM never edits PCM.
- Model proposals always require review, even with Apply small changes enabled.
- Explicit English preservation patterns are checked independently of the model.
  Unrecognized linguistic constraints still rely on model interpretation and user review.
- Partial bass undo requires an exact match with the last assistant-applied settings.
  Manual edits invalidate that conversational baseline, without deleting ordinary full undo.
- Feedback remains private prompt examples, not model-weight training or reliable proof of
  a learned preference. Model file removal now verifies deletion instead of silently ignoring failure.

## Supported intent vocabulary

Goals: bass_weight, bass_punch, warmth, body, vocal_presence, brightness, harshness,
detail, stereo_width, reverb, undo_half_bass.

Preservation groups: bass, mids, treble, detail, stereo, reverb. Shared effects can affect
perception across groups: these are control locks, not a perceptual invariance guarantee.
The compiler preserves preamp and limiter settings; it does not promise clipping-free
output or distortion-free physical devices. Positive EQ/effect gain still needs headroom.

## Try these

- Higher bass and lower highs.
- Add bass and turn on reverb.
- Bring the vocals forward, soften the cymbals, and keep the bass unchanged.
- More detail without more brightness.
- Undo half of the last bass change.

The expanded in-app Tuning examples and limits guide explains capabilities and limitations.
Review changes before applying. Use Undo if the result is worse. Keep volume moderate and
do not mistake a louder result for a better result.

## Model evaluation, not just protocol tests

Unit tests verify compilation of intent, numeric budgets, locks, and rejection. They do not
measure Qwen's understanding. Run the supported English requests on the device, recording:

- Every requested goal present, without an invented goal.
- Every preservation constraint present and actually unchanged in the compiled proposal.
- Negation and compound clauses handled correctly.
- Unrelated questions rejected, not converted into sound changes.
- Contextual rollback unavailable after manual edits.
- Cold/warm latency and phone memory consumption.

Broader languages need device/model evaluation before advertising reliable support. A
syntactically valid JSON object or model-generated confidence value is not an acceptance criterion.

## Recommendation follow-ups

1. Measurement-assisted diagnosis: add timestamped, route/session-labelled analyzer snapshots
   (band energy, RMS, peaks, crest factor, stereo correlation, limiter reduction). Keep this
   outside the sample callback and avoid analyzing microphone input instead of playback.
   Existing peak meters alone cannot establish muddiness, transducer distortion, or calibration.
2. A/B tuning: bounded candidate snapshots, explicit audition/restore, equal-loudness-aware
   comparisons, route-scoped preference examples. Do not infer preference after a fixed number
   of approvals or claim that model weights were trained.
3. Profile creation: explicit create/overwrite confirmation, duplicate-name handling, validated
   derived profiles, no silent route assignment. Existing Save profile remains available.
4. Troubleshooting: explanations grounded in fresh measurements and known DSP behavior;
   otherwise say the information is unavailable. No fabricated audio diagnosis.
5. Per-device refinement: separate physical device identity from broad route category;
   transfer no preference automatically between all Bluetooth devices or all speakers.
6. Other languages are deferred. Reintroduce a language only after reliable speech,
   command interpretation, and offline readback pass device acceptance checks.

Those follow-ups are not enabled features in this build. Native telemetry changes will
require a new driver module and device validation; the current build is app-only and local.
