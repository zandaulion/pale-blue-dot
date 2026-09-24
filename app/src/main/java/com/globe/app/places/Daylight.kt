package com.globe.app.places

import com.globe.app.earth.SunPosition
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Approximate NOAA sunrise at apparent upper-limb zenith 90.833°. */
object Daylight {
    enum class State { ORDINARY, POLAR_DAY, POLAR_NIGHT, ZONE_UNSET }
    data class Summary(
        val state: State,
        val localTimeMs: Long,
        val sunriseMs: Long?,
        val sunsetMs: Long?,
        val daylightMinutes: Int?,
        val isDay: Boolean
    )

    fun at(place: SavedPlace, nowMs: Long): Summary {
        val direction = SunPosition.calculate(nowMs)
        val lat = Math.toRadians(place.lat)
        val lon = Math.toRadians(place.lon)
        val x = -cos(lat) * cos(lon)
        val y = sin(lat)
        val z = cos(lat) * sin(lon)
        val day = x * direction[0] + y * direction[1] + z * direction[2] > 0.0
        val zone = place.zoneId?.takeIf(PlaceStore::validZone)
            ?: return Summary(State.ZONE_UNSET, nowMs, null, null, null, day)
        val local = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply { timeInMillis = nowMs }
        val year = local.get(Calendar.YEAR)
        val month = local.get(Calendar.MONTH)
        val date = local.get(Calendar.DAY_OF_MONTH)
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(year, month, date, 0, 0, 0)
        }
        val rises = mutableListOf<Long>()
        val sets = mutableListOf<Long>()
        var polar: State? = null
        for (dayOffset in -1..1) {
            val candidate = utc.clone() as Calendar
            candidate.add(Calendar.DATE, dayOffset)
            val n = candidate.get(Calendar.DAY_OF_YEAR)
            val daysInYear = if (candidate.getActualMaximum(Calendar.DAY_OF_YEAR) == 366) 366.0 else 365.0
            val gamma = 2.0 * Math.PI / daysInYear * (n - 1)
            val eq = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
            val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
                0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
                0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
            val cosHa = (cos(Math.toRadians(90.833)) / (cos(lat) * cos(decl))) - tan(lat) * tan(decl)
            if (dayOffset == 0) polar = when {
                cosHa < -1.0 -> State.POLAR_DAY
                cosHa > 1.0 -> State.POLAR_NIGHT
                else -> null
            }
            if (cosHa !in -1.0..1.0) continue
            val ha = Math.toDegrees(acos(cosHa))
            val rise = candidate.timeInMillis + ((720 - 4 * (place.lon + ha) - eq) * 60_000).toLong()
            val set = candidate.timeInMillis + ((720 - 4 * (place.lon - ha) - eq) * 60_000).toLong()
            if (sameLocalDate(rise, zone, year, month, date)) rises.add(rise)
            if (sameLocalDate(set, zone, year, month, date)) sets.add(set)
        }
        if (polar != null && rises.isEmpty() && sets.isEmpty())
            return Summary(polar, nowMs, null, null, if (polar == State.POLAR_DAY) 1440 else 0, day)
        val rise = rises.minOrNull()
        val set = sets.minOrNull()
        val duration = if (rise != null && set != null && set > rise)
            ((set - rise) / 60_000).toInt() else null
        return Summary(State.ORDINARY, nowMs, rise, set, duration, day)
    }

    private fun sameLocalDate(ms: Long, zone: String, y: Int, m: Int, d: Int): Boolean {
        val c = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply { timeInMillis = ms }
        return c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m && c.get(Calendar.DAY_OF_MONTH) == d
    }
}
