package com.globe.app.moon

import android.opengl.Matrix
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.globe.app.render.SceneProjection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoonGuideTest {
    @Test fun guideUsesSceneLightingAndFadesOnBothSidesOfNewMoon() {
        val moon = floatArrayOf(0f, 0f, 1f)
        assertEquals(0f, MoonGuide.illumination(moon, moon), 0.00001f)
        assertEquals(1f, MoonGuide.illumination(moon, floatArrayOf(0f, 0f, -1f)), 0.00001f)
        assertEquals(0.5f, MoonGuide.illumination(moon, floatArrayOf(1f, 0f, 0f)), 0.00001f)
        assertEquals(1f, MoonGuide.strength(0f), 0f)
        assertEquals(1f, MoonGuide.strength(0.03f), 0f)
        assertEquals(0.5f, MoonGuide.strength(0.075f), 0.00001f)
        assertEquals(0f, MoonGuide.strength(0.12f), 0.00001f)
        assertEquals(0f, MoonGuide.strength(1f), 0f)
        val angle = 0.3
        val waxing = floatArrayOf(kotlin.math.sin(angle).toFloat(), 0f, kotlin.math.cos(angle).toFloat())
        val waning = floatArrayOf(-waxing[0], 0f, waxing[2])
        assertEquals(MoonGuide.strength(MoonGuide.illumination(moon, waxing)),
            MoonGuide.strength(MoonGuide.illumination(moon, waning)), 0f)
    }

    @Test fun labelDisappearsBehindEarthCameraAndScreenEdges() {
        val projection = SceneProjection.perspective(1080, 2200)
        val moon = floatArrayOf(0f, 0f, -1f)
        fun frame(eye: FloatArray, direction: FloatArray = moon): MoonGuide.Frame? {
            val view = FloatArray(16)
            Matrix.setLookAtM(view, 0, eye[0], eye[1], eye[2], 0f, 0f, 0f, 0f, 1f, 0f)
            return MoonGuide.project(direction, direction, eye, view, projection, 1080, 2200)
        }
        assertNull(frame(floatArrayOf(0f, 0f, 12f))) // Earth blocks the Moon.
        assertNull(frame(floatArrayOf(0f, 0f, -12f))) // Behind the camera.
        val visible = frame(floatArrayOf(1.6f, 0f, 12f))
        assertNotNull(visible)
        assertTrue(visible!!.radiusPx > 0)
        assertTrue(visible.x in 0f..1080f && visible.y in 0f..2200f)
        assertNull(frame(floatArrayOf(12f, 0f, 0f))) // Outside the view.
        val view = FloatArray(16)
        Matrix.setLookAtM(view, 0, 1.6f, 0f, 12f, 0f, 0f, 0f, 0f, 1f, 0f)
        assertNull(MoonGuide.project(moon, floatArrayOf(0f, 0f, 1f), floatArrayOf(1.6f, 0f, 12f),
            view, projection, 1080, 2200)) // Full Moon needs no guide.
    }
}
