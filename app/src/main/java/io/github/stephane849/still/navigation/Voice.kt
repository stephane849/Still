package io.github.stephane849.still.navigation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.github.stephane849.still.routing.RouteStep
import java.util.Locale

/**
 * Spoken guidance. Uses whichever text-to-speech engine is chosen in
 * Android's settings (a natural offline engine such as SherpaTTS sounds
 * far better than the default). Speech is marked as navigation guidance,
 * so music ducks under it and Android Auto plays it through the car.
 */
class Voice(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    private var tts: TextToSpeech? = null
    private var ready = false

    /** Mirrors the voice setting; nothing is spoken while false. */
    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) stop()
        }

    fun prepare() {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            tts?.let { engine ->
                engine.language = Locale.getDefault()
                engine.setAudioAttributes(attributes)
                engine.setSpeechRate(0.95f)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        audioManager.abandonAudioFocusRequest(focusRequest)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        audioManager.abandonAudioFocusRequest(focusRequest)
                    }
                })
            }
            ready = true
        }
    }

    fun say(text: String) {
        if (!enabled || !ready) return
        audioManager.requestAudioFocus(focusRequest)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    fun stop() {
        tts?.stop()
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    companion object {
        /** "Turn right onto Bronson Avenue" — the step as a spoken sentence. */
        fun phrase(step: RouteStep): String {
            val road = spokenRoad(step.road)
            val onto = if (road.isBlank() || step.type == "arrive") "" else " onto $road"
            return step.action + onto
        }

        /** "In 800 metres, turn right onto Bronson Avenue". */
        fun ahead(distanceM: Double, step: RouteStep): String {
            val p = phrase(step)
            return "In ${spokenDistance(distanceM)}, ${p.replaceFirstChar { it.lowercase() }}"
        }

        fun spokenDistance(m: Double): String = when {
            m >= 1_500 -> "%.1f kilometres".format(m / 1000).replace(".0 ", " ")
            m >= 950 -> "1 kilometre"
            m >= 100 -> "${(m / 100).toInt() * 100} metres"
            else -> "${((m / 10).toInt() * 10).coerceAtLeast(10)} metres"
        }

        private val suffixes = mapOf(
            "Ave" to "Avenue", "St" to "Street", "Rd" to "Road", "Dr" to "Drive",
            "Blvd" to "Boulevard", "Cres" to "Crescent", "Pkwy" to "Parkway",
            "Hwy" to "Highway", "Ct" to "Court", "Pl" to "Place", "Cir" to "Circle",
            "Ln" to "Lane", "Terr" to "Terrace", "Pvt" to "Private", "Gate" to "Gate"
        )
        private val compass = mapOf("N" to "North", "S" to "South", "E" to "East", "W" to "West")

        /** Expands street abbreviations so they're read as words: "St Laurent Blvd" → "Saint Laurent Boulevard". */
        fun spokenRoad(road: String): String {
            val words = road.replace(".", "").split(" ").filter { it.isNotBlank() }.toMutableList()
            if (words.isEmpty()) return ""
            if (words.size > 1 && words[0] == "St") words[0] = "Saint"
            val last = words.lastIndex
            if (compass.containsKey(words[last]) && words.size > 1) words[last] = compass.getValue(words[last])
            for (i in 1..words.lastIndex) {
                suffixes[words[i]]?.let { words[i] = it }
            }
            return words.joinToString(" ")
        }
    }
}
