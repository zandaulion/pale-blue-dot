package com.globe.app.events

import android.content.Context
import com.globe.app.R

fun EarthEventsProvider.Event.Type.label(context: Context): String = context.getString(when (this) {
    EarthEventsProvider.Event.Type.EARTHQUAKE -> R.string.text_earthquake
    EarthEventsProvider.Event.Type.VOLCANO -> R.string.text_volcano
    EarthEventsProvider.Event.Type.WILDFIRE -> R.string.text_wildfire
    EarthEventsProvider.Event.Type.STORM -> R.string.text_storm
})

fun eventCategoryLabel(context: Context, category: String): String =
    EarthEventsProvider.Event.Type.values().firstOrNull { it.name.equals(category, ignoreCase = true) }
        ?.label(context) ?: category
