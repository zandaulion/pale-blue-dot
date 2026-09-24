package com.globe.app.places

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.globe.app.widget.WidgetSnapshotRenderer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class DaylightAndWidgetTest {
    private fun utc(y: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(y, month - 1, day, hour, 0, 0)
        }.timeInMillis

    @Test fun bucharestSeptemberReferenceWithinTenMinutes() {
        // Independent reference: timeanddate Bucharest Sep 23 2026, rise 07:03, set 19:11 EEST.
        val place = CityCatalog.cities.first { it.id == "city:bucharest" }
        val result = Daylight.at(place, utc(2026, 9, 23))
        assertEquals(Daylight.State.ORDINARY, result.state)
        val local = Calendar.getInstance(TimeZone.getTimeZone("Europe/Bucharest"))
        local.timeInMillis = result.sunriseMs!!
        assertTrue(kotlin.math.abs(local.get(Calendar.HOUR_OF_DAY) * 60 + local.get(Calendar.MINUTE) - 423) <= 10)
        local.timeInMillis = result.sunsetMs!!
        assertTrue(kotlin.math.abs(local.get(Calendar.HOUR_OF_DAY) * 60 + local.get(Calendar.MINUTE) - 1151) <= 10)
    }

    @Test fun polarAndUnsetZoneAreNamed() {
        val tromso = CityCatalog.cities.first { it.id == "city:tromso" }
        assertEquals(Daylight.State.POLAR_DAY, Daylight.at(tromso, utc(2026, 6, 21)).state)
        assertEquals(Daylight.State.POLAR_NIGHT, Daylight.at(tromso, utc(2026, 12, 21)).state)
        assertEquals(Daylight.State.ZONE_UNSET, Daylight.at(tromso.copy(zoneId = null), utc(2026, 6, 21)).state)
    }

    @Test fun widgetOffscreenSceneRendersWithoutActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = WidgetSnapshotRenderer.render(context, CityCatalog.cities.first())
        try {
            assertEquals(320, bitmap.width)
            assertEquals(180, bitmap.height)
            var colored = 0
            for (y in 0 until bitmap.height step 12) for (x in 0 until bitmap.width step 12) {
                if (bitmap.getPixel(x, y) != android.graphics.Color.BLACK) colored++
            }
            assertTrue(colored > 5)
        } finally { bitmap.recycle() }
    }
}
