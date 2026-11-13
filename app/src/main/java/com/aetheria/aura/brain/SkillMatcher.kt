package com.aetheria.aura.brain

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Matches user queries against seeded skills.
 * Runs BEFORE any LLM call — zero-cost for simple queries.
 */
class SkillMatcher {

    companion object {
        private const val TAG = "SkillMatcher"
    }

    sealed interface SkillResult {
        data class Static(val response: String) : SkillResult
        data class RequiresLlm(val actionType: String, val params: Map<String, String>) : SkillResult
    }

    data class Skill(
        val name: String,
        val triggerPatterns: List<String>,
        val staticResponse: String? = null,  // If null, requires LLM
        val actionType: String? = null,       // For LLM skills
        val defaultParams: Map<String, String> = emptyMap()
    )

    private val seedSkills = mutableListOf<Skill>()

    init {
        seedDefaults()
    }

    private fun seedDefaults() {
        seedSkills.addAll(listOf(
            // Identity queries (handled by CommandRouter now, but kept for reference)
            Skill(
                name = "who_am_i",
                triggerPatterns = listOf("who am i", "whoami"),
                staticResponse = "Identity query handled by router"
            ),
            Skill(
                name = "whats_my_name",
                triggerPatterns = listOf("what's my name", "what is my name", "whats my name"),
                staticResponse = "Identity query handled by router"
            ),
            Skill(
                name = "where_am_i",
                triggerPatterns = listOf("where am i", "whereami"),
                staticResponse = "Identity query handled by router"
            ),

            // Static info skills (no LLM needed)
            Skill(
                name = "battery_check",
                triggerPatterns = listOf("battery", "battery level", "battery percent", "how much battery", "what's my battery", "whats my battery"),
                staticResponse = "Battery is {battery}%."
            ),
            Skill(
                name = "time_check",
                triggerPatterns = listOf("what time", "what's the time", "whats the time", "current time", "tell me the time", "time is it"),
                staticResponse = "It's {time} on {date}."
            ),
            Skill(
                name = "date_check",
                triggerPatterns = listOf("what's the date", "whats the date", "what date", "today's date", "current date", "what day"),
                staticResponse = "Today is {date}."
            ),
            Skill(
                name = "wifi_status",
                triggerPatterns = listOf("wifi status", "wi-fi status", "wifi name", "what's my wifi", "whats my wifi", "connected to wifi", "wifi network"),
                staticResponse = "{wifi}."
            ),

            // Action skills (require LLM/tool execution)
            Skill(
                name = "open_app",
                triggerPatterns = listOf("open ", "launch ", "start "),
                actionType = "OPEN_APP",
                defaultParams = emptyMap()
            ),
            Skill(
                name = "make_call",
                triggerPatterns = listOf("call ", "dial ", "phone "),
                actionType = "CALL_CONTACT",
                defaultParams = emptyMap()
            ),
            Skill(
                name = "send_sms",
                triggerPatterns = listOf("text ", "send text", "send message", "message "),
                actionType = "SEND_TEXT",
                defaultParams = emptyMap()
            ),
            Skill(
                name = "web_search",
                triggerPatterns = listOf("search ", "google ", "look up ", "lookup ", "find "),
                actionType = "WEB_SEARCH",
                defaultParams = emptyMap()
            ),
            Skill(
                name = "take_screenshot",
                triggerPatterns = listOf("screenshot", "screen shot", "capture screen"),
                actionType = "TAKE_SCREENSHOT",
                defaultParams = emptyMap()
            ),
            Skill(
                name = "type_text",
                triggerPatterns = listOf("type ", "input "),
                actionType = "TYPE_TEXT",
                defaultParams = emptyMap()
            ),
            Skill(
                name = "tap",
                triggerPatterns = listOf("tap "),
                actionType = "TAP",
                defaultParams = emptyMap()
            )
        ))
    }

    /**
     * Match a query against seeded skills.
     * Returns SkillResult.Static for instant responses, SkillResult.RequiresLlm for LLM-needed actions,
     * or null if no match.
     */
    fun match(query: String): SkillResult? {
        val q = query.lowercase().trim()
        if (q.isBlank()) return null

        for (skill in seedSkills) {
            for (pattern in skill.triggerPatterns) {
                val patternLower = pattern.lowercase()
                // For action skills (requires LLM), only match at start of query to avoid false positives
                // For static skills, allow matching anywhere in the query
                val matches = if (skill.staticResponse == null) {
                    q.startsWith(patternLower)
                } else {
                    q.startsWith(patternLower) || q.contains(patternLower)
                }
                if (matches) {
                    try {
                        Log.i(TAG, "Matched skill: '${skill.name}' (requiresLlm=${skill.staticResponse == null}) for query: '${query.take(40)}'")
                    } catch (e: Exception) {
                        // Log not available in tests
                    }
                    return if (skill.staticResponse != null) {
                        SkillResult.Static(skill.staticResponse!!)
                    } else {
                        SkillResult.RequiresLlm(skill.actionType!!, skill.defaultParams)
                    }
                }
            }
        }
        return null
    }

    /** Get all seeded skills for debugging/testing */
    fun getSeedSkills(): List<Skill> = seedSkills.toList()
}