package com.globe.app.iss

import android.opengl.GLES30
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Calendar
import java.util.TimeZone

/**
 * Renders the ISS orbital path as a ribbon (triangle strip) around the Earth,
 * plus a marker dot at the ISS's approximate real-time position.
 *
 * Uses simplified Keplerian orbital mechanics:
 *   - Circular orbit at ~408 km altitude (radius ≈ 1.064 Earth radii)
 *   - 51.6° inclination
 *   - ~92.68 min orbital period (mean motion 15.49 rev/day)
 *   - RAAN precesses at ~-5°/day due to J2 perturbation
 *
 * Coordinate system: +Y = North Pole, -X = Greenwich, +Z = 90°E.
 */
class ISSOrbitRenderer {

    companion object {
        private const val SEGMENT_COUNT = 256
        /** ISS altitude ~408 km, Earth radius ~6371 km -> model radius ~ 1.064 */
        private const val ORBIT_RADIUS = 1.064f
        /** ISS orbital inclination in degrees */
        private const val INCLINATION_DEG = 51.6f
        /** Half-width of the orbit ribbon in model units */
        private const val RIBBON_HALF_WIDTH = 0.003f
        /** ISS mean motion in revolutions per day */
        private const val MEAN_MOTION = 15.49
        /** RAAN precession rate in degrees per day (negative = westward) */
        private const val RAAN_RATE_DEG_PER_DAY = -5.0
        /** Reference epoch: 2025-01-01 00:00:00 UTC (milliseconds) */
        private val EPOCH_MS: Long = run {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.set(2025, Calendar.JANUARY, 1, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        /** RAAN at the reference epoch (degrees) */
        private const val RAAN_EPOCH_DEG = 120.0
        /** Mean anomaly at the reference epoch (degrees) */
        private const val MA_EPOCH_DEG = 0.0
        /** Marker point size in pixels */
        private const val ISS_POINT_SIZE = 28.0f
        /** Number of vertices in the ribbon strip: 2 per segment + 2 to close */
        private const val RIBBON_VERTEX_COUNT = (SEGMENT_COUNT + 1) * 2
    }

    private var programId = 0
    private var orbitVao = 0
    private var orbitVbo = 0
    private var markerVao = 0
    private var markerVbo = 0

    private var uMVPLoc = -1
    private var uColorLoc = -1
    private var uPointSizeLoc = -1
    private var uIsPointLoc = -1

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
            throw RuntimeException("ISS orbit shader link failed: $log")
        }

        GLES30.glDeleteShader(vertId)
        GLES30.glDeleteShader(fragId)

        uMVPLoc = GLES30.glGetUniformLocation(programId, "uMVP")
        uColorLoc = GLES30.glGetUniformLocation(programId, "uColor")
        uPointSizeLoc = GLES30.glGetUniformLocation(programId, "uPointSize")
        uIsPointLoc = GLES30.glGetUniformLocation(programId, "uIsPoint")

        // --- Orbit ribbon geometry (RAAN = 0) ---
        val ribbonData = generateRibbonVertices()

        val vaos = IntArray(2)
        GLES30.glGenVertexArrays(2, vaos, 0)
        orbitVao = vaos[0]
        markerVao = vaos[1]

        val vbos = IntArray(2)
        GLES30.glGenBuffers(2, vbos, 0)
        orbitVbo = vbos[0]
        markerVbo = vbos[1]

        // Orbit ribbon VAO
        GLES30.glBindVertexArray(orbitVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, orbitVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            ribbonData.capacity() * 4, ribbonData, GLES30.GL_STATIC_DRAW
        )
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindVertexArray(0)

        // Marker VAO — single point, updated each frame
        val markerInit = ByteBuffer.allocateDirect(3 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        markerInit.put(floatArrayOf(0f, 0f, 0f)).position(0)

        GLES30.glBindVertexArray(markerVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, markerVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER, 3 * 4, markerInit, GLES30.GL_DYNAMIC_DRAW
        )
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindVertexArray(0)
    }

    fun draw(viewMatrix: FloatArray, projectionMatrix: FloatArray) {
        if (programId == 0) return

        GLES30.glUseProgram(programId)

        // Compute current RAAN and mean anomaly
        val now = System.currentTimeMillis()
        val daysSinceEpoch = (now - EPOCH_MS) / 86_400_000.0
        val raanDeg = (RAAN_EPOCH_DEG + RAAN_RATE_DEG_PER_DAY * daysSinceEpoch) % 360.0
        val maDeg = (MA_EPOCH_DEG + MEAN_MOTION * 360.0 * daysSinceEpoch) % 360.0

        // Model matrix: rotate orbit plane around Y by current RAAN
        val modelMatrix = FloatArray(16)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, raanDeg.toFloat(), 0f, 1f, 0f)

        // MVP = projection * view * model
        val mvMatrix = FloatArray(16)
        val mvpMatrix = FloatArray(16)
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0)

        GLES30.glUniformMatrix4fv(uMVPLoc, 1, false, mvpMatrix, 0)

        // GL state
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        // --- Draw orbit ribbon ---
        GLES30.glUniform4f(uColorLoc, 1.0f, 0.85f, 0.2f, 0.7f)
        GLES30.glUniform1f(uPointSizeLoc, 1.0f)
        GLES30.glUniform1f(uIsPointLoc, 0.0f)

        GLES30.glBindVertexArray(orbitVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, RIBBON_VERTEX_COUNT)
        GLES30.glBindVertexArray(0)

        // --- Draw ISS marker ---
        val issPos = computeISSPosition(maDeg)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, markerVbo)
        val posBuf = ByteBuffer.allocateDirect(3 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        posBuf.put(issPos).position(0)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, 3 * 4, posBuf)

        GLES30.glUniform4f(uColorLoc, 1.0f, 1.0f, 1.0f, 1.0f)
        GLES30.glUniform1f(uPointSizeLoc, ISS_POINT_SIZE)
        GLES30.glUniform1f(uIsPointLoc, 1.0f)

        GLES30.glBindVertexArray(markerVao)
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
        if (orbitVao != 0) {
            GLES30.glDeleteVertexArrays(2, intArrayOf(orbitVao, markerVao), 0)
            orbitVao = 0; markerVao = 0
        }
        if (orbitVbo != 0) {
            GLES30.glDeleteBuffers(2, intArrayOf(orbitVbo, markerVbo), 0)
            orbitVbo = 0; markerVbo = 0
        }
    }

    // ------------------------------------------------------------------
    // Orbit geometry
    // ------------------------------------------------------------------

    /**
     * Generates a triangle-strip ribbon for the orbit.
     * For each point on the orbit circle, two vertices are emitted:
     * one offset inward and one outward along the radial direction.
     * The strip wraps around and closes by repeating the first pair.
     */
    private fun generateRibbonVertices(): FloatBuffer {
        val incRad = Math.toRadians(INCLINATION_DEG.toDouble())
        val cosInc = Math.cos(incRad).toFloat()
        val sinInc = Math.sin(incRad).toFloat()

        // 2 vertices per segment + 2 to close the loop
        val data = FloatArray(RIBBON_VERTEX_COUNT * 3)

        for (i in 0..SEGMENT_COUNT) {
            val idx = i % SEGMENT_COUNT
            val angle = 2.0 * Math.PI * idx / SEGMENT_COUNT
            val cosA = Math.cos(angle).toFloat()
            val sinA = Math.sin(angle).toFloat()

            // Centre point on orbit (before inclination tilt)
            val cx = ORBIT_RADIUS * cosA
            val cz = ORBIT_RADIUS * sinA

            // Radial direction in the equatorial plane (outward from centre)
            // For a circle in XZ, the radial direction is simply (cosA, 0, sinA)
            val nx = cosA
            val nz = sinA

            // Inner and outer points offset along the radial direction
            val inX = cx - RIBBON_HALF_WIDTH * nx
            val inZ = cz - RIBBON_HALF_WIDTH * nz
            val outX = cx + RIBBON_HALF_WIDTH * nx
            val outZ = cz + RIBBON_HALF_WIDTH * nz

            val base = i * 6
            // Inner vertex — apply inclination tilt (rotate around Z)
            data[base + 0] = inX * cosInc
            data[base + 1] = inX * sinInc
            data[base + 2] = inZ
            // Outer vertex
            data[base + 3] = outX * cosInc
            data[base + 4] = outX * sinInc
            data[base + 5] = outZ
        }

        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(data); position(0) }
    }

    /**
     * Computes the ISS position on the canonical (RAAN=0) orbit
     * for the given mean anomaly in degrees.
     */
    private fun computeISSPosition(maDeg: Double): FloatArray {
        val maRad = Math.toRadians(maDeg)
        val incRad = Math.toRadians(INCLINATION_DEG.toDouble())
        val cosInc = Math.cos(incRad).toFloat()
        val sinInc = Math.sin(incRad).toFloat()

        val cosA = Math.cos(maRad).toFloat()
        val sinA = Math.sin(maRad).toFloat()

        val ex = ORBIT_RADIUS * cosA
        val ez = ORBIT_RADIUS * sinA

        return floatArrayOf(
            ex * cosInc,
            ex * sinInc,
            ez
        )
    }

    // ------------------------------------------------------------------
    // Shader compilation
    // ------------------------------------------------------------------

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("ISS orbit shader compile failed: $log")
        }
        return shader
    }

    // ------------------------------------------------------------------
    // GLSL sources
    // ------------------------------------------------------------------

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
        uniform float uIsPoint;

        out vec4 fragColor;

        void main() {
            if (uIsPoint > 0.5) {
                // Point sprite: render as soft circle
                vec2 coord = gl_PointCoord - vec2(0.5);
                float dist = length(coord) * 2.0;
                if (dist > 1.0) discard;
                float alpha = uColor.a * smoothstep(1.0, 0.3, dist);
                fragColor = vec4(uColor.rgb, alpha);
            } else {
                // Ribbon: flat color
                fragColor = uColor;
            }
        }
    """.trimIndent()
}
