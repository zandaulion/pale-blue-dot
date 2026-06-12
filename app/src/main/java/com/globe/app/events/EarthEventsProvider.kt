package com.globe.app.events

import android.util.Log
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Fetches recent earthquakes from the USGS API and active volcanoes, wildfires,
 * and severe storms from NASA EONET. All APIs are free and require no API key.
 */
class EarthEventsProvider {

    data class Event(
        val lat: Double,
        val lon: Double,
        val magnitude: Float,
        val title: String,
        val type: Type,
        val timeMs: Long
    ) {
        enum class Type { EARTHQUAKE, VOLCANO, WILDFIRE, STORM }
    }

    companion object {
        private const val TAG = "EarthEventsProvider"
        private const val QUAKE_URL =
            "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_week.geojson"
        private const val EONET_URL =
            "https://eonet.gsfc.nasa.gov/api/v3/events?category=%s&days=%d&status=open"
    }

    /**
     * Fetches earthquakes, volcanoes, wildfires, and storms. Call from a background thread.
     */
    fun fetch(): List<Event> {
        val events = mutableListOf<Event>()
        events.addAll(fetchEarthquakes())
        events.addAll(fetchEonet("volcanoes", 30, Event.Type.VOLCANO))
        events.addAll(fetchEonet("wildfires", 10, Event.Type.WILDFIRE))
        events.addAll(fetchEonet("severeStorms", 7, Event.Type.STORM))
        Log.d(TAG, "Fetched ${events.size} events: " +
            Event.Type.values().joinToString { t -> "${events.count { it.type == t }} $t" })
        return events
    }

    private fun fetchEarthquakes(): List<Event> {
        return try {
            val json = URL(QUAKE_URL).openConnection().apply {
                connectTimeout = 10_000
                readTimeout = 15_000
            }.getInputStream().bufferedReader().readText()

            val root = JSONObject(json)
            val features = root.getJSONArray("features")
            val events = mutableListOf<Event>()

            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val props = feature.getJSONObject("properties")
                val geom = feature.getJSONObject("geometry")
                val coords = geom.getJSONArray("coordinates")

                val mag = props.optDouble("mag", 0.0).toFloat()
                if (mag < 4.5f) continue

                events.add(Event(
                    lat = coords.getDouble(1),
                    lon = coords.getDouble(0),
                    magnitude = mag,
                    title = props.optString("title", "Unknown earthquake"),
                    type = Event.Type.EARTHQUAKE,
                    timeMs = props.optLong("time", 0L)
                ))
            }
            events
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch earthquakes", e)
            emptyList()
        }
    }

    /**
     * Fetches one EONET category. Storms report a track of positions over time;
     * using the most recent geometry entry gives the current position for those
     * and the (single) location for volcanoes and wildfires.
     */
    private fun fetchEonet(category: String, days: Int, type: Event.Type): List<Event> {
        return try {
            val url = EONET_URL.format(Locale.US, category, days)
            val json = URL(url).openConnection().apply {
                connectTimeout = 10_000
                readTimeout = 15_000
            }.getInputStream().bufferedReader().readText()

            val root = JSONObject(json)
            val eventsArray = root.getJSONArray("events")
            val events = mutableListOf<Event>()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            for (i in 0 until eventsArray.length()) {
                val event = eventsArray.getJSONObject(i)
                val title = event.optString("title", "Unknown event")
                val geometries = event.optJSONArray("geometry") ?: continue

                if (geometries.length() == 0) continue
                val latest = geometries.getJSONObject(geometries.length() - 1)
                val coords = latest.optJSONArray("coordinates") ?: continue

                val timeStr = latest.optString("date", "")
                val timeMs = try {
                    dateFormat.parse(timeStr.take(19))?.time ?: 0L
                } catch (_: Exception) { 0L }

                events.add(Event(
                    lat = coords.getDouble(1),
                    lon = coords.getDouble(0),
                    magnitude = 5.0f, // EONET events have no magnitude; fixed visual size
                    title = title,
                    type = type,
                    timeMs = timeMs
                ))
            }
            events
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch EONET $category", e)
            emptyList()
        }
    }
}
