package com.medassist

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleHelper {

    fun getLocale(langCode: String): Locale {
        return when (langCode.lowercase()) {
            "hi" -> Locale("hi", "IN")
            "te" -> Locale("te", "IN")
            "pa" -> Locale("pa", "IN")
            "en" -> Locale("en", "US")
            else -> Locale.getDefault()
        }
    }

    fun getLocalizedContext(context: Context, langCode: String): Context {
        val locale = getLocale(langCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    fun applyLanguage(context: Context, langCode: String) {
        val locale = getLocale(langCode)
        Locale.setDefault(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeList = if (langCode == "system") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(langCode)
            }
            AppCompatDelegate.setApplicationLocales(localeList)
        } else {
            val resources = context.resources
            val config = Configuration(resources.configuration)
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
        }

        // Update TextToSpeech engine language
        VoiceReminderManager.setLanguage(locale)

        // Recreate Activity if necessary
        if (context is Activity) {
            context.recreate()
        }
    }
}
