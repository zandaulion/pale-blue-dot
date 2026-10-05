package com.globe.app.wallpaper

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.globe.app.camera.OrbitCamera
import com.globe.app.places.SavedPlace
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.cos
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class WallpaperSetupTest {
    // Use the test APK's preferences so the phone's wallpaper settings stay intact.
    private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().context) {
        override fun getApplicationContext(): Context = this
    }
    private lateinit var settings: WallpaperSettings
    private val bucharest = SavedPlace("test:bucharest", "Bucharest, Romania", 44.43225, 26.10626, "Europe/Bucharest")

    @Before fun prepare() {
        settings = WallpaperSettings(context)
        settings.preferences.edit().clear().commit()
    }

    @After fun clean() {
        if (::settings.isInitialized) settings.preferences.edit().clear().commit()
    }

    @Test fun cityFacesCameraAndSurvivesReopen() {
        settings.setPlace(bucharest)
        val reopened = WallpaperSettings(context)
        val values = reopened.read()
        val camera = OrbitCamera().apply { restore(values.azimuth, values.elevation, 1.5f) }
        val position = camera.getPosition()
        val lat = Math.toRadians(bucharest.lat)
        val lon = Math.toRadians(bucharest.lon)
        assertEquals(-cos(lat) * cos(lon), position[0] / 1.5, 0.00001)
        assertEquals(sin(lat), position[1] / 1.5, 0.00001)
        assertEquals(cos(lat) * sin(lon), position[2] / 1.5, 0.00001)
        assertEquals(bucharest.name, reopened.placeName())
    }

    @Test fun choosingPlaceOverridesNightTrackingAndPageMotion() {
        settings.setPreset(WallpaperSettings.Preset.NIGHT_LIGHTS)
        settings.setPageParallax(true)
        settings.setPlace(bucharest)
        assertEquals(WallpaperSettings.Preset.NIGHT_LIGHTS, settings.read().preset)
        assertEquals(WallpaperSettings.Motion.FIXED, settings.read().motion)
        assertFalse(settings.read().pageParallax)
        settings.setPreset(WallpaperSettings.Preset.WHOLE_EARTH)
        settings.setPreset(WallpaperSettings.Preset.NIGHT_LIGHTS)
        assertEquals(WallpaperSettings.Motion.FIXED, settings.read().motion)
        assertEquals(bucharest.name, settings.placeName())
    }

    @Test fun manualAnglesClearCityLabelButFramingKeepsIt() {
        settings.setPlace(bucharest)
        settings.setScale(1.2f)
        settings.setFrameX(0.1f)
        assertEquals(bucharest.name, settings.placeName())
        settings.setAzimuth(0f)
        assertNull(settings.placeName())
        settings.setPlace(bucharest)
        settings.setElevation(-15f)
        assertNull(settings.placeName())
    }
}
