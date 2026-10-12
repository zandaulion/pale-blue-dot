package com.globe.app.kids

import android.app.LocaleManager
import android.content.Context
import android.graphics.Bitmap
import android.os.LocaleList
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.globe.app.AppLanguage
import com.globe.app.MainActivity
import com.globe.app.R
import com.globe.app.events.EarthEventsProvider
import com.globe.app.explore.JourneyContent
import com.globe.app.explore.JourneyStore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in captures of the newly narrated UI, including actual event explanation buttons. */
@RunWith(AndroidJUnit4::class)
class LearningNarrationLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun field(activity: MainActivity, name: String): Any = MainActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)!!
    private fun invoke(activity: MainActivity, name: String, vararg args: Any) {
        val types = args.map { if (it is Boolean) Boolean::class.javaPrimitiveType!! else it.javaClass }.toTypedArray()
        MainActivity::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(activity, *args)
    }
    private fun taskLock(command: String) {
        instrumentation.uiAutomation.executeShellCommand("am task lock $command").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
    private fun clickDialog(activity: MainActivity, resource: Int) {
        WindowInspector.getGlobalWindowViews().asReversed().firstNotNullOf { window ->
            descendants(window).filterIsInstance<TextView>().singleOrNull {
                it.visibility == View.VISIBLE && it.text.toString() == activity.getString(resource)
            }
        }.performClick()
    }
    private fun capture(directory: File, name: String, scenario: ActivityScenario<MainActivity>) {
        instrumentation.waitForIdleSync(); Thread.sleep(500)
        scenario.onActivity { activity ->
            if (InstrumentationRegistry.getArguments().getString("auditLandscape") == "true") {
                val window = activity.window.decorView
                check(window.width > window.height) { "Landscape capture returned to portrait: $name" }
            }
            if (!name.endsWith("-why")) {
                val globe = field(activity, "globeView") as View
                val card = field(activity, "journeyCard") as View
                val globeBounds = android.graphics.Rect().also { check(globe.getGlobalVisibleRect(it)) }
                val cardBounds = android.graphics.Rect().also { check(card.getGlobalVisibleRect(it)) }
                // Local visibility avoids mixing window/root and physical-screen coordinates
                // when Android offsets a landscape window around the display cutout.
                val localVisible = android.graphics.Rect().also { check(card.getLocalVisibleRect(it)) }
                check(localVisible == android.graphics.Rect(0, 0, card.width, card.height)) {
                    "Journey card clipped: $name; visible=$localVisible; size=${card.width}x${card.height}"
                }
                check(!android.graphics.Rect.intersects(globeBounds, cardBounds)) { "Globe covered by journey: $name" }
                check(globe.height >= (field(activity, "companionPanel") as View).height / 4) { "Globe too small: $name" }
            }
            val windows = WindowInspector.getGlobalWindowViews().filter { it.hasWindowFocus() }
            check(windows.isNotEmpty()) { "App lost focus: $name" }
            windows.flatMap(::descendants).filterIsInstance<TextView>()
                .filter { it.isShown && it.text.isNotBlank() }.forEach { view ->
                    val layout = view.layout ?: return@forEach
                    check(layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom + 2) {
                        "Clipped text height: $name"
                    }
                    for (line in 0 until layout.lineCount) {
                        check(layout.getLineMax(line) <= layout.width + 2 && layout.getEllipsisCount(line) == 0) {
                            "Clipped text width: $name"
                        }
                    }
                }
        }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @SdkSuppress(minSdkVersion = 33)
    @Test fun auditFixedLearningSurfaces() {
        if (InstrumentationRegistry.getArguments().getString("learningNarrationAudit") != "true") return
        val prefs = context.getSharedPreferences("globe_prefs", Context.MODE_PRIVATE)
        val keys = listOf("onboarded", "narrate_enabled", "device_narrate_enabled")
        val before = keys.associateWith { if (prefs.contains(it)) prefs.getBoolean(it, false) else null }
        val manager = context.getSystemService(LocaleManager::class.java)
        val original = manager.applicationLocales
        val directory = File(context.getExternalFilesDir(null), "learning-narration-audit").apply { mkdirs() }
        try {
            prefs.edit().putBoolean("onboarded", true).putBoolean("narrate_enabled", true)
                .putBoolean("device_narrate_enabled", false).commit()
            val tags = InstrumentationRegistry.getArguments().getString("auditTags")?.split(',') ?: AppLanguage.tags
            for (tag in tags) {
                instrumentation.runOnMainSync { manager.applicationLocales = LocaleList.forLanguageTags(tag) }
                instrumentation.waitForIdleSync(); Thread.sleep(500)
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    var localeReady = false
                    for (attempt in 0 until 30) {
                        scenario.onActivity { localeReady = it.resources.configuration.locales[0].toLanguageTag() == tag }
                        if (localeReady) break
                        Thread.sleep(100)
                    }
                    check(localeReady) { "Requested language not applied: $tag" }
                    // Rebuild views after locale delivery, avoiding labels from an earlier configuration.
                    scenario.recreate()
                    instrumentation.waitForIdleSync()
                    if (InstrumentationRegistry.getArguments().getString("auditLandscape") == "true") {
                        scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                        instrumentation.waitForIdleSync()
                    }
                    lateinit var activity: MainActivity
                    scenario.onActivity { activity = it }
                    taskLock(activity.taskId.toString())
                    try {
                        Thread.sleep(1500)
                        val event = EarthEventsProvider.Event("synthetic-audit", 20.0, -15.0,
                            "Example observation", EarthEventsProvider.Event.Type.EARTHQUAKE,
                            System.currentTimeMillis(), null, "Sample data", null, null, null, emptyList(), 0.014f)
                        scenario.onActivity { invoke(it, "showEventCard", event) }
                        instrumentation.waitForIdleSync(); Thread.sleep(300)
                        scenario.onActivity { clickDialog(it, R.string.companion_why) }
                        capture(directory, "$tag-why", scenario)
                        scenario.onActivity { clickDialog(it, R.string.companion_close) }
                        lateinit var journey: com.globe.app.explore.Journey
                        scenario.onActivity {
                            journey = JourneyContent.all(it).first()
                            invoke(it, "startJourney", journey, true)
                            (field(it, "journeyStore") as JourneyStore).advance("predict")
                            invoke(it, "showCompanionPanel", "Journey")
                        }
                        capture(directory, "$tag-journey", scenario)
                        scenario.onActivity { (field(it, "companionScroll") as ScrollView).fullScroll(View.FOCUS_DOWN) }
                        capture(directory, "$tag-journey-bottom", scenario)
                        scenario.onActivity {
                            val step = journey.step("predict")!!
                            descendants(field(it, "companionColumn") as View).filterIsInstance<TextView>()
                                .single { view -> view.text.toString() == step.options[step.correct] }.performClick()
                        }
                        capture(directory, "$tag-feedback", scenario)
                        scenario.onActivity { clickDialog(it, R.string.text_continue); invoke(it, "leaveJourney") }
                    } finally { taskLock("stop"); Thread.sleep(1000) }
                }
            }
        } finally {
            instrumentation.runOnMainSync { manager.applicationLocales = original }
            val editor = prefs.edit()
            before.forEach { (key, value) -> if (value == null) editor.remove(key) else editor.putBoolean(key, value) }
            editor.commit()
        }
    }
}
