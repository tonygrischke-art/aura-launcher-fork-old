package com.aetheria.aura.brain

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class Qwen3Action(
    val action: String,
    val params: Map<String, String>
)

/**
 * Parses Qwen3 tool-call JSON from LLM output.
 * Expected format: {"action": "open_app", "packageName": "com.android.chrome"}
 */
class Qwen3ToolCallParser {
    companion object {
        private const val TAG = "Qwen3ToolCallParser"
        private val json = Json { ignoreUnknownKeys = true }

        fun parseFromLlmOutput(llmOutput: String): Qwen3Action? {
            return try {
                val jsonStart = llmOutput.indexOf('{')
                val jsonEnd = llmOutput.lastIndexOf('}')

                if (jsonStart == -1 || jsonEnd == -1 || jsonStart >= jsonEnd) {
                    Log.d(TAG, "No valid JSON found in LLM output")
                    return null
                }

                val jsonStr = llmOutput.substring(jsonStart, jsonEnd + 1)
                val jsonObject = json.parseToJsonElement(jsonStr) as? JsonObject
                    ?: return null

                val action = (jsonObject["action"] as? JsonPrimitive)?.content
                    ?: return null

                val params = mutableMapOf<String, String>()
                jsonObject.forEach { entry ->
                    val key = entry.key
                    val value = entry.value
                    if (key != "action") {
                        val primitive = value as? JsonPrimitive
                        if (primitive != null) {
                            params[key] = primitive.content
                        }
                    }
                }

                Log.i(TAG, "Parsed action=$action with ${params.size} params")
                return Qwen3Action(action = action, params = params)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse Qwen3 action: ${e.message}", e)
                null
            }
        }

        /**
         * Maps Qwen3 action to Aura's ActionExecutor action types.
         * Returns Pair<actionType, params> for ActionExecutor.
         */
        fun mapToActionExecutor(qwen3Action: Qwen3Action): Pair<String, Map<String, String>>? {
            return when (qwen3Action.action) {
                "open_app" -> Pair("OPEN_APP", mapOf(
                    "target" to (qwen3Action.params["packageName"] ?: qwen3Action.params["target"] ?: "")
                ))
                "make_call" -> Pair("CALL_CONTACT", mapOf(
                    "name" to (qwen3Action.params["phone"] ?: qwen3Action.params["name"] ?: "")
                ))
                "send_sms" -> Pair("SEND_TEXT", mapOf(
                    "name" to (qwen3Action.params["phone"] ?: qwen3Action.params["contact"] ?: ""),
                    "body" to (qwen3Action.params["message"] ?: qwen3Action.params["text"] ?: "")
                ))
                "web_search", "open_url" -> Pair("WEB_SEARCH", mapOf(
                    "query" to (qwen3Action.params["query"] ?: qwen3Action.params["url"] ?: "")
                ))
                "take_screenshot" -> Pair("TAKE_SCREENSHOT", emptyMap())
                "type_text" -> Pair("TYPE_TEXT", mapOf(
                    "text" to (qwen3Action.params["text"] ?: "")
                ))
                "tap" -> Pair("TAP", mapOf(
                    "x" to (qwen3Action.params["x"] ?: ""),
                    "y" to (qwen3Action.params["y"] ?: "")
                ))
                else -> {
                    Log.w(TAG, "Unmapped action: ${qwen3Action.action}")
                    null
                }
            }
        }
    }
}