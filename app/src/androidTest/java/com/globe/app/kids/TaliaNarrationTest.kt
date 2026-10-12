package com.globe.app.kids

import android.content.Context
import android.content.res.Configuration
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.LocaleList
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.globe.app.AppLanguage
import com.globe.app.MainActivity
import com.globe.app.R
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
            val narrator = TaliaNarrator(ctx) { fail("Unexpected fallback") }
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
            assertEquals(12, all.size)
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
        val keys = listOf("onboarded", "narrate_enabled")
        val before = keys.associateWith { if (prefs.contains(it)) prefs.getBoolean(it, false) else null }
        try {
            prefs.edit().putBoolean("onboarded", false).putBoolean("narrate_enabled", false).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertEquals(View.VISIBLE, (field(activity, "onboardingOverlay") as View).visibility)
                    click(activity, R.string.narration_explore_quietly)
                    assertFalse(prefs.getBoolean("narrate_enabled", true))
                    assertTrue(prefs.getBoolean("onboarded", false))
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertEquals(View.GONE, (field(activity, "onboardingOverlay") as View).visibility)
                    assertEquals(false, field(activity, "narrateEnabled"))
                }
            }
            prefs.edit().putBoolean("onboarded", false).putBoolean("narrate_enabled", false).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> click(activity, R.string.narration_explore_with_talia) }
                Thread.sleep(500)
                scenario.onActivity { activity ->
                    assertTrue(prefs.getBoolean("narrate_enabled", false))
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
}
