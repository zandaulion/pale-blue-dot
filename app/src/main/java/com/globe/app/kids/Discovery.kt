package com.globe.app.kids

import com.globe.app.R
import android.content.Context

/**
 * One collectible "discovery" a child can unlock by exploring the globe. Each is
 * triggered by an interaction the app already supports (tapping a marker, tapping
 * the day/night side for the first time, tapping the sky, or moving the time scrubber), so the
 * collection fills in naturally as kids play.
 */
enum class Discovery(
    val id: String,
    val emoji: String,
    val titleRes: Int,
    val factRes: Int
) {
    EARTHQUAKE("earthquake", "🔶", R.string.text_earthquake,
        R.string.text_you_explored_a_reported_earthquake),
    VOLCANO("volcano", "🌋", R.string.text_volcano,
        R.string.text_you_explored_a_reported_volcanic_event),
    WILDFIRE("wildfire", "🔥", R.string.text_wildfire,
        R.string.text_you_explored_a_reported_wildfire),
    STORM("storm", "🌀", R.string.text_storm,
        R.string.text_you_explored_a_reported_storm),
    DAYTIME("daytime", "☀️", R.string.text_daytime_2,
        R.string.text_you_found_a_place_facing_the_sun),
    NIGHT("night", "🌙", R.string.text_city_lights_2,
        R.string.text_you_found_earth_s_night_side),
    STARGAZER("stargazer", "🌟", R.string.text_star_gazer,
        R.string.text_you_looked_beyond_earth),
    TIME_TRAVELER("time_traveler", "⏰", R.string.text_time_traveler,
        R.string.text_you_moved_time_to_turn_day_into_night);
    fun title(context: Context): String = context.getString(titleRes)
    fun fact(context: Context): String = context.getString(factRes)
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
