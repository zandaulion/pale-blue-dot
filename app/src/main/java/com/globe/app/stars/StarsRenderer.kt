package com.globe.app.stars

import android.opengl.GLES30
import android.opengl.Matrix
import java.nio.FloatBuffer

/**
 * Manages the full render pipeline for the starfield background.
 *
 * Usage from the main GLSurfaceView.Renderer:
 *
 *   // In onSurfaceCreated:
 *   starsRenderer.init()
 *
 *   // In onDrawFrame (draw stars FIRST, before opaque geometry):
 *   starsRenderer.draw(viewMatrix, projectionMatrix)
 *
 *   // In cleanup / onPause:
 *   starsRenderer.destroy()
 *
 * The renderer strips the translation component from the view matrix
 * so that camera movement does not shift the stars — only rotation
 * affects them, producing a skybox-like illusion of infinite distance.
 */
class StarsRenderer {

    // GL handles
    private var vboId: Int = 0
    private var vaoId: Int = 0
    private var starCount: Int = 0

    // Time tracking for twinkle animation
    private var startTimeNanos: Long = 0L

    // Scratch matrix to avoid per-frame allocations
    private val vpMatrix = FloatArray(16)
    private val viewRotationOnly = FloatArray(16)

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    /**
     * Compile shaders, generate star vertex data, and upload to GPU.
     * Must be called on the GL thread (e.g. inside onSurfaceCreated).
     */
    fun init() {
        StarsShader.init()

        val vertexData: FloatBuffer = StarsModel.generateVertexData()
        starCount = StarsModel.starCount

        // Create VAO
        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        vaoId = vaos[0]
        GLES30.glBindVertexArray(vaoId)

        // Create & upload VBO
        val vbos = IntArray(1)
        GLES30.glGenBuffers(1, vbos, 0)
        vboId = vbos[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            starCount * StarsModel.STRIDE_BYTES,
            vertexData,
            GLES30.GL_STATIC_DRAW
        )

        val stride = StarsModel.STRIDE_BYTES

        // aPosition — 3 floats at offset 0
        GLES30.glEnableVertexAttribArray(StarsShader.aPositionLoc)
        GLES30.glVertexAttribPointer(
            StarsShader.aPositionLoc, 3, GLES30.GL_FLOAT, false, stride, 0
        )

        // aSize — 1 float at offset 12
        GLES30.glEnableVertexAttribArray(StarsShader.aSizeLoc)
        GLES30.glVertexAttribPointer(
            StarsShader.aSizeLoc, 1, GLES30.GL_FLOAT, false, stride, 3 * 4
        )

        // aColor — 4 floats at offset 16
        GLES30.glEnableVertexAttribArray(StarsShader.aColorLoc)
        GLES30.glVertexAttribPointer(
            StarsShader.aColorLoc, 4, GLES30.GL_FLOAT, false, stride, 4 * 4
        )

        // Unbind
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        startTimeNanos = System.nanoTime()
    }

    // ---------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------

    /**
     * Render the starfield. Call this **before** drawing opaque scene
     * geometry so that stars naturally appear behind everything.
     *
     * @param viewMatrix       The current camera view matrix (4x4, column-major).
     * @param projectionMatrix The current projection matrix (4x4, column-major).
     */
    fun draw(viewMatrix: FloatArray, projectionMatrix: FloatArray) {
        if (starCount == 0) return

        // Use the full view matrix so stars move in the same direction as the earth.
        // The star sphere radius (500) is large enough that camera translation
        // has no visible effect, but keeping it avoids directional mismatch.
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        // --- GL state for background stars ---
        // Disable depth writes so stars never occlude scene geometry,
        // but keep depth test off entirely since we draw first.
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)

        // Additive blending makes bright stars bloom naturally
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)

        // --- Draw ---
        GLES30.glUseProgram(StarsShader.programId)

        // Upload VP matrix
        GLES30.glUniformMatrix4fv(StarsShader.uVPMatrixLoc, 1, false, vpMatrix, 0)

        // Upload time for twinkle animation (seconds since init)
        val elapsed = (System.nanoTime() - startTimeNanos) / 1_000_000_000.0f
        GLES30.glUniform1f(StarsShader.uTimeLoc, elapsed)

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, starCount)
        GLES30.glBindVertexArray(0)

        // --- Restore state for subsequent scene rendering ---
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    // ---------------------------------------------------------------
    // Cleanup
    // ---------------------------------------------------------------

    /** Release all GL resources. Must be called on the GL thread. */
    fun destroy() {
        if (vaoId != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(vaoId), 0)
            vaoId = 0
        }
        if (vboId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
            vboId = 0
        }
        StarsShader.destroy()
        starCount = 0
    }
}
