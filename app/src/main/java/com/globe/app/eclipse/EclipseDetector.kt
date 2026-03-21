package com.globe.app.eclipse

import com.globe.app.earth.SunPosition
import com.globe.app.moon.MoonPosition

/**
 * Detects solar and lunar eclipse conditions based on the alignment
 * of sun and moon direction vectors as seen from Earth.
 *
 * - Solar eclipse: sun and moon directions nearly identical (dot ≈ +1)
 * - Lunar eclipse: sun and moon directions nearly opposite (dot ≈ -1)
 */
object EclipseDetector {

    /** Tight threshold for declaring a full eclipse (~1.8°). */
    private const val ECLIPSE_THRESHOLD = 0.9995f

    /** Relaxed threshold for "near eclipse" warning (~3.6°). */
    private const val NEAR_ECLIPSE_THRESHOLD = 0.998f

    enum class EclipseState {
        NONE,
        NEAR_SOLAR,
        NEAR_LUNAR,
        SOLAR,
        LUNAR
    }

    /**
     * Computes the current eclipse state by taking the dot product
     * of the sun and moon direction vectors.
     *
     * Call this every frame from the GL thread.
     */
    fun detect(): EclipseState {
        val sunDir = SunPosition.calculate()
        val moonDir = MoonPosition.calculate()

        val dot = sunDir[0] * moonDir[0] +
                  sunDir[1] * moonDir[1] +
                  sunDir[2] * moonDir[2]

        return when {
            dot > ECLIPSE_THRESHOLD       -> EclipseState.SOLAR
            dot > NEAR_ECLIPSE_THRESHOLD   -> EclipseState.NEAR_SOLAR
            dot < -ECLIPSE_THRESHOLD       -> EclipseState.LUNAR
            dot < -NEAR_ECLIPSE_THRESHOLD  -> EclipseState.NEAR_LUNAR
            else                           -> EclipseState.NONE
        }
    }
}
