package com.svetlio.audiofreedom

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal sealed interface UpdateCheckResult {
    data class UpdateAvailable(
        val version: String,
        val releaseUrl: String,
        val asset: AppUpdateAsset? = null,
    ) : UpdateCheckResult

    data class UpToDate(val latestVersion: String) : UpdateCheckResult

    data object Failed : UpdateCheckResult
}

internal object AppUpdateChecker {
    private const val LatestReleaseEndpoint =
        "https://api.github.com/repos/unicastbg/AudioFreedom/releases/latest"

    suspend fun check(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(LatestReleaseEndpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "AudioFreedom/$currentVersion")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateCheckResult.Failed
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parseAppUpdateRelease(JSONObject(response), currentVersion)
        } catch (_: IOException) {
            UpdateCheckResult.Failed
        } catch (_: RuntimeException) {
            UpdateCheckResult.Failed
        } finally {
            connection?.disconnect()
        }
    }
}

internal const val MaximumAppUpdateBytes = 100L * 1024 * 1024

internal data class AppUpdateAsset(
    val version: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
)

internal fun isTrustedAppUpdateUrl(value: String, version: String, asset: Boolean): Boolean = runCatching {
    val url = URL(value)
    val expected = if (asset) "/unicastbg/AudioFreedom/releases/download/v$version/AudioFreedom-$version.apk"
        else "/unicastbg/AudioFreedom/releases/tag/v$version"
    url.protocol == "https" && url.host == "github.com" && url.port == -1 &&
        url.userInfo == null && url.query == null && url.ref == null && url.path == expected &&
        SemanticVersion.parse(version) != null
}.getOrDefault(false)

internal fun parseAppUpdateRelease(release: JSONObject, currentVersion: String): UpdateCheckResult {
    val version = release.getString("tag_name").removePrefix("v")
    val releaseUrl = release.getString("html_url")
    if (release.optBoolean("draft") || release.optBoolean("prerelease") ||
        SemanticVersion.parse(version) == null || !isTrustedAppUpdateUrl(releaseUrl, version, false)) {
        return UpdateCheckResult.Failed
    }
    if (!isVersionNewer(version, currentVersion)) return UpdateCheckResult.UpToDate(version)
    val assets = release.optJSONArray("assets")
    val candidates = buildList {
        if (assets != null) for (index in 0 until assets.length()) {
            val candidate = assets.getJSONObject(index)
            if (candidate.optString("name") == "AudioFreedom-$version.apk") add(candidate)
        }
    }
    val asset = candidates.singleOrNull()?.let { candidate ->
        val url = candidate.optString("browser_download_url")
        val hash = parseSha256Digest(candidate.optString("digest"))
        val size = candidate.optLong("size", -1)
        if (!isTrustedAppUpdateUrl(url, version, true) || hash == null || size !in 1..MaximumAppUpdateBytes) null
        else AppUpdateAsset(version, url, hash, size)
    }
    return UpdateCheckResult.UpdateAvailable(version, releaseUrl, asset)
}

internal fun isVersionNewer(candidate: String, current: String): Boolean {
    val candidateVersion = SemanticVersion.parse(candidate) ?: return false
    val currentVersion = SemanticVersion.parse(current) ?: return false
    return candidateVersion > currentVersion
}

private data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String>,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }

        if (preRelease.isEmpty() && other.preRelease.isNotEmpty()) return 1
        if (preRelease.isNotEmpty() && other.preRelease.isEmpty()) return -1

        preRelease.zip(other.preRelease).forEach { (left, right) ->
            val comparison = comparePreReleaseIdentifier(left, right)
            if (comparison != 0) return comparison
        }
        return compareValues(preRelease.size, other.preRelease.size)
    }

    companion object {
        private val Pattern = Regex(
            "^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$",
        )

        fun parse(value: String): SemanticVersion? {
            val match = Pattern.matchEntire(value.trim()) ?: return null
            return SemanticVersion(
                major = match.groupValues[1].toIntOrNull() ?: return null,
                minor = match.groupValues[2].toIntOrNull() ?: return null,
                patch = match.groupValues[3].toIntOrNull() ?: return null,
                preRelease = match.groupValues[4]
                    .takeIf(String::isNotEmpty)
                    ?.split('.')
                    .orEmpty(),
            )
        }
    }
}

private fun comparePreReleaseIdentifier(left: String, right: String): Int {
    val leftNumber = left.toLongOrNull()
    val rightNumber = right.toLongOrNull()
    return when {
        leftNumber != null && rightNumber != null -> compareValues(leftNumber, rightNumber)
        leftNumber != null -> -1
        rightNumber != null -> 1
        else -> left.compareTo(right, ignoreCase = true)
    }
}
