# App and Driver Updates

## First Installation

The APK includes the universal AudioFreedom driver ZIP. With a rooted Android 15+ device
and a compatible root-module manager:

1. Install the APK and open AudioFreedom.
2. Go to Settings > Updates > Driver module and select Install.
3. Confirm installation, grant root access when prompted, and wait for success.
4. Reboot the phone, reopen AudioFreedom, and enable processing once the driver is detected.

No separate ZIP download is required. Installing the APK alone does not install or
activate the root module. If the in-app installer cannot use your root manager, install
the universal ZIP from the same GitHub release manually in that manager and reboot.

## Checking and Installing Updates

Settings > Updates > Check updates checks both the companion app and the driver module.

Release highlights are intentionally short. Starting with 1.0.0, the updater shows
up to three plain-text bullets from the trusted release body's `## Highlights` section.
The first launch of 1.0.0 also shows its three highlights once, including the LOS 24
fix, so an upgrade from a build without the new updater UI still sees the changes.

For an available app update:

1. Select Download. Android's Download Manager downloads the APK in the background.
2. AudioFreedom verifies the release's SHA-256 digest, package identity, version code,
   and the same signing certificate as the installed app. It also checks that the APK
   contains a driver ZIP matching its bundled-driver metadata.
3. Select Install. If needed, Android asks you to allow installs from AudioFreedom.
4. Confirm installation in Android's package installer.

The APK includes the driver's ZIP for offline installation, so the new app brings its
matching driver package. The driver may have its own version when no native changes are
needed. Installing the APK does not silently install or activate a root module. After
updating the app, use the driver module's Install or Update action if offered, then reboot.

The updater never installs APKs using root and never bypasses Android's confirmation.
Existing settings and profiles are retained by a normal signed upgrade.

## Driver Build Identity

Local builds now include a SHA-256 payload identity in module.prop and the APK's bundled
metadata. It is derived from packaged file contents, not ZIP timestamps. Repackaging the
same inputs keeps the identity; changing a native backend or installer changes it.
Settings shows its short build ID beside the visible driver version.

The app offers Update if the bundled version is newer or the same version has a different
payload identity. A legacy module without an ID needs one matching update. An identical
build offers Reinstall for repair. A newer installed version is not silently downgraded.
App-only changes that retain the driver payload do not require reinstalling it.

The root probe checks staged modules before active ones, so an external module-manager
installation pending activation is shown as requiring a reboot. Successful in-app installs
retain their restart notice until the boot count changes. Restart always requires explicit
confirmation. The one-time main-screen notice is keyed to payload ID, not just version.
GitHub update discovery remains version-based; it does not treat a republished same-version
remote archive as a new release.

Downloads continue if the Settings page is closed, and pending state is restored on return.
Use the cancel icon to remove a pending update. If the release lacks a trusted APK,
valid file digest, or acceptable size, View on GitHub remains available for manual download.
Only the AudioFreedom repository's canonical release APK is eligible for direct download.

GitHub checks and downloads require internet access. If the updater can't reach GitHub,
check your connection and use **App info** to review AudioFreedom's network access.
Some ROMs, including LineageOS, can block network access for an individual app.
No audio, assistant commands, profiles, or other app settings are sent as part of
the update check.

Android references: [DownloadManager](https://developer.android.com/reference/android/app/DownloadManager),
[FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider),
[package-install permission](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()).
