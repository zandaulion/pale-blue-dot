package com.globe.app.explore

import android.content.Context
import org.json.JSONObject

data class JourneyStep(
    val id: String,
    val title: String,
    val instruction: String,
    val kind: String,
    val action: String?,
    val options: List<String>,
    val correct: Int,
    val right: String,
    val wrong: String,
    val lat: Double?,
    val lon: Double?,
    val month: Int?,
    val day: Int?,
    val hourUtc: Int?,
    val hours: Int,
    val plates: Boolean,
    val guides: Boolean,
    val recent: String?,
    val source: String?
)

data class Journey(val id: String, val title: String, val minutes: String, val steps: List<JourneyStep>) {
    fun step(id: String): JourneyStep? = steps.firstOrNull { it.id == id }
    fun next(id: String): JourneyStep? = steps.getOrNull(steps.indexOfFirst { it.id == id } + 1)
}

/** Ordered local lesson content; no network fetch or live-feed insertion. */
object JourneyContent {
    @Volatile private var cached: List<Journey>? = null

    @Synchronized fun all(context: Context): List<Journey> {
        cached?.let { return it }
        val root = JSONObject(context.assets.open("journeys_v1.json").bufferedReader().use { it.readText() })
        check(root.getInt("schema") == 1)
        val array = root.getJSONArray("journeys")
        val journeys = (0 until array.length()).map { i ->
            val j = array.getJSONObject(i)
            val steps = j.getJSONArray("steps")
            Journey(j.getString("id"), j.getString("title"), j.getString("minutes"),
                (0 until steps.length()).map { k ->
                    val s = steps.getJSONObject(k)
                    val options = s.optJSONArray("options")
                    JourneyStep(s.getString("id"), s.getString("title"), s.getString("instruction"),
                        s.getString("kind"), s.optString("action").ifBlank { null },
                        if (options == null) emptyList() else (0 until options.length()).map { options.getString(it) },
                        s.optInt("correct", -1), s.optString("right"), s.optString("wrong"),
                        s.optDouble("lat").takeUnless { it.isNaN() },
                        s.optDouble("lon").takeUnless { it.isNaN() },
                        s.optInt("month", 0).takeIf { it != 0 },
                        s.optInt("day", 0).takeIf { it != 0 },
                        s.optInt("hourUtc", -1).takeIf { it >= 0 },
                        s.optInt("hours", 0), s.optBoolean("plates"), s.optBoolean("guides"),
                        s.optString("recent").ifBlank { null }, s.optString("source").ifBlank { null })
                })
        }
        cached = journeys
        return journeys
    }
}

data class JourneySceneSnapshot(
    val cameraAz: Float, val cameraEl: Float, val cameraDistance: Float,
    val exploring: Boolean, val timeMs: Long, val playing: Boolean,
    val plateLayer: Boolean, val latitudeGuides: Boolean, val compareObservations: Boolean
) {
    fun json(): String = JSONObject().put("az", cameraAz.toDouble()).put("el", cameraEl.toDouble())
        .put("distance", cameraDistance.toDouble()).put("exploring", exploring).put("time", timeMs)
        .put("playing", playing).put("plates", plateLayer).put("guides", latitudeGuides)
        .put("compare", compareObservations).toString()

    companion object {
        fun parse(text: String): JourneySceneSnapshot? = try {
            val o = JSONObject(text)
            JourneySceneSnapshot(o.getDouble("az").toFloat(), o.getDouble("el").toFloat(),
                o.getDouble("distance").toFloat(), o.getBoolean("exploring"), o.getLong("time"),
                o.getBoolean("playing"), o.getBoolean("plates"), o.getBoolean("guides"),
                o.getBoolean("compare"))
        } catch (_: Exception) { null }
    }
}

/** Atomic preference commits preserve the last completed step and prior scene. */
class JourneyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("journeys_v1", Context.MODE_PRIVATE)
    data class Active(val journeyId: String, val stepId: String, val previous: JourneySceneSnapshot)

    fun active(): Active? {
        val id = prefs.getString("active_id", null) ?: return null
        val step = prefs.getString("active_step", null) ?: return null
        val previous = JourneySceneSnapshot.parse(prefs.getString("previous_scene", "") ?: "") ?: return null
        return Active(id, step, previous)
    }

    fun start(journey: Journey, previous: JourneySceneSnapshot) {
        prefs.edit().putString("active_id", journey.id).putString("active_step", journey.steps.first().id)
            .putString("previous_scene", previous.json()).commit()
    }

    fun advance(stepId: String) { prefs.edit().putString("active_step", stepId).commit() }
    fun completed(id: String): Boolean = prefs.getBoolean("completed_$id", false)
    fun finish(journeyId: String) {
        prefs.edit().putBoolean("completed_$journeyId", true).remove("active_id")
            .remove("active_step").remove("previous_scene").commit()
    }
    fun exit() {
        prefs.edit().remove("active_id").remove("active_step").remove("previous_scene").commit()
    }
}
