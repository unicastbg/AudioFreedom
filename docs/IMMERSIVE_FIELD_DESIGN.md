# Immersive Field design

Immersive Field is the proposed clean-room AudioFreedom spatial stage. It must not use
Dolby, Atmos, DTS, THX, Creative, or related product names, code, assets, test vectors,
or branding.

## What the reference screenshots show

The Dolby Digital Live and DTS Connect screen is a real-time bitstream encoder intended
to carry multichannel audio over a constrained digital connection such as S/PDIF. An
encoder packages channels; it does not itself render virtual speakers around a headphone
listener. AudioFreedom does not need or plan to clone those encoders.

## Phase 1: static headphone renderer

Implemented initially in `0.6.0-immersive1` without HRTF datasets.

The first useful milestone can remain in the existing global insert effect and support
ordinary stereo playback:

- frequency-dependent mid/side control instead of full-band widening;
- center anchoring so vocals do not become hollow;
- short, decorrelated early reflections for externalization;
- optional virtual-speaker filtering using independently licensed HRTF data;
- strict mono compatibility and output-gain compensation;
- no head tracking and no claim of object-audio decoding.

This should be named `Immersive field` and expose Amount, Stage width, Center, and Room.
It can make stereo feel wider and less inside the head, but it is not equivalent to an
object-based Dolby Atmos soundtrack and renderer.

## Phase 2: Android spatializer backend

True multichannel-to-headphone rendering belongs in Android's dedicated Spatializer
path. Android routes a multichannel mix to a post-processing spatializer that outputs
stereo, and optionally supplies head-pose data. Supporting that path requires:

- a separate AIDL spatializer effect descriptor and implementation;
- audio-policy declarations for a dedicated spatializer output;
- static multichannel HRTF rendering before any head-tracking work;
- compatible headset sensors and low-latency pose transport for dynamic head tracking;
- per-OEM installation validation beyond the current Xiaomi insert-effect profile.

## Commercial boundary

AudioFreedom can implement generic spatial rendering from public DSP literature and
properly licensed HRTF measurements. It cannot market the result as Dolby Atmos or ship
Dolby technology without a Dolby agreement, implementation materials, testing, and
approval. A patent and dataset-license review is required before selling the spatial
renderer, even when the implementation is independently written.
