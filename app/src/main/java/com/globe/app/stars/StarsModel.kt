package com.globe.app.stars

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*
import java.util.Random

/**
 * Generates star vertex data for a realistic starfield.
 *
 * Each star vertex contains 8 floats:
 *   position (x, y, z) — on a large surrounding sphere
 *   size (point size / magnitude)
 *   color (r, g, b, a)
 *
 * Stars are distributed on a sphere with slight clustering to mimic
 * the Milky Way band and natural sky variation. Sizes follow a
 * power-law so most stars are dim with a few bright ones. Colors
 * reflect realistic spectral classes (O/B blue-white through M red).
 */
object StarsModel {

    /** Stride in floats per vertex: x,y,z, size, r,g,b,a */
    const val FLOATS_PER_VERTEX = 8

    /** Stride in bytes */
    const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4

    /** Radius of the star sphere — large enough to surround the entire scene */
    private const val SPHERE_RADIUS = 500f

    /** Total number of stars to generate */
    private const val STAR_COUNT = 8000

    /** Seed for reproducible starfields across sessions */
    private const val SEED = 42L

    val starCount: Int get() = STAR_COUNT

    /**
     * Build and return a direct FloatBuffer containing all star vertex data,
     * ready to be uploaded to a VBO.
     */
    fun generateVertexData(): FloatBuffer {
        val rng = Random(SEED)
        val data = FloatArray(STAR_COUNT * FLOATS_PER_VERTEX)

        for (i in 0 until STAR_COUNT) {
            val offset = i * FLOATS_PER_VERTEX

            // --- Position on sphere with clustered distribution ---
            val (x, y, z) = generateStarPosition(rng)
            data[offset + 0] = x
            data[offset + 1] = y
            data[offset + 2] = z

            // --- Size (apparent magnitude) ---
            // Power-law: many faint, few bright
            data[offset + 3] = generateStarSize(rng)

            // --- Color based on spectral class ---
            val (r, g, b) = generateStarColor(rng)
            data[offset + 4] = r
            data[offset + 5] = g
            data[offset + 6] = b
            data[offset + 7] = 1.0f // alpha
        }

        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }
    }

    // ---------------------------------------------------------------
    // Position generation
    // ---------------------------------------------------------------

    /**
     * Generate a point on the star sphere. About 30 % of stars are
     * concentrated near a "galactic plane" (a band across the sky)
     * to give a subtle Milky Way effect. The rest are uniformly
     * distributed.
     */
    private fun generateStarPosition(rng: Random): Triple<Float, Float, Float> {
        val theta: Double // azimuth  [0, 2pi)
        val phi: Double   // polar    [0, pi]

        if (rng.nextFloat() < 0.30f) {
            // Clustered near a galactic band — constrain polar angle
            // around the equator (phi ~ pi/2) with Gaussian spread
            theta = rng.nextDouble() * 2.0 * PI
            val deviation = rng.nextGaussian() * 0.18 // tight band
            phi = (PI / 2.0) + deviation
        } else {
            // Uniform distribution on sphere via inverse-CDF
            theta = rng.nextDouble() * 2.0 * PI
            phi = acos(1.0 - 2.0 * rng.nextDouble())
        }

        // Add small per-star jitter so clustered stars aren't on a
        // perfect mathematical band
        val jitteredPhi = phi + rng.nextGaussian() * 0.02
        val jitteredTheta = theta + rng.nextGaussian() * 0.02

        val sinPhi = sin(jitteredPhi)
        val x = (SPHERE_RADIUS * sinPhi * cos(jitteredTheta)).toFloat()
        val y = (SPHERE_RADIUS * cos(jitteredPhi)).toFloat()
        val z = (SPHERE_RADIUS * sinPhi * sin(jitteredTheta)).toFloat()

        return Triple(x, y, z)
    }

    // ---------------------------------------------------------------
    // Size / magnitude generation
    // ---------------------------------------------------------------

    /**
     * Returns a point-size value in the range ~1..6.
     * Uses a power-law: the vast majority of stars are small (1-2 px),
     * with progressively fewer at each larger size.
     */
    private fun generateStarSize(rng: Random): Float {
        // u in (0,1], power-law exponent 3 skews toward small values
        val u = rng.nextFloat().coerceIn(0.001f, 1.0f)
        val base = u.toDouble().pow(3.0).toFloat() // 0..1 heavily weighted low
        return 1.0f + base * 5.0f                  // 1..6
    }

    // ---------------------------------------------------------------
    // Color generation
    // ---------------------------------------------------------------

    /**
     * Approximate spectral-class distribution:
     *   ~55 % white / blue-white  (A/F class — hot)
     *   ~25 % yellow / yellow-white (G class — sun-like)
     *   ~12 % orange (K class)
     *    ~8 % red-orange (M class)
     *
     * Returns (r, g, b) each in 0..1.
     */
    private fun generateStarColor(rng: Random): Triple<Float, Float, Float> {
        val roll = rng.nextFloat()
        // Base colors per class, then apply small random variation
        val (r, g, b) = when {
            roll < 0.30f -> Triple(0.75f, 0.85f, 1.00f) // blue-white (O/B)
            roll < 0.55f -> Triple(0.95f, 0.95f, 1.00f) // white (A/F)
            roll < 0.80f -> Triple(1.00f, 0.96f, 0.84f) // yellow-white (G)
            roll < 0.92f -> Triple(1.00f, 0.82f, 0.55f) // orange (K)
            else         -> Triple(1.00f, 0.65f, 0.45f) // red-orange (M)
        }

        // Subtle per-star variation so stars of the same class aren't identical
        fun jitter(v: Float) = (v + rng.nextFloat() * 0.06f - 0.03f).coerceIn(0f, 1f)
        return Triple(jitter(r), jitter(g), jitter(b))
    }
}
