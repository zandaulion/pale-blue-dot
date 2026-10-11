package com.globe.app.moon

import android.annotation.TargetApi
import android.app.LocaleManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.LocaleList
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.Window
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.globe.app.AppLanguage
import com.globe.app.GlobeSurfaceView
import com.globe.app.MainActivity
import com.globe.app.R
import com.globe.app.earth.SunPosition
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Opt-in real GPU/camera/locale audit; screenshots contain only the app's test scenes. */
@RunWith(AndroidJUnit4::class)
@TargetApi(33)
class MoonGuideDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun children(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { children(view.getChildAt(it)) } else emptyList()

    private fun timeFor(target: Float, waxing: Boolean = true): Long {
        // Synthetic scene instants from one lunar cycle, independent of today's date.
        val start = 1_791_720_000_000L
        return (0L..720L).map { start + it * 3_600_000L }.filter { time ->
            val now = MoonGuide.illumination(MoonPosition.calculate(time), SunPosition.calculate(time))
            val next = MoonGuide.illumination(MoonPosition.calculate(time + 3_600_000), SunPosition.calculate(time + 3_600_000))
            (next >= now) == waxing
        }.minBy { time -> abs(MoonGuide.illumination(MoonPosition.calculate(time), SunPosition.calculate(time)) - target) }
    }

    private fun field(activity: MainActivity, name: String): Any = MainActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity).let { requireNotNull(it) }

    private fun taskLock(command: String) {
        instrumentation.uiAutomation.executeShellCommand("am task lock $command").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }

    private fun capture(scenario: ActivityScenario<MainActivity>, time: Long, name: String, expectedGuide: Boolean) {
        var configuredGlobe: GlobeSurfaceView? = null
        fun configure(activity: MainActivity) {
            val globe = field(activity, "globeView") as GlobeSurfaceView
            if (configuredGlobe === globe) return
            configuredGlobe = globe
            // Device navigation is pinned separately; keep unsolicited app
            // touches from cancelling the camera flight or opening a panel.
            val callback = activity.window.callback
            activity.window.callback = object : Window.Callback by callback {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean = true
            }
            (field(activity, "onboardingOverlay") as View).visibility = View.GONE
            globe.camera.reduceMotion = true
            globe.sceneClock.enter(time)
            MainActivity::class.java.getDeclaredMethod("updateTimeLabel").apply { isAccessible = true }.invoke(activity)
            val moon = MoonPosition.calculate(time)
            globe.camera.faceSky(moon[0], moon[1], moon[2], aboveGlobe = true)
        }
        scenario.onActivity { configure(it) }
        val deadline = android.os.SystemClock.elapsedRealtime() + 12_000
        var stableSince = android.os.SystemClock.elapsedRealtime()
        var focused = false
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                val current = field(activity, "globeView") as GlobeSurfaceView
                if (configuredGlobe !== current) {
                    configure(activity)
                    stableSince = android.os.SystemClock.elapsedRealtime()
                }
                focused = activity.window.decorView.hasWindowFocus()
            }
            if (focused && android.os.SystemClock.elapsedRealtime() - stableSince >= 2600) break
            Thread.sleep(100)
        }
        assertTrue("App window lost focus: $name", focused)
        val committed = CountDownLatch(1)
        scenario.onActivity { activity ->
            activity.window.decorView.apply {
                viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                requestLayout()
                invalidate()
            }
        }
        assertTrue("No committed UI frame: $name", committed.await(5, TimeUnit.SECONDS))
        Thread.sleep(100)
        // Retain the actual device frame even if a subsequent assertion fails.
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)),
            "moon-guide-${instrumentation.targetContext.resources.configuration.fontScale}").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        scenario.onActivity { activity ->
            val globe = field(activity, "globeView") as GlobeSurfaceView
            assertTrue("App window changed during capture: $name", activity.window.decorView.hasWindowFocus())
            assertEquals("The GL scene must use the requested simulated date", time, globe.renderer.lastFrameTimeMs)
            val overlay = children(activity.window.decorView).filterIsInstance<MoonGuideOverlay>().single()
            val covered = MoonGuideOverlay::class.java.getDeclaredField("isCovered").apply { isAccessible = true }
                .get(overlay) as Function0<*>
            assertEquals("$name: frame=${globe.renderer.moonGuideFrame}, covered=${covered.invoke()}, " +
                "overlay=${overlay.width}x${overlay.height}, visible=${overlay.visibility}/${overlay.windowVisibility}, " +
                "label=${overlay.label.visibility}, camera=${globe.camera.azimuth}/${globe.camera.elevation}/${globe.camera.distance}",
                expectedGuide, overlay.label.isShown)
            if (expectedGuide) {
                val label = overlay.label
                val layout = requireNotNull(label.layout)
                assertTrue("Wrapped text is clipped: $name", layout.height <= label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
                for (line in 0 until layout.lineCount) {
                    assertTrue("Text width overflow: $name", layout.getLineMax(line) <= layout.width + 2)
                    assertEquals(0, layout.getEllipsisCount(line))
                }
                val rect = Rect()
                assertTrue(label.getGlobalVisibleRect(rect))
                // Global rectangles round subpixel camera tracking to integer pixels.
                assertTrue("Label width clipped: $name ${label.width}/${rect.width()}", abs(label.width - rect.width()) <= 1)
                assertTrue("Label height clipped: $name ${label.height}/${rect.height()}", abs(label.height - rect.height()) <= 1)
                assertTrue(label.text.contains(activity.getString(R.string.text_new_moon)) ||
                    label.text.toString() == activity.getString(R.string.moon_guide_crescent,
                        java.text.NumberFormat.getPercentInstance(AppLanguage.locale(activity)).format(globe.renderer.moonGuideFrame!!.illumination)))
            }
        }
    }

    @SdkSuppress(minSdkVersion = 33)
    @Test fun inspectPhasesAndLanguages() {
        if (InstrumentationRegistry.getArguments().getString("moonGuideAudit") != "true") return
        val manager = instrumentation.targetContext.getSystemService(LocaleManager::class.java)
        val original = manager.applicationLocales
        var originalPose: FloatArray? = null
        var originalTime: Long? = null
        try {
            instrumentation.runOnMainSync { manager.applicationLocales = LocaleList.forLanguageTags("en-US") }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val protectTask = InstrumentationRegistry.getArguments().getString("moonGuidePinTask") == "true"
                if (protectTask) {
                    var taskId = 0
                    scenario.onActivity { taskId = it.taskId }
                    taskLock(taskId.toString())
                }
                try {
                    Thread.sleep(2000)
                    scenario.onActivity { activity ->
                        val globe = field(activity, "globeView") as GlobeSurfaceView
                        originalPose = floatArrayOf(globe.camera.azimuth, globe.camera.elevation, globe.camera.distance)
                        originalTime = if (globe.sceneClock.isExploring) globe.sceneClock.nowMs() else null
                    }
                    capture(scenario, timeFor(0.001f), "new-moon", true)
                    capture(scenario, timeFor(0.05f), "waxing-crescent", true)
                    capture(scenario, timeFor(0.05f, false), "waning-crescent", true)
                    capture(scenario, timeFor(0.10f), "fading-guide", true)
                    capture(scenario, timeFor(0.50f), "quarter-moon", false)
                    capture(scenario, timeFor(0.99f), "full-moon", false)
                    scenario.onActivity { activity ->
                        val globe = field(activity, "globeView") as GlobeSurfaceView
                        globe.sceneClock.enter(timeFor(0.001f))
                        val moon = MoonPosition.calculate(globe.sceneClock.nowMs())
                        val az = Math.toDegrees(kotlin.math.atan2(-moon[0].toDouble(), -moon[2].toDouble())).toFloat()
                        val el = Math.toDegrees(kotlin.math.asin(-moon[1].toDouble())).toFloat()
                        globe.camera.restore(az, el, 12f)
                    }
                    Thread.sleep(500)
                    scenario.onActivity { activity ->
                        assertNull((field(activity, "globeView") as GlobeSurfaceView).renderer.moonGuideFrame)
                    }
                    // Keep one task open to avoid the test invoker's EmptyActivity
                    // transitions between language captures.
                    val tags = InstrumentationRegistry.getArguments().getString("moonGuideTags")?.split(',') ?: AppLanguage.tags
                    for (tag in tags) {
                        instrumentation.runOnMainSync { manager.applicationLocales = LocaleList.forLanguageTags(tag) }
                        Thread.sleep(1200)
                        capture(scenario, timeFor(0.001f), "$tag-new-moon", true)
                        capture(scenario, timeFor(0.05f), "$tag-crescent", true)
                    }
                } finally {
                    // Restore navigation before ActivityScenario launches its cleanup activity.
                    if (protectTask) taskLock("stop")
                }
            }
        } finally {
            instrumentation.runOnMainSync { manager.applicationLocales = original }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val globe = field(activity, "globeView") as GlobeSurfaceView
                    originalTime?.let { globe.sceneClock.enter(it) } ?: globe.sceneClock.exit()
                    originalPose?.let { globe.camera.restore(it[0], it[1], it[2]) }
                }
            }
        }
    }
}
