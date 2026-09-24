package com.globe.app.render

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log

/**
 * Per-surface texture budget. The bundled maps are 8192 px wide; small or
 * long-lived surfaces decode a power-of-two downsample instead of the full image.
 */
enum class TextureQuality(val earthMaxWidth: Int, val moonMaxWidth: Int) {
    /** Interactive app: full detail for close zoom. */
    FULL(8192, 8192),
    /** Live wallpaper: fixed framing, may run alongside the app and the system preview. */
    WALLPAPER(4096, 1024),
    /** 320x180 widget snapshot; the Moon is a few pixels at most. */
    WIDGET(1024, 256)
}

/** Decodes a drawable straight to a mipmapped GL texture. Call with a current GL context. */
object TextureLoader {
    private const val TAG = "TextureLoader"

    fun load(context: Context, resourceId: Int, maxWidth: Int, wrapS: Int = GLES30.GL_REPEAT): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
        BitmapFactory.decodeResource(context.resources, resourceId, bounds)
        val limit = minOf(maxWidth, maxTextureSize())
        var sample = 1
        while (bounds.outWidth / sample > limit || bounds.outHeight / sample > limit) sample *= 2

        val options = BitmapFactory.Options().apply { inScaled = false; inSampleSize = sample }
        val bitmap = BitmapFactory.decodeResource(context.resources, resourceId, options) ?: run {
            Log.e(TAG, "Failed to decode resource $resourceId")
            return 0
        }
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        if (ids[0] == 0) {
            Log.e(TAG, "glGenTextures failed")
            bitmap.recycle()
            return 0
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrapS)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        Log.d(TAG, "Loaded ${bitmap.width}x${bitmap.height} (1/$sample) for resource $resourceId")
        bitmap.recycle()
        return ids[0]
    }

    private fun maxTextureSize(): Int {
        val size = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, size, 0)
        return if (size[0] > 0) size[0] else 4096
    }
}
