package com.globe.app.widget

import com.globe.app.AppLanguage
import com.globe.app.R
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.globe.app.places.PlaceStore
import com.globe.app.ui.ReadingColumn

/** Widget instance owns an explicit place choice; cancellation leaves it unconfigured. */
class EarthWidgetConfigActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppLanguage.restore(this)
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val store = PlaceStore(this)
        val places = store.all()
        val prefs = getSharedPreferences("widget_places", Context.MODE_PRIVATE)
        // A saved "" means the child chose "No place yet"; only a missing entry falls back to the primary place.
        val selected = prefs.getString(id.toString(), null) ?: store.primaryId()
        val root = ReadingColumn(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(16, 28, 45))
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(dp(24), dp(24) + insets.systemWindowInsetTop, dp(24), dp(24) + insets.systemWindowInsetBottom)
                insets
            }
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.text_earth_today_widget); textSize = 22f; setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.text_which_place_should_this_widget_show_other_widgets_can_show_differ)
            textSize = 15f; setTextColor(Color.rgb(190, 205, 219))
            setPadding(0, dp(8), 0, dp(12))
        })
        val group = RadioGroup(this)
        val choices = listOf(null) + places
        choices.forEachIndexed { index, place ->
            group.addView(RadioButton(this).apply {
                this.id = index + 1
                text = place?.name ?: getString(R.string.text_no_place_yet)
                setTextColor(Color.WHITE)
                textSize = 16f
                minHeight = dp(48)
                isChecked = if (place == null) selected.isNullOrEmpty() else place.id == selected
            })
        }
        if (places.isEmpty()) root.addView(TextView(this).apply {
            text = getString(R.string.text_save_a_place_in_the_app_explore_places_to_see_its_day_and_night_h)
            textSize = 14f; setTextColor(Color.rgb(190, 205, 219))
        })
        root.addView(ScrollView(this).apply { addView(group) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Button(this).apply {
            text = getString(R.string.text_save_widget)
            minHeight = dp(52)
            setOnClickListener {
                val place = choices.getOrNull(group.checkedRadioButtonId - 1)
                prefs.edit().putString(id.toString(), place?.id ?: "").apply()
                EarthTodayWidget.updateOne(this@EarthWidgetConfigActivity, id)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                finish()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(16, 28, 45))
            addView(root, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_HORIZONTAL
            ))
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
