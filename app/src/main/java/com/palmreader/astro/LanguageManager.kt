package com.palmreader.astro

import android.content.Context
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Manages app language switching between English and Hindi.
 * Uses AppCompatDelegate.setApplicationLocales() for proper locale support.
 */
object LanguageManager {

    private const val PREF_NAME = "astro_language_prefs"
    private const val KEY_LANGUAGE = "app_language"

    const val ENGLISH = "en"
    const val HINDI = "hi"

    fun getCurrentLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, ENGLISH) ?: ENGLISH
    }

    fun setLanguage(context: Context, languageCode: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, languageCode)
            .apply()

        val localeList = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun isHindi(context: Context): Boolean = getCurrentLanguage(context) == HINDI

    fun toggleLanguage(context: Context) {
        val current = getCurrentLanguage(context)
        val next = if (current == ENGLISH) HINDI else ENGLISH
        setLanguage(context, next)
    }
}
