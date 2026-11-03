package com.svetlio.audiofreedom

import org.junit.Assert.*
import org.junit.Test

class ProfileBrowserTest {
    private fun profile(id: String, name: String, group: String = "") =
        AudioFreedomProfile(id, name, AudioFreedomSettings(), group)

    @Test fun groupsSortDevicesBeforeUngroupedAndNamesWithinDevice() {
        val result = groupedProfiles(listOf(profile("1", "Z", "Sony Amp"), profile("2", "Legacy"),
            profile("3", "Clear", "Sony Amp"), profile("4", "Car", "Bluetooth")), "")
        assertEquals(listOf("Bluetooth", "Sony Amp", ""), result.map { it.first })
        assertEquals(listOf("Clear", "Z"), result[1].second.map { it.name })
    }

    @Test fun searchMatchesBothNameAndDeviceWithoutChangingSnapshots() {
        val profiles = listOf(profile("1", "Clear", "Sony Amp"), profile("2", "Bass", "Buds"))
        assertEquals(listOf("1"), groupedProfiles(profiles, " SONY ").flatMap { it.second }.map { it.id })
        assertEquals(listOf("2"), groupedProfiles(profiles, "bass").flatMap { it.second }.map { it.id })
        assertTrue(groupedProfiles(profiles, "absent").isEmpty())
        assertEquals("Sony Amp", profiles.first().deviceGroup)
    }

    @Test fun deviceGroupsAreCaseInsensitive() {
        assertEquals(1, groupedProfiles(listOf(profile("1", "One", "Sony Amp"),
            profile("2", "Two", "sony amp")), "").size)
    }

    @Test fun voiceProfileSelectionCanBeQualifiedByDevice() {
        val one = profile("1", "Clear", "Sony Amp")
        val two = profile("2", "Clear", "Buds")
        val result = AssistantCommandPlanner.plan("Load profile Sony Amp Clear", AudioFreedomSettings(), listOf(one, two), 300)
        assertTrue(result is AssistantPlanResult.Proposed)
        assertEquals("1", (result as AssistantPlanResult.Proposed).proposal.selectedProfileId)
        val ambiguous = AssistantCommandPlanner.plan("Load profile Clear", AudioFreedomSettings(), listOf(one, two), 300)
        assertFalse(ambiguous is AssistantPlanResult.Proposed)
        val named = profile("3", "Rock and Roll", "Sony Amp")
        val compoundName = AssistantCommandPlanner.plan("Load profile Sony Amp Rock and Roll",
            AudioFreedomSettings(), listOf(named), 300)
        assertEquals("3", (compoundName as AssistantPlanResult.Proposed).proposal.selectedProfileId)
    }
}
