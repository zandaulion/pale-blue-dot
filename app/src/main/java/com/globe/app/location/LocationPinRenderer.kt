package com.globe.app.location

import android.opengl.GLES30
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Renders a small colored dot on the Earth's surface at the user's GPS location.
 *
 * Coordinate system: +Y = North Pole, -X = Greenwich (0 lon), +Z = 90 E.
 * The pin is placed at radius 1.005 to avoid z-fighting with the Earth surface.
 */
class LocationPinRenderer {

    companion object {
        /** Slightly above the unit sphere to avoid z-fighting */
        private const val PIN_RADIUS = 1.005f
        /** Point sprite size in pixels */
        private const val PIN_POINT_SIZE = 24.0f
    }

    private var programId = 0
    private var vao = 0
    private var vbo = 0

    private var uMVPLoc = -1
    private var uColorLoc = -1
    private var uPointSizeLoc = -1

    // Thread-safe location storage (written from UI thread, read from GL thread)
    @Volatile
    private var hasLocation = false
    @Volatile
    private var latDeg = 0.0
    @Volatile
    private var lonDeg = 0.0

    /**
     * Called from the UI thread when the user's location changes.
     */
    fun setLocation(lat: Double, lon: Double) {
        latDeg = lat
        lonDeg = lon
        hasLocation = true
    }

    fun init() {
        val vertId = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragId = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vertId)
        GLES30.glAttachShader(programId, fragId)
        GLES30.glLinkProgram(programId)

        val status = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(programId)
            GLES30.glDeleteProgram(programId)
            throw RuntimeException("Location pin shader link failed: $log")
        }

        GLES30.glDeleteShader(vertId)
        GLES30.glDeleteShader(fragId)

        uMVPLoc = GLES30.glGetUniformLocation(programId, "uMVP")
        uColorLoc = GLES30.glGetUniformLocation(programId, "uColor")
        uPointSizeLoc = GLES30.glGetUniformLocation(programId, "uPointSize")

        // Single-point VAO/VBO
        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        vao = vaos[0]

        val vbos = IntArray(1)
        GLES30.glGenBuffers(1, vbos, 0)
        vbo = vbos[0]

        val initBuf = ByteBuffer.allocateDirect(3 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        initBuf.put(floatArrayOf(0f, 0f, 0f)).position(0)

        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 3 * 4, initBuf, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindVertexArray(0)
    }

    fun draw(viewMatrix: FloatArray, projectionMatrix: FloatArray) {
        if (programId == 0 || !hasLocation) return

        // Convert lat/lon to 3D position
        // Coordinate system: +Y = North Pole, -X = Greenwich, +Z = 90 E
        val latRad = Math.toRadians(latDeg)
        val lonRad = Math.toRadians(lonDeg)

        val cosLat = Math.cos(latRad).toFloat()
        val sinLat = Math.sin(latRad).toFloat()
        val cosLon = Math.cos(lonRad).toFloat()
        val sinLon = Math.sin(lonRad).toFloat()

        // Standard spherical: x = cos(lat)*cos(lon), y = sin(lat), z = cos(lat)*sin(lon)
        // But in this coordinate system, -X = Greenwich (lon=0), so negate X
        val x = -PIN_RADIUS * cosLat * cosLon
        val y = PIN_RADIUS * sinLat
        val z = PIN_RADIUS * cosLat * sinLon

        // Update VBO with new position
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        val posBuf = ByteBuffer.allocateDirect(3 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        posBuf.put(floatArrayOf(x, y, z)).position(0)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, 3 * 4, posBuf)

        // MVP = projection * view (no model transform needed, position is in world space)
        val mvpMatrix = FloatArray(16)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        GLES30.glUseProgram(programId)
        GLES30.glUniformMatrix4fv(uMVPLoc, 1, false, mvpMatrix, 0)
        GLES30.glUniform4f(uColorLoc, 0.0f, 0.9f, 0.8f, 1.0f) // cyan-green
        GLES30.glUniform1f(uPointSizeLoc, PIN_POINT_SIZE)

        // GL state: depth tested (hidden on far side), blended for soft edges
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, 1)
        GLES30.glBindVertexArray(0)

        // Restore GL state
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    fun destroy() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
        if (vao != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
            vao = 0
        }
        if (vbo != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
            vbo = 0
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Location pin shader compile failed: $log")
        }
        return shader
    }

    private val VERTEX_SHADER = """
        #version 300 es
        precision highp float;

        layout(location = 0) in vec3 aPosition;

        uniform mat4 uMVP;
        uniform float uPointSize;

        void main() {
            gl_Position = uMVP * vec4(aPosition, 1.0);
            gl_PointSize = uPointSize;
        }
    """.trimIndent()

    private val FRAGMENT_SHADER = """
        #version 300 es
        precision mediump float;

        uniform vec4 uColor;

        out vec4 fragColor;

        void main() {
            vec2 coord = gl_PointCoord - vec2(0.5);
            float dist = length(coord) * 2.0;
            if (dist > 1.0) discard;
            float alpha = uColor.a * smoothstep(1.0, 0.3, dist);
            fragColor = vec4(uColor.rgb, alpha);
        }
    """.trimIndent()
}
