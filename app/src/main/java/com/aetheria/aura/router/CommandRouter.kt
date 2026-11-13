package com.aetheria.aura.router

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/** Screen control verbs that should route to ScreenAgent (shizuku) */
private val SCREEN_CONTROL_VERBS = setOf(
    "tap", "swipe", "type", "input", "key", "press", "longpress", "long-press",
    "screenshot", "screen", "read", "dump", "uiautomator", "uidump", "scroll"
)

/** Identity-related queries that should short-circuit to local response */
private val IDENTITY_QUERIES = setOf(
    "who am i", "whoami",
    "what's my name", "what is my name", "whats my name",
    "where am i", "whereami"
)

/** Sealed class representing the possible route outcomes from CommandRouter. */
sealed class AuraActionRoute {
    data class OpenApp(val target: String) : AuraActionRoute()
    data class CallContact(val name: String) : AuraActionRoute()
    data class SendText(val name: String, val body: String) : AuraActionRoute()
    data class WebSearch(val query: String) : AuraActionRoute()
    data class NavigateWeb(val url: String) : AuraActionRoute()
    data class AskHermes(val input: String) : AuraActionRoute()
    data class ShizukuShell(val command: String) : AuraActionRoute()
    /** Screen control action that routes to ScreenAgent (requires Shizuku READY). */
    data class ScreenControl(val command: String) : AuraActionRoute()
    /** Identity query that returns local user profile info. */
    data class IdentityQuery(val queryType: String) : AuraActionRoute() // "name" or "location"
}

/**
 * In-process label -> package map built from launcher-resolvable activities.
 * Refreshes on package add/remove broadcasts.
 */
class AppIndex(private val context: Context) {

    private val labelToPackage = ConcurrentHashMap<String, String>()
    private val packageToLabel = ConcurrentHashMap<String, String>()
    private val normalizedLabels = ConcurrentHashMap<String, String>() // normalized -> original label
    private val buildTimeMs = AtomicReference<Long>(-1L)

    /**
     * Builds the index synchronously. Call on background thread.
     * @return pair of (appCount, buildTimeMs)
     */
    fun build(): Pair<Int, Long> {
        val start = System.currentTimeMillis()
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(intent, 0)

        labelToPackage.clear()
        packageToLabel.clear()
        normalizedLabels.clear()

        for (info in activities) {
            val pkgName = info.activityInfo.packageName
            val label = info.loadLabel(pm).toString()
            if (label.isNotBlank()) {
                labelToPackage[label] = pkgName
                packageToLabel[pkgName] = label
                normalizedLabels[normalize(label)] = label
            }
        }

        val elapsed = System.currentTimeMillis() - start
        buildTimeMs.set(elapsed)
        Log.d("AuraRouter", "AppIndex built: ${labelToPackage.size} apps in ${elapsed}ms")
        return Pair(labelToPackage.size, elapsed)
    }

    /** Normalize for fuzzy matching: lowercase, strip non-alphanum, collapse spaces. */
    private fun normalize(s: String): String {
        return s.lowercase()
            .replace("[^a-z0-9]+".toRegex(), " ")
            .trim()
    }

    /** Find best match for target against app labels.
     * Returns package name if clear best match found (edit distance <= 2, unique), else null. */
    fun findBestMatch(target: String): String? {
        if (labelToPackage.isEmpty()) return null

        val normTarget = normalize(target)
        if (normTarget.isBlank()) return null

        // 1. Exact normalized match
        val exact = normalizedLabels[normTarget]
        if (exact != null) return labelToPackage[exact]

        // 2. Prefix match (normalized)
        val prefixMatches = mutableListOf<String>()
        for ((normLabel, origLabel) in normalizedLabels) {
            if (normLabel.startsWith(normTarget) || normTarget.startsWith(normLabel)) {
                prefixMatches.add(origLabel)
            }
        }
        if (prefixMatches.size == 1) return labelToPackage[prefixMatches[0]]

        // 3. Edit distance <= 2
        var bestLabel: String? = null
        var bestDist = 3
        var tie = false
        for ((normLabel, origLabel) in normalizedLabels) {
            val dist = levenshtein(normTarget, normLabel)
            if (dist <= 2) {
                if (dist < bestDist) {
                    bestDist = dist
                    bestLabel = origLabel
                    tie = false
                } else if (dist == bestDist) {
                    tie = true
                }
            }
        }
        if (!tie && bestLabel != null) return labelToPackage[bestLabel]

        return null
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val cur = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + cost)
                prev = cur
            }
        }
        return dp[b.length]
    }

    fun getBuildTimeMs(): Long = buildTimeMs.get()
    fun getAppCount(): Int = labelToPackage.size
}

/**
 * Pure function command router. No I/O, no Android deps.
 * Input: raw user string. Output: AuraActionRoute.
 */
object CommandRouter {

    private val OPEN_VERBS = setOf("open", "launch", "start", "run")
    private val CALL_VERBS = setOf("call", "phone", "dial")
    private val TEXT_VERBS = setOf("text", "message", "send")
    // search|google|look up|find|search for <query> and exact phrases "what is the weather" / "weather"
    private val SEARCH_VERBS = setOf("search", "google", "look up", "lookup", "find", "weather")
    // Navigation verbs for web URLs
    private val NAVIGATE_VERBS = setOf("go to", "navigate to", "visit", "browse to", "open url", "load")

    /**
     * Route a raw user command to an action route.
     * Pure function - no side effects.
     */
    fun route(input: String): AuraActionRoute {
        val trimmed = input.trim()
        val words = trimmed.split("\\s+".toRegex())
        val firstWord = words.firstOrNull()?.lowercase() ?: ""
        if (trimmed.isBlank()) return AuraActionRoute.AskHermes(input)

        // Check exact phrase "what is the weather" first
        // For "what is the weather", we want query = "the weather"
        val lowerInput = trimmed.lowercase()
        if (lowerInput.startsWith("what is the weather")) {
            val query = if (lowerInput == "what is the weather") "the weather"
            else trimmed.substring("what is the weather".length).trim()
            return if (query.isBlank()) AuraActionRoute.WebSearch("the weather")
            else AuraActionRoute.WebSearch(query)
        }

        // Identity short-circuit: handle "who am i", "what's my name", "where am i" before any LLM call
        if (lowerInput in IDENTITY_QUERIES) {
            return when {
                lowerInput in setOf("who am i", "whoami", "what's my name", "what is my name", "whats my name") ->
                    AuraActionRoute.IdentityQuery("name")
                lowerInput in setOf("where am i", "whereami") ->
                    AuraActionRoute.IdentityQuery("location")
                else -> AuraActionRoute.AskHermes(input)
            }
        }

        if (firstWord.isBlank()) return AuraActionRoute.AskHermes(input)

        // "shizuku:" prefix -> raw shell command via the Shizuku user service (shell uid)
        if (lowerInput.startsWith("shizuku:")) {
            val command = trimmed.substring("shizuku:".length).trim()
            return if (command.isBlank()) AuraActionRoute.AskHermes(input)
            else AuraActionRoute.ShizukuShell(command)
        }

        // Screen control verbs -> route to ScreenControl (will be handled by ScreenAgent)
        if (firstWord in SCREEN_CONTROL_VERBS) {
            return AuraActionRoute.ScreenControl(trimmed)
        }

        return when {
            firstWord in OPEN_VERBS -> {
                val rest = words.drop(1).joinToString(" ")
                // Check for "open <app> and go to <url>" pattern
                if (rest.contains(" and go to ") || rest.contains(" and navigate to ") || rest.contains(" and visit ")) {
                    val parts = rest.split(Regex(" and (go to|navigate to|visit|browse to|open url|load) "))
                    val appTarget = parts.firstOrNull()?.trim() ?: ""
                    val urlTarget = parts.drop(1).firstOrNull()?.trim() ?: ""
                    if (appTarget.isNotBlank() && urlTarget.isNotBlank()) {
                        return AuraActionRoute.NavigateWeb("$appTarget|$urlTarget")
                    }
                }
                val target = words.drop(1).joinToString(" ")
                if (target.isBlank()) AuraActionRoute.AskHermes(input)
                else AuraActionRoute.OpenApp(target)
            }
            firstWord in CALL_VERBS -> {
                val name = words.drop(1).joinToString(" ")
                if (name.isBlank()) AuraActionRoute.AskHermes(input)
                else AuraActionRoute.CallContact(name)
            }
            firstWord in TEXT_VERBS || (firstWord == "message" && words.size > 1) -> {
                // "text Alisha I'm running late" -> name="Alisha", body="I'm running late"
                val rest = words.drop(1)
                val name = rest.firstOrNull() ?: ""
                val body = rest.drop(1).joinToString(" ")
                if (name.isBlank()) AuraActionRoute.AskHermes(input)
                else AuraActionRoute.SendText(name, body)
            }
            firstWord in SEARCH_VERBS -> {
                val query = words.drop(1).joinToString(" ")
                return AuraActionRoute.WebSearch(query)
            }
            // Check for navigation phrases at start
            lowerInput.startsWith("go to ") -> {
                val url = trimmed.substring("go to ".length).trim()
                if (url.isBlank()) AuraActionRoute.AskHermes(input)
                else AuraActionRoute.NavigateWeb(url)
            }
            lowerInput.startsWith("navigate to ") -> {
                val url = trimmed.substring("navigate to ".length).trim()
                if (url.isBlank()) AuraActionRoute.AskHermes(input)
                else AuraActionRoute.NavigateWeb(url)
            }
            lowerInput.startsWith("visit ") -> {
                val url = trimmed.substring("visit ".length).trim()
                if (url.isBlank()) AuraActionRoute.AskHermes(input)
                else AuraActionRoute.NavigateWeb(url)
            }
            lowerInput.startsWith("browse to ") -> {
                val url = trimmed.substring("browse to ".length).trim()
                if (url.isBlank()) AuraActionRoute.AskHermes(input)
                else AuraActionRoute.NavigateWeb(url)
            }
            lowerInput.startsWith("visit ") -> {
                val url = trimmed.substring("visit ".length).trim()
                if (url.isBlank()) AuraActionRoute.AskHermes(input)
                else AuraActionRoute.NavigateWeb(url)
            }
            else -> AuraActionRoute.AskHermes(input)
        }
    }
}