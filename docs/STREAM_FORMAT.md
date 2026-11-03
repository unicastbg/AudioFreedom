# DSP Stream Information

Protocol 1.10 adds optional read-only parameter 0x2004. Its 20-byte little-endian payload is:

| Offset | Type | Meaning |
| --- | --- | --- |
| 0 | uint32 | Publication sequence, zero means unavailable |
| 4 | uint32 | PCM callback sample rate in Hz |
| 8 | uint32 | PCM callback channel count |
| 12 | uint32 | Internal sample format: 1 = float32 |
| 16 | uint32 | Adapter I/O: 1 = float32, 2 = signed PCM16 |

Both adapters publish only after a successful, nonempty PCM callback. AIDL publication
is shared between effect instances so querying the app's control session does not report
that session's possibly unrelated configuration. Legacy publication is also shared in
its effect library. The latest callback wins when multiple streams run; this is not a
complete list of active streams. Independent HAL processes cannot share this in-memory
snapshot. Unsupported or inaccessible telemetry remains unavailable.

Publication uses lock-free 32-bit atomics and a bounded snapshot read. Audio callbacks
never wait for a reader or another publisher and may skip a contended telemetry update.
The app baselines the sequence on attachment/route change and requires an advancing
callback before showing data. An unchanged sequence expires after 1.5 seconds. Calls,
VoIP bypass, stopping the service, and disabling processing clear the displayed frame.
Older modules continue processing but do not provide this optional parameter.

The main screen explicitly labels this DSP, for example DSP: 44.1 kHz, 32-bit float, 2 ch.
The engine processes in float32 even when the legacy adapter receives 16-bit PCM. This
does not mean a 16-bit source gained information. It does not identify the music file's
original sample rate/bit depth, Bluetooth codec, negotiated radio bitrate, DAC output
format, or hardware capability. A 48 kHz stream is not evidence of hi-res source quality.

No resampler, automatic sound tuning, or measurement-assisted tuning is added here.
