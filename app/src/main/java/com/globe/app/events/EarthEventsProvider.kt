package com.globe.app.events

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Network and parsing only. EarthRepository owns scheduling, status, and disk cache. */
class EarthEventsProvider {
    data class Geometry(val lat: Double, val lon: Double, val timeMs: Long?)

    data class Event(
        val id: String,
        val lat: Double,
        val lon: Double,
        val title: String,
        val type: Type,
        val observedAtMs: Long?,
        val updatedAtMs: Long?,
        val source: String,
        val sourceUrl: String?,
        val magnitude: Float?,
        val depthKm: Float?,
        val geometry: List<Geometry>,
        val markerSize: Float
    ) {
        enum class Type { EARTHQUAKE, VOLCANO, WILDFIRE, STORM }
    }

    fun fetchRaw(type: Event.Type): String {
        val url = when (type) {
            Event.Type.EARTHQUAKE -> "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_week.geojson"
            Event.Type.VOLCANO -> eonetUrl("volcanoes", 0)
            Event.Type.WILDFIRE -> eonetUrl("wildfires", 10)
            Event.Type.STORM -> eonetUrl("severeStorms", 7)
        }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val limit = 8 * 1024 * 1024
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    if (output.size() + count > limit) error("Event response too large")
                    output.write(chunk, 0, count)
                }
                return output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    fun parse(type: Event.Type, json: String): List<Event> {
        val root = JSONObject(json)
        val items = root.getJSONArray(if (type == Event.Type.EARTHQUAKE) "features" else "events")
        val result = ArrayList<Event>(items.length())
        for (index in 0 until items.length()) {
            try {
                val item = items.getJSONObject(index)
                val event = if (type == Event.Type.EARTHQUAKE) parseQuake(item) else parseEonet(type, item)
                if (event != null) result.add(event)
            } catch (_: Exception) {
                // One malformed record must not discard a usable feed.
            }
        }
        return result.toList()
    }

    private fun parseQuake(item: JSONObject): Event? {
        val props = item.getJSONObject("properties")
        val geometry = item.getJSONObject("geometry")
        if (geometry.optString("type") != "Point") return null
        val coords = geometry.getJSONArray("coordinates")
        val lon = coords.getDouble(0)
        val lat = coords.getDouble(1)
        if (!valid(lat, lon)) return null
        val magnitude = number(props, "mag")?.toFloat()
        if (magnitude != null && magnitude < 4.5f) return null
        val time = positiveTime(props, "time")
        val depth = if (coords.length() > 2 && !coords.isNull(2)) coords.optDouble(2).takeIf { it.isFinite() }?.toFloat() else null
        val id = item.optString("id").takeIf { it.isNotBlank() } ?: return null
        return Event(
            "usgs:$id", lat, lon, props.optString("title", "Earthquake"), Event.Type.EARTHQUAKE,
            time, positiveTime(props, "updated"), "USGS", props.optString("url").takeIf { it.startsWith("https://") },
            magnitude, depth, listOf(Geometry(lat, lon, time)),
            if (magnitude == null) 14f else (8f + (magnitude - 4f) * 8f).coerceIn(10f, 36f)
        )
    }

    private fun parseEonet(type: Event.Type, item: JSONObject): Event? {
        val id = item.optString("id").takeIf { it.isNotBlank() } ?: return null
        val geometries = item.optJSONArray("geometry") ?: return null
        val points = ArrayList<Geometry>()
        for (index in 0 until geometries.length()) {
            try {
                val shape = geometries.getJSONObject(index)
                if (shape.optString("type") != "Point") continue // Polygon/other shapes need a different renderer.
                val coords = shape.getJSONArray("coordinates")
                val lon = coords.getDouble(0)
                val lat = coords.getDouble(1)
                if (valid(lat, lon)) points.add(Geometry(lat, lon, parseDate(shape.optString("date"))))
            } catch (_: Exception) { }
        }
        val latest = points.maxByOrNull { it.timeMs ?: Long.MIN_VALUE } ?: return null
        return Event(
            "eonet:$id", latest.lat, latest.lon, item.optString("title", "Reported event"), type,
            latest.timeMs, null, "NASA EONET", item.optString("link").takeIf { it.startsWith("https://") },
            null, null, points.toList(), 16f
        )
    }

    private fun valid(lat: Double, lon: Double) = lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0

    private fun number(json: JSONObject, key: String): Double? =
        if (json.has(key) && !json.isNull(key)) json.optDouble(key).takeIf { it.isFinite() } else null

    private fun positiveTime(json: JSONObject, key: String): Long? =
        if (json.has(key) && !json.isNull(key)) json.optLong(key).takeIf { it > 0L } else null

    private fun parseDate(raw: String): Long? {
        val date = raw.take(19)
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }.parse(date)?.time
        } catch (_: Exception) { null }
    }

    private fun eonetUrl(category: String, days: Int): String =
        "https://eonet.gsfc.nasa.gov/api/v3/events?status=open&category=$category" +
            if (days > 0) "&days=$days" else ""
}
