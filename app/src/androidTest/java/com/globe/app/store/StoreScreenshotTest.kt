package com.globe.app.store

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.graphics.Bitmap
import android.os.LocaleList
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.Window
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inspector.WindowInspector
import android.widget.TextView
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.globe.app.GlobeSurfaceView
import com.globe.app.MainActivity
import com.globe.app.data.EarthRepository
import com.globe.app.earth.SunPosition
import com.globe.app.events.EarthEventsProvider
import com.globe.app.moon.MoonGuide
import com.globe.app.moon.MoonPosition
import com.globe.app.places.PlaceStore
import com.globe.app.places.SavedPlace
import com.globe.app.wallpaper.WallpaperConfigActivity
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/** Opt-in store captures. Seeds synthetic places and reports; restores preferences and locale. */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun field(activity: MainActivity, name: String) = MainActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)!!
    private fun globe(activity: MainActivity) = field(activity, "globeView") as GlobeSurfaceView
    private fun invoke(activity: MainActivity, name: String, vararg args: Any) {
        val types = args.map { when (it) {
            is Double -> Double::class.javaPrimitiveType!!
            is Long -> Long::class.javaPrimitiveType!!
            else -> it.javaClass
        } }.toTypedArray()
        MainActivity::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(activity, *args)
    }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun taskLock(command: String) {
        instrumentation.uiAutomation.executeShellCommand("am task lock $command").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
    private fun protect(activity: Activity) {
        val callback = activity.window.callback
        activity.window.callback = object : Window.Callback by callback {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean = true
        }
    }
    private fun immersive(activity: Activity) {
        activity.window.insetsController?.hide(WindowInsets.Type.systemBars())
    }
    private fun settle() { instrumentation.waitForIdleSync(); Thread.sleep(1400) }
    private fun capture(activity: Activity, directory: File, name: String, dialog: Boolean = false) {
        instrumentation.runOnMainSync {
            immersive(activity)
            if (dialog) WindowInspector.getGlobalWindowViews().forEach {
                it.windowInsetsController?.hide(WindowInsets.Type.systemBars())
            }
        }
        settle()
        // Android's first-use immersive hint owns focus until its explicit acknowledgement.
        val root = instrumentation.uiAutomation.rootInActiveWindow
        if (root?.packageName?.toString() == "com.android.systemui" &&
            root.findAccessibilityNodeInfosByText("Viewing full screen").isNotEmpty()) {
            val button = root.findAccessibilityNodeInfosByText("Got it").single()
            check(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            settle()
        }
        val diagnostic = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { diagnostic.compress(Bitmap.CompressFormat.PNG, 100, it) }
        diagnostic.recycle()
        instrumentation.runOnMainSync {
            val windows = if (dialog) WindowInspector.getGlobalWindowViews() else listOf(activity.window.decorView)
            check(windows.any { it.hasWindowFocus() }) { "App lost focus: $name" }
            windows.flatMap(::descendants).filterIsInstance<TextView>()
                .filter { it.isShown && it.text.isNotEmpty() }.forEach { view ->
                    val layout = view.layout ?: return@forEach
                    check(layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom + 2) {
                        "Clipped text height in $name: ${view.text}"
                    }
                    for (line in 0 until layout.lineCount) {
                        check(layout.getLineMax(line) <= layout.width + 2) { "Clipped text width in $name" }
                        check(layout.getEllipsisCount(line) == 0) { "Truncated text in $name" }
                    }
                }
        }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun date(month: Int, day: Int) = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(2026, month, day, 12, 0)
    }.timeInMillis
    private fun newMoonTime(): Long {
        val start = date(Calendar.OCTOBER, 1)
        return (0L..720L).map { start + it * 3_600_000L }.minBy {
            MoonGuide.illumination(MoonPosition.calculate(it), SunPosition.calculate(it))
        }
    }
    private fun restorePreferences(name: String, values: Map<String, *>) {
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        values.forEach { (key, value) -> when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Float -> editor.putFloat(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        } }
        check(editor.commit())
    }
    @SdkSuppress(minSdkVersion = 33)
    @Test fun captureStoreScreens() {
        if (InstrumentationRegistry.getArguments().getString("storeScreenshots") != "true") return
        val profile = requireNotNull(InstrumentationRegistry.getArguments().getString("storeProfile"))
        require(profile in listOf("phone", "tablet-7", "tablet-10"))
        val directory = File(context.getExternalFilesDir(null), "store-screenshots/$profile").apply { mkdirs() }
        val names = File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty()
            .filter { it.extension == "xml" }.map { it.nameWithoutExtension }.toSet() +
            setOf("globe_prefs", "saved_places", "discoveries", "field_notebook_v1", "wallpaper_prefs")
        val preferences = names.associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all }
        val localeManager = context.getSystemService(LocaleManager::class.java)
        val originalLocale = localeManager.applicationLocales
        val originalTimeZone = TimeZone.getDefault()
        try {
            // Deliberately fixed display timezone; never expose a device/user timezone preference.
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            names.forEach { restorePreferences(it, emptyMap<String, Any>()) }
            context.getSharedPreferences("globe_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("onboarded", true).putBoolean("narrate_enabled", false).putBoolean("device_narrate_enabled", false)
                .putBoolean("music_enabled", false).putBoolean("reduce_motion", true)
                .putBoolean("layer_aurora", false).commit()
            // Explicitly fictional place; never reuse a person's saved places or coordinates.
            PlaceStore(context).save(SavedPlace("synthetic-place", "Sample place", 20.0, -15.0, "Etc/UTC"))
            instrumentation.runOnMainSync { localeManager.applicationLocales = LocaleList.forLanguageTags("en-US") }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var activity: MainActivity
                scenario.onActivity { activity = it; immersive(it); protect(it) }
                taskLock(activity.taskId.toString())
                try {
                    settle()
                    scenario.onActivity {
                        val repository = field(it, "repository") as EarthRepository
                        @Suppress("UNCHECKED_CAST")
                        val observer = field(it, "repositoryObserver") as (EarthRepository.Snapshot) -> Unit
                        repository.removeObserver(observer)
                        (field(it, "onboardingOverlay") as View).visibility = View.GONE
                        val view = globe(it)
                        view.camera.reduceMotion = true
                        view.camera.restore(-65f, 20f, if (profile == "phone") 8.5f else 5.4f)
                        view.renderer.setAllEvents(emptyList())
                        view.renderer.setPlaces(emptyList())
                        view.sceneClock.exit()
                        invoke(it, "updateTimeLabel")
                    }
                    capture(activity, directory, "01-real-time-earth")
                    // Fictional event, clearly labelled in its actual native information card.
                    val now = System.currentTimeMillis()
                    val event = EarthEventsProvider.Event("synthetic-earthquake", 20.0, -15.0,
                        "Example earthquake", EarthEventsProvider.Event.Type.EARTHQUAKE,
                        now, null, "Sample data · illustration", null, 5.2f, 18f, emptyList(), 0.014f)
                    scenario.onActivity { invoke(it, "showEventCard", event) }
                    capture(activity, directory, "02-earth-events", dialog = true)
                    instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    settle()
                    scenario.onActivity { invoke(it, "showDayNightCard", 20.0, -15.0) }
                    capture(activity, directory, "03-day-or-night")
                    scenario.onActivity {
                        invoke(it, "hideEventCard")
                        invoke(it, "enterExploreTime", date(Calendar.JUNE, 21))
                        // Show the explanatory diagram and hemisphere examples on shorter tablet windows.
                        if (profile != "phone") (field(it, "companionScroll") as ScrollView).post {
                            (field(it, "companionScroll") as ScrollView).fullScroll(View.FOCUS_DOWN)
                        }
                    }
                    capture(activity, directory, "04-seasons")
                    scenario.onActivity {
                        invoke(it, "closeCompanionPanel")
                        val view = globe(it)
                        view.sceneClock.enter(newMoonTime())
                        view.renderer.showLatitudeGuides = false
                        view.renderer.showEvents = false
                        val moon = MoonPosition.calculate(view.sceneClock.nowMs())
                        view.camera.faceSky(moon[0], moon[1], moon[2], aboveGlobe = true)
                        invoke(it, "updateTimeLabel")
                    }
                    Thread.sleep(3000)
                    capture(activity, directory, "05-night-sky")
                    scenario.onActivity {
                        val view = globe(it)
                        view.sceneClock.exit()
                        view.camera.restore(-65f, 20f, if (profile == "phone") 8.5f else 5.4f)
                        invoke(it, "updateTimeLabel")
                        invoke(it, "showCompanionPanel", "Settings")
                    }
                    capture(activity, directory, "06-languages-and-narration")
                    scenario.onActivity { invoke(it, "showCompanionPanel", "Today") }
                    capture(activity, directory, "08-today")
                } finally { taskLock("stop") }
            }
            // App preview, rather than a fabricated launcher/widget montage.
            ActivityScenario.launch(WallpaperConfigActivity::class.java).use { scenario ->
                lateinit var activity: WallpaperConfigActivity
                scenario.onActivity { activity = it; immersive(it); protect(it) }
                taskLock(activity.taskId.toString())
                try {
                    Thread.sleep(10000) // Let Android's task-pinning toast disappear before capture.
                    capture(activity, directory, "07-live-wallpaper")
                } finally { taskLock("stop") }
            }
        } finally {
            TimeZone.setDefault(originalTimeZone)
            preferences.forEach { (name, values) -> restorePreferences(name, values) }
            instrumentation.runOnMainSync { localeManager.applicationLocales = originalLocale }
        }
    }
}
