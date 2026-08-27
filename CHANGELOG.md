# Changelog

## 0.9.2 Beta

- Show the active speaker, wired, Bluetooth, USB, or HDMI route in the processing
  notification using a dedicated icon and route-specific accent color.
- Keep Android's required status-bar notification glyph monochrome while allowing the
  expanded notification to use its route accent where the system UI supports it.
- Avoid reposting the foreground notification when profiles or DSP settings change.
- Update the notification only for visible route, call-state, or display-preference changes.

The universal audio-stack module is unchanged from version 0.9.0.
