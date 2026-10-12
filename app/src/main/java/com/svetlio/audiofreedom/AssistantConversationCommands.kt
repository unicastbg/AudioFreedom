package com.svetlio.audiofreedom

internal enum class AssistantHelpTopic { General, Bass, Treble, Mids, Equalizer, Profiles, Effects, Reverb, Immersive, Detail, Leveler, Undo, Music, Unsupported }

internal sealed interface AssistantConversationControl {
    data object Cancel : AssistantConversationControl
    data class Help(val topic: AssistantHelpTopic) : AssistantConversationControl
}

internal object AssistantConversationCommands {
    const val MaximumHelpReplies = 3
    private val cancellations = setOf("cancel", "cancel command", "cancel the command", "stop", "stop listening",
        "never mind", "nevermind", "abort", "отказ", "откажи", "откажи командата", "отмени командата",
        "спри", "спри да слушаш", "прекрати", "прекрати командата")
    private val generalHelp = setOf("help", "help me", "what can you do", "what can i say", "show commands",
        "помощ", "какво можеш", "какво можеш да правиш", "какво мога да кажа", "какви са командите")
    private val topics = mapOf(
        "bass" to AssistantHelpTopic.Bass, "бас" to AssistantHelpTopic.Bass, "баса" to AssistantHelpTopic.Bass,
        "treble" to AssistantHelpTopic.Treble, "highs" to AssistantHelpTopic.Treble, "високи" to AssistantHelpTopic.Treble,
        "високите" to AssistantHelpTopic.Treble, "mids" to AssistantHelpTopic.Mids, "midrange" to AssistantHelpTopic.Mids,
        "среди" to AssistantHelpTopic.Mids, "средите" to AssistantHelpTopic.Mids,
        "equalizer" to AssistantHelpTopic.Equalizer, "eq" to AssistantHelpTopic.Equalizer,
        "еквалайзер" to AssistantHelpTopic.Equalizer, "еквалайзера" to AssistantHelpTopic.Equalizer,
        "profiles" to AssistantHelpTopic.Profiles, "profile" to AssistantHelpTopic.Profiles,
        "профили" to AssistantHelpTopic.Profiles, "профилите" to AssistantHelpTopic.Profiles,
        "effects" to AssistantHelpTopic.Effects, "ефекти" to AssistantHelpTopic.Effects, "ефектите" to AssistantHelpTopic.Effects,
        "reverb" to AssistantHelpTopic.Reverb, "echo" to AssistantHelpTopic.Reverb,
        "ехо" to AssistantHelpTopic.Reverb, "реверберация" to AssistantHelpTopic.Reverb, "реверберацията" to AssistantHelpTopic.Reverb,
        "immersive field" to AssistantHelpTopic.Immersive, "surround" to AssistantHelpTopic.Immersive,
        "обгръщащ звук" to AssistantHelpTopic.Immersive,
        "detail" to AssistantHelpTopic.Detail, "detail recovery" to AssistantHelpTopic.Detail, "детайлност" to AssistantHelpTopic.Detail,
        "volume leveler" to AssistantHelpTopic.Leveler, "normalizer" to AssistantHelpTopic.Leveler,
        "music" to AssistantHelpTopic.Music, "player" to AssistantHelpTopic.Music,
        "media player" to AssistantHelpTopic.Music,
        "undo" to AssistantHelpTopic.Undo, "отмяна" to AssistantHelpTopic.Undo)

    fun recognize(command: String): AssistantConversationControl? {
        val text = HandsFreeVoicePolicy.normalize(command).removePrefix("please ").removeSuffix(" please")
            .removePrefix("моля ").removeSuffix(" моля")
        if (text in cancellations) return AssistantConversationControl.Cancel
        if (text in generalHelp) return AssistantConversationControl.Help(AssistantHelpTopic.General)
        if (listOf("load ", "switch ", "зареди ", "смени ").any(text::startsWith)) return null
        val topic = when {
            text.startsWith("help with ") -> text.removePrefix("help with ")
            text.startsWith("help for ") -> text.removePrefix("help for ")
            text.startsWith("help ") -> text.removePrefix("help ")
            text.startsWith("помощ за ") -> text.removePrefix("помощ за ")
            text.endsWith(" help") && !text.startsWith("load ") && !text.startsWith("switch ") -> text.removeSuffix(" help")
            text.endsWith(" помощ") -> text.removeSuffix(" помощ")
            else -> return null
        }
        return AssistantConversationControl.Help(topics[topic] ?: AssistantHelpTopic.Unsupported)
    }

    fun response(control: AssistantConversationControl, language: AssistantLanguage, musicEnabled: Boolean = false): String {
        val bulgarian = language == AssistantLanguage.Bulgarian
        if (control == AssistantConversationControl.Cancel) return if (bulgarian) "Отказвам командата." else "Command cancelled."
        val topic = (control as AssistantConversationControl.Help).topic
        if (topic == AssistantHelpTopic.Music) return if (bulgarian) {
            if (musicEnabled) "За Media Player кажете на английски: Play trance, Play The Prodigy, Next track, Pause music, или Stop music. Cancel прекратява слушането."
            else "За музикални команди инсталирайте съвместим Media Player и включете препращането в настройките."
        } else {
            if (musicEnabled) "Say play trance, play The Prodigy, shuffle drum and bass, next track, pause music, or stop music. Music requests go to Media Player immediately. Cancel stops listening."
            else "Install compatible Media Player and enable music forwarding in settings to use music commands."
        }
        if (topic == AssistantHelpTopic.General && musicEnabled) return if (bulgarian) {
            "Мога да променям звука, да управлявам ефекти, да зареждам профили и да връщам последната промяна. Мога да препращам музикални команди към Media Player. Кажете: Music help."
        } else {
            "I can adjust bass, mids and treble, control effects, load profiles, undo sound changes, and pass playback requests to Media Player. Try bass help, profiles help, or music help."
        }
        return if (bulgarian) when (topic) {
            AssistantHelpTopic.Music -> error("Handled above")
            AssistantHelpTopic.Leveler -> "За Volume Leveler кажете на английски: turn on volume leveler, или turn off volume leveler. Изберете Gentle, Balanced или Night в приложението."
            AssistantHelpTopic.General -> "Мога да променям баса, средите и високите, да включвам ефекти, да зареждам профили и да връщам последната промяна. Опитайте: помощ за баса, или помощ за профилите."
            AssistantHelpTopic.Bass -> "Кажете: добави бас, намали баса, или включи баса. Променям ниските честоти и настройките на баса с малки стъпки."
            AssistantHelpTopic.Treble -> "Кажете: увеличи високите, или намали високите. Можете да комбинирате: увеличи баса и намали високите."
            AssistantHelpTopic.Mids -> "Кажете: увеличи средите, намали средите, или по-ясни вокали. Промените са с малки стъпки."
            AssistantHelpTopic.Equalizer -> "Кажете: включи еквалайзера, изключи еквалайзера, добави бас, увеличи средите, или намали високите. Можете да комбинирате промени."
            AssistantHelpTopic.Profiles -> "За профил кажете: зареди профил, последвано от точното му име. Гласовите команди зареждат съществуващи профили. Нови профили се запазват в приложението."
            AssistantHelpTopic.Effects -> "Мога да включвам и изключвам еквалайзера, баса, детайлността, обгръщащия звук, реверберацията и защитата на изхода. Например: включи реверберацията."
            AssistantHelpTopic.Reverb -> "Кажете: добави ехо, намали ехото, включи реверберацията, или изключи реверберацията. Ехо и реверберация означават един и същ ефект."
            AssistantHelpTopic.Immersive -> "Кажете: включи обгръщащия звук, изключи обгръщащия звук, увеличи ширината, или намали ширината."
            AssistantHelpTopic.Detail -> "Кажете: повече детайл, по-малко детайл, включи детайлността, или изключи детайлността."
            AssistantHelpTopic.Undo -> "Кажете: отмени, за да върнете настройките преди последната промяна от асистента. Кажете: отказ, за да прекратите текущата команда."
            AssistantHelpTopic.Unsupported -> "Помагам с настройки на звука, ефекти и профили в AudioFreedom. Не управлявам радио или музикалния плеър. Кажете: помощ, за общите команди."
        } else when (topic) {
            AssistantHelpTopic.Music -> error("Handled above")
            AssistantHelpTopic.Leveler -> "Say turn on volume leveler or turn off volume leveler. Choose Gentle, Balanced or Night in the app."
            AssistantHelpTopic.General -> "I can adjust bass, mids and treble, control effects, load profiles, or undo the last change. Try bass help, equalizer help, or profiles help."
            AssistantHelpTopic.Bass -> "Say add bass, reduce bass, or turn on bass foundation. I adjust low frequencies and bass settings in small steps."
            AssistantHelpTopic.Treble -> "Say increase treble or lower highs. You can combine changes, such as increase bass and lower treble."
            AssistantHelpTopic.Mids -> "Say increase mids, reduce mids, or clearer vocals. Changes use small steps."
            AssistantHelpTopic.Equalizer -> "Say turn on equalizer, turn off equalizer, add bass, increase mids, or lower treble. You can combine tonal changes."
            AssistantHelpTopic.Profiles -> "Say switch profile to, followed by its exact saved name. I can load existing profiles. Save new profiles in the app."
            AssistantHelpTopic.Effects -> "I can enable or disable equalizer, bass foundation, detail recovery, immersive field, reverb, volume leveler and output protection. For example, turn on volume leveler."
            AssistantHelpTopic.Reverb -> "Say add echo, reduce reverb, turn on reverb, or turn off reverb. Echo and reverb refer to the same effect."
            AssistantHelpTopic.Immersive -> "Say turn on immersive field, turn off immersive field, wider stage, or narrower stage."
            AssistantHelpTopic.Detail -> "Say more detail, less detail, turn on detail recovery, or turn off detail recovery."
            AssistantHelpTopic.Undo -> "Say undo to restore settings before the last assistant change. Say cancel to end the current command without undoing anything."
            AssistantHelpTopic.Unsupported -> if (musicEnabled) "I cannot control a radio or other music players. Say music help for Media Player commands, or help for sound controls."
                else "I help with AudioFreedom sound settings, effects and profiles. I cannot control a radio or music player. Say help for the available commands."
        }
    }
}
