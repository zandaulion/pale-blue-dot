package com.globe.app.kids

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import com.globe.app.AppLanguage
import com.globe.app.R
import org.json.JSONObject

/** Offline fixed-card recordings. Errors stay silent; there is no voice substitution. */
class TaliaNarrator(private val context: Context) {
    private val locale = AppLanguage.locale(context).toLanguageTag()
    private val directory = "narration/$locale/"
    private val index: Map<String, String> by lazy {
        val json = context.assets.open(directory + "index.json").bufferedReader().use { JSONObject(it.readText()) }
        json.keys().asSequence().associateWith { json.getString(it) }
    }
    private val addedPrefix = NarrationText.clean(context.getString(R.string.text_added_to_field_notebook_1_s, ""))
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var focusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var player: MediaPlayer? = null
    private var generation = 0

    /** Resolves full cards and a separate notebook celebration without splicing words. */
    fun clipsFor(text: String): List<String>? {
        val clean = NarrationText.clean(text)
        index[clean]?.let { return listOf(it) }
        if (clean.startsWith("$addedPrefix ")) {
            val body = clean.removePrefix(addedPrefix).trim()
            val clip = index[body] ?: return null
            return listOf(index.getValue(addedPrefix), clip)
        }
        return null
    }

    /** True also when audio focus is refused, so fallback cannot bypass that refusal. */
    fun speak(text: String): Boolean {
        stop()
        val clips = clipsFor(text) ?: return false
        if (!requestFocus()) return true
        play(clips, 0, generation)
        return true
    }

    private fun play(clips: List<String>, position: Int, token: Int) {
        if (token != generation) return
        val next = MediaPlayer()
        player = next
        try {
            next.setAudioAttributes(attributes)
            context.assets.openFd(directory + clips[position]).use { fd ->
                next.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            next.setOnPreparedListener { if (token == generation) it.start() }
            next.setOnCompletionListener {
                if (token == generation) {
                    it.release()
                    player = null
                    if (position + 1 < clips.size) play(clips, position + 1, token)
                    else abandonFocus()
                }
            }
            next.setOnErrorListener { _, _, _ ->
                if (token == generation) {
                    stop()
                }
                true
            }
            next.prepareAsync()
        } catch (_: Exception) {
            stop()
        }
    }

    @Suppress("DEPRECATION")
    private fun requestFocus(): Boolean {
        val token = generation
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            // A queued callback from an earlier card must not stop the new one.
            if (change < 0 && token == generation) stop()
        }
        focusListener = listener
        val result = if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener(listener).build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (hasFocus) {
            if (Build.VERSION.SDK_INT >= 26) focusRequest?.let(audioManager::abandonAudioFocusRequest)
            else focusListener?.let(audioManager::abandonAudioFocus)
        }
        hasFocus = false
        focusRequest = null
        focusListener = null
    }

    fun stop() {
        generation++
        player?.release()
        player = null
        abandonFocus()
    }
}
