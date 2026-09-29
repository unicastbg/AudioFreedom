package com.svetlio.audiofreedom

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal object AssistantFeedbackStore {
    private const val Preferences = "audiofreedom_assistant_feedback"
    private const val KeyRecords = "records"
    private const val MaximumRecords = 100

    fun record(
        context: Context,
        candidate: AssistantFeedbackCandidate,
        accepted: Boolean,
    ) {
        val preferences = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        val records = runCatching {
            JSONArray(preferences.getString(KeyRecords, "[]"))
        }.getOrElse { JSONArray() }
        while (records.length() >= MaximumRecords) records.remove(0)
        records.put(
            JSONObject()
                .put("command", candidate.command.trim().take(500))
                .put("changes", JSONArray(candidate.changes.take(16)))
                .put("accepted", accepted)
                .put("created_at", System.currentTimeMillis()),
        )
        preferences.edit().putString(KeyRecords, records.toString()).apply()
    }

    fun recentExamples(context: Context, limit: Int = 6): List<AssistantFeedbackExample> {
        val raw = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            .getString(KeyRecords, "[]")
        val records = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (records.length() - 1 downTo 0)
            .asSequence()
            .mapNotNull { index ->
                runCatching {
                    val item = records.getJSONObject(index)
                    val changes = item.optJSONArray("changes") ?: JSONArray()
                    AssistantFeedbackExample(
                        command = item.optString("command").safePromptText(),
                        changes = List(changes.length()) { changeIndex ->
                            changes.optString(changeIndex).safePromptText()
                        }.filter(String::isNotBlank),
                        accepted = item.optBoolean("accepted"),
                    )
                }.getOrNull()
            }
            .filter { it.command.isNotBlank() && it.changes.isNotEmpty() }
            .take(limit.coerceIn(0, 12))
            .toList()
            .reversed()
    }
}

internal data class AssistantFeedbackCandidate(
    val command: String,
    val changes: List<String>,
)

internal data class AssistantFeedbackExample(
    val command: String,
    val changes: List<String>,
    val accepted: Boolean,
)

private fun String.safePromptText(): String =
    replace(Regex("[\\r\\n\\p{Cntrl}]+"), " ").trim().take(160)
