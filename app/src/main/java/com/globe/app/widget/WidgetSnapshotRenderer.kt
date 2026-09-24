package com.globe.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES30
import com.globe.app.GlobeRenderer
import com.globe.app.camera.OrbitCamera
import com.globe.app.earth.EarthRenderer
import com.globe.app.render.TextureQuality
import com.globe.app.places.SavedPlace
import com.globe.app.time.RealTimeSceneClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/** Bounded, one-frame offscreen GLES scene. Called only from widget worker thread. */
object WidgetSnapshotRenderer {
    private const val MAX_WIDTH = 720

    /** Renders at the requested aspect, scaled down so the longer side stays within [MAX_WIDTH]. */
    fun render(context: Context, place: SavedPlace?, requestedWidth: Int = 320, requestedHeight: Int = 180): Bitmap {
        val scale = minOf(1f, MAX_WIDTH.toFloat() / maxOf(requestedWidth, requestedHeight))
        val width = (requestedWidth * scale).toInt().coerceAtLeast(16)
        val height = (requestedHeight * scale).toInt().coerceAtLeast(16)
        val camera = OrbitCamera().apply {
            val az = if (place == null) 150f else {
                val lon = Math.toRadians(place.lon)
                Math.toDegrees(Math.atan2(-cos(lon), sin(lon))).toFloat()
            }
            restore(az, place?.lat?.toFloat() ?: 15f, 3.9f)
            reduceMotion = true
        }
        val renderer = GlobeRenderer(context.applicationContext, camera, RealTimeSceneClock(), textureQuality = TextureQuality.WIDGET).apply {
            showEvents = false; showIss = false; showIndicators = false; showConstellations = false
            showPlacePins = false
            earthRenderer.cloudMode = EarthRenderer.CloudMode.OFF
            earthRenderer.auroraVisible = false
            earthRenderer.terminatorVisible = false
            setDecorativeMotionReduced(true)
        }
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
        var surface = EGL14.EGL_NO_SURFACE
        var glContext = EGL14.EGL_NO_CONTEXT
        var initialized = false
        try {
            check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0))
            val attrs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 0x0040,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_NONE)
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0) && count[0] > 0)
            glContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            check(glContext != EGL14.EGL_NO_CONTEXT)
            surface = EGL14.eglCreatePbufferSurface(display, configs[0],
                intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE), 0)
            check(surface != EGL14.EGL_NO_SURFACE)
            check(EGL14.eglMakeCurrent(display, surface, surface, glContext))
            renderer.onSurfaceCreated(null, null)
            initialized = true
            renderer.onSurfaceChanged(null, width, height)
            renderer.onDrawFrame(null)
            GLES30.glFinish()
            val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
            GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
            check(GLES30.glGetError() == GLES30.GL_NO_ERROR) { "Widget GL read failed" }
            val pixels = IntArray(width * height)
            for (y in 0 until height) for (x in 0 until width) {
                val i = (y * width + x) * 4
                pixels[(height - 1 - y) * width + x] = (0xff shl 24) or
                    ((buffer.get(i).toInt() and 255) shl 16) or
                    ((buffer.get(i + 1).toInt() and 255) shl 8) or
                    (buffer.get(i + 2).toInt() and 255)
            }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        } finally {
            if (initialized) renderer.release()
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (glContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, glContext)
            EGL14.eglTerminate(display)
        }
    }
}
