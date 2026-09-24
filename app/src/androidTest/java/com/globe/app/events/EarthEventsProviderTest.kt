package com.globe.app.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EarthEventsProviderTest {
    private val parser = EarthEventsProvider()

    @Test fun quakeParsingKeepsUnknownFactsAndSkipsMalformedRecord() {
        val json = """
            {"features":[
              {"id":"a1","properties":{"title":"Test quake","mag":5.2,"time":0,"updated":1720000000000,"url":"https://example.org/a1"},
               "geometry":{"type":"Point","coordinates":[-122.0,38.0,12.5]}},
              {"id":"broken","properties":{},"geometry":{"type":"Polygon","coordinates":[]}},
              {"id":"a2","properties":{"title":"Unknown magnitude","mag":null},
               "geometry":{"type":"Point","coordinates":[10,20]}}
            ]}
        """.trimIndent()
        val events = parser.parse(EarthEventsProvider.Event.Type.EARTHQUAKE, json)
        assertEquals(2, events.size)
        assertEquals("usgs:a1", events[0].id)
        assertNull(events[0].observedAtMs)
        assertEquals(5.2f, events[0].magnitude!!)
        assertEquals(12.5f, events[0].depthKm!!)
        assertNull(events[1].magnitude)
        assertNull(events[1].depthKm)
    }

    @Test fun emptyFeedIsAValidEmptyList() {
        assertTrue(parser.parse(EarthEventsProvider.Event.Type.EARTHQUAKE, """{"features":[]}""").isEmpty())
    }

    @Test fun eonetTrackUsesLatestDatedPointWithoutInventingMagnitude() {
        val json = """
            {"events":[{"id":"E1","title":"Reported storm","link":"https://eonet.gsfc.nasa.gov/api/v3/events/E1",
            "geometry":[
              {"type":"Point","date":"2026-09-20T10:00:00Z","coordinates":[20,10]},
              {"type":"Polygon","date":"2026-09-22T10:00:00Z","coordinates":[]},
              {"type":"Point","date":"2026-09-21T11:00:00Z","coordinates":[21,11]}
            ]}]}
        """.trimIndent()
        val event = parser.parse(EarthEventsProvider.Event.Type.STORM, json).single()
        assertEquals("eonet:E1", event.id)
        assertEquals(11.0, event.lat, 0.0)
        assertEquals(21.0, event.lon, 0.0)
        assertEquals(2, event.geometry.size)
        assertNull(event.magnitude)
    }
}
