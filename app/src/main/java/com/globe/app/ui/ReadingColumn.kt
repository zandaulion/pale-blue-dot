package com.globe.app.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout

/** Keeps prose and controls readable when their host becomes tablet-sized. */
class ReadingColumn(context: Context, private val maxWidthDp: Int = 600) : LinearLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = View.MeasureSpec.getMode(widthMeasureSpec)
        val limit = (maxWidthDp * resources.displayMetrics.density).toInt()
        val width = if (mode == View.MeasureSpec.UNSPECIFIED) limit
            else minOf(View.MeasureSpec.getSize(widthMeasureSpec), limit)
        super.onMeasure(View.MeasureSpec.makeMeasureSpec(width,
            if (mode == View.MeasureSpec.UNSPECIFIED) View.MeasureSpec.AT_MOST else mode), heightMeasureSpec)
    }
}
