package com.globe.app.earth

import android.opengl.GLES30
import android.util.Log

/**
 * Compiles and links the GLSL shaders used for Earth rendering.
 *
 * The fragment shader blends between a daytime texture and a nighttime (city lights)
 * texture based on diffuse lighting from a directional sun, with a smooth terminator
 * transition and a fresnel-based atmosphere rim glow.
 */
class EarthShader {

    var program: Int = 0
        private set

    // Uniform locations (resolved once after linking)
    var uModelLoc: Int = -1; private set
    var uViewLoc: Int = -1; private set
    var uProjectionLoc: Int = -1; private set
    var uSunDirectionLoc: Int = -1; private set
    var uCameraPositionLoc: Int = -1; private set
    var uDayTextureLoc: Int = -1; private set
    var uNightTextureLoc: Int = -1; private set
    var uCloudTextureLoc: Int = -1; private set
    var uCloudRotationLoc: Int = -1; private set
    var uCloudOpacityLoc: Int = -1; private set
    var uShowTerminatorLoc: Int = -1; private set
    var uShowAuroraLoc: Int = -1; private set
    var uTimeLoc: Int = -1; private set

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** Compile, link, and resolve all uniform locations. */
    fun create() {
        val vertShader = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SOURCE)
        val fragShader = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE)
        program = linkProgram(vertShader, fragShader)

        // Shaders can be detached after linking
        GLES30.glDeleteShader(vertShader)
        GLES30.glDeleteShader(fragShader)

        resolveUniforms()
    }

    fun use() {
        GLES30.glUseProgram(program)
    }

    fun release() {
        if (program != 0) {
            GLES30.glDeleteProgram(program)
            program = 0
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun resolveUniforms() {
        uModelLoc = GLES30.glGetUniformLocation(program, "uModel")
        uViewLoc = GLES30.glGetUniformLocation(program, "uView")
        uProjectionLoc = GLES30.glGetUniformLocation(program, "uProjection")
        uSunDirectionLoc = GLES30.glGetUniformLocation(program, "uSunDirection")
        uCameraPositionLoc = GLES30.glGetUniformLocation(program, "uCameraPosition")
        uDayTextureLoc = GLES30.glGetUniformLocation(program, "uDayTexture")
        uNightTextureLoc = GLES30.glGetUniformLocation(program, "uNightTexture")
        uCloudTextureLoc = GLES30.glGetUniformLocation(program, "uCloudTexture")
        uCloudRotationLoc = GLES30.glGetUniformLocation(program, "uCloudRotation")
        uCloudOpacityLoc = GLES30.glGetUniformLocation(program, "uCloudOpacity")
        uShowTerminatorLoc = GLES30.glGetUniformLocation(program, "uShowTerminator")
        uShowAuroraLoc = GLES30.glGetUniformLocation(program, "uShowAurora")
        uTimeLoc = GLES30.glGetUniformLocation(program, "uTime")
    }

    // ------------------------------------------------------------------
    // Shader compilation helpers
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
            val typeName = if (type == GLES30.GL_VERTEX_SHADER) "vertex" else "fragment"
            throw RuntimeException("Failed to compile $typeName shader:\n$log")
        }
        return shader
    }

    private fun linkProgram(vertShader: Int, fragShader: Int): Int {
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vertShader)
        GLES30.glAttachShader(prog, fragShader)
        GLES30.glLinkProgram(prog)

        val status = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(prog)
            GLES30.glDeleteProgram(prog)
            throw RuntimeException("Failed to link shader program:\n$log")
        }
        return prog
    }

    // ------------------------------------------------------------------
    // GLSL sources
    // ------------------------------------------------------------------

    companion object {
        private const val TAG = "EarthShader"

        /**
         * Vertex shader.
         *
         * Transforms vertices by Model-View-Projection and passes world-space
         * position, normal, and texture coordinates to the fragment stage.
         */
        const val VERTEX_SOURCE = """#version 300 es
precision highp float;

layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aTexCoord;

uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProjection;

out vec3 vWorldPosition;
out vec3 vWorldNormal;
out vec2 vTexCoord;

void main() {
    vec4 worldPos = uModel * vec4(aPosition, 1.0);
    vWorldPosition = worldPos.xyz;

    // Transform normal by the model matrix (assumes uniform scale; for
    // non-uniform scale, use the inverse-transpose instead).
    vWorldNormal = normalize(mat3(uModel) * aNormal);

    vTexCoord = aTexCoord;
    gl_Position = uProjection * uView * worldPos;
}
"""

        /**
         * Fragment shader.
         *
         * - Samples day and night textures.
         * - Computes diffuse factor from a directional sun.
         * - Smoothly blends between day and night across the terminator.
         * - Adds a fresnel-like atmosphere rim glow.
         */
        const val FRAGMENT_SOURCE = """#version 300 es
precision highp float;

in vec3 vWorldPosition;
in vec3 vWorldNormal;
in vec2 vTexCoord;

uniform vec3 uSunDirection;   // normalised, pointing *toward* the sun
uniform vec3 uCameraPosition;
uniform sampler2D uDayTexture;
uniform sampler2D uNightTexture;
uniform sampler2D uCloudTexture;
uniform float uCloudRotation; // slow horizontal offset for cloud drift
uniform float uCloudOpacity;  // overall cloud layer opacity (0.0–1.0)
uniform float uShowTerminator; // 1.0 = draw terminator line, 0.0 = off
uniform float uShowAurora;    // 1.0 = draw aurora zones, 0.0 = off
uniform float uTime;          // elapsed seconds for aurora animation

out vec4 fragColor;

void main() {
    vec3 normal = normalize(vWorldNormal);
    vec3 sunDir = normalize(uSunDirection);

    // ---- Day / night lighting ------------------------------------------
    float NdotL = dot(normal, sunDir);

    // Smooth terminator: map the [-0.15, 0.15] band around the day/night
    // boundary to a [0, 1] blend factor.  Outside that band the value
    // saturates to 0 or 1, giving a gentle transition.
    float dayFactor = smoothstep(-0.15, 0.15, NdotL);

    // The mesh maps u=0 to the -X axis (Greenwich in the sun calculation),
    // but the standard equirectangular texture has Greenwich at u=0.5.
    // Shift by 0.5 so the texture geography aligns with the lighting.
    vec2 geoCoord = vec2(0.5 - vTexCoord.x, vTexCoord.y);

    vec3 dayColor   = texture(uDayTexture,   geoCoord).rgb;
    vec3 nightColor = texture(uNightTexture, geoCoord).rgb;

    // Night side: show city lights at full brightness.
    // Day side:   lit by diffuse sunlight (clamped to avoid harsh shadows).
    float diffuse = max(NdotL, 0.0);
    vec3 litDay   = dayColor * (0.1 + 0.9 * diffuse);   // subtle ambient

    vec3 surface = mix(nightColor, litDay, dayFactor);

    // ---- Cloud layer ---------------------------------------------------
    vec2 cloudUV = vec2(vTexCoord.x + uCloudRotation, vTexCoord.y);
    float cloudAlpha = texture(uCloudTexture, cloudUV).r * uCloudOpacity;

    // Clouds are white, lit by the sun on the day side, faintly visible at night
    float cloudBrightness = mix(0.06, 0.1 + 0.9 * diffuse, dayFactor);
    vec3 cloudColor = vec3(cloudBrightness);

    // Clouds also obscure city lights underneath
    surface = mix(surface, cloudColor, cloudAlpha);

    // ---- Atmosphere rim glow (fresnel) ---------------------------------
    vec3 viewDir = normalize(uCameraPosition - vWorldPosition);
    float fresnel = 1.0 - max(dot(normal, viewDir), 0.0);
    fresnel = pow(fresnel, 3.0);

    // Tint the glow blue; stronger on the dayside.
    vec3 atmosphereColor = vec3(0.3, 0.6, 1.0);
    float atmosphereIntensity = 0.45 * fresnel * (0.4 + 0.6 * dayFactor);

    vec3 finalColor = surface + atmosphereColor * atmosphereIntensity;

    // ---- Terminator line ------------------------------------------------
    if (uShowTerminator > 0.5) {
        // Thin glowing line at the day/night boundary (NdotL ≈ 0)
        float terminator = 1.0 - smoothstep(0.0, 0.018, abs(NdotL));
        vec3 terminatorColor = vec3(1.0, 0.6, 0.15); // warm amber
        finalColor = mix(finalColor, terminatorColor, terminator * 0.8);
    }

    // ---- Aurora zones ---------------------------------------------------
    if (uShowAurora > 0.5) {
        // Geomagnetic pole directions (tilted ~11° from geographic poles)
        // North: ~80.7°N, 72.8°W  South: ~80.7°S, 107.2°E
        vec3 geomagNorth = normalize(vec3(-0.057, 0.986, -0.155));
        vec3 geomagSouth = -geomagNorth;

        // Geomagnetic latitude: angle from the geomagnetic equatorial plane
        float geomagLatN = dot(normal, geomagNorth);
        float geomagLatS = dot(normal, geomagSouth);

        // Aurora oval: band at ~65-75° geomagnetic latitude
        // dot = cos(colatitude) = sin(latitude)
        // sin(65°) ≈ 0.906, sin(75°) ≈ 0.966
        float auroraN = smoothstep(0.86, 0.92, geomagLatN) * (1.0 - smoothstep(0.95, 0.98, geomagLatN));
        float auroraS = smoothstep(0.86, 0.92, geomagLatS) * (1.0 - smoothstep(0.95, 0.98, geomagLatS));
        float auroraBase = max(auroraN, auroraS);

        // Only visible on the night side
        float nightMask = 1.0 - smoothstep(-0.1, 0.1, NdotL);
        auroraBase *= nightMask;

        if (auroraBase > 0.001) {
            // Animated shimmer using sine waves at different frequencies
            float lon = atan(normal.z, -normal.x); // approximate longitude
            float shimmer = 0.5 + 0.5 * sin(lon * 8.0 + uTime * 1.5);
            shimmer *= 0.6 + 0.4 * sin(lon * 13.0 - uTime * 2.3);
            shimmer = 0.3 + 0.7 * shimmer; // keep minimum brightness

            float aurora = auroraBase * shimmer;

            // Color: green dominant, with hints of purple at edges
            float edgeFactor = max(auroraN, auroraS);
            vec3 auroraColor = mix(
                vec3(0.1, 0.8, 0.3),   // green core
                vec3(0.4, 0.1, 0.6),   // purple edge
                smoothstep(0.90, 0.97, edgeFactor)
            );

            finalColor += auroraColor * aurora * 0.6;
        }
    }

    fragColor = vec4(finalColor, 1.0);
}
"""
    }
}
