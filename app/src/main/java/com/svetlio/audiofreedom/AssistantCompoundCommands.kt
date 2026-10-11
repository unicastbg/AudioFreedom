package com.svetlio.audiofreedom

import java.util.Locale

internal object AssistantCompoundCommands {
    private val separator = Regex("\\s+(?:and|then|и|после)\\s+|[,;]|[.!?]\\s+")
    private fun normalize(value: String) = value.lowercase(Locale.ROOT)
        .replace(Regex("[\\p{Z}\\s]+"), " ").trim()
    private val targets = mapOf("bass" to "bass", "бас" to "bass", "баса" to "bass",
        "treble" to "treble", "highs" to "treble", "високи" to "treble", "високите" to "treble",
        "високи честоти" to "treble", "високите честоти" to "treble", "високите тонове" to "treble",
        "ниски честоти" to "bass", "ниските честоти" to "bass", "басите" to "bass",
        "mids" to "mids", "midrange" to "mids", "среди" to "mids", "средите" to "mids",
        "detail" to "detail", "reverb" to "reverb", "echo" to "reverb", "ехо" to "reverb")

    fun canonical(clause: String): String? {
        val text = normalize(clause).trimEnd('.', '!', '?')
            .removePrefix("and ").removePrefix("then ").removePrefix("и ")
            .removePrefix("please ").removeSuffix(" please")
        val adjustment = Regex("^(more|higher|increase|add|boost|raise|less|lower|reduce|decrease|remove|повече|увеличи|увеличете|повиши|повишете|добави|добавете|засили|засилете|намали|намалете|понижи|понижете|махни|махнете) (.+)$").matchEntire(text)
        if (adjustment != null) {
            val targetText = adjustment.groupValues[2].removePrefix("малко ").removePrefix("леко ")
            val target = targets[targetText]
            if (target != null) {
                val down = adjustment.groupValues[1] in setOf("less", "lower", "reduce", "decrease", "remove", "намали", "намалете", "понижи", "понижете", "махни", "махнете")
                return "${if (down) "less" else "more"} $target"
            }
        }
        val toggle = Regex("^(turn on|turn off|enable|disable|включи|изключи) (.+)$").matchEntire(text)
        if (toggle != null && toggle.groupValues[2] in setOf("equalizer", "eq", "bass foundation", "detail recovery",
                "immersive field", "reverb", "echo", "volume leveler", "leveler", "normalizer", "sound normalizer",
                "limiter", "output protection", "еквалайзера", "баса", "реверберацията", "ехото")) return text
        return when (text) {
            "clear vocals", "clearer vocals", "ясни вокали", "по-ясни вокали" -> "clearer vocals"
            "warmer sound", "по-топъл звук" -> "warmer sound"
            "make it warmer" -> "warmer sound"
            "less muddy", "reduce muddy boominess" -> "reduce muddy boominess"
            "give it more bass" -> "more bass"
            "reduce harsh sharp sound" -> "reduce harsh sharp sound"
            "fuller sound", "по-плътен звук" -> "fuller sound"
            "reduce harshness" -> "reduce harsh sharp sound"
            "wider stage", "narrower stage" -> text
            else -> null
        }
    }

    fun plan(command: String, current: AudioFreedomSettings, profiles: List<AudioFreedomProfile>, limit: Int): AssistantPlanResult? {
        // Keep profile names containing conjunctions intact when the entire name is requested.
        val text = normalize(command).trimEnd('.', '!', '?')
        if (text in setOf("make the bass deeper and stronger", "the vocals sound harsh and sharp", "искам повече и по-дълбок бас")) return null
        if (profiles.any { profile -> text == profile.name.lowercase(Locale.ROOT) ||
                text in listOf("load profile ${profile.name.lowercase(Locale.ROOT)}", "switch profile to ${profile.name.lowercase(Locale.ROOT)}") }) return null
        val clauses = separator.split(text).map(String::trim).filter(String::isNotBlank)
        if (clauses.size < 2) {
            val normalized = canonical(text)
            return if (normalized != null && normalized != text)
                AssistantCommandPlanner.planSingle(normalized, current, profiles, limit) else null
        }
        var previousDirection: String? = null
        val expanded = clauses.map { clause ->
            val direct = canonical(clause)
            val target = targets[normalize(clause).trimEnd('.', '!', '?')]
            val action = direct ?: if (previousDirection != null && target != null) "$previousDirection $target" else null
            previousDirection = when {
                action?.startsWith("more ") == true -> "more"
                action?.startsWith("less ") == true -> "less"
                else -> null
            }
            action
        }
        if (clauses.size > 6 || expanded.any { it == null })
            return AssistantPlanResult.NotUnderstood("Not every part of this request was understood. Nothing was changed. Try separate commands or experimental conversational tuning.")
        val actions = expanded.filterNotNull().distinct()
        if (actions.any { it.startsWith("more ") && "less ${it.removePrefix("more ")}" in actions })
            return AssistantPlanResult.NotUnderstood("The request contains opposing changes. Say which direction you want; nothing was changed.")
        var updated = current
        val explanations = mutableListOf<String>()
        for (action in actions) {
            when (val result = AssistantCommandPlanner.planSingle(action, updated, emptyList(), limit)) {
                is AssistantPlanResult.Proposed -> {
                    updated = result.proposal.settings
                    explanations += result.proposal.explanation
                }
                is AssistantPlanResult.NotUnderstood -> Unit // Valid commands can already be at their limit.
                AssistantPlanResult.Undo -> return AssistantPlanResult.NotUnderstood("Undo must be a separate command.")
            }
        }
        val maximum = limit.coerceIn(50, 300)
        updated = updated.copy(bandGainsMillibels = updated.bandGainsMillibels.mapIndexed { index, gain ->
            gain.coerceIn(current.bandGainsMillibels[index] - maximum, current.bandGainsMillibels[index] + maximum)
        }, bassBoostMillibels = updated.bassBoostMillibels.coerceIn(
            (current.bassBoostMillibels - maximum).coerceAtLeast(0), (current.bassBoostMillibels + maximum).coerceAtMost(1200)))
        val changes = AssistantCommandPlanner.describeChanges(current, updated)
        if (updated == current) return AssistantPlanResult.NotUnderstood("Those settings are already selected or at their safe limit.")
        return AssistantPlanResult.Proposed(AssistantProposal("Combined sound change", explanations.joinToString(" "), current, updated, changes))
    }
}
