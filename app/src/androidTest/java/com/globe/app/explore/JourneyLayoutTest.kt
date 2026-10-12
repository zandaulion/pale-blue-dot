package com.globe.app.explore

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.globe.app.GlobeSurfaceView
import com.globe.app.MainActivity
import com.globe.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JourneyLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun field(activity: MainActivity, name: String): Any = MainActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)!!
    private fun invoke(activity: MainActivity, name: String, vararg args: Any) {
        val types = args.map { if (it is Boolean) Boolean::class.javaPrimitiveType!! else it.javaClass }.toTypedArray()
        MainActivity::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(activity, *args)
    }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun bounds(view: View) = Rect().also { check(view.getGlobalVisibleRect(it)) }
    private fun taskLock(command: String) {
        instrumentation.uiAutomation.executeShellCommand("am task lock $command").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
    private fun settle() { instrumentation.waitForIdleSync(); Thread.sleep(600) }
    private fun withJourney(block: (ActivityScenario<MainActivity>) -> Unit) {
        val prefs = context.getSharedPreferences("globe_prefs", Context.MODE_PRIVATE)
        val keys = listOf("onboarded", "narrate_enabled", "device_narrate_enabled")
        val before = keys.associateWith { if (prefs.contains(it)) prefs.getBoolean(it, false) else null }
        prefs.edit().putBoolean("onboarded", true).putBoolean("narrate_enabled", false)
            .putBoolean("device_narrate_enabled", false).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val globe = field(activity, "globeView") as GlobeSurfaceView
                    globe.camera.reduceMotion = true
                    invoke(activity, "startJourney", JourneyContent.all(activity).first(), true)
                }
                var taskId = 0
                scenario.onActivity { taskId = it.taskId }
                taskLock(taskId.toString())
                try { settle(); block(scenario) } finally { taskLock("stop"); Thread.sleep(1000) }
            }
        } finally {
            val editor = prefs.edit()
            before.forEach { (key, value) -> if (value == null) editor.remove(key) else editor.putBoolean(key, value) }
            editor.commit()
        }
    }

    private fun geometry(activity: MainActivity) {
        val globe = field(activity, "globeView") as GlobeSurfaceView
        val card = field(activity, "journeyCard") as View
        val footer = field(activity, "journeyFooter") as View
        val overlay = field(activity, "companionPanel") as View
        assertFalse("Globe and lesson overlap", Rect.intersects(bounds(globe), bounds(card)))
        val cardVisible = Rect().also { check(card.getLocalVisibleRect(it)) }
        assertEquals("Journey card extends beyond the screen", Rect(0, 0, card.width, card.height), cardVisible)
        assertTrue("No room remains for the globe", globe.height >= overlay.height / 4)
        val footerVisible = Rect().also { check(footer.getLocalVisibleRect(it)) }
        assertEquals("Footer is clipped", Rect(0, 0, footer.width, footer.height), footerVisible)
        assertTrue("No readable lesson area", (field(activity, "companionScroll") as View).height >=
            (48 * activity.resources.displayMetrics.density).toInt())
        assertFalse("Journey scrim intercepts the globe", overlay.isClickable)
        assertEquals(0, (overlay.background as ColorDrawable).alpha)
    }

    @Test fun globeGesturesPassThroughAndHidingPreservesTheCurrentStep() = withJourney { scenario ->
        scenario.onActivity { activity ->
            geometry(activity)
            val globe = field(activity, "globeView") as GlobeSurfaceView
            val point = IntArray(2); globe.getLocationInWindow(point)
            val centerX = point[0] + globe.width * .5f; val centerY = point[1] + globe.height * .5f
            val before = globe.camera.azimuth
            val time = SystemClock.uptimeMillis()
            for ((delay, action, offset) in listOf(Triple(0L, MotionEvent.ACTION_DOWN, 0f),
                Triple(40L, MotionEvent.ACTION_MOVE, 100f), Triple(80L, MotionEvent.ACTION_UP, 100f))) {
                val event = MotionEvent.obtain(time, time + delay, action, centerX + offset, centerY, 0)
                activity.dispatchTouchEvent(event); event.recycle()
            }
            assertTrue("The card blocks globe dragging", kotlin.math.abs(before - globe.camera.azimuth) > 1f)
            globe.camera.restore(globe.camera.azimuth, globe.camera.elevation, 8f)
            val fingers = Array(2) { pointerId -> MotionEvent.PointerProperties().apply {
                id = pointerId; toolType = MotionEvent.TOOL_TYPE_FINGER
            } }
            fun pinch(action: Int, count: Int, span: Float, delay: Long) {
                val coordinates = Array(count) { id -> MotionEvent.PointerCoords().apply {
                    x = centerX + if (id == 0) -span / 2 else span / 2
                    y = centerY; pressure = 1f; size = 1f
                } }
                val event = MotionEvent.obtain(time + 100, time + 100 + delay, action, count, fingers,
                    coordinates, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
                activity.dispatchTouchEvent(event); event.recycle()
            }
            // Use a real two-finger spread, exceeding Android's density-dependent minimum span.
            pinch(MotionEvent.ACTION_DOWN, 1, globe.width * .2f, 0)
            pinch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, globe.width * .2f, 20)
            pinch(MotionEvent.ACTION_MOVE, 2, globe.width * .4f, 40)
            pinch(MotionEvent.ACTION_MOVE, 2, globe.width * .6f, 60)
            pinch(MotionEvent.ACTION_MOVE, 2, globe.width * .8f, 80)
            pinch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, globe.width * .8f, 100)
            pinch(MotionEvent.ACTION_UP, 1, globe.width * .8f, 120)
            assertTrue("The card blocks globe zooming: distance ${globe.camera.distance}", globe.camera.distance < 7f)
            assertEquals(View.GONE, (field(activity, "eventCard") as View).visibility)
            val store = field(activity, "journeyStore") as JourneyStore
            store.advance("predict")
            invoke(activity, "showCompanionPanel", "Journey")
            invoke(activity, "closeCompanionPanel")
            assertEquals("predict", store.active()?.stepId)
            assertEquals(View.VISIBLE, (field(activity, "journeyResumeButton") as View).visibility)
            (field(activity, "journeyResumeButton") as View).performClick()
            assertEquals("predict", store.active()?.stepId)
        }
        settle()
        scenario.onActivity(::geometry)
        scenario.recreate()
        settle()
        scenario.onActivity { activity ->
            geometry(activity)
            assertEquals("predict", (field(activity, "journeyStore") as JourneyStore).active()?.stepId)
        }
    }

    @Test fun everyStepAndFeedbackKeepFixedControlsAndTheGlobeVisible() = withJourney { scenario ->
        for (journeyIndex in 0..2) {
            lateinit var journey: Journey
            scenario.onActivity { journey = JourneyContent.all(it)[journeyIndex]; invoke(it, "startJourney", journey, true) }
            for (step in journey.steps) {
                scenario.onActivity {
                    (field(it, "journeyStore") as JourneyStore).advance(step.id)
                    invoke(it, "showCompanionPanel", "Journey")
                }
                settle()
                scenario.onActivity { activity ->
                    geometry(activity)
                    val footer = field(activity, "journeyFooter") as View
                    if (step.kind != "choice") assertTrue("Missing persistent step action", descendants(footer)
                        .filterIsInstance<TextView>().any { it.text.toString() == step.action })
                }
            }
        }
        scenario.onActivity { activity ->
            val journey = JourneyContent.all(activity).first()
            invoke(activity, "startJourney", journey, true)
            (field(activity, "journeyStore") as JourneyStore).advance("predict")
            invoke(activity, "showCompanionPanel", "Journey")
            val step = journey.step("predict")!!
            descendants(field(activity, "companionColumn") as View).filterIsInstance<TextView>()
                .single { it.text.toString() == step.options[step.correct] }.performClick()
        }
        settle()
        scenario.onActivity(::geometry)
        scenario.recreate()
        settle()
        scenario.onActivity { activity ->
            geometry(activity)
            assertTrue("Feedback was lost during recreation", descendants(field(activity, "companionColumn") as View)
                .filterIsInstance<TextView>().any { it.text.toString() == activity.getString(R.string.text_that_s_the_pattern) })
            descendants(field(activity, "journeyFooter") as View).filterIsInstance<TextView>()
                .single { it.text.toString() == activity.getString(R.string.text_continue) }.performClick()
            assertEquals("advance", (field(activity, "journeyStore") as JourneyStore).active()?.stepId)
            invoke(activity, "showCompanionPanel", "Settings")
        }
        settle()
        scenario.onActivity { activity ->
            val globe = field(activity, "globeView") as View
            assertEquals((field(activity, "companionPanel") as View).height, globe.height)
        }
    }
}
