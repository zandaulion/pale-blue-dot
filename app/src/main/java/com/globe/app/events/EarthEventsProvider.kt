package com.globe.app.events

import android.util.Log
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Fetches recent earthquakes from the USGS API and active volcanoes from NASA EONET.
 * Both APIs are free and require no API key.
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
        enum class Type { EARTHQUAKE, VOLCANO }
    }

    companion object {
        private const val TAG = "EarthEventsProvider"
        private const val QUAKE_URL =
            "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_week.geojson"
        private const val VOLCANO_URL =
            "https://eonet.gsfc.nasa.gov/api/v3/events?category=volcanoes&days=30&status=open"
    }

    /**
     * Fetches earthquakes and volcanoes. Call from a background thread.
     */
    fun fetch(): List<Event> {
        val events = mutableListOf<Event>()
        events.addAll(fetchEarthquakes())
        events.addAll(fetchVolcanoes())
        Log.d(TAG, "Fetched ${events.size} events (${events.count { it.type == Event.Type.EARTHQUAKE }} quakes, ${events.count { it.type == Event.Type.VOLCANO }} volcanoes)")
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

    private fun fetchVolcanoes(): List<Event> {
        return try {
            val json = URL(VOLCANO_URL).openConnection().apply {
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
                val title = event.optString("title", "Unknown volcano")
                val geometries = event.optJSONArray("geometry") ?: continue

                // Use the most recent geometry entry
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
                    magnitude = 5.0f, // volcanoes don't have a mag; use a fixed visual size
                    title = title,
                    type = Event.Type.VOLCANO,
                    timeMs = timeMs
                ))
            }
            events
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch volcanoes", e)
            emptyList()
        }
    }
}
