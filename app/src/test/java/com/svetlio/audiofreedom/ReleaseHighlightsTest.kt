package com.svetlio.audiofreedom

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReleaseHighlightsTest {
    @Test fun conciseHighlightsAreLimitedToThreeAndDoNotIncludeInstallSteps() {
        val body = "# 1.0.0\n## Highlights\n- LOS 24 fix\n- Device profiles\n- DSP format\n- Small UI fix\n" +
            "## Installation\n- Install APK\n"
        assertEquals(listOf("LOS 24 fix", "Device profiles", "DSP format"), parseReleaseHighlights(body))
        assertTrue(parseReleaseHighlights("## Installation\n- Reboot").isEmpty())
    }

    @Test fun highlightsArePlainBoundedTextFromTheTrustedRelease() {
        val release = JSONObject().put("tag_name", "v1.0.0")
            .put("html_url", "https://github.com/unicastbg/AudioFreedom/releases/tag/v1.0.0")
            .put("body", "## Highlights\n- " + "x".repeat(500))
        val result = parseAppUpdateRelease(release, "0.9.8-beta") as UpdateCheckResult.UpdateAvailable
        assertEquals(180, result.highlights.single().length)
        assertEquals(3, VersionOneHighlights.size)
        assertTrue(VersionOneHighlights.first().contains("LOS 24"))
    }
}
