package com.globe.app.moon

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.globe.app.AppLanguage
import com.globe.app.R
import java.text.NumberFormat

/** Native text follows the GL Moon; it can wrap, scale with system text, and be read aloud. */
class MoonGuideOverlay(
    context: Context,
    private val frame: () -> MoonGuide.Frame?,
    private val blockedAreas: () -> List<Rect>,
    private val isCovered: () -> Boolean
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val percent = NumberFormat.getPercentInstance(AppLanguage.locale(context))
    val label = TextView(context).apply {
        gravity = Gravity.CENTER
        textDirection = View.TEXT_DIRECTION_LOCALE
        setTextSize(13f)
        setTextColor(Color.rgb(224, 234, 246))
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = GradientDrawable().apply {
            setColor(Color.argb(235, 9, 17, 29))
            cornerRadius = dp(8).toFloat()
        }
        visibility = View.GONE
    }
    private val update = object : Runnable {
        override fun run() {
            positionLabel()
            if (isAttachedToWindow && windowVisibility == View.VISIBLE) postOnAnimation(this)
        }
    }

    init {
        // Projected x is measured from the physical left edge, in every language.
        // The label itself still inherits the locale's text direction.
        @Suppress("RtlHardcoded")
        val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT)
        addView(label, params)
    }

    private fun positionLabel() {
        val moon = frame()
        if (moon == null || moon.strength < 0.05f || isCovered() || width <= dp(32)) {
            label.visibility = View.GONE
            return
        }
        val text = if (moon.illumination <= 0.01f) {
            context.getString(R.string.moon_guide_new_moon, context.getString(R.string.text_new_moon))
        } else {
            context.getString(R.string.moon_guide_crescent, percent.format(moon.illumination))
        }
        if (label.text.toString() != text) label.text = text
        label.maxWidth = minOf(dp(280), width - dp(24))
        label.measure(MeasureSpec.makeMeasureSpec(label.maxWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST))
        val w = label.measuredWidth
        val h = label.measuredHeight
        val x = (moon.x - w / 2f).coerceIn(dp(12).toFloat(), (width - dp(12) - w).toFloat())
        val radius = moon.earthRadiusPx * 1.08f // Include the atmosphere.
        val obstacles = blockedAreas() + Rect((moon.earthX - radius).toInt(), (moon.earthY - radius).toInt(),
            (moon.earthX + radius).toInt(), (moon.earthY + radius).toInt())
        val positions = listOf(moon.y - moon.radiusPx - dp(8) - h, moon.y + moon.radiusPx + dp(8))
        val y = positions.firstOrNull { candidate ->
            candidate >= dp(8) && candidate + h <= height - dp(8) &&
                obstacles.none { Rect.intersects(it, Rect(x.toInt(), candidate.toInt(), (x + w).toInt(), (candidate + h).toInt())) }
        }
        if (y == null) {
            label.visibility = View.GONE
            return
        }
        // No layout request per frame: only translate the measured native label.
        label.layout(0, 0, w, h)
        label.translationX = x
        label.translationY = y
        label.alpha = moon.strength.coerceAtLeast(0.7f)
        label.visibility = View.VISIBLE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(update)
        postOnAnimation(update)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        removeCallbacks(update)
        if (visibility == View.VISIBLE && isAttachedToWindow) postOnAnimation(update)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(update)
        super.onDetachedFromWindow()
    }
}
