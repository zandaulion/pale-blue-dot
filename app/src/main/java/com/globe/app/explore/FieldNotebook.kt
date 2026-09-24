package com.globe.app.explore

import android.content.Context
import com.globe.app.events.EarthEventsProvider
import com.globe.app.kids.Discovery
import com.globe.app.kids.DiscoveryJournal
import org.json.JSONArray
import org.json.JSONObject

data class SavedObservation(
    val id: String,
    val title: String,
    val category: String,
    val lat: Double,
    val lon: Double,
    val observedAtMs: Long?,
    val source: String,
    val sourceUrl: String?,
    val magnitude: Float?,
    val depthKm: Float?
)

/** Idempotent copy-forward of legacy discoveries plus durable observation facts. */
class FieldNotebook(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("field_notebook_v1", Context.MODE_PRIVATE)

    @Synchronized fun migrateLegacy(journal: DiscoveryJournal) {
        val present = prefs.getStringSet("legacy_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        var changed = false
        Discovery.values().forEach { discovery ->
            if (journal.isUnlocked(discovery) && present.add("legacy:${discovery.id}")) changed = true
        }
        if (changed || prefs.getInt("schema", 0) != 1) {
            // Commit the complete new representation before any old preference could be retired.
            prefs.edit().putInt("schema", 1).putStringSet("legacy_ids", present).commit()
        }
    }

    fun legacyDiscoveries(): List<Discovery> {
        val ids = prefs.getStringSet("legacy_ids", emptySet()) ?: emptySet()
        return Discovery.values().filter { "legacy:${it.id}" in ids }
    }

    @Synchronized fun saveEvent(event: EarthEventsProvider.Event) {
        val current = savedEvents().filterNot { it.id == event.id }.takeLast(99)
        val next = current + SavedObservation(event.id, event.title, event.type.name,
            event.lat, event.lon, event.observedAtMs, event.source, event.sourceUrl,
            event.magnitude, event.depthKm)
        val array = JSONArray()
        next.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("title", item.title)
                .put("category", item.category).put("lat", item.lat).put("lon", item.lon)
                .put("observed", item.observedAtMs ?: JSONObject.NULL).put("source", item.source)
                .put("url", item.sourceUrl ?: JSONObject.NULL)
                .put("magnitude", item.magnitude ?: JSONObject.NULL)
                .put("depth", item.depthKm ?: JSONObject.NULL))
        }
        prefs.edit().putInt("schema", 1).putString("observations", array.toString()).commit()
    }

    fun hasEvent(id: String): Boolean = savedEvents().any { it.id == id }

    fun savedEvents(): List<SavedObservation> = try {
        val array = JSONArray(prefs.getString("observations", "[]"))
        (0 until array.length()).mapNotNull { i ->
            try {
                val o = array.getJSONObject(i)
                SavedObservation(o.getString("id"), o.getString("title"), o.getString("category"),
                    o.getDouble("lat"), o.getDouble("lon"), o.optLong("observed").takeIf { !o.isNull("observed") },
                    o.getString("source"), o.optString("url").takeIf { !o.isNull("url") },
                    o.optDouble("magnitude").takeUnless { o.isNull("magnitude") }.toFloatOrNull(),
                    o.optDouble("depth").takeUnless { o.isNull("depth") }.toFloatOrNull())
            } catch (_: Exception) { null }
        }
    } catch (_: Exception) { emptyList() }

    private fun Double?.toFloatOrNull(): Float? = this?.toFloat()
}
