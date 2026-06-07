package com.globe.app.camera

import android.opengl.Matrix
import java.util.TimeZone

/**
 * Simple orbit camera defined by azimuth, elevation, and distance from origin.
 * Thread-safe: touch events mutate from the UI thread, getViewMatrix() is called
 * from the GL thread.
 */
class OrbitCamera {

    @Volatile var azimuth: Float = run {
        val tz = TimeZone.getDefault()
        val offsetMillis = tz.getOffset(System.currentTimeMillis())
        val longitude = (offsetMillis / 3600000f) * 15f
        longitude - 90f
    }
    @Volatile var elevation: Float = 20f    // degrees, vertical
    @Volatile var distance: Float = 8.0f    // Earth-radii from origin

    // Momentum / inertia
    @Volatile private var velocityAz: Float = 0f   // degrees per frame
    @Volatile private var velocityEl: Float = 0f
    @Volatile private var isDragging: Boolean = false

    // Fly-to animation (eased toward a target view)
    @Volatile private var flying: Boolean = false
    @Volatile private var targetAz: Float = 0f
    @Volatile private var targetEl: Float = 0f
    @Volatile private var targetDist: Float = 0f

    // Idle auto-rotation
    @Volatile private var lastInteractionMs: Long = System.currentTimeMillis()

    companion object {
        private const val MIN_DISTANCE = 1.15f
        private const val MAX_DISTANCE = 20.0f
        private const val MAX_ELEVATION = 89f
        private const val ROTATE_SENSITIVITY = 0.15f
        private const val FRICTION = 0.985f
        private const val MIN_VELOCITY = 0.01f

        private const val FLY_EASE = 0.12f          // exponential smoothing per frame
        private const val FLY_DISTANCE = 2.8f       // zoom level when flying to a point
        private const val IDLE_DELAY_MS = 12_000L   // wait before idle spin kicks in
        private const val AUTO_ROTATE_SPEED = 0.04f // degrees per frame (~150 s / rev)
    }

    fun rotate(dx: Float, dy: Float) {
        val zoomScale = distance / MAX_DISTANCE
        val dAz = -dx * ROTATE_SENSITIVITY * zoomScale
        val dEl = dy * ROTATE_SENSITIVITY * zoomScale * 0.2f
        azimuth += dAz
        elevation = (elevation + dEl).coerceIn(-MAX_ELEVATION, MAX_ELEVATION)
        velocityAz = dAz
        velocityEl = dEl
        notifyInteraction()
    }

    fun startDrag() {
        isDragging = true
        flying = false
        velocityAz = 0f
        velocityEl = 0f
        notifyInteraction()
    }

    fun endDrag() {
        isDragging = false
    }

    /** Call once per frame to apply fly-to easing, momentum, or idle rotation. */
    fun update() {
        if (isDragging) return

        if (flying) {
            azimuth += (targetAz - azimuth) * FLY_EASE
            elevation = (elevation + (targetEl - elevation) * FLY_EASE)
                .coerceIn(-MAX_ELEVATION, MAX_ELEVATION)
            distance = (distance + (targetDist - distance) * FLY_EASE)
                .coerceIn(MIN_DISTANCE, MAX_DISTANCE)
            if (Math.abs(targetAz - azimuth) < 0.05f &&
                Math.abs(targetEl - elevation) < 0.05f &&
                Math.abs(targetDist - distance) < 0.01f
            ) {
                azimuth = targetAz
                elevation = targetEl
                distance = targetDist
                flying = false
            }
            return
        }

        // Momentum
        if (Math.abs(velocityAz) >= MIN_VELOCITY || Math.abs(velocityEl) >= MIN_VELOCITY) {
            azimuth += velocityAz
            elevation = (elevation + velocityEl).coerceIn(-MAX_ELEVATION, MAX_ELEVATION)
            velocityAz *= FRICTION
            velocityEl *= FRICTION
            return
        }

        // Idle auto-rotation — gentle spin once the user has been still a while
        if (System.currentTimeMillis() - lastInteractionMs > IDLE_DELAY_MS) {
            azimuth += AUTO_ROTATE_SPEED
        }
    }

    fun zoom(factor: Float) {
        distance = (distance * factor).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
        flying = false
        notifyInteraction()
    }

    /** Reset the idle timer so auto-rotation pauses while the user is engaged. */
    fun notifyInteraction() {
        lastInteractionMs = System.currentTimeMillis()
    }

    /**
     * Smoothly rotate the globe so the given surface point faces the camera.
     *
     * Coordinate system: +Y = North pole, -X = 0° (Greenwich), +Z = 90° E, so
     * elevation maps directly to latitude and azimuth derives from longitude.
     */
    fun flyTo(latDeg: Double, lonDeg: Double) {
        val lonRad = Math.toRadians(lonDeg)
        targetEl = latDeg.toFloat().coerceIn(-MAX_ELEVATION, MAX_ELEVATION)

        var ta = Math.toDegrees(Math.atan2(-Math.cos(lonRad), Math.sin(lonRad))).toFloat()
        // Take the shortest angular path from the current azimuth.
        while (ta - azimuth > 180f) ta -= 360f
        while (ta - azimuth < -180f) ta += 360f
        targetAz = ta
        targetDist = FLY_DISTANCE

        velocityAz = 0f
        velocityEl = 0f
        notifyInteraction()
        flying = true
    }

    /** Restore a previously saved camera pose (used on app restart). */
    fun restore(az: Float, el: Float, dist: Float) {
        azimuth = az
        elevation = el.coerceIn(-MAX_ELEVATION, MAX_ELEVATION)
        distance = dist.coerceIn(MIN_DISTANCE, MAX_DISTANCE)
        velocityAz = 0f
        velocityEl = 0f
        flying = false
        notifyInteraction()
    }

    fun getViewMatrix(): FloatArray {
        val viewMatrix = FloatArray(16)

        val azRad = Math.toRadians(azimuth.toDouble())
        val elRad = Math.toRadians(elevation.toDouble())

        val eyeX = (distance * Math.cos(elRad) * Math.sin(azRad)).toFloat()
        val eyeY = (distance * Math.sin(elRad)).toFloat()
        val eyeZ = (distance * Math.cos(elRad) * Math.cos(azRad)).toFloat()

        Matrix.setLookAtM(
            viewMatrix, 0,
            eyeX, eyeY, eyeZ,  // eye
            0f, 0f, 0f,        // center (Earth at origin)
            0f, 1f, 0f         // up
        )
        return viewMatrix
    }

    /**
     * Returns the camera's eye position in world space as [x, y, z].
     * Used for fresnel/atmosphere calculations in the Earth shader.
     */
    fun getPosition(): FloatArray {
        val azRad = Math.toRadians(azimuth.toDouble())
        val elRad = Math.toRadians(elevation.toDouble())

        return floatArrayOf(
            (distance * Math.cos(elRad) * Math.sin(azRad)).toFloat(),
            (distance * Math.sin(elRad)).toFloat(),
            (distance * Math.cos(elRad) * Math.cos(azRad)).toFloat()
        )
    }
}
