package com.globe.app.kids

import com.globe.app.R
/**
 * Computes the Moon's phase for the real current date (not the scrubbed sim
 * time), using the Moon's age within the synodic month. Accurate to well within
 * a day — plenty for a friendly "today's moon" readout.
 */
object MoonPhase {

    private const val SYNODIC = 29.530588853       // days between new moons
    private const val JD_NEW_MOON_REF = 2451550.1  // a known new moon (2000-01-06)

    data class Phase(val emoji: String, val nameRes: Int, val illuminationPercent: Int) {
        fun name(context: android.content.Context): String = context.getString(nameRes)
    }

    fun current(nowMs: Long = System.currentTimeMillis()): Phase {
        val jd = nowMs / 86_400_000.0 + 2440587.5   // Unix ms -> Julian Date
        var age = (jd - JD_NEW_MOON_REF) % SYNODIC
        if (age < 0) age += SYNODIC

        val illumination = ((1 - Math.cos(2 * Math.PI * age / SYNODIC)) / 2 * 100).toInt()
        val (emoji, name) = when {
            age < 1.85 -> "🌑" to R.string.text_new_moon
            age < 5.54 -> "🌒" to R.string.text_waxing_crescent
            age < 9.23 -> "🌓" to R.string.text_first_quarter
            age < 12.91 -> "🌔" to R.string.text_waxing_gibbous
            age < 16.61 -> "🌕" to R.string.text_full_moon
            age < 20.30 -> "🌖" to R.string.text_waning_gibbous
            age < 23.99 -> "🌗" to R.string.text_last_quarter
            age < 27.68 -> "🌘" to R.string.text_waning_crescent
            else -> "🌑" to R.string.text_new_moon
        }
        return Phase(emoji, name, illumination)
    }
}
