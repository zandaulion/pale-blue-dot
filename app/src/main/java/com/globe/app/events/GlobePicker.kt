package com.globe.app.events

import android.opengl.Matrix
import com.globe.app.camera.OrbitCamera

/**
 * Converts a screen tap into a latitude/longitude on the globe by unprojecting
 * the tap into a world-space ray and intersecting it with the Earth sphere.
 *
 * The projection parameters (33° fov, 0.1 near, 1000 far) must match the ones
 * GlobeRenderer uses in onSurfaceChanged, or picked points will drift.
 *
 * Coordinate system: +Y = North Pole, -X = Greenwich, +Z = 90°E.
 */
object GlobePicker {

    private const val FOV_DEG = 33f
    private const val SPHERE_RADIUS = 1.006  // marker shell radius

    /**
     * Returns [latDeg, lonDeg] of the tapped surface point, or null if the tap
     * missed the globe (sky) or the view is degenerate.
     */
    fun pick(camera: OrbitCamera, x: Float, y: Float, width: Int, height: Int): DoubleArray? {
        if (width <= 0 || height <= 0) return null

        val proj = FloatArray(16)
        Matrix.perspectiveM(proj, 0, FOV_DEG, width.toFloat() / height, 0.1f, 1000f)
        val viewProj = FloatArray(16)
        Matrix.multiplyMM(viewProj, 0, proj, 0, camera.getViewMatrix(), 0)
        val inv = FloatArray(16)
        if (!Matrix.invertM(inv, 0, viewProj, 0)) return null

        val ndcX = 2f * x / width - 1f
        val ndcY = 1f - 2f * y / height

        val near = unproject(inv, ndcX, ndcY, -1f) ?: return null
        val far = unproject(inv, ndcX, ndcY, 1f) ?: return null

        // Ray-sphere intersection (sphere centered at origin)
        val ox = near[0]; val oy = near[1]; val oz = near[2]
        var dx = far[0] - ox; var dy = far[1] - oy; var dz = far[2] - oz
        val len = Math.sqrt(dx * dx + dy * dy + dz * dz)
        if (len == 0.0) return null
        dx /= len; dy /= len; dz /= len

        val b = ox * dx + oy * dy + oz * dz
        val c = ox * ox + oy * oy + oz * oz - SPHERE_RADIUS * SPHERE_RADIUS
        val disc = b * b - c
        if (disc < 0) return null            // ray misses the globe
        val t = -b - Math.sqrt(disc)         // nearest intersection
        if (t < 0) return null               // sphere is behind the camera

        val px = ox + t * dx
        val py = oy + t * dy
        val pz = oz + t * dz

        val lat = Math.toDegrees(Math.asin((py / SPHERE_RADIUS).coerceIn(-1.0, 1.0)))
        val lon = Math.toDegrees(Math.atan2(pz, -px))  // -X = Greenwich, +Z = 90°E
        return doubleArrayOf(lat, lon)
    }

    private fun unproject(invViewProj: FloatArray, ndcX: Float, ndcY: Float, ndcZ: Float): DoubleArray? {
        val v = floatArrayOf(ndcX, ndcY, ndcZ, 1f)
        val out = FloatArray(4)
        Matrix.multiplyMV(out, 0, invViewProj, 0, v, 0)
        if (out[3] == 0f) return null
        return doubleArrayOf(
            (out[0] / out[3]).toDouble(),
            (out[1] / out[3]).toDouble(),
            (out[2] / out[3]).toDouble()
        )
    }
}
