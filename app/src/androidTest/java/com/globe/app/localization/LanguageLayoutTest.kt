package com.globe.app.localization

import android.annotation.TargetApi
import android.app.LocaleManager
import android.os.LocaleList
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.filters.SdkSuppress
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.globe.app.AppLanguage
import com.globe.app.MainActivity
import com.globe.app.R
import com.globe.app.explore.Journey
import com.globe.app.explore.JourneyContent
import com.globe.app.wallpaper.WallpaperConfigActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in physical-device audit. Screenshots stay in the test app's storage. */
@RunWith(AndroidJUnit4::class)
@TargetApi(33)
class LanguageLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val issues = linkedSetOf<String>()
    private val output by lazy {
        val scale = instrumentation.targetContext.resources.configuration.fontScale
        File(requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "language-layout-$scale").apply {
            check(isDirectory || mkdirs()) { "Cannot create screenshot directory" }
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun settle() {
        instrumentation.waitForIdleSync()
        Thread.sleep(300)
        instrumentation.waitForIdleSync()
    }

    private fun record(activity: Activity, name: String) {
        descendants(activity.window.decorView).filterIsInstance<TextView>().filter { it.isShown && it.text.isNotEmpty() }.forEach { view ->
            val layout = view.layout ?: return@forEach
            val height = view.height - view.compoundPaddingTop - view.compoundPaddingBottom
            if (layout.height > height + 2) issues += "$name: clipped height ${layout.height}/$height: ${view.text}"
            for (line in 0 until layout.lineCount) {
                if (layout.getLineMax(line) > layout.width + 2) issues += "$name: clipped width: ${view.text}"
                if (layout.getEllipsisCount(line) > 0) issues += "$name: ellipsis: ${view.text}"
            }
        }
        // The two independent controls at the top of the globe must never intersect.
        val views = descendants(activity.window.decorView).filterIsInstance<TextView>().filter { it.isShown }
        val time = views.firstOrNull {
            it.contentDescription == activity.getString(R.string.text_time_controls) ||
                it.contentDescription == activity.getString(R.string.text_explore_time_simulated_scene)
        }
        val settings = views.firstOrNull { it.contentDescription == activity.getString(R.string.companion_settings) }
        if (time != null && settings != null) {
            val a = Rect(); val b = Rect()
            time.getGlobalVisibleRect(a); settings.getGlobalVisibleRect(b)
            if (Rect.intersects(a, b)) issues += "$name: globe time overlaps Settings"
        }
    }

    private fun capture(name: String) {
        val committed = CountDownLatch(1)
        lateinit var activity: Activity
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
            activity.window.decorView.apply {
                viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                requestLayout()
                invalidate()
            }
        }
        check(committed.await(5, TimeUnit.SECONDS)) { "No completed UI frame: $name" }
        Thread.sleep(100)
        instrumentation.runOnMainSync { record(activity, name) }
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("No screenshot: $name")
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun panel(activity: MainActivity, name: String) {
        val method = MainActivity::class.java.getDeclaredMethod(
            if (name == "Globe") "closeCompanionPanel" else "showCompanionPanel",
            *if (name == "Globe") emptyArray() else arrayOf(String::class.java)
        ).apply { isAccessible = true }
        if (name == "Globe") method.invoke(activity) else method.invoke(activity, name)
    }

    @SdkSuppress(minSdkVersion = 33)
    @Test fun auditAllLanguages() {
        if (InstrumentationRegistry.getArguments().getString("languageLayoutAudit") != "true") return
        val manager = instrumentation.targetContext.getSystemService(LocaleManager::class.java)
        val original = manager.applicationLocales
        val tags = InstrumentationRegistry.getArguments().getString("auditTags")?.split(',') ?: AppLanguage.tags
        try {
            for (tag in tags) {
                instrumentation.runOnMainSync { manager.applicationLocales = LocaleList.forLanguageTags(tag) }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    Thread.sleep(2500)
                    settle()
                    scenario.onActivity { activity ->
                        assertEquals(tag, AppLanguage.selected(activity))
                        MainActivity::class.java.getDeclaredField("onboardingOverlay").apply { isAccessible = true }
                            .get(activity).let { (it as View).visibility = View.VISIBLE }
                    }
                    settle()
                    capture("$tag-onboarding")
                    scenario.onActivity { activity ->
                        MainActivity::class.java.getDeclaredField("onboardingOverlay").apply { isAccessible = true }
                            .get(activity).let { (it as View).visibility = View.GONE }
                    }
                    val compact = InstrumentationRegistry.getArguments().getString("auditCompact") == "true"
                    val panels = if (compact) listOf("Globe", "Settings", "Layers")
                        else listOf("Globe", "Settings", "Layers", "Explore", "Today", "Places", "Journeys", "Notebook", "Guide", "Explore time")
                    for (name in panels) {
                        scenario.onActivity { panel(it, name) }
                        settle()
                        val captureName = "$tag-${name.replace(' ', '-')}-top"
                        capture(captureName)
                        if (name != "Globe") {
                            scenario.onActivity { activity ->
                                descendants(activity.window.decorView).filterIsInstance<ScrollView>().filter { it.isShown }
                                    .forEach { it.fullScroll(View.FOCUS_DOWN) }
                            }
                            settle()
                            capture("$tag-${name.replace(' ', '-')}-bottom")
                        }
                    }
                    if (!compact) {
                        scenario.onActivity { activity ->
                            MainActivity::class.java.getDeclaredMethod("startJourney", Journey::class.java, Boolean::class.javaPrimitiveType)
                                .apply { isAccessible = true }.invoke(activity, JourneyContent.all(activity).first(), false)
                        }
                        settle()
                        capture("$tag-journey-step")
                        scenario.onActivity { activity ->
                            MainActivity::class.java.getDeclaredMethod("leaveJourney").apply { isAccessible = true }.invoke(activity)
                        }
                    }
                    scenario.onActivity { activity ->
                        MainActivity::class.java.getDeclaredMethod("enterExploreTime", Long::class.javaPrimitiveType)
                            .apply { isAccessible = true }.invoke(activity, System.currentTimeMillis() + 86_400_000L)
                        panel(activity, "Globe")
                    }
                    settle()
                    capture("$tag-simulated-time")
                    scenario.onActivity { activity ->
                        MainActivity::class.java.getDeclaredMethod("exitExploreTime").apply { isAccessible = true }.invoke(activity)
                        panel(activity, "Settings")
                        MainActivity::class.java.getDeclaredMethod("chooseLanguage").apply { isAccessible = true }.invoke(activity)
                    }
                    settle()
                    capture("$tag-language-picker")
                    instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                }
                ActivityScenario.launch(WallpaperConfigActivity::class.java).use { wallpaper ->
                    Thread.sleep(2500)
                    settle()
                    capture("$tag-wallpaper")
                }
            }
        } finally {
            instrumentation.runOnMainSync { manager.applicationLocales = original }
            File(output, "issues.txt").writeText(issues.joinToString("\n"))
        }
        assertTrue("Layout issues (${issues.size}); see issues.txt:\n${issues.take(12).joinToString("\n")}", issues.isEmpty())
    }
}
