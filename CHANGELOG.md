# Changelog

## 1.0.0

- Fixed the Xperia LOS 24 processing suspension with an automatic media-session fallback
  and explicit disable-before-release cleanup. No matching driver reinstall is required.
- Added a short first-open update notice and release highlights in future update checks.
- Added network-access guidance when the updater cannot reach GitHub.

- Replaced long profile-button rows with a searchable, device-grouped profile browser in all themes.
- Added optional device groups when saving, per-output default selection, unsaved-edit
  confirmation, and complete assignment cleanup when deleting profiles.
- Added actual DSP stream rate, channel count, and internal float-format reporting with
  stale/idle handling. This is not a source bit-depth or Bluetooth codec claim.
- Added stable driver payload identities, same-version native update detection, and
  staged-module reboot status. The driver is versioned separately from the app.
- Validated positional front-left/right layouts before stereo spatial DSP, retained mono
  reverb, and made Detail Recovery transient detection consistent at quiet playback levels.

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
