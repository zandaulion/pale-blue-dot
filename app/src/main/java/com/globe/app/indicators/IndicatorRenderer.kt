package com.globe.app.indicators

import android.opengl.GLES30
import android.opengl.Matrix
import com.globe.app.earth.SunPosition
import com.globe.app.moon.MoonPosition
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.atan2

/**
 * Renders minimal directional arrows at the bottom of the screen pointing
 * toward the sun and moon. Each arrow is a slim pointer shape with a subtle
 * glow layer behind it.
 */
class IndicatorRenderer {

    companion object {
        /** Arrow size in NDC units (height). */
        private const val ARROW_SIZE = 0.045f
        /** Glow layer scale multiplier. */
        private const val GLOW_SCALE = 1.6f
        /** Glow layer alpha multiplier. */
        private const val GLOW_ALPHA = 0.18f
        /** Vertical position of arrows in NDC (-1 = bottom, 1 = top). */
        private const val ARROW_Y = -0.88f
        /** Horizontal offset from center for each arrow. */
        private const val ARROW_SPACING = 0.10f

        // Sun: warm amber gold
        private val SUN_COLOR = floatArrayOf(1.0f, 0.76f, 0.16f, 0.85f)
        // Moon: cool pale silver-blue
        private val MOON_COLOR = floatArrayOf(0.72f, 0.80f, 0.92f, 0.85f)
    }

    private var vaoId: Int = 0
    private var vboId: Int = 0
    private var vertexCount: Int = 0

    private var aspectRatio: Float = 1f

    private val transform = FloatArray(16)
    private val tempVec = FloatArray(4)
    private val clipVec = FloatArray(4)
    private val vpMatrix = FloatArray(16)

    fun init() {
        IndicatorShader.init()

        // Arrow shape: slim pointer with notch at base, pointing up (+Y).
        // Coordinates in local space, centered at origin.
        val vertices = floatArrayOf(
            // Triangle 1: tip → left → notch
             0.0f,   0.65f,
            -0.30f, -0.50f,
             0.0f,  -0.15f,
            // Triangle 2: tip → notch → right
             0.0f,   0.65f,
             0.0f,  -0.15f,
             0.30f, -0.50f,
        )
        vertexCount = vertices.size / 2

        val buffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(vertices); position(0) }

        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        vaoId = vaos[0]
        GLES30.glBindVertexArray(vaoId)

        val vbos = IntArray(1)
        GLES30.glGenBuffers(1, vbos, 0)
        vboId = vbos[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, buffer, GLES30.GL_STATIC_DRAW)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        aspectRatio = width.toFloat() / height.toFloat()
    }

    fun draw(viewMatrix: FloatArray, projectionMatrix: FloatArray) {
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        val sunDir = SunPosition.calculate()
        val moonDir = MoonPosition.calculate()

        val sunAngle = directionAngle(sunDir, -ARROW_SPACING, ARROW_Y)
        val moonAngle = directionAngle(moonDir, ARROW_SPACING, ARROW_Y)

        // Setup GL state for 2D overlay
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        GLES30.glUseProgram(IndicatorShader.programId)
        GLES30.glBindVertexArray(vaoId)

        // Draw sun arrow (left position)
        drawArrow(-ARROW_SPACING, ARROW_Y, sunAngle, SUN_COLOR)
        // Draw moon arrow (right position)
        drawArrow(ARROW_SPACING, ARROW_Y, moonAngle, MOON_COLOR)

        GLES30.glBindVertexArray(0)

        // Restore GL state
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    fun destroy() {
        if (vaoId != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(vaoId), 0)
            vaoId = 0
        }
        if (vboId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
            vboId = 0
        }
        IndicatorShader.destroy()
    }

    // ------------------------------------------------------------------

    /**
     * Draws one arrow: first a larger translucent glow layer, then the
     * sharp foreground layer on top.
     */
    private fun drawArrow(posX: Float, posY: Float, angleDeg: Float, color: FloatArray) {
        // Glow layer
        buildTransform(posX, posY, angleDeg, ARROW_SIZE * GLOW_SCALE)
        GLES30.glUniformMatrix4fv(IndicatorShader.uTransformLoc, 1, false, transform, 0)
        GLES30.glUniform4f(
            IndicatorShader.uColorLoc,
            color[0], color[1], color[2], color[3] * GLOW_ALPHA
        )
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)

        // Foreground layer
        buildTransform(posX, posY, angleDeg, ARROW_SIZE)
        GLES30.glUniformMatrix4fv(IndicatorShader.uTransformLoc, 1, false, transform, 0)
        GLES30.glUniform4f(
            IndicatorShader.uColorLoc,
            color[0], color[1], color[2], color[3]
        )
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
    }

    /**
     * Builds a 2D transform matrix: translate to [posX],[posY] in NDC,
     * rotate by [angleDeg], and scale to [size] (aspect-corrected).
     */
    private fun buildTransform(posX: Float, posY: Float, angleDeg: Float, size: Float) {
        Matrix.setIdentityM(transform, 0)
        Matrix.translateM(transform, 0, posX, posY, 0f)
        Matrix.rotateM(transform, 0, angleDeg, 0f, 0f, 1f)
        Matrix.scaleM(transform, 0, size / aspectRatio, size, 1f)
    }

    /**
     * Computes the rotation angle (in degrees) that makes the arrow point
     * from its anchor position toward the projected world-space direction.
     */
    private fun directionAngle(worldDir: FloatArray, anchorX: Float, anchorY: Float): Float {
        // Project the world direction onto screen (treat as a point at distance 1)
        tempVec[0] = worldDir[0]
        tempVec[1] = worldDir[1]
        tempVec[2] = worldDir[2]
        tempVec[3] = 1f
        Matrix.multiplyMV(clipVec, 0, vpMatrix, 0, tempVec, 0)

        var ndcX: Float
        var ndcY: Float

        if (clipVec[3] <= 0f) {
            // Behind camera — flip direction so arrow points toward the edge
            ndcX = -clipVec[0]
            ndcY = -clipVec[1]
        } else {
            ndcX = clipVec[0] / clipVec[3]
            ndcY = clipVec[1] / clipVec[3]
        }

        // Direction from anchor to target, corrected for aspect ratio
        val dx = (ndcX - anchorX) * aspectRatio
        val dy = ndcY - anchorY

        // atan2 gives angle from +X axis; subtract 90° because arrow points up
        val angleRad = atan2(dy.toDouble(), dx.toDouble())
        return Math.toDegrees(angleRad).toFloat() - 90f
    }
}
