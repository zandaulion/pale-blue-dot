package com.globe.app.explore

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.globe.app.data.EarthRepository
import com.globe.app.data.TodayBriefing
import com.globe.app.events.EarthEventsProvider
import com.globe.app.kids.Discovery
import com.globe.app.kids.DiscoveryJournal
import com.globe.app.places.CityCatalog
import com.globe.app.places.Daylight
import com.globe.app.time.AppSceneClock
import com.globe.app.time.RealTimeSceneClock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class CompanionStateTest {
    private val testContext: Context get() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testPackage = instrumentation.context
        return object : ContextWrapper(instrumentation.targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                testPackage.getSharedPreferences("isolated_$name", mode)
        }
    }

    @Test fun journeyProgressAndPriorSceneSurviveRecreation() {
        testContext.getSharedPreferences("journeys_v1", Context.MODE_PRIVATE).edit().clear().commit()
        val journey = JourneyContent.all(testContext).first { it.id == "follow_sunrise" }
        val previous = JourneySceneSnapshot(42f, 15f, 5f, true, 1_790_000_000_000L,
            false, true, false, false)
        JourneyStore(testContext).start(journey, previous)
        JourneyStore(testContext).advance("predict")
        val recovered = JourneyStore(testContext).active()
        assertEquals("predict", recovered?.stepId)
        assertEquals(previous, recovered?.previous)
        JourneyStore(testContext).finish(journey.id)
        assertNull(JourneyStore(testContext).active())
        assertTrue(JourneyStore(testContext).completed(journey.id))
    }

    @Test fun legacyMigrationIsIdempotentAndKeepsOldFlags() {
        testContext.getSharedPreferences("discoveries", Context.MODE_PRIVATE).edit().clear().commit()
        testContext.getSharedPreferences("field_notebook_v1", Context.MODE_PRIVATE).edit().clear().commit()
        val old = DiscoveryJournal(testContext)
        Discovery.values().forEach { old.unlock(it) }
        val notebook = FieldNotebook(testContext)
        notebook.migrateLegacy(old)
        notebook.migrateLegacy(old)
        assertEquals(8, notebook.legacyDiscoveries().size)
        assertEquals(8, old.unlockedCount())
    }

    @Test fun seasonsReverseBetweenHemispheresAndClocksStaySeparate() {
        fun utc(month: Int, day: Int): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(2026, month - 1, day, 12, 0, 0)
        }.timeInMillis
        val london = CityCatalog.cities.first { it.id == "city:london" }
        val sydney = CityCatalog.cities.first { it.id == "city:sydney" }
        assertTrue(Daylight.at(london, utc(6, 21)).daylightMinutes!! > Daylight.at(sydney, utc(6, 21)).daylightMinutes!!)
        assertTrue(Daylight.at(sydney, utc(12, 21)).daylightMinutes!! > Daylight.at(london, utc(12, 21)).daylightMinutes!!)
        val app = AppSceneClock().apply { enter(utc(6, 21)) }
        assertEquals(utc(6, 21), app.nowMs())
        assertTrue(kotlin.math.abs(RealTimeSceneClock().nowMs() - System.currentTimeMillis()) < 1000)
        app.exit()
        assertTrue(kotlin.math.abs(app.nowMs() - System.currentTimeMillis()) < 1000)
    }

    @Test fun briefingUsesRecentReportedEventsAndHandlesAbsence() {
        val now = System.currentTimeMillis()
        val feeds = EarthEventsProvider.Event.Type.values().associateWith { type ->
            val event = if (type == EarthEventsProvider.Event.Type.EARTHQUAKE)
                EarthEventsProvider.Event("test-quake", 1.0, 2.0, "Reported event", type,
                    now - 86_400_000L, null, "USGS", null, 5f, null, emptyList(), 12f)
            else null
            EarthRepository.Feed(EarthRepository.State.READY, listOfNotNull(event), now)
        }
        val withEvent = TodayBriefing.build(now, null, EarthRepository.Snapshot(feeds))
        assertEquals("test-quake", withEvent.event?.id)
        val empty = TodayBriefing.build(now, null, EarthRepository.Snapshot(feeds.mapValues { it.value.copy(events = emptyList()) }))
        assertNull(empty.event)
        assertTrue(empty.eventMessage.contains("No recent"))
    }
}
