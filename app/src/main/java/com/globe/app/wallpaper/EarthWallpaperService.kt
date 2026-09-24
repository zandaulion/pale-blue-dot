package com.globe.app.wallpaper

import android.app.WallpaperColors
import android.graphics.Color
import android.opengl.EGL14
import android.os.Build
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.globe.app.GlobeRenderer
import com.globe.app.camera.OrbitCamera
import com.globe.app.data.EarthRepository
import com.globe.app.earth.CloudMapProvider
import com.globe.app.earth.EarthRenderer
import com.globe.app.render.TextureQuality
import com.globe.app.time.RealTimeSceneClock

/** Each preview or active engine owns its EGL context, renderer, clock, and draw thread. */
class EarthWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = EarthEngine()

    private inner class EarthEngine : Engine() {
        private val thread = HandlerThread("EarthWallpaper-${System.nanoTime()}").apply { start() }
        private val handler = Handler(thread.looper)
        private val settings = WallpaperSettings(applicationContext)
        private val repository = EarthRepository.get(applicationContext)
        private val camera = OrbitCamera()
        private val renderer = GlobeRenderer(applicationContext, camera, RealTimeSceneClock(), textureQuality = TextureQuality.WALLPAPER)
        private val controller = WallpaperSceneController(renderer, camera)
        private val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var context: EGLContext = EGL14.EGL_NO_CONTEXT
        private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        private var surfaceReady = false
        private var visibleBySystem = false
        private var destroyed = false
        private var width = 0
        private var height = 0
        private var frames = 0L
        private var totalFrameMs = 0.0
        private var lastReportMs = System.currentTimeMillis()

        private var colorPreset: WallpaperSettings.Preset? = null

        private val settingsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "preset" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
                settings.read().preset != colorPreset) notifyColorsChanged()
            handler.post {
                if (!destroyed) {
                    controller.apply(settings.read())
                    if (settings.read().clouds == EarthRenderer.CloudMode.LIVE) repository.requestClouds()
                    drawSoon()
                }
            }
        }
        private val cloudObserver: (CloudMapProvider.Result?, EarthRepository.State) -> Unit = { result, _ ->
            if (destroyed) result?.bitmap?.recycle()
            else if (result != null) renderer.setCloudResult(result)
        }

        init {
            controller.apply(settings.read())
            settings.preferences.registerOnSharedPreferenceChangeListener(settingsListener)
            repository.observeClouds(cloudObserver)
            if (settings.read().clouds == EarthRenderer.CloudMode.LIVE) repository.requestClouds()
        }

        private val drawTask = object : Runnable {
            override fun run() {
                if (destroyed || !visibleBySystem || !surfaceReady) return
                try {
                    if (surface == EGL14.EGL_NO_SURFACE) createEgl()
                    if (surface == EGL14.EGL_NO_SURFACE) return
                    val start = System.nanoTime()
                    renderer.onDrawFrame(null)
                    if (!EGL14.eglSwapBuffers(display, surface)) {
                        Log.w(TAG, "Wallpaper swap failed: 0x${EGL14.eglGetError().toString(16)}")
                        teardown()
                    } else {
                        frames++
                        totalFrameMs += (System.nanoTime() - start) / 1_000_000.0
                        reportIfDue()
                    }
                } catch (error: Exception) {
                    Log.w(TAG, "Wallpaper frame failed", error)
                    teardown()
                }
                if (!destroyed && visibleBySystem && surfaceReady) {
                    handler.postDelayed(this, frameDelayMs())
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceReady = holder.surface.isValid
            drawSoon()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height
            surfaceReady = holder.surface.isValid
            handler.post {
                if (!destroyed && surface != EGL14.EGL_NO_SURFACE) renderer.onSurfaceChanged(null, width, height)
                drawSoon()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            visibleBySystem = visible
            handler.post {
                if (!visible) {
                    handler.removeCallbacks(drawTask)
                    report("hidden")
                } else {
                    drawSoon()
                }
            }
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixels: Int, yPixels: Int
        ) {
            // A single-page launcher reports step 0 (or 1); keep the chosen view centred there.
            controller.pageOffset = if (xOffsetStep > 0f && xOffsetStep < 1f) xOffset.coerceIn(0f, 1f) else 0.5f
            if (settings.read().pageParallax) handler.post { drawSoon() }
        }

        /** Fixed palette per composition so system theming stays stable while the globe turns. */
        override fun onComputeColors(): WallpaperColors? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
            val preset = settings.read().preset
            colorPreset = preset
            Log.d(TAG, "Reporting theme colors for $preset")
            val (primary, secondary, tertiary) = when (preset) {
                WallpaperSettings.Preset.WHOLE_EARTH -> Triple(0xFF2A5C9A, 0xFF5E7A3A, 0xFF0A1020)
                WallpaperSettings.Preset.NIGHT_LIGHTS -> Triple(0xFFD9A441, 0xFF0B1830, 0xFF1E3A5F)
                WallpaperSettings.Preset.HORIZON -> Triple(0xFF4A90D9, 0xFF0A1020, 0xFF2A5C9A)
            }
            val p = Color.valueOf(primary.toInt())
            val s = Color.valueOf(secondary.toInt())
            val t = Color.valueOf(tertiary.toInt())
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                WallpaperColors(p, s, t, WallpaperColors.HINT_SUPPORTS_DARK_THEME)
            } else WallpaperColors(p, s, t)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            handler.removeCallbacks(drawTask)
            handler.post { teardown() }
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            destroyed = true
            visibleBySystem = false
            surfaceReady = false
            settings.preferences.unregisterOnSharedPreferenceChangeListener(settingsListener)
            repository.removeCloudObserver(cloudObserver)
            handler.removeCallbacks(drawTask)
            handler.post {
                report("destroyed")
                teardown()
                thread.quitSafely()
            }
            super.onDestroy()
        }

        private fun drawSoon() {
            handler.removeCallbacks(drawTask)
            if (!destroyed && visibleBySystem && surfaceReady && width > 0 && height > 0) {
                handler.post(drawTask)
            }
        }

        private fun frameDelayMs(): Long {
            if (powerManager.isPowerSaveMode) return 2_000L
            return if (settings.read().motion == WallpaperSettings.Motion.SLOW_ORBIT) 67L else 1_000L
        }

        private fun createEgl() {
            if (!surfaceReady || width <= 0 || height <= 0) return
            val holderSurface = surfaceHolder.surface
            if (!holderSurface.isValid) return
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
            check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0)) { "EGL initialize failed" }
            val attrs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, 0x0040, // EGL_OPENGL_ES3_BIT_KHR
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0) && count[0] > 0) {
                "No GLES3 wallpaper config"
            }
            context = EGL14.eglCreateContext(
                display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0
            )
            check(context != EGL14.EGL_NO_CONTEXT) { "EGL context failed" }
            surface = EGL14.eglCreateWindowSurface(display, configs[0], holderSurface, intArrayOf(EGL14.EGL_NONE), 0)
            check(surface != EGL14.EGL_NO_SURFACE) { "EGL surface failed" }
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "EGL make current failed" }
            renderer.onSurfaceCreated(null, null)
            renderer.onSurfaceChanged(null, width, height)
            Log.d(TAG, "Created independent wallpaper context ${width}x$height preview=$isPreview")
        }

        private fun teardown() {
            if (display != EGL14.EGL_NO_DISPLAY) {
                if (context != EGL14.EGL_NO_CONTEXT && surface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglMakeCurrent(display, surface, surface, context)
                    try { renderer.release() } catch (error: Exception) { Log.w(TAG, "GL release failed", error) }
                }
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            }
            surface = EGL14.EGL_NO_SURFACE
            context = EGL14.EGL_NO_CONTEXT
            display = EGL14.EGL_NO_DISPLAY
        }

        private fun reportIfDue() {
            if (System.currentTimeMillis() - lastReportMs >= 30_000L) report("visible")
        }
        private fun report(state: String) {
            if (frames > 0L) {
                val elapsed = (System.currentTimeMillis() - lastReportMs).coerceAtLeast(1)
                Log.i(TAG, "$state preview=$isPreview frames=$frames fps=${"%.2f".format(frames * 1000.0 / elapsed)} " +
                    "meanDrawMs=${"%.2f".format(totalFrameMs / frames)}")
            } else Log.i(TAG, "$state preview=$isPreview frames=0")
            frames = 0
            totalFrameMs = 0.0
            lastReportMs = System.currentTimeMillis()
        }
    }

    companion object { private const val TAG = "EarthWallpaper" }
}
