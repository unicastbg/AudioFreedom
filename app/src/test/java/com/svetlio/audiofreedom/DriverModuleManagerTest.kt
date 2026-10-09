package com.svetlio.audiofreedom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverModuleManagerTest {
    @Test
    fun parsesModuleProperties() {
        val properties = parseModuleProperties(
            """
                # AudioFreedom
                id=audiofreedom
                version=0.9.4-beta
                versionCode=27
                description=value=with=equals
            """.trimIndent(),
        )

        assertEquals("audiofreedom", properties["id"])
        assertEquals("0.9.4-beta", properties["version"])
        assertEquals("27", properties["versionCode"])
        assertEquals("value=with=equals", properties["description"])
    }

    @Test
    fun acceptsGitHubSha256Digest() {
        val digest = "b151a5850c31ecac1537a9c05f56f179231b4b89933642313350865a983c5f3f"

        assertEquals(digest, parseSha256Digest("sha256:$digest"))
        assertEquals(digest, parseSha256Digest(digest.uppercase()))
        assertNull(parseSha256Digest("sha256:not-a-digest"))
    }

    @Test
    fun selectsOnlyUniversalDriverArchives() {
        assertTrue(isDriverModuleAsset("AudioFreedom-universal-0.9.5-beta.zip"))
        assertTrue(isDriverModuleAsset("AudioFreedom-driver-0.9.5-beta.zip"))
        assertFalse(isDriverModuleAsset("AudioFreedom-0.9.5-beta.apk"))
        assertFalse(isDriverModuleAsset("AudioFreedom-portable-0.9.5-beta.zip"))
    }

    @Test
    fun readsDriverVersionIndependentlyOfAppRelease() {
        assertEquals("0.9.4-beta", driverModuleAssetVersion("AudioFreedom-universal-0.9.4-beta.zip"))
        assertEquals("0.9.5-beta", driverModuleAssetVersion("AudioFreedom-driver-0.9.5-beta.zip"))
        assertNull(driverModuleAssetVersion("AudioFreedom-universal-latest.zip"))
        assertNull(driverModuleAssetVersion("AudioFreedom-0.9.5-beta.apk"))
    }
}
