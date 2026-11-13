package com.aetheria.aura.actions

import org.json.JSONObject

/**
 * Sealed class representing possible actions that Aura can execute.
 * Parsed from JSON format: {"action":"<name>","args":{...}}
 */
sealed class AuraAction {

    /** Action to open an app by matching the target against installed app labels */
    data class OpenApp(val target: String) : AuraAction()

    /** Action to call a contact by name */
    data class CallContact(val name: String) : AuraAction()

    /** Action to send a text message to a contact */
    data class SendText(val name: String, val body: String) : AuraAction()

    /** Action to perform a web search */
    data class WebSearch(val query: String) : AuraAction()

    /** Action to navigate to a web URL (open browser + navigate) */
    data class NavigateWeb(val url: String) : AuraAction()

    /** Action to take a screenshot via Shizuku */
    data class TakeScreenshot(val dummy: String = "") : AuraAction()

    /** Action to type text via screen control */
    data class TypeText(val text: String) : AuraAction()

    /** Action to tap at coordinates via screen control */
    data class Tap(val x: Int, val y: Int) : AuraAction()

    /** Action to ask Hermes (catch-all for non-phone actions) */
    data class AskHermes(val input: String) : AuraAction()

    /** Action for any unrecognized or unhandled command */
    object Unknown : AuraAction()

    /** Parse JSON into an AuraAction sealed class.
     *  Returns Unknown if parsing fails or the action is not recognized.
     */
    companion object {
        @JvmStatic
        fun parse(json: String): AuraAction {
            return try {
                val obj = JSONObject(json)
                val action = obj.getString("action")
                val args = obj.optJSONObject("args")

                return when (action) {
                    "open_app" -> {
                        val target = args?.optString("target") ?: ""
                        AuraAction.OpenApp(target)
                    }
                    "call_contact" -> {
                        val name = args?.getString("name") ?: ""
                        AuraAction.CallContact(name)
                    }
                    "send_text" -> {
                        val name = args?.getString("name") ?: ""
                        val body = args?.getString("body") ?: ""
                        AuraAction.SendText(name, body)
                    }
                    "web_search" -> {
                        val query = args?.getString("query") ?: ""
                        AuraAction.WebSearch(query)
                    }
                    "navigate_web" -> {
                        val url = args?.getString("url") ?: ""
                        AuraAction.NavigateWeb(url)
                    }
                    "take_screenshot" -> AuraAction.TakeScreenshot()
                    "type_text" -> {
                        val text = args?.getString("text") ?: ""
                        AuraAction.TypeText(text)
                    }
                    "tap" -> {
                        val x = args?.getInt("x") ?: 0
                        val y = args?.getInt("y") ?: 0
                        AuraAction.Tap(x, y)
                    }
                    "ask_hermes" -> {
                        val input = args?.getString("input") ?: ""
                        AuraAction.AskHermes(input)
                    }
                    else -> AuraAction.Unknown
                }
            } catch (e: Exception) {
                // Parsing failure returns Unknown
                AuraAction.Unknown
            }
        }
    }
}