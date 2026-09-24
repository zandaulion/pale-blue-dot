package com.globe.app.places

import android.opengl.GLES30
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

/** Cyan outlined pins remain visually distinct from live observation markers. */
class PlacePinsRenderer {
    private var program = 0
    private var vao = 0
    private var vbo = 0
    private var count = 0
    private val pending = AtomicReference<List<SavedPlace>?>(null)
    @Volatile private var places = emptyList<SavedPlace>()

    fun setPlaces(value: List<SavedPlace>) { places = value.toList(); pending.set(places) }

    fun init() {
        val vertex = shader(GLES30.GL_VERTEX_SHADER, """
            #version 300 es
            layout(location=0) in vec3 aPosition;
            uniform mat4 uMvp;
            void main() { gl_Position = uMvp * vec4(aPosition,1.0); gl_PointSize=19.0; }
        """.trimIndent())
        val fragment = shader(GLES30.GL_FRAGMENT_SHADER, """
            #version 300 es
            precision mediump float;
            out vec4 color;
            void main() {
                float r=length(gl_PointCoord-vec2(0.5));
                if(r>0.48) discard;
                color = r>0.30 ? vec4(0.1,0.92,1.0,1.0) : vec4(0.03,0.14,0.22,0.95);
            }
        """.trimIndent())
        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertex); GLES30.glAttachShader(program, fragment)
        GLES30.glLinkProgram(program)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        check(status[0] != 0) { GLES30.glGetProgramInfoLog(program) }
        GLES30.glDeleteShader(vertex); GLES30.glDeleteShader(fragment)
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0); vao = ids[0]
        GLES30.glGenBuffers(1, ids, 0); vbo = ids[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindVertexArray(0)
        pending.set(places)
    }

    fun draw(view: FloatArray, projection: FloatArray) {
        if (program == 0) return
        pending.getAndSet(null)?.let { list ->
            val points = FloatArray(list.size * 3)
            list.forEachIndexed { i, p ->
                val lat = Math.toRadians(p.lat); val lon = Math.toRadians(p.lon)
                points[i * 3] = (-1.014 * kotlin.math.cos(lat) * kotlin.math.cos(lon)).toFloat()
                points[i * 3 + 1] = (1.014 * kotlin.math.sin(lat)).toFloat()
                points[i * 3 + 2] = (1.014 * kotlin.math.cos(lat) * kotlin.math.sin(lon)).toFloat()
            }
            val buffer = ByteBuffer.allocateDirect(points.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            buffer.put(points).position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, points.size * 4, buffer, GLES30.GL_STATIC_DRAW)
            count = list.size
        }
        if (count == 0) return
        val mvp = FloatArray(16)
        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uMvp"), 1, false, mvp, 0)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, count)
        GLES30.glBindVertexArray(0)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    fun destroy() {
        if (vbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
        if (vao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        if (program != 0) GLES30.glDeleteProgram(program)
        vbo = 0; vao = 0; program = 0
    }

    private fun shader(type: Int, source: String): Int {
        val id = GLES30.glCreateShader(type)
        GLES30.glShaderSource(id, source); GLES30.glCompileShader(id)
        val status = IntArray(1)
        GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { GLES30.glGetShaderInfoLog(id) }
        return id
    }
}
