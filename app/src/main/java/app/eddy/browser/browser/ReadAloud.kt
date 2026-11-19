package app.eddy.browser.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

enum class TtsState { IDLE, PREPARING, PLAYING, PAUSED }

/**
 * Reads the visible page or Reader Mode article text with the system speech engine.
 * Splits text into sentence-bounded chunks so playback supports pause/resume from the
 * active sentence as well as progress tracking.
 */
class ReadAloud(
    private val context: Context,
    private val onStatusChange: (TtsState, Float) -> Unit,
) {

    constructor(context: Context, onStateChange: (Boolean) -> Unit) : this(
        context,
        { state, _ -> onStateChange(state == TtsState.PLAYING || state == TtsState.PREPARING) },
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var state: TtsState = TtsState.IDLE
    private var chunks: List<String> = emptyList()
    private var currentChunk: Int = 0
    private var sessionId: Int = 0

    /** Speaks [text] from the start, replacing anything already queued. Empty text stops. */
    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return stop()
        val split = SpeechChunks.split(clean)
        if (split.isEmpty()) return stop()

        chunks = split
        currentChunk = 0
        sessionId++
        notify(TtsState.PREPARING, 0f)

        val engine = tts
        if (engine != null && ready) {
            queueFromCurrent(engine)
            return
        }
        if (engine != null) return

        tts = TextToSpeech(context) { status ->
            mainHandler.post {
                ready = status == TextToSpeech.SUCCESS
                val e = tts
                if (ready && e != null) {
                    e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String) {
                            val parsed = parseUtterance(id) ?: return
                            mainHandler.post {
                                val (sid, idx) = parsed
                                if (sid == sessionId && state == TtsState.PLAYING) {
                                    currentChunk = idx
                                    val progress = if (chunks.isEmpty()) 0f else (idx + 1f) / chunks.size
                                    notify(TtsState.PLAYING, progress.coerceIn(0f, 1f))
                                }
                            }
                        }

                        override fun onDone(id: String) {
                            val parsed = parseUtterance(id) ?: return
                            mainHandler.post {
                                val (sid, idx) = parsed
                                if (sid == sessionId && state == TtsState.PLAYING && idx >= chunks.lastIndex) {
                                    chunks = emptyList()
                                    currentChunk = 0
                                    notify(TtsState.IDLE, 0f)
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String) {
                            mainHandler.post {
                                if (state == TtsState.PLAYING || state == TtsState.PREPARING) {
                                    chunks = emptyList()
                                    currentChunk = 0
                                    notify(TtsState.IDLE, 0f)
                                }
                            }
                        }
                    })
                    if (state == TtsState.PREPARING && chunks.isNotEmpty()) {
                        queueFromCurrent(e)
                    }
                } else {
                    tts?.shutdown()
                    tts = null
                    chunks = emptyList()
                    currentChunk = 0
                    notify(TtsState.IDLE, 0f)
                }
            }
        }
    }

    /** Pauses playback while retaining remaining article chunks so [resume] continues cleanly. */
    fun pause() {
        if (state != TtsState.PLAYING && state != TtsState.PREPARING) return
        sessionId++
        tts?.stop()
        val progress = if (chunks.isEmpty()) 0f else (currentChunk + 1f) / chunks.size
        notify(TtsState.PAUSED, progress.coerceIn(0f, 1f))
    }

    /** Resumes paused article playback from the current sentence chunk. */
    fun resume() {
        if (state != TtsState.PAUSED || chunks.isEmpty()) return
        val engine = tts
        if (engine != null && ready) {
            sessionId++
            queueFromCurrent(engine)
        } else {
            val remaining = chunks.drop(currentChunk).joinToString(" ")
            speak(remaining)
        }
    }

    fun stop() {
        sessionId++
        chunks = emptyList()
        currentChunk = 0
        tts?.stop()
        notify(TtsState.IDLE, 0f)
    }

    fun release() {
        sessionId++
        chunks = emptyList()
        currentChunk = 0
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        state = TtsState.IDLE
    }

    private fun queueFromCurrent(engine: TextToSpeech) {
        if (chunks.isEmpty() || currentChunk !in chunks.indices) {
            chunks = emptyList()
            currentChunk = 0
            notify(TtsState.IDLE, 0f)
            return
        }
        val activeSession = sessionId
        val start = currentChunk
        for (i in start until chunks.size) {
            val mode = if (i == start) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(chunks[i], mode, null, "eddy-$activeSession-$i")
        }
        val progress = ((start + 1f) / chunks.size).coerceIn(0f, 1f)
        notify(TtsState.PLAYING, progress)
    }

    private fun parseUtterance(id: String): Pair<Int, Int>? {
        if (!id.startsWith("eddy-")) return null
        val parts = id.split('-')
        if (parts.size != 3) return null
        val sid = parts[1].toIntOrNull() ?: return null
        val idx = parts[2].toIntOrNull() ?: return null
        return sid to idx
    }

    private fun notify(newState: TtsState, progress: Float) {
        state = newState
        onStatusChange(newState, progress)
    }
}

/** Splitting is the only part worth testing on its own, so it lives outside the engine wrapper. */
object SpeechChunks {
    // TextToSpeech.getMaxSpeechInputLength() is 4000; stay well inside it.
    const val MAX_CHUNK = 1500
    // A very long page would otherwise queue for hours.
    const val MAX_CHUNKS = 400

    /** Splits on sentence ends so a chunk boundary does not cut a word, with a hard cap as a fallback. */
    fun split(text: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        text.split(Regex("(?<=[.!?\u3002\uFF01\uFF1F\n])\\s+")).forEach { sentence ->
            sentence.chunked(MAX_CHUNK).forEach { part ->
                if (current.isNotEmpty() && current.length + part.length + 1 > MAX_CHUNK) {
                    out.add(current.toString())
                    current.clear()
                }
                if (current.isNotEmpty()) current.append(' ')
                current.append(part)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out.filter { it.isNotBlank() }.take(MAX_CHUNKS)
    }
}
