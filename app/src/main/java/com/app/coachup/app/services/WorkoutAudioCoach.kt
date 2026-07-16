package com.app.coachup.app.services

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Android equivalent of iOS WorkoutAudioCoach (AVSpeechSynthesizer).
 * Uses Android's TextToSpeech API to announce workout events in Turkish.
 *
 * Call [init] once with an application Context before use.
 */
object WorkoutAudioCoach {

    private var tts: TextToSpeech? = null
    private var isReady = false
    var isEnabled: Boolean = true

    fun init(context: Context) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("tr", "TR"))
                isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                          result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    fun toggle() { isEnabled = !isEnabled }

    fun announceWorkoutStart() {
        speak("Antrenman başladı. Başarılar!")
    }

    fun announceWorkoutComplete() {
        speak("Antrenman tamamlandı. Harika iş!")
    }

    fun announceKmSplit(km: Int, paceMinPerKm: Double) {
        val paceText = formattedPaceTurkish(paceMinPerKm)
        speak("$km kilometre. Tempo $paceText.")
    }

    fun announceDistance(km: Double) {
        if (km == 0.5) speak("Yarım kilometre.")
    }

    fun announceAutoPause() {
        speak("Otomatik durakladı.")
    }

    fun announceAutoResume() {
        speak("Antrenman devam ediyor.")
    }

    fun announceGoalReachedDistance(km: Double) {
        val kmText = if (km % 1.0 == 0.0) "${km.toInt()}" else "%.1f".format(km)
        speak("Tebrikler! $kmText kilometre hedefinize ulaştınız.")
    }

    fun announceGoalReachedDuration(seconds: Int) {
        val mins = seconds / 60
        speak("Tebrikler! $mins dakika hedefinize ulaştınız.")
    }

    fun announceSetComplete(setNumber: Int, reps: Int, weight: Double) {
        val weightText = if (weight % 1.0 == 0.0) "${weight.toInt()} kilo" else "%.1f kilo".format(weight)
        speak("$setNumber. set tamamlandı. $reps tekrar, $weightText.")
    }

    fun announceRestStart(seconds: Int) {
        speak("$seconds saniyelik dinlenme başladı.")
    }

    fun announceRestEnd() {
        speak("Dinlenme bitti. Hazır olun.")
    }

    private fun speak(text: String) {
        if (!isEnabled || !isReady) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }

    private fun formattedPaceTurkish(paceMinPerKm: Double): String {
        if (paceMinPerKm <= 0) return "bilinmiyor"
        val min = paceMinPerKm.toInt()
        val sec = ((paceMinPerKm - min) * 60).toInt()
        return "$min dakika $sec saniye"
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
