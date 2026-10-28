package com.medassist

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

object VoiceReminderManager {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingSpeech: String? = null

    fun initialize(context: Context, onReady: (() -> Unit)? = null) {
        if (isInitialized) {
            onReady?.invoke()
            return
        }

        val appContext = context.applicationContext
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isInitialized = true
                    Log.d("VoiceReminder", "TextToSpeech initialized successfully")
                    pendingSpeech?.let {
                        speakTextInternal(it)
                        pendingSpeech = null
                    }
                    onReady?.invoke()
                } else {
                    Log.e("VoiceReminder", "Language not supported")
                }
            } else {
                Log.e("VoiceReminder", "TextToSpeech initialization failed")
            }
        }
    }

    fun setLanguage(locale: Locale) {
        try {
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("VoiceReminder", "Language ${locale.language} not supported by TTS engine")
            }
        } catch (e: Exception) {
            Log.e("VoiceReminder", "Error setting language: ${e.localizedMessage}")
        }
    }

    fun speakReminder(context: Context, medicineName: String, instructions: String = "", quantity: Int = 1) {
        val dm = DataManager(context)
        if (!dm.isVoiceRemindersEnabled()) return

        val speechText = if (instructions.isNotBlank()) {
            "Time to take $quantity dose of $medicineName. $instructions."
        } else {
            "Time to take $quantity dose of $medicineName."
        }

        speakText(context, speechText)
    }

    fun speakText(context: Context, text: String) {
        if (!isInitialized) {
            pendingSpeech = text
            initialize(context) {
                pendingSpeech?.let {
                    speakTextInternal(it)
                    pendingSpeech = null
                }
            }
            return
        }

        speakTextInternal(text)
    }

    private fun speakTextInternal(text: String) {
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MedReminderTTS")
        } catch (e: Exception) {
            Log.e("VoiceReminder", "Error speaking text: ${e.localizedMessage}", e)
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (_: Exception) {}
    }
}
