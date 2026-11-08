package com.svetlio.audiofreedom

import org.junit.Assert.*
import org.junit.Test

class ProfileManagementTest {
    private fun profiles() = (1..5).map {
        AudioFreedomProfile("$it", "Sound $it", AudioFreedomSettings(preampMillibels = -100 * it),
            if (it == 1) "Old group" else "")
    }

    @Test fun moveFiveProfilesUnderOneNameWithoutChangingIdentityOrDsp() {
        val before = profiles()
        val after = planProfileMetadata(before, ProfileAction.Move(before.map { it.id }.toSet(), " Sony Amp "))
        assertEquals(List(5) { "Sony Amp" }, after.map { it.deviceGroup })
        assertEquals(before.map { it.id }, after.map { it.id })
        assertEquals(before.map { it.settings }, after.map { it.settings })
        assertEquals(before.map { it.name }, after.map { it.name })
    }

    @Test fun renameAndRemoveGroupsKeepProfilesAndAllowReturningToUngrouped() {
        val moved = planProfileMetadata(profiles(), ProfileAction.Move(setOf("1","2","3"), "Amp"))
        val renamed = planProfileMetadata(moved, ProfileAction.RenameGroup("AMP", "Sony"))
        assertEquals(3, renamed.count { it.deviceGroup == "Sony" })
        val removed = planProfileMetadata(renamed, ProfileAction.RemoveGroup("sony"))
        assertEquals(5, removed.size)
        assertTrue(removed.all { it.deviceGroup.isEmpty() })
        assertEquals(profiles().map { it.settings }, removed.map { it.settings })
    }

    @Test fun collisionsNeverSilentlyOverwriteProfiles() {
        val settings = AudioFreedomSettings()
        val values = listOf(AudioFreedomProfile("1","Clear",settings,"A"), AudioFreedomProfile("2","clear",settings,"B"))
        assertThrows(IllegalArgumentException::class.java) {
            planProfileMetadata(values, ProfileAction.Move(setOf("1"), "b"))
        }
        assertEquals(listOf("A", "B"), values.map { it.deviceGroup })
        assertThrows(IllegalArgumentException::class.java) {
            planProfileMetadata(values, ProfileAction.RenameGroup("A", "b"))
        }
    }

    @Test fun groupsAreReusableAndCopiesHaveUniqueNames() {
        val source = profiles().first()
        val values = profiles() + source.copy(id = "copy", name = "Sound 1 copy", deviceGroup = "old GROUP")
        assertEquals(listOf("Old group"), profileGroups(values))
        assertEquals("Sound 1 copy 2", duplicateProfileName(source, values))
        val moved = planProfileMetadata(values, ProfileAction.Move(setOf("2"), " OLD GROUP "))
        assertEquals("Old group", moved.first { it.id == "2" }.deviceGroup)
    }
}
