package com.globe.app.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.globe.app.GlobeRenderer
import com.globe.app.AppLanguage
import com.globe.app.R
import com.globe.app.camera.OrbitCamera
import com.globe.app.data.EarthRepository
import com.globe.app.earth.CloudMapProvider
import com.globe.app.earth.EarthRenderer
import com.globe.app.render.TextureQuality
import com.globe.app.places.CityCatalog
import com.globe.app.places.PlaceStore
import com.globe.app.places.SavedPlace
import com.globe.app.time.RealTimeSceneClock

/** Live in-app preview plus settings; Android owns the final preview/apply decision. */
class WallpaperConfigActivity : AppCompatActivity() {
    private lateinit var settings: WallpaperSettings
    private lateinit var preview: GLSurfaceView
    private lateinit var controls: LinearLayout
    private lateinit var renderer: GlobeRenderer
    private lateinit var controller: WallpaperSceneController
    private lateinit var repository: EarthRepository
    private lateinit var placeSummary: TextView
    private var advancedExpanded = false

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val next = settings.read()
        preview.queueEvent { controller.apply(next) }
        if (key == "preset" || key == "motion" || key == "page_parallax") renderControls()
        else if (::placeSummary.isInitialized) updatePlaceSummary()
        if (settings.read().clouds == EarthRenderer.CloudMode.LIVE) repository.requestClouds()
    }
    private val cloudObserver: (CloudMapProvider.Result?, EarthRepository.State) -> Unit = { result, _ ->
        if (result != null) renderer.setCloudResult(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLanguage.restore(this)
        super.onCreate(savedInstanceState)
        settings = WallpaperSettings(applicationContext)
        repository = EarthRepository.get(applicationContext)
        val camera = OrbitCamera()
        renderer = GlobeRenderer(applicationContext, camera, RealTimeSceneClock(), textureQuality = TextureQuality.WALLPAPER)
        controller = WallpaperSceneController(renderer, camera).also { it.apply(settings.read()) }
        preview = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 15, 25))
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
                insets
            }
        }
        root.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp((resources.configuration.screenHeightDp * 0.3f).toInt().coerceIn(150, 250))
        ))
        root.addView(TextView(this).apply {
            text = getString(R.string.text_live_preview_current_sunlight_no_audio)
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
        })
        controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(24))
        }
        root.addView(ScrollView(this).apply { addView(controls) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(12))
            addView(actionButton(getString(R.string.text_preview_set_wallpaper), primary = true) {
                startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@WallpaperConfigActivity, EarthWallpaperService::class.java))
                })
            })
            addView(TextView(this@WallpaperConfigActivity).apply {
                text = getString(R.string.text_choose_home_or_lock_screen_in_the_android_preview)
                setTextColor(Color.rgb(190, 205, 219))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
        })
        setContentView(root)
        settings.preferences.registerOnSharedPreferenceChangeListener(listener)
        repository.observeClouds(cloudObserver)
        renderControls()
        if (settings.read().clouds == EarthRenderer.CloudMode.LIVE) repository.requestClouds()
    }

    private fun renderControls() {
        controls.removeAllViews()
        heading(getString(R.string.wallpaper_settings_title))
        heading(getString(R.string.text_view_from))
        placeSummary = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(190, 205, 219))
            setPadding(0, 0, 0, dp(8))
        }
        controls.addView(placeSummary)
        updatePlaceSummary()
        val locationActions = LinearLayout(this)
        locationActions.addView(actionButton(getString(R.string.text_saved_places)) { chooseSavedPlace() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        locationActions.addView(actionButton(getString(R.string.text_search_cities)) { searchCities() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(locationActions)
        heading(getString(R.string.text_composition))
        radioOptions(WallpaperSettings.Preset.values().toList(), settings.read().preset,
            { when (it) {
                WallpaperSettings.Preset.WHOLE_EARTH -> getString(R.string.text_whole_earth)
                WallpaperSettings.Preset.NIGHT_LIGHTS -> getString(R.string.text_night_lights)
                WallpaperSettings.Preset.HORIZON -> getString(R.string.text_earth_s_horizon)
            } }) { settings.setPreset(it) }
        paragraph(when (settings.read().preset) {
            WallpaperSettings.Preset.WHOLE_EARTH -> getString(R.string.text_complete_globe_with_current_sunlight_and_quiet_stars)
            WallpaperSettings.Preset.NIGHT_LIGHTS -> getString(R.string.text_city_lights_on_the_dark_side_of_earth_with_current_sunlight)
            WallpaperSettings.Preset.HORIZON -> getString(R.string.text_a_close_cropped_view_of_earth_s_atmospheric_limb)
        })
        heading(getString(R.string.text_camera_motion))
        radioOptions(WallpaperSettings.Motion.values().toList(), settings.read().motion,
            { when (it) {
                WallpaperSettings.Motion.FIXED -> getString(R.string.text_fixed_viewpoint)
                WallpaperSettings.Motion.SLOW_ORBIT -> getString(R.string.text_slow_orbit)
                WallpaperSettings.Motion.FOLLOW_NIGHT -> getString(R.string.text_follow_the_night_side)
            } }) { settings.setMotion(it) }
        paragraph(when (settings.read().motion) {
            WallpaperSettings.Motion.FIXED -> getString(R.string.text_keeps_your_chosen_place_in_view_as_daylight_changes)
            WallpaperSettings.Motion.SLOW_ORBIT -> getString(R.string.text_starts_at_your_chosen_view_then_slowly_travels_around_earth)
            WallpaperSettings.Motion.FOLLOW_NIGHT -> getString(R.string.text_the_view_moves_with_the_night_side_instead_of_staying_over_a_plac)
        })
        controls.addView(CheckBox(this).apply {
            text = getString(R.string.text_turn_the_globe_when_i_swipe_between_home_screens)
            isChecked = settings.read().pageParallax
            minHeight = dp(48)
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, checked -> settings.setPageParallax(checked) }
        })
        heading(getString(R.string.companion_clouds))
        radioOptions(EarthRenderer.CloudMode.values().toList(), settings.read().clouds,
            { when (it) {
                EarthRenderer.CloudMode.OFF -> getString(R.string.companion_off)
                EarthRenderer.CloudMode.GENERATED -> getString(R.string.text_generated_illustration)
                EarthRenderer.CloudMode.LIVE -> getString(R.string.text_satellite_imagery_when_cached_or_available)
            } }) { settings.setClouds(it) }
        slider(getString(R.string.text_globe_scale), 70, 140, (settings.read().scale * 100).toInt()) { settings.setScale(it / 100f) }
        controls.addView(actionButton(if (advancedExpanded) getString(R.string.text_hide_framing_controls) else getString(R.string.text_adjust_framing_angles)) {
            advancedExpanded = !advancedExpanded
            renderControls()
        })
        if (advancedExpanded) {
            slider(getString(R.string.text_horizontal_framing), 0, 100, ((settings.read().frameX + 0.5f) * 100).toInt()) {
                settings.setFrameX(it / 100f - 0.5f)
            }
            slider(getString(R.string.text_vertical_framing), 0, 100, ((settings.read().frameY + 0.5f) * 100).toInt()) {
                settings.setFrameY(it / 100f - 0.5f)
            }
            slider(getString(R.string.text_view_angle), 0, 360, (settings.read().azimuth + 180).toInt()) {
                settings.setAzimuth(it - 180f)
            }
            slider(getString(R.string.text_view_latitude), 0, 160, (settings.read().elevation + 80).toInt()) {
                settings.setElevation(it - 80f)
            }
        }
    }

    private fun updatePlaceSummary() {
        val name = settings.placeName()
        placeSummary.text = when (settings.read().motion) {
            WallpaperSettings.Motion.FOLLOW_NIGHT -> getString(R.string.text_following_the_night_side_1_s, name?.let { getString(R.string.text_saved_view_1_s, it) } ?: "")
            WallpaperSettings.Motion.SLOW_ORBIT -> getString(R.string.text_orbit_starts_at_1_s, name ?: getString(R.string.text_your_custom_view))
            WallpaperSettings.Motion.FIXED -> name ?: getString(R.string.text_choose_a_place_or_adjust_the_angles_below)
        }
    }

    private fun choosePlace(place: SavedPlace) {
        settings.setPlace(place)
        renderControls()
    }

    private fun chooseSavedPlace() {
        val places = PlaceStore(this).all()
        if (places.isEmpty()) {
            AlertDialog.Builder(this).setTitle(getString(R.string.text_no_saved_places_yet))
                .setMessage(getString(R.string.text_search_for_a_city_here_you_can_also_save_a_custom_point_in_explor))
                .setPositiveButton(getString(R.string.text_search_cities)) { _, _ -> searchCities() }
                .setNegativeButton(getString(R.string.text_cancel), null).show()
            return
        }
        AlertDialog.Builder(this).setTitle(getString(R.string.text_view_from_a_saved_place))
            .setItems(places.map { it.name }.toTypedArray()) { _, index -> choosePlace(places[index]) }
            .setNegativeButton(getString(R.string.text_cancel), null).show()
    }

    private fun searchCities() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val search = EditText(this).apply {
            hint = getString(R.string.text_city_or_country)
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            contentDescription = getString(R.string.text_search_city_or_country)
        }
        val empty = TextView(this).apply {
            text = getString(R.string.text_no_matching_city_try_a_country_or_another_name)
            setPadding(0, dp(12), 0, dp(12))
            visibility = View.GONE
        }
        var matches = CityCatalog.search("", this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            matches.map { it.name }.toMutableList())
        val list = ListView(this).apply { this.adapter = adapter; emptyView = empty }
        content.addView(search)
        content.addView(empty)
        content.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            dp((resources.configuration.screenHeightDp * 0.4f).toInt().coerceIn(150, 340))))
        val dialog = AlertDialog.Builder(this).setTitle(getString(R.string.text_choose_a_city))
            .setView(content).setNegativeButton(getString(R.string.text_cancel), null).create()
        list.setOnItemClickListener { _, _, index, _ ->
            val place = matches[index]
            PlaceStore(this).save(place)
            choosePlace(place)
            dialog.dismiss()
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                matches = CityCatalog.search(s.toString(), this@WallpaperConfigActivity)
                adapter.clear()
                adapter.addAll(matches.map { it.name })
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        dialog.show()
    }

    private fun actionButton(label: String, primary: Boolean = false, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            minHeight = dp(48)
            setTextColor(if (primary) Color.rgb(8, 24, 39) else Color.rgb(190, 225, 241))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (primary) Color.rgb(128, 210, 203) else Color.rgb(25, 46, 66))
            }
            backgroundTintList = ColorStateList.valueOf(
                if (primary) Color.rgb(128, 210, 203) else Color.rgb(25, 46, 66))
            setOnClickListener { action() }
        }

    private fun heading(text: String) {
        controls.addView(TextView(this).apply {
            this.text = text
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, dp(16), 0, dp(8))
        })
    }
    private fun paragraph(text: String) {
        controls.addView(TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.rgb(190, 205, 219))
            setPadding(0, dp(6), 0, dp(12))
        })
    }
    private fun <T> radioOptions(
        options: List<T>, selected: T, label: (T) -> String, choose: (T) -> Unit
    ) {
        val group = RadioGroup(this)
        options.forEach { value ->
            group.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = label(value)
                isChecked = selected == value
                minHeight = dp(48)
                setTextColor(Color.WHITE)
                setOnClickListener { choose(value) }
            })
        }
        controls.addView(group)
    }
    private fun slider(label: String, min: Int, max: Int, current: Int, changed: (Int) -> Unit) {
        paragraph(label)
        controls.addView(SeekBar(this).apply {
            this.max = max - min
            progress = (current - min).coerceIn(0, this.max)
            contentDescription = label
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) changed(progress + min)
                }
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
            })
        })
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onResume() { super.onResume(); preview.onResume() }
    override fun onPause() { preview.onPause(); super.onPause() }
    override fun onDestroy() {
        settings.preferences.unregisterOnSharedPreferenceChangeListener(listener)
        repository.removeCloudObserver(cloudObserver)
        super.onDestroy()
    }
}
