package com.globe.app.render

import android.opengl.GLES30
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/** Equator, tropics, and polar circles for held-time seasons lessons. */
class LatitudeGuidesRenderer {
    private var program = 0
    private var vao = 0
    private var vbo = 0
    private val vertices = buildList {
        for (latDeg in listOf(-66.56, -23.44, 0.0, 23.44, 66.56)) {
            val lat = Math.toRadians(latDeg)
            for (lonDeg in -180 until 180) {
                for (v in listOf(lonDeg.toDouble(), (lonDeg + 1).toDouble())) {
                    val lon = Math.toRadians(v)
                    add((-1.018 * cos(lat) * cos(lon)).toFloat())
                    add((1.018 * sin(lat)).toFloat())
                    add((1.018 * cos(lat) * sin(lon)).toFloat())
                }
            }
        }
    }.toFloatArray()

    fun init() {
        val vert = compile(GLES30.GL_VERTEX_SHADER, """
            #version 300 es
            layout(location=0) in vec3 position;
            uniform mat4 mvp;
            void main() { gl_Position=mvp*vec4(position,1.0); }
        """.trimIndent())
        val frag = compile(GLES30.GL_FRAGMENT_SHADER, """
            #version 300 es
            precision mediump float;
            out vec4 color;
            void main() { color=vec4(0.40,0.92,1.0,0.60); }
        """.trimIndent())
        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vert); GLES30.glAttachShader(program, frag)
        GLES30.glLinkProgram(program)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        check(status[0] != 0) { GLES30.glGetProgramInfoLog(program) }
        GLES30.glDeleteShader(vert); GLES30.glDeleteShader(frag)
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0); vao = ids[0]
        GLES30.glGenBuffers(1, ids, 0); vbo = ids[0]
        GLES30.glBindVertexArray(vao); GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        val data = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        data.put(vertices).position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, data, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindVertexArray(0)
    }

    fun draw(view: FloatArray, projection: FloatArray) {
        if (program == 0) return
        val mvp = FloatArray(16)
        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "mvp"), 1, false, mvp, 0)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, vertices.size / 3)
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

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source); GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { GLES30.glGetShaderInfoLog(shader) }
        return shader
    }
}
