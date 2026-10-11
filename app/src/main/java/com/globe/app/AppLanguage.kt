package com.globe.app

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** One app preference shared by activities and the independent widget process. */
object AppLanguage {
    val tags = listOf("en-US", "de-DE", "es-ES", "fr-FR", "ar", "hi-IN", "ja-JP", "ko-KR", "pt-BR", "ro", "uk", "zh-CN")
    val nativeNames = listOf("English (US)", "Deutsch", "Español", "Français", "العربية", "हिन्दी", "日本語", "한국어", "Português (Brasil)", "Română", "Українська", "简体中文")
    private const val PREFS = "app_language"
    private const val TAG = "tag"

    fun selected(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
    } else context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TAG, "") ?: ""

    fun restore(context: Context) {
        if (Build.VERSION.SDK_INT < 33) {
            val locales = LocaleListCompat.forLanguageTags(selected(context))
            if (AppCompatDelegate.getApplicationLocales() != locales) AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    fun select(context: Context, tag: String) {
        require(tag.isEmpty() || tag in tags)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(TAG, tag).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        com.globe.app.widget.EarthTodayWidget.requestUpdate(context)
    }

    /** Widgets use their own Context, so they must explicitly use the app language. */
    fun context(context: Context): Context {
        val tag = selected(context)
        if (tag.isEmpty()) return context
        val config = Configuration(context.resources.configuration)
        config.setLocales(android.os.LocaleList.forLanguageTags(tag))
        return context.createConfigurationContext(config)
    }

    /** Use the supported display language, including English fallback on other devices. */
    fun locale(context: Context): Locale {
        val requested = context.resources.configuration.locales
        for (index in 0 until requested.size()) {
            val candidate = requested[index]
            tags.firstOrNull { Locale.forLanguageTag(it).language == candidate.language }
                ?.let { return Locale.forLanguageTag(it) }
        }
        return Locale.US
    }
}
