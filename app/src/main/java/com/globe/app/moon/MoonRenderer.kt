package com.globe.app.moon

import android.content.Context
import android.opengl.GLES30
import android.opengl.Matrix
import com.globe.app.earth.EarthModel
import com.globe.app.earth.SunPosition
import com.globe.app.render.TextureLoader
import com.globe.app.render.TextureQuality

/**
 * Renders the Moon as a small lit sphere at the astronomically correct position.
 * Phase is produced naturally by sun lighting.
 *
 * The visual distance and radius are shared with the label in [MoonGuide].
 */
class MoonRenderer {
    private val shader = MoonShader()

    private var gpuBuffers: EarthModel.GpuBuffers? = null
    private var textureId: Int = 0
    private var outlineWidthPx = 1f
    private var surfaceWidth = 1
    private var surfaceHeight = 1

    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    fun init(context: Context, textureResId: Int = 0, maxTextureWidth: Int = TextureQuality.FULL.moonMaxWidth) {
        shader.init()
        outlineWidthPx = context.resources.displayMetrics.density

        // Reuse EarthModel for a sphere mesh (lower resolution is fine)
        val model = EarthModel(latSegments = 24, lonSegments = 48)
        gpuBuffers = model.uploadToGpu()

        textureId = if (textureResId != 0) {
            TextureLoader.load(context, textureResId, maxTextureWidth)
        } else {
            0
        }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
    }

    fun draw(viewMatrix: FloatArray, projectionMatrix: FloatArray, timeMs: Long) {
        val buffers = gpuBuffers ?: return
        if (textureId == 0) return

        val moonDir = MoonPosition.calculate(timeMs)
        val sunDir = SunPosition.calculate(timeMs)

        // Build model matrix: translate to moon position, scale to moon size
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(
            modelMatrix, 0,
            moonDir[0] * MoonGuide.DISTANCE,
            moonDir[1] * MoonGuide.DISTANCE,
            moonDir[2] * MoonGuide.DISTANCE
        )
        Matrix.scaleM(modelMatrix, 0, MoonGuide.RADIUS, MoonGuide.RADIUS, MoonGuide.RADIUS)

        // MVP = projection * view * model
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)

        GLES30.glUseProgram(shader.programId)
        GLES30.glUniformMatrix4fv(shader.uMVPLoc, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(shader.uModelLoc, 1, false, modelMatrix, 0)
        GLES30.glUniform3f(shader.uSunDirectionLoc, sunDir[0], sunDir[1], sunDir[2])

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(shader.uMoonTextureLoc, 0)

        GLES30.glBindVertexArray(buffers.vao)
        val guideStrength = MoonGuide.strength(MoonGuide.illumination(moonDir, sunDir))
        if (guideStrength > 0f) {
            GLES30.glUniform1i(shader.uOutlinePassLoc, 1)
            GLES30.glUniform1f(shader.uOutlineStrengthLoc, guideStrength)
            GLES30.glUniform2f(shader.uViewportLoc, surfaceWidth.toFloat(), surfaceHeight.toFloat())
            GLES30.glUniform1f(shader.uOutlineWidthLoc, outlineWidthPx)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            // The surface overwrites the interior; Earth still occludes the guide.
            GLES30.glDepthMask(false)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, buffers.indexCount, GLES30.GL_UNSIGNED_INT, 0)
            GLES30.glDepthMask(true)
            GLES30.glDisable(GLES30.GL_BLEND)
        }
        GLES30.glUniform1i(shader.uOutlinePassLoc, 0)
        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES,
            buffers.indexCount,
            GLES30.GL_UNSIGNED_INT,
            0
        )
        GLES30.glBindVertexArray(0)
    }

    fun destroy() {
        gpuBuffers?.release()
        gpuBuffers = null
        if (textureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
        shader.destroy()
    }
}
