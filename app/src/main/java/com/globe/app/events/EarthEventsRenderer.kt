package com.globe.app.events

import android.opengl.GLES30
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

/**
 * Renders earthquake and volcano markers as pulsing point sprites on the globe.
 * Earthquakes are orange-red with size proportional to magnitude.
 * Volcanoes are bright yellow.
 *
 * Coordinate system: +Y = North Pole, -X = Greenwich, +Z = 90°E.
 */
class EarthEventsRenderer {

    companion object {
        private const val PIN_RADIUS = 1.006f
        /** Floats per vertex: x, y, z, pointSize, type (0=quake, 1=volcano) */
        private const val FLOATS_PER_VERTEX = 5
        private const val STRIDE = FLOATS_PER_VERTEX * 4
    }

    private var programId = 0
    private var vao = 0
    private var vbo = 0
    private var vertexCount = 0

    private var uMVPLoc = -1
    private var uTimeLoc = -1

    private var startTimeMs = 0L

    // Thread-safe event data (set from background thread, consumed on GL thread)
    private val pendingEvents = AtomicReference<List<EarthEventsProvider.Event>?>(null)

    fun setEvents(events: List<EarthEventsProvider.Event>) {
        pendingEvents.set(events)
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
            throw RuntimeException("EarthEvents shader link failed: $log")
        }

        GLES30.glDeleteShader(vertId)
        GLES30.glDeleteShader(fragId)

        uMVPLoc = GLES30.glGetUniformLocation(programId, "uMVP")
        uTimeLoc = GLES30.glGetUniformLocation(programId, "uTime")

        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        vao = vaos[0]

        val vbos = IntArray(1)
        GLES30.glGenBuffers(1, vbos, 0)
        vbo = vbos[0]

        // Set up VAO with empty buffer
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        // aPosition (location 0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, STRIDE, 0)
        // aPointSize (location 1)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 1, GLES30.GL_FLOAT, false, STRIDE, 3 * 4)
        // aType (location 2)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, STRIDE, 4 * 4)
        GLES30.glBindVertexArray(0)

        startTimeMs = System.currentTimeMillis()
    }

    fun draw(viewMatrix: FloatArray, projectionMatrix: FloatArray) {
        if (programId == 0) return

        // Upload pending event data if available
        pendingEvents.getAndSet(null)?.let { events ->
            uploadEvents(events)
        }

        if (vertexCount == 0) return

        val mvpMatrix = FloatArray(16)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        val elapsed = (System.currentTimeMillis() - startTimeMs) / 1000.0f

        GLES30.glUseProgram(programId)
        GLES30.glUniformMatrix4fv(uMVPLoc, 1, false, mvpMatrix, 0)
        GLES30.glUniform1f(uTimeLoc, elapsed)

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, vertexCount)
        GLES30.glBindVertexArray(0)

        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun uploadEvents(events: List<EarthEventsProvider.Event>) {
        if (events.isEmpty()) {
            vertexCount = 0
            return
        }

        val data = FloatArray(events.size * FLOATS_PER_VERTEX)
        for ((i, event) in events.withIndex()) {
            val latRad = Math.toRadians(event.lat)
            val lonRad = Math.toRadians(event.lon)

            val cosLat = Math.cos(latRad).toFloat()
            val sinLat = Math.sin(latRad).toFloat()
            val cosLon = Math.cos(lonRad).toFloat()
            val sinLon = Math.sin(lonRad).toFloat()

            val base = i * FLOATS_PER_VERTEX
            data[base + 0] = -PIN_RADIUS * cosLat * cosLon  // x (-X = Greenwich)
            data[base + 1] = PIN_RADIUS * sinLat             // y (+Y = North)
            data[base + 2] = PIN_RADIUS * cosLat * sinLon    // z (+Z = 90E)

            // Point size scales with magnitude: M4.5 -> 12px, M7+ -> 32px
            data[base + 3] = (8f + (event.magnitude - 4f) * 8f).coerceIn(10f, 36f)

            // Type: 0 = earthquake, 1 = volcano
            data[base + 4] = if (event.type == EarthEventsProvider.Event.Type.VOLCANO) 1f else 0f
        }

        val buffer = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(data); position(0) }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, buffer, GLES30.GL_STATIC_DRAW)

        vertexCount = events.size
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
            throw RuntimeException("EarthEvents shader compile failed: $log")
        }
        return shader
    }

    private val VERTEX_SHADER = """
        #version 300 es
        precision highp float;

        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in float aPointSize;
        layout(location = 2) in float aType;

        uniform mat4 uMVP;
        uniform float uTime;

        out float vType;
        out float vPulse;

        void main() {
            gl_Position = uMVP * vec4(aPosition, 1.0);

            // Pulse animation: gentle size oscillation
            float pulse = 1.0 + 0.2 * sin(uTime * 3.0 + aPosition.x * 10.0);
            gl_PointSize = aPointSize * pulse;

            vType = aType;
            vPulse = pulse;
        }
    """.trimIndent()

    private val FRAGMENT_SHADER = """
        #version 300 es
        precision mediump float;

        in float vType;
        in float vPulse;

        out vec4 fragColor;

        void main() {
            vec2 coord = gl_PointCoord - vec2(0.5);
            float dist = length(coord) * 2.0;
            if (dist > 1.0) discard;

            // Soft circle with bright center
            float alpha = smoothstep(1.0, 0.2, dist);

            // Earthquake: orange-red, Volcano: bright yellow
            vec3 color;
            if (vType < 0.5) {
                color = mix(vec3(1.0, 0.3, 0.1), vec3(1.0, 0.7, 0.2), dist);
            } else {
                color = mix(vec3(1.0, 0.9, 0.2), vec3(1.0, 0.6, 0.1), dist);
            }

            fragColor = vec4(color, alpha * 0.85);
        }
    """.trimIndent()
}
