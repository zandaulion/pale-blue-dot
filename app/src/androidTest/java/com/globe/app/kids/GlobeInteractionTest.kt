package com.globe.app.kids

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.globe.app.GlobeSurfaceView
import com.globe.app.MainActivity
import com.globe.app.earth.SunPosition
import com.globe.app.data.EarthRepository
import com.globe.app.events.EarthEventsProvider
import com.globe.app.explore.FieldNotebook
import com.globe.app.places.PlaceStore
import com.globe.app.places.SavedPlace
import com.globe.app.render.LayerSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.TimeZone

/** Real gesture delivery with isolated discoveries/places; existing app preferences are restored. */
@RunWith(AndroidJUnit4::class)
class GlobeInteractionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val base get() = instrumentation.targetContext
    private val fixtureContext get() = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            instrumentation.context.getSharedPreferences("globe_interaction_$name", mode)
    }
    private val instant = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(2026, Calendar.JANUARY, 1, 12, 0)
    }.timeInMillis
    private fun field(activity: MainActivity, name: String) = MainActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }
    private fun view(activity: MainActivity) = field(activity, "globeView").get(activity) as GlobeSurfaceView
    private fun card(activity: MainActivity) = field(activity, "eventCard").get(activity) as TextView
    private fun closeCard(activity: MainActivity) = MainActivity::class.java
        .getDeclaredMethod("hideEventCard").apply { isAccessible = true }.invoke(activity)

    private fun configure(activity: MainActivity, day: Boolean = true) {
        val globe = view(activity)
        val repository = field(activity, "repository").get(activity) as EarthRepository
        @Suppress("UNCHECKED_CAST")
        val observer = field(activity, "repositoryObserver").get(activity) as (EarthRepository.Snapshot) -> Unit
        repository.removeObserver(observer) // Live refreshes must not replace synthetic test markers.
        field(activity, "journal").set(activity, DiscoveryJournal(fixtureContext))
        field(activity, "fieldNotebook").set(activity, FieldNotebook(fixtureContext))
        field(activity, "placeStore").set(activity, PlaceStore(fixtureContext))
        field(activity, "challengeKind").set(activity, null)
        val layers = LayerSettings(fixtureContext.getSharedPreferences("layers", Context.MODE_PRIVATE)).apply {
            iss = false; reduceMotion = true
        }
        field(activity, "layers").set(activity, layers)
        globe.renderer.applyLayers(layers)
        globe.sceneClock.enter(instant)
        globe.camera.reduceMotion = true
        val sun = SunPosition.calculate(instant)
        val sign = if (day) 1 else -1
        val az = Math.toDegrees(kotlin.math.atan2((sign * sun[0]).toDouble(), (sign * sun[2]).toDouble())).toFloat()
        val el = Math.toDegrees(kotlin.math.asin((sign * sun[1]).toDouble())).toFloat()
        globe.camera.restore(az, el, 4f)
        closeCard(activity)
    }

    private fun taskLock(command: String) {
        instrumentation.uiAutomation.executeShellCommand("am task lock $command").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }

    private fun withGlobe(block: (ActivityScenario<MainActivity>) -> Unit) {
        listOf("discoveries", "saved_places", "field_notebook_v1").forEach {
            fixtureContext.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        val prefs = base.getSharedPreferences("globe_prefs", Context.MODE_PRIVATE)
        val before = prefs.all
        prefs.edit().putBoolean("onboarded", true).putBoolean("narrate_enabled", false).putBoolean("device_narrate_enabled", false)
            .putBoolean("music_enabled", false).putBoolean("explore_time_active", false).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var taskId = 0
                scenario.onActivity { configure(it); taskId = it.taskId }
                // Keep system navigation from redirecting native input between dialogs.
                taskLock(taskId.toString())
                try {
                    waitForGlobe(scenario)
                    block(scenario)
                } finally { taskLock("stop") }
            }
        } finally {
            val edit = prefs.edit()
            (prefs.all.keys - before.keys).forEach(edit::remove)
            before.forEach { (key, value) -> when (value) {
                is Boolean -> edit.putBoolean(key, value)
                is Float -> edit.putFloat(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is String -> edit.putString(key, value)
                is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
            } }
            edit.commit()
        }
    }

    private fun waitForGlobe(scenario: ActivityScenario<MainActivity>) {
        repeat(50) {
            var ready = false
            scenario.onActivity { activity ->
                val globe = view(activity)
                ready = globe.width > 0 && globe.height > 0 && globe.hasWindowFocus() &&
                    globe.renderer.lastFrameTimeMs == instant &&
                    globe.renderer.pick(globe.width / 2f, globe.height / 2f, globe.width, globe.height) != null
            }
            if (ready) return
            Thread.sleep(100)
        }
        error("Globe did not complete a frame for the held test instant")
    }

    private fun settle() {
        instrumentation.waitForIdleSync()
        Thread.sleep(200)
    }
    private fun send(globe: GlobeSurfaceView, down: Long, action: Int, x: Float, y: Float) {
        MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
            try { globe.onTouchEvent(this) } finally { recycle() }
        }
    }
    private fun pointer(down: Long, action: Int, x: Float, y: Float) {
        MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
            try { instrumentation.sendPointerSync(this) } finally { recycle() }
        }
    }
    private fun center(scenario: ActivityScenario<MainActivity>, clearEvents: Boolean): FloatArray {
        val point = FloatArray(2)
        scenario.onActivity { activity ->
            val globe = view(activity)
            if (clearEvents) globe.renderer.earthEventsRenderer.setEvents(emptyList())
            val location = IntArray(2).also { globe.getLocationOnScreen(it) }
            point[0] = location[0] + globe.width / 2f
            point[1] = location[1] + globe.height / 2f
        }
        return point
    }
    private fun tap(scenario: ActivityScenario<MainActivity>, clearEvents: Boolean = true) {
        val point = center(scenario, clearEvents)
        val down = SystemClock.uptimeMillis()
        pointer(down, MotionEvent.ACTION_DOWN, point[0], point[1])
        pointer(down, MotionEvent.ACTION_UP, point[0], point[1])
        settle()
    }
    private fun hold(scenario: ActivityScenario<MainActivity>) {
        val point = center(scenario, true)
        val down = SystemClock.uptimeMillis()
        pointer(down, MotionEvent.ACTION_DOWN, point[0], point[1])
        Thread.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 150)
        pointer(down, MotionEvent.ACTION_UP, point[0], point[1])
        settle()
    }

    private fun skyPoint(scenario: ActivityScenario<MainActivity>): FloatArray {
        val point = FloatArray(2)
        scenario.onActivity { activity ->
            val globe = view(activity)
            val x = globe.width * 0.08f
            val y = globe.height * 0.08f
            assertNull("Sky test point must be beyond Earth", globe.renderer.pick(x, y, globe.width, globe.height))
            val location = IntArray(2).also { globe.getLocationOnScreen(it) }
            point[0] = location[0] + x
            point[1] = location[1] + y
        }
        return point
    }
    private fun skyGesture(scenario: ActivityScenario<MainActivity>, hold: Boolean) {
        val point = skyPoint(scenario)
        val down = SystemClock.uptimeMillis()
        pointer(down, MotionEvent.ACTION_DOWN, point[0], point[1])
        if (hold) Thread.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 150)
        pointer(down, MotionEvent.ACTION_UP, point[0], point[1])
        settle()
    }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureInteractions") != "true") return
        val output = java.io.File(base.getExternalFilesDir(null), "interaction-audit").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(output, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun dayAndNightAppearOnceAndHoldsReopenAfterRecreation() = withGlobe { scenario ->
        for (day in listOf(true, false)) {
            scenario.onActivity { configure(it, day) }; settle()
            tap(scenario)
            capture(if (day) "day-first" else "night-first")
            scenario.onActivity { activity ->
                val discovery = if (day) Discovery.DAYTIME else Discovery.NIGHT
                assertTrue(DiscoveryJournal(fixtureContext).isUnlocked(discovery))
                assertEquals(View.VISIBLE, card(activity).visibility)
                val original = card(activity).text.toString()
                val globe = view(activity)
                globe.renderer.earthEventsRenderer.setEvents(emptyList())
                val down = SystemClock.uptimeMillis()
                send(globe, down, MotionEvent.ACTION_DOWN, globe.width / 2f, globe.height / 2f)
                send(globe, down, MotionEvent.ACTION_UP, globe.width / 2f, globe.height / 2f)
                assertEquals("Repeated taps must not replace the discovery card", original, card(activity).text.toString())
                closeCard(activity)
            }
            repeat(3) { tap(scenario) }
            capture(if (day) "day-repeat-quiet" else "night-repeat-quiet")
            scenario.onActivity { assertEquals(View.GONE, card(it).visibility) }
            hold(scenario)
            capture(if (day) "day-hold" else "night-hold")
            scenario.onActivity {
                assertEquals(View.VISIBLE, card(it).visibility)
                assertEquals(View.VISIBLE, (field(it, "savePlaceButton").get(it) as View).visibility)
                assertNotNull(field(it, "pendingSavePoint").get(it))
                closeCard(it)
            }
        }
        scenario.recreate()
        scenario.onActivity { configure(it) }; waitForGlobe(scenario)
        tap(scenario)
        scenario.onActivity { assertEquals(View.GONE, card(it).visibility) }
        hold(scenario)
        scenario.onActivity { assertEquals(View.VISIBLE, card(it).visibility) }
    }

    @Test fun skyAppearsOnceAndHoldReopensAfterRecreation() = withGlobe { scenario ->
        val journal = DiscoveryJournal(fixtureContext)
        skyGesture(scenario, hold = false)
        var firstCard = ""
        scenario.onActivity { activity ->
            assertTrue(journal.isUnlocked(Discovery.STARGAZER))
            assertEquals(View.VISIBLE, card(activity).visibility)
            firstCard = card(activity).text.toString()
            assertTrue(card(activity).text.contains(activity.getString(
                com.globe.app.R.string.text_the_night_sky_beyond_earth_are_thousands_of_stars_planets_and_who)))
        }
        skyGesture(scenario, hold = false)
        scenario.onActivity { activity ->
            assertEquals(View.VISIBLE, card(activity).visibility)
            assertEquals("Repeated sky taps must leave the discovery card alone", firstCard, card(activity).text.toString())
            closeCard(activity)
        }
        repeat(3) { skyGesture(scenario, hold = false) }
        scenario.onActivity { assertEquals(View.GONE, card(it).visibility) }

        skyGesture(scenario, hold = true)
        scenario.onActivity { activity ->
            assertEquals(View.VISIBLE, card(activity).visibility)
            assertTrue(card(activity).text.contains(activity.getString(
                com.globe.app.R.string.text_the_night_sky_beyond_earth_are_thousands_of_stars_planets_and_who)))
            closeCard(activity)
        }
        scenario.recreate()
        scenario.onActivity { configure(it) }; waitForGlobe(scenario)
        skyGesture(scenario, hold = false)
        scenario.onActivity { assertEquals(View.GONE, card(it).visibility) }
        skyGesture(scenario, hold = true)
        scenario.onActivity { assertEquals(View.VISIBLE, card(it).visibility) }
    }

    @Test fun jitterDragPinchAndCancellationDoNotOpenLessons() = withGlobe { scenario ->
        val journal = DiscoveryJournal(fixtureContext)
        journal.unlock(Discovery.DAYTIME); journal.unlock(Discovery.NIGHT)
        val delay = ViewConfiguration.getLongPressTimeout().toLong() + 150
        var down = SystemClock.uptimeMillis()
        scenario.onActivity { activity ->
            val globe = view(activity); val x = globe.width / 2f; val y = globe.height / 2f
            val az = globe.camera.azimuth
            globe.renderer.earthEventsRenderer.setEvents(emptyList())
            val jitter = ViewConfiguration.get(activity).scaledTouchSlop / 3f
            send(globe, down, MotionEvent.ACTION_DOWN, x, y)
            send(globe, down, MotionEvent.ACTION_MOVE, x + jitter, y)
            send(globe, down, MotionEvent.ACTION_UP, x + jitter, y)
            assertEquals("Tap jitter must not impart camera momentum", az, globe.camera.azimuth, 0.001f)
        }
        down = SystemClock.uptimeMillis()
        scenario.onActivity { activity ->
            val globe = view(activity); val x = globe.width / 2f; val y = globe.height / 2f
            val az = globe.camera.azimuth
            send(globe, down, MotionEvent.ACTION_DOWN, x, y)
            send(globe, down, MotionEvent.ACTION_MOVE, x + ViewConfiguration.get(activity).scaledTouchSlop * 3, y)
            assertNotEquals(az, globe.camera.azimuth)
        }
        Thread.sleep(delay)
        scenario.onActivity { activity ->
            val globe = view(activity)
            assertEquals(View.GONE, card(activity).visibility)
            send(globe, down, MotionEvent.ACTION_UP, globe.width / 2f + 200, globe.height / 2f)
        }
        down = SystemClock.uptimeMillis()
        scenario.onActivity { activity ->
            val globe = view(activity); val x = globe.width / 2f; val y = globe.height / 2f
            send(globe, down, MotionEvent.ACTION_DOWN, x, y)
            val properties = Array(2) { id -> MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coordinates = Array(2) { id -> MotionEvent.PointerCoords().apply { this.x = x + id * 120; this.y = y; pressure = 1f; size = 1f } }
            MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                2, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0).apply {
                try { globe.onTouchEvent(this) } finally { recycle() }
            }
        }
        Thread.sleep(delay)
        scenario.onActivity { activity ->
            assertEquals(View.GONE, card(activity).visibility)
            send(view(activity), down, MotionEvent.ACTION_CANCEL, view(activity).width / 2f, view(activity).height / 2f)
        }
        down = SystemClock.uptimeMillis()
        scenario.onActivity { activity ->
            val globe = view(activity)
            send(globe, down, MotionEvent.ACTION_DOWN, globe.width / 2f, globe.height / 2f)
            send(globe, down, MotionEvent.ACTION_CANCEL, globe.width / 2f, globe.height / 2f)
        }
        Thread.sleep(delay)
        scenario.onActivity { assertEquals(View.GONE, card(it).visibility) }
    }

    private fun texts(node: AccessibilityNodeInfo?): List<String> = if (node == null) emptyList()
        else listOfNotNull(node.text?.toString()) + (0 until node.childCount).flatMap { texts(node.getChild(it)) }

    private fun awaitText(expected: String) {
        repeat(20) {
            if (texts(instrumentation.uiAutomation.rootInActiveWindow).any { it.contains(expected) }) return
            Thread.sleep(100)
        }
        fail("Expected synthetic dialog text: $expected")
    }

    @Test fun markersSavedPlacesAndChallengeAnswersKeepTheirTapActions() = withGlobe { scenario ->
        lateinit var point: DoubleArray
        scenario.onActivity { activity ->
            val globe = view(activity)
            point = requireNotNull(globe.renderer.pick(globe.width / 2f, globe.height / 2f, globe.width, globe.height))
            val event = EarthEventsProvider.Event("synthetic-observation", point[0], point[1], "Synthetic observation", EarthEventsProvider.Event.Type.EARTHQUAKE,
                instant, null, "Example source", null, 5f, null, emptyList(), 12f)
            globe.renderer.earthEventsRenderer.setEvents(listOf(event))
        }
        tap(scenario, clearEvents = false)
        capture("marker-tap")
        awaitText("Synthetic observation")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); settle()
        PlaceStore(fixtureContext).save(SavedPlace("synthetic-place", "Synthetic place", point[0], point[1], "Etc/UTC"))
        tap(scenario)
        capture("saved-place-tap")
        awaitText("Synthetic place")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); settle()
        scenario.onActivity { activity ->
            field(activity, "challengeKind").set(activity, ChallengeKind.DAYTIME)
            field(activity, "challengeScore").setInt(activity, 0)
            closeCard(activity)
        }
        hold(scenario)
        scenario.onActivity { assertEquals(0, field(it, "challengeScore").getInt(it)); assertEquals(View.GONE, card(it).visibility) }
        tap(scenario)
        scenario.onActivity { assertEquals(1, field(it, "challengeScore").getInt(it)); assertEquals(View.VISIBLE, card(it).visibility) }
    }
}
