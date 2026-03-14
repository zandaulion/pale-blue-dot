package com.globe.app.earth

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Computes a simplified sun direction vector for use in Earth rendering.
 *
 * The algorithm is a low-precision solar position model (good to ~1 degree)
 * based on the "Astronomical Almanac" approximation. It is more than adequate
 * for a visually convincing day/night terminator.
 *
 * The returned direction vector is in the Earth model's reference frame where:
 *   +Y = north pole, +X = prime meridian at the equator, +Z = 90 E at equator.
 *
 * The vector points *from* the Earth *toward* the sun (unit length).
 */
object SunPosition {

    /** Earth's axial tilt in radians. */
    private const val AXIAL_TILT_RAD = 23.44 * Math.PI / 180.0

    /**
     * Returns the unit direction vector toward the sun.
     * Always uses the current system time if no [Calendar] is provided.
     *
     * @return FloatArray of [x, y, z] in the Earth's model coordinate system:
     *         +Y = North Pole, -X = 0° (Greenwich), +Z = 90°W
     */
    fun calculate(calendar: Calendar? = null): FloatArray {
        val cal = calendar ?: Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val jd = julianDate(cal)
        val n = jd - 2451545.0  // days since J2000.0

        // Mean longitude and mean anomaly of the sun (degrees)
        val L = (280.460 + 0.9856474 * n) % 360.0
        val g = Math.toRadians((357.528 + 0.9856003 * n) % 360.0)

        // Ecliptic longitude (degrees) and obliquity
        val eclipticLonDeg = L + 1.915 * sin(g) + 0.020 * sin(2.0 * g)
        val eclipticLonRad = Math.toRadians(eclipticLonDeg)

        // The ecliptic latitude of the sun is effectively 0.
        val obliquity = AXIAL_TILT_RAD

        // Equatorial coordinates: right ascension (RA) and declination (Dec)
        val sinEclLon = sin(eclipticLonRad)
        val cosEclLon = cos(eclipticLonRad)

        val ra = atan2(cos(obliquity) * sinEclLon, cosEclLon)   // radians
        val dec = asin(sin(obliquity) * sinEclLon)               // radians

        // Convert RA + Dec to a direction in the Earth-fixed frame.
        // We need the Greenwich Mean Sidereal Time (GMST) to rotate from the
        // celestial frame to the Earth-fixed frame.
        val gmst = greenwichMeanSiderealTime(jd, n)

        // Hour angle at the prime meridian. 
        // hourAngle = 0 means sun is directly over Greenwich (-X).
        val hourAngle = gmst - ra

        // Direction toward the sun in model coordinates.
        // HA=0 -> x=-cosDec, z=0. HA=90W -> x=0, z=cosDec.
        val cosDec = cos(dec)
        val x = -(cosDec * cos(hourAngle)).toFloat()
        val y = sin(dec).toFloat()
        val z = (cosDec * sin(hourAngle)).toFloat()

        return floatArrayOf(x, y, z)
    }

    // ------------------------------------------------------------------
    // Helper functions
    // ------------------------------------------------------------------

    /**
     * Computes the Julian Date for the given [Calendar].
     */
    private fun julianDate(cal: Calendar): Double {
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1  // Calendar months are 0-based
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)

        val dayFraction = day + (hour + (minute + second / 60.0) / 60.0) / 24.0

        // Use the standard Julian Date formula (valid for dates after 1582-10-15).
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = y / 100
        val b = 2 - a + a / 4

        return (365.25 * (y + 4716)).toLong() +
                (30.6001 * (m + 1)).toLong() +
                dayFraction + b - 1524.5
    }

    /**
     * Greenwich Mean Sidereal Time in radians for the given Julian Date.
     */
    private fun greenwichMeanSiderealTime(jd: Double, daysSinceJ2000: Double): Double {
        // Centuries since J2000.0
        val t = daysSinceJ2000 / 36525.0

        // GMST at 0h UT in seconds, then converted to degrees
        var gmstDeg = 280.46061837 +
                360.98564736629 * daysSinceJ2000 +
                0.000387933 * t * t -
                t * t * t / 38710000.0

        // Normalise to [0, 360)
        gmstDeg %= 360.0
        if (gmstDeg < 0) gmstDeg += 360.0

        return Math.toRadians(gmstDeg)
    }
}
