package com.aetheria.aura.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * User profile storage - independent of Hermes.
 * Stores the user's name and optional bio for use in system prompts.
 */
object UserProfileStore {

    private const val PREFS_NAME = "aura_settings"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_BIO = "user_bio"
    private const val KEY_CONVERSATION_ID = "conversation_id"

    fun getUserName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val newKey = prefs.getString(KEY_USER_NAME, null)
        if (newKey != null) return newKey
        // One-time migration from legacy 'user_profile' key
        val legacy = prefs.getString("user_profile", null)
        if (legacy != null && legacy.isNotBlank()) {
            prefs.edit().putString(KEY_USER_NAME, legacy).remove("user_profile").apply()
            return legacy
        }
        return ""
    }

    fun setUserName(context: Context, name: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_NAME, name)
            .apply()
    }

    fun getUserBio(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_BIO, null) ?: ""
    }

    fun setUserBio(context: Context, bio: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_BIO, bio)
            .apply()
    }

    fun getConversationId(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_CONVERSATION_ID, -1L)
    }

    fun setConversationId(context: Context, conversationId: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_CONVERSATION_ID, conversationId)
            .apply()
    }

    /** Returns a formatted profile string for injection into system prompts. */
    fun getProfileForPrompt(context: Context): String {
        val name = getUserName(context)
        val bio = getUserBio(context)
        return if (name.isNotBlank()) {
            if (bio.isNotBlank()) "Name: $name\nBio: $bio" else "Name: $name"
        } else {
            ""
        }
    }
}