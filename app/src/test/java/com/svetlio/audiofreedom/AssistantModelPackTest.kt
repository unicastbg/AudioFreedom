package com.svetlio.audiofreedom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantModelPackTest {
    @Test
    fun managedPackReportsCurrentVersion() {
        val info = AssistantModelPackInfo(
            installed = true,
            sizeBytes = AssistantModelCatalog.language.sizeBytes,
            installedPackId = AssistantModelCatalog.language.id,
            installedVersion = AssistantModelCatalog.language.version,
        )

        assertTrue(info.isCurrent(AssistantModelCatalog.language))
        assertFalse(info.hasUpdate(AssistantModelCatalog.language))
    }

    @Test
    fun importedPackOffersManagedUpdate() {
        val info = AssistantModelPackInfo(installed = true, sizeBytes = 400_000_000L)

        assertFalse(info.isCurrent(AssistantModelCatalog.language))
        assertTrue(info.hasUpdate(AssistantModelCatalog.language))
    }

    @Test
    fun downloadProgressIsBounded() {
        assertEquals(0, AssistantModelDownloadProgress(0, 100).percent)
        assertEquals(52, AssistantModelDownloadProgress(52, 100).percent)
        assertEquals(100, AssistantModelDownloadProgress(150, 100).percent)
    }
}
