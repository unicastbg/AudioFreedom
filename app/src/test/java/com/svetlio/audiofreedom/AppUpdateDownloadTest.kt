package com.svetlio.audiofreedom

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppUpdateDownloadTest {
    private val version = "0.9.7-beta"
    private val hash = "a".repeat(64)
    private val url = "https://github.com/unicastbg/AudioFreedom/releases/download/v$version/AudioFreedom-$version.apk"

    private fun asset() = JSONObject().put("name", "AudioFreedom-$version.apk")
        .put("browser_download_url", url).put("digest", "sha256:$hash").put("size", 18_000_000)
    private fun release(assets: JSONArray = JSONArray().put(asset())) = JSONObject()
        .put("tag_name", "v$version")
        .put("html_url", "https://github.com/unicastbg/AudioFreedom/releases/tag/v$version")
        .put("assets", assets)

    @Test fun extractsVerifiedApkFromNewRelease() {
        val result = parseAppUpdateRelease(release(), "0.9.6-beta") as UpdateCheckResult.UpdateAvailable
        assertEquals(AppUpdateAsset(version, url, hash, 18_000_000), result.asset)
    }

    @Test fun olderOrEqualReleaseIsUpToDate() {
        assertTrue(parseAppUpdateRelease(release(), version) is UpdateCheckResult.UpToDate)
        assertTrue(parseAppUpdateRelease(release(), "1.0.0") is UpdateCheckResult.UpToDate)
    }

    @Test fun missingDigestKeepsGithubFallbackButDisablesDirectDownload() {
        val candidate = asset().put("digest", JSONObject.NULL)
        val result = parseAppUpdateRelease(release(JSONArray().put(candidate)), "0.9.6-beta")
            as UpdateCheckResult.UpdateAvailable
        assertNull(result.asset)
    }

    @Test fun duplicateOrMissingApksCannotBeDownloaded() {
        for (assets in listOf(JSONArray(), JSONArray().put(asset()).put(asset()))) {
            assertNull((parseAppUpdateRelease(release(assets), "0.9.6-beta") as UpdateCheckResult.UpdateAvailable).asset)
        }
    }

    @Test fun foreignUrlAndOversizeAssetsCannotBeDownloaded() {
        for (candidate in listOf(asset().put("browser_download_url", "https://example.com/update.apk"),
                asset().put("size", MaximumAppUpdateBytes + 1), asset().put("size", 0))) {
            assertNull((parseAppUpdateRelease(release(JSONArray().put(candidate)), "0.9.6-beta")
                as UpdateCheckResult.UpdateAvailable).asset)
        }
    }

    @Test fun downloadUrlIsPinnedToRepositoryVersionAndFileName() {
        assertTrue(isTrustedAppUpdateUrl(url, version, true))
        for (bad in listOf(url.replace("https:", "http:"), url.replace("github.com", "github.com.example.org"),
                url.replace("unicastbg", "attacker"), url.replace("v$version/", "v0.9.6-beta/"),
                "$url?redirect=x", url.replace("github.com", "user@github.com"),
                url.replace("github.com", "github.com:443"))) {
            assertFalse(bad, isTrustedAppUpdateUrl(bad, version, true))
        }
    }

    @Test fun draftPrereleaseOrForeignProjectMetadataIsRejected() {
        assertEquals(UpdateCheckResult.Failed, parseAppUpdateRelease(release().put("draft", true), "0.9.6-beta"))
        assertEquals(UpdateCheckResult.Failed, parseAppUpdateRelease(release().put("prerelease", true), "0.9.6-beta"))
        assertEquals(UpdateCheckResult.Failed, parseAppUpdateRelease(release().put("html_url", "https://example.com"), "0.9.6-beta"))
    }

    @Test fun matchingSignedPackageAndNewerBuildAreAccepted() {
        assertTrue(isValidAppUpdateIdentity("app", version, 38, version, "app", 36, setOf("cert"), setOf("cert")))
    }

    @Test fun wrongPackageVersionSignerOrDowngradeAreRejected() {
        assertFalse(isValidAppUpdateIdentity("other", version, 38, version, "app", 36, setOf("cert"), setOf("cert")))
        assertFalse(isValidAppUpdateIdentity("app", "0.9.6-beta", 38, version, "app", 36, setOf("cert"), setOf("cert")))
        assertFalse(isValidAppUpdateIdentity("app", version, 36, version, "app", 36, setOf("cert"), setOf("cert")))
        assertFalse(isValidAppUpdateIdentity("app", version, 35, version, "app", 36, setOf("cert"), setOf("cert")))
        assertFalse(isValidAppUpdateIdentity("app", version, 38, version, "app", 36, setOf("wrong"), setOf("cert")))
        assertFalse(isValidAppUpdateIdentity("app", version, 38, version, "app", 36, emptySet(), emptySet()))
    }
}
