package com.globe.app.moon

import android.opengl.Matrix
import kotlin.math.sqrt

/** A visibility aid, separate from the sunlight applied to the Moon's surface. */
object MoonGuide {
    const val DISTANCE = 30f
    const val RADIUS = 0.6f

    data class Frame(
        val x: Float, val y: Float, val radiusPx: Float,
        val illumination: Float, val strength: Float,
        val earthX: Float, val earthY: Float, val earthRadiusPx: Float
    )

    /** Earth-facing illuminated fraction, from the same directions used by the scene. */
    fun illumination(moon: FloatArray, sun: FloatArray): Float =
        ((1f - (moon[0] * sun[0] + moon[1] * sun[1] + moon[2] * sun[2]).coerceIn(-1f, 1f)) / 2f)

    /** Full guide below 3%; smoothly disappear by 12%, on either side of new Moon. */
    fun strength(illumination: Float): Float {
        val t = ((illumination - 0.03f) / 0.09f).coerceIn(0f, 1f)
        return 1f - t * t * (3f - 2f * t)
    }

    fun project(
        moon: FloatArray, sun: FloatArray, eye: FloatArray,
        view: FloatArray, projection: FloatArray, width: Int, height: Int
    ): Frame? {
        if (width <= 0 || height <= 0) return null
        val lit = illumination(moon, sun)
        val strength = strength(lit)
        if (strength <= 0f) return null
        val p = floatArrayOf(moon[0] * DISTANCE, moon[1] * DISTANCE, moon[2] * DISTANCE, 1f)
        if (earthOccludes(eye, p)) return null
        val camera = FloatArray(4)
        val clip = FloatArray(4)
        Matrix.multiplyMV(camera, 0, view, 0, p, 0)
        Matrix.multiplyMV(clip, 0, projection, 0, camera, 0)
        if (clip[3] <= 0f) return null
        val x = clip[0] / clip[3]
        val y = clip[1] / clip[3]
        val z = clip[2] / clip[3]
        if (x !in -1f..1f || y !in -1f..1f || z !in -1f..1f) return null
        val radiusPx = projection[5] * RADIUS / clip[3] * height * 0.5f
        val earthCamera = FloatArray(4)
        val earthClip = FloatArray(4)
        Matrix.multiplyMV(earthCamera, 0, view, 0, floatArrayOf(0f, 0f, 0f, 1f), 0)
        Matrix.multiplyMV(earthClip, 0, projection, 0, earthCamera, 0)
        val eyeDistanceSquared = eye[0] * eye[0] + eye[1] * eye[1] + eye[2] * eye[2]
        val earthRadiusPx = projection[5] / sqrt((eyeDistanceSquared - 1f).coerceAtLeast(0.01f)) * height * 0.5f
        return Frame((x + 1f) * width * 0.5f, (1f - y) * height * 0.5f, radiusPx, lit, strength,
            (earthClip[0] / earthClip[3] + 1f) * width * 0.5f,
            (1f - earthClip[1] / earthClip[3]) * height * 0.5f, earthRadiusPx)
    }

    /** Do not float a label over Earth when the Moon is hidden behind it. */
    private fun earthOccludes(eye: FloatArray, p: FloatArray): Boolean {
        val dx = (p[0] - eye[0]).toDouble()
        val dy = (p[1] - eye[1]).toDouble()
        val dz = (p[2] - eye[2]).toDouble()
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        if (distance < 1e-6) return false
        val dot = (eye[0] * dx + eye[1] * dy + eye[2] * dz) / distance
        val discriminant = dot * dot - (eye[0] * eye[0] + eye[1] * eye[1] + eye[2] * eye[2] - 1.0)
        if (discriminant < 0.0) return false
        val hit = -dot - sqrt(discriminant)
        return hit > 0.0 && hit < distance
    }
}
