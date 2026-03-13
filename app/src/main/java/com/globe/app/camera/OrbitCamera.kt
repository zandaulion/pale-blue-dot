package com.globe.app.camera

import android.opengl.Matrix

/**
 * Simple orbit camera defined by azimuth, elevation, and distance from origin.
 * Thread-safe: touch events mutate from the UI thread, getViewMatrix() is called
 * from the GL thread.
 */
class OrbitCamera {

    @Volatile var azimuth: Float = 0f       // degrees, horizontal
    @Volatile var elevation: Float = 20f    // degrees, vertical
    @Volatile var distance: Float = 4.5f    // Earth-radii from origin

    // Momentum / inertia
    @Volatile private var velocityAz: Float = 0f   // degrees per frame
    @Volatile private var velocityEl: Float = 0f
    @Volatile private var isDragging: Boolean = false

    companion object {
        private const val MIN_DISTANCE = 1.15f
        private const val MAX_DISTANCE = 8.0f
        private const val MAX_ELEVATION = 89f
        private const val ROTATE_SENSITIVITY = 0.3f
        private const val FRICTION = 0.985f
        private const val MIN_VELOCITY = 0.01f
    }

    fun rotate(dx: Float, dy: Float) {
        val zoomScale = distance / MAX_DISTANCE
        val dAz = dx * ROTATE_SENSITIVITY * zoomScale
        val dEl = -dy * ROTATE_SENSITIVITY * zoomScale * 0.4f
        azimuth += dAz
        elevation = (elevation + dEl).coerceIn(-MAX_ELEVATION, MAX_ELEVATION)
        velocityAz = dAz
        velocityEl = dEl
    }

    fun startDrag() {
        isDragging = true
        velocityAz = 0f
        velocityEl = 0f
    }

    fun endDrag() {
        isDragging = false
    }

    /** Call once per frame to apply momentum. */
    fun update() {
        if (isDragging) return
        if (Math.abs(velocityAz) < MIN_VELOCITY && Math.abs(velocityEl) < MIN_VELOCITY) return
        azimuth += velocityAz
        elevation = (elevation + velocityEl).coerceIn(-MAX_ELEVATION, MAX_ELEVATION)
        velocityAz *= FRICTION
        velocityEl *= FRICTION
    }

    fun zoom(factor: Float) {
        distance = (distance * factor).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
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
