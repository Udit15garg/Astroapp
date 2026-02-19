package com.palmreader.astro

import android.content.Context

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("astro_prefs", Context.MODE_PRIVATE)

    var userId: Long
        get() = prefs.getLong("uid", -1L)
        set(v) = prefs.edit().putLong("uid", v).apply()

    val isLoggedIn: Boolean get() = userId != -1L

    fun logout() = prefs.edit().clear().apply()
}
