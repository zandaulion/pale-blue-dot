package com.globe.app.kids

/**
 * Computes the Moon's phase for the real current date (not the scrubbed sim
 * time), using the Moon's age within the synodic month. Accurate to well within
 * a day — plenty for a friendly "today's moon" readout.
 */
object MoonPhase {

    private const val SYNODIC = 29.530588853       // days between new moons
    private const val JD_NEW_MOON_REF = 2451550.1  // a known new moon (2000-01-06)

    data class Phase(val emoji: String, val name: String, val illuminationPercent: Int)

    fun current(nowMs: Long = System.currentTimeMillis()): Phase {
        val jd = nowMs / 86_400_000.0 + 2440587.5   // Unix ms -> Julian Date
        var age = (jd - JD_NEW_MOON_REF) % SYNODIC
        if (age < 0) age += SYNODIC

        val illumination = ((1 - Math.cos(2 * Math.PI * age / SYNODIC)) / 2 * 100).toInt()
        val (emoji, name) = when {
            age < 1.85 -> "🌑" to "New Moon"
            age < 5.54 -> "🌒" to "Waxing Crescent"
            age < 9.23 -> "🌓" to "First Quarter"
            age < 12.91 -> "🌔" to "Waxing Gibbous"
            age < 16.61 -> "🌕" to "Full Moon"
            age < 20.30 -> "🌖" to "Waning Gibbous"
            age < 23.99 -> "🌗" to "Last Quarter"
            age < 27.68 -> "🌘" to "Waning Crescent"
            else -> "🌑" to "New Moon"
        }
        return Phase(emoji, name, illumination)
    }
}
