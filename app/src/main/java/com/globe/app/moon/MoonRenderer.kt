package com.globe.app.moon

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import com.globe.app.earth.EarthModel
import com.globe.app.earth.SunPosition

/**
 * Renders the Moon as a small lit sphere at the astronomically correct position.
 * Phase is produced naturally by sun lighting.
 *
 * The Moon is placed at [MOON_DISTANCE] units from the origin along the
 * computed direction, and rendered as a sphere of radius [MOON_RADIUS].
 */
class MoonRenderer {

    companion object {
        private const val TAG = "MoonRenderer"
        /** Distance from Earth center (visual, not to scale) */
        private const val MOON_DISTANCE = 30f
        /** Visual radius of the Moon sphere */
        private const val MOON_RADIUS = 0.6f
    }

    private var gpuBuffers: EarthModel.GpuBuffers? = null
    private var textureId: Int = 0

    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    fun init(context: Context, textureResId: Int = 0) {
        MoonShader.init()

        // Reuse EarthModel for a sphere mesh (lower resolution is fine)
        val model = EarthModel(latSegments = 24, lonSegments = 48)
        gpuBuffers = model.uploadToGpu()

        textureId = if (textureResId != 0) {
            loadTexture(context, textureResId)
        } else {
            0
        }
    }

    fun draw(viewMatrix: FloatArray, projectionMatrix: FloatArray) {
        val buffers = gpuBuffers ?: return
        if (textureId == 0) return

        val moonDir = MoonPosition.calculate(null)
        val sunDir = SunPosition.calculate(null)

        // Build model matrix: translate to moon position, scale to moon size
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(
            modelMatrix, 0,
            moonDir[0] * MOON_DISTANCE,
            moonDir[1] * MOON_DISTANCE,
            moonDir[2] * MOON_DISTANCE
        )
        Matrix.scaleM(modelMatrix, 0, MOON_RADIUS, MOON_RADIUS, MOON_RADIUS)

        // MVP = projection * view * model
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)

        GLES30.glUseProgram(MoonShader.programId)
        GLES30.glUniformMatrix4fv(MoonShader.uMVPLoc, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(MoonShader.uModelLoc, 1, false, modelMatrix, 0)
        GLES30.glUniform3f(MoonShader.uSunDirectionLoc, sunDir[0], sunDir[1], sunDir[2])

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(MoonShader.uMoonTextureLoc, 0)

        GLES30.glBindVertexArray(buffers.vao)
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
        MoonShader.destroy()
    }

    private fun loadTexture(context: Context, resourceId: Int): Int {
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        val texId = textureIds[0]

        if (texId == 0) {
            Log.e(TAG, "glGenTextures failed")
            return 0
        }

        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)
            ?: run {
                Log.e(TAG, "Failed to decode resource $resourceId")
                GLES30.glDeleteTextures(1, textureIds, 0)
                return 0
            }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)

        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)

        bitmap.recycle()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        return texId
    }
}
