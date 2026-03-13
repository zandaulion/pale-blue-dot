package com.globe.app.sun

import android.opengl.GLES30
import android.opengl.Matrix
import com.globe.app.earth.SunPosition
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Renders the Sun as a camera-facing billboard quad with a glowing corona.
 * Placed at the astronomically correct direction at a large distance.
 * Uses additive blending so the glow naturally blooms over the background.
 */
class SunRenderer {

    companion object {
        /** Distance from origin — behind the star sphere isn't needed,
         *  just far enough to look correct relative to the moon/earth. */
        private const val SUN_DISTANCE = 80f
        /** Half-size of the billboard quad */
        private const val SUN_SIZE = 6f
    }

    private var vaoId: Int = 0
    private var vboId: Int = 0

    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    fun init() {
        SunShader.init()

        // Billboard quad: 4 vertices with position (x,y,z) and UV (u,v)
        // Positions are in local space, centered at origin
        val s = SUN_SIZE
        val vertices = floatArrayOf(
            // x,     y,    z,    u,   v
            -s,   -s,  0f,  0f,  0f,
             s,   -s,  0f,  1f,  0f,
             s,    s,  0f,  1f,  1f,
            -s,   -s,  0f,  0f,  0f,
             s,    s,  0f,  1f,  1f,
            -s,    s,  0f,  0f,  1f,
        )

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
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertices.size * 4,
            buffer,
            GLES30.GL_STATIC_DRAW
        )

        val stride = 5 * 4 // 5 floats * 4 bytes
        // aPosition (location 0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        // aTexCoord (location 1)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, 3 * 4)

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun draw(viewMatrix: FloatArray, projectionMatrix: FloatArray) {
        val sunDir = SunPosition.calculate()

        // Position the billboard at the sun direction
        val px = sunDir[0] * SUN_DISTANCE
        val py = sunDir[1] * SUN_DISTANCE
        val pz = sunDir[2] * SUN_DISTANCE

        // Build a billboard model matrix that always faces the camera.
        // Extract the camera right and up vectors from the view matrix.
        // View matrix rows 0 and 1 give world-space right and up.
        val rightX = viewMatrix[0]; val rightY = viewMatrix[4]; val rightZ = viewMatrix[8]
        val upX = viewMatrix[1];    val upY = viewMatrix[5];    val upZ = viewMatrix[9]

        // Build model matrix: columns are right, up, forward (toward camera), translation
        Matrix.setIdentityM(modelMatrix, 0)
        modelMatrix[0]  = rightX;  modelMatrix[1]  = rightY;  modelMatrix[2]  = rightZ
        modelMatrix[4]  = upX;     modelMatrix[5]  = upY;     modelMatrix[6]  = upZ
        // Forward = cross(right, up) — but we don't need it for a flat quad
        modelMatrix[8]  = -(rightY * upZ - rightZ * upY)
        modelMatrix[9]  = -(rightZ * upX - rightX * upZ)
        modelMatrix[10] = -(rightX * upY - rightY * upX)
        modelMatrix[12] = px; modelMatrix[13] = py; modelMatrix[14] = pz

        // MVP
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        // Additive blending for glow
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)

        GLES30.glUseProgram(SunShader.programId)
        GLES30.glUniformMatrix4fv(SunShader.uMVPLoc, 1, false, mvpMatrix, 0)

        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        GLES30.glBindVertexArray(0)

        // Restore state
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
        SunShader.destroy()
    }
}
