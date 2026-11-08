# Profile Browser

The main-screen profile selector opens a searchable sheet in every theme. Search matches
both profile names and group names. Groups can be collapsed. The active profile has a check mark; unsaved
adjustments are marked Modified. Switching away from modified settings asks before
discarding them. Save updates a selected profile; Add opens the save-as-new dialog.

The save dialog separates Update existing from Save as new. Its Group selector lists
existing groups and also accepts a new name. A group is optional; profiles without a
group appear under Ungrouped. Names can be reused in different groups.

## Manage Profiles

Open Manage profiles beside the main profile selector, or Manage inside the picker.
This page organises saved profiles without loading them or saving the current sound.

- Select several profiles, then Move to group. Choose a group, enter a new one,
  or choose Ungrouped. Select all results applies to the current search.
- Each profile has Rename, Move to group, Duplicate and Delete actions.
- Each named group has Select group, Rename group and Remove group actions.
- Removing a group moves its profiles to Ungrouped; it does not delete them.
- Moving or renaming preserves profile IDs, DSP snapshots and device assignments.
- Duplicate creates a new profile with the same saved DSP, not the current live edits,
  and does not inherit automatic device assignments.
- Name collisions block a move or rename before anything is written. Rename one of
  the conflicting profiles first; nothing is silently replaced or merged.
- Deleting saved profiles requires confirmation and retains current sound settings.

Groups can describe devices, genres or any other category. Empty named groups are not
stored independently; they disappear when their last profile is moved or deleted.

The checkbox beside a profile sets or clears the automatic default for the currently connected
output. This does not load that profile immediately or overwrite current adjustments.
Enable Settings > Device profiles > Automatic profiles to use the default on connection.
There is one optional default per detected output, not one mandatory profile per group.
Groups organize the browser; they do not change Android's detected route identity.
If a device does not expose a unique name, Android route detection can still conflate
identically named outputs. Do not assume group names identify physical hardware.

Deleting a profile removes its defaults on all routes but keeps current sound settings.
For voice selection, a unique profile name still works. When a name is reused, qualify it:
Load profile Sony Amp Clear. Ambiguous names are not resolved to an arbitrary device.

These changes are local and have not been published as a new release.
