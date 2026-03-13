package com.globe.app.sun

import android.opengl.GLES30

/**
 * Shader for rendering the Sun as a camera-facing billboard with a bright
 * core and soft corona glow.
 */
object SunShader {

    var programId: Int = 0; private set
    var uMVPLoc: Int = -1; private set

    const val VERTEX_SOURCE = """#version 300 es
precision highp float;

layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aTexCoord;

uniform mat4 uMVP;

out vec2 vUV;

void main() {
    vUV = aTexCoord;
    gl_Position = uMVP * vec4(aPosition, 1.0);
}
"""

    const val FRAGMENT_SOURCE = """#version 300 es
precision highp float;

in vec2 vUV;

out vec4 fragColor;

void main() {
    // Distance from center of the quad (0 at center, 1 at edge)
    vec2 centered = vUV * 2.0 - 1.0;
    float dist = length(centered);

    // Bright white-yellow core
    float core = exp(-dist * dist * 18.0);

    // Warm glow / corona
    float glow = 0.4 * exp(-dist * dist * 3.0);

    // Outer halo
    float halo = 0.08 * exp(-dist * dist * 0.8);

    float brightness = core + glow + halo;

    // Sun color: hot white core fading to warm yellow/orange
    vec3 coreColor = vec3(1.0, 1.0, 0.95);
    vec3 glowColor = vec3(1.0, 0.85, 0.4);
    vec3 haloColor = vec3(1.0, 0.7, 0.3);

    vec3 color = coreColor * core + glowColor * glow + haloColor * halo;

    // Discard fully transparent pixels
    if (brightness < 0.005) discard;

    fragColor = vec4(color, brightness);
}
"""

    fun init() {
        val vert = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SOURCE)
        val frag = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE)

        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vert)
        GLES30.glAttachShader(programId, frag)
        GLES30.glLinkProgram(programId)

        val status = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(programId)
            GLES30.glDeleteProgram(programId)
            throw RuntimeException("Failed to link sun shader:\n$log")
        }

        GLES30.glDeleteShader(vert)
        GLES30.glDeleteShader(frag)

        uMVPLoc = GLES30.glGetUniformLocation(programId, "uMVP")
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
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Failed to compile sun shader:\n$log")
        }
        return shader
    }
}
