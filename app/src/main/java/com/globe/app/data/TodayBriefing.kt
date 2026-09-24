package com.globe.app.data

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
        val eventMessage: String,
        val prompt: String,
        val generatedAtMs: Long
    )

    private val prompts = listOf(
        "Watch where sunrise reaches next.",
        "Compare daylight in the north and south.",
        "Trace a curve of Earth's plate boundaries."
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
            chosen != null && stale -> "Saved observation; feed update unavailable"
            chosen != null -> "Recent reported observation"
            snapshot.feeds.values.all { it.state == EarthRepository.State.NOT_LOADED || it.state == EarthRepository.State.LOADING } ->
                "Observations are loading"
            else -> "No recent observations available; the Moon and daylight are still here"
        }
        return Briefing(MoonPhase.current(nowMs), place, place?.let { Daylight.at(it, nowMs) },
            chosen, stale, message, prompts[day % prompts.size], nowMs)
    }
}
