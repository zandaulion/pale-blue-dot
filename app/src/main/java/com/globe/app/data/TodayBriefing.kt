package com.globe.app.data

import com.globe.app.R
import com.globe.app.events.EarthEventsProvider
import com.globe.app.kids.MoonPhase
import com.globe.app.places.Daylight
import com.globe.app.places.SavedPlace
import java.util.Calendar
import java.util.TimeZone

/** One deterministic on-device briefing for the app and widgets; always uses real time. */
object TodayBriefing {
    data class Briefing(
        val moon: MoonPhase.Phase,
        val place: SavedPlace?,
        val daylight: Daylight.Summary?,
        val event: EarthEventsProvider.Event?,
        val eventIsStale: Boolean,
        val eventMessageRes: Int,
        val promptRes: Int,
        val generatedAtMs: Long
    ) {
        fun eventMessage(context: android.content.Context): String = context.getString(eventMessageRes)
        fun prompt(context: android.content.Context): String = context.getString(promptRes)
    }

    private val prompts = listOf(
        R.string.text_watch_where_sunrise_reaches_next,
        R.string.text_compare_daylight_in_the_north_and_south,
        R.string.text_trace_a_curve_of_earth_s_plate_boundaries
    )

    fun build(nowMs: Long, place: SavedPlace?, snapshot: EarthRepository.Snapshot): Briefing {
        val day = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = nowMs }
            .get(Calendar.DAY_OF_YEAR)
        val valid = snapshot.feeds.values.flatMap { it.events }
            .filter { it.observedAtMs != null && it.observedAtMs <= nowMs &&
                nowMs - it.observedAtMs <= 30L * 86_400_000L }
        val categories = EarthEventsProvider.Event.Type.values()
        val chosen = categories.indices.asSequence().map { categories[(day + it) % categories.size] }
            .mapNotNull { type -> valid.filter { it.type == type }
                .sortedWith(compareByDescending<EarthEventsProvider.Event> { it.observedAtMs }.thenBy { it.id })
                .firstOrNull() }.firstOrNull()
        val stale = chosen?.let { snapshot.feeds[it.type]?.state != EarthRepository.State.READY } ?: false
        val message = when {
            chosen != null && stale -> R.string.text_saved_observation_feed_update_unavailable
            chosen != null -> R.string.text_recent_reported_observation
            snapshot.feeds.values.all { it.state == EarthRepository.State.NOT_LOADED || it.state == EarthRepository.State.LOADING } ->
                R.string.text_observations_are_loading
            else -> R.string.text_no_recent_observations_available_the_moon_and_daylight_are_still
        }
        return Briefing(MoonPhase.current(nowMs), place, place?.let { Daylight.at(it, nowMs) },
            chosen, stale, message, prompts[day % prompts.size], nowMs)
    }
}
