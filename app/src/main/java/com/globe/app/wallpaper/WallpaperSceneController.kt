package com.globe.app.wallpaper

import com.globe.app.GlobeRenderer
import com.globe.app.camera.OrbitCamera
import com.globe.app.earth.SunPosition
import kotlin.math.asin
import kotlin.math.atan2

/** Scene policy shared by the in-app preview and every independent wallpaper engine. */
class WallpaperSceneController(
    private val renderer: GlobeRenderer,
    private val camera: OrbitCamera
) {
    @Volatile private var values: WallpaperSettings.Values? = null
    /** Launcher page position, 0..1; 0.5 is centred. Written from the engine's main thread. */
    @Volatile var pageOffset = 0.5f
    private var orbitDegrees = 0f
    private var lastMotionNanos = 0L

    init {
        renderer.showEvents = false
        renderer.showIss = false
        renderer.showIndicators = false
        renderer.showConstellations = false
        renderer.earthRenderer.auroraVisible = false
        renderer.earthRenderer.terminatorVisible = false
        renderer.setDecorativeMotionReduced(true)
        renderer.beforeFrame = ::beforeFrame
    }

    fun apply(next: WallpaperSettings.Values) {
        values = next
        renderer.earthRenderer.cloudMode = next.clouds
        renderer.setFraming(next.frameX, next.frameY)
        val baseDistance = when (next.preset) {
            WallpaperSettings.Preset.WHOLE_EARTH -> 4.8f
            WallpaperSettings.Preset.NIGHT_LIGHTS -> 4.4f
            WallpaperSettings.Preset.HORIZON -> 3.0f
        }
        camera.restore(next.azimuth, next.elevation, baseDistance / next.scale)
        orbitDegrees = 0f
        lastMotionNanos = 0L
    }

    /** Runs on the GL thread; the camera pose is recomputed from scratch every frame. */
    private fun beforeFrame(timeMs: Long) {
        val v = values ?: return
        var azimuth = v.azimuth
        var elevation = v.elevation
        when (v.motion) {
            WallpaperSettings.Motion.FOLLOW_NIGHT -> {
                val sun = SunPosition.calculate(timeMs)
                azimuth = Math.toDegrees(atan2(-sun[0].toDouble(), -sun[2].toDouble())).toFloat()
                elevation = Math.toDegrees(asin((-sun[1].toDouble()).coerceIn(-1.0, 1.0))).toFloat()
            }
            WallpaperSettings.Motion.SLOW_ORBIT -> {
                val now = System.nanoTime()
                if (lastMotionNanos != 0L) {
                    val seconds = ((now - lastMotionNanos) / 1_000_000_000f).coerceIn(0f, 0.2f)
                    orbitDegrees = (orbitDegrees + ORBIT_DEGREES_PER_SECOND * seconds) % 360f
                }
                lastMotionNanos = now
            }
            WallpaperSettings.Motion.FIXED -> lastMotionNanos = 0L
        }
        val parallax = if (v.pageParallax) (pageOffset - 0.5f) * PARALLAX_DEGREES else 0f
        camera.azimuth = azimuth + orbitDegrees + parallax
        camera.elevation = elevation
    }

    private companion object {
        const val ORBIT_DEGREES_PER_SECOND = 0.35f
        /** Total turn across all launcher pages: enough to feel, not enough to disorient. */
        const val PARALLAX_DEGREES = 30f
    }
}
