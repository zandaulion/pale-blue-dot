package com.globe.app

import java.util.Calendar
import java.util.TimeZone

/**
 * Central time source for all globe components.
 * When the time scrubber is active, [offsetMs] shifts the simulated time
 * relative to the real clock. When the scrubber is released, offset resets to 0.
 */
object TimeProvider {

    /** Time offset in milliseconds (positive = future, negative = past). */
    @Volatile var offsetMs: Long = 0L

    /**
     * Snapshot of the simulated time for the frame currently being drawn.
     *
     * Every renderer in a single frame (Earth terminator, Sun, Moon, indicator
     * arrows, eclipse detector) reads the clock through [nowMs]. If the scrubber
     * changes [offsetMs] *mid-frame*, those consumers would otherwise compute
     * positions for slightly different times and visibly disagree (flicker).
     * Snapshotting once per frame keeps them consistent.
     */
    @Volatile private var frameNowMs: Long = System.currentTimeMillis()

    /** Call once at the very start of each rendered frame, before any position math. */
    fun beginFrame() {
        frameNowMs = System.currentTimeMillis() + offsetMs
    }

    /** Returns the simulated current time in milliseconds (stable within a frame). */
    fun nowMs(): Long = frameNowMs

    /** Returns a UTC Calendar at the simulated current time. */
    fun calendar(): Calendar {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = nowMs()
        return cal
    }
}
