package com.svetlio.audiofreedom

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class DriverModuleInfo(
    val version: String,
    val versionCode: Int,
)

internal sealed interface DriverModuleProbeResult {
    data class Installed(val info: DriverModuleInfo) : DriverModuleProbeResult
    data object Missing : DriverModuleProbeResult
    data object RootUnavailable : DriverModuleProbeResult
    data class Failed(val message: String) : DriverModuleProbeResult
}

internal data class DriverModuleRelease(
    val version: String,
    val downloadUrl: String,
    val sha256: String,
)

internal sealed interface DriverModuleUpdateResult {
    data class Available(val release: DriverModuleRelease) : DriverModuleUpdateResult
    data class Current(val latestVersion: String) : DriverModuleUpdateResult
    data object Failed : DriverModuleUpdateResult
}

internal sealed interface DriverModuleInstallResult {
    data class Success(val rootManager: String) : DriverModuleInstallResult
    data class Failed(val message: String) : DriverModuleInstallResult
}

internal data class BundledDriverModule(
    val assetName: String,
    val info: DriverModuleInfo,
    val sha256: String,
)

internal object DriverModuleManager {
    private const val ModuleId = "audiofreedom"
    private const val MetadataAsset = "driver-module.properties"
    private const val LatestReleaseEndpoint =
        "https://api.github.com/repos/unicastbg/AudioFreedom/releases/latest"
    private const val MaximumModuleBytes = 64L * 1024L * 1024L
    private const val RootProbeTimeoutSeconds = 10L
    private const val InstallTimeoutSeconds = 180L

    fun bundled(context: Context): BundledDriverModule? = runCatching {
        val properties = Properties().apply {
            context.assets.open(MetadataAsset).use(::load)
        }
        parseBundledMetadata(properties)
    }.getOrNull()

    suspend fun probeInstalled(context: Context): DriverModuleProbeResult =
        withContext(Dispatchers.IO) {
            if (probeRootAccess() != RootAccessState.Available) {
                return@withContext DriverModuleProbeResult.RootUnavailable
            }

            val command = """
                for path in \
                    /data/adb/modules/$ModuleId/module.prop \
                    /data/adb/modules_update/$ModuleId/module.prop; do
                    if [ -r "${'$'}path" ]; then
                        cat "${'$'}path"
                        exit 0
                    fi
                done
                exit 44
            """.trimIndent()
            val result = executeRootCommand(
                context = context,
                command = command,
                timeoutSeconds = RootProbeTimeoutSeconds,
            )
            when {
                result.timedOut -> DriverModuleProbeResult.Failed("Module check timed out")
                result.exitCode == 44 -> DriverModuleProbeResult.Missing
                result.exitCode != 0 -> DriverModuleProbeResult.Failed("Could not read module status")
                else -> parseModuleInfo(result.output)?.let(DriverModuleProbeResult::Installed)
                    ?: DriverModuleProbeResult.Failed("Module metadata is invalid")
            }
        }

    suspend fun checkForUpdate(
        currentVersion: String,
    ): DriverModuleUpdateResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = openGitHubConnection(LatestReleaseEndpoint)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext DriverModuleUpdateResult.Failed
            }

            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val assets = release.getJSONArray("assets")
            val candidates = buildList {
                for (index in 0 until assets.length()) {
                    val asset = assets.getJSONObject(index)
                    val name = asset.optString("name")
                    if (isDriverModuleAsset(name)) add(asset)
                }
            }
            val asset = candidates.singleOrNull() ?: return@withContext DriverModuleUpdateResult.Failed
            // App-only releases can carry an unchanged, independently versioned driver.
            val latestVersion = driverModuleAssetVersion(asset.getString("name"))
                ?: return@withContext DriverModuleUpdateResult.Failed
            if (!isVersionNewer(latestVersion, currentVersion)) {
                return@withContext DriverModuleUpdateResult.Current(latestVersion)
            }
            val digest = parseSha256Digest(asset.optString("digest"))
                ?: return@withContext DriverModuleUpdateResult.Failed
            val downloadUrl = asset.getString("browser_download_url")
            if (!isTrustedDownloadUrl(downloadUrl)) {
                return@withContext DriverModuleUpdateResult.Failed
            }

            DriverModuleUpdateResult.Available(
                DriverModuleRelease(
                    version = latestVersion,
                    downloadUrl = downloadUrl,
                    sha256 = digest,
                ),
            )
        } catch (_: IOException) {
            DriverModuleUpdateResult.Failed
        } catch (_: RuntimeException) {
            DriverModuleUpdateResult.Failed
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun installBundled(context: Context): DriverModuleInstallResult =
        withContext(Dispatchers.IO) {
            val bundled = bundled(context)
                ?: return@withContext DriverModuleInstallResult.Failed(
                    "Bundled driver package is unavailable",
                )
            val target = prepareCacheFile(context, "bundled-driver-module.zip")
            try {
                context.assets.open(bundled.assetName).use { input ->
                    target.outputStream().use(input::copyTo)
                }
                validateAndInstall(
                    context = context,
                    archive = target,
                    expectedSha256 = bundled.sha256,
                    expectedVersion = bundled.info.version,
                    expectedVersionCode = bundled.info.versionCode,
                )
            } finally {
                target.delete()
            }
        }

    suspend fun downloadAndInstall(
        context: Context,
        release: DriverModuleRelease,
    ): DriverModuleInstallResult = withContext(Dispatchers.IO) {
        val target = prepareCacheFile(context, "downloaded-driver-module.zip")
        try {
            downloadModule(release.downloadUrl, target)
            validateAndInstall(
                context = context,
                archive = target,
                expectedSha256 = release.sha256,
                expectedVersion = release.version,
                expectedVersionCode = null,
            )
        } catch (_: IOException) {
            DriverModuleInstallResult.Failed("Driver download failed")
        } finally {
            target.delete()
        }
    }

    private fun validateAndInstall(
        context: Context,
        archive: File,
        expectedSha256: String,
        expectedVersion: String,
        expectedVersionCode: Int?,
    ): DriverModuleInstallResult {
        if (archive.length() !in 1..MaximumModuleBytes) {
            return DriverModuleInstallResult.Failed("Driver package size is invalid")
        }
        if (!sha256(archive).equals(expectedSha256, ignoreCase = true)) {
            return DriverModuleInstallResult.Failed("Driver package verification failed")
        }
        val moduleInfo = readModuleInfo(archive)
            ?: return DriverModuleInstallResult.Failed("Driver package metadata is invalid")
        if (moduleInfo.version != expectedVersion ||
            expectedVersionCode != null && moduleInfo.versionCode != expectedVersionCode
        ) {
            return DriverModuleInstallResult.Failed("Driver package version does not match")
        }
        return installArchive(context, archive)
    }

    private fun installArchive(context: Context, archive: File): DriverModuleInstallResult {
        if (probeRootAccess() != RootAccessState.Available) {
            return DriverModuleInstallResult.Failed("Root access is required")
        }
        val zip = shellQuote(archive.absolutePath)
        val command = """
            root_flavor="${'$'}(su -v 2>/dev/null)"
            install_kernelsu() {
                if command -v ksud >/dev/null 2>&1; then
                    ksud module install $zip
                    return ${'$'}?
                fi
                if [ -x /data/adb/ksu/bin/ksud ]; then
                    /data/adb/ksu/bin/ksud module install $zip
                    return ${'$'}?
                fi
                ksu_binary="${'$'}(find /data/app -type f -name libksud.so 2>/dev/null | head -n 1)"
                if [ -n "${'$'}ksu_binary" ] && [ -x "${'$'}ksu_binary" ]; then
                    "${'$'}ksu_binary" module install $zip
                    return ${'$'}?
                fi
                return 127
            }
            install_apatch() {
                if command -v apd >/dev/null 2>&1; then
                    apd module install $zip
                    return ${'$'}?
                fi
                if [ -x /data/adb/ap/bin/apd ]; then
                    /data/adb/ap/bin/apd module install $zip
                    return ${'$'}?
                fi
                return 127
            }

            if echo "${'$'}root_flavor" | grep -Eqi 'KernelSU|SukiSU'; then
                echo AUDIOFREEDOM_ROOT_MANAGER=KernelSU
                install_kernelsu
            elif echo "${'$'}root_flavor" | grep -qi 'APatch'; then
                echo AUDIOFREEDOM_ROOT_MANAGER=APatch
                install_apatch
            elif command -v magisk >/dev/null 2>&1; then
                echo AUDIOFREEDOM_ROOT_MANAGER=Magisk
                magisk --install-module $zip
            elif [ -x /data/adb/magisk/magisk ]; then
                echo AUDIOFREEDOM_ROOT_MANAGER=Magisk
                /data/adb/magisk/magisk --install-module $zip
            elif command -v ksud >/dev/null 2>&1; then
                echo AUDIOFREEDOM_ROOT_MANAGER=KernelSU
                ksud module install $zip
            elif [ -x /data/adb/ksu/bin/ksud ]; then
                echo AUDIOFREEDOM_ROOT_MANAGER=KernelSU
                /data/adb/ksu/bin/ksud module install $zip
            elif [ -x /data/adb/ksud ]; then
                echo AUDIOFREEDOM_ROOT_MANAGER=KernelSU
                /data/adb/ksud module install $zip
            elif command -v apd >/dev/null 2>&1 || [ -x /data/adb/ap/bin/apd ]; then
                echo AUDIOFREEDOM_ROOT_MANAGER=APatch
                install_apatch
            else
                echo AUDIOFREEDOM_ROOT_MANAGER=Unsupported
                exit 64
            fi
        """.trimIndent()
        val result = executeRootCommand(
            context = context,
            command = command,
            timeoutSeconds = InstallTimeoutSeconds,
        )
        if (result.timedOut) {
            return DriverModuleInstallResult.Failed("Driver installation timed out")
        }
        val rootManager = result.output.lineSequence()
            .firstOrNull { it.startsWith("AUDIOFREEDOM_ROOT_MANAGER=") }
            ?.substringAfter('=')
            .orEmpty()
        return when {
            result.exitCode == 64 -> DriverModuleInstallResult.Failed(
                "This root manager does not provide a supported module installer",
            )
            result.exitCode != 0 -> DriverModuleInstallResult.Failed(
                result.output.lineSequence()
                    .lastOrNull { it.isNotBlank() && !it.startsWith("AUDIOFREEDOM_") }
                    ?.take(160)
                    ?: "Driver installation failed",
            )
            else -> {
                val manager = rootManager.ifBlank { "root manager" }
                DriverModuleNoticeStore.installed(context, manager)
                DriverModuleInstallResult.Success(manager)
            }
        }
    }

    suspend fun restartPhone(context: Context): String? = withContext(Dispatchers.IO) {
        if (probeRootAccess() != RootAccessState.Available) {
            return@withContext "Root access is required to restart. You can restart using the phone's power menu."
        }
        val result = executeRootCommand(context, "reboot", RootProbeTimeoutSeconds)
        if (result.timedOut || result.exitCode != 0) {
            "Could not restart automatically. Restart using the phone's power menu."
        } else null
    }

    private fun downloadModule(downloadUrl: String, target: File) {
        require(isTrustedDownloadUrl(downloadUrl))
        var connection: HttpURLConnection? = null
        try {
            connection = openGitHubConnection(downloadUrl).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK ||
                !isTrustedDownloadUrl(connection.url.toString())
            ) {
                throw IOException("Unexpected module download response")
            }
            val declaredLength = connection.contentLengthLong
            if (declaredLength > MaximumModuleBytes) throw IOException("Module is too large")
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MaximumModuleBytes) throw IOException("Module is too large")
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun openGitHubConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "AudioFreedom/${BuildConfig.VERSION_NAME}")
        }

    private fun readModuleInfo(archive: File): DriverModuleInfo? = runCatching {
        ZipFile(archive).use { zip ->
            val entry = zip.getEntry("module.prop") ?: return null
            if (entry.isDirectory || entry.size !in 1..16_384) return null
            val contents = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            val properties = parseModuleProperties(contents)
            if (properties["id"] != ModuleId) return null
            moduleInfo(properties)
        }
    }.getOrNull()

    private fun prepareCacheFile(context: Context, name: String): File {
        val directory = File(context.cacheDir, "driver-module").apply { mkdirs() }
        return File(directory, name).apply { delete() }
    }

    private fun executeRootCommand(
        context: Context,
        command: String,
        timeoutSeconds: Long,
    ): RootCommandResult {
        val log = File.createTempFile("audiofreedom-root-", ".log", context.cacheDir)
        val process = try {
            ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start()
        } catch (_: IOException) {
            log.delete()
            return RootCommandResult(-1, "", timedOut = false)
        }
        return try {
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            RootCommandResult(
                exitCode = if (finished) process.exitValue() else -1,
                output = log.takeIf(File::exists)?.readText().orEmpty(),
                timedOut = !finished,
            )
        } finally {
            log.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private data class RootCommandResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean,
)

internal fun parseModuleProperties(contents: String): Map<String, String> =
    contents.lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith('#') && '=' in it }
        .associate { line ->
            line.substringBefore('=').trim() to line.substringAfter('=').trim()
        }

internal fun parseSha256Digest(value: String): String? {
    val digest = value.removePrefix("sha256:").lowercase()
    return digest.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
}

internal fun isDriverModuleAsset(name: String): Boolean {
    val normalized = name.lowercase()
    return normalized.endsWith(".zip") &&
        (normalized.startsWith("audiofreedom-universal-") ||
            normalized.startsWith("audiofreedom-driver-"))
}

internal fun driverModuleAssetVersion(name: String): String? =
    Regex("^AudioFreedom-(?:universal|driver)-(\\d+\\.\\d+\\.\\d+(?:-[A-Za-z0-9.]+)?)\\.zip$",
        RegexOption.IGNORE_CASE).matchEntire(name)?.groupValues?.get(1)

private fun parseBundledMetadata(properties: Properties): BundledDriverModule {
    val assetName = properties.getProperty("asset").orEmpty()
    require(assetName.isNotBlank() && '/' !in assetName && '\\' !in assetName)
    val version = properties.getProperty("version").orEmpty()
    require(version.isNotBlank())
    val info = DriverModuleInfo(
        version = version,
        versionCode = properties.getProperty("versionCode")?.toIntOrNull()
            ?.takeIf { it > 0 } ?: error("Invalid bundled module version code"),
    )
    val digest = parseSha256Digest(properties.getProperty("sha256").orEmpty())
        ?: error("Invalid bundled module digest")
    return BundledDriverModule(assetName, info, digest)
}

private fun parseModuleInfo(contents: String): DriverModuleInfo? =
    moduleInfo(parseModuleProperties(contents))

private fun moduleInfo(properties: Map<String, String>): DriverModuleInfo? {
    if (properties["id"] != "audiofreedom") return null
    val version = properties["version"]?.takeIf(String::isNotBlank) ?: return null
    val versionCode = properties["versionCode"]?.toIntOrNull()?.takeIf { it > 0 } ?: return null
    return DriverModuleInfo(version, versionCode)
}

private fun isTrustedDownloadUrl(value: String): Boolean = runCatching {
    val url = URL(value)
    url.protocol == "https" && url.host.lowercase() in setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )
}.getOrDefault(false)

private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
