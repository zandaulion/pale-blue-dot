package com.globe.app.kids

import android.content.Context

/**
 * One collectible "discovery" a child can unlock by exploring the globe. Each is
 * triggered by an interaction the app already supports (tapping a marker, tapping
 * the day/night side, tapping the sky, or moving the time scrubber), so the
 * collection fills in naturally as kids play.
 */
enum class Discovery(
    val id: String,
    val emoji: String,
    val title: String,
    val fact: String
) {
    EARTHQUAKE("earthquake", "🔶", "Earthquake",
        "You found where the ground was shaking!"),
    VOLCANO("volcano", "🌋", "Volcano",
        "You spotted hot lava erupting from deep inside the Earth."),
    WILDFIRE("wildfire", "🔥", "Wildfire",
        "You caught a fire so big that satellites can see it from space."),
    STORM("storm", "🌀", "Storm",
        "You tracked a giant swirling storm."),
    DAYTIME("daytime", "☀️", "Daytime",
        "You found the side of Earth facing the Sun."),
    NIGHT("night", "🌙", "City Lights",
        "You found the night side, sparkling with city lights."),
    STARGAZER("stargazer", "🌟", "Star Gazer",
        "You looked past Earth into the stars and galaxies beyond."),
    TIME_TRAVELER("time_traveler", "⏰", "Time Traveler",
        "You moved time to turn day into night.");
}

/** Persists which discoveries a child has unlocked, in its own prefs file. */
class DiscoveryJournal(context: Context) {

    private val prefs = context.getSharedPreferences("discoveries", Context.MODE_PRIVATE)

    fun isUnlocked(d: Discovery): Boolean = prefs.getBoolean(d.id, false)

    /** Marks [d] found; returns true only the first time it is unlocked. */
    fun unlock(d: Discovery): Boolean {
        if (isUnlocked(d)) return false
        prefs.edit().putBoolean(d.id, true).apply()
        return true
    }

    fun unlockedCount(): Int = Discovery.values().count { isUnlocked(it) }

    val total: Int get() = Discovery.values().size
}
