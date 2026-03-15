package com.globe.app.stars

import android.opengl.GLES30
import android.util.Log

/**
 * Compiles and links the GLSL shaders used for starfield rendering.
 *
 * Vertex shader:
 *   - Transforms star positions by a view-projection matrix
 *     (no model matrix — stars sit at infinity).
 *   - Sets gl_PointSize from the per-vertex magnitude attribute,
 *     modulated by a subtle twinkle driven by a time uniform.
 *
 * Fragment shader:
 *   - Renders each point-sprite as a soft circular glow using
 *     distance from gl_PointCoord centre.
 *   - Applies the per-star color passed from the vertex stage.
 *   - Incorporates the twinkle brightness variation.
 */
object StarsShader {

    private const val TAG = "StarsShader"

    // ---------------------------------------------------------------
    // Uniform / attribute locations (populated after link)
    // ---------------------------------------------------------------
    var programId: Int = 0; private set

    var uVPMatrixLoc: Int = -1; private set
    var uTimeLoc: Int = -1; private set

    var aPositionLoc: Int = 0; private set
    var aSizeLoc: Int = 1; private set
    var aColorLoc: Int = 2; private set

    // ---------------------------------------------------------------
    // GLSL source
    // ---------------------------------------------------------------

    private val VERTEX_SHADER = """
        #version 300 es
        precision highp float;

        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in float aSize;
        layout(location = 2) in vec4 aColor;

        uniform mat4 uVPMatrix;
        uniform float uTime;

        out vec4 vColor;
        out float vTwinkle;

        // Simple pseudo-random hash from a vec3 seed
        float hash(vec3 p) {
            p = fract(p * vec3(443.897, 441.423, 437.195));
            p += dot(p, p.yzx + 19.19);
            return fract((p.x + p.y) * p.z);
        }

        void main() {
            gl_Position = uVPMatrix * vec4(aPosition, 1.0);

            // Each star gets a unique twinkle phase and speed from its position
            float starSeed = hash(aPosition);
            float speed = 0.5 + starSeed * 1.5;          // 0.5 – 2.0 Hz
            float phase = starSeed * 6.2831853;            // 0 – 2pi
            // Twinkle oscillates brightness between ~0.6 and 1.0
            vTwinkle = 0.80 + 0.20 * sin(uTime * speed + phase);

            // Point size: base magnitude scaled slightly by twinkle
            gl_PointSize = aSize * (0.9 + 0.1 * vTwinkle);

            vColor = aColor;
        }
    """.trimIndent()

    private val FRAGMENT_SHADER = """
        #version 300 es
        precision mediump float;

        in vec4 vColor;
        in float vTwinkle;

        out vec4 fragColor;

        void main() {
            // Distance from point-sprite centre (0..~0.707 corner)
            vec2 coord = gl_PointCoord - vec2(0.5);
            float dist = length(coord) * 2.0; // normalise so edge = 1

            // Discard pixels outside the circle
            if (dist > 1.0) discard;

            // Smooth circular falloff — bright core, soft halo
            float coreBrightness = exp(-dist * dist * 4.0);  // tight core
            float haloBrightness = exp(-dist * dist * 2.0) * 0.6; // wider halo
            float brightness = coreBrightness + haloBrightness;

            // Apply star color, twinkle, and overall alpha
            vec3 color = vColor.rgb * brightness * vTwinkle;
            float alpha = brightness * vTwinkle;

            fragColor = vec4(color, alpha);
        }
    """.trimIndent()

    // ---------------------------------------------------------------
    // Compilation helpers
    // ---------------------------------------------------------------

    /** Compile shaders, link program, and resolve uniform/attrib locations. */
    fun init() {
        val vertId = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragId = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        programId = GLES30.glCreateProgram().also { prog ->
            GLES30.glAttachShader(prog, vertId)
            GLES30.glAttachShader(prog, fragId)
            GLES30.glLinkProgram(prog)

            val status = IntArray(1)
            GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                val info = GLES30.glGetProgramInfoLog(prog)
                GLES30.glDeleteProgram(prog)
                throw RuntimeException("Stars program link failed: $info")
            }
        }

        // Clean up individual shader objects — they're linked into the program
        GLES30.glDeleteShader(vertId)
        GLES30.glDeleteShader(fragId)

        // Resolve locations
        uVPMatrixLoc = GLES30.glGetUniformLocation(programId, "uVPMatrix")
        uTimeLoc = GLES30.glGetUniformLocation(programId, "uTime")

        aPositionLoc = GLES30.glGetAttribLocation(programId, "aPosition").let { if (it < 0) 0 else it }
        aSizeLoc = GLES30.glGetAttribLocation(programId, "aSize").let { if (it < 0) 1 else it }
        aColorLoc = GLES30.glGetAttribLocation(programId, "aColor").let { if (it < 0) 2 else it }

        Log.d(TAG, "Stars shader compiled — program=$programId")
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val info = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            val typeStr = if (type == GLES30.GL_VERTEX_SHADER) "vertex" else "fragment"
            throw RuntimeException("Stars $typeStr shader compile failed: $info")
        }
        return shader
    }

    /** Delete the program when no longer needed. */
    fun destroy() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }
}
