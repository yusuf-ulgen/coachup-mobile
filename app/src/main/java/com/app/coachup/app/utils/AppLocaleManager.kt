package com.app.coachup.app.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Uygulama dili tercihi — Ayarlar > Dil.
 * TR/EN seçimi [androidx.appcompat] locale API ile uygulanır.
 */
object AppLocaleManager {

    private const val PREFS = "coachup_app_prefs"
    private const val KEY_LANGUAGE = "app_language"

    const val LANG_TR = "tr"
    const val LANG_EN = "en"

    fun getLanguage(context: Context): String =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LANG_TR)
            ?: LANG_TR

    fun displayName(lang: String): String = when (lang) {
        LANG_EN -> "English"
        else -> "Türkçe"
    }

    fun applyStored(context: Context) {
        applyLanguage(context, getLanguage(context))
    }

    fun applyLanguage(context: Context, lang: String) {
        val tag = if (lang == LANG_EN) "en" else "tr"
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, if (lang == LANG_EN) LANG_EN else LANG_TR)
            .apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
