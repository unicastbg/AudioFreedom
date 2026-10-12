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

Downloads continue if the Settings page is closed, and pending state is restored on return.
Use the cancel icon to remove a pending update. If the release lacks a trusted APK,
valid file digest, or acceptable size, View on GitHub remains available for manual download.
Only the AudioFreedom repository's canonical release APK is eligible for direct download.

GitHub checks and downloads require internet access. No audio, assistant commands,
profiles, or other app settings are sent as part of the update check.

Android references: [DownloadManager](https://developer.android.com/reference/android/app/DownloadManager),
[FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider),
[package-install permission](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()).
