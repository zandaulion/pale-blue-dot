package com.globe.app.wallpaper

import android.content.Context
import android.content.SharedPreferences
import com.globe.app.earth.EarthRenderer

/** Separate from app exploration camera and layers. All values survive process restart. */
class WallpaperSettings(context: Context) {
    enum class Preset { WHOLE_EARTH, NIGHT_LIGHTS, HORIZON }
    enum class Motion { FIXED, SLOW_ORBIT, FOLLOW_NIGHT }
    data class Values(
        val preset: Preset,
        val motion: Motion,
        val clouds: EarthRenderer.CloudMode,
        val scale: Float,
        val frameX: Float,
        val frameY: Float,
        val azimuth: Float,
        val elevation: Float,
        val pageParallax: Boolean
    )

    val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)

    fun read(): Values = Values(
        enumValue(preferences.getString("preset", null), Preset.WHOLE_EARTH),
        enumValue(preferences.getString("motion", null), Motion.FIXED),
        enumValue(preferences.getString("clouds", null), EarthRenderer.CloudMode.GENERATED),
        preferences.getFloat("scale", 1f).coerceIn(0.7f, 1.4f),
        preferences.getFloat("frame_x", 0f).coerceIn(-0.5f, 0.5f),
        preferences.getFloat("frame_y", 0f).coerceIn(-0.5f, 0.5f),
        preferences.getFloat("azimuth", 170f),
        preferences.getFloat("elevation", 20f).coerceIn(-80f, 80f),
        preferences.getBoolean("page_parallax", true)
    )

    fun setPreset(value: Preset) {
        preferences.edit()
            .putString("preset", value.name)
            .putString("motion", if (value == Preset.NIGHT_LIGHTS) Motion.FOLLOW_NIGHT.name else Motion.FIXED.name)
            .putFloat("frame_y", if (value == Preset.HORIZON) 0.35f else 0f)
            .apply()
    }
    fun setMotion(value: Motion) { preferences.edit().putString("motion", value.name).apply() }
    fun setClouds(value: EarthRenderer.CloudMode) { preferences.edit().putString("clouds", value.name).apply() }
    fun setScale(value: Float) { preferences.edit().putFloat("scale", value.coerceIn(0.7f, 1.4f)).apply() }
    fun setFrameX(value: Float) { preferences.edit().putFloat("frame_x", value.coerceIn(-0.5f, 0.5f)).apply() }
    fun setFrameY(value: Float) { preferences.edit().putFloat("frame_y", value.coerceIn(-0.5f, 0.5f)).apply() }
    fun setAzimuth(value: Float) { preferences.edit().putFloat("azimuth", value).apply() }
    fun setPageParallax(value: Boolean) { preferences.edit().putBoolean("page_parallax", value).apply() }
    fun setElevation(value: Float) { preferences.edit().putFloat("elevation", value.coerceIn(-80f, 80f)).apply() }

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}
