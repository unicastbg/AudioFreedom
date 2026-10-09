package com.svetlio.audiofreedom

import java.util.Locale

internal enum class VoiceAnswer { Yes, No, Unknown }

internal object HandsFreeVoicePolicy {
    @Suppress("UNUSED_PARAMETER")
    fun replyLanguage(selected: AssistantLanguage, transcript: String = ""): AssistantLanguage =
        AssistantLanguage.English

    fun bulgarianSummary(proposal: AssistantProposal, profiles: List<AudioFreedomProfile>): String {
        proposal.selectedProfileId?.let { id ->
            val name = profiles.firstOrNull { it.id == id }?.name
            return if (name != null) "Зареждам профил $name" else "Зареждам избрания профил"
        }
        val before = proposal.baseSettings
        val after = proposal.settings
        val changes = buildList {
            fun toggle(name: String, old: Boolean, new: Boolean) {
                if (old != new) add("${if (new) "Включвам" else "Изключвам"} $name")
            }
            toggle("еквалайзера", before.equalizerEnabled, after.equalizerEnabled)
            toggle("баса", before.dynamicBassEnabled, after.dynamicBassEnabled)
            toggle("детайлността", before.detailRecoveryEnabled, after.detailRecoveryEnabled)
            toggle("обгръщащия звук", before.immersiveFieldEnabled, after.immersiveFieldEnabled)
            toggle("реверберацията", before.reverbEnabled, after.reverbEnabled)
            toggle("защитата на изхода", before.limiterEnabled, after.limiterEnabled)
            if (before.bandGainsMillibels != after.bandGainsMillibels) add("Променям честотните ленти на еквалайзера")
            if (before.preampMillibels != after.preampMillibels) add("Променям предусилването")
            if (before.bassBoostMillibels != after.bassBoostMillibels || before.bassCutoffHz != after.bassCutoffHz ||
                before.bassDynamicsPercent != after.bassDynamicsPercent) add("Променям настройките на баса")
            if (before.detailAmountPercent != after.detailAmountPercent || before.detailFocusHz != after.detailFocusHz ||
                before.detailTransientsPercent != after.detailTransientsPercent) add("Променям настройките за детайлност")
            if (before.immersiveAmountPercent != after.immersiveAmountPercent || before.immersiveWidthPercent != after.immersiveWidthPercent ||
                before.immersiveCenterPercent != after.immersiveCenterPercent || before.immersiveRoomPercent != after.immersiveRoomPercent)
                add("Променям настройките за обгръщащ звук")
            if (before.reverbAmountPercent != after.reverbAmountPercent || before.reverbSpacePercent != after.reverbSpacePercent ||
                before.reverbDampingPercent != after.reverbDampingPercent || before.reverbDecayMilliseconds != after.reverbDecayMilliseconds)
                add("Променям настройките на реверберацията")
            if (before.limiterThresholdMillibels != after.limiterThresholdMillibels || before.limiterReleaseMilliseconds != after.limiterReleaseMilliseconds)
                add("Променям настройките за защита на изхода")
        }
        return changes.joinToString(". ").ifBlank { "Прилагам предложените настройки" }
    }

    fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .trim().replace(Regex("\\s+"), " ")

    fun confirmation(transcript: String): VoiceAnswer {
        if (AssistantConversationCommands.recognize(transcript) == AssistantConversationControl.Cancel) return VoiceAnswer.No
        return when (normalize(transcript)) {
        "yes", "yes please", "confirm", "confirmed", "apply", "apply it", "do it",
        "да", "да моля", "потвърждавам", "потвърди", "приложи" -> VoiceAnswer.Yes
        "no", "no thanks", "cancel", "stop", "не", "откажи", "отказ", "спри" -> VoiceAnswer.No
        else -> VoiceAnswer.Unknown
        }
    }
}
