package com.palmreader.astro

import android.content.Context

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("astro_prefs", Context.MODE_PRIVATE)

    var userId: Long
        get() = prefs.getLong("uid", -1L)
        set(v) = prefs.edit().putLong("uid", v).apply()

    var profilePromptDeferred: Boolean
        get() = prefs.getBoolean("profile_prompt_deferred", false)
        set(v) = prefs.edit().putBoolean("profile_prompt_deferred", v).apply()

    val isLoggedIn: Boolean get() = userId != -1L

    fun startSession(newUserId: Long) {
        prefs.edit()
            .putLong("uid", newUserId)
            .putBoolean("profile_prompt_deferred", false)
            .apply()
    }

    fun logout() = prefs.edit().clear().apply()
}
