package com.globe.app.kids

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * A parental gate shown before actions that leave the app (e.g. sharing), as
 * required by Google Play's Families policy for child-directed apps.
 *
 * It poses a multiplication question — trivial for an adult or older child, but
 * a deliberate speed bump for a young child. This is not a security control; it
 * only needs to be hard to pass by accident or by a very young user.
 *
 * The card draws its own dark background so it reads correctly regardless of the
 * host dialog theme.
 */
object ParentalGate {

    fun show(activity: Activity, onPass: () -> Unit) {
        val a = (6..12).random()
        val b = (6..12).random()
        val answer = a * b

        val dp = { v: Float ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, activity.resources.displayMetrics
            ).toInt()
        }

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24f), dp(22f), dp(24f), dp(16f))
            background = GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(Color.rgb(14, 22, 36))
                setStroke(dp(1f), Color.argb(60, 255, 255, 255))
            }
        }

        card.addView(TextView(activity).apply {
            text = "Ask a grown-up"
            setTextColor(Color.rgb(150, 200, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        })

        card.addView(TextView(activity).apply {
            text = "What is  $a × $b ?"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(8f), 0, dp(16f))
        })

        val dialog = AlertDialog.Builder(activity)
            .setView(card)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Four choices: the answer plus three nearby distractors.
        val options = linkedSetOf(answer)
        while (options.size < 4) {
            val cand = answer + (-15..15).random()
            if (cand > 0) options.add(cand)
        }
        for (opt in options.shuffled()) {
            card.addView(TextView(activity).apply {
                text = opt.toString()
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(0, dp(12f), 0, dp(12f))
                isClickable = true
                background = GradientDrawable().apply {
                    cornerRadius = dp(14f).toFloat()
                    setColor(Color.argb(90, 40, 60, 90))
                    setStroke(dp(1f), Color.argb(50, 255, 255, 255))
                }
                setOnClickListener {
                    dialog.dismiss()
                    if (opt == answer) onPass() else show(activity, onPass)
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8f) })
        }

        card.addView(TextView(activity).apply {
            text = "Maybe later"
            setTextColor(Color.argb(180, 200, 200, 210))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, dp(16f), 0, dp(4f))
            isClickable = true
            setOnClickListener { dialog.dismiss() }
        })

        dialog.show()
    }
}
