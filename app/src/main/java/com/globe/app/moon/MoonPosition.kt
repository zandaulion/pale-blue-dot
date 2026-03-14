package com.globe.app.moon

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

/**
 * Computes a simplified Moon direction vector in the Earth-fixed frame.
 *
 * Uses a low-precision lunar position model (~1-2 degree accuracy) sufficient
 * for visually placing the Moon in the sky with the correct phase.
 *
 * Coordinate system matches the Earth model (u=0 at phi=0):
 *   +Y = north pole, +X = 180°W (dateline), +Z = 90°W at equator.
 *
 * The vector points from Earth toward the Moon (unit length).
 */
object MoonPosition {

    private const val AXIAL_TILT_RAD = 23.44 * Math.PI / 180.0

    /**
     * Returns the unit direction vector toward the Moon.
     */
    fun calculate(calendar: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))): FloatArray {
        val jd = julianDate(calendar)
        val n = jd - 2451545.0 // days since J2000.0

        // Fundamental lunar elements (degrees)
        val L0 = (218.316 + 13.176396 * n) % 360.0        // mean longitude
        val M = Math.toRadians((134.963 + 13.064993 * n) % 360.0)  // mean anomaly
        val F = Math.toRadians((93.272 + 13.229350 * n) % 360.0)   // mean distance

        // Ecliptic longitude and latitude (degrees)
        val eclLonDeg = L0 + 6.289 * sin(M)
        val eclLatDeg = 5.128 * sin(F)

        val eclLon = Math.toRadians(eclLonDeg)
        val eclLat = Math.toRadians(eclLatDeg)

        // Convert ecliptic to equatorial (RA, Dec)
        val sinLon = sin(eclLon)
        val cosLon = cos(eclLon)
        val sinLat = sin(eclLat)
        val cosLat = cos(eclLat)
        val cosObl = cos(AXIAL_TILT_RAD)
        val sinObl = sin(AXIAL_TILT_RAD)

        val ra = atan2(
            cosLat * sinLon * cosObl - sinLat * sinObl,
            cosLat * cosLon
        )
        val dec = asin(
            sinLat * cosObl + cosLat * sinObl * sinLon
        )

        // Convert to Earth-fixed frame via GMST
        val gmst = greenwichMeanSiderealTime(jd, n)
        val hourAngle = gmst - ra

        val cosDec = cos(dec)
        val x = -(cosDec * cos(hourAngle)).toFloat()
        val y = sin(dec).toFloat()
        val z = (cosDec * sin(hourAngle)).toFloat()

        return floatArrayOf(x, y, z)
    }

    private fun julianDate(cal: Calendar): Double {
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)

        val dayFraction = day + (hour + (minute + second / 60.0) / 60.0) / 24.0

        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = y / 100
        val b = 2 - a + a / 4

        return (365.25 * (y + 4716)).toLong() +
                (30.6001 * (m + 1)).toLong() +
                dayFraction + b - 1524.5
    }

    private fun greenwichMeanSiderealTime(jd: Double, daysSinceJ2000: Double): Double {
        val t = daysSinceJ2000 / 36525.0
        var gmstDeg = 280.46061837 +
                360.98564736629 * daysSinceJ2000 +
                0.000387933 * t * t -
                t * t * t / 38710000.0
        gmstDeg %= 360.0
        if (gmstDeg < 0) gmstDeg += 360.0
        return Math.toRadians(gmstDeg)
    }
}
