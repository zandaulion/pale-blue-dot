package com.globe.app.kids

import android.content.Context
import android.content.res.Configuration
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.LocaleList
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Switch
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.globe.app.AppLanguage
import com.globe.app.MainActivity
import com.globe.app.R
import com.globe.app.events.EarthEventsProvider
import com.globe.app.explore.JourneyContent
import com.globe.app.explore.JourneyStore
import android.view.inspector.WindowInspector
import android.view.Window
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaliaNarrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val base get() = instrumentation.targetContext
    private fun context(tag: String): Context = base.createConfigurationContext(
        Configuration(base.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
    )
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun click(activity: MainActivity, resource: Int) {
        descendants(activity.window.decorView).filterIsInstance<TextView>()
            .single { it.text.toString() == activity.getString(resource) }.performClick()
    }
    private fun field(activity: MainActivity, name: String): Any = MainActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)!!

    @Test fun everyLanguageHasPlayableClipsMatchingTheDisplayedCards() {
        val resources = listOf(
            R.string.text_read_to_me_is_on_i_ll_read_the_cards_out_loud,
            R.string.text_the_night_sky_beyond_earth_are_thousands_of_stars_planets_and_who,
            R.string.text_it_s_daytime_here_this_side_of_earth_is_facing_the_sun_right_now,
            R.string.text_it_s_night_time_here_this_side_is_turned_away_from_the_sun_those,
            R.string.text_iss_day, R.string.text_iss_night,
            R.string.text_tap_on_the_earth_to_answer,
            R.string.text_yes_that_s_right,
            R.string.text_not_quite_take_another_look_and_try_again,
            R.string.text_challenge_stopped_play_again_anytime
        )
        for (tag in AppLanguage.tags) {
            val ctx = context(tag)
            val narrator = TaliaNarrator(ctx)
            val all = resources.flatMap { resource ->
                val text = ctx.getString(resource)
                val clip = requireNotNull(narrator.clipsFor(text)) { "$tag/$resource missing" }
                assertEquals(1, clip.size)
                val celebration = requireNotNull(narrator.clipsFor(ctx.getString(R.string.text_added_to_field_notebook_1_s, text)))
                assertEquals(listOf("added.mp3") + clip, celebration)
                clip
            }.toMutableSet()
            assertEquals(listOf("time_traveler.mp3"), narrator.clipsFor(
                "${Discovery.TIME_TRAVELER.emoji} ${Discovery.TIME_TRAVELER.title(ctx)}\n${Discovery.TIME_TRAVELER.fact(ctx)}"))
            assertNull(narrator.clipsFor(ctx.getString(R.string.text_great_job_challenges_solved_1_s, 123)))
            all += listOf("added.mp3", "time_traveler.mp3")
            for ((id, text) in NarrationCatalog.scripts(ctx)) {
                assertEquals("$tag/$id", listOf("$id.mp3"), narrator.clipsFor(text))
                all += "$id.mp3"
            }
            assertEquals(101, all.size)
            for (journey in JourneyContent.all(ctx)) {
                for (step in journey.steps) {
                    assertNotNull(narrator.clipsFor((listOf(step.title, step.instruction) + step.options).joinToString("\n")))
                    for ((title, message) in listOf(ctx.getString(R.string.text_that_s_the_pattern) to step.right,
                        ctx.getString(R.string.text_look_again) to step.wrong).filter { it.second.isNotBlank() }) {
                        assertNotNull(narrator.clipsFor("$title\n$message"))
                    }
                    if (step.kind == "advance") assertNotNull("$tag advance mismatch: " + ctx.getString(
                        R.string.text_the_scene_moved_1_s_hours_ahead_watch_the_changing_sunlight_on_th, step.hours), narrator.clipsFor(ctx.getString(
                        R.string.text_the_scene_moved_1_s_hours_ahead_watch_the_changing_sunlight_on_th, step.hours)))
                }
                assertNotNull(narrator.clipsFor(ctx.getString(R.string.text_1_s_is_in_your_field_notebook, journey.title)))
            }
            for (day in 0L..23L) assertNotNull(narrator.clipsFor(DailyFacts.today(ctx, day * 86_400_000L)))
            for (discovery in Discovery.values()) assertNotNull(narrator.clipsFor("${discovery.title(ctx)}\n${discovery.fact(ctx)}"))
            for (kind in ChallengeKind.values()) assertNotNull(narrator.clipsFor(kind.prompt(ctx)))
            for (clip in all) {
                MediaMetadataRetriever().use { reader ->
                    ctx.assets.openFd("narration/$tag/$clip").use { fd ->
                        reader.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    }
                    assertTrue("$tag/$clip has no audio", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
                    assertTrue("$tag/$clip is empty", requireNotNull(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong() > 500)
                }
            }
        }
        assertEquals("It's daytime. Read aloud.", NarrationText.clean("☀️ It\\'s daytime.\\nRead aloud."))
    }

    @Test fun firstLaunchChoicePersistsAndPlaybackStopsWhenLeavingOrSwitchingOff() {
        val prefs = base.getSharedPreferences("globe_prefs", Context.MODE_PRIVATE)
        val keys = listOf("onboarded", "narrate_enabled", "device_narrate_enabled")
        val before = keys.associateWith { if (prefs.contains(it)) prefs.getBoolean(it, false) else null }
        try {
            prefs.edit().putBoolean("onboarded", false).putBoolean("narrate_enabled", false).putBoolean("device_narrate_enabled", true).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertEquals(View.VISIBLE, (field(activity, "onboardingOverlay") as View).visibility)
                    click(activity, R.string.narration_explore_quietly)
                    assertFalse(prefs.getBoolean("narrate_enabled", true))
                    assertTrue(prefs.getBoolean("onboarded", false))
                    assertFalse(prefs.getBoolean("device_narrate_enabled", true))
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertEquals(View.GONE, (field(activity, "onboardingOverlay") as View).visibility)
                    assertEquals(false, field(activity, "narrateEnabled"))
                    assertEquals(false, field(activity, "deviceNarrateEnabled"))
                }
            }
            prefs.edit().putBoolean("onboarded", false).putBoolean("narrate_enabled", false).putBoolean("device_narrate_enabled", true).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> click(activity, R.string.narration_explore_with_talia) }
                Thread.sleep(500)
                scenario.onActivity { activity ->
                    assertTrue(prefs.getBoolean("narrate_enabled", false))
                    assertFalse(prefs.getBoolean("device_narrate_enabled", true))
                    val narrator = field(activity, "taliaNarrator") as TaliaNarrator
                    val player = TaliaNarrator::class.java.getDeclaredField("player").apply { isAccessible = true }.get(narrator) as MediaPlayer?
                    assertNotNull("Talia did not start after choosing narration", player)
                    assertTrue(requireNotNull(player).isPlaying)
                }
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                scenario.onActivity { activity ->
                    val narrator = field(activity, "taliaNarrator") as TaliaNarrator
                    assertNull(TaliaNarrator::class.java.getDeclaredField("player").apply { isAccessible = true }.get(narrator))
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertEquals(View.GONE, (field(activity, "onboardingOverlay") as View).visibility)
                    assertEquals(true, field(activity, "narrateEnabled"))
                    assertEquals(false, field(activity, "deviceNarrateEnabled"))
                    MainActivity::class.java.getDeclaredMethod("toggleNarration").apply { isAccessible = true }.invoke(activity)
                    assertFalse(prefs.getBoolean("narrate_enabled", true))
                    val narrator = field(activity, "taliaNarrator") as TaliaNarrator
                    assertNull(TaliaNarrator::class.java.getDeclaredField("player").apply { isAccessible = true }.get(narrator))
                }
            }
        } finally {
            val edit = prefs.edit()
            before.forEach { (key, value) -> if (value == null) edit.remove(key) else edit.putBoolean(key, value) }
            edit.commit()
        }
    }

    private class RecordingTts(context: Context) : TextToSpeech(context, null) {
        val spoken = mutableListOf<String>()
        override fun speak(text: CharSequence?, queueMode: Int, params: Bundle?, utteranceId: String?): Int {
            spoken += text.toString()
            return SUCCESS
        }
    }

    private fun withSavedVoicePreferences(block: (android.content.SharedPreferences) -> Unit) {
        val prefs = base.getSharedPreferences("globe_prefs", Context.MODE_PRIVATE)
        val keys = listOf("onboarded", "narrate_enabled", "device_narrate_enabled")
        val before = keys.associateWith { if (prefs.contains(it)) prefs.getBoolean(it, false) else null }
        try { block(prefs) } finally {
            val edit = prefs.edit()
            before.forEach { (key, value) -> if (value == null) edit.remove(key) else edit.putBoolean(key, value) }
            edit.commit()
        }
    }

    private fun invoke(activity: MainActivity, name: String, vararg args: Any) {
        val types = args.map { when (it) {
            is Boolean -> Boolean::class.javaPrimitiveType!!
            else -> it.javaClass
        } }.toTypedArray()
        MainActivity::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(activity, *args)
    }

    private fun player(activity: MainActivity): MediaPlayer? = TaliaNarrator::class.java.getDeclaredField("player")
        .apply { isAccessible = true }.get(field(activity, "taliaNarrator")) as MediaPlayer?

    private fun clickDialog(activity: MainActivity, resource: Int) {
        // A just-shown dialog is attached before its window-focus callback arrives.
        val button = WindowInspector.getGlobalWindowViews().asReversed().firstNotNullOf { window ->
            descendants(window).filterIsInstance<TextView>().singleOrNull {
                it.visibility == View.VISIBLE && it.text.toString() == activity.getString(resource)
            }
        }
        button.performClick()
    }

    private fun protect(activity: MainActivity) {
        val callback = activity.window.callback
        activity.window.callback = object : Window.Callback by callback {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean = true
        }
    }

    @Test fun everyEventExplanationReadsWithTaliaAndStopsOnDismissal() = withSavedVoicePreferences { prefs ->
        prefs.edit().putBoolean("onboarded", true).putBoolean("narrate_enabled", true)
            .putBoolean("device_narrate_enabled", false).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity(::protect)
            for (category in EarthEventsProvider.Event.Type.values()) {
                val event = EarthEventsProvider.Event("synthetic-${category.name}", 20.0, -15.0,
                    "Example observation", category, System.currentTimeMillis(), null,
                    "Sample data", null, null, null, emptyList(), 0.014f)
                scenario.onActivity { invoke(it, "showEventCard", event) }
                instrumentation.waitForIdleSync(); Thread.sleep(300)
                scenario.onActivity { clickDialog(it, R.string.companion_why) }
                instrumentation.waitForIdleSync(); Thread.sleep(600)
                scenario.onActivity { activity ->
                    assertTrue("Explanation lost playback after parent dismissed: $category", player(activity)?.isPlaying == true)
                    clickDialog(activity, R.string.text_read_aloud)
                    assertNotNull(player(activity))
                }
                instrumentation.waitForIdleSync(); Thread.sleep(300)
                scenario.onActivity { clickDialog(it, R.string.companion_close) }
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertNull(player(it)) }
            }
            scenario.onActivity { activity ->
                val recorder = RecordingTts(activity)
                (field(activity, "tts") as TextToSpeech).shutdown()
                MainActivity::class.java.getDeclaredField("tts").apply { isAccessible = true }.set(activity, recorder)
                MainActivity::class.java.getDeclaredField("ttsReady").apply { isAccessible = true }.setBoolean(activity, true)
                invoke(activity, "setNarrationEnabled", false)
                invoke(activity, "setDeviceNarrationEnabled", true)
                val event = EarthEventsProvider.Event("synthetic-quiet", 20.0, -15.0, "Example observation",
                    EarthEventsProvider.Event.Type.EARTHQUAKE, System.currentTimeMillis(), null,
                    "Sample data", null, null, null, emptyList(), 0.014f)
                invoke(activity, "showEventCard", event)
                assertFalse(recorder.spoken.isEmpty())
                recorder.spoken.clear()
                clickDialog(activity, R.string.companion_why)
                assertNull(player(activity))
                assertTrue("A fixed explanation must not use Android", recorder.spoken.isEmpty())
                clickDialog(activity, R.string.companion_close)
            }
        }
    }

    @Test fun journeyFeedbackAndReferenceReadActionsHaveRecordings() = withSavedVoicePreferences { prefs ->
        prefs.edit().putBoolean("onboarded", true).putBoolean("narrate_enabled", true)
            .putBoolean("device_narrate_enabled", false).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                protect(activity)
                Discovery.values().forEach { (field(activity, "journal") as DiscoveryJournal).unlock(it) }
                // Reference panels are quiet until the reader deliberately asks to hear a passage.
                for (panel in listOf("Guide", "Explore time", "Layers", "Today", "Notebook")) {
                    invoke(activity, "showCompanionPanel", panel)
                    assertNull(player(activity))
                    val readers = descendants(field(activity, "companionColumn") as View).filterIsInstance<TextView>()
                        .filter { it.text.toString() == activity.getString(R.string.text_read_aloud) }
                    assertTrue("No reading action in $panel", readers.isNotEmpty())
                    for (reader in readers) {
                        reader.performClick()
                        assertNotNull("Reading action has no matching recording: $panel", player(activity))
                    }
                    if (panel == "Today") {
                        val reading = player(activity)
                        invoke(activity, "showCompanionPanel", panel)
                        assertSame("Feed refresh interrupted a fixed daily fact", reading, player(activity))
                    }
                    invoke(activity, "closeCompanionPanel")
                    assertNull(player(activity))
                }
                invoke(activity, "showCompanionPanel", "Places")
                descendants(field(activity, "companionColumn") as View).filterIsInstance<TextView>().single {
                    it.text.toString() == activity.getString(R.string.text_add_a_place_from_the_globe)
                }.performClick()
                assertNotNull("Place-saving instructions are fixed educational text", player(activity))
                clickDialog(activity, R.string.text_ok)
                val journey = JourneyContent.all(activity).first()
                invoke(activity, "startJourney", journey, true)
                assertNotNull(player(activity))
                val store = field(activity, "journeyStore") as JourneyStore
                store.advance("predict")
                invoke(activity, "showCompanionPanel", "Journey")
                val step = journey.step("predict")!!
                descendants(field(activity, "companionColumn") as View).filterIsInstance<TextView>()
                    .single { it.text.toString() == step.options[step.correct] }.performClick()
                assertNotNull(player(activity))
                clickDialog(activity, R.string.text_continue)
            }
            instrumentation.waitForIdleSync(); Thread.sleep(600)
            scenario.onActivity { activity ->
                assertTrue("Continuing journey feedback stopped the next lesson", player(activity)?.isPlaying == true)
                invoke(activity, "leaveJourney")
                assertNull(player(activity))
                invoke(activity, "startChallenge")
                assertNotNull(player(activity))
                MainActivity::class.java.getDeclaredField("challengeScore").apply { isAccessible = true }.setInt(activity, 3)
                invoke(activity, "stopChallenge")
                assertNotNull("A scored challenge still needs Talia feedback", player(activity))
                invoke(activity, "hideEventCard")
                assertNull(player(activity))
            }
        }
    }

    @Test fun legacyNarrationEnablesOnlyTaliaAndSettingsPersistIndependently() = withSavedVoicePreferences { prefs ->
        prefs.edit().putBoolean("onboarded", true).putBoolean("narrate_enabled", true)
            .remove("device_narrate_enabled").commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(true, field(activity, "narrateEnabled"))
                assertEquals(false, field(activity, "deviceNarrateEnabled"))
                MainActivity::class.java.getDeclaredMethod("showCompanionPanel", String::class.java)
                    .apply { isAccessible = true }.invoke(activity, "Settings")
                fun switch(resource: Int) = descendants(activity.window.decorView).filterIsInstance<Switch>()
                    .single { it.text.toString().startsWith(activity.getString(resource) + "\n") }
                assertTrue(switch(R.string.narration_talia_label).isChecked)
                assertFalse(switch(R.string.narration_android_label).isChecked)
                switch(R.string.narration_android_label).performClick()
                assertTrue(prefs.getBoolean("narrate_enabled", false))
                assertTrue(prefs.getBoolean("device_narrate_enabled", false))
                switch(R.string.narration_talia_label).performClick()
                assertFalse(prefs.getBoolean("narrate_enabled", true))
                assertTrue(prefs.getBoolean("device_narrate_enabled", false))
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(false, field(activity, "narrateEnabled"))
                assertEquals(true, field(activity, "deviceNarrateEnabled"))
            }
        }
    }

    @Test fun voicesRouteIndependentlyAndMissingRecordingsNeverUseAndroid() = withSavedVoicePreferences { prefs ->
        prefs.edit().putBoolean("onboarded", true).putBoolean("narrate_enabled", false)
            .putBoolean("device_narrate_enabled", false).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var recorder: RecordingTts
            scenario.onActivity { activity ->
                recorder = RecordingTts(activity)
                val ttsField = MainActivity::class.java.getDeclaredField("tts").apply { isAccessible = true }
                (ttsField.get(activity) as TextToSpeech).shutdown()
                ttsField.set(activity, recorder)
                MainActivity::class.java.getDeclaredField("ttsReady").apply { isAccessible = true }.setBoolean(activity, true)
                val say = MainActivity::class.java.getDeclaredMethod("speak", String::class.java).apply { isAccessible = true }
                val natural = MainActivity::class.java.getDeclaredField("narrateEnabled").apply { isAccessible = true }
                val device = MainActivity::class.java.getDeclaredField("deviceNarrateEnabled").apply { isAccessible = true }
                val narrator = field(activity, "taliaNarrator") as TaliaNarrator
                val player = TaliaNarrator::class.java.getDeclaredField("player").apply { isAccessible = true }
                val lesson = activity.getString(R.string.text_it_s_daytime_here_this_side_of_earth_is_facing_the_sun_right_now)
                val facts = "Example observation: magnitude 5.2."
                for (taliaOn in listOf(false, true)) for (androidOn in listOf(false, true)) {
                    natural.setBoolean(activity, taliaOn); device.setBoolean(activity, androidOn)
                    narrator.stop(); recorder.spoken.clear()
                    say.invoke(activity, lesson)
                    assertEquals(taliaOn, player.get(narrator) != null)
                    assertTrue("A recorded lesson must never use Android", recorder.spoken.isEmpty())
                    say.invoke(activity, facts)
                    assertNull(player.get(narrator))
                    assertEquals(if (androidOn) listOf(facts) else emptyList<String>(), recorder.spoken)
                }
                natural.setBoolean(activity, true); device.setBoolean(activity, true)
                @Suppress("UNCHECKED_CAST")
                val index = (TaliaNarrator::class.java.getDeclaredField("index\$delegate")
                    .apply { isAccessible = true }.get(narrator) as Lazy<Map<String, String>>).value as MutableMap<String, String>
                val key = NarrationText.clean(lesson)
                val original = index.getValue(key)
                try {
                    index[key] = "missing-test-recording.mp3"
                    recorder.spoken.clear()
                    say.invoke(activity, lesson)
                    assertNull(player.get(narrator))
                    assertTrue("Playback failure must stay silent", recorder.spoken.isEmpty())
                } finally { index[key] = original }
            }
        }
    }

}
