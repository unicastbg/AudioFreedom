package com.svetlio.audiofreedom

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PendingAppUpdate(val downloadId: Long, val release: UpdateCheckResult.UpdateAvailable)

internal sealed interface AppDownloadState {
    data object None : AppDownloadState
    data class Downloading(val bytes: Long, val total: Long, val paused: Boolean) : AppDownloadState
    data object Verifying : AppDownloadState
    data class Ready(val apk: File) : AppDownloadState
    data class Failed(val message: String) : AppDownloadState
}

internal object AppUpdateDownloader {
    private const val Preferences = "audiofreedom_app_update"
    private const val ApkName = "AudioFreedom-update.apk"

    fun pending(context: Context): PendingAppUpdate? {
        val preferences = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        return runCatching {
            val id = preferences.getLong("download_id", -1)
            if (id < 0) return null
            val version = preferences.getString("version", "").orEmpty()
            if (!isVersionNewer(version, BuildConfig.VERSION_NAME)) {
                cancel(context, id)
                return null
            }
            val url = preferences.getString("url", "").orEmpty()
            val releaseUrl = preferences.getString("release_url", "").orEmpty()
            val hash = parseSha256Digest(preferences.getString("hash", "").orEmpty()) ?: return null
            val size = preferences.getLong("size", -1)
            if (!isTrustedAppUpdateUrl(url, version, true) ||
                !isTrustedAppUpdateUrl(releaseUrl, version, false) || size !in 1..MaximumAppUpdateBytes) return null
            PendingAppUpdate(id, UpdateCheckResult.UpdateAvailable(version, releaseUrl,
                AppUpdateAsset(version, url, hash, size)))
        }.getOrNull()
    }

    suspend fun start(context: Context, release: UpdateCheckResult.UpdateAvailable): PendingAppUpdate =
        withContext(Dispatchers.IO) {
            val asset = release.asset ?: throw IOException("A verified APK download is unavailable")
            require(release.version == asset.version && isVersionNewer(asset.version, BuildConfig.VERSION_NAME))
            require(isTrustedAppUpdateUrl(asset.downloadUrl, asset.version, true))
            require(asset.sizeBytes in 1..MaximumAppUpdateBytes)
            val preferences = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            preferences.getLong("download_id", -1).takeIf { it >= 0 }?.let { cancel(context, it) }
            val target = downloadFile(context)
            target.parentFile?.mkdirs()
            if (target.exists() && !target.delete()) throw IOException("Could not replace the previous download")
            val request = DownloadManager.Request(Uri.parse(asset.downloadUrl))
                .setTitle("AudioFreedom ${asset.version}")
                .setDescription("App update. Return to AudioFreedom Settings to verify and install.")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS,
                    "app-updates/$ApkName")
            val id = manager(context).enqueue(request)
            preferences.edit().putLong("download_id", id).putString("version", asset.version)
                .putString("url", asset.downloadUrl).putString("release_url", release.releaseUrl)
                .putString("hash", asset.sha256).putLong("size", asset.sizeBytes).commit().also { saved ->
                    if (!saved) {
                        manager(context).remove(id)
                        throw IOException("Could not save the pending update")
                    }
                }
            PendingAppUpdate(id, release)
        }

    suspend fun status(context: Context, pending: PendingAppUpdate): AppDownloadState = withContext(Dispatchers.IO) {
        manager(context).query(DownloadManager.Query().setFilterById(pending.downloadId)).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) return@withContext AppDownloadState.Failed("Download is no longer available. Try again.")
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            if (bytes > MaximumAppUpdateBytes || total > MaximumAppUpdateBytes) {
                cancel(context, pending.downloadId)
                return@withContext AppDownloadState.Failed("The APK exceeds the download size limit")
            }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> AppDownloadState.Verifying
                DownloadManager.STATUS_FAILED -> AppDownloadState.Failed("Download failed. Check your connection and storage, then retry.")
                else -> AppDownloadState.Downloading(bytes.coerceAtLeast(0), total,
                    status == DownloadManager.STATUS_PAUSED)
            }
        }
    }

    suspend fun prepare(context: Context, pending: PendingAppUpdate): File = withContext(Dispatchers.IO) {
        val asset = pending.release.asset ?: throw IOException("Missing APK verification data")
        val source = downloadFile(context)
        if (source.length() != asset.sizeBytes || source.length() !in 1..MaximumAppUpdateBytes) {
            throw IOException("Downloaded APK size does not match the release")
        }
        val target = installFile(context)
        target.parentFile?.mkdirs()
        try {
            source.copyTo(target, overwrite = true)
            verify(context, target, asset)
            target
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    private fun verify(context: Context, apk: File, asset: AppUpdateAsset) {
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        if (digest.digest().joinToString("") { "%02x".format(it) } != asset.sha256) {
            throw IOException("APK verification failed: file hash does not match")
        }
        val packageManager = context.packageManager
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        val archive = packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw IOException("Downloaded file is not a valid Android package")
        val installed = packageManager.getPackageInfo(context.packageName, flags)
        val actualSigners = archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        val installedSigners = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        if (!isValidAppUpdateIdentity(archive.packageName, archive.versionName, archive.longVersionCode,
                asset.version, context.packageName, installed.longVersionCode, actualSigners, installedSigners)) {
            throw IOException("APK verification failed: app identity, version, or signing certificate does not match")
        }
        // The installed app must carry the driver bytes matching its own bundle metadata.
        java.util.zip.ZipFile(apk).use { zip ->
            val metadata = zip.getEntry("assets/driver-module.properties")
                ?: throw IOException("Update is missing its bundled driver metadata")
            val properties = java.util.Properties().apply { zip.getInputStream(metadata).use(::load) }
            if (properties.getProperty("asset") != "audiofreedom-driver-module.zip" ||
                properties.getProperty("version").isNullOrBlank() ||
                (properties.getProperty("versionCode")?.toIntOrNull() ?: 0) <= 0) {
                throw IOException("Update has invalid bundled driver metadata")
            }
            val driver = zip.getEntry("assets/audiofreedom-driver-module.zip")
                ?: throw IOException("Update is missing its bundled driver")
            val expected = parseSha256Digest(properties.getProperty("sha256").orEmpty())
                ?: throw IOException("Update has invalid bundled driver metadata")
            val driverHash = MessageDigest.getInstance("SHA-256")
            zip.getInputStream(driver).use { input ->
                val buffer = ByteArray(64 * 1024)
                var count = input.read(buffer)
                while (count >= 0) {
                    driverHash.update(buffer, 0, count)
                    count = input.read(buffer)
                }
            }
            if (driverHash.digest().joinToString("") { "%02x".format(it) } != expected) {
                throw IOException("Update's bundled driver failed verification")
            }
        }
    }

    fun installIntent(context: Context, apk: File): Intent {
        require(apk.canonicalFile == installFile(context).canonicalFile && apk.isFile)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.appupdates", apk)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun cancel(context: Context, id: Long) {
        manager(context).remove(id)
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE).edit().clear().apply()
        installFile(context).delete()
    }

    private fun manager(context: Context): DownloadManager = context.getSystemService(DownloadManager::class.java)
    private fun downloadFile(context: Context): File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IOException("Download storage is unavailable"), "app-updates/$ApkName")
    private fun installFile(context: Context): File = File(context.filesDir, "app-update/$ApkName")
}

internal fun isValidAppUpdateIdentity(
    packageName: String?, versionName: String?, versionCode: Long,
    expectedVersion: String, installedPackage: String, installedVersionCode: Long,
    signers: Set<String>, installedSigners: Set<String>,
): Boolean = packageName == installedPackage && versionName == expectedVersion &&
    versionCode > installedVersionCode && signers.isNotEmpty() && signers == installedSigners
