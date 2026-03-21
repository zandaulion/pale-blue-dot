package com.globe.app.stars

import android.opengl.GLES30
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Renders constellation stick figures as lines connecting real star positions,
 * plus small point sprites at each constellation star.
 *
 * Star positions use J2000 RA/Dec converted to the same coordinate system
 * and sphere radius (500) as the procedural starfield.
 */
class ConstellationRenderer {

    companion object {
        private const val R = 500f // must match StarsModel.SPHERE_RADIUS
    }

    @Volatile var visible = true

    private var lineProgramId = 0
    private var lineVao = 0
    private var lineVbo = 0
    private var lineVertexCount = 0

    private var starVao = 0
    private var starVbo = 0
    private var starVertexCount = 0

    private var uVPLoc = -1
    private var uColorLoc = -1
    private var uAlphaLoc = -1
    private var uPointSizeLoc = -1
    private var uIsPointLoc = -1

    // Scratch matrix
    private val vpMatrix = FloatArray(16)

    fun init() {
        buildShader()

        val (starPositions, linePositions) = buildGeometry()

        // Star points VBO/VAO
        val vaos = IntArray(2)
        GLES30.glGenVertexArrays(2, vaos, 0)
        starVao = vaos[0]
        lineVao = vaos[1]

        val vbos = IntArray(2)
        GLES30.glGenBuffers(2, vbos, 0)
        starVbo = vbos[0]
        lineVbo = vbos[1]

        // Star points
        GLES30.glBindVertexArray(starVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, starVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, starPositions.capacity() * 4, starPositions, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)
        starVertexCount = starPositions.capacity() / 3

        // Constellation lines
        GLES30.glBindVertexArray(lineVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, lineVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, linePositions.capacity() * 4, linePositions, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)
        lineVertexCount = linePositions.capacity() / 3

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun draw(viewMatrix: FloatArray, projectionMatrix: FloatArray) {
        if (!visible || lineProgramId == 0) return

        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)

        GLES30.glUseProgram(lineProgramId)
        GLES30.glUniformMatrix4fv(uVPLoc, 1, false, vpMatrix, 0)

        // Draw constellation lines
        GLES30.glUniform3f(uColorLoc, 0.6f, 0.8f, 1.0f) // bright blue
        GLES30.glUniform1f(uAlphaLoc, 0.5f)
        GLES30.glUniform1f(uIsPointLoc, 0f)
        GLES30.glUniform1f(uPointSizeLoc, 1f)
        GLES30.glLineWidth(2.5f)

        GLES30.glBindVertexArray(lineVao)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, lineVertexCount)
        GLES30.glBindVertexArray(0)

        // Draw constellation star markers
        GLES30.glUniform3f(uColorLoc, 1.0f, 1.0f, 1.0f) // white
        GLES30.glUniform1f(uAlphaLoc, 1.0f)
        GLES30.glUniform1f(uIsPointLoc, 1f)
        GLES30.glUniform1f(uPointSizeLoc, 7f)

        GLES30.glBindVertexArray(starVao)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, starVertexCount)
        GLES30.glBindVertexArray(0)

        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    fun destroy() {
        if (lineProgramId != 0) { GLES30.glDeleteProgram(lineProgramId); lineProgramId = 0 }
        if (starVao != 0) { GLES30.glDeleteVertexArrays(1, intArrayOf(starVao), 0); starVao = 0 }
        if (lineVao != 0) { GLES30.glDeleteVertexArrays(1, intArrayOf(lineVao), 0); lineVao = 0 }
        if (starVbo != 0) { GLES30.glDeleteBuffers(1, intArrayOf(starVbo), 0); starVbo = 0 }
        if (lineVbo != 0) { GLES30.glDeleteBuffers(1, intArrayOf(lineVbo), 0); lineVbo = 0 }
    }

    // ------------------------------------------------------------------
    // Shader
    // ------------------------------------------------------------------

    private fun buildShader() {
        val vertSrc = """
            #version 300 es
            precision highp float;
            layout(location = 0) in vec3 aPosition;
            uniform mat4 uVP;
            uniform float uPointSize;
            void main() {
                gl_Position = uVP * vec4(aPosition, 1.0);
                gl_PointSize = uPointSize;
            }
        """.trimIndent()

        val fragSrc = """
            #version 300 es
            precision mediump float;
            uniform vec3 uColor;
            uniform float uAlpha;
            uniform float uIsPoint;
            out vec4 fragColor;
            void main() {
                float a = uAlpha;
                if (uIsPoint > 0.5) {
                    vec2 c = gl_PointCoord - vec2(0.5);
                    float d = length(c) * 2.0;
                    if (d > 1.0) discard;
                    a *= 1.0 - d * d;
                }
                fragColor = vec4(uColor, a);
            }
        """.trimIndent()

        val vertId = compileShader(GLES30.GL_VERTEX_SHADER, vertSrc)
        val fragId = compileShader(GLES30.GL_FRAGMENT_SHADER, fragSrc)
        lineProgramId = GLES30.glCreateProgram()
        GLES30.glAttachShader(lineProgramId, vertId)
        GLES30.glAttachShader(lineProgramId, fragId)
        GLES30.glLinkProgram(lineProgramId)
        val status = IntArray(1)
        GLES30.glGetProgramiv(lineProgramId, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(lineProgramId)
            GLES30.glDeleteProgram(lineProgramId)
            throw RuntimeException("Constellation shader link failed: $log")
        }
        GLES30.glDeleteShader(vertId)
        GLES30.glDeleteShader(fragId)

        uVPLoc = GLES30.glGetUniformLocation(lineProgramId, "uVP")
        uColorLoc = GLES30.glGetUniformLocation(lineProgramId, "uColor")
        uAlphaLoc = GLES30.glGetUniformLocation(lineProgramId, "uAlpha")
        uPointSizeLoc = GLES30.glGetUniformLocation(lineProgramId, "uPointSize")
        uIsPointLoc = GLES30.glGetUniformLocation(lineProgramId, "uIsPoint")
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
            throw RuntimeException("Constellation shader compile failed: $log")
        }
        return shader
    }

    // ------------------------------------------------------------------
    // Geometry generation from real star data
    // ------------------------------------------------------------------

    /**
     * Converts Right Ascension (decimal hours) and Declination (decimal degrees)
     * to a 3D position on the star sphere.
     */
    private fun raDecToXYZ(raHours: Float, decDeg: Float): FloatArray {
        val ra = raHours * (PI / 12.0)
        val dec = decDeg * (PI / 180.0)
        return floatArrayOf(
            (R * cos(dec) * cos(ra)).toFloat(),
            (R * sin(dec)).toFloat(),
            (R * cos(dec) * sin(ra)).toFloat()
        )
    }

    private data class Constellation(
        val name: String,
        /** RA (hours), Dec (degrees) pairs, flattened */
        val stars: FloatArray,
        /** Index pairs for lines — each pair is (from, to) into the stars array */
        val lines: IntArray
    )

    private fun buildGeometry(): Pair<java.nio.FloatBuffer, java.nio.FloatBuffer> {
        val constellations = defineConstellations()

        // Collect unique star positions and line vertex positions
        val allStarCoords = mutableListOf<Float>()
        val allLineCoords = mutableListOf<Float>()

        for (c in constellations) {
            val starCount = c.stars.size / 2
            val positions = Array(starCount) { i ->
                raDecToXYZ(c.stars[i * 2], c.stars[i * 2 + 1])
            }

            // Star marker positions
            for (pos in positions) {
                allStarCoords.addAll(pos.toList())
            }

            // Line segment positions (pairs of vertices)
            for (i in c.lines.indices step 2) {
                val from = positions[c.lines[i]]
                val to = positions[c.lines[i + 1]]
                allStarCoords // not needed, lines below
                allLineCoords.addAll(from.toList())
                allLineCoords.addAll(to.toList())
            }
        }

        val starBuf = ByteBuffer.allocateDirect(allStarCoords.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (v in allStarCoords) starBuf.put(v)
        starBuf.position(0)

        val lineBuf = ByteBuffer.allocateDirect(allLineCoords.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (v in allLineCoords) lineBuf.put(v)
        lineBuf.position(0)

        return Pair(starBuf, lineBuf)
    }

    // ------------------------------------------------------------------
    // Constellation definitions — J2000 approximate RA/Dec
    // ------------------------------------------------------------------

    private fun defineConstellations(): List<Constellation> = listOf(
        // Ursa Major (Big Dipper asterism)
        Constellation("Ursa Major",
            floatArrayOf(
                11.06f, 61.75f,  // 0 Dubhe (α)
                11.03f, 56.38f,  // 1 Merak (β)
                11.90f, 53.69f,  // 2 Phecda (γ)
                12.26f, 57.03f,  // 3 Megrez (δ)
                12.90f, 55.96f,  // 4 Alioth (ε)
                13.40f, 54.93f,  // 5 Mizar (ζ)
                13.79f, 49.31f,  // 6 Alkaid (η)
            ),
            intArrayOf(0,1, 1,2, 2,3, 3,0, 3,4, 4,5, 5,6)
        ),

        // Ursa Minor (Little Dipper)
        Constellation("Ursa Minor",
            floatArrayOf(
                2.53f, 89.26f,   // 0 Polaris (α)
                14.85f, 74.16f,  // 1 Kochab (β)
                15.73f, 77.79f,  // 2 Pherkad (γ)
                15.35f, 71.83f,  // 3 Yildun (δ... actually ε)
                16.29f, 75.76f,  // 4 ζ
                17.54f, 86.59f,  // 5 δ UMi
                16.77f, 82.04f,  // 6 ε UMi
            ),
            intArrayOf(0,5, 5,6, 6,4, 4,2, 2,1, 1,3, 3,4)
        ),

        // Orion
        Constellation("Orion",
            floatArrayOf(
                5.92f, 7.41f,    // 0 Betelgeuse (α)
                5.24f, -8.20f,   // 1 Rigel (β)
                5.42f, 6.35f,    // 2 Bellatrix (γ)
                5.53f, -0.30f,   // 3 Mintaka (δ) belt
                5.60f, -1.20f,   // 4 Alnilam (ε) belt
                5.68f, -1.94f,   // 5 Alnitak (ζ) belt
                5.80f, -9.67f,   // 6 Saiph (κ)
            ),
            intArrayOf(0,2, 0,3, 2,3, 3,4, 4,5, 5,6, 1,5, 1,3)
        ),

        // Cassiopeia (W shape)
        Constellation("Cassiopeia",
            floatArrayOf(
                0.68f, 56.54f,   // 0 Schedar (α)
                0.15f, 59.15f,   // 1 Caph (β)
                0.95f, 60.72f,   // 2 Gamma Cas (γ)
                1.43f, 60.24f,   // 3 Ruchbah (δ)
                1.91f, 63.67f,   // 4 Segin (ε)
            ),
            intArrayOf(1,0, 0,2, 2,3, 3,4)
        ),

        // Cygnus (Northern Cross)
        Constellation("Cygnus",
            floatArrayOf(
                20.69f, 45.28f,  // 0 Deneb (α)
                19.51f, 27.96f,  // 1 Albireo (β)
                20.37f, 40.26f,  // 2 Sadr (γ)
                19.75f, 45.13f,  // 3 Gienah (ε)
                20.77f, 33.97f,  // 4 δ Cyg
            ),
            intArrayOf(0,2, 2,1, 3,2, 2,4)
        ),

        // Leo
        Constellation("Leo",
            floatArrayOf(
                10.14f, 11.97f,  // 0 Regulus (α)
                11.82f, 14.57f,  // 1 Denebola (β)
                10.33f, 19.84f,  // 2 Algieba (γ)
                11.24f, 20.52f,  // 3 Zosma (δ)
                9.76f, 23.77f,   // 4 μ Leo (Rasalas)
                9.88f, 26.01f,   // 5 ε Leo
                10.28f, 23.42f,  // 6 ζ Leo
            ),
            intArrayOf(0,2, 2,6, 6,3, 3,1, 2,5, 5,4)
        ),

        // Scorpius
        Constellation("Scorpius",
            floatArrayOf(
                16.49f, -26.43f, // 0 Antares (α)
                16.09f, -19.81f, // 1 Dschubba (δ)
                16.01f, -22.62f, // 2 π Sco
                15.98f, -26.11f, // 3 ρ Sco (actually σ)
                16.84f, -34.29f, // 4 ε Sco
                17.20f, -43.24f, // 5 λ Sco (Shaula)
                17.37f, -42.99f, // 6 υ Sco (Lesath)
                16.60f, -28.22f, // 7 τ Sco
                17.00f, -37.10f, // 8 η Sco
                16.87f, -38.05f, // 9 θ Sco
            ),
            intArrayOf(1,2, 2,3, 3,0, 0,7, 7,4, 4,9, 9,8, 8,5, 5,6)
        ),

        // Crux (Southern Cross)
        Constellation("Crux",
            floatArrayOf(
                12.44f, -63.10f, // 0 Acrux (α)
                12.80f, -59.69f, // 1 Mimosa (β)
                12.52f, -57.11f, // 2 Gacrux (γ)
                12.25f, -58.75f, // 3 δ Cru
            ),
            intArrayOf(0,2, 1,3)
        ),

        // Gemini
        Constellation("Gemini",
            floatArrayOf(
                7.58f, 31.89f,   // 0 Castor (α)
                7.76f, 28.03f,   // 1 Pollux (β)
                6.63f, 16.40f,   // 2 Alhena (γ)
                7.07f, 20.57f,   // 3 Mebsuta (ε)
                6.38f, 22.51f,   // 4 Tejat (μ)
                6.73f, 25.13f,   // 5 η Gem
            ),
            intArrayOf(0,1, 0,5, 5,4, 1,3, 3,2)
        ),

        // Taurus (V of the Hyades + Aldebaran)
        Constellation("Taurus",
            floatArrayOf(
                4.60f, 16.51f,   // 0 Aldebaran (α)
                5.44f, 28.61f,   // 1 Elnath (β)
                4.33f, 15.63f,   // 2 γ Tau
                4.48f, 15.96f,   // 3 δ Tau (Secunda Hyadum)
                4.47f, 19.18f,   // 4 ε Tau (Ain)
                5.63f, 21.14f,   // 5 ζ Tau
            ),
            intArrayOf(0,3, 3,2, 3,4, 4,1, 1,5)
        ),

        // Canis Major
        Constellation("Canis Major",
            floatArrayOf(
                6.75f, -16.72f,  // 0 Sirius (α)
                6.38f, -17.96f,  // 1 Mirzam (β)
                7.14f, -26.39f,  // 2 Wezen (δ)
                6.98f, -28.97f,  // 3 Aludra (η)
                7.06f, -23.83f,  // 4 σ CMa
                6.93f, -17.05f,  // 5 ο² CMa
            ),
            intArrayOf(0,1, 0,5, 5,4, 4,2, 2,3)
        ),

        // Lyra
        Constellation("Lyra",
            floatArrayOf(
                18.62f, 38.78f,  // 0 Vega (α)
                18.83f, 33.36f,  // 1 Sheliak (β)
                18.98f, 32.69f,  // 2 Sulafat (γ)
                18.91f, 36.90f,  // 3 δ² Lyr
                18.75f, 37.60f,  // 4 ζ Lyr
            ),
            intArrayOf(0,4, 4,1, 0,3, 3,2, 1,2)
        ),

        // Aquila
        Constellation("Aquila",
            floatArrayOf(
                19.85f, 8.87f,   // 0 Altair (α)
                19.77f, 10.61f,  // 1 Tarazed (γ)
                20.19f, -0.82f,  // 2 θ Aql
                19.09f, 13.86f,  // 3 ζ Aql
                19.42f, 3.11f,   // 4 δ Aql
                19.10f, -4.88f,  // 5 λ Aql
            ),
            intArrayOf(3,1, 1,0, 0,4, 4,2)
        ),

        // Pegasus (Great Square)
        Constellation("Pegasus",
            floatArrayOf(
                23.08f, 28.08f,  // 0 Markab (α)
                23.06f, 15.21f,  // 1 Homam (ζ)
                0.22f, 15.18f,   // 2 Algenib (γ)
                21.74f, 9.88f,   // 3 Enif (ε)
                22.72f, 30.22f,  // 4 Scheat (β)
                0.14f, 29.09f,   // 5 Alpheratz (α And — corner of square)
            ),
            intArrayOf(0,4, 4,5, 5,2, 2,0, 0,1, 4,3)
        ),

        // Centaurus (pointer stars)
        Constellation("Centaurus",
            floatArrayOf(
                14.66f, -60.84f, // 0 Alpha Centauri
                14.06f, -60.37f, // 1 Hadar (β)
                13.66f, -53.47f, // 2 ε Cen (Birdun)
                14.59f, -42.16f, // 3 θ Cen (Menkent)
                12.69f, -48.96f, // 4 γ Cen
                13.93f, -47.29f, // 5 η Cen
            ),
            intArrayOf(0,1, 1,2, 2,4, 1,5, 5,3)
        ),
    )
}
