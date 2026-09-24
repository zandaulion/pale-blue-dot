package com.globe.app.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.globe.app.GlobeRenderer
import com.globe.app.R
import com.globe.app.camera.OrbitCamera
import com.globe.app.data.EarthRepository
import com.globe.app.earth.CloudMapProvider
import com.globe.app.earth.EarthRenderer
import com.globe.app.render.TextureQuality
import com.globe.app.time.RealTimeSceneClock

/** Live in-app preview plus settings; Android owns the final preview/apply decision. */
class WallpaperConfigActivity : AppCompatActivity() {
    private lateinit var settings: WallpaperSettings
    private lateinit var preview: GLSurfaceView
    private lateinit var controls: LinearLayout
    private lateinit var renderer: GlobeRenderer
    private lateinit var controller: WallpaperSceneController
    private lateinit var repository: EarthRepository

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val next = settings.read()
        preview.queueEvent { controller.apply(next) }
        if (key == "preset") renderControls()
        if (settings.read().clouds == EarthRenderer.CloudMode.LIVE) repository.requestClouds()
    }
    private val cloudObserver: (CloudMapProvider.Result?, EarthRepository.State) -> Unit = { result, _ ->
        if (result != null) renderer.setCloudResult(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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
            LinearLayout.LayoutParams.MATCH_PARENT, dp(250)
        ))
        root.addView(TextView(this).apply {
            text = "Live preview · current sunlight · no audio"
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
        setContentView(root)
        settings.preferences.registerOnSharedPreferenceChangeListener(listener)
        repository.observeClouds(cloudObserver)
        renderControls()
        if (settings.read().clouds == EarthRenderer.CloudMode.LIVE) repository.requestClouds()
    }

    private fun renderControls() {
        controls.removeAllViews()
        heading(getString(R.string.wallpaper_settings_title))
        heading("Composition")
        radioOptions(WallpaperSettings.Preset.values().toList(), settings.read().preset,
            { when (it) {
                WallpaperSettings.Preset.WHOLE_EARTH -> "Whole Earth"
                WallpaperSettings.Preset.NIGHT_LIGHTS -> "Night Lights"
                WallpaperSettings.Preset.HORIZON -> "Earth's Horizon"
            } }) { settings.setPreset(it) }
        paragraph(when (settings.read().preset) {
            WallpaperSettings.Preset.WHOLE_EARTH -> "Complete globe with current sunlight and quiet stars."
            WallpaperSettings.Preset.NIGHT_LIGHTS -> "Follows the real night side by default; the Sun direction is never changed."
            WallpaperSettings.Preset.HORIZON -> "A close, cropped view of Earth's atmospheric limb."
        })
        heading("Camera motion")
        radioOptions(WallpaperSettings.Motion.values().toList(), settings.read().motion,
            { when (it) {
                WallpaperSettings.Motion.FIXED -> "Fixed viewpoint"
                WallpaperSettings.Motion.SLOW_ORBIT -> "Slow orbit"
                WallpaperSettings.Motion.FOLLOW_NIGHT -> "Follow the night side"
            } }) { settings.setMotion(it) }
        paragraph("Follow the night side updates the viewpoint as Earth turns. Fixed and slow orbit use the angle controls below.")
        controls.addView(CheckBox(this).apply {
            text = "Turn the globe when I swipe between home screens"
            isChecked = settings.read().pageParallax
            minHeight = dp(48)
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, checked -> settings.setPageParallax(checked) }
        })
        heading("Clouds")
        radioOptions(EarthRenderer.CloudMode.values().toList(), settings.read().clouds,
            { when (it) {
                EarthRenderer.CloudMode.OFF -> "Off"
                EarthRenderer.CloudMode.GENERATED -> "Generated illustration"
                EarthRenderer.CloudMode.LIVE -> "Satellite imagery, when cached or available"
            } }) { settings.setClouds(it) }
        slider("Globe scale", 70, 140, (settings.read().scale * 100).toInt()) { settings.setScale(it / 100f) }
        slider("Horizontal framing", 0, 100, ((settings.read().frameX + 0.5f) * 100).toInt()) {
            settings.setFrameX(it / 100f - 0.5f)
        }
        slider("Vertical framing", 0, 100, ((settings.read().frameY + 0.5f) * 100).toInt()) {
            settings.setFrameY(it / 100f - 0.5f)
        }
        slider("View angle", 0, 360, (settings.read().azimuth + 180).toInt()) {
            settings.setAzimuth(it - 180f)
        }
        slider("View latitude", 0, 160, (settings.read().elevation + 80).toInt()) {
            settings.setElevation(it - 80f)
        }
        controls.addView(Button(this).apply {
            text = getString(R.string.wallpaper_preview_apply)
            minHeight = dp(48)
            setOnClickListener {
                startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@WallpaperConfigActivity, EarthWallpaperService::class.java))
                })
            }
        })
        paragraph("Android shows the final preview and supported home/lock-screen choices before applying.")
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
