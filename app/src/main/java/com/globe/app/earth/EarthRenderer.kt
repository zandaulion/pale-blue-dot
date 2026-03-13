package com.globe.app.earth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-level renderer that ties together the sphere mesh, shader programme,
 * sun position, and textures to draw a slowly-rotating Earth.
 *
 * Typical usage inside a [android.opengl.GLSurfaceView.Renderer]:
 *
 * ```
 * override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
 *     earthRenderer.onSurfaceCreated(context)
 * }
 * override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
 *     earthRenderer.onSurfaceChanged(width, height)
 * }
 * override fun onDrawFrame(gl: GL10?) {
 *     earthRenderer.onDrawFrame()
 * }
 * ```
 */
class EarthRenderer {

    companion object {
        private const val TAG = "EarthRenderer"
    }

    // GL resources
    private var gpuBuffers: EarthModel.GpuBuffers? = null
    private var shader: EarthShader? = null
    private var dayTextureId: Int = 0
    private var nightTextureId: Int = 0
    private var cloudTextureId: Int = 0

    // Cloud drift
    private var startTimeMs: Long = 0L

    // Matrices
    private val modelMatrix = FloatArray(16)

    // Externally provided matrices (set via setMatrices before each draw)
    private var viewMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private var projectionMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private var cameraPosition = floatArrayOf(0f, 0f, 3f)

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Initialise GPU resources. Call from `onSurfaceCreated`.
     *
     * @param context          Android context used for loading texture resources.
     * @param dayTextureResId  Optional drawable resource ID for the daytime Earth texture.
     *                         Pass 0 to use a generated placeholder.
     * @param nightTextureResId Optional drawable resource ID for the nighttime texture.
     *                          Pass 0 to use a generated placeholder.
     */
    fun onSurfaceCreated(
        context: Context,
        dayTextureResId: Int = 0,
        nightTextureResId: Int = 0
    ) {
        // --- Shader ---
        val s = EarthShader()
        s.create()
        shader = s

        // --- Mesh ---
        val model = EarthModel()
        gpuBuffers = model.uploadToGpu()

        // --- Textures ---
        dayTextureId = if (dayTextureResId != 0) {
            loadTexture(context, dayTextureResId)
        } else {
            createPlaceholderTexture(isDay = true)
        }

        nightTextureId = if (nightTextureResId != 0) {
            loadTexture(context, nightTextureResId)
        } else {
            createPlaceholderTexture(isDay = false)
        }

        // --- Cloud texture (always procedural) ---
        cloudTextureId = createCloudTexture()
        startTimeMs = System.currentTimeMillis()
    }

    /**
     * Set the view and projection matrices for the next draw call.
     * Called each frame by the parent renderer before [onDrawFrame].
     *
     * @param view       4x4 view matrix from the orbit camera.
     * @param projection 4x4 projection matrix.
     * @param camPos     Camera position in world space (for fresnel calculation).
     */
    fun setMatrices(view: FloatArray, projection: FloatArray, camPos: FloatArray) {
        viewMatrix = view
        projectionMatrix = projection
        cameraPosition = camPos
    }

    /**
     * Render one frame. Call from `onDrawFrame`.
     * The caller must have called [setMatrices] before this.
     * GL clear and viewport are the caller's responsibility.
     */
    fun onDrawFrame() {
        val s = shader ?: return
        val buffers = gpuBuffers ?: return

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)

        s.use()

        // ---- Model matrix: rotate Earth so correct longitude faces the sun ----
        Matrix.setIdentityM(modelMatrix, 0)
        // SunPosition.calculate() already returns the sun direction in the
        // Earth-fixed frame (accounting for GMST), so we do NOT rotate the
        // model matrix by time-of-day. The sun vector encodes the rotation.

        // ---- Uniforms ----
        GLES30.glUniformMatrix4fv(s.uModelLoc, 1, false, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(s.uViewLoc, 1, false, viewMatrix, 0)
        GLES30.glUniformMatrix4fv(s.uProjectionLoc, 1, false, projectionMatrix, 0)

        // Sun direction (points toward the sun in world space)
        val sunDir = SunPosition.calculate()
        GLES30.glUniform3f(s.uSunDirectionLoc, sunDir[0], sunDir[1], sunDir[2])

        // Camera position for fresnel calculation
        GLES30.glUniform3f(s.uCameraPositionLoc, cameraPosition[0], cameraPosition[1], cameraPosition[2])

        // Bind textures
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dayTextureId)
        GLES30.glUniform1i(s.uDayTextureLoc, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, nightTextureId)
        GLES30.glUniform1i(s.uNightTextureLoc, 1)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, cloudTextureId)
        GLES30.glUniform1i(s.uCloudTextureLoc, 2)

        // Slow cloud drift: full rotation in ~10 minutes
        val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000.0f
        GLES30.glUniform1f(s.uCloudRotationLoc, elapsedSec * 0.0017f)

        // ---- Draw ----
        GLES30.glBindVertexArray(buffers.vao)
        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES,
            buffers.indexCount,
            GLES30.GL_UNSIGNED_INT,
            0
        )
        GLES30.glBindVertexArray(0)
    }

    /** Release all GPU resources. */
    fun release() {
        gpuBuffers?.release()
        gpuBuffers = null

        shader?.release()
        shader = null

        val textures = intArrayOf(dayTextureId, nightTextureId, cloudTextureId)
        GLES30.glDeleteTextures(3, textures, 0)
        dayTextureId = 0
        nightTextureId = 0
        cloudTextureId = 0
    }

    // ------------------------------------------------------------------
    // Texture loading
    // ------------------------------------------------------------------

    /**
     * Loads a texture from a drawable resource.
     */
    private fun loadTexture(context: Context, resourceId: Int): Int {
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        val texId = textureIds[0]

        if (texId == 0) {
            Log.e(TAG, "glGenTextures failed")
            return 0
        }

        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)
            ?: run {
                Log.e(TAG, "Failed to decode resource $resourceId")
                GLES30.glDeleteTextures(1, textureIds, 0)
                return 0
            }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)

        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)

        bitmap.recycle()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        return texId
    }

    /**
     * Creates a small procedural placeholder texture so the app can run
     * before real texture assets are added.
     *
     * Day placeholder: blue/green Earth-ish tones.
     * Night placeholder: mostly black with faint orange dots (city lights).
     */
    private fun createPlaceholderTexture(isDay: Boolean): Int {
        val width = 256
        val height = 128
        val pixels = ByteBuffer.allocateDirect(width * height * 4)
            .order(ByteOrder.nativeOrder())

        for (y in 0 until height) {
            val v = y.toFloat() / height
            for (x in 0 until width) {
                val u = x.toFloat() / width

                val r: Int
                val g: Int
                val b: Int

                if (isDay) {
                    // Simple latitude-based colouring: poles = white, mid = green, equator = blue
                    val lat = Math.abs(v - 0.5f) * 2f   // 0 at equator, 1 at poles
                    if (lat > 0.85f) {
                        // Polar ice
                        r = 230; g = 235; b = 240
                    } else if (lat > 0.3f) {
                        // Land-ish green with longitude variation
                        val t = ((Math.sin((u * 12.0).toDouble()) * 0.5 + 0.5) * 0.3).toFloat()
                        r = (60 + (t * 40).toInt()).coerceIn(0, 255)
                        g = (120 + (t * 50).toInt()).coerceIn(0, 255)
                        b = (50 + (t * 20).toInt()).coerceIn(0, 255)
                    } else {
                        // Ocean blue
                        r = 30; g = 80; b = 170
                    }
                } else {
                    // Night texture: mostly black with scattered "city" dots
                    val hash = ((x * 7919 + y * 104729) and 0xFFFF).toFloat() / 0xFFFF
                    if (hash > 0.97f && Math.abs(v - 0.5f) < 0.35f) {
                        // Faint orange city light
                        val brightness = ((hash - 0.97f) / 0.03f * 200).toInt()
                        r = brightness.coerceIn(0, 255)
                        g = (brightness * 0.7f).toInt().coerceIn(0, 255)
                        b = (brightness * 0.2f).toInt().coerceIn(0, 255)
                    } else {
                        r = 0; g = 0; b = 0
                    }
                }

                pixels.put(r.toByte())
                pixels.put(g.toByte())
                pixels.put(b.toByte())
                pixels.put(0xFF.toByte()) // alpha
            }
        }
        pixels.flip()

        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        val texId = textureIds[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
            width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels
        )
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        return texId
    }

    // ------------------------------------------------------------------
    // Cloud texture generation
    // ------------------------------------------------------------------

    /**
     * Creates a procedural cloud texture using layered value noise.
     * Stored in the alpha channel (RGB is white); alpha controls opacity.
     */
    private fun createCloudTexture(): Int {
        val width = 512
        val height = 256
        val pixels = ByteBuffer.allocateDirect(width * height * 4)
            .order(ByteOrder.nativeOrder())

        for (y in 0 until height) {
            val v = y.toFloat() / height
            // Reduce clouds near poles (latitude fade)
            val lat = Math.abs(v - 0.5f) * 2f
            val latFade = if (lat > 0.85f) 0f
            else if (lat > 0.7f) 1f - (lat - 0.7f) / 0.15f
            else 1f

            for (x in 0 until width) {
                val u = x.toFloat() / width

                // Layered noise (fBm-like) with 5 octaves
                var noise = 0f
                var amplitude = 0.5f
                var frequency = 4f
                for (octave in 0 until 5) {
                    noise += amplitude * valueNoise(u * frequency, v * frequency)
                    amplitude *= 0.5f
                    frequency *= 2.1f
                }

                // Shape into cloud-like coverage: threshold and soften
                val cloud = ((noise - 0.38f) / 0.35f).coerceIn(0f, 1f)
                val alpha = (cloud * cloud * latFade * 0.7f * 255f).toInt().coerceIn(0, 255)

                pixels.put(0xFF.toByte()) // R (white)
                pixels.put(0xFF.toByte()) // G
                pixels.put(0xFF.toByte()) // B
                pixels.put(alpha.toByte())
            }
        }
        pixels.flip()

        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        val texId = textureIds[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
            width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels
        )
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        return texId
    }

    /**
     * Simple 2D value noise using a hash function.
     * Returns a value in [0, 1], smoothly interpolated.
     */
    private fun valueNoise(x: Float, y: Float): Float {
        val ix = kotlin.math.floor(x).toInt()
        val iy = kotlin.math.floor(y).toInt()
        val fx = x - kotlin.math.floor(x)
        val fy = y - kotlin.math.floor(y)

        // Smoothstep interpolation
        val sx = fx * fx * (3f - 2f * fx)
        val sy = fy * fy * (3f - 2f * fy)

        // Hash corners
        val n00 = hash2d(ix, iy)
        val n10 = hash2d(ix + 1, iy)
        val n01 = hash2d(ix, iy + 1)
        val n11 = hash2d(ix + 1, iy + 1)

        // Bilinear interpolation
        val nx0 = n00 + sx * (n10 - n00)
        val nx1 = n01 + sx * (n11 - n01)
        return nx0 + sy * (nx1 - nx0)
    }

    /** Simple integer hash returning a float in [0, 1]. */
    private fun hash2d(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF.toFloat()
    }
}
