package com.globe.app

import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.RadioGroup
import android.widget.RadioButton
import android.widget.Switch
import android.widget.EditText
import android.text.InputType
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Handler
import android.os.Looper
import java.util.Calendar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import android.view.View
import com.globe.app.earth.EarthRenderer
import com.globe.app.indicators.IndicatorRenderer
import com.globe.app.eclipse.EclipseDetector
import com.globe.app.events.EarthEventsProvider
import com.globe.app.data.EarthRepository
import com.globe.app.data.TodayBriefing
import com.globe.app.places.PlaceStore
import com.globe.app.places.SavedPlace
import com.globe.app.places.CityCatalog
import com.globe.app.places.TimeZoneSuggester
import com.globe.app.places.Daylight
import com.globe.app.explore.JourneyContent
import com.globe.app.explore.JourneyStore
import com.globe.app.explore.JourneySceneSnapshot
import com.globe.app.explore.Journey
import com.globe.app.explore.JourneyStep
import com.globe.app.explore.FieldNotebook
import com.globe.app.explore.SavedObservation
import com.globe.app.render.LayerSettings
import com.globe.app.wallpaper.WallpaperConfigActivity
import com.globe.app.kids.ChallengeKind
import com.globe.app.kids.DailyFacts
import com.globe.app.kids.Discovery
import com.globe.app.kids.DiscoveryJournal
import com.globe.app.kids.MoonPhase
import com.globe.app.kids.ParentalGate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "globe_prefs"
        private const val PREF_MUSIC_ENABLED = "music_enabled"
        private const val PREF_MUSIC_VOLUME = "music_volume"
        private const val PREF_CLOUD_MODE = "cloud_mode"
        private const val PREF_CAM_AZ = "cam_az"
        private const val PREF_CAM_EL = "cam_el"
        private const val PREF_CAM_DIST = "cam_dist"
        private const val PREF_TODAY_SHOWN_DAY = "today_shown_day"
        private const val PREF_NARRATE = "narrate_enabled"
        private const val PREF_ONBOARDED = "onboarded"
    }

    private lateinit var globeView: GlobeSurfaceView
    private lateinit var cloudLabel: TextView
    private lateinit var eclipseLabel: TextView
    private lateinit var timeLabel: TextView
    private lateinit var timeScrubber: SeekBar
    private lateinit var legendButton: TextView
    private lateinit var legendOverlay: FrameLayout
    private lateinit var musicButton: TextView
    private lateinit var narrateButton: TextView
    private lateinit var volumeSlider: SeekBar
    private lateinit var onboardingOverlay: FrameLayout
    private lateinit var shareButton: TextView
    private lateinit var journalButton: TextView
    private lateinit var journalOverlay: FrameLayout
    private lateinit var journalColumn: LinearLayout
    private lateinit var todayButton: TextView
    private lateinit var todayOverlay: FrameLayout
    private lateinit var todayColumn: LinearLayout
    private lateinit var eventCard: TextView
    private lateinit var savePlaceButton: TextView
    /** Globe point behind the current day/night card, offered for saving. */
    private var pendingSavePoint: DoubleArray? = null
    private lateinit var challengeBanner: TextView
    private lateinit var prefs: SharedPreferences
    private lateinit var journal: DiscoveryJournal
    private lateinit var layers: LayerSettings
    private lateinit var repository: EarthRepository
    private lateinit var placeStore: PlaceStore
    private lateinit var journeyStore: JourneyStore
    private lateinit var fieldNotebook: FieldNotebook
    private lateinit var companionPanel: FrameLayout
    private lateinit var companionScroll: ScrollView
    private lateinit var companionColumn: LinearLayout
    private var openPanel: String? = null
    private var pendingWidgetEventId: String? = null
    private var observationsInSimulation = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private val timeTick = object : Runnable {
        override fun run() {
            if (::globeView.isInitialized && globeView.sceneClock.isExploring) {
                updateTimeLabel()
                uiHandler.postDelayed(this, 1_000L)
            }
        }
    }
    private val repositoryObserver: (EarthRepository.Snapshot) -> Unit = { snapshot ->
        if (::globeView.isInitialized) {
            globeView.renderer.setAllEvents(snapshot.events)
            if (openPanel == "Today" || openPanel == "Explore") showCompanionPanel(openPanel!!)
            resolveWidgetEvent(snapshot)
        }
    }
    private val cloudObserver: (com.globe.app.earth.CloudMapProvider.Result?, EarthRepository.State) -> Unit =
        { result, _ ->
            if (::globeView.isInitialized && result != null) globeView.renderer.setCloudResult(result)
            cloudTimestamp = result?.timestamp
            if (::globeView.isInitialized) updateCloudLabel()
        }

    // Challenge (quiz) mode state
    private var challengeKind: ChallengeKind? = null
    private var challengeScore = 0

    private var mediaPlayer: MediaPlayer? = null
    private var musicEnabled = false
    private var musicVolume = 0.4f
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var narrateEnabled = false
    private var scrubberAnimator: ValueAnimator? = null
    private var cloudTimestamp: String? = null
    private var lastEclipseState: EclipseDetector.EclipseState = EclipseDetector.EclipseState.NONE

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }

    /** Range of the scrubber: +/- 24 hours in milliseconds. */
    private val scrubberRangeMs = 24 * 60 * 60 * 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while the app is visible
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val dp = { value: Float ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
            ).toInt()
        }

        cloudLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
            text = "\u2601 Clouds: live (loading\u2026)"
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                cycleClouds()
            }
            isClickable = true
        }

        timeLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
            gravity = Gravity.CENTER
            text = "Time · Now"
            minHeight = dp(48f)
            contentDescription = "Time controls"
            setOnClickListener {
                if (::globeView.isInitialized && globeView.sceneClock.isExploring)
                    showCompanionPanel("Explore time")
                else timeScrubber.visibility =
                    if (timeScrubber.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }

        timeScrubber = SeekBar(this).apply {
            max = 1000
            progress = 500 // center = now
            visibility = View.GONE
            contentDescription = "Move time up to 24 hours before or after now"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val fraction = (progress - 500) / 500.0
                    globeView.sceneClock.offsetMs = (fraction * scrubberRangeMs).toLong()
                    updateTimeLabel()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    scrubberAnimator?.cancel()
                    // Keep the globe from drifting into idle auto-rotation while scrubbing.
                    globeView.camera.notifyInteraction()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    seekBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    globeView.camera.notifyInteraction()
                    awardTimeTraveler()
                    // Animate back to "now" over 1 second
                    val startProgress = seekBar.progress
                    scrubberAnimator?.cancel()
                    scrubberAnimator = ValueAnimator.ofInt(startProgress, 500).apply {
                        duration = 1000
                        interpolator = DecelerateInterpolator(2f)
                        addUpdateListener { anim ->
                            val progress = anim.animatedValue as Int
                            seekBar.progress = progress
                            val fraction = (progress - 500) / 500.0
                            globeView.sceneClock.offsetMs = (fraction * scrubberRangeMs).toLong()
                            updateTimeLabel()
                        }
                        start()
                    }
                    seekBar.postDelayed({ timeScrubber.visibility = View.GONE }, 4_000L)
                }
            })
        }

        eclipseLabel = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
            visibility = View.GONE
        }

        legendButton = TextView(this).apply {
            text = "?"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
            setBackgroundColor(Color.argb(100, 255, 255, 255))
            gravity = Gravity.CENTER
            val size = dp(36f)
            minimumWidth = size
            minimumHeight = size
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                showLegend()
            }
        }

        legendOverlay = createLegendOverlay(dp)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        layers = LayerSettings(prefs)
        repository = EarthRepository.get(applicationContext)
        placeStore = PlaceStore(applicationContext)
        journeyStore = JourneyStore(applicationContext)
        journal = DiscoveryJournal(this)
        fieldNotebook = FieldNotebook(this)
        fieldNotebook.migrateLegacy(journal)
        journalOverlay = createJournalOverlay()
        todayOverlay = createTodayOverlay()
        musicEnabled = prefs.getBoolean(PREF_MUSIC_ENABLED, false)
        musicVolume = prefs.getFloat(PREF_MUSIC_VOLUME, 0.4f)
        narrateEnabled = prefs.getBoolean(PREF_NARRATE, false)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
            }
        }

        musicButton = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                toggleMusic()
            }
            isClickable = true
        }
        updateMusicButton()

        // Music volume slider — shown under the Music toggle only when music is on
        volumeSlider = SeekBar(this).apply {
            max = 100
            progress = (musicVolume * 100).toInt()
            visibility = if (musicEnabled) View.VISIBLE else View.GONE
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    musicVolume = progress / 100f
                    mediaPlayer?.setVolume(musicVolume, musicVolume)
                    prefs.edit().putFloat(PREF_MUSIC_VOLUME, musicVolume).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }

        narrateButton = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                toggleNarration()
            }
            isClickable = true
        }
        updateNarrateButton()

        shareButton = makePillButton("↑  Share", dp).apply {
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                shareCurrentView()
            }
        }

        journalButton = makePillButton("📖", dp).apply {
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                showJournal()
            }
        }
        refreshJournalButton()

        todayButton = makePillButton("🗓  Today", dp).apply {
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                showToday()
            }
        }

        // Info card shown when the user taps a marker or a spot on the globe
        eventCard = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setLineSpacing(dp(3f).toFloat(), 1f)
            maxWidth = dp(320f)
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            background = GradientDrawable().apply {
                cornerRadius = dp(12f).toFloat()
                setColor(Color.argb(170, 10, 18, 30))
                setStroke(dp(1f), Color.argb(60, 255, 255, 255))
            }
            visibility = View.GONE
            setOnClickListener { hideEventCard() }
        }
        savePlaceButton = makePillButton("+  Save this place", dp).apply {
            minHeight = dp(48f)
            gravity = Gravity.CENTER
            visibility = View.GONE
            setOnClickListener { pendingSavePoint?.let { savePlaceAt(it[0], it[1]) } }
        }

        // Challenge prompt banner — top center, shown only during a challenge
        challengeBanner = makePillButton("", dp).apply {
            maxWidth = dp(340f)
            gravity = Gravity.CENTER
            setLineSpacing(dp(2f).toFloat(), 1f)
            visibility = View.GONE
            setOnClickListener { stopChallenge() }
        }

        globeView = GlobeSurfaceView(
            context = this,
            onCloudStatusChanged = { timestamp ->
                cloudTimestamp = timestamp
                runOnUiThread { updateCloudLabel() }
            },
            onEclipseStateChanged = { state ->
                if (state != lastEclipseState) {
                    lastEclipseState = state
                    runOnUiThread { updateEclipseLabel(state) }
                }
            },
            onGlobeTap = { x, y -> onGlobeTapped(x, y) }
        )

        // Restore saved view state (camera pose + cloud visibility)
        if (prefs.contains(PREF_CAM_AZ)) {
            globeView.camera.restore(
                prefs.getFloat(PREF_CAM_AZ, globeView.camera.azimuth),
                prefs.getFloat(PREF_CAM_EL, globeView.camera.elevation),
                prefs.getFloat(PREF_CAM_DIST, globeView.camera.distance)
            )
        }
        applyAppLayers()
        globeView.renderer.showPlacePins = true
        globeView.renderer.setPlaces(placeStore.all())
        if (prefs.getBoolean("explore_time_active", false)) {
            globeView.sceneClock.enter(prefs.getLong("explore_time_ms", System.currentTimeMillis()))
            applySimulationObservations()
            globeView.renderer.showLatitudeGuides = true
            uiHandler.post(timeTick)
        }
        repository.observeClouds(cloudObserver)
        repository.observe(repositoryObserver)
        repository.refresh()
        updateCloudLabel()

        val margin = dp(12f)

        val root = FrameLayout(this)
        root.addView(globeView)

        // Time label — top center
        root.addView(timeLabel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { setMargins(margin, dp(14f), margin, 0) })

        // Sun/Moon arrows sit just above the 56dp bottom bar; the scrubber and card stack above them.
        globeView.renderer.indicatorRenderer.bottomOffsetPx = dp(56f + 32f).toFloat()

        // Time scrubber — above the indicator arrows
        root.addView(timeScrubber, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply { setMargins(margin, 0, margin, dp(112f)) })

        // Event info card — bottom center, above the time scrubber; Save place sits under day/night cards
        val eventCardBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(eventCard, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(savePlaceButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8f) })
        }
        root.addView(eventCardBox, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { setMargins(margin, 0, margin, dp(184f)) })

        // Challenge banner — top center, below the time label
        root.addView(challengeBanner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { setMargins(margin, dp(72f), margin, 0) })

        // Legend overlay — full screen, initially hidden
        root.addView(legendOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Discovery journal overlay — full screen, initially hidden
        root.addView(journalOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Today overlay — full screen, initially hidden
        root.addView(todayOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Onboarding overlay — full screen, shown once on the very first launch
        onboardingOverlay = createOnboardingOverlay()
        root.addView(onboardingOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        companionPanel = createCompanionPanel()

        val settingsButton = makePillButton(getString(R.string.companion_settings), dp).apply {
            minHeight = dp(48f)
            contentDescription = getString(R.string.companion_settings)
            setOnClickListener { showCompanionPanel("Settings") }
        }
        root.addView(settingsButton, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, dp(48f), Gravity.TOP or Gravity.END
        ).apply { setMargins(margin, dp(8f), margin, 0) })

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(225, 9, 17, 29))
            for ((label, panel) in listOf(
                getString(R.string.companion_today) to "Today",
                getString(R.string.companion_layers) to "Layers",
                getString(R.string.companion_explore) to "Explore"
            )) {
                addView(TextView(this@MainActivity).apply {
                    text = label
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    gravity = Gravity.CENTER
                    minHeight = dp(56f)
                    contentDescription = label
                    setOnClickListener { showCompanionPanel(panel) }
                }, LinearLayout.LayoutParams(0, dp(56f), 1f))
            }
        }
        root.addView(nav, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(56f), Gravity.BOTTOM
        ))

        root.addView(companionPanel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            insets
        }

        setContentView(root)
        updateTimeLabel()
        root.post { handleWidgetIntent(intent) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!closeTopLayer()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (!prefs.getBoolean(PREF_ONBOARDED, false)) {
            // A short, dismissible instruction leaves the globe in view.
            onboardingOverlay.visibility = View.VISIBLE
        }
    }

    /** Cycle clouds: off -> generated -> live -> off. */
    private fun cycleClouds() {
        val earth = globeView.renderer.earthRenderer
        earth.cloudMode = when (earth.cloudMode) {
            EarthRenderer.CloudMode.OFF -> EarthRenderer.CloudMode.GENERATED
            EarthRenderer.CloudMode.GENERATED -> EarthRenderer.CloudMode.LIVE
            EarthRenderer.CloudMode.LIVE -> EarthRenderer.CloudMode.OFF
        }
        layers.cloudMode = earth.cloudMode
        updateCloudLabel()
    }

    private fun createCompanionPanel(): FrameLayout {
        val overlay = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.argb(150, 0, 5, 12))
            isClickable = true
            setOnClickListener { closeCompanionPanel() }
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            background = GradientDrawable().apply {
                cornerRadius = dpi(18f).toFloat()
                setColor(Color.rgb(17, 27, 41))
                setStroke(dpi(1f), Color.rgb(64, 86, 107))
            }
            isClickable = true
            setOnClickListener { }
        }
        companionScroll = scroll
        companionColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(20f), dpi(16f), dpi(20f), dpi(24f))
        }
        scroll.addView(companionColumn)
        overlay.addView(scroll)
        overlay.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val available = overlay.width
            if (available > 0) {
                val wide = available >= dpi(600f)
                val desiredWidth = if (wide) dpi(420f) else (available - dpi(24f)).coerceAtLeast(dpi(280f))
                val desiredHeight = if (wide) overlay.height - dpi(24f) else overlay.height - dpi(32f)
                if (scroll.layoutParams.width == desiredWidth && scroll.layoutParams.height == desiredHeight) return@addOnLayoutChangeListener
                scroll.layoutParams = FrameLayout.LayoutParams(
                    desiredWidth, desiredHeight,
                    if (wide) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.CENTER
                ).apply { setMargins(dpi(12f), dpi(12f), dpi(12f), dpi(12f)) }
            }
        }
        return overlay
    }

    private fun closeCompanionPanel() {
        companionPanel.visibility = View.GONE
        openPanel = null
    }

    private fun panelText(text: String, size: Float = 15f, color: Int = Color.WHITE): TextView =
        TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(color)
            setPadding(0, dpi(8f), 0, dpi(8f))
        }

    private fun panelAction(label: String, action: () -> Unit) {
        companionColumn.addView(panelText(label, 16f, Color.rgb(173, 215, 255)).apply {
            minHeight = dpi(48f)
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener { action() }
        })
    }

    private fun panelSwitch(label: String, description: String, checked: Boolean, action: (Boolean) -> Unit) {
        companionColumn.addView(Switch(this).apply {
            text = "$label\n$description"
            isChecked = checked
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            minHeight = dpi(60f)
            setPadding(0, dpi(4f), 0, dpi(4f))
            setOnCheckedChangeListener { _, value -> action(value) }
        })
    }

    private fun showCompanionPanel(name: String) {
        openPanel = name
        companionColumn.removeAllViews()
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heading.addView(panelText(name, 23f).apply { typeface = Typeface.DEFAULT_BOLD },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        heading.addView(panelText("×", 30f).apply {
            contentDescription = getString(R.string.companion_close)
            gravity = Gravity.CENTER
            minWidth = dpi(48f)
            minHeight = dpi(48f)
            setOnClickListener { closeCompanionPanel() }
        })
        companionColumn.addView(heading)
        when (name) {
            "Today" -> populateCompanionToday()
            "Layers" -> populateCompanionLayers()
            "Explore" -> populateCompanionExplore()
            "Settings" -> populateCompanionSettings()
            "Notebook" -> populateCompanionNotebook()
            "Guide" -> populateCompanionGuide()
            "Places" -> populatePlaces()
            "Explore time" -> populateExploreTime()
            "Journeys" -> populateJourneys()
            "Journey" -> populateJourney()
        }
        companionPanel.visibility = View.VISIBLE
        companionScroll.post { companionScroll.scrollTo(0, 0) }
    }

    private fun populateCompanionToday() {
        val briefing = TodayBriefing.build(System.currentTimeMillis(), placeStore.primary(), repository.snapshot())
        companionColumn.addView(panelText("Planetary briefing · real time", 14f, Color.rgb(173, 215, 255)))
        companionColumn.addView(panelText("${briefing.moon.emoji} ${briefing.moon.name} · ${briefing.moon.illuminationPercent}% estimated illumination", 17f))
        val place = briefing.place
        if (place == null) panelAction("Choose a primary place") { showCompanionPanel("Places") }
        else {
            companionColumn.addView(panelText("${place.name}\n${formatPlaceDaylight(place, briefing.daylight!!)}", 15f))
            panelAction("Fly to ${place.name}") { closeCompanionPanel(); globeView.camera.flyTo(place.lat, place.lon) }
        }
        companionColumn.addView(panelText(
            cloudTimestamp?.let {
                "Satellite imagery — $it" +
                    if (repository.cloudState == EarthRepository.State.STALE) " · saved copy" else ""
            } ?: "Satellite imagery unavailable or still loading",
            13f, Color.rgb(190, 205, 219)
        ))
        companionColumn.addView(panelText(briefing.prompt, 15f))
        companionColumn.addView(panelText("One observation · ${briefing.eventMessage}", 17f))
        briefing.event?.let { event ->
            panelAction("${event.type.name.lowercase().replaceFirstChar { it.uppercase() }} · ${event.title}\n${event.observedAtMs?.let(::formatDate) ?: "Time unavailable"} · ${event.source}") {
                if (repository.snapshot().events.any { it.id == event.id }) {
                    closeCompanionPanel(); globeView.camera.flyTo(event.lat, event.lon); showEventCard(event)
                } else AlertDialog.Builder(this).setMessage("This observation is no longer in the saved feed.")
                    .setPositiveButton("Current events") { _, _ -> showCompanionPanel("Explore") }.show()
            }
        }
        panelAction(getString(R.string.companion_refresh)) { repository.refresh(manual = true) }
    }

    private fun populateCompanionLayers() {
        companionColumn.addView(panelText("Choose what appears on the globe. Hidden events also leave the event list and challenges.",
            14f, Color.rgb(190, 205, 219)))
        companionColumn.addView(panelText(getString(R.string.companion_clouds), 18f))
        val cloudChoices = listOf(EarthRenderer.CloudMode.OFF, EarthRenderer.CloudMode.GENERATED, EarthRenderer.CloudMode.LIVE)
        val cloudLabels = listOf(R.string.companion_off, R.string.companion_generated, R.string.companion_satellite)
        val radio = RadioGroup(this)
        cloudChoices.forEachIndexed { index, mode ->
            radio.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = getString(cloudLabels[index])
                setTextColor(Color.WHITE)
                minHeight = dpi(48f)
                isChecked = layers.cloudMode == mode
                setOnClickListener {
                    layers.cloudMode = mode
                    applyAppLayers()
                    updateCloudLabel()
                }
            })
        }
        companionColumn.addView(radio)
        companionColumn.addView(panelText(cloudTimestamp?.let { "Satellite imagery — $it" +
            if (repository.cloudState == EarthRepository.State.STALE) " · saved copy" else "" }
            ?: "Satellite imagery may be unavailable offline; generated clouds still work.", 13f,
            Color.rgb(190, 205, 219)))
        val descriptions = mapOf(
            EarthEventsProvider.Event.Type.EARTHQUAKE to "Reported by USGS",
            EarthEventsProvider.Event.Type.VOLCANO to "Reported by NASA EONET",
            EarthEventsProvider.Event.Type.WILDFIRE to "Reported by NASA EONET",
            EarthEventsProvider.Event.Type.STORM to "Reported by NASA EONET"
        )
        for (type in EarthEventsProvider.Event.Type.values()) {
            panelSwitch(type.name.lowercase().replaceFirstChar { it.uppercase() }, descriptions.getValue(type),
                layers.enabled(type)) {
                layers.setEnabled(type, it)
                applyAppLayers()
            }
        }
        panelSwitch("Constellations", "Catalog lines in the star background", layers.constellations) {
            layers.constellations = it; applyAppLayers()
        }
        panelSwitch("ISS illustration", "Approximate orbit, not live tracking", layers.iss) {
            layers.iss = it; applyAppLayers()
        }
        panelSwitch("Aurora illustration", "Decorative glow, not a forecast", layers.aurora) {
            layers.aurora = it; applyAppLayers()
        }
        panelSwitch("Day/night boundary", "Calculated terminator line", layers.terminator) {
            layers.terminator = it; applyAppLayers()
        }
        panelSwitch("Plate boundaries", "Offline PB2002 model · gold lines", layers.plateBoundaries) {
            layers.plateBoundaries = it; applyAppLayers()
        }
        companionColumn.addView(panelText("Plate boundaries: gold lines mark modelled plate edges. Earthquakes can also happen away from them. Source: Bird (2003), PB2002; ODbL 1.0.", 13f))
    }

    private fun populateCompanionExplore() {
        panelAction("Places · ${placeStore.all().size} saved") { showCompanionPanel("Places") }
        panelAction("Explore time and seasons") { showCompanionPanel("Explore time") }
        panelAction("Guided journeys") { showCompanionPanel("Journeys") }
        panelAction("Field notebook · ${journal.unlockedCount()}/${journal.total} discoveries") {
            showCompanionPanel("Notebook")
        }
        panelAction("Try a quick challenge") { closeCompanionPanel(); startChallenge() }
        panelAction("Scene guide and legend") { showCompanionPanel("Guide") }
        companionColumn.addView(panelText(getString(R.string.companion_events), 19f))
        addEventList(limitPerType = 12)
    }

    private fun populateCompanionSettings() {
        panelSwitch("Ambient music", "Only while the app is open", musicEnabled) { enabled ->
            if (musicEnabled != enabled) toggleMusic()
        }
        companionColumn.addView(panelText("Music volume", 14f))
        companionColumn.addView(SeekBar(this).apply {
            max = 100; progress = (musicVolume * 100).toInt()
            contentDescription = "Music volume"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    if (fromUser) {
                        musicVolume = value / 100f
                        mediaPlayer?.setVolume(musicVolume, musicVolume)
                        prefs.edit().putFloat(PREF_MUSIC_VOLUME, musicVolume).apply()
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
            })
        })
        panelSwitch("Read aloud", "Speak cards when narration is enabled", narrateEnabled) { enabled ->
            if (narrateEnabled != enabled) toggleNarration()
        }
        panelSwitch(getString(R.string.companion_reduce_motion),
            "Stops idle spin and decorative marker movement", layers.reduceMotion) {
            layers.reduceMotion = it; applyAppLayers()
        }
        panelAction("Share globe image") { closeCompanionPanel(); shareCurrentView() }
        panelAction("Earth live wallpaper") {
            closeCompanionPanel()
            startActivity(Intent(this, WallpaperConfigActivity::class.java))
        }
        panelAction("Scene guide and legend") { showCompanionPanel("Guide") }
    }

    private fun populateCompanionNotebook() {
        panelAction("‹ Explore") { showCompanionPanel("Explore") }
        fieldNotebook.migrateLegacy(journal)
        companionColumn.addView(panelText("Completed journeys", 19f))
        val completed = JourneyContent.all(this).filter { journeyStore.completed(it.id) }
        if (completed.isEmpty()) companionColumn.addView(panelText("No journeys completed yet.", 14f))
        completed.forEach { companionColumn.addView(panelText("✓ ${it.title}", 15f)) }
        companionColumn.addView(panelText("Saved observations", 19f))
        val saved = fieldNotebook.savedEvents()
        if (saved.isEmpty()) companionColumn.addView(panelText("Save an observation from its card to keep its source and date.", 14f))
        saved.asReversed().forEach { item ->
            panelAction("${item.category.lowercase().replaceFirstChar { it.uppercase() }} · ${item.title}") {
                showSavedObservation(item)
            }
        }
        companionColumn.addView(panelText("Earlier discoveries · ${fieldNotebook.legacyDiscoveries().size}/${journal.total}", 19f))
        val descriptions = mapOf(
            Discovery.EARTHQUAKE to "You explored a reported earthquake.",
            Discovery.VOLCANO to "You explored a reported volcanic event.",
            Discovery.WILDFIRE to "You explored a reported wildfire.",
            Discovery.STORM to "You explored a reported storm.",
            Discovery.DAYTIME to "You found a place facing the Sun.",
            Discovery.NIGHT to "You found Earth's night side.",
            Discovery.STARGAZER to "You looked beyond Earth.",
            Discovery.TIME_TRAVELER to "You changed the scene time."
        )
        fieldNotebook.legacyDiscoveries().forEach {
            companionColumn.addView(panelText("${it.emoji} ${it.title}\n${descriptions[it]}", 15f))
        }
    }

    private fun showSavedObservation(item: SavedObservation) {
        val facts = buildString {
            append(item.title).append("\n")
            append("Observed: ").append(item.observedAtMs?.let(::formatDate) ?: "Time not reported").append("\n")
            append("Source: ").append(item.source).append("\n")
            item.magnitude?.let { append("Magnitude: ${"%.1f".format(Locale.getDefault(), it)}\n") }
            item.depthKm?.let { append("Depth: ${"%.1f".format(Locale.getDefault(), it)} km\n") }
            item.sourceUrl?.let { append("Source link: $it") }
        }
        AlertDialog.Builder(this).setTitle("Saved observation").setMessage(facts)
            .setPositiveButton("Fly to") { _, _ -> closeCompanionPanel(); globeView.camera.flyTo(item.lat, item.lon) }
            .setNegativeButton("Close", null).show()
    }

    private fun populateCompanionGuide() {
        panelAction("‹ Explore") { showCompanionPanel("Explore") }
        listOf(
            "Sunlight and city lights" to "Calculated sunlight shows the current day and night sides.",
            "Clouds" to "Generated clouds are illustrative. Satellite mode derives opacity from a dated NASA VIIRS image.",
            "Earthquakes" to "USGS reports magnitude 4.5+ earthquakes from the last seven days.",
            "Volcanoes, wildfires and storms" to "NASA EONET open reports may have older observation dates.",
            "ISS" to "Illustrated orbit, not live tracking.",
            "Aurora" to "Decorative illustration, not a forecast.",
            "Day/night boundary" to "Calculated terminator line.",
            "Constellations" to "Catalog stars joined into familiar patterns.",
            "Plate boundaries" to "A simplified offline PB2002 model. Many earthquakes occur near boundaries, though some occur elsewhere. Gold lines are not a diagnosis for any event."
        ).forEach { (title, body) -> companionColumn.addView(panelText("$title\n$body", 15f)) }
    }

    private fun populatePlaces() {
        panelAction("‹ Explore") { showCompanionPanel("Explore") }
        companionColumn.addView(panelText("Saved places stay on this device. Cyan rings mark them on the globe.", 14f))
        panelAction("Search bundled cities") {
            val query = EditText(this).apply {
                hint = "City or country (blank lists all ${CityCatalog.cities.size})"; inputType = InputType.TYPE_CLASS_TEXT
            }
            AlertDialog.Builder(this).setTitle("Find a city").setView(query)
                .setPositiveButton("Search") { _, _ ->
                    val matches = CityCatalog.search(query.text.toString())
                    if (matches.isEmpty()) AlertDialog.Builder(this).setMessage("No bundled city matches that name.")
                        .setPositiveButton("OK", null).show()
                    else AlertDialog.Builder(this).setTitle("Bundled cities")
                        .setItems(matches.map { it.name }.toTypedArray()) { _, which ->
                            placeStore.save(matches[which]); placesChanged(); showPlaceDetails(matches[which])
                        }.show()
                }.setNegativeButton("Cancel", null).show()
        }
        panelAction("Add a place from the globe") {
            closeCompanionPanel()
            AlertDialog.Builder(this).setMessage("Tap a point on the globe, then tap + Save this place under its card.")
                .setPositiveButton("OK", null).show()
        }
        val primary = placeStore.primaryId()
        placeStore.all().forEach { place ->
            panelAction("${if (place.id == primary) "★ " else ""}${place.name}\n${place.zoneId ?: "Time zone unset"}") {
                showPlaceDetails(place)
            }
        }
    }

    private fun placesChanged() {
        globeView.renderer.setPlaces(placeStore.all())
        if (openPanel == "Places" || openPanel == "Today") showCompanionPanel(openPanel!!)
    }

    private fun showPlaceDetails(place: SavedPlace) {
        val current = placeStore.all().firstOrNull { it.id == place.id } ?: return
        val summary = Daylight.at(current, System.currentTimeMillis())
        val options = arrayOf("Fly to", "Set as primary", "Rename", "Set time zone", "Delete")
        AlertDialog.Builder(this).setTitle(current.name + "\n" + formatPlaceDaylight(current, summary) +
            "\n${"%.3f".format(Locale.getDefault(), current.lat)}°, " +
            "${"%.3f".format(Locale.getDefault(), current.lon)}°")
            .setItems(options) { _, which -> when (which) {
                0 -> { closeCompanionPanel(); globeView.camera.flyTo(current.lat, current.lon) }
                1 -> { placeStore.setPrimary(current.id); placesChanged() }
                2 -> editPlaceName(current)
                3 -> editPlaceZone(current)
                4 -> {
                    val wasPrimary = placeStore.primaryId() == current.id
                    placeStore.delete(current.id); placesChanged()
                    AlertDialog.Builder(this).setMessage("${current.name} removed")
                        .setPositiveButton("Undo") { _, _ ->
                            placeStore.save(current)
                            if (wasPrimary) placeStore.setPrimary(current.id)
                            placesChanged()
                        }.setNegativeButton("Done", null).show()
                }
            } }.setNegativeButton("Close", null).show()
    }

    private fun editPlaceName(place: SavedPlace) {
        val input = EditText(this).apply { setText(place.name); selectAll() }
        AlertDialog.Builder(this).setTitle("Name this place").setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) { placeStore.save(place.copy(name = name)); placesChanged() }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun editPlaceZone(place: SavedPlace, suggestedZone: String? = null) {
        val input = EditText(this).apply {
            hint = "IANA zone, e.g. Europe/Bucharest"
            setText(place.zoneId ?: suggestedZone ?: "")
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val suggestion = if (place.zoneId == null && suggestedZone != null)
            "Suggested from the nearest town; change it if this place is across a border. " else ""
        AlertDialog.Builder(this).setTitle("Time zone for ${place.name}")
            .setMessage(suggestion + "Enter an IANA time-zone ID, or leave blank to keep local clock and sunrise unset.")
            .setView(input).setPositiveButton("Save") { _, _ ->
                val zone = input.text.toString().trim().ifBlank { null }
                if (zone == null || PlaceStore.validZone(zone)) {
                    placeStore.save(place.copy(zoneId = zone)); placesChanged()
                } else AlertDialog.Builder(this).setMessage("Unknown IANA time-zone ID: $zone")
                    .setPositiveButton("Try again") { _, _ -> editPlaceZone(place) }.show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun formatPlaceDaylight(place: SavedPlace, info: Daylight.Summary): String {
        val zone = place.zoneId ?: return "Time zone unset · choose a zone for local clock and sunrise"
        val formatter = SimpleDateFormat("EEE, MMM d · HH:mm z", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone(zone)
        }
        val clock = formatter.format(Date(info.localTimeMs))
        val sun = when (info.state) {
            Daylight.State.POLAR_DAY -> "Polar day · Sun stays above the horizon"
            Daylight.State.POLAR_NIGHT -> "Polar night · Sun stays below the horizon"
            Daylight.State.ZONE_UNSET -> "Time zone unset"
            Daylight.State.ORDINARY -> {
                val hour = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone(zone) }
                val rise = info.sunriseMs?.let { hour.format(Date(it)) } ?: "not available"
                val set = info.sunsetMs?.let { hour.format(Date(it)) } ?: "not available"
                val duration = info.daylightMinutes?.let { " · about ${it / 60}h ${it % 60}m daylight" } ?: ""
                "Approx. sunrise $rise · sunset $set$duration"
            }
        }
        return "$clock · ${if (info.isDay) "day" else "night"}\n$sun"
    }

    private fun populateExploreTime() {
        panelAction("‹ Explore") { showCompanionPanel("Explore") }
        val clock = globeView.sceneClock
        if (!clock.isExploring) {
            companionColumn.addView(panelText("Choose a date to hold the globe in an explicit lesson time. Today and wallpaper remain on real time.", 15f))
            panelAction("Begin at current time") { enterExploreTime(System.currentTimeMillis()) }
        } else {
            companionColumn.addView(panelText("SIMULATED · ${timeFormat.format(Date(clock.nowMs()))}", 17f, Color.rgb(255, 225, 160)))
            panelAction("Choose date and time") { chooseExploreDate() }
            panelAction(if (clock.isPlaying) "Pause" else "Play at real-time speed") {
                clock.setPlaying(!clock.isPlaying); persistExploreTime(); showCompanionPanel("Explore time")
            }
            panelAction("Advance one hour") { clock.seek(clock.nowMs() + 3_600_000L); persistExploreTime(); updateTimeLabel(); showCompanionPanel("Explore time") }
            panelAction("Advance one day") { clock.seek(clock.nowMs() + 86_400_000L); persistExploreTime(); updateTimeLabel(); showCompanionPanel("Explore time") }
            panelAction("Now · leave time exploration") { exitExploreTime() }
            panelSwitch("Compare latest observations", "These reports are from real dates, not the simulated date.", observationsInSimulation) {
                observationsInSimulation = it; applySimulationObservations()
            }
            val year = Calendar.getInstance().apply { timeInMillis = clock.nowMs() }.get(Calendar.YEAR)
            val start = Calendar.getInstance().apply { clear(); set(year, Calendar.JANUARY, 1, 12, 0) }
            val currentDay = Calendar.getInstance().apply { timeInMillis = clock.nowMs() }.get(Calendar.DAY_OF_YEAR)
            companionColumn.addView(panelText("Year timeline · $year", 15f))
            companionColumn.addView(SeekBar(this).apply {
                max = start.getActualMaximum(Calendar.DAY_OF_YEAR) - 1
                progress = currentDay - 1
                contentDescription = "Choose day of year"
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val date = start.clone() as Calendar
                            date.add(Calendar.DAY_OF_YEAR, value)
                            clock.seek(date.timeInMillis); updateTimeLabel()
                        }
                    }
                    override fun onStartTrackingTouch(bar: SeekBar) {}
                    override fun onStopTrackingTouch(bar: SeekBar) { persistExploreTime(); showCompanionPanel("Explore time") }
                })
            })
        }
        companionColumn.addView(panelText("Seasons · Earth's axis stays tilted about 23.4° as Earth orbits the Sun. Sunlight moves between hemispheres across the year; the scene's Sun already accounts for this tilt.", 15f))
        companionColumn.addView(object : View(this) {
            private val paint = Paint(3)
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width * .5f; val cy = height * .5f; val r = height * .27f
                paint.style = Paint.Style.STROKE; paint.strokeWidth = dpi(2f).toFloat()
                paint.color = Color.rgb(165, 210, 255); canvas.drawCircle(cx, cy, r, paint)
                // A 23.4 degree axis leans relative to the perpendicular of sunlight.
                val tilt = Math.toRadians(23.44)
                val dx = (kotlin.math.sin(tilt) * r * 1.55).toFloat()
                val dy = (kotlin.math.cos(tilt) * r * 1.55).toFloat()
                paint.color = Color.rgb(255, 224, 151)
                canvas.drawLine(cx - dx, cy + dy, cx + dx, cy - dy, paint)
                paint.style = Paint.Style.FILL; paint.textSize = dpi(12f).toFloat()
                canvas.drawText("23.4° axis", cx + r * .65f, cy - r * .8f, paint)
                paint.color = Color.rgb(255, 240, 177)
                for (i in 0..2) {
                    val y = cy - r * .65f + i * r * .65f
                    canvas.drawLine(dpi(8f).toFloat(), y, cx-r, y, paint)
                }
                canvas.drawText("Sunlight →", dpi(5f).toFloat(), cy + r * 1.3f, paint)
            }
        }.apply { minimumHeight = dpi(170f) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpi(170f)))
        val current = clock.nowMs()
        val north = CityCatalog.cities.first { it.id == "city:london" }
        val south = CityCatalog.cities.first { it.id == "city:sydney" }
        listOf(north, south).forEach { p ->
            val day = Daylight.at(p, current)
            val duration = when (day.state) {
                Daylight.State.POLAR_DAY -> "polar day"
                Daylight.State.POLAR_NIGHT -> "polar night"
                else -> day.daylightMinutes?.let { "about ${it / 60}h ${it % 60}m daylight" } ?: "daylight changing"
            }
            panelAction("${p.name} · $duration") { closeCompanionPanel(); globeView.camera.flyTo(p.lat, p.lon) }
        }
        companionColumn.addView(panelText("Guides: equator, tropics (±23.4°), and polar circles (±66.6°). Compare the two hemispheres in March, June, September, and December.", 13f))
    }

    private fun enterExploreTime(ms: Long) {
        scrubberAnimator?.cancel(); timeScrubber.visibility = View.GONE
        globeView.sceneClock.enter(ms)
        persistExploreTime()
        observationsInSimulation = false
        applySimulationObservations()
        globeView.renderer.showLatitudeGuides = true
        updateTimeLabel(); uiHandler.removeCallbacks(timeTick); uiHandler.post(timeTick)
        showCompanionPanel("Explore time")
    }

    private fun exitExploreTime() {
        globeView.sceneClock.exit()
        uiHandler.removeCallbacks(timeTick)
        globeView.renderer.showLatitudeGuides = false
        globeView.renderer.showEvents = true
        applyAppLayers()
        prefs.edit().putBoolean("explore_time_active", false).apply()
        updateTimeLabel()
        showCompanionPanel("Explore time")
    }

    private fun persistExploreTime() {
        prefs.edit()
            .putBoolean("explore_time_active", globeView.sceneClock.isExploring)
            .putLong("explore_time_ms", globeView.sceneClock.nowMs())
            .apply()
    }

    private fun applySimulationObservations() {
        val active = globeView.sceneClock.isExploring
        globeView.renderer.showEvents = !active || observationsInSimulation
        globeView.renderer.earthRenderer.cloudMode = if (active && !observationsInSimulation)
            EarthRenderer.CloudMode.OFF else layers.cloudMode
    }

    private fun applyAppLayers() {
        globeView.renderer.applyLayers(layers)
        applySimulationObservations()
    }

    private fun chooseExploreDate() {
        val c = Calendar.getInstance().apply { timeInMillis = globeView.sceneClock.nowMs() }
        val picker = DatePickerDialog(this, { _, year, month, day ->
            val chosen = Calendar.getInstance().apply {
                timeInMillis = globeView.sceneClock.nowMs()
                set(year, month, day)
            }
            TimePickerDialog(this, { _, hour, minute ->
                chosen.set(Calendar.HOUR_OF_DAY, hour); chosen.set(Calendar.MINUTE, minute)
                val min = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }.timeInMillis
                val max = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.timeInMillis
                globeView.sceneClock.seek(chosen.timeInMillis.coerceIn(min, max))
                persistExploreTime()
                updateTimeLabel(); showCompanionPanel("Explore time")
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
        picker.datePicker.minDate = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }.timeInMillis
        picker.datePicker.maxDate = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.timeInMillis
        picker.show()
    }

    private fun populateJourneys() {
        panelAction("‹ Explore") { showCompanionPanel("Explore") }
        val active = journeyStore.active()
        companionColumn.addView(panelText("Short, local lessons. Each restores your previous globe view and layers when you leave.", 14f))
        JourneyContent.all(this).forEach { journey ->
            val complete = journeyStore.completed(journey.id)
            panelAction("${journey.title} · ${journey.minutes} min${if (complete) " · completed" else ""}") {
                if (active?.journeyId == journey.id) {
                    AlertDialog.Builder(this).setTitle(journey.title)
                        .setItems(arrayOf("Resume", "Restart")) { _, index ->
                            if (index == 0) resumeJourney() else startJourney(journey, restart = true)
                        }.show()
                } else startJourney(journey)
            }
        }
    }

    private fun startJourney(journey: Journey, restart: Boolean = false) {
        val existing = journeyStore.active()
        val previous = when {
            existing == null -> captureJourneyScene()
            existing.journeyId == journey.id && restart -> existing.previous
            else -> {
                restoreJourneyScene(existing.previous)
                captureJourneyScene()
            }
        }
        journeyStore.start(journey, previous)
        applyJourneyStepScene(journey, journey.steps.first())
        showCompanionPanel("Journey")
    }

    private fun resumeJourney() {
        val active = journeyStore.active() ?: return
        val journey = JourneyContent.all(this).firstOrNull { it.id == active.journeyId } ?: return
        val step = journey.step(active.stepId) ?: journey.steps.first()
        applyJourneyStepScene(journey, step)
        showCompanionPanel("Journey")
    }

    private fun populateJourney() {
        val active = journeyStore.active() ?: run {
            panelAction("Choose a journey") { showCompanionPanel("Journeys") }; return
        }
        val journey = JourneyContent.all(this).firstOrNull { it.id == active.journeyId } ?: return
        val step = journey.step(active.stepId) ?: journey.steps.first()
        val index = journey.steps.indexOf(step) + 1
        companionColumn.addView(panelText("${journey.title} · step $index of ${journey.steps.size}", 15f, Color.rgb(173, 215, 255)))
        companionColumn.addView(panelText(step.title, 20f))
        companionColumn.addView(panelText(step.instruction, 15f))
        if (step.source != null) companionColumn.addView(panelText("Historical source: ${step.source}", 13f))
        recentJourneyEvent(step)?.let { event ->
            companionColumn.addView(panelText("Recent reported example: ${event.title}\n${event.observedAtMs?.let(::formatDate)} · ${event.source}", 14f))
        }
        if (journey.id == "understand_seasons" && step.month != null) {
            val sceneTime = globeView.sceneClock.nowMs()
            listOf("city:london", "city:sydney").forEach { cityId ->
                val city = CityCatalog.cities.first { it.id == cityId }
                val daylight = Daylight.at(city, sceneTime)
                companionColumn.addView(panelText("${city.name}: ${daylight.daylightMinutes?.let { "about ${it / 60}h ${it % 60}m daylight" } ?: daylight.state.name.lowercase().replace('_', ' ')}", 14f))
            }
        }
        when (step.kind) {
            "choice" -> step.options.forEachIndexed { answer, option ->
                panelAction(option) {
                    AlertDialog.Builder(this).setTitle(if (answer == step.correct) "That's the pattern" else "Look again")
                        .setMessage(if (answer == step.correct) step.right else step.wrong)
                        .setPositiveButton("Continue") { _, _ -> advanceJourney() }.show()
                }
            }
            "advance" -> panelAction(step.action ?: "Advance") {
                globeView.sceneClock.seek(globeView.sceneClock.nowMs() + step.hours * 3_600_000L)
                updateTimeLabel()
                AlertDialog.Builder(this).setMessage("The scene moved ${step.hours} hours ahead. Watch the changing sunlight on the globe.")
                    .setPositiveButton("Continue") { _, _ -> advanceJourney() }.show()
            }
            "reveal" -> panelAction(step.action ?: "Reveal") {
                globeView.renderer.showPlateBoundaries = true
                AlertDialog.Builder(this).setMessage("The gold PB2002 lines show a broad plate-edge pattern. Some reported events lie away from them.")
                    .setPositiveButton("Continue") { _, _ -> advanceJourney() }.show()
            }
            else -> panelAction(step.action ?: "Continue") { advanceJourney() }
        }
        panelAction("View globe") { closeCompanionPanel() }
        if (index < journey.steps.size) panelAction("Skip this step") { advanceJourney() }
        panelAction("Leave journey · restore my view") { leaveJourney() }
    }

    private fun advanceJourney() {
        val active = journeyStore.active() ?: return
        val journey = JourneyContent.all(this).firstOrNull { it.id == active.journeyId } ?: return
        val next = journey.next(active.stepId)
        if (next == null) {
            journeyStore.finish(journey.id)
            restoreJourneyScene(active.previous)
            AlertDialog.Builder(this).setMessage("${journey.title} is in your Field notebook.")
                .setPositiveButton("Done") { _, _ -> showCompanionPanel("Journeys") }.show()
        } else {
            journeyStore.advance(next.id)
            applyJourneyStepScene(journey, next)
            showCompanionPanel("Journey")
        }
    }

    private fun leaveJourney() {
        val previous = journeyStore.active()?.previous
        journeyStore.exit()
        if (previous != null) restoreJourneyScene(previous)
        showCompanionPanel("Journeys")
    }

    private fun captureJourneyScene(): JourneySceneSnapshot = JourneySceneSnapshot(
        globeView.camera.azimuth, globeView.camera.elevation, globeView.camera.distance,
        globeView.sceneClock.isExploring, globeView.sceneClock.nowMs(), globeView.sceneClock.isPlaying,
        globeView.renderer.showPlateBoundaries, globeView.renderer.showLatitudeGuides,
        observationsInSimulation
    )

    private fun restoreJourneyScene(scene: JourneySceneSnapshot) {
        globeView.camera.restore(scene.cameraAz, scene.cameraEl, scene.cameraDistance)
        if (scene.exploring) {
            globeView.sceneClock.enter(scene.timeMs)
            globeView.sceneClock.setPlaying(scene.playing)
        } else globeView.sceneClock.exit()
        observationsInSimulation = scene.compareObservations
        applyAppLayers()
        globeView.renderer.showPlateBoundaries = scene.plateLayer
        globeView.renderer.showLatitudeGuides = scene.latitudeGuides
        updateTimeLabel()
    }

    private fun applyJourneyStepScene(journey: Journey, step: JourneyStep) {
        val year = Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.YEAR)
        if (step.month != null && step.day != null) {
            val date = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear(); set(year, step.month - 1, step.day, step.hourUtc ?: 12, 0, 0)
            }
            globeView.sceneClock.enter(date.timeInMillis)
            observationsInSimulation = false
            applySimulationObservations()
            updateTimeLabel()
        } else if (journey.id == "ring_of_fire") {
            globeView.sceneClock.exit(); updateTimeLabel()
            globeView.renderer.showEvents = true
        }
        if (journey.id == "ring_of_fire") globeView.renderer.showPlateBoundaries = step.plates
        if (journey.id == "understand_seasons") globeView.renderer.showLatitudeGuides = step.guides
        val recent = recentJourneyEvent(step)
        val lat = recent?.lat ?: step.lat
        val lon = recent?.lon ?: step.lon
        if (lat != null && lon != null) globeView.camera.flyTo(lat, lon)
    }

    private fun recentJourneyEvent(step: JourneyStep): EarthEventsProvider.Event? {
        val type = when (step.recent) {
            "earthquake" -> EarthEventsProvider.Event.Type.EARTHQUAKE
            "volcano" -> EarthEventsProvider.Event.Type.VOLCANO
            else -> return null
        }
        val lat = step.lat ?: return null
        val lon = step.lon ?: return null
        val now = System.currentTimeMillis()
        return repository.snapshot().events.filter { it.type == type && it.observedAtMs != null &&
            now - it.observedAtMs in 0..30L * 86_400_000L &&
            angularDistanceDeg(lat, lon, it.lat, it.lon) <= 15.0 }
            .sortedWith(compareByDescending<EarthEventsProvider.Event> { it.observedAtMs }.thenBy { it.id })
            .firstOrNull()
    }

    private fun addEventList(limitPerType: Int) {
        val current = repository.snapshot()
        for (type in EarthEventsProvider.Event.Type.values()) {
            if (!layers.enabled(type)) continue
            val feed = current.feeds.getValue(type)
            val title = type.name.lowercase().replaceFirstChar { it.uppercase() }
            val status = when (feed.state) {
                EarthRepository.State.NOT_LOADED -> "Not loaded"
                EarthRepository.State.LOADING -> "Checking"
                EarthRepository.State.READY -> "Checked ${feed.fetchedAtMs?.let(::formatDate) ?: "recently"}"
                EarthRepository.State.STALE -> "Saved observations · offline or update failed"
                EarthRepository.State.UNAVAILABLE -> "Unavailable"
            }
            companionColumn.addView(panelText("$title · $status", 15f, Color.rgb(173, 215, 255)))
            if (feed.state == EarthRepository.State.READY && feed.events.isEmpty()) {
                companionColumn.addView(panelText(getString(R.string.companion_no_events), 13f))
            }
            feed.events.take(limitPerType).forEach { event ->
                panelAction(event.title) { showEventCard(event) }
            }
        }
    }

    private fun formatDate(timeMs: Long): String =
        SimpleDateFormat("MMM d, yyyy HH:mm z", Locale.getDefault()).format(Date(timeMs))

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        when (intent?.getStringExtra("widget_action")) {
            "open" -> showCompanionPanel("Today")
            "place" -> {
                val place = placeStore.all().firstOrNull { it.id == intent.getStringExtra("widget_value") }
                if (place == null) showCompanionPanel("Places")
                else { globeView.camera.flyTo(place.lat, place.lon); showPlaceDetails(place) }
            }
            "event" -> {
                pendingWidgetEventId = intent.getStringExtra("widget_value")
                resolveWidgetEvent(repository.snapshot())
            }
            "journey" -> showCompanionPanel("Explore")
        }
    }

    private fun resolveWidgetEvent(snapshot: EarthRepository.Snapshot) {
        val id = pendingWidgetEventId ?: return
        val event = snapshot.events.firstOrNull { it.id == id }
        if (event != null) {
            pendingWidgetEventId = null
            globeView.camera.flyTo(event.lat, event.lon)
            showEventCard(event)
        } else if (snapshot.feeds.values.none { it.state == EarthRepository.State.NOT_LOADED ||
                it.state == EarthRepository.State.LOADING }) {
            pendingWidgetEventId = null
            AlertDialog.Builder(this).setMessage("This observation is no longer in the saved feed.")
                .setPositiveButton("Current events") { _, _ -> showCompanionPanel("Explore") }
                .setNegativeButton("Close", null).show()
        }
    }

    /**
     * Capture the current globe view and share it. Sharing leaves the app and
     * carries a store link, so per Google Play Families policy it is placed
     * behind a parental gate.
     */
    private fun shareCurrentView() {
        ParentalGate.show(this) {
            com.globe.app.share.ShareManager.share(this, globeView, globeView.sceneClock.nowMs())
        }
    }

    // ------------------------------------------------------------------
    // Tap-to-learn: identify event markers, or explain day/night anywhere
    // ------------------------------------------------------------------

    private fun onGlobeTapped(x: Float, y: Float) {
        if (handleIndicatorTap(x, y)) return
        if (handleISSTap(x, y)) return

        if (challengeKind != null) {
            evaluateChallenge(x, y)
            return
        }

        val picked = globeView.renderer.pick(x, y, globeView.width, globeView.height)
        if (picked == null) {
            // Tapped the sky beyond Earth — a stargazing moment.
            globeView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showCard(
                "🌟 The night sky!\nBeyond Earth are thousands of stars, planets, and whole galaxies.",
                Discovery.STARGAZER
            )
            return
        }

        val savedHit = placeStore.all().map { it to angularDistanceDeg(picked[0], picked[1], it.lat, it.lon) }
            .minByOrNull { it.second }
        if (savedHit != null && savedHit.second <= 3.0) {
            showPlaceDetails(savedHit.first)
            return
        }

        // If a marker is near the tap, explain that event...
        val events = globeView.renderer.earthEventsRenderer.events
        var best: EarthEventsProvider.Event? = null
        var bestDeg = Double.MAX_VALUE
        for (event in events) {
            val d = angularDistanceDeg(picked[0], picked[1], event.lat, event.lon)
            if (d < bestDeg) {
                bestDeg = d
                best = event
            }
        }
        // Pick radius grows as the camera pulls back (markers shrink on screen)
        val thresholdDeg = (2.0 + globeView.camera.distance * 0.7).coerceIn(3.0, 9.0)

        globeView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (best != null && bestDeg <= thresholdDeg) {
            showEventCard(best)
        } else {
            // ...otherwise turn the tapped spot into a day/night lesson.
            showDayNightCard(picked[0], picked[1])
        }
    }

    private fun showEventCard(event: EarthEventsProvider.Event) {
        val explain = when (event.type) {
            EarthEventsProvider.Event.Type.EARTHQUAKE ->
                "Earthquakes happen when stress makes rock slip along a fault. Some occur far from plate boundaries."
            EarthEventsProvider.Event.Type.VOLCANO ->
                "Magma can rise through Earth's crust. This report does not confirm an eruption at this moment."
            EarthEventsProvider.Event.Type.WILDFIRE ->
                "Wildfire can begin naturally or through human activity and may spread with dry, windy conditions. This report may be older than today."
            EarthEventsProvider.Event.Type.STORM ->
                "Severe storms draw energy from warm, moist air. This marker shows the latest reported point, not a live forecast."
        }
        val discovery = when (event.type) {
            EarthEventsProvider.Event.Type.EARTHQUAKE -> Discovery.EARTHQUAKE
            EarthEventsProvider.Event.Type.VOLCANO -> Discovery.VOLCANO
            EarthEventsProvider.Event.Type.WILDFIRE -> Discovery.WILDFIRE
            EarthEventsProvider.Event.Type.STORM -> Discovery.STORM
        }
        if (journal.unlock(discovery)) refreshJournalButton()
        val feed = repository.snapshot().feeds.getValue(event.type)
        val facts = buildString {
            append(event.title)
            append("\n\n")
            event.magnitude?.let { append("Magnitude: ${"%.1f".format(Locale.getDefault(), it)}\n") }
            event.depthKm?.let { append("Depth: ${"%.1f".format(Locale.getDefault(), it)} km\n") }
            append("Observed: ${event.observedAtMs?.let(::formatDate) ?: "Time not reported"}\n")
            event.updatedAtMs?.let { append("Source updated: ${formatDate(it)}\n") }
            append("Source: ${event.source}\n")
            append("Feed: ${when (feed.state) {
                EarthRepository.State.READY -> "checked ${feed.fetchedAtMs?.let(::formatDate) ?: "recently"}"
                EarthRepository.State.STALE -> "saved copy; refresh unavailable"
                EarthRepository.State.LOADING -> "checking; showing saved copy"
                else -> "unavailable"
            }}")
            event.sourceUrl?.let { append("\nSource link: $it") }
        }
        val cardBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(24f), dpi(12f), dpi(24f), dpi(8f))
            addView(panelText(facts, 15f))
            addView(panelText("Read aloud", 14f, Color.rgb(173, 215, 255)).apply {
                minHeight = dpi(48f)
                setOnClickListener { speak(facts) }
            })
        }
        val cardScroll = ScrollView(this).apply { addView(cardBody) }
        AlertDialog.Builder(this)
            .setTitle(event.type.name.lowercase().replaceFirstChar { it.uppercase() })
            .setView(cardScroll)
            .setPositiveButton(if (fieldNotebook.hasEvent(event.id)) "Saved" else "Save") { _, _ ->
                fieldNotebook.saveEvent(event)
                if (openPanel == "Notebook") showCompanionPanel("Notebook")
            }
            .setNeutralButton(getString(R.string.companion_why)) { _, _ ->
                AlertDialog.Builder(this).setTitle(getString(R.string.companion_why))
                    .setMessage(explain).setPositiveButton(getString(R.string.companion_close), null).show()
            }
            .setNegativeButton(getString(R.string.companion_close), null)
            .show()
        if (narrateEnabled) speak(facts)
    }

    /**
     * If the tap hit the sun or moon indicator arrow (fixed at the bottom
     * center — see IndicatorRenderer), rotate the camera to bring that body
     * into view. Returns true when handled.
     */
    private fun handleIndicatorTap(x: Float, y: Float): Boolean {
        val w = globeView.width.toFloat()
        val h = globeView.height.toFloat()
        if (w <= 0f || h <= 0f) return false

        val arrowY = IndicatorRenderer.arrowCenterY(h, globeView.renderer.indicatorRenderer.bottomOffsetPx)
        val sunX = (-IndicatorRenderer.ARROW_SPACING + 1f) * 0.5f * w
        val moonX = (IndicatorRenderer.ARROW_SPACING + 1f) * 0.5f * w
        val radius = dpi(46f).toDouble()

        val dSun = Math.hypot((x - sunX).toDouble(), (y - arrowY).toDouble())
        val dMoon = Math.hypot((x - moonX).toDouble(), (y - arrowY).toDouble())

        return when {
            dSun <= radius && dSun <= dMoon -> {
                faceSkyBody(com.globe.app.earth.SunPosition.calculate(globeView.sceneClock.nowMs())); true
            }
            dMoon <= radius -> {
                faceSkyBody(com.globe.app.moon.MoonPosition.calculate(globeView.sceneClock.nowMs())); true
            }
            else -> false
        }
    }

    private fun faceSkyBody(dir: FloatArray) {
        globeView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        globeView.camera.faceSky(dir[0], dir[1], dir[2])
    }

    /**
     * If the tap hit the ISS marker (and it isn't hidden behind Earth), show an
     * info card about it. Returns true when handled.
     */
    private fun handleISSTap(x: Float, y: Float): Boolean {
        if (!layers.iss) return false
        val p = globeView.renderer.issOrbitRenderer.currentWorldPosition(globeView.sceneClock.nowMs())
        if (issOccluded(p)) return false
        val screen = projectToScreen(p) ?: return false
        val d = Math.hypot((x - screen[0]).toDouble(), (y - screen[1]).toDouble())
        if (d > dpi(42f)) return false

        val sun = com.globe.app.earth.SunPosition.calculate(globeView.sceneClock.nowMs())
        val sunlit = p[0] * sun[0] + p[1] * sun[1] + p[2] * sun[2] > 0
        globeView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        showCard(
            "🛰 International Space Station\n" +
            "Astronauts live and work here, about 400 km above Earth — circling the whole planet about every 90 minutes at 28,000 km/h!\n" +
            "Right now it's over the ${if (sunlit) "daytime" else "night"} side."
        )
        return true
    }

    /** Projects a world-space point to screen pixels, or null if behind the camera. */
    private fun projectToScreen(p: FloatArray): FloatArray? {
        return globeView.renderer.projectWorldToScreen(p, globeView.width, globeView.height)
    }

    /** True if the Earth sphere blocks the line of sight from the camera to [p]. */
    private fun issOccluded(p: FloatArray): Boolean {
        val eye = globeView.camera.getPosition()
        val dx = (p[0] - eye[0]).toDouble()
        val dy = (p[1] - eye[1]).toDouble()
        val dz = (p[2] - eye[2]).toDouble()
        val dist = Math.sqrt(dx * dx + dy * dy + dz * dz)
        if (dist < 1e-6) return false
        val ux = dx / dist
        val uy = dy / dist
        val uz = dz / dist
        // Ray (eye + t*u) vs unit sphere at origin.
        val hh = eye[0] * ux + eye[1] * uy + eye[2] * uz
        val c0 = eye[0] * eye[0] + eye[1] * eye[1] + eye[2] * eye[2] - 1.0
        val disc = hh * hh - c0
        if (disc < 0) return false
        val t = -hh - Math.sqrt(disc)
        return t > 0 && t < dist
    }

    /**
     * Dot product of the surface normal at [lat],[lon] with the Sun direction.
     * Positive means that spot is in daylight right now.
     */
    private fun facingSun(lat: Double, lon: Double): Double {
        val latR = Math.toRadians(lat)
        val lonR = Math.toRadians(lon)
        val cosLat = Math.cos(latR)
        // Surface normal in the app frame: -X = Greenwich, +Y = North, +Z = 90°E
        val nx = -cosLat * Math.cos(lonR)
        val ny = Math.sin(latR)
        val nz = cosLat * Math.sin(lonR)
        val sun = com.globe.app.earth.SunPosition.calculate(globeView.sceneClock.nowMs())
        return nx * sun[0] + ny * sun[1] + nz * sun[2]
    }

    /** Tap any land or ocean to learn whether it's day or night there right now. */
    /** Names a tapped point; the time zone starts from the nearest offline town and can be changed. */
    private fun savePlaceAt(lat: Double, lon: Double) {
        val input = EditText(this).apply {
            hint = "Place name"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        AlertDialog.Builder(this).setTitle("Name this place")
            .setMessage("${"%.3f".format(Locale.getDefault(), lat)}°, ${"%.3f".format(Locale.getDefault(), lon)}°")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val place = placeStore.create(name, lat, lon, null)
                    placesChanged(); hideEventCard()
                    editPlaceZone(place, TimeZoneSuggester.suggest(this, lat, lon))
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showDayNightCard(lat: Double, lon: Double) {
        if (facingSun(lat, lon) > 0) {
            showCard(
                "☀️ It's daytime here!\nThis side of Earth is facing the Sun right now. Drag the time slider to watch night arrive.",
                Discovery.DAYTIME
            )
        } else {
            showCard(
                "🌙 It's night-time here.\nThis side is turned away from the Sun — those tiny lights are cities. Drag the time slider to bring the Sun back.",
                Discovery.NIGHT
            )
        }
        pendingSavePoint = doubleArrayOf(lat, lon)
        savePlaceButton.visibility = View.VISIBLE
    }

    /**
     * Shows the info card. If [discovery] is provided and unlocked for the first
     * time, the card is prefixed with a celebration and the journal updates.
     */
    private fun showCard(text: String, discovery: Discovery? = null) {
        var body = text
        if (discovery != null && journal.unlock(discovery)) {
            body = "Added to Field notebook.\n\n$text"
            globeView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            refreshJournalButton()
        }
        eventCard.text = "×  Close\n\n$body"
        eventCard.visibility = View.VISIBLE
        pendingSavePoint = null
        savePlaceButton.visibility = View.GONE
        speak(body)
    }

    /** Unlocks the Time Traveler discovery the first time the scrubber is used. */
    private fun awardTimeTraveler() {
        if (!journal.isUnlocked(Discovery.TIME_TRAVELER)) {
            showCard(
                "${Discovery.TIME_TRAVELER.emoji} ${Discovery.TIME_TRAVELER.title}\n${Discovery.TIME_TRAVELER.fact}",
                Discovery.TIME_TRAVELER
            )
        }
    }

    private fun refreshJournalButton() {
        journalButton.text = "📖  ${journal.unlockedCount()}/${journal.total}"
    }

    // ------------------------------------------------------------------
    // Challenge mode (find-it prediction game)
    // ------------------------------------------------------------------

    private fun startChallenge() {
        challengeScore = 0
        hideToday()
        hideJournal()
        nextChallenge()
    }

    private fun nextChallenge() {
        val events = globeView.renderer.earthEventsRenderer.events
        val options = mutableListOf(ChallengeKind.DAYTIME, ChallengeKind.NIGHT)
        if (events.any { it.type == EarthEventsProvider.Event.Type.EARTHQUAKE }) options.add(ChallengeKind.FIND_EARTHQUAKE)
        if (events.any { it.type == EarthEventsProvider.Event.Type.VOLCANO }) options.add(ChallengeKind.FIND_VOLCANO)
        if (events.any { it.type == EarthEventsProvider.Event.Type.WILDFIRE }) options.add(ChallengeKind.FIND_WILDFIRE)
        if (events.any { it.type == EarthEventsProvider.Event.Type.STORM }) options.add(ChallengeKind.FIND_STORM)

        // Avoid repeating the same challenge back-to-back when there's a choice.
        challengeKind = options.filter { it != challengeKind }.ifEmpty { options }.random()
        challengeBanner.text = "🎯 ${challengeKind!!.prompt}\n⭐ $challengeScore   ·   tap to stop"
        challengeBanner.visibility = View.VISIBLE
    }

    private fun stopChallenge() {
        val score = challengeScore
        challengeKind = null
        challengeBanner.visibility = View.GONE
        showCard(
            if (score > 0) "🎉 Great job! You solved $score challenge${if (score == 1) "" else "s"}!"
            else "Challenge stopped — play again anytime!"
        )
    }

    private fun evaluateChallenge(x: Float, y: Float) {
        val kind = challengeKind ?: return
        val picked = globeView.renderer.pick(x, y, globeView.width, globeView.height)
        if (picked == null) {
            showCard("Tap on the Earth to answer! 🌍")
            return
        }

        val threshold = (2.0 + globeView.camera.distance * 0.7).coerceIn(3.0, 9.0)
        fun nearEvent(type: EarthEventsProvider.Event.Type) =
            globeView.renderer.earthEventsRenderer.events.any {
                it.type == type && angularDistanceDeg(picked[0], picked[1], it.lat, it.lon) <= threshold
            }

        val correct = when (kind) {
            ChallengeKind.DAYTIME -> facingSun(picked[0], picked[1]) > 0
            ChallengeKind.NIGHT -> facingSun(picked[0], picked[1]) < 0
            ChallengeKind.FIND_EARTHQUAKE -> nearEvent(EarthEventsProvider.Event.Type.EARTHQUAKE)
            ChallengeKind.FIND_VOLCANO -> nearEvent(EarthEventsProvider.Event.Type.VOLCANO)
            ChallengeKind.FIND_WILDFIRE -> nearEvent(EarthEventsProvider.Event.Type.WILDFIRE)
            ChallengeKind.FIND_STORM -> nearEvent(EarthEventsProvider.Event.Type.STORM)
        }

        if (correct) {
            globeView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            challengeScore++
            showCard("🎉 Yes! That's right.")
            nextChallenge()
        } else {
            globeView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showCard("Not quite — take another look and try again! 🔍")
        }
    }

    // ------------------------------------------------------------------
    // Discovery journal screen
    // ------------------------------------------------------------------

    private fun showJournal() {
        populateJournal()
        journalOverlay.visibility = View.VISIBLE
    }

    private fun hideJournal() {
        journalOverlay.visibility = View.GONE
    }

    private fun createJournalOverlay(): FrameLayout {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(235, 0, 2, 10))
            visibility = View.GONE
            isClickable = true
            setOnClickListener { hideJournal() }
        }
        val scroll = ScrollView(this).apply {
            setPadding(dpi(20f), dpi(20f), dpi(20f), dpi(20f))
        }
        journalColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(journalColumn)
        overlay.addView(scroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply { setMargins(dpi(24f), dpi(48f), dpi(24f), dpi(48f)) })
        return overlay
    }

    /** Rebuilds the journal contents to reflect the current unlock state. */
    private fun populateJournal() {
        journalColumn.removeAllViews()

        journalColumn.addView(TextView(this).apply {
            text = "My Discoveries"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        journalColumn.addView(TextView(this).apply {
            text = "${journal.unlockedCount()} of ${journal.total} found — keep exploring!"
            setTextColor(Color.rgb(150, 200, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, dpi(6f), 0, dpi(18f))
        })

        for (d in Discovery.values()) {
            val unlocked = journal.isUnlocked(d)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpi(8f), 0, dpi(8f))
                alpha = if (unlocked) 1f else 0.45f
            }
            row.addView(TextView(this).apply {
                text = if (unlocked) d.emoji else "❓"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                gravity = Gravity.CENTER
                width = dpi(48f)
            })
            val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            textCol.addView(TextView(this).apply {
                text = if (unlocked) d.title else "? ? ?"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.DEFAULT_BOLD
            })
            textCol.addView(TextView(this).apply {
                text = if (unlocked) d.fact else "Not found yet"
                setTextColor(Color.rgb(180, 180, 185))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
            row.addView(textCol, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dpi(12f) })
            journalColumn.addView(row)
        }

        journalColumn.addView(TextView(this).apply {
            text = "Tap anywhere to close"
            setTextColor(Color.rgb(140, 140, 140))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, dpi(20f), 0, 0)
        })
    }

    private fun dpi(value: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
        ).toInt()

    // ------------------------------------------------------------------
    // Today panel (daily return hook)
    // ------------------------------------------------------------------

    /** Shows the Today panel automatically the first time the app opens each day. */
    private fun maybeShowTodayOnLaunch() {
        val todayEpochDay = System.currentTimeMillis() / 86_400_000L
        if (prefs.getLong(PREF_TODAY_SHOWN_DAY, -1L) != todayEpochDay) {
            prefs.edit().putLong(PREF_TODAY_SHOWN_DAY, todayEpochDay).apply()
            showToday()
        }
    }

    private fun showToday() {
        populateToday()
        todayOverlay.visibility = View.VISIBLE
    }

    private fun hideToday() {
        todayOverlay.visibility = View.GONE
    }

    private fun createTodayOverlay(): FrameLayout {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(205, 0, 0, 8))
            visibility = View.GONE
            isClickable = true
            setOnClickListener { hideToday() }
        }
        todayColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(24f), dpi(24f), dpi(24f), dpi(18f))
            background = GradientDrawable().apply {
                cornerRadius = dpi(20f).toFloat()
                setColor(Color.rgb(14, 22, 36))
                setStroke(dpi(1f), Color.argb(60, 255, 255, 255))
            }
            // Absorb taps so only the surrounding scrim dismisses the panel.
            isClickable = true
        }
        overlay.addView(todayColumn, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply { setMargins(dpi(28f), dpi(28f), dpi(28f), dpi(28f)) })
        return overlay
    }

    /** Rebuilds the Today card — moon phase and fact reflect the real current day. */
    private fun populateToday() {
        todayColumn.removeAllViews()

        todayColumn.addView(TextView(this).apply {
            text = "Today"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        todayColumn.addView(TextView(this).apply {
            text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
            setTextColor(Color.rgb(150, 200, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, dpi(4f), 0, dpi(18f))
        })

        val phase = MoonPhase.current()
        todayColumn.addView(TextView(this).apply {
            text = "${phase.emoji}  ${phase.name}\n${phase.illuminationPercent}% lit up tonight"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setLineSpacing(dpi(4f).toFloat(), 1f)
            setPadding(0, 0, 0, dpi(18f))
        })

        todayColumn.addView(TextView(this).apply {
            text = "Did you know?"
            setTextColor(Color.rgb(150, 200, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
        })
        todayColumn.addView(TextView(this).apply {
            text = DailyFacts.today()
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setLineSpacing(dpi(3f).toFloat(), 1f)
            setPadding(0, dpi(4f), 0, dpi(16f))
        })

        todayColumn.addView(TextView(this).apply {
            text = "Tap a glowing dot on Earth to explore — or find a new discovery for your journal!"
            setTextColor(Color.rgb(190, 190, 195))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setLineSpacing(dpi(3f).toFloat(), 1f)
        })

        todayColumn.addView(makePillButton("🎯  Play a challenge!", ::dpi).apply {
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                startChallenge()
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dpi(18f)
        })

        todayColumn.addView(TextView(this).apply {
            text = "Tap outside the box to close"
            setTextColor(Color.rgb(140, 140, 140))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, dpi(16f), 0, 0)
        })
    }

    private fun hideEventCard() {
        eventCard.visibility = View.GONE
        pendingSavePoint = null
        savePlaceButton.visibility = View.GONE
    }

    private fun relativeTime(timeMs: Long): String {
        if (timeMs <= 0L) return "ongoing"
        val diffMin = (System.currentTimeMillis() - timeMs) / 60_000L
        return when {
            diffMin < 1 -> "just now"
            diffMin < 60 -> "$diffMin min ago"
            diffMin < 48 * 60 -> "${diffMin / 60} h ago"
            else -> "${diffMin / (24 * 60)} days ago"
        }
    }

    /** Great-circle angle between two lat/lon points, in degrees. */
    private fun angularDistanceDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val cosD = Math.sin(p1) * Math.sin(p2) + Math.cos(p1) * Math.cos(p2) * Math.cos(dl)
        return Math.toDegrees(Math.acos(cosD.coerceIn(-1.0, 1.0)))
    }

    /**
     * Builds a quiet rounded chip matching the app's monospace HUD style —
     * dark translucent fill with a faint rim, like the labels rather than a
     * loud material button.
     */
    private fun makePillButton(label: String, dp: (Float) -> Int): TextView =
        TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(dp(12f), dp(7f), dp(12f), dp(7f))
            background = GradientDrawable().apply {
                cornerRadius = dp(18f).toFloat()
                setColor(Color.argb(110, 12, 22, 34))
                setStroke(dp(1f), Color.argb(70, 255, 255, 255))
            }
        }

    private fun updateCloudLabel() {
        cloudLabel.text = when (globeView.renderer.earthRenderer.cloudMode) {
            EarthRenderer.CloudMode.OFF -> "\u2601 Clouds: off"
            EarthRenderer.CloudMode.GENERATED -> "\u2601 Clouds: generated"
            EarthRenderer.CloudMode.LIVE ->
                if (cloudTimestamp != null) "\u2601 Clouds: live (NASA VIIRS)\n    Updated: $cloudTimestamp"
                else "\u2601 Clouds: live (loading\u2026)"
        }
    }

    private fun updateEclipseLabel(state: EclipseDetector.EclipseState) {
        when (state) {
            EclipseDetector.EclipseState.SOLAR -> {
                eclipseLabel.text = "\u2600 Solar Eclipse!"
                eclipseLabel.setTextColor(Color.rgb(255, 191, 0)) // amber/gold
                eclipseLabel.visibility = View.VISIBLE
            }
            EclipseDetector.EclipseState.LUNAR -> {
                eclipseLabel.text = "\uD83C\uDF19 Lunar Eclipse!"
                eclipseLabel.setTextColor(Color.rgb(173, 216, 230)) // pale blue
                eclipseLabel.visibility = View.VISIBLE
            }
            EclipseDetector.EclipseState.NEAR_SOLAR -> {
                eclipseLabel.text = "Near solar eclipse"
                eclipseLabel.setTextColor(Color.rgb(180, 150, 80)) // dim amber
                eclipseLabel.visibility = View.VISIBLE
            }
            EclipseDetector.EclipseState.NEAR_LUNAR -> {
                eclipseLabel.text = "Near lunar eclipse"
                eclipseLabel.setTextColor(Color.rgb(120, 150, 170)) // dim blue
                eclipseLabel.visibility = View.VISIBLE
            }
            EclipseDetector.EclipseState.NONE -> {
                eclipseLabel.visibility = View.GONE
            }
        }
    }

    private fun updateTimeLabel() {
        if (globeView.sceneClock.isExploring) {
            timeLabel.text = "SIMULATED · ${timeFormat.format(Date(globeView.sceneClock.nowMs()))}"
            timeLabel.contentDescription = "Explore time, simulated scene"
            timeLabel.translationY = dpi(55f).toFloat()
            return
        }
        timeLabel.contentDescription = "Time controls"
        timeLabel.translationY = 0f
        val offsetMs = globeView.sceneClock.offsetMs
        if (offsetMs == 0L) {
            timeLabel.text = "Time · Now"
        } else {
            val simTime = timeFormat.format(Date(globeView.sceneClock.nowMs()))
            val hours = offsetMs / 3_600_000.0
            val sign = if (hours >= 0) "+" else ""
            timeLabel.text = "$simTime (${sign}${String.format("%.1f", hours)}h)"
        }
    }

    // ------------------------------------------------------------------
    // Legend
    // ------------------------------------------------------------------

    private fun createLegendOverlay(dp: (Float) -> Int): FrameLayout {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(200, 0, 0, 0))
            visibility = View.GONE
            isClickable = true
            setOnClickListener { hideLegend() }
        }

        val iconSize = dp(32f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        data class LegendEntry(val icon: Bitmap, val name: String, val description: String)

        val entries = listOf(
            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Earth: half day/half night globe
                pt.shader = RadialGradient(s*0.4f, s*0.4f, s*0.5f,
                    intArrayOf(Color.rgb(40,120,60), Color.rgb(30,80,170), Color.rgb(20,50,120)),
                    floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(s/2f, s/2f, s*0.42f, pt)
                pt.shader = null
                // Dark half
                pt.color = Color.argb(140, 0, 0, 30)
                c.drawArc(RectF(s*0.08f, s*0.08f, s*0.92f, s*0.92f), -90f, 180f, true, pt)
            }, "Earth", "Textured globe with day/night cycle and diffuse sunlight"),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // City lights: dark circle with orange dots
                pt.color = Color.rgb(10, 10, 30)
                c.drawCircle(s/2f, s/2f, s*0.42f, pt)
                pt.color = Color.rgb(255, 180, 60)
                val dots = floatArrayOf(0.35f,0.38f, 0.55f,0.42f, 0.45f,0.55f, 0.62f,0.35f,
                    0.3f,0.5f, 0.7f,0.5f, 0.5f,0.62f, 0.38f,0.68f, 0.6f,0.6f)
                for (i in dots.indices step 2) {
                    c.drawCircle(s*dots[i], s*dots[i+1], s*0.03f, pt)
                }
            }, "City lights", "Visible on the night side of the Earth"),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Clouds: soft white wisps
                pt.color = Color.argb(200, 255, 255, 255)
                c.drawOval(RectF(s*0.05f, s*0.3f, s*0.55f, s*0.6f), pt)
                pt.color = Color.argb(160, 255, 255, 255)
                c.drawOval(RectF(s*0.3f, s*0.2f, s*0.85f, s*0.55f), pt)
                pt.color = Color.argb(120, 255, 255, 255)
                c.drawOval(RectF(s*0.15f, s*0.5f, s*0.7f, s*0.78f), pt)
            }, "Clouds", "Procedural or live satellite imagery (NASA VIIRS). Tap cloud label to toggle"),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Sun: yellow circle with radial glow
                pt.shader = RadialGradient(s/2f, s/2f, s*0.45f,
                    intArrayOf(Color.rgb(255,255,220), Color.rgb(255,200,50), Color.argb(0,255,180,0)),
                    floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(s/2f, s/2f, s*0.45f, pt)
                pt.shader = null
            }, "Sun", "Billboard glow showing the sun's real-time position"),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Moon: gray sphere with craters
                pt.shader = RadialGradient(s*0.4f, s*0.38f, s*0.45f,
                    intArrayOf(Color.rgb(200,200,195), Color.rgb(140,140,135)),
                    null, Shader.TileMode.CLAMP)
                c.drawCircle(s/2f, s/2f, s*0.38f, pt)
                pt.shader = null
                pt.color = Color.rgb(120, 118, 115)
                c.drawCircle(s*0.4f, s*0.4f, s*0.07f, pt)
                c.drawCircle(s*0.6f, s*0.55f, s*0.05f, pt)
                c.drawCircle(s*0.35f, s*0.6f, s*0.04f, pt)
            }, "Moon", "Textured sphere at its real orbital position"),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Terminator: circle split with amber line
                pt.color = Color.rgb(30, 80, 170)
                c.drawArc(RectF(s*0.08f, s*0.08f, s*0.92f, s*0.92f), -90f, -180f, true, pt)
                pt.color = Color.rgb(10, 10, 30)
                c.drawArc(RectF(s*0.08f, s*0.08f, s*0.92f, s*0.92f), -90f, 180f, true, pt)
                pt.color = Color.rgb(255, 153, 40)
                pt.strokeWidth = s * 0.05f
                pt.style = Paint.Style.STROKE
                c.drawLine(s/2f, s*0.08f, s/2f, s*0.92f, pt)
                pt.style = Paint.Style.FILL
            }, "Terminator line", "Amber line at the day/night boundary"),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Aurora: wavy green/purple bands near top of circle
                pt.color = Color.rgb(10, 10, 30)
                c.drawCircle(s/2f, s/2f, s*0.42f, pt)
                pt.strokeWidth = s*0.04f
                pt.style = Paint.Style.STROKE
                val path = Path()
                for (band in 0..2) {
                    val y = s * (0.18f + band * 0.06f)
                    path.reset()
                    path.moveTo(s*0.15f, y)
                    path.cubicTo(s*0.3f, y - s*0.05f, s*0.5f, y + s*0.05f, s*0.65f, y)
                    path.cubicTo(s*0.75f, y - s*0.03f, s*0.8f, y + s*0.02f, s*0.85f, y)
                    pt.color = if (band == 1) Color.rgb(50, 200, 100) else Color.rgb(100, 50, 160)
                    pt.alpha = 200
                    c.drawPath(path, pt)
                }
                pt.style = Paint.Style.FILL
            }, "Aurora", "Green/purple glow near the geomagnetic poles (night side only)"),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Atmosphere: blue ring around dark circle
                pt.color = Color.rgb(10, 15, 40)
                c.drawCircle(s/2f, s/2f, s*0.35f, pt)
                pt.style = Paint.Style.STROKE
                pt.strokeWidth = s*0.08f
                pt.color = Color.argb(160, 80, 160, 255)
                c.drawCircle(s/2f, s/2f, s*0.39f, pt)
                pt.style = Paint.Style.FILL
            }, "Atmosphere", "Blue fresnel glow around the Earth's rim"),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Stars: white dots on dark background
                c.drawColor(Color.rgb(5, 5, 15))
                pt.color = Color.WHITE
                val stars = floatArrayOf(0.2f,0.15f,2.5f, 0.7f,0.25f,2f, 0.5f,0.5f,3f,
                    0.15f,0.7f,1.5f, 0.8f,0.6f,2f, 0.4f,0.8f,2.5f, 0.6f,0.15f,1.5f,
                    0.3f,0.4f,1.8f, 0.85f,0.85f,2f, 0.1f,0.45f,1.5f)
                for (i in stars.indices step 3) {
                    c.drawCircle(s*stars[i], s*stars[i+1], stars[i+2], pt)
                }
            }, "Stars", "Background star field"),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Earthquakes: amber/golden pulsing dot
                pt.shader = RadialGradient(s/2f, s/2f, s*0.4f,
                    intArrayOf(Color.rgb(255,153,0), Color.rgb(255,179,51), Color.argb(0,255,128,0)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(s/2f, s/2f, s*0.4f, pt)
                pt.shader = null
            }, "Earthquakes", "Pulsing amber dots (M4.5+ from USGS, past 7 days)"),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Volcanoes: magenta/hot-red pulsing dot
                pt.shader = RadialGradient(s/2f, s/2f, s*0.4f,
                    intArrayOf(Color.rgb(255,26,128), Color.rgb(230,77,166), Color.argb(0,200,20,100)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(s/2f, s/2f, s*0.4f, pt)
                pt.shader = null
            }, "Volcanoes", "Pulsing magenta dots (active eruptions from NASA EONET)"),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Wildfires: red pulsing dot
                pt.shader = RadialGradient(s/2f, s/2f, s*0.4f,
                    intArrayOf(Color.rgb(255,46,13), Color.rgb(255,102,38), Color.argb(0,255,60,20)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(s/2f, s/2f, s*0.4f, pt)
                pt.shader = null
            }, "Wildfires", "Pulsing red dots (active fires from NASA EONET)"),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Storms: electric blue pulsing dot
                pt.shader = RadialGradient(s/2f, s/2f, s*0.4f,
                    intArrayOf(Color.rgb(64,166,255), Color.rgb(140,204,255), Color.argb(0,64,166,255)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(s/2f, s/2f, s*0.4f, pt)
                pt.shader = null
            }, "Storms", "Pulsing blue dots (severe storms from NASA EONET) — tap any dot for details"),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // ISS orbit: curved red line with dot
                pt.color = Color.rgb(255, 80, 80)
                pt.style = Paint.Style.STROKE
                pt.strokeWidth = s*0.05f
                val path = Path()
                path.moveTo(s*0.05f, s*0.6f)
                path.cubicTo(s*0.25f, s*0.2f, s*0.75f, s*0.8f, s*0.95f, s*0.4f)
                c.drawPath(path, pt)
                pt.style = Paint.Style.FILL
                pt.color = Color.WHITE
                c.drawCircle(s*0.5f, s*0.5f, s*0.06f, pt)
            }, "ISS orbit", "Thin line showing the International Space Station's path"),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Sun/Moon arrows: two small arrows
                pt.style = Paint.Style.FILL
                // Sun arrow (yellow)
                val sunArrow = Path()
                sunArrow.moveTo(s*0.4f, s*0.2f)
                sunArrow.lineTo(s*0.5f, s*0.05f)
                sunArrow.lineTo(s*0.6f, s*0.2f)
                sunArrow.close()
                pt.color = Color.rgb(255, 220, 100)
                c.drawPath(sunArrow, pt)
                pt.strokeWidth = s*0.05f
                c.drawLine(s*0.5f, s*0.2f, s*0.5f, s*0.45f, pt)
                // Moon arrow (light blue)
                val moonArrow = Path()
                moonArrow.moveTo(s*0.4f, s*0.6f)
                moonArrow.lineTo(s*0.5f, s*0.45f)
                moonArrow.lineTo(s*0.6f, s*0.6f)
                moonArrow.close()
                pt.color = Color.rgb(180, 200, 220)
                c.drawPath(moonArrow, pt)
                c.drawLine(s*0.5f, s*0.6f, s*0.5f, s*0.85f, pt)
            }, "Sun/Moon arrows", "2D overlay arrows pointing toward the sun and moon"),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Eclipse: overlapping sun and moon circles
                pt.shader = RadialGradient(s*0.42f, s*0.5f, s*0.28f,
                    intArrayOf(Color.rgb(255,240,180), Color.rgb(255,180,40)),
                    null, Shader.TileMode.CLAMP)
                c.drawCircle(s*0.42f, s*0.5f, s*0.28f, pt)
                pt.shader = null
                pt.color = Color.rgb(40, 40, 50)
                c.drawCircle(s*0.58f, s*0.5f, s*0.28f, pt)
                // Corona glow
                pt.style = Paint.Style.STROKE
                pt.strokeWidth = s*0.03f
                pt.color = Color.argb(120, 255, 200, 80)
                c.drawCircle(s*0.58f, s*0.5f, s*0.32f, pt)
                pt.style = Paint.Style.FILL
            }, "Eclipse alerts", "Notifies when sun-earth-moon alignment approaches an eclipse"),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Time scrubber: slider track with knob
                pt.color = Color.rgb(80, 80, 80)
                c.drawRoundRect(RectF(s*0.08f, s*0.44f, s*0.92f, s*0.56f), s*0.06f, s*0.06f, pt)
                pt.color = Color.rgb(100, 180, 255)
                c.drawRoundRect(RectF(s*0.08f, s*0.44f, s*0.55f, s*0.56f), s*0.06f, s*0.06f, pt)
                pt.color = Color.WHITE
                c.drawCircle(s*0.55f, s*0.5f, s*0.12f, pt)
            }, "Time scrubber", "Drag the slider to simulate +/- 24 hours; releases to snap back to now")
        )

        val pad = dp(20f)

        val scrollView = ScrollView(this).apply {
            setPadding(pad, pad, pad, pad)
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Title
        column.addView(TextView(this).apply {
            text = "Legend"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16f))
        })

        for (entry in entries) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6f), 0, dp(6f))
                gravity = Gravity.CENTER_VERTICAL
            }

            // Icon
            row.addView(ImageView(this).apply {
                setImageBitmap(entry.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(iconSize, iconSize).apply {
                setMargins(0, 0, dp(14f), 0)
            })

            // Text column
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            textCol.addView(TextView(this).apply {
                text = entry.name
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
            })
            textCol.addView(TextView(this).apply {
                text = entry.description
                setTextColor(Color.rgb(180, 180, 180))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
            row.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            column.addView(row)
        }

        // Dismiss hint
        column.addView(TextView(this).apply {
            text = "Tap anywhere to close"
            setTextColor(Color.rgb(140, 140, 140))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, dp(20f), 0, 0)
        })

        scrollView.addView(column)

        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply {
            val hMargin = dp(24f)
            setMargins(hMargin, dp(48f), hMargin, dp(48f))
        }
        overlay.addView(scrollView, cardParams)

        // Close (X) button — top right, an obvious way to dismiss the legend
        val closeButton = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val size = dp(40f)
            minimumWidth = size
            minimumHeight = size
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(120, 40, 60, 90))
                setStroke(dp(1f), Color.argb(70, 255, 255, 255))
            }
            isClickable = true
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                hideLegend()
            }
        }
        overlay.addView(closeButton, FrameLayout.LayoutParams(
            dp(40f), dp(40f), Gravity.TOP or Gravity.END
        ).apply { setMargins(0, dp(20f), dp(20f), 0) })

        return overlay
    }

    /** Creates a small legend icon bitmap by running a Canvas draw lambda. */
    private fun drawLegendIcon(
        size: Int, paint: Paint,
        draw: (Canvas, Float, Paint) -> Unit
    ): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        draw(canvas, size.toFloat(), paint)
        paint.reset()
        return bmp
    }

    private fun showLegend() {
        legendOverlay.visibility = View.VISIBLE
    }

    private fun hideLegend() {
        legendOverlay.visibility = View.GONE
    }

    // ------------------------------------------------------------------
    // Music
    // ------------------------------------------------------------------

    private fun startMusic() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.ambient_space)?.apply {
                isLooping = true
                setVolume(musicVolume, musicVolume)
            }
        }
        mediaPlayer?.setVolume(musicVolume, musicVolume)
        mediaPlayer?.start()
    }

    private fun stopMusic() {
        mediaPlayer?.pause()
    }

    private fun releaseMusic() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun toggleMusic() {
        musicEnabled = !musicEnabled
        prefs.edit().putBoolean(PREF_MUSIC_ENABLED, musicEnabled).apply()
        if (musicEnabled) startMusic() else stopMusic()
        volumeSlider.visibility = if (musicEnabled) View.VISIBLE else View.GONE
        updateMusicButton()
    }

    private fun updateMusicButton() {
        musicButton.text = if (musicEnabled) "\u266B Music: on" else "\u266B Music: off"
    }

    // ------------------------------------------------------------------
    // Read-aloud narration (for early readers)
    // ------------------------------------------------------------------

    private fun toggleNarration() {
        narrateEnabled = !narrateEnabled
        prefs.edit().putBoolean(PREF_NARRATE, narrateEnabled).apply()
        updateNarrateButton()
        if (narrateEnabled) speak("Read to me is on. I'll read the cards out loud.") else tts?.stop()
    }

    private fun updateNarrateButton() {
        narrateButton.text = if (narrateEnabled) "\uD83D\uDD0A Read: on" else "\uD83D\uDD0A Read: off"
    }

    /** Speaks [text] aloud when narration is on, stripping emoji for clean speech. */
    private fun speak(text: String) {
        if (!narrateEnabled || !ttsReady) return
        val clean = text.replace(Regex("[^\\p{L}\\p{N} .,!?'\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isNotEmpty()) tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "card")
    }

    // ------------------------------------------------------------------
    // First-run onboarding
    // ------------------------------------------------------------------

    private fun createOnboardingOverlay(): FrameLayout {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(135, 0, 2, 12))
            visibility = View.GONE
            isClickable = true   // block taps to the globe behind it
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(26f), dpi(28f), dpi(26f), dpi(22f))
            background = GradientDrawable().apply {
                cornerRadius = dpi(20f).toFloat()
                setColor(Color.rgb(14, 22, 36))
                setStroke(dpi(1f), Color.argb(60, 255, 255, 255))
            }
            isClickable = true
        }

        card.addView(TextView(this).apply {
            text = "A living window onto Earth"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setLineSpacing(dpi(2f).toFloat(), 1f)
            setPadding(0, 0, 0, dpi(18f))
        })

        val tips = listOf(
            "Drag to rotate",
            "Pinch to zoom",
            "Tap the globe to discover more"
        )
        for (tip in tips) {
            card.addView(TextView(this).apply {
                text = tip
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setLineSpacing(dpi(2f).toFloat(), 1f)
                setPadding(0, dpi(7f), 0, dpi(7f))
            })
        }

        card.addView(makePillButton("Explore Earth", ::dpi).apply {
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                dismissOnboarding()
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dpi(20f)
        })

        overlay.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply { setMargins(dpi(28f), dpi(28f), dpi(28f), dpi(28f)) })
        return overlay
    }

    private fun dismissOnboarding() {
        prefs.edit().putBoolean(PREF_ONBOARDED, true).apply()
        onboardingOverlay.visibility = View.GONE
    }

    /**
     * Back steps out of whatever is open — an overlay, a running challenge, or
     * the info card — returning to the main globe. Only when nothing is open
     * does Back leave the app.
     */
    private fun closeTopLayer(): Boolean {
        when {
            onboardingOverlay.visibility == View.VISIBLE -> dismissOnboarding()
            openPanel != null -> if (openPanel == "Notebook" || openPanel == "Guide" || openPanel == "Places") {
                showCompanionPanel("Explore")
            } else closeCompanionPanel()
            todayOverlay.visibility == View.VISIBLE -> hideToday()
            journalOverlay.visibility == View.VISIBLE -> hideJournal()
            legendOverlay.visibility == View.VISIBLE -> hideLegend()
            challengeKind != null -> stopChallenge()
            eventCard.visibility == View.VISIBLE -> hideEventCard()
            else -> return false
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        globeView.onResume()
        if (globeView.sceneClock.isExploring) { uiHandler.removeCallbacks(timeTick); uiHandler.post(timeTick) }
        if (musicEnabled) startMusic()
    }

    override fun onPause() {
        super.onPause()
        saveViewState()
        globeView.onPause()
        uiHandler.removeCallbacks(timeTick)
        stopMusic()
        tts?.stop()
    }

    /** Persist the camera pose and cloud visibility so the app reopens as left. */
    private fun saveViewState() {
        prefs.edit()
            .putFloat(PREF_CAM_AZ, globeView.camera.azimuth)
            .putFloat(PREF_CAM_EL, globeView.camera.elevation)
            .putFloat(PREF_CAM_DIST, globeView.camera.distance)
            .putInt(PREF_CLOUD_MODE, layers.cloudMode.ordinal)
            .putBoolean("explore_time_active", globeView.sceneClock.isExploring)
            .putLong("explore_time_ms", globeView.sceneClock.nowMs())
            .apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        repository.removeObserver(repositoryObserver)
        repository.removeCloudObserver(cloudObserver)
        uiHandler.removeCallbacks(timeTick)
        releaseMusic()
        tts?.shutdown()
        tts = null
    }
}
