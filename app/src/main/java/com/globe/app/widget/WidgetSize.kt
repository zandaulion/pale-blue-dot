package com.globe.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.SizeF

/** Current widget size in dp, from launcher options for the device's orientation. */
data class WidgetSize(val widthDp: Int, val heightDp: Int) {
    /** Pixel size of the full-bleed image for the selected responsive layout. */
    fun imagePixels(context: Context, imageHeightDp: Int): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        return (widthDp.coerceAtLeast(180) * density).toInt() to
            (imageHeightDp.coerceAtLeast(90) * density).toInt()
    }

    companion object {
        fun from(context: Context, options: Bundle): WidgetSize {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                @Suppress("DEPRECATION") // The typed overload is API 33+.
                val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                if (!sizes.isNullOrEmpty()) {
                    return WidgetSize(sizes.maxOf { it.width }.toInt(), sizes.maxOf { it.height }.toInt())
                }
            }
            // Launchers report portrait as min width x max height, landscape as max width x min height.
            val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            val width = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
                else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 180)
            val height = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
                else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 90)
            return WidgetSize(width, height)
        }
    }
}
