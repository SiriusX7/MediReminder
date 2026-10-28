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
            resources.updateConfiguration(config, resources.displayMetrics)
        }

        // Update TextToSpeech engine language
        VoiceReminderManager.setLanguage(locale)

        // Recreate Activity to refresh UI text
        if (context is Activity) {
            context.recreate()
        }
    }

    // Multilingual UI Translation Dictionary
    fun tr(key: String, langCode: String): String {
        val lang = langCode.lowercase()
        return when (key) {
            "home" -> when (lang) {
                "hi" -> "होम"
                "te" -> "హోమ్"
                "pa" -> "ਹੋਮ"
                else -> "Home"
            }
            "medicines" -> when (lang) {
                "hi" -> "दवाइयां"
                "te" -> "మందులు"
                "pa" -> "ਦਵਾਈਆਂ"
                else -> "Medicines"
            }
            "med_info" -> when (lang) {
                "hi" -> "दवा जानकारी"
                "te" -> "మందుల సమాచారం"
                "pa" -> "ਦਵਾਈ ਜਾਣਕਾਰੀ"
                else -> "Med Info"
            }
            "history" -> when (lang) {
                "hi" -> "इतिहास"
                "te" -> "చరిత్ర"
                "pa" -> "ਇਤਿਹਾਸ"
                else -> "History"
            }
            "settings" -> when (lang) {
                "hi" -> "सेटिंग्स"
                "te" -> "సెట్టింగులు"
                "pa" -> "ਸੈਟਿੰਗਾਂ"
                else -> "Settings"
            }
            "todays_schedule" -> when (lang) {
                "hi" -> "आज का शेड्यूल"
                "te" -> "ఈనాటి షెడ్యూల్"
                "pa" -> "ਅੱਜ ਦਾ ਸ਼ੈਡਿਊਲ"
                else -> "Today's Schedule"
            }
            "scan_prescription" -> when (lang) {
                "hi" -> "प्रिस्क्रिप्शन स्कैन करें"
                "te" -> "ప్రిస్క్రిప్షన్ స్కాన్ చేయండి"
                "pa" -> "ਨੁਸਖਾ ਸਕੈਨ ਕਰੋ"
                else -> "Scan Prescription"
            }
            else -> key
        }
    }
}
