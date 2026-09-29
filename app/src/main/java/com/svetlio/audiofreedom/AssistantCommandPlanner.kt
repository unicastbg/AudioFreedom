package com.svetlio.audiofreedom

internal data class AssistantProposal(
    val title: String,
    val explanation: String,
    val baseSettings: AudioFreedomSettings,
    val settings: AudioFreedomSettings,
    val changes: List<String>,
    val selectedProfileId: String? = null,
    val canApplyAutomatically: Boolean = true,
)

internal sealed interface AssistantPlanResult {
    data class Proposed(val proposal: AssistantProposal) : AssistantPlanResult
    data object Undo : AssistantPlanResult
    data class NotUnderstood(val message: String) : AssistantPlanResult
}

internal data class AssistantRequest(
    val command: String,
    val current: AudioFreedomSettings,
    val profiles: List<AudioFreedomProfile>,
    val maximumBandDeltaMillibels: Int,
    val usePersonalization: Boolean = true,
)

internal fun interface AssistantEngine {
    suspend fun plan(request: AssistantRequest): AssistantPlanResult
}

internal object BuiltInAssistantEngine : AssistantEngine {
    override suspend fun plan(request: AssistantRequest): AssistantPlanResult =
        AssistantCommandPlanner.plan(
            command = request.command,
            current = request.current,
            profiles = request.profiles,
            maximumBandDeltaMillibels = request.maximumBandDeltaMillibels,
        )
}

internal object AssistantCommandPlanner {
    fun plan(
        command: String,
        current: AudioFreedomSettings,
        profiles: List<AudioFreedomProfile>,
        maximumBandDeltaMillibels: Int,
    ): AssistantPlanResult {
        val normalized = normalizeCommand(command)
        if (normalized.isBlank()) {
            return AssistantPlanResult.NotUnderstood("Describe the sound change you want")
        }
        planDirectControl(normalized, current, profiles)?.let { return it }

        val limit = maximumBandDeltaMillibels.coerceIn(50, 300)
        val bandDeltas = IntArray(EqualizerBandCount)
        var updated = current
        var recognized = false
        val descriptions = mutableListOf<String>()

        fun adjustBand(index: Int, delta: Int) {
            bandDeltas[index] = (bandDeltas[index] + delta).coerceIn(-limit, limit)
        }

        val wantsLessBass = normalized.containsAny(
            "less bass",
            "reduce bass",
            "lower bass",
            "decrease bass",
            "remove bass",
            "cut bass",
            "bass is too",
            "too much bass",
            "по-малко бас",
            "намали баса",
            "махни баса",
            "басът е много",
            "твърде много бас",
        )
        val mentionsBass = normalized.containsAny("bass", "бас")
        val wantsMoreBass = mentionsBass && normalized.containsAny(
            "more",
            "increase",
            "boost",
            "add",
            "raise",
            "stronger",
            "deeper",
            "powerful",
            "punch",
            "bring back",
            "повече",
            "увеличи",
            "засили",
            "добави",
            "по-силен",
            "по-дълбок",
            "мощен",
            "ударен",
            "върни",
        )
        if (wantsLessBass || wantsMoreBass) {
            recognized = true
            val direction = if (wantsLessBass) -1 else 1
            adjustBand(0, direction * limit)
            adjustBand(1, direction * limit)
            adjustBand(2, direction * (limit * 2 / 3))
            updated = updated.copy(
                dynamicBassEnabled = wantsMoreBass || updated.dynamicBassEnabled,
                bassBoostMillibels = (updated.bassBoostMillibels + direction * limit)
                    .coerceIn(0, 1200),
                bassCutoffHz = if (normalized.containsAny("deep", "дълб")) {
                    (updated.bassCutoffHz - 10).coerceIn(40, 160)
                } else {
                    updated.bassCutoffHz
                },
            )
            descriptions += if (wantsMoreBass) {
                "Strengthen the bass foundation and low-frequency EQ"
            } else {
                "Reduce bass weight and low-frequency EQ"
            }
        }

        if (
            normalized.containsAny(
                "boomy",
                "boominess",
                "rumble",
                "muddy",
                "mud",
                "бумтящ",
                "бумтене",
                "тътнеж",
                "мътен",
            )
        ) {
            recognized = true
            adjustBand(0, -limit)
            adjustBand(1, -limit)
            adjustBand(2, -(limit * 2 / 3))
            adjustBand(3, -(limit / 2))
            descriptions += "Reduce sub-bass rumble and low-mid boom"
        }

        if (
            normalized.containsAny(
                "warmer",
                "warm sound",
                "more warmth",
                "по-топъл",
                "топъл звук",
                "повече топлина",
            )
        ) {
            recognized = true
            adjustBand(2, limit / 2)
            adjustBand(3, limit)
            adjustBand(7, -(limit / 3))
            descriptions += "Add warmth while gently relaxing the upper presence range"
        }

        if (
            normalized.containsAny(
                "thin",
                "more body",
                "fuller",
                "тънък",
                "повече плътност",
                "по-плътен",
            )
        ) {
            recognized = true
            adjustBand(1, limit / 2)
            adjustBand(2, limit)
            adjustBand(3, limit / 2)
            descriptions += "Add body through the upper-bass and low-mid bands"
        }

        val wantsLessTreble = normalized.containsAny(
            "less treble",
            "reduce treble",
            "lower treble",
            "decrease treble",
            "remove treble",
            "cut treble",
            "too bright",
            "по-малко високи",
            "намали високите",
            "махни високите",
            "твърде ярък",
        )
        val mentionsTreble = normalized.containsAny("treble", "високи")
        val wantsMoreTreble = mentionsTreble && normalized.containsAny(
            "more",
            "increase",
            "boost",
            "add",
            "raise",
            "clearer",
            "brighter",
            "bring back",
            "повече",
            "увеличи",
            "засили",
            "добави",
            "по-ясни",
            "по-ярки",
            "върни",
        )
        if (wantsLessTreble || wantsMoreTreble) {
            recognized = true
            val direction = if (wantsLessTreble) -1 else 1
            adjustBand(7, direction * (limit * 2 / 3))
            adjustBand(8, direction * limit)
            adjustBand(9, direction * (limit / 2))
            descriptions += if (wantsMoreTreble) {
                "Lift treble clarity and air"
            } else {
                "Soften treble and upper-frequency energy"
            }
        }

        val wantsLessMids = normalized.containsAny(
            "less mids",
            "less midrange",
            "reduce mids",
            "reduce midrange",
            "lower mids",
            "lower midrange",
            "remove mids",
            "cut mids",
            "по-малко среди",
            "по-малко средни",
            "намали средите",
            "намали средните",
            "махни средите",
            "махни средните",
        )
        val mentionsMids = normalized.containsAny(
            "mids",
            "midrange",
            "mid frequencies",
            "middle frequencies",
            "среди",
            "средни",
            "средните",
        )
        val wantsMoreMids = mentionsMids && normalized.containsAny(
            "more",
            "increase",
            "boost",
            "add",
            "raise",
            "повече",
            "увеличи",
            "засили",
            "добави",
        )
        if (wantsLessMids || wantsMoreMids) {
            recognized = true
            val direction = if (wantsLessMids) -1 else 1
            adjustBand(4, direction * (limit * 2 / 3))
            adjustBand(5, direction * limit)
            adjustBand(6, direction * (limit * 2 / 3))
            descriptions += if (wantsMoreMids) {
                "Lift the midrange"
            } else {
                "Reduce midrange emphasis"
            }
        }

        if (
            normalized.containsAny(
                "harsh",
                "sharp",
                "sibilant",
                "piercing",
                "fatiguing",
                "остър",
                "рязък",
                "съскащ",
                "пронизващ",
                "уморителен",
            )
        ) {
            recognized = true
            adjustBand(7, -limit)
            adjustBand(8, -(limit * 2 / 3))
            updated = updated.copy(
                detailAmountPercent = (updated.detailAmountPercent - 10).coerceIn(0, 100),
            )
            descriptions += "Relax harsh presence and high-frequency emphasis"
        }

        val wantsLessVocals = normalized.containsAny(
            "less vocals",
            "reduce vocals",
            "lower vocals",
            "remove vocals",
            "по-малко вокали",
            "намали вокалите",
            "махни вокалите",
        )
        val wantsMoreVocals = normalized.containsAny(
                "clearer vocals",
                "clear vocals",
                "clear vocal",
                "cleaner vocals",
                "vocal clarity",
                "make vocals clear",
                "bring vocals forward",
                "increase vocals",
                "add vocals",
                "boost vocals",
                "raise vocals",
                "more vocals",
                "vocal forward",
                "vocal presence",
                "по-ясни вокали",
                "изчисти вокалите",
                "изчисти вокала",
                "ясни вокали",
                "ясен вокал",
                "по-ясен глас",
                "ясен глас",
                "подчертай вокалите",
                "повече вокали",
                "увеличи вокалите",
                "засили вокалите",
                "добави вокали",
                "вокалите напред",
        )
        if (wantsLessVocals || wantsMoreVocals) {
            recognized = true
            val direction = if (wantsLessVocals) -1 else 1
            adjustBand(5, direction * (limit / 2))
            adjustBand(6, direction * limit)
            adjustBand(7, direction * (limit / 3))
            descriptions += if (wantsMoreVocals) {
                "Bring vocals forward through the presence bands"
            } else {
                "Reduce vocal presence"
            }
        }

        val wantsLessDetail = normalized.containsAny(
            "less detail",
            "reduce detail",
            "lower detail",
            "remove detail",
            "less clarity",
            "reduce clarity",
            "по-малко детайл",
            "намали детайла",
            "махни детайла",
            "по-малко яснота",
        )
        val wantsMoreDetail = normalized.containsAny(
                "more detail",
                "increase detail",
                "add detail",
                "boost detail",
                "clearer",
                "crisper",
                "more clarity",
                "detail recovery",
                "повече детайл",
                "увеличи детайла",
                "добави детайл",
                "по-ясен",
                "по-чист",
                "повече яснота",
        )
        if (wantsLessDetail || wantsMoreDetail) {
            recognized = true
            val direction = if (wantsLessDetail) -1 else 1
            updated = updated.copy(
                detailRecoveryEnabled = wantsMoreDetail || updated.detailRecoveryEnabled,
                detailAmountPercent =
                    (updated.detailAmountPercent + direction * 10).coerceIn(0, 100),
                detailTransientsPercent =
                    (updated.detailTransientsPercent + direction * 10).coerceIn(0, 100),
            )
            descriptions += if (wantsMoreDetail) {
                "Increase detail recovery and transient definition"
            } else {
                "Reduce detail recovery and transient emphasis"
            }
        }

        if (
            normalized.containsAny(
                "wider",
                "increase width",
                "add width",
                "widen the stage",
                "more immersive",
                "surround",
                "bigger stage",
                "по-широк",
                "по-обгръщащ",
                "съраунд",
                "по-голяма сцена",
                "увеличи ширината",
                "добави ширина",
            )
        ) {
            recognized = true
            updated = updated.copy(
                immersiveFieldEnabled = true,
                immersiveAmountPercent = (updated.immersiveAmountPercent + 10).coerceIn(0, 100),
                immersiveWidthPercent = (updated.immersiveWidthPercent + 15).coerceIn(0, 100),
            )
            descriptions += "Widen the immersive sound stage"
        }

        if (
            normalized.containsAny(
                "narrower",
                "reduce width",
                "decrease width",
                "remove width",
                "less immersive",
                "по-тесен",
                "по-малко обгръщащ",
                "намали ширината",
                "махни ширината",
            )
        ) {
            recognized = true
            updated = updated.copy(
                immersiveAmountPercent = (updated.immersiveAmountPercent - 10).coerceIn(0, 100),
                immersiveWidthPercent = (updated.immersiveWidthPercent - 15).coerceIn(0, 100),
                immersiveRoomPercent = (updated.immersiveRoomPercent - 10).coerceIn(0, 100),
            )
            descriptions += "Reduce stage width and room ambience"
        }

        val wantsLessReverb = normalized.containsAny(
            "less reverb",
            "reduce reverb",
            "decrease reverb",
            "remove reverb",
            "less echo",
            "reduce echo",
            "decrease echo",
            "remove echo",
            "drier",
            "smaller room",
            "по-малко реверберация",
            "намали реверберацията",
            "по-малко ехо",
            "намали ехото",
            "махни ехото",
            "по-сух",
            "по-малка стая",
        )
        val wantsMoreReverb = normalized.containsAny(
            "more reverb",
            "increase reverb",
            "add reverb",
            "boost reverb",
            "more echo",
            "increase echo",
            "add echo",
            "boost echo",
            "more ambience",
            "larger room",
            "concert hall",
            "повече реверберация",
            "увеличи реверберацията",
            "повече ехо",
            "увеличи ехото",
            "добави ехо",
            "повече атмосфера",
            "по-голяма зала",
        )
        if (wantsLessReverb || wantsMoreReverb) {
            recognized = true
            val direction = if (wantsLessReverb) -1 else 1
            updated = updated.copy(
                reverbEnabled = wantsMoreReverb || updated.reverbEnabled,
                reverbAmountPercent =
                    (updated.reverbAmountPercent + direction * 10).coerceIn(0, 100),
                reverbSpacePercent =
                    (updated.reverbSpacePercent + direction * 10).coerceIn(0, 100),
                reverbDecayMilliseconds =
                    (updated.reverbDecayMilliseconds + direction * 300).coerceIn(300, 5000),
            )
            descriptions += if (wantsMoreReverb) {
                "Add a longer, more spacious reverberation tail"
            } else {
                "Shorten and reduce the reverberation tail"
            }
        }

        if (!recognized) {
            return AssistantPlanResult.NotUnderstood(
                "Try a tonal request such as more bass, clearer vocals, less harshness, or a saved profile name",
            )
        }

        if (bandDeltas.any { it != 0 }) {
            val gains = current.bandGainsMillibels.mapIndexed { index, gain ->
                (gain + bandDeltas[index]).coerceIn(-1200, 1200)
            }
            updated = updated.copy(
                equalizerEnabled = true,
                bandGainsMillibels = gains,
            )
        }

        val changes = describeChanges(current, updated)
        if (changes.isEmpty()) {
            return AssistantPlanResult.NotUnderstood("Those settings are already at their safe limit")
        }
        return AssistantPlanResult.Proposed(
            AssistantProposal(
                title = descriptions.firstOrNull() ?: "Adjust the sound",
                explanation = descriptions.distinct().joinToString(". ") + ".",
                baseSettings = current,
                settings = updated,
                changes = changes,
            ),
        )
    }

    fun planDirectControl(
        command: String,
        current: AudioFreedomSettings,
        profiles: List<AudioFreedomProfile>,
    ): AssistantPlanResult? {
        val normalized = normalizeCommand(command)
        if (normalized.isBlank()) return null
        if (
            normalized.containsAny(
                "undo",
                "go back",
                "restore previous",
                "previous settings",
                "отмени",
                "върни предишните",
                "предишни настройки",
            )
        ) {
            return AssistantPlanResult.Undo
        }

        findRequestedProfile(normalized, profiles)?.let { profile ->
            return profileProposal(current, profile)
        }

        var updated = current
        val requestedStates = mutableListOf<String>()

        fun applyToggle(
            label: String,
            aliases: List<String>,
            currentlyEnabled: Boolean,
            update: (AudioFreedomSettings, Boolean) -> AudioFreedomSettings,
        ) {
            val requested = requestedToggle(normalized, aliases) ?: return
            requestedStates += "$label ${if (requested) "on" else "off"}"
            if (requested != currentlyEnabled) updated = update(updated, requested)
        }

        applyToggle(
            label = "Equalizer",
            aliases = listOf("equalizer", "eq", "еквалайзер", "еквалайзера"),
            currentlyEnabled = current.equalizerEnabled,
            update = { settings, enabled -> settings.copy(equalizerEnabled = enabled) },
        )
        applyToggle(
            label = "Bass foundation",
            aliases = listOf(
                "bass foundation",
                "dynamic bass",
                "bass boost",
                "бас",
                "баса",
            ),
            currentlyEnabled = current.dynamicBassEnabled,
            update = { settings, enabled -> settings.copy(dynamicBassEnabled = enabled) },
        )
        applyToggle(
            label = "Detail recovery",
            aliases = listOf(
                "detail recovery",
                "detail enhancer",
                "crystalizer",
                "crystallizer",
                "възстановяване на детайла",
            ),
            currentlyEnabled = current.detailRecoveryEnabled,
            update = { settings, enabled -> settings.copy(detailRecoveryEnabled = enabled) },
        )
        applyToggle(
            label = "Immersive field",
            aliases = listOf(
                "immersive field",
                "immersive",
                "surround",
                "spatial sound",
                "обгръщащ звук",
                "пространствен звук",
            ),
            currentlyEnabled = current.immersiveFieldEnabled,
            update = { settings, enabled -> settings.copy(immersiveFieldEnabled = enabled) },
        )
        applyToggle(
            label = "Reverb",
            aliases = listOf(
                "reverb",
                "reverberation",
                "ambience",
                "echo",
                "реверберация",
                "реверберацията",
                "ехо",
                "ехото",
            ),
            currentlyEnabled = current.reverbEnabled,
            update = { settings, enabled -> settings.copy(reverbEnabled = enabled) },
        )
        applyToggle(
            label = "Output protection",
            aliases = listOf(
                "limiter",
                "output protection",
                "лимитер",
                "защита на изхода",
            ),
            currentlyEnabled = current.limiterEnabled,
            update = { settings, enabled -> settings.copy(limiterEnabled = enabled) },
        )

        if (requestedStates.isEmpty()) return null
        if (updated == current) {
            return AssistantPlanResult.NotUnderstood(
                requestedStates.joinToString(" and ") + " already selected",
            )
        }
        return AssistantPlanResult.Proposed(
            AssistantProposal(
                title = if (requestedStates.size == 1) {
                    "Turn ${requestedStates.single()}"
                } else {
                    "Update effects"
                },
                explanation = requestedStates.joinToString(". ") + ".",
                baseSettings = current,
                settings = updated,
                changes = describeChanges(current, updated),
            ),
        )
    }

    private fun profileProposal(
        current: AudioFreedomSettings,
        profile: AudioFreedomProfile,
    ) = AssistantPlanResult.Proposed(
        AssistantProposal(
            title = "Load ${profile.name}",
            explanation = "Replace the current DSP settings with this saved profile.",
            baseSettings = current,
            settings = profile.settings,
            changes = describeChanges(current, profile.settings),
            selectedProfileId = profile.id,
            canApplyAutomatically = false,
        ),
    )

    private fun findRequestedProfile(
        command: String,
        profiles: List<AudioFreedomProfile>,
    ): AudioFreedomProfile? {
        val requestsProfile = command.containsAny(
            "profile",
            "load",
            "switch",
            "select",
            "choose",
            "apply",
            "activate",
            "use",
            "профил",
            "зареди",
            "смени",
            "избери",
            "приложи",
            "активирай",
            "използвай",
        )
        return profiles
            .sortedByDescending { it.name.length }
            .firstOrNull { profile ->
                val profileName = normalizeCommand(profile.name)
                profileName.isNotBlank() &&
                    (command == profileName || requestsProfile && command.containsPhrase(profileName))
            }
    }

    private fun describeChanges(
        before: AudioFreedomSettings,
        after: AudioFreedomSettings,
    ): List<String> = buildList {
        if (before.equalizerEnabled != after.equalizerEnabled) {
            add("Equalizer ${if (after.equalizerEnabled) "on" else "off"}")
        }
        if (before.preampMillibels != after.preampMillibels) {
            add("Preamp ${formatDelta(before.preampMillibels, after.preampMillibels)}")
        }
        before.bandGainsMillibels.zip(after.bandGainsMillibels).forEachIndexed { index, pair ->
            if (pair.first != pair.second) {
                add("${EqualizerBandLabels[index]} Hz ${formatDelta(pair.first, pair.second)}")
            }
        }
        if (before.dynamicBassEnabled != after.dynamicBassEnabled) {
            add("Bass foundation ${if (after.dynamicBassEnabled) "on" else "off"}")
        }
        if (before.bassBoostMillibels != after.bassBoostMillibels) {
            add("Bass strength ${formatDelta(before.bassBoostMillibels, after.bassBoostMillibels)}")
        }
        if (before.bassCutoffHz != after.bassCutoffHz) {
            add("Bass range ${after.bassCutoffHz} Hz")
        }
        if (before.detailRecoveryEnabled != after.detailRecoveryEnabled) {
            add("Detail recovery ${if (after.detailRecoveryEnabled) "on" else "off"}")
        }
        if (before.detailAmountPercent != after.detailAmountPercent) {
            add("Detail amount ${after.detailAmountPercent}%")
        }
        if (before.detailTransientsPercent != after.detailTransientsPercent) {
            add("Transients ${after.detailTransientsPercent}%")
        }
        if (before.immersiveFieldEnabled != after.immersiveFieldEnabled) {
            add("Immersive field ${if (after.immersiveFieldEnabled) "on" else "off"}")
        }
        if (before.immersiveAmountPercent != after.immersiveAmountPercent) {
            add("Immersive amount ${after.immersiveAmountPercent}%")
        }
        if (before.immersiveWidthPercent != after.immersiveWidthPercent) {
            add("Stage width ${after.immersiveWidthPercent}%")
        }
        if (before.immersiveRoomPercent != after.immersiveRoomPercent) {
            add("Room ${after.immersiveRoomPercent}%")
        }
        if (before.reverbEnabled != after.reverbEnabled) {
            add("Reverb ${if (after.reverbEnabled) "on" else "off"}")
        }
        if (before.reverbAmountPercent != after.reverbAmountPercent) {
            add("Reverb amount ${after.reverbAmountPercent}%")
        }
        if (before.reverbSpacePercent != after.reverbSpacePercent) {
            add("Reverb space ${after.reverbSpacePercent}%")
        }
        if (before.reverbDecayMilliseconds != after.reverbDecayMilliseconds) {
            add("Reverb decay ${after.reverbDecayMilliseconds / 1000F} s")
        }
        if (before.limiterEnabled != after.limiterEnabled) {
            add("Output protection ${if (after.limiterEnabled) "on" else "off"}")
        }
    }

    private fun formatDelta(before: Int, after: Int): String {
        val delta = (after - before) / 100F
        return if (delta >= 0F) "+%.1f dB".format(delta) else "%.1f dB".format(delta)
    }
}

private fun String.containsAny(vararg candidates: String): Boolean = candidates.any(::contains)

private fun normalizeCommand(value: String): String = value
    .lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()
    .replace(Regex("\\s+"), " ")

private fun String.containsPhrase(phrase: String): Boolean =
    " $this ".contains(" $phrase ")

private fun requestedToggle(command: String, aliases: List<String>): Boolean? {
    val offPatterns = listOf(
        "turn off %s",
        "turn off the %s",
        "turn %s off",
        "turn the %s off",
        "switch off %s",
        "switch %s off",
        "disable %s",
        "disable the %s",
        "set %s off",
        "%s off",
        "изключи %s",
        "спри %s",
    )
    val onPatterns = listOf(
        "turn on %s",
        "turn on the %s",
        "turn %s on",
        "turn the %s on",
        "switch on %s",
        "switch %s on",
        "enable %s",
        "enable the %s",
        "set %s on",
        "%s on",
        "включи %s",
        "пусни %s",
    )
    aliases.forEach { alias ->
        if (offPatterns.any { command.containsPhrase(it.format(alias)) }) return false
    }
    aliases.forEach { alias ->
        if (onPatterns.any { command.containsPhrase(it.format(alias)) }) return true
    }
    return null
}
