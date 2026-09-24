package com.globe.app.render

import android.opengl.Matrix

/** Shared projection settings for draw, tap unprojection, and screen overlays. */
object SceneProjection {
    const val FOV_DEG = 33f
    const val NEAR = 0.1f
    const val FAR = 1000f

    fun perspective(width: Int, height: Int, frameX: Float = 0f, frameY: Float = 0f): FloatArray =
        FloatArray(16).also {
            Matrix.perspectiveM(it, 0, FOV_DEG, width.toFloat() / height.coerceAtLeast(1), NEAR, FAR)
            it[8] = frameX
            it[9] = frameY
        }
}
