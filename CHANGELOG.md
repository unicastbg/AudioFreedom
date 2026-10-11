# Changelog

## 0.9.6 Beta

- Added optional Volume Leveler with Gentle, Balanced, and Night presets and explanatory descriptions.
- Added on-demand playback analysis: input/output RMS, crest factor, stereo correlation,
  near-full-scale samples, and a ten-band output spectrum. No audio is recorded or uploaded.
- Added route-specific A/B snapshots with explicit Store A/Store B controls and persistent comparisons.
- Fixed Store B layout and selecting identical snapshots.
- Added Settings > Appearance > Main page functions. All sections are shown by default;
  hiding an effect disables it while preserving its slider values. Profiles and assistant
  changes cannot re-enable hidden effects.
- Fixed the voice widget microphone so another command can be issued without closing the panel.
- Added Volume Leveler assistant commands and included leveling settings in profiles and undo.

Update both the companion app and universal driver module. Reboot after updating the driver.

## 0.9.3 Beta

- Added an Updates section to Settings with an explicit GitHub release check.
- Show the installed version and clear checking, up-to-date, available, and retry states.
- Open an available update on its verified GitHub release page; the app does not silently
  download or install packages.
- Compare semantic versions correctly across beta and stable release tags.

The universal audio-stack module is unchanged from version 0.9.0.

## 0.9.2 Beta

- Show the active speaker, wired, Bluetooth, USB, or HDMI route in the processing
  notification using a dedicated icon and route-specific accent color.
- Keep Android's required status-bar notification glyph monochrome while allowing the
  expanded notification to use its route accent where the system UI supports it.
- Avoid reposting the foreground notification when profiles or DSP settings change.
- Update the notification only for visible route, call-state, or display-preference changes.

The universal audio-stack module is unchanged from version 0.9.0.
