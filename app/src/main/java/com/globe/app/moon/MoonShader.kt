package com.globe.app.moon

import android.opengl.GLES30

/**
 * Shader for rendering the Moon as a lit sphere.
 * The phase is produced naturally by diffuse sun lighting.
 */
object MoonShader {

    var programId: Int = 0; private set
    var uMVPLoc: Int = -1; private set
    var uModelLoc: Int = -1; private set
    var uSunDirectionLoc: Int = -1; private set
    var uMoonTextureLoc: Int = -1; private set

    const val VERTEX_SOURCE = """#version 300 es
precision highp float;

layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aTexCoord;

uniform mat4 uMVP;
uniform mat4 uModel;

out vec3 vWorldNormal;
out vec2 vTexCoord;

void main() {
    vWorldNormal = normalize(mat3(uModel) * aNormal);
    vTexCoord = aTexCoord;
    gl_Position = uMVP * vec4(aPosition, 1.0);
}
"""

    const val FRAGMENT_SOURCE = """#version 300 es
precision highp float;

in vec3 vWorldNormal;
in vec2 vTexCoord;

uniform vec3 uSunDirection;
uniform sampler2D uMoonTexture;

out vec4 fragColor;

void main() {
    vec3 normal = normalize(vWorldNormal);
    vec3 sunDir = normalize(uSunDirection);

    float NdotL = dot(normal, sunDir);
    float diffuse = max(NdotL, 0.0);

    // Subtle ambient so the dark side isn't fully black
    float lighting = 0.03 + 0.97 * diffuse;

    vec3 baseColor = texture(uMoonTexture, vTexCoord).rgb;
    fragColor = vec4(baseColor * lighting, 1.0);
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
            throw RuntimeException("Failed to link moon shader:\n$log")
        }

        GLES30.glDeleteShader(vert)
        GLES30.glDeleteShader(frag)

        uMVPLoc = GLES30.glGetUniformLocation(programId, "uMVP")
        uModelLoc = GLES30.glGetUniformLocation(programId, "uModel")
        uSunDirectionLoc = GLES30.glGetUniformLocation(programId, "uSunDirection")
        uMoonTextureLoc = GLES30.glGetUniformLocation(programId, "uMoonTexture")
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
            throw RuntimeException("Failed to compile moon shader:\n$log")
        }
        return shader
    }
}
