package com.globe.app.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout

/** Wrap short lessons; scroll long lessons within a capped card beside the globe. */
class JourneyCardLayout(context: Context) : LinearLayout(context) {
    var maximumHeight = 0
        set(value) {
            if (field != value) { field = value; requestLayout() }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED)
            maximumHeight else MeasureSpec.getSize(heightMeasureSpec)
        val limit = if (maximumHeight > 0) minOf(available, maximumHeight) else available
        if (childCount == 3) {
            // Measure fixed controls first; a long ScrollView must not consume their space.
            val unlimited = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            measureChildWithMargins(getChildAt(0), widthMeasureSpec, 0, unlimited, 0)
            measureChildWithMargins(getChildAt(2), widthMeasureSpec, 0, unlimited, 0)
            val remaining = (limit - paddingTop - paddingBottom - getChildAt(0).measuredHeight - getChildAt(2).measuredHeight).coerceAtLeast(0)
            val body = getChildAt(1)
            val params = body.layoutParams as LayoutParams
            params.weight = 0f
            params.height = LayoutParams.WRAP_CONTENT
            measureChildWithMargins(body, widthMeasureSpec, 0, MeasureSpec.makeMeasureSpec(remaining + paddingTop + paddingBottom, MeasureSpec.AT_MOST), 0)
            params.height = minOf(body.measuredHeight, remaining)
        }
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST))
    }
}
