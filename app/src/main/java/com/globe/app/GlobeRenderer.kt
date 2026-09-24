package com.globe.app

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.globe.app.camera.OrbitCamera
import com.globe.app.data.EarthRepository
import com.globe.app.earth.EarthRenderer
import com.globe.app.moon.MoonRenderer
import com.globe.app.stars.ConstellationRenderer
import com.globe.app.stars.StarsRenderer
import com.globe.app.indicators.IndicatorRenderer
import com.globe.app.iss.ISSOrbitRenderer
import com.globe.app.events.EarthEventsProvider
import com.globe.app.render.LayerSettings
import com.globe.app.render.SceneProjection
import com.globe.app.render.TextureQuality
import com.globe.app.render.PlateBoundariesRenderer
import com.globe.app.render.LatitudeGuidesRenderer
import com.globe.app.events.GlobePicker
import com.globe.app.time.SceneClock
import com.globe.app.time.RealTimeSceneClock
import com.globe.app.events.EarthEventsRenderer
import com.globe.app.places.PlacePinsRenderer
import com.globe.app.places.SavedPlace
import com.globe.app.sun.SunRenderer
import com.globe.app.eclipse.EclipseDetector
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Main renderer. Orchestrates drawing the starfield background and Earth each frame.
 *
 * Draw order:
 *   1. Stars (depth test off, depth write off — infinite background)
 *   2. Earth (depth test on, depth write on — opaque foreground)
 */
class GlobeRenderer(
    private val context: Context,
    private val camera: OrbitCamera,
    val clock: SceneClock = RealTimeSceneClock(),
    private val onCloudStatusChanged: ((String?) -> Unit)? = null,
    private val onEclipseStateChanged: ((EclipseDetector.EclipseState) -> Unit)? = null,
    private val textureQuality: TextureQuality = TextureQuality.FULL
) : GLSurfaceView.Renderer {

    val earthRenderer = EarthRenderer()
    private val starsRenderer = StarsRenderer()
    val constellationRenderer = ConstellationRenderer()
    private val moonRenderer = MoonRenderer()
    private val sunRenderer = SunRenderer()
    val indicatorRenderer = IndicatorRenderer()
    val issOrbitRenderer = ISSOrbitRenderer()
    val earthEventsRenderer = EarthEventsRenderer()
    private val placePinsRenderer = PlacePinsRenderer()
    private val plateBoundariesRenderer = PlateBoundariesRenderer(context)
    private val latitudeGuidesRenderer = LatitudeGuidesRenderer()
    private var allEvents: List<EarthEventsProvider.Event> = emptyList()
    private var layerSettings: LayerSettings? = null
    @Volatile var showConstellations = true
    @Volatile var showIss = true
    @Volatile var showEvents = true
    @Volatile var showIndicators = true
    @Volatile var showStars = true
    @Volatile var showPlacePins = false
    @Volatile var showPlateBoundaries = false
    @Volatile var showLatitudeGuides = false
    @Volatile var beforeFrame: ((Long) -> Unit)? = null
    @Volatile private var requestedFrameX = 0f
    @Volatile private var requestedFrameY = 0f
    private var appliedFrameX = Float.NaN
    private var appliedFrameY = Float.NaN
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    @Volatile var lastFrameTimeMs = 0L
        private set

    private val projectionMatrix = FloatArray(16)
    @Volatile private var pickViewMatrix = FloatArray(16)
    @Volatile private var pickProjectionMatrix = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)

        earthRenderer.onSurfaceCreated(
            context,
            dayTextureResId = R.drawable.earth_day,
            nightTextureResId = R.drawable.earth_night,
            maxTextureWidth = textureQuality.earthMaxWidth
        )
        starsRenderer.init()
        constellationRenderer.init()
        moonRenderer.init(context, R.drawable.moon, textureQuality.moonMaxWidth)
        sunRenderer.init()
        indicatorRenderer.init()
        issOrbitRenderer.init()
        earthEventsRenderer.init()
        placePinsRenderer.init()
        plateBoundariesRenderer.init()
        latitudeGuidesRenderer.init()
        // Re-upload CPU data after GL context recreation. Network requests are owned by EarthRepository.
        earthEventsRenderer.setEvents(allEvents.filter { layerSettings?.enabled(it.type) != false })
        EarthRepository.get(context).copyCloudResult()?.let { setCloudResult(it) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        surfaceWidth = width
        surfaceHeight = height
        updateProjection()
        indicatorRenderer.onSurfaceChanged(width, height)
    }

    private fun updateProjection() {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return
        val projection = SceneProjection.perspective(
            surfaceWidth, surfaceHeight, requestedFrameX, requestedFrameY
        )
        System.arraycopy(projection, 0, projectionMatrix, 0, 16)
        pickProjectionMatrix = projection
        appliedFrameX = requestedFrameX
        appliedFrameY = requestedFrameY
    }

    fun setFraming(x: Float, y: Float) {
        requestedFrameX = x.coerceIn(-0.5f, 0.5f)
        requestedFrameY = y.coerceIn(-0.5f, 0.5f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // Every renderer in this frame receives the same explicit time.
        val timeMs = clock.nowMs()
        lastFrameTimeMs = timeMs
        if (requestedFrameX != appliedFrameX || requestedFrameY != appliedFrameY) updateProjection()
        beforeFrame?.invoke(timeMs)
        camera.update()
        val viewMatrix = camera.getViewMatrix()
        pickViewMatrix = viewMatrix
        val camPos = camera.getPosition()

        // 1. Stars — drawn first as background (handles its own GL state)
        if (showStars) starsRenderer.draw(viewMatrix, projectionMatrix)

        // 1b. Constellation lines — drawn right after stars, same depth/blend state
        if (showConstellations) constellationRenderer.draw(viewMatrix, projectionMatrix)

        // 2. Sun — billboard with glow, drawn behind everything (no depth)
        sunRenderer.draw(viewMatrix, projectionMatrix, timeMs)

        // 3. Moon — drawn between stars and Earth (depth tested, behind Earth)
        moonRenderer.draw(viewMatrix, projectionMatrix, timeMs)

        // 4. Earth — pass current camera matrices, then draw
        earthRenderer.setMatrices(viewMatrix, projectionMatrix, camPos)
        earthRenderer.onDrawFrame(timeMs)

        // 5. Earthquake/volcano markers — pulsing dots on the globe
        if (showEvents) earthEventsRenderer.draw(viewMatrix, projectionMatrix)
        if (showPlateBoundaries) plateBoundariesRenderer.draw(viewMatrix, projectionMatrix)
        if (showLatitudeGuides) latitudeGuidesRenderer.draw(viewMatrix, projectionMatrix)
        if (showPlacePins) placePinsRenderer.draw(viewMatrix, projectionMatrix)

        // 7. ISS orbit — thin line, drawn after Earth so depth test occludes the far side
        if (showIss) issOrbitRenderer.draw(viewMatrix, projectionMatrix, timeMs)

        // 8. Indicator arrows — 2D overlay pointing toward sun and moon
        if (showIndicators) indicatorRenderer.draw(viewMatrix, projectionMatrix, timeMs)

        // 9. Eclipse detection — notify UI thread of alignment state
        onEclipseStateChanged?.invoke(EclipseDetector.detect(timeMs))
    }

    fun setAllEvents(events: List<EarthEventsProvider.Event>) {
        allEvents = events.toList()
        earthEventsRenderer.setEvents(allEvents.filter { layerSettings?.enabled(it.type) != false })
    }

    fun setPlaces(places: List<SavedPlace>) { placePinsRenderer.setPlaces(places) }

    fun pick(x: Float, y: Float, width: Int, height: Int): DoubleArray? =
        GlobePicker.pick(pickViewMatrix, pickProjectionMatrix, x, y, width, height)

    fun projectWorldToScreen(p: FloatArray, width: Int, height: Int): FloatArray? {
        if (width <= 0 || height <= 0) return null
        val vp = FloatArray(16)
        Matrix.multiplyMM(vp, 0, pickProjectionMatrix, 0, pickViewMatrix, 0)
        val clip = FloatArray(4)
        Matrix.multiplyMV(clip, 0, vp, 0, floatArrayOf(p[0], p[1], p[2], 1f), 0)
        if (clip[3] <= 0f) return null
        return floatArrayOf(
            (clip[0] / clip[3] + 1f) * 0.5f * width,
            (1f - clip[1] / clip[3]) * 0.5f * height
        )
    }

    fun applyLayers(settings: LayerSettings) {
        layerSettings = settings
        earthRenderer.cloudMode = settings.cloudMode
        earthRenderer.auroraVisible = settings.aurora
        earthRenderer.terminatorVisible = settings.terminator
        showConstellations = settings.constellations
        showIss = settings.iss
        showPlateBoundaries = settings.plateBoundaries
        setDecorativeMotionReduced(settings.reduceMotion)
        earthEventsRenderer.setEvents(allEvents.filter { settings.enabled(it.type) })
    }

    fun setDecorativeMotionReduced(reduced: Boolean) {
        camera.reduceMotion = reduced
        earthEventsRenderer.reduceMotion = reduced
        starsRenderer.reduceMotion = reduced
        earthRenderer.reduceMotion = reduced
    }

    fun setCloudResult(result: com.globe.app.earth.CloudMapProvider.Result) {
        // The repository hands each scene an owned copy; GL upload recycles it.
        earthRenderer.setCloudBitmap(result.bitmap)
        onCloudStatusChanged?.invoke(result.timestamp)
    }

    /** Must be called on the owning GL thread while its context is current. */
    fun release() {
        placePinsRenderer.destroy()
        plateBoundariesRenderer.destroy()
        latitudeGuidesRenderer.destroy()
        earthEventsRenderer.destroy()
        issOrbitRenderer.destroy()
        indicatorRenderer.destroy()
        sunRenderer.destroy()
        moonRenderer.destroy()
        constellationRenderer.destroy()
        starsRenderer.destroy()
        earthRenderer.release()
    }
}
