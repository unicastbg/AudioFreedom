# Profile Browser

The main-screen profile selector opens a searchable sheet in every theme. Search matches
both profile names and device-group names. The active profile has a check mark; unsaved
adjustments are marked Modified. Switching away from modified settings asks before
discarding them. Save updates a selected profile; Add opens the save-as-new dialog.

The save dialog includes an optional Device group, for example Sony Amp, Buds, or Speakers.
Several sound profiles can share a group, and separate groups can reuse a profile name.
An existing profile can be moved by changing its group while saving. Empty groups appear
under Ungrouped. Existing IDs, DSP snapshots, and automatic route assignments are retained;
there is no destructive migration or reset of current adjustments.

The checkbox beside a profile sets or clears the automatic default for the currently connected
output. This does not load that profile immediately or overwrite current adjustments.
Enable Settings > Device profiles > Automatic profiles to use the default on connection.
There is one optional default per detected output, not one mandatory profile per group.
Device groups organize the browser; they do not change Android's detected route identity.
If a device does not expose a unique name, Android route detection can still conflate
identically named outputs. Do not assume group names identify physical hardware.

Deleting a profile removes its defaults on all routes but keeps current sound settings.
For voice selection, a unique profile name still works. When a name is reused, qualify it:
Load profile Sony Amp Clear. Ambiguous names are not resolved to an arbitrary device.

These changes are local and have not been published as a new release.
