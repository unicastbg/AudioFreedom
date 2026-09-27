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
            val release = JSONObject(response)
            val tag = release.getString("tag_name")
            val releaseUrl = release.getString("html_url")
            val latestVersion = tag.removePrefix("v")

            if (isVersionNewer(latestVersion, currentVersion)) {
                UpdateCheckResult.UpdateAvailable(latestVersion, releaseUrl)
            } else {
                UpdateCheckResult.UpToDate(latestVersion)
            }
        } catch (_: IOException) {
            UpdateCheckResult.Failed
        } catch (_: RuntimeException) {
            UpdateCheckResult.Failed
        } finally {
            connection?.disconnect()
        }
    }
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
