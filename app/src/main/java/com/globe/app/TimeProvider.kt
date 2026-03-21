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

    /** Returns the simulated current time in milliseconds. */
    fun nowMs(): Long = System.currentTimeMillis() + offsetMs

    /** Returns a UTC Calendar at the simulated current time. */
    fun calendar(): Calendar {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = nowMs()
        return cal
    }
}
