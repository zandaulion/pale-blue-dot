package com.globe.app.time

/** A scene captures one value per frame and passes it to all astronomy consumers. */
fun interface SceneClock {
    fun nowMs(): Long
}

class AppSceneClock : SceneClock {
    @Volatile var offsetMs: Long = 0L
    @Volatile private var heldMs: Long? = null
    @Volatile private var playing = false
    @Volatile private var playStartedElapsed = 0L

    val isExploring: Boolean get() = heldMs != null
    val isPlaying: Boolean get() = playing

    @Synchronized fun enter(timeMs: Long) {
        heldMs = timeMs
        playing = false
        offsetMs = 0L
    }

    @Synchronized fun seek(timeMs: Long) {
        heldMs = timeMs
        playStartedElapsed = android.os.SystemClock.elapsedRealtime()
    }

    @Synchronized fun setPlaying(value: Boolean) {
        if (!isExploring || playing == value) return
        if (value) playStartedElapsed = android.os.SystemClock.elapsedRealtime()
        else heldMs = nowMs()
        playing = value
    }

    @Synchronized fun exit() {
        heldMs = null
        playing = false
        offsetMs = 0L
    }

    override fun nowMs(): Long {
        val held = heldMs ?: return System.currentTimeMillis() + offsetMs
        return if (playing) held + (android.os.SystemClock.elapsedRealtime() - playStartedElapsed) else held
    }
}

class RealTimeSceneClock : SceneClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
