package com.globe.app.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Composites a subtle "Pale Blue Dot" wordmark into the corner of a captured
 * frame — the app's only growth funnel, since every shared image carries the
 * brand. Sizes are relative to the bitmap width so the mark looks consistent at
 * any capture resolution.
 *
 * Operates on a single [Bitmap], so the v2 video path can reuse it per frame.
 * The clickable Play Store link lives in the share caption (you can't tap
 * pixels); this just brands the image.
 */
object ShareWatermark {

    private const val TITLE = "Pale Blue Dot"
    private const val SUBTITLE = "on Google Play"

    /**
     * Draws the watermark in the bottom-left corner. If [src] is mutable the
     * draw happens in place and the same instance is returned; otherwise a
     * mutable copy is made and [src] is recycled.
     */
    fun apply(src: Bitmap): Bitmap {
        val target = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(target)
        val w = target.width.toFloat()
        val pad = w * 0.04f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = w * 0.045f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(w * 0.012f, 0f, 0f, Color.BLACK)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 200, 220, 255) // faint pale-blue
            textSize = w * 0.028f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            setShadowLayer(w * 0.01f, 0f, 0f, Color.BLACK)
        }

        val subBaseline = target.height - pad
        val titleBaseline = subBaseline - subPaint.textSize * 1.5f
        canvas.drawText(TITLE, pad, titleBaseline, titlePaint)
        canvas.drawText(SUBTITLE, pad, subBaseline, subPaint)

        if (target !== src) src.recycle()
        return target
    }
}
