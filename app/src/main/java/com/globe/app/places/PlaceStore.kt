package com.globe.app.places

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.TimeZone
import com.globe.app.widget.EarthTodayWidget

data class SavedPlace(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val zoneId: String?
)

/** Local-only, versioned saved places. IDs never depend on list position. */
class PlaceStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("saved_places", Context.MODE_PRIVATE)

    @Synchronized fun all(): List<SavedPlace> = try {
        val array = JSONArray(prefs.getString("places_v1", "[]"))
        (0 until array.length()).mapNotNull { i ->
            try {
                val o = array.getJSONObject(i)
                SavedPlace(o.getString("id"), o.getString("name"), o.getDouble("lat"),
                    o.getDouble("lon"), o.optString("zone", "").ifBlank { null })
            } catch (_: Exception) { null }
        }
    } catch (_: Exception) { emptyList() }

    fun primaryId(): String? = prefs.getString("primary_id", null)
    fun primary(): SavedPlace? = all().firstOrNull { it.id == primaryId() }

    @Synchronized fun save(place: SavedPlace) {
        require(place.name.isNotBlank() && place.lat in -90.0..90.0 && place.lon in -180.0..180.0)
        val places = all().filterNot { it.id == place.id } + place
        write(places)
        if (primaryId() == null) setPrimary(place.id)
    }

    @Synchronized fun create(name: String, lat: Double, lon: Double, zoneId: String?): SavedPlace {
        val place = SavedPlace("custom:${UUID.randomUUID()}", name.trim(), lat, lon, zoneId)
        save(place)
        return place
    }

    @Synchronized fun delete(id: String): SavedPlace? {
        val deleted = all().firstOrNull { it.id == id } ?: return null
        write(all().filterNot { it.id == id })
        if (primaryId() == id) prefs.edit().remove("primary_id").apply()
        EarthTodayWidget.requestUpdate(appContext)
        return deleted
    }

    @Synchronized fun setPrimary(id: String) {
        if (all().any { it.id == id }) {
            prefs.edit().putString("primary_id", id).apply()
            EarthTodayWidget.requestUpdate(appContext)
        }
    }

    private fun write(places: List<SavedPlace>) {
        val array = JSONArray()
        places.forEach { p -> array.put(JSONObject().put("id", p.id).put("name", p.name)
            .put("lat", p.lat).put("lon", p.lon).put("zone", p.zoneId ?: "")) }
        prefs.edit().putString("places_v1", array.toString()).commit()
        EarthTodayWidget.requestUpdate(appContext)
    }

    companion object {
        fun validZone(id: String): Boolean = TimeZone.getAvailableIDs().contains(id)
    }
}
