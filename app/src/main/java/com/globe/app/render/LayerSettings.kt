package com.globe.app.render

import android.content.SharedPreferences
import com.globe.app.earth.EarthRenderer
import com.globe.app.events.EarthEventsProvider

/** Per-install app scene choices. Existing ordinal cloud value is read without changing enum order. */
class LayerSettings(private val prefs: SharedPreferences) {
    var cloudMode: EarthRenderer.CloudMode
        get() = EarthRenderer.CloudMode.values()[
            prefs.getInt("cloud_mode", EarthRenderer.CloudMode.OFF.ordinal)
                .coerceIn(0, EarthRenderer.CloudMode.values().size - 1)
        ]
        set(value) { prefs.edit().putInt("cloud_mode", value.ordinal).apply() }

    fun enabled(type: EarthEventsProvider.Event.Type): Boolean =
        prefs.getBoolean("layer_${type.name.lowercase()}", true)

    fun setEnabled(type: EarthEventsProvider.Event.Type, enabled: Boolean) {
        prefs.edit().putBoolean("layer_${type.name.lowercase()}", enabled).apply()
    }

    var constellations: Boolean
        get() = prefs.getBoolean("layer_constellations", true)
        set(value) { prefs.edit().putBoolean("layer_constellations", value).apply() }
    var iss: Boolean
        get() = prefs.getBoolean("layer_iss", true)
        set(value) { prefs.edit().putBoolean("layer_iss", value).apply() }
    var aurora: Boolean
        get() = prefs.getBoolean("layer_aurora", true)
        set(value) { prefs.edit().putBoolean("layer_aurora", value).apply() }
    var terminator: Boolean
        get() = prefs.getBoolean("layer_terminator", true)
        set(value) { prefs.edit().putBoolean("layer_terminator", value).apply() }
    var plateBoundaries: Boolean
        get() = prefs.getBoolean("layer_plate_boundaries", false)
        set(value) { prefs.edit().putBoolean("layer_plate_boundaries", value).apply() }
    var reduceMotion: Boolean
        get() = prefs.getBoolean("reduce_motion", false)
        set(value) { prefs.edit().putBoolean("reduce_motion", value).apply() }
}
