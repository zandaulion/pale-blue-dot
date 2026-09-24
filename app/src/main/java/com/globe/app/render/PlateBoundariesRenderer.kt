package com.globe.app.render

import android.content.Context
import android.opengl.GLES30
import android.opengl.Matrix
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Offline PB2002 lines. Each context owns its own GL program and buffer. */
class PlateBoundariesRenderer(context: Context) {
    private val vertices = data(context.applicationContext)
    private var program = 0
    private var vao = 0
    private var vbo = 0

    fun init() {
        val vert = compile(GLES30.GL_VERTEX_SHADER, """
            #version 300 es
            layout(location=0) in vec3 position;
            uniform mat4 mvp;
            void main() { gl_Position = mvp * vec4(position, 1.0); }
        """.trimIndent())
        val frag = compile(GLES30.GL_FRAGMENT_SHADER, """
            #version 300 es
            precision mediump float;
            out vec4 color;
            void main() { color=vec4(1.0,0.79,0.36,0.86); }
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
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        val buffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(vertices).position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, buffer, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindVertexArray(0)
    }

    fun draw(view: FloatArray, projection: FloatArray) {
        if (program == 0 || vertices.isEmpty()) return
        val mvp = FloatArray(16)
        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "mvp"), 1, false, mvp, 0)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glLineWidth(2f)
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

    companion object {
        @Volatile private var cached: FloatArray? = null
        @Synchronized private fun data(context: Context): FloatArray {
            cached?.let { return it }
            val root = JSONObject(context.assets.open("plate_boundaries_v1.json").bufferedReader().use { it.readText() })
            val lines = root.getJSONArray("lines")
            val output = ArrayList<Float>(100_000)
            for (i in 0 until lines.length()) {
                val points = lines.getJSONArray(i)
                if (points.length() < 2) continue
                var previous = point(points.getJSONArray(0).getDouble(1), points.getJSONArray(0).getDouble(0))
                for (j in 1 until points.length()) {
                    val pair = points.getJSONArray(j)
                    val next = point(pair.getDouble(1), pair.getDouble(0))
                    val dot = (previous[0] * next[0] + previous[1] * next[1] + previous[2] * next[2]).coerceIn(-1.0, 1.0)
                    val angle = acos(dot)
                    val steps = ceil(Math.toDegrees(angle) / 1.0).toInt().coerceIn(1, 180)
                    var start = previous
                    for (step in 1..steps) {
                        val end = slerp(previous, next, step.toDouble() / steps)
                        append(output, start); append(output, end)
                        start = end
                    }
                    previous = next
                }
            }
            val result = FloatArray(output.size) { output[it] }
            cached = result
            return result
        }

        private fun point(latDeg: Double, lonDeg: Double): DoubleArray {
            val lat = Math.toRadians(latDeg); val lon = Math.toRadians(lonDeg)
            return doubleArrayOf(-cos(lat) * cos(lon), sin(lat), cos(lat) * sin(lon))
        }

        private fun slerp(a: DoubleArray, b: DoubleArray, t: Double): DoubleArray {
            // Normalized linear interpolation is stable for short PB2002 segments and dateline crossings.
            val x = a[0] * (1-t) + b[0] * t
            val y = a[1] * (1-t) + b[1] * t
            val z = a[2] * (1-t) + b[2] * t
            val n = sqrt(x*x + y*y + z*z).coerceAtLeast(1e-9)
            return doubleArrayOf(x/n, y/n, z/n)
        }

        private fun append(output: ArrayList<Float>, p: DoubleArray) {
            output.add((p[0] * 1.012).toFloat())
            output.add((p[1] * 1.012).toFloat())
            output.add((p[2] * 1.012).toFloat())
        }
    }
}
