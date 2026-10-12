package com.globe.app.explore

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.LocaleList
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.Switch
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.globe.app.AppLanguage
import com.globe.app.GlobeSurfaceView
import com.globe.app.MainActivity
import com.globe.app.R
import com.globe.app.render.LayerSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LatitudeGuidesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun field(activity: MainActivity, name: String): Any = MainActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)!!
    private fun invoke(activity: MainActivity, name: String, vararg args: Any) {
        val types = args.map { when (it) {
            is Boolean -> Boolean::class.javaPrimitiveType!!
            is Long -> Long::class.javaPrimitiveType!!
            else -> it.javaClass
        } }.toTypedArray()
        MainActivity::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(activity, *args)
    }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun guideSwitch(activity: MainActivity) = descendants(field(activity, "companionColumn") as View)
        .filterIsInstance<Switch>().single { it.text.startsWith(activity.getString(R.string.layer_latitude_guides) + "\n") }
    private fun restore(prefs: SharedPreferences, values: Map<String, *>) {
        val editor = prefs.edit().clear()
        values.forEach { (key, value) -> when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Float -> editor.putFloat(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        } }
        editor.commit()
    }
    private fun withPreferences(block: () -> Unit) {
        val prefs = listOf("globe_prefs", "journeys_v1").map { context.getSharedPreferences(it, Context.MODE_PRIVATE) }
        val before = prefs.map { it.all }
        prefs.first().edit().putBoolean("onboarded", true).putBoolean("narrate_enabled", false)
            .putBoolean("device_narrate_enabled", false).putBoolean("music_enabled", false)
            .putBoolean("explore_time_active", false).putBoolean("layer_latitude_guides", false).commit()
        try { block() } finally { prefs.zip(before).forEach { (store, values) -> restore(store, values) } }
    }
    private fun settle() { instrumentation.waitForIdleSync(); Thread.sleep(500) }
    private fun pin(command: String) {
        instrumentation.uiAutomation.executeShellCommand("am task lock $command").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
    private fun withActivity(block: (ActivityScenario<MainActivity>) -> Unit) = withPreferences {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val globe = field(activity, "globeView") as GlobeSurfaceView
                globe.camera.restore(42f, 15f, 4f)
                globe.camera.reduceMotion = true
                globe.renderer.showPlacePins = false
                pin(activity.taskId.toString())
            }
            try { settle(); block(scenario) } finally { pin("stop"); Thread.sleep(1000) }
        }
    }
    private fun assertGuides(activity: MainActivity, enabled: Boolean) {
        assertEquals(enabled, (field(activity, "layers") as LayerSettings).latitudeGuides)
        assertEquals(enabled, (field(activity, "globeView") as GlobeSurfaceView).renderer.showLatitudeGuides)
    }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureLatitudeGuides") != "true" &&
            InstrumentationRegistry.getArguments().getString("latitudeGuidesAudit") != "true") return
        settle()
        val directory = File(context.getExternalFilesDir(null), "latitude-guides-audit").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun migrationPreservesLegacyVisibilityOnlyOnce() {
        val prefs = instrumentation.context.getSharedPreferences("isolated_latitude_layers", Context.MODE_PRIVATE)
        try {
            prefs.edit().clear().commit()
            assertFalse(LayerSettings(prefs).latitudeGuides)
            prefs.edit().clear().putBoolean("explore_time_active", true).commit()
            val settings = LayerSettings(prefs)
            assertTrue(settings.latitudeGuides)
            settings.latitudeGuides = false
            assertFalse(LayerSettings(prefs).latitudeGuides)
            settings.latitudeGuides = true
            prefs.edit().putBoolean("explore_time_active", false).commit()
            assertTrue(LayerSettings(prefs).latitudeGuides)
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun switchPersistsAcrossRecreationAndTimeModeChanges() = withActivity { scenario ->
        val deadline = android.os.SystemClock.uptimeMillis() + 15_000L
        var rendered = false
        while (!rendered && android.os.SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { rendered = (field(it, "globeView") as GlobeSurfaceView).renderer.lastFrameTimeMs != 0L }
            if (!rendered) Thread.sleep(100)
        }
        assertTrue("The scene should render before captures", rendered)
        Thread.sleep(2500) // Let Android's transient app-pinned toast disappear.
        capture("real-off")
        scenario.onActivity { activity ->
            invoke(activity, "showCompanionPanel", "Layers")
            guideSwitch(activity).performClick()
            assertGuides(activity, true)
            assertFalse((field(activity, "globeView") as GlobeSurfaceView).sceneClock.isExploring)
            invoke(activity, "closeCompanionPanel")
        }
        capture("real-on")
        val heldTime = System.currentTimeMillis() - 3_600_000L
        scenario.onActivity { activity ->
            invoke(activity, "enterExploreTime", heldTime)
            assertGuides(activity, true)
            invoke(activity, "showCompanionPanel", "Layers")
            guideSwitch(activity).performClick()
            assertGuides(activity, false)
            val clock = (field(activity, "globeView") as GlobeSurfaceView).sceneClock
            assertTrue(clock.isExploring); assertEquals(heldTime, clock.nowMs())
            invoke(activity, "closeCompanionPanel")
        }
        capture("simulated-off")
        scenario.recreate(); settle()
        scenario.onActivity { activity ->
            assertGuides(activity, false)
            val globe = field(activity, "globeView") as GlobeSurfaceView
            assertTrue(globe.sceneClock.isExploring)
            assertEquals(heldTime, globe.sceneClock.nowMs())
            globe.camera.restore(42f, 15f, 4f)
            globe.camera.reduceMotion = true
            globe.renderer.showPlacePins = false
            invoke(activity, "showCompanionPanel", "Layers")
            guideSwitch(activity).performClick()
            assertGuides(activity, true)
            assertEquals(heldTime, globe.sceneClock.nowMs())
            invoke(activity, "closeCompanionPanel")
        }
        capture("simulated-on")
        scenario.onActivity { activity ->
            invoke(activity, "exitExploreTime")
            assertGuides(activity, true)
            assertFalse((field(activity, "globeView") as GlobeSurfaceView).sceneClock.isExploring)
        }
        scenario.recreate(); settle()
        scenario.onActivity { assertGuides(it, true) }
    }

    @Test fun seasonsJourneyAndSceneRestorationRespectTheCurrentSwitch() = withActivity { scenario ->
        scenario.onActivity { activity ->
            val journey = JourneyContent.all(activity).single { it.id == "understand_seasons" }
            invoke(activity, "startJourney", journey, true)
            assertGuides(activity, false)
            invoke(activity, "advanceJourney")
            assertGuides(activity, false)
            invoke(activity, "showCompanionPanel", "Layers")
            guideSwitch(activity).performClick()
            assertGuides(activity, true)
            invoke(activity, "resumeJourney")
            assertGuides(activity, true)
            invoke(activity, "advanceJourney")
            assertGuides(activity, true)
            invoke(activity, "leaveJourney")
            assertGuides(activity, true)
            invoke(activity, "startJourney", journey, true)
            invoke(activity, "showCompanionPanel", "Layers")
            guideSwitch(activity).performClick()
            invoke(activity, "resumeJourney")
            assertGuides(activity, false)
            repeat(journey.steps.size) { invoke(activity, "advanceJourney") }
            assertNull((field(activity, "journeyStore") as JourneyStore).active())
            assertGuides(activity, false)
        }
    }

    @SdkSuppress(minSdkVersion = 33)
    @Test fun auditTranslatedSwitch() {
        if (InstrumentationRegistry.getArguments().getString("latitudeGuidesAudit") != "true") return
        val manager = context.getSystemService(LocaleManager::class.java)
        val original = manager.applicationLocales
        try {
            for (tag in AppLanguage.tags) {
                instrumentation.runOnMainSync { manager.applicationLocales = LocaleList.forLanguageTags(tag) }
                instrumentation.waitForIdleSync(); Thread.sleep(500)
                withActivity { scenario ->
                    scenario.recreate(); settle()
                    scenario.onActivity { activity ->
                        assertEquals(tag, activity.resources.configuration.locales[0].toLanguageTag())
                        invoke(activity, "showCompanionPanel", "Layers")
                    }
                    settle()
                    scenario.onActivity { activity ->
                        val control = guideSwitch(activity)
                        (field(activity, "companionScroll") as ScrollView).scrollTo(0, control.top)
                    }
                    settle()
                    scenario.onActivity { activity ->
                        val control = guideSwitch(activity)
                        val local = Rect().also { assertTrue(control.getLocalVisibleRect(it)) }
                        assertEquals(Rect(0, 0, control.width, control.height), local)
                        assertTrue(control.height >= 48 * activity.resources.displayMetrics.density)
                        val layout = requireNotNull(control.layout)
                        assertTrue(layout.height <= control.height - control.compoundPaddingTop - control.compoundPaddingBottom + 2)
                        for (line in 0 until layout.lineCount) {
                            assertTrue(layout.getLineMax(line) <= layout.width + 2)
                            assertEquals(0, layout.getEllipsisCount(line))
                        }
                        control.performClick()
                        assertGuides(activity, true)
                    }
                    capture("$tag-layers")
                }
            }
        } finally { instrumentation.runOnMainSync { manager.applicationLocales = original } }
    }
}
