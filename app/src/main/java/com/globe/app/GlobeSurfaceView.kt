package com.globe.app

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.globe.app.camera.OrbitCamera
import com.globe.app.eclipse.EclipseDetector

/**
 * Custom GLSurfaceView that handles touch input for orbiting and zooming the camera.
 */
class GlobeSurfaceView(
    context: Context,
    onCloudStatusChanged: ((String?) -> Unit)? = null,
    onEclipseStateChanged: ((EclipseDetector.EclipseState) -> Unit)? = null
) : GLSurfaceView(context) {

    val renderer: GlobeRenderer
    private val camera: OrbitCamera = OrbitCamera()
    private val scaleDetector: ScaleGestureDetector

    private var previousX = 0f
    private var previousY = 0f

    init {
        // Request OpenGL ES 3.0 context
        setEGLContextClientVersion(3)

        renderer = GlobeRenderer(context, camera, onCloudStatusChanged, onEclipseStateChanged)
        setRenderer(renderer)

        // Render continuously (the Earth rotates)
        renderMode = RENDERMODE_CONTINUOUSLY

        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                camera.zoom(1.0f / detector.scaleFactor)
                return true
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                camera.startDrag()
                previousX = event.x
                previousY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                camera.endDrag()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // When a finger lifts during a pinch, reset to the remaining finger
                // so the next ACTION_MOVE doesn't jump from a stale position.
                val remainingIndex = if (event.actionIndex == 0) 1 else 0
                previousX = event.getX(remainingIndex)
                previousY = event.getY(remainingIndex)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                    val dx = event.x - previousX
                    val dy = event.y - previousY
                    camera.rotate(dx, dy)
                }
                previousX = event.x
                previousY = event.y
            }
        }

        return true
    }
}
