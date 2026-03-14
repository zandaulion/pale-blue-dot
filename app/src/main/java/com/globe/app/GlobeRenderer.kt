package com.globe.app

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.globe.app.camera.OrbitCamera
import com.globe.app.earth.EarthRenderer
import com.globe.app.moon.MoonRenderer
import com.globe.app.stars.StarsRenderer
import com.globe.app.indicators.IndicatorRenderer
import com.globe.app.sun.SunRenderer
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
    private val camera: OrbitCamera
) : GLSurfaceView.Renderer {

    private val earthRenderer = EarthRenderer()
    private val starsRenderer = StarsRenderer()
    private val moonRenderer = MoonRenderer()
    private val sunRenderer = SunRenderer()
    private val indicatorRenderer = IndicatorRenderer()

    private val projectionMatrix = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)

        earthRenderer.onSurfaceCreated(
            context,
            dayTextureResId = R.drawable.earth_day,
            nightTextureResId = R.drawable.earth_night
        )
        starsRenderer.init()
        moonRenderer.init(context, textureResId = R.drawable.moon)
        sunRenderer.init()
        indicatorRenderer.init()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)

        val aspect = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 33f, aspect, 0.1f, 1000f)
        indicatorRenderer.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        camera.update()
        val viewMatrix = camera.getViewMatrix()
        val camPos = camera.getPosition()

        // 1. Stars — drawn first as background (handles its own GL state)
        starsRenderer.draw(viewMatrix, projectionMatrix)

        // 2. Sun — billboard with glow, drawn behind everything (no depth)
        sunRenderer.draw(viewMatrix, projectionMatrix)

        // 3. Moon — drawn between stars and Earth (depth tested, behind Earth)
        moonRenderer.draw(viewMatrix, projectionMatrix)

        // 4. Earth — pass current camera matrices, then draw
        earthRenderer.setMatrices(viewMatrix, projectionMatrix, camPos)
        earthRenderer.onDrawFrame()

        // 5. Indicator arrows — 2D overlay pointing toward sun and moon
        indicatorRenderer.draw(viewMatrix, projectionMatrix)
    }
}
