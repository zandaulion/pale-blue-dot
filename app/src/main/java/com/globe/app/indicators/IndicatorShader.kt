package com.globe.app.indicators

import android.opengl.GLES30

/**
 * Simple 2D shader for rendering directional indicator arrows as an overlay.
 */
object IndicatorShader {

    var programId: Int = 0; private set
    var uTransformLoc: Int = -1; private set
    var uColorLoc: Int = -1; private set

    private const val VERTEX_SOURCE = """#version 300 es
precision highp float;

layout(location = 0) in vec2 aPosition;

uniform mat4 uTransform;

void main() {
    gl_Position = uTransform * vec4(aPosition, 0.0, 1.0);
}
"""

    private const val FRAGMENT_SOURCE = """#version 300 es
precision mediump float;

uniform vec4 uColor;
out vec4 fragColor;

void main() {
    fragColor = uColor;
}
"""

    fun init() {
        val vert = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SOURCE)
        val frag = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE)

        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vert)
        GLES30.glAttachShader(programId, frag)
        GLES30.glLinkProgram(programId)

        GLES30.glDeleteShader(vert)
        GLES30.glDeleteShader(frag)

        uTransformLoc = GLES30.glGetUniformLocation(programId, "uTransform")
        uColorLoc = GLES30.glGetUniformLocation(programId, "uColor")
    }

    fun destroy() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        return shader
    }
}
