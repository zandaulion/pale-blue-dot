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
import android.widget.Button
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
import com.globe.app.events.label
import com.globe.app.events.eventCategoryLabel
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
import com.globe.app.moon.MoonGuideOverlay
import com.globe.app.kids.ParentalGate
import com.globe.app.kids.NarrationText
import com.globe.app.kids.TaliaNarrator
import java.text.SimpleDateFormat
import java.util.Date
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
        private const val PREF_NARRATE = "narrate_enabled" // Existing preference now controls Talia only.
        private const val PREF_DEVICE_NARRATE = "device_narrate_enabled"
        private const val PREF_ONBOARDED = "onboarded"
        private const val STATE_SETTINGS_OPEN = "settings_open"
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
    private var deviceNarrateEnabled = false
    private lateinit var taliaNarrator: TaliaNarrator
    private var scrubberAnimator: ValueAnimator? = null
    private var cloudTimestamp: String? = null
    private var lastEclipseState: EclipseDetector.EclipseState = EclipseDetector.EclipseState.NONE

    private val timeFormat by lazy {
        val locale = AppLanguage.locale(this)
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMdHm") + " z", locale)
            .apply { timeZone = TimeZone.getDefault() }
    }

    /** Range of the scrubber: +/- 24 hours in milliseconds. */
    private val scrubberRangeMs = 24 * 60 * 60 * 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLanguage.restore(this)
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
            text = getString(R.string.text_clouds_live_loading)
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
            text = getString(R.string.text_time_now)
            minHeight = dp(48f)
            contentDescription = getString(R.string.text_time_controls)
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
            contentDescription = getString(R.string.text_move_time_up_to_24_hours_before_or_after_now)
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
        deviceNarrateEnabled = prefs.getBoolean(PREF_DEVICE_NARRATE, false)
        taliaNarrator = TaliaNarrator(this)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(AppLanguage.locale(this))
                ttsReady = result != null && result >= TextToSpeech.LANG_AVAILABLE
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

        shareButton = makePillButton(getString(R.string.text_share), dp).apply {
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

        todayButton = makePillButton(getString(R.string.text_today), dp).apply {
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
        savePlaceButton = makePillButton(getString(R.string.text_save_this_place), dp).apply {
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
            onGlobeTap = { x, y -> onGlobeTapped(x, y) },
            onGlobeLongPress = { x, y -> onGlobeHeld(x, y) }
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

        // Reserve separate space for time and Settings so translated labels can wrap.
        val settingsButton = makePillButton(getString(R.string.companion_settings), dp).apply {
            minHeight = dp(48f)
            contentDescription = getString(R.string.companion_settings)
            setOnClickListener { showCompanionPanel("Settings") }
        }
        val topControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(timeLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(settingsButton, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8f) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(challengeBanner, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8f) })
        }
        root.addView(MoonGuideOverlay(this,
            frame = { globeView.renderer.moonGuideFrame },
            blockedAreas = {
                buildList {
                    add(android.graphics.Rect(0, 0, globeView.width, topControls.bottom + dp(8f)))
                    add(android.graphics.Rect(0, globeView.height - dp(144f), globeView.width, globeView.height))
                    for (view in listOf(eventCard, savePlaceButton, timeScrubber)) {
                        if (view.isShown) {
                            val area = android.graphics.Rect()
                            view.getDrawingRect(area)
                            root.offsetDescendantRectToMyCoords(view, area)
                            area.offset(-globeView.left, -globeView.top)
                            area.inset(-dp(8f), -dp(8f))
                            add(area)
                        }
                    }
                }
            },
            isCovered = {
                openPanel != null || onboardingOverlay.visibility == View.VISIBLE ||
                    legendOverlay.visibility == View.VISIBLE || journalOverlay.visibility == View.VISIBLE ||
                    todayOverlay.visibility == View.VISIBLE
            }
        ), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(topControls, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP
        ).apply { setMargins(margin, dp(8f), margin, 0) })

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

        companionPanel = createCompanionPanel()

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
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        root.addView(nav, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ))

        // Welcome content must cover globe controls, including Settings and navigation.
        root.addView(onboardingOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
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
        root.post {
            if (savedInstanceState?.getBoolean(STATE_SETTINGS_OPEN) == true) {
                showCompanionPanel("Settings")
            } else {
                handleWidgetIntent(intent)
            }
        }
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_SETTINGS_OPEN, openPanel == "Settings")
        super.onSaveInstanceState(outState)
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
        val title = when (name) {
            "Today" -> getString(R.string.companion_today)
            "Layers" -> getString(R.string.companion_layers)
            "Explore" -> getString(R.string.companion_explore)
            "Settings" -> getString(R.string.companion_settings)
            "Notebook" -> getString(R.string.text_notebook)
            "Guide" -> getString(R.string.text_guide)
            "Places" -> getString(R.string.text_places)
            "Explore time" -> getString(R.string.text_explore_time)
            "Journeys" -> getString(R.string.text_journeys)
            "Journey" -> getString(R.string.text_journey)
            else -> name
        }
        heading.addView(panelText(title, 23f).apply { typeface = Typeface.DEFAULT_BOLD },
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
        companionColumn.addView(panelText(getString(R.string.text_planetary_briefing_real_time), 14f, Color.rgb(173, 215, 255)))
        companionColumn.addView(panelText(getString(R.string.text_1_s_2_s_3_s_estimated_illumination, briefing.moon.emoji, briefing.moon.name(this), briefing.moon.illuminationPercent), 17f))
        val place = briefing.place
        if (place == null) panelAction(getString(R.string.text_choose_a_primary_place)) { showCompanionPanel("Places") }
        else {
            companionColumn.addView(panelText("${place.name}\n${formatPlaceDaylight(place, briefing.daylight!!)}", 15f))
            panelAction(getString(R.string.text_fly_to_1_s, place.name)) { closeCompanionPanel(); globeView.camera.flyTo(place.lat, place.lon) }
        }
        companionColumn.addView(panelText(
            cloudTimestamp?.let {
                getString(R.string.text_satellite_imagery_1_s, it) +
                    if (repository.cloudState == EarthRepository.State.STALE) getString(R.string.text_saved_copy) else ""
            } ?: getString(R.string.text_satellite_imagery_unavailable_or_still_loading),
            13f, Color.rgb(190, 205, 219)
        ))
        companionColumn.addView(panelText(briefing.prompt(this), 15f))
        companionColumn.addView(panelText(getString(R.string.text_one_observation_1_s, briefing.eventMessage(this)), 17f))
        briefing.event?.let { event ->
            panelAction("${event.type.label(this)} · ${event.title}\n${event.observedAtMs?.let(::formatDate) ?: getString(R.string.text_time_unavailable)} · ${event.source}") {
                if (repository.snapshot().events.any { it.id == event.id }) {
                    closeCompanionPanel(); globeView.camera.flyTo(event.lat, event.lon); showEventCard(event)
                } else AlertDialog.Builder(this).setMessage(getString(R.string.text_this_observation_is_no_longer_in_the_saved_feed))
                    .setPositiveButton(getString(R.string.text_current_events)) { _, _ -> showCompanionPanel("Explore") }.show()
            }
        }
        panelAction(getString(R.string.companion_refresh)) { repository.refresh(manual = true) }
    }

    private fun populateCompanionLayers() {
        companionColumn.addView(panelText(getString(R.string.text_choose_what_appears_on_the_globe_hidden_events_also_leave_the_eve),
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
        companionColumn.addView(panelText(cloudTimestamp?.let { getString(R.string.text_satellite_imagery_1_s, it) +
            if (repository.cloudState == EarthRepository.State.STALE) getString(R.string.text_saved_copy) else "" }
            ?: getString(R.string.text_satellite_imagery_may_be_unavailable_offline_generated_clouds_sti), 13f,
            Color.rgb(190, 205, 219)))
        val descriptions = mapOf(
            EarthEventsProvider.Event.Type.EARTHQUAKE to getString(R.string.text_reported_by_usgs),
            EarthEventsProvider.Event.Type.VOLCANO to getString(R.string.text_reported_by_nasa_eonet),
            EarthEventsProvider.Event.Type.WILDFIRE to getString(R.string.text_reported_by_nasa_eonet),
            EarthEventsProvider.Event.Type.STORM to getString(R.string.text_reported_by_nasa_eonet)
        )
        for (type in EarthEventsProvider.Event.Type.values()) {
            panelSwitch(type.label(this), descriptions.getValue(type),
                layers.enabled(type)) {
                layers.setEnabled(type, it)
                applyAppLayers()
            }
        }
        panelSwitch(getString(R.string.text_constellations), getString(R.string.text_catalog_lines_in_the_star_background), layers.constellations) {
            layers.constellations = it; applyAppLayers()
        }
        panelSwitch(getString(R.string.text_iss_illustration), getString(R.string.text_approximate_orbit_not_live_tracking), layers.iss) {
            layers.iss = it; applyAppLayers()
        }
        panelSwitch(getString(R.string.text_aurora_illustration), getString(R.string.text_decorative_glow_not_a_forecast), layers.aurora) {
            layers.aurora = it; applyAppLayers()
        }
        panelSwitch(getString(R.string.text_day_night_boundary), getString(R.string.text_calculated_terminator_line), layers.terminator) {
            layers.terminator = it; applyAppLayers()
        }
        panelSwitch(getString(R.string.text_plate_boundaries), getString(R.string.text_offline_pb2002_model_gold_lines), layers.plateBoundaries) {
            layers.plateBoundaries = it; applyAppLayers()
        }
        companionColumn.addView(panelText(getString(R.string.text_plate_boundaries_gold_lines_mark_modelled_plate_edges_earthquakes), 13f))
    }

    private fun populateCompanionExplore() {
        panelAction(getString(R.string.text_places_1_s_saved, placeStore.all().size)) { showCompanionPanel("Places") }
        panelAction(getString(R.string.text_explore_time_and_seasons)) { showCompanionPanel("Explore time") }
        panelAction(getString(R.string.text_guided_journeys)) { showCompanionPanel("Journeys") }
        panelAction(getString(R.string.text_field_notebook_1_s_2_s_discoveries, journal.unlockedCount(), journal.total)) {
            showCompanionPanel("Notebook")
        }
        panelAction(getString(R.string.text_try_a_quick_challenge)) { closeCompanionPanel(); startChallenge() }
        panelAction(getString(R.string.text_scene_guide_and_legend)) { showCompanionPanel("Guide") }
        companionColumn.addView(panelText(getString(R.string.companion_events), 19f))
        addEventList(limitPerType = 12)
    }

    private fun populateCompanionSettings() {
        val languageIndex = AppLanguage.tags.indexOf(AppLanguage.selected(this).substringBefore(','))
        val languageName = AppLanguage.nativeNames.getOrNull(languageIndex)
            ?: getString(R.string.text_use_device_language)
        companionColumn.addView(Button(this).apply {
            text = listOf(getString(R.string.text_language), languageName).joinToString("\n")
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextColor(Color.rgb(173, 215, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            minHeight = dpi(64f)
            setPadding(dpi(12f), dpi(8f), dpi(12f), dpi(8f))
            background = GradientDrawable().apply {
                cornerRadius = dpi(10f).toFloat()
                setColor(Color.rgb(22, 38, 57))
            }
            setOnClickListener { chooseLanguage() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        panelSwitch(getString(R.string.text_ambient_music), getString(R.string.text_only_while_the_app_is_open), musicEnabled) { enabled ->
            if (musicEnabled != enabled) toggleMusic()
        }
        companionColumn.addView(panelText(getString(R.string.text_music_volume), 14f))
        companionColumn.addView(SeekBar(this).apply {
            max = 100; progress = (musicVolume * 100).toInt()
            contentDescription = getString(R.string.text_music_volume)
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
        panelSwitch(getString(R.string.narration_talia_label), getString(R.string.narration_settings_detail), narrateEnabled) {
            setNarrationEnabled(it)
        }
        panelSwitch(getString(R.string.narration_android_label), getString(R.string.narration_android_detail), deviceNarrateEnabled) {
            setDeviceNarrationEnabled(it)
        }
        panelSwitch(getString(R.string.companion_reduce_motion),
            getString(R.string.text_stops_idle_spin_and_decorative_marker_movement), layers.reduceMotion) {
            layers.reduceMotion = it; applyAppLayers()
        }
        panelAction(getString(R.string.text_share_globe_image)) { closeCompanionPanel(); shareCurrentView() }
        panelAction(getString(R.string.text_earth_live_wallpaper)) {
            closeCompanionPanel()
            startActivity(Intent(this, WallpaperConfigActivity::class.java))
        }
        panelAction(getString(R.string.text_scene_guide_and_legend)) { showCompanionPanel("Guide") }
    }

    private fun chooseLanguage() {
        val labels = listOf(getString(R.string.text_use_device_language)) + AppLanguage.nativeNames
        val selected = AppLanguage.selected(this).substringBefore(',')
        val index = AppLanguage.tags.indexOf(selected) + 1
        AlertDialog.Builder(this).setTitle(getString(R.string.text_language))
            .setSingleChoiceItems(labels.toTypedArray(), index) { dialog, which ->
                dialog.dismiss()
                AppLanguage.select(this, if (which == 0) "" else AppLanguage.tags[which - 1])
                showCompanionPanel("Settings")
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun populateCompanionNotebook() {
        panelAction(getString(R.string.text_explore)) { showCompanionPanel("Explore") }
        fieldNotebook.migrateLegacy(journal)
        companionColumn.addView(panelText(getString(R.string.text_completed_journeys), 19f))
        val completed = JourneyContent.all(this).filter { journeyStore.completed(it.id) }
        if (completed.isEmpty()) companionColumn.addView(panelText(getString(R.string.text_no_journeys_completed_yet), 14f))
        completed.forEach { companionColumn.addView(panelText("✓ ${it.title}", 15f)) }
        companionColumn.addView(panelText(getString(R.string.text_saved_observations), 19f))
        val saved = fieldNotebook.savedEvents()
        if (saved.isEmpty()) companionColumn.addView(panelText(getString(R.string.text_save_an_observation_from_its_card_to_keep_its_source_and_date), 14f))
        saved.asReversed().forEach { item ->
            panelAction("${eventCategoryLabel(this, item.category)} · ${item.title}") {
                showSavedObservation(item)
            }
        }
        companionColumn.addView(panelText(getString(R.string.text_earlier_discoveries_1_s_2_s, fieldNotebook.legacyDiscoveries().size, journal.total), 19f))
        val descriptions = mapOf(
            Discovery.EARTHQUAKE to getString(R.string.text_you_explored_a_reported_earthquake),
            Discovery.VOLCANO to getString(R.string.text_you_explored_a_reported_volcanic_event),
            Discovery.WILDFIRE to getString(R.string.text_you_explored_a_reported_wildfire),
            Discovery.STORM to getString(R.string.text_you_explored_a_reported_storm),
            Discovery.DAYTIME to getString(R.string.text_you_found_a_place_facing_the_sun),
            Discovery.NIGHT to getString(R.string.text_you_found_earth_s_night_side),
            Discovery.STARGAZER to getString(R.string.text_you_looked_beyond_earth),
            Discovery.TIME_TRAVELER to getString(R.string.text_you_changed_the_scene_time)
        )
        fieldNotebook.legacyDiscoveries().forEach {
            companionColumn.addView(panelText("${it.emoji} ${it.title(this)}\n${descriptions[it]}", 15f))
        }
    }

    private fun showSavedObservation(item: SavedObservation) {
        val facts = buildString {
            append(item.title).append("\n")
            append(getString(R.string.text_observed)).append(item.observedAtMs?.let(::formatDate) ?: getString(R.string.text_time_not_reported)).append("\n")
            append(getString(R.string.text_source)).append(item.source).append("\n")
            item.magnitude?.let { append(getString(R.string.text_magnitude_1_s, "%.1f".format(AppLanguage.locale(this@MainActivity), it))) }
            item.depthKm?.let { append(getString(R.string.text_depth_1_s_km, "%.1f".format(AppLanguage.locale(this@MainActivity), it))) }
            item.sourceUrl?.let { append(getString(R.string.text_source_link_1_s, it)) }
        }
        AlertDialog.Builder(this).setTitle(getString(R.string.text_saved_observation)).setMessage(facts)
            .setPositiveButton(getString(R.string.text_fly_to)) { _, _ -> closeCompanionPanel(); globeView.camera.flyTo(item.lat, item.lon) }
            .setNegativeButton(getString(R.string.companion_close), null).show()
    }

    private fun populateCompanionGuide() {
        panelAction(getString(R.string.text_explore)) { showCompanionPanel("Explore") }
        companionColumn.addView(panelText(getString(R.string.text_tap_the_globe_to_discover_more), 15f))
        listOf(
            getString(R.string.text_sunlight_and_city_lights) to getString(R.string.text_calculated_sunlight_shows_the_current_day_and_night_sides),
            getString(R.string.companion_clouds) to getString(R.string.text_generated_clouds_are_illustrative_satellite_mode_derives_opacity),
            getString(R.string.text_earthquakes) to getString(R.string.text_usgs_reports_magnitude_4_5_earthquakes_from_the_last_seven_days),
            getString(R.string.text_volcanoes_wildfires_and_storms) to getString(R.string.text_nasa_eonet_open_reports_may_have_older_observation_dates),
            getString(R.string.text_iss) to getString(R.string.text_illustrated_orbit_not_live_tracking),
            getString(R.string.text_aurora) to getString(R.string.text_decorative_illustration_not_a_forecast),
            getString(R.string.text_day_night_boundary) to getString(R.string.text_calculated_terminator_line_2),
            getString(R.string.text_constellations) to getString(R.string.text_catalog_stars_joined_into_familiar_patterns),
            getString(R.string.text_plate_boundaries) to getString(R.string.text_a_simplified_offline_pb2002_model_many_earthquakes_occur_near_bou)
        ).forEach { (title, body) -> companionColumn.addView(panelText("$title\n$body", 15f)) }
    }

    private fun populatePlaces() {
        panelAction(getString(R.string.text_explore)) { showCompanionPanel("Explore") }
        companionColumn.addView(panelText(getString(R.string.text_saved_places_stay_on_this_device_cyan_rings_mark_them_on_the_glob), 14f))
        panelAction(getString(R.string.text_search_bundled_cities)) {
            val query = EditText(this).apply {
                hint = getString(R.string.text_city_or_country_blank_lists_all_1_s, CityCatalog.cities.size); inputType = InputType.TYPE_CLASS_TEXT
            }
            AlertDialog.Builder(this).setTitle(getString(R.string.text_find_a_city)).setView(query)
                .setPositiveButton(getString(R.string.text_search)) { _, _ ->
                    val matches = CityCatalog.search(query.text.toString(), this)
                    if (matches.isEmpty()) AlertDialog.Builder(this).setMessage(getString(R.string.text_no_bundled_city_matches_that_name))
                        .setPositiveButton(getString(R.string.text_ok), null).show()
                    else AlertDialog.Builder(this).setTitle(getString(R.string.text_bundled_cities))
                        .setItems(matches.map { it.name }.toTypedArray()) { _, which ->
                            placeStore.save(matches[which]); placesChanged(); showPlaceDetails(matches[which])
                        }.show()
                }.setNegativeButton(getString(R.string.text_cancel), null).show()
        }
        panelAction(getString(R.string.text_add_a_place_from_the_globe)) {
            closeCompanionPanel()
            AlertDialog.Builder(this).setMessage(getString(R.string.text_tap_a_point_on_the_globe_then_tap_save_this_place_under_its_card))
                .setPositiveButton(getString(R.string.text_ok), null).show()
        }
        val primary = placeStore.primaryId()
        placeStore.all().forEach { place ->
            panelAction("${if (place.id == primary) "★ " else ""}${place.name}\n${place.zoneId ?: getString(R.string.text_time_zone_unset)}") {
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
        val options = arrayOf(getString(R.string.text_fly_to), getString(R.string.text_set_as_primary), getString(R.string.text_rename), getString(R.string.text_set_time_zone), getString(R.string.text_delete))
        AlertDialog.Builder(this).setTitle(current.name + "\n" + formatPlaceDaylight(current, summary) +
            "\n${"%.3f".format(AppLanguage.locale(this@MainActivity), current.lat)}°, " +
            "${"%.3f".format(AppLanguage.locale(this@MainActivity), current.lon)}°")
            .setItems(options) { _, which -> when (which) {
                0 -> { closeCompanionPanel(); globeView.camera.flyTo(current.lat, current.lon) }
                1 -> { placeStore.setPrimary(current.id); placesChanged() }
                2 -> editPlaceName(current)
                3 -> editPlaceZone(current)
                4 -> {
                    val wasPrimary = placeStore.primaryId() == current.id
                    placeStore.delete(current.id); placesChanged()
                    AlertDialog.Builder(this).setMessage(getString(R.string.text_1_s_removed, current.name))
                        .setPositiveButton(getString(R.string.text_undo)) { _, _ ->
                            placeStore.save(current)
                            if (wasPrimary) placeStore.setPrimary(current.id)
                            placesChanged()
                        }.setNegativeButton(getString(R.string.text_done), null).show()
                }
            } }.setNegativeButton(getString(R.string.companion_close), null).show()
    }

    private fun editPlaceName(place: SavedPlace) {
        val input = EditText(this).apply { setText(place.name); selectAll() }
        AlertDialog.Builder(this).setTitle(getString(R.string.text_name_this_place)).setView(input)
            .setPositiveButton(getString(R.string.text_save)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) { placeStore.save(place.copy(name = name)); placesChanged() }
            }.setNegativeButton(getString(R.string.text_cancel), null).show()
    }

    private fun editPlaceZone(place: SavedPlace, suggestedZone: String? = null) {
        val input = EditText(this).apply {
            hint = getString(R.string.text_iana_zone_e_g_europe_bucharest)
            setText(place.zoneId ?: suggestedZone ?: "")
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val suggestion = if (place.zoneId == null && suggestedZone != null)
            getString(R.string.text_suggested_from_the_nearest_town_change_it_if_this_place_is_across) else ""
        AlertDialog.Builder(this).setTitle(getString(R.string.text_time_zone_for_1_s, place.name))
            .setMessage(suggestion + getString(R.string.text_enter_an_iana_time_zone_id_or_leave_blank_to_keep_local_clock_and))
            .setView(input).setPositiveButton(getString(R.string.text_save)) { _, _ ->
                val zone = input.text.toString().trim().ifBlank { null }
                if (zone == null || PlaceStore.validZone(zone)) {
                    placeStore.save(place.copy(zoneId = zone)); placesChanged()
                } else AlertDialog.Builder(this).setMessage(getString(R.string.text_unknown_iana_time_zone_id_1_s, zone))
                    .setPositiveButton(getString(R.string.text_try_again)) { _, _ -> editPlaceZone(place) }.show()
            }.setNegativeButton(getString(R.string.text_cancel), null).show()
    }

    private fun formatPlaceDaylight(place: SavedPlace, info: Daylight.Summary): String {
        val zone = place.zoneId ?: return getString(R.string.text_time_zone_unset_choose_a_zone_for_local_clock_and_sunrise)
        val formatter = SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(AppLanguage.locale(this@MainActivity), "EEEMMMdHm") + " z", AppLanguage.locale(this@MainActivity)).apply {
            timeZone = TimeZone.getTimeZone(zone)
        }
        val clock = formatter.format(Date(info.localTimeMs))
        val sun = when (info.state) {
            Daylight.State.POLAR_DAY -> getString(R.string.text_polar_day_sun_stays_above_the_horizon)
            Daylight.State.POLAR_NIGHT -> getString(R.string.text_polar_night_sun_stays_below_the_horizon)
            Daylight.State.ZONE_UNSET -> getString(R.string.text_time_zone_unset)
            Daylight.State.ORDINARY -> {
                val hour = SimpleDateFormat("HH:mm", AppLanguage.locale(this@MainActivity)).apply { timeZone = TimeZone.getTimeZone(zone) }
                val rise = info.sunriseMs?.let { hour.format(Date(it)) } ?: getString(R.string.text_not_available)
                val set = info.sunsetMs?.let { hour.format(Date(it)) } ?: getString(R.string.text_not_available)
                val duration = info.daylightMinutes?.let { getString(R.string.text_about_1_sh_2_sm_daylight, it / 60, it % 60) } ?: ""
                getString(R.string.text_approx_sunrise_1_s_sunset_2_s_3_s, rise, set, duration)
            }
        }
        return "$clock · ${if (info.isDay) getString(R.string.text_day) else getString(R.string.text_night)}\n$sun"
    }

    private fun populateExploreTime() {
        panelAction(getString(R.string.text_explore)) { showCompanionPanel("Explore") }
        val clock = globeView.sceneClock
        if (!clock.isExploring) {
            companionColumn.addView(panelText(getString(R.string.text_choose_a_date_to_hold_the_globe_in_an_explicit_lesson_time_today), 15f))
            panelAction(getString(R.string.text_begin_at_current_time)) { enterExploreTime(System.currentTimeMillis()) }
        } else {
            companionColumn.addView(panelText(getString(R.string.text_simulated_1_s, timeFormat.format(Date(clock.nowMs()))), 17f, Color.rgb(255, 225, 160)))
            panelAction(getString(R.string.text_choose_date_and_time)) { chooseExploreDate() }
            panelAction(if (clock.isPlaying) getString(R.string.text_pause) else getString(R.string.text_play_at_real_time_speed)) {
                clock.setPlaying(!clock.isPlaying); persistExploreTime(); showCompanionPanel("Explore time")
            }
            panelAction(getString(R.string.text_advance_one_hour)) { clock.seek(clock.nowMs() + 3_600_000L); persistExploreTime(); updateTimeLabel(); showCompanionPanel("Explore time") }
            panelAction(getString(R.string.text_advance_one_day)) { clock.seek(clock.nowMs() + 86_400_000L); persistExploreTime(); updateTimeLabel(); showCompanionPanel("Explore time") }
            panelAction(getString(R.string.text_now_leave_time_exploration)) { exitExploreTime() }
            panelSwitch(getString(R.string.text_compare_latest_observations), getString(R.string.text_these_reports_are_from_real_dates_not_the_simulated_date), observationsInSimulation) {
                observationsInSimulation = it; applySimulationObservations()
            }
            val year = Calendar.getInstance().apply { timeInMillis = clock.nowMs() }.get(Calendar.YEAR)
            val start = Calendar.getInstance().apply { clear(); set(year, Calendar.JANUARY, 1, 12, 0) }
            val currentDay = Calendar.getInstance().apply { timeInMillis = clock.nowMs() }.get(Calendar.DAY_OF_YEAR)
            companionColumn.addView(panelText(getString(R.string.text_year_timeline_1_s, year), 15f))
            companionColumn.addView(SeekBar(this).apply {
                max = start.getActualMaximum(Calendar.DAY_OF_YEAR) - 1
                progress = currentDay - 1
                contentDescription = getString(R.string.text_choose_day_of_year)
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
        companionColumn.addView(panelText(getString(R.string.text_seasons_earth_s_axis_stays_tilted_about_23_4_as_earth_orbits_the), 15f))
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
                canvas.drawText(getString(R.string.text_23_4_axis), cx + r * .65f, cy - r * .8f, paint)
                paint.color = Color.rgb(255, 240, 177)
                for (i in 0..2) {
                    val y = cy - r * .65f + i * r * .65f
                    canvas.drawLine(dpi(8f).toFloat(), y, cx-r, y, paint)
                }
                canvas.drawText(getString(R.string.text_sunlight), dpi(5f).toFloat(), cy + r * 1.3f, paint)
            }
        }.apply { minimumHeight = dpi(170f); contentDescription = getString(R.string.text_earth_s_axis_is_tilted_about_23_4_degrees_relative_to_its_orbit_a) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpi(170f)))
        val current = clock.nowMs()
        val north = CityCatalog.search("", this).first { it.id == "city:london" }
        val south = CityCatalog.search("", this).first { it.id == "city:sydney" }
        listOf(north, south).forEach { p ->
            val day = Daylight.at(p, current)
            val duration = when (day.state) {
                Daylight.State.POLAR_DAY -> getString(R.string.text_polar_day)
                Daylight.State.POLAR_NIGHT -> getString(R.string.text_polar_night)
                else -> day.daylightMinutes?.let { getString(R.string.text_about_1_sh_2_sm_daylight_2, it / 60, it % 60) } ?: getString(R.string.text_daylight_changing)
            }
            panelAction("${p.name} · $duration") { closeCompanionPanel(); globeView.camera.flyTo(p.lat, p.lon) }
        }
        companionColumn.addView(panelText(getString(R.string.text_guides_equator_tropics_23_4_and_polar_circles_66_6_compare_the_tw), 13f))
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
        panelAction(getString(R.string.text_explore)) { showCompanionPanel("Explore") }
        val active = journeyStore.active()
        companionColumn.addView(panelText(getString(R.string.text_short_local_lessons_each_restores_your_previous_globe_view_and_la), 14f))
        JourneyContent.all(this).forEach { journey ->
            val complete = journeyStore.completed(journey.id)
            panelAction(getString(R.string.text_1_s_2_s_min_3_s, journey.title, journey.minutes, if (complete) getString(R.string.text_completed) else "")) {
                if (active?.journeyId == journey.id) {
                    AlertDialog.Builder(this).setTitle(journey.title)
                        .setItems(arrayOf(getString(R.string.text_resume), getString(R.string.text_restart))) { _, index ->
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
            panelAction(getString(R.string.text_choose_a_journey)) { showCompanionPanel("Journeys") }; return
        }
        val journey = JourneyContent.all(this).firstOrNull { it.id == active.journeyId } ?: return
        val step = journey.step(active.stepId) ?: journey.steps.first()
        val index = journey.steps.indexOf(step) + 1
        companionColumn.addView(panelText(getString(R.string.text_1_s_step_2_s_of_3_s, journey.title, index, journey.steps.size), 15f, Color.rgb(173, 215, 255)))
        companionColumn.addView(panelText(step.title, 20f))
        companionColumn.addView(panelText(step.instruction, 15f))
        if (step.source != null) companionColumn.addView(panelText(getString(R.string.text_historical_source_1_s, step.source), 13f))
        recentJourneyEvent(step)?.let { event ->
            companionColumn.addView(panelText(getString(R.string.text_recent_reported_example_1_s_2_s_3_s, event.title, event.observedAtMs?.let(::formatDate), event.source), 14f))
        }
        if (journey.id == "understand_seasons" && step.month != null) {
            val sceneTime = globeView.sceneClock.nowMs()
            listOf("city:london", "city:sydney").forEach { cityId ->
                val city = CityCatalog.search("", this).first { it.id == cityId }
                val daylight = Daylight.at(city, sceneTime)
                companionColumn.addView(panelText("${city.name}: ${daylight.daylightMinutes?.let { getString(R.string.text_about_1_sh_2_sm_daylight_2, it / 60, it % 60) } ?: when (daylight.state) {
                    Daylight.State.POLAR_DAY -> getString(R.string.text_polar_day)
                    Daylight.State.POLAR_NIGHT -> getString(R.string.text_polar_night)
                    else -> getString(R.string.text_time_zone_unset)
                }}", 14f))
            }
        }
        when (step.kind) {
            "choice" -> step.options.forEachIndexed { answer, option ->
                panelAction(option) {
                    AlertDialog.Builder(this).setTitle(if (answer == step.correct) getString(R.string.text_that_s_the_pattern) else getString(R.string.text_look_again))
                        .setMessage(if (answer == step.correct) step.right else step.wrong)
                        .setPositiveButton(getString(R.string.text_continue)) { _, _ -> advanceJourney() }.show()
                }
            }
            "advance" -> panelAction(step.action ?: getString(R.string.text_advance)) {
                globeView.sceneClock.seek(globeView.sceneClock.nowMs() + step.hours * 3_600_000L)
                updateTimeLabel()
                AlertDialog.Builder(this).setMessage(getString(R.string.text_the_scene_moved_1_s_hours_ahead_watch_the_changing_sunlight_on_th, step.hours))
                    .setPositiveButton(getString(R.string.text_continue)) { _, _ -> advanceJourney() }.show()
            }
            "reveal" -> panelAction(step.action ?: getString(R.string.text_reveal)) {
                globeView.renderer.showPlateBoundaries = true
                AlertDialog.Builder(this).setMessage(getString(R.string.text_the_gold_pb2002_lines_show_a_broad_plate_edge_pattern_some_report))
                    .setPositiveButton(getString(R.string.text_continue)) { _, _ -> advanceJourney() }.show()
            }
            else -> panelAction(step.action ?: getString(R.string.text_continue)) { advanceJourney() }
        }
        panelAction(getString(R.string.text_view_globe)) { closeCompanionPanel() }
        if (index < journey.steps.size) panelAction(getString(R.string.text_skip_this_step)) { advanceJourney() }
        panelAction(getString(R.string.text_leave_journey_restore_my_view)) { leaveJourney() }
    }

    private fun advanceJourney() {
        val active = journeyStore.active() ?: return
        val journey = JourneyContent.all(this).firstOrNull { it.id == active.journeyId } ?: return
        val next = journey.next(active.stepId)
        if (next == null) {
            journeyStore.finish(journey.id)
            restoreJourneyScene(active.previous)
            AlertDialog.Builder(this).setMessage(getString(R.string.text_1_s_is_in_your_field_notebook, journey.title))
                .setPositiveButton(getString(R.string.text_done)) { _, _ -> showCompanionPanel("Journeys") }.show()
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
            val title = type.label(this)
            val status = when (feed.state) {
                EarthRepository.State.NOT_LOADED -> getString(R.string.text_not_loaded)
                EarthRepository.State.LOADING -> getString(R.string.text_checking)
                EarthRepository.State.READY -> getString(R.string.text_checked_1_s, feed.fetchedAtMs?.let(::formatDate) ?: getString(R.string.text_recently))
                EarthRepository.State.STALE -> getString(R.string.text_saved_observations_offline_or_update_failed)
                EarthRepository.State.UNAVAILABLE -> getString(R.string.text_unavailable)
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
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(AppLanguage.locale(this@MainActivity), "yMMMdHm") + " z", AppLanguage.locale(this@MainActivity)).format(Date(timeMs))

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
            AlertDialog.Builder(this).setMessage(getString(R.string.text_this_observation_is_no_longer_in_the_saved_feed))
                .setPositiveButton(getString(R.string.text_current_events)) { _, _ -> showCompanionPanel("Explore") }
                .setNegativeButton(getString(R.string.companion_close), null).show()
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
    // Tap markers or discover day/night once; hold Earth to revisit a lesson.
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
                getString(R.string.text_the_night_sky_beyond_earth_are_thousands_of_stars_planets_and_who),
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

        if (best != null && bestDeg <= thresholdDeg) {
            globeView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showEventCard(best)
        } else {
            showDayNightCard(picked[0], picked[1], onlyNew = true)
        }
    }

    private fun onGlobeHeld(x: Float, y: Float) {
        if (challengeKind != null) return
        val picked = globeView.renderer.pick(x, y, globeView.width, globeView.height) ?: return
        globeView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        showDayNightCard(picked[0], picked[1])
    }

    private fun showEventCard(event: EarthEventsProvider.Event) {
        val explain = when (event.type) {
            EarthEventsProvider.Event.Type.EARTHQUAKE ->
                getString(R.string.text_earthquakes_happen_when_stress_makes_rock_slip_along_a_fault_some)
            EarthEventsProvider.Event.Type.VOLCANO ->
                getString(R.string.text_magma_can_rise_through_earth_s_crust_this_report_does_not_confirm)
            EarthEventsProvider.Event.Type.WILDFIRE ->
                getString(R.string.text_wildfire_can_begin_naturally_or_through_human_activity_and_may_sp)
            EarthEventsProvider.Event.Type.STORM ->
                getString(R.string.text_severe_storms_draw_energy_from_warm_moist_air_this_marker_shows_t)
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
            event.magnitude?.let { append(getString(R.string.text_magnitude_1_s, "%.1f".format(AppLanguage.locale(this@MainActivity), it))) }
            event.depthKm?.let { append(getString(R.string.text_depth_1_s_km, "%.1f".format(AppLanguage.locale(this@MainActivity), it))) }
            append(getString(R.string.text_observed_1_s, event.observedAtMs?.let(::formatDate) ?: getString(R.string.text_time_not_reported)))
            event.updatedAtMs?.let { append(getString(R.string.text_source_updated_1_s, formatDate(it))) }
            append(getString(R.string.text_source_1_s, event.source))
            append(getString(R.string.text_feed_1_s, when (feed.state) {
                EarthRepository.State.READY -> getString(R.string.text_checked_1_s_2, feed.fetchedAtMs?.let(::formatDate) ?: getString(R.string.text_recently))
                EarthRepository.State.STALE -> getString(R.string.text_saved_copy_refresh_unavailable)
                EarthRepository.State.LOADING -> getString(R.string.text_checking_showing_saved_copy)
                else -> getString(R.string.text_unavailable_2)
            }))
            event.sourceUrl?.let { append(getString(R.string.text_source_link_1_s_2, it)) }
        }
        val cardBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(24f), dpi(12f), dpi(24f), dpi(8f))
            addView(panelText(facts, 15f))
            if (deviceNarrateEnabled) addView(panelText(getString(R.string.text_read_aloud), 14f, Color.rgb(173, 215, 255)).apply {
                minHeight = dpi(48f)
                setOnClickListener { speak(facts) }
            })
        }
        val cardScroll = ScrollView(this).apply { addView(cardBody) }
        AlertDialog.Builder(this)
            .setTitle(event.type.label(this))
            .setView(cardScroll)
            .setPositiveButton(if (fieldNotebook.hasEvent(event.id)) getString(R.string.text_saved) else getString(R.string.text_save)) { _, _ ->
                fieldNotebook.saveEvent(event)
                if (openPanel == "Notebook") showCompanionPanel("Notebook")
            }
            .setNeutralButton(getString(R.string.companion_why)) { _, _ ->
                AlertDialog.Builder(this).setTitle(getString(R.string.companion_why))
                    .setMessage(explain).setPositiveButton(getString(R.string.companion_close), null).show()
            }
            .setNegativeButton(getString(R.string.companion_close), null)
            .setOnDismissListener { stopNarration() }
            .show()
        if (deviceNarrateEnabled) speak(facts)
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
                faceSkyBody(com.globe.app.moon.MoonPosition.calculate(globeView.sceneClock.nowMs()), aboveGlobe = true); true
            }
            else -> false
        }
    }

    private fun faceSkyBody(dir: FloatArray, aboveGlobe: Boolean = false) {
        globeView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        globeView.camera.faceSky(dir[0], dir[1], dir[2], aboveGlobe)
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
            getString(if (sunlit) R.string.text_iss_day else R.string.text_iss_night)
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

    /** Names a tapped point; the time zone starts from the nearest offline town and can be changed. */
    private fun savePlaceAt(lat: Double, lon: Double) {
        val input = EditText(this).apply {
            hint = getString(R.string.text_place_name)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        AlertDialog.Builder(this).setTitle(getString(R.string.text_name_this_place))
            .setMessage("${"%.3f".format(AppLanguage.locale(this@MainActivity), lat)}°, ${"%.3f".format(AppLanguage.locale(this@MainActivity), lon)}°")
            .setView(input)
            .setPositiveButton(getString(R.string.text_save)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val place = placeStore.create(name, lat, lon, null)
                    placesChanged(); hideEventCard()
                    editPlaceZone(place, TimeZoneSuggester.suggest(this, lat, lon))
                }
            }.setNegativeButton(getString(R.string.text_cancel), null).show()
    }

    /** Ordinary taps introduce each lesson once; deliberate holds can always revisit it. */
    private fun showDayNightCard(lat: Double, lon: Double) = showDayNightCard(lat, lon, onlyNew = false)

    private fun showDayNightCard(lat: Double, lon: Double, onlyNew: Boolean) {
        val discovery = if (facingSun(lat, lon) > 0) Discovery.DAYTIME else Discovery.NIGHT
        if (onlyNew && journal.isUnlocked(discovery)) return
        val text = if (discovery == Discovery.DAYTIME)
            R.string.text_it_s_daytime_here_this_side_of_earth_is_facing_the_sun_right_now
        else R.string.text_it_s_night_time_here_this_side_is_turned_away_from_the_sun_those
        showCard(getString(text), discovery)
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
            body = getString(R.string.text_added_to_field_notebook_1_s, text)
            globeView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            refreshJournalButton()
        }
        eventCard.text = getString(R.string.text_close_1_s, body)
        eventCard.visibility = View.VISIBLE
        pendingSavePoint = null
        savePlaceButton.visibility = View.GONE
        speak(body)
    }

    /** Unlocks the Time Traveler discovery the first time the scrubber is used. */
    private fun awardTimeTraveler() {
        if (!journal.isUnlocked(Discovery.TIME_TRAVELER)) {
            showCard(
                "${Discovery.TIME_TRAVELER.emoji} ${Discovery.TIME_TRAVELER.title(this)}\n${Discovery.TIME_TRAVELER.fact(this)}",
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
        challengeBanner.text = getString(R.string.text_1_s_2_s_tap_to_stop, challengeKind!!.prompt(this), challengeScore)
        challengeBanner.visibility = View.VISIBLE
    }

    private fun stopChallenge() {
        val score = challengeScore
        challengeKind = null
        challengeBanner.visibility = View.GONE
        showCard(
            if (score > 0) getString(R.string.text_great_job_challenges_solved_1_s, score)
            else getString(R.string.text_challenge_stopped_play_again_anytime)
        )
    }

    private fun evaluateChallenge(x: Float, y: Float) {
        val kind = challengeKind ?: return
        val picked = globeView.renderer.pick(x, y, globeView.width, globeView.height)
        if (picked == null) {
            showCard(getString(R.string.text_tap_on_the_earth_to_answer))
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
            showCard(getString(R.string.text_yes_that_s_right))
            nextChallenge()
        } else {
            globeView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showCard(getString(R.string.text_not_quite_take_another_look_and_try_again))
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
            text = getString(R.string.text_my_discoveries)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        journalColumn.addView(TextView(this).apply {
            text = getString(R.string.text_1_s_of_2_s_found_keep_exploring, journal.unlockedCount(), journal.total)
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
                text = if (unlocked) d.title(this@MainActivity) else "? ? ?"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.DEFAULT_BOLD
            })
            textCol.addView(TextView(this).apply {
                text = if (unlocked) d.fact(this@MainActivity) else getString(R.string.text_not_found_yet)
                setTextColor(Color.rgb(180, 180, 185))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
            row.addView(textCol, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dpi(12f) })
            journalColumn.addView(row)
        }

        journalColumn.addView(TextView(this).apply {
            text = getString(R.string.text_tap_anywhere_to_close)
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
            text = getString(R.string.companion_today)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        todayColumn.addView(TextView(this).apply {
            text = SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(AppLanguage.locale(this@MainActivity), "EEEEMMMMd") + "", AppLanguage.locale(this@MainActivity)).format(Date())
            setTextColor(Color.rgb(150, 200, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, dpi(4f), 0, dpi(18f))
        })

        val phase = MoonPhase.current()
        todayColumn.addView(TextView(this).apply {
            text = getString(R.string.text_1_s_2_s_3_s_lit_up_tonight, phase.emoji, phase.name(this@MainActivity), phase.illuminationPercent)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setLineSpacing(dpi(4f).toFloat(), 1f)
            setPadding(0, 0, 0, dpi(18f))
        })

        todayColumn.addView(TextView(this).apply {
            text = getString(R.string.text_did_you_know)
            setTextColor(Color.rgb(150, 200, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
        })
        todayColumn.addView(TextView(this).apply {
            text = DailyFacts.today(this@MainActivity)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setLineSpacing(dpi(3f).toFloat(), 1f)
            setPadding(0, dpi(4f), 0, dpi(16f))
        })

        todayColumn.addView(TextView(this).apply {
            text = getString(R.string.text_tap_a_glowing_dot_on_earth_to_explore_or_find_a_new_discovery_for)
            setTextColor(Color.rgb(190, 190, 195))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setLineSpacing(dpi(3f).toFloat(), 1f)
        })

        todayColumn.addView(makePillButton(getString(R.string.text_play_a_challenge), ::dpi).apply {
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
            text = getString(R.string.text_tap_outside_the_box_to_close)
            setTextColor(Color.rgb(140, 140, 140))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, dpi(16f), 0, 0)
        })
    }

    private fun hideEventCard() {
        stopNarration()
        eventCard.visibility = View.GONE
        pendingSavePoint = null
        savePlaceButton.visibility = View.GONE
    }

    private fun relativeTime(timeMs: Long): String {
        if (timeMs <= 0L) return getString(R.string.text_ongoing)
        val diffMin = (System.currentTimeMillis() - timeMs) / 60_000L
        return when {
            diffMin < 1 -> getString(R.string.text_just_now)
            diffMin < 60 -> getString(R.string.text_elapsed_minutes, diffMin)
            diffMin < 48 * 60 -> getString(R.string.text_elapsed_hours, diffMin / 60)
            else -> getString(R.string.text_elapsed_days, diffMin / (24 * 60))
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
            EarthRenderer.CloudMode.OFF -> getString(R.string.text_clouds_off)
            EarthRenderer.CloudMode.GENERATED -> getString(R.string.text_clouds_generated)
            EarthRenderer.CloudMode.LIVE ->
                if (cloudTimestamp != null) getString(R.string.text_clouds_live_nasa_viirs_updated_1_s, cloudTimestamp)
                else getString(R.string.text_clouds_live_loading)
        }
    }

    private fun updateEclipseLabel(state: EclipseDetector.EclipseState) {
        when (state) {
            EclipseDetector.EclipseState.SOLAR -> {
                eclipseLabel.text = getString(R.string.text_solar_eclipse)
                eclipseLabel.setTextColor(Color.rgb(255, 191, 0)) // amber/gold
                eclipseLabel.visibility = View.VISIBLE
            }
            EclipseDetector.EclipseState.LUNAR -> {
                eclipseLabel.text = getString(R.string.text_lunar_eclipse)
                eclipseLabel.setTextColor(Color.rgb(173, 216, 230)) // pale blue
                eclipseLabel.visibility = View.VISIBLE
            }
            EclipseDetector.EclipseState.NEAR_SOLAR -> {
                eclipseLabel.text = getString(R.string.text_near_solar_eclipse)
                eclipseLabel.setTextColor(Color.rgb(180, 150, 80)) // dim amber
                eclipseLabel.visibility = View.VISIBLE
            }
            EclipseDetector.EclipseState.NEAR_LUNAR -> {
                eclipseLabel.text = getString(R.string.text_near_lunar_eclipse)
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
            timeLabel.text = getString(R.string.text_simulated_1_s, timeFormat.format(Date(globeView.sceneClock.nowMs())))
            timeLabel.contentDescription = getString(R.string.text_explore_time_simulated_scene)
            return
        }
        timeLabel.contentDescription = getString(R.string.text_time_controls)
        val offsetMs = globeView.sceneClock.offsetMs
        if (offsetMs == 0L) {
            timeLabel.text = getString(R.string.text_time_now)
        } else {
            val simTime = timeFormat.format(Date(globeView.sceneClock.nowMs()))
            val hours = offsetMs / 3_600_000.0
            val sign = if (hours >= 0) "+" else ""
            timeLabel.text = "$simTime (${sign}${String.format(AppLanguage.locale(this), "%.1f", hours)}h)"
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
            }, getString(R.string.text_earth), getString(R.string.text_textured_globe_with_day_night_cycle_and_diffuse_sunlight)),

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
            }, getString(R.string.text_city_lights), getString(R.string.text_visible_on_the_night_side_of_the_earth)),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Clouds: soft white wisps
                pt.color = Color.argb(200, 255, 255, 255)
                c.drawOval(RectF(s*0.05f, s*0.3f, s*0.55f, s*0.6f), pt)
                pt.color = Color.argb(160, 255, 255, 255)
                c.drawOval(RectF(s*0.3f, s*0.2f, s*0.85f, s*0.55f), pt)
                pt.color = Color.argb(120, 255, 255, 255)
                c.drawOval(RectF(s*0.15f, s*0.5f, s*0.7f, s*0.78f), pt)
            }, getString(R.string.companion_clouds), getString(R.string.text_procedural_or_live_satellite_imagery_nasa_viirs_tap_cloud_label_t)),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Sun: yellow circle with radial glow
                pt.shader = RadialGradient(s/2f, s/2f, s*0.45f,
                    intArrayOf(Color.rgb(255,255,220), Color.rgb(255,200,50), Color.argb(0,255,180,0)),
                    floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(s/2f, s/2f, s*0.45f, pt)
                pt.shader = null
            }, getString(R.string.text_sun), getString(R.string.text_billboard_glow_showing_the_sun_s_real_time_position)),

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
            }, getString(R.string.text_moon), getString(R.string.text_textured_sphere_at_its_real_orbital_position)),

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
            }, getString(R.string.text_terminator_line), getString(R.string.text_amber_line_at_the_day_night_boundary)),

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
            }, getString(R.string.text_aurora), getString(R.string.text_green_purple_glow_near_the_geomagnetic_poles_night_side_only)),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Atmosphere: blue ring around dark circle
                pt.color = Color.rgb(10, 15, 40)
                c.drawCircle(s/2f, s/2f, s*0.35f, pt)
                pt.style = Paint.Style.STROKE
                pt.strokeWidth = s*0.08f
                pt.color = Color.argb(160, 80, 160, 255)
                c.drawCircle(s/2f, s/2f, s*0.39f, pt)
                pt.style = Paint.Style.FILL
            }, getString(R.string.text_atmosphere), getString(R.string.text_blue_fresnel_glow_around_the_earth_s_rim)),

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
            }, getString(R.string.text_stars), getString(R.string.text_background_star_field)),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Earthquakes: amber/golden pulsing dot
                pt.shader = RadialGradient(s/2f, s/2f, s*0.4f,
                    intArrayOf(Color.rgb(255,153,0), Color.rgb(255,179,51), Color.argb(0,255,128,0)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(s/2f, s/2f, s*0.4f, pt)
                pt.shader = null
            }, getString(R.string.text_earthquakes), getString(R.string.text_pulsing_amber_dots_m4_5_from_usgs_past_7_days)),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Volcanoes: magenta/hot-red pulsing dot
                pt.shader = RadialGradient(s/2f, s/2f, s*0.4f,
                    intArrayOf(Color.rgb(255,26,128), Color.rgb(230,77,166), Color.argb(0,200,20,100)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(s/2f, s/2f, s*0.4f, pt)
                pt.shader = null
            }, getString(R.string.text_volcanoes), getString(R.string.text_pulsing_magenta_dots_active_eruptions_from_nasa_eonet)),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Wildfires: red pulsing dot
                pt.shader = RadialGradient(s/2f, s/2f, s*0.4f,
                    intArrayOf(Color.rgb(255,46,13), Color.rgb(255,102,38), Color.argb(0,255,60,20)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(s/2f, s/2f, s*0.4f, pt)
                pt.shader = null
            }, getString(R.string.text_wildfires), getString(R.string.text_pulsing_red_dots_active_fires_from_nasa_eonet)),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Storms: electric blue pulsing dot
                pt.shader = RadialGradient(s/2f, s/2f, s*0.4f,
                    intArrayOf(Color.rgb(64,166,255), Color.rgb(140,204,255), Color.argb(0,64,166,255)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(s/2f, s/2f, s*0.4f, pt)
                pt.shader = null
            }, getString(R.string.text_storms), getString(R.string.text_pulsing_blue_dots_severe_storms_from_nasa_eonet_tap_any_dot_for_d)),

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
            }, getString(R.string.text_iss_orbit), getString(R.string.text_thin_line_showing_the_international_space_station_s_path)),

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
            }, getString(R.string.text_sun_moon_arrows), getString(R.string.text_2d_overlay_arrows_pointing_toward_the_sun_and_moon)),

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
            }, getString(R.string.text_eclipse_alerts), getString(R.string.text_notifies_when_sun_earth_moon_alignment_approaches_an_eclipse)),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Time scrubber: slider track with knob
                pt.color = Color.rgb(80, 80, 80)
                c.drawRoundRect(RectF(s*0.08f, s*0.44f, s*0.92f, s*0.56f), s*0.06f, s*0.06f, pt)
                pt.color = Color.rgb(100, 180, 255)
                c.drawRoundRect(RectF(s*0.08f, s*0.44f, s*0.55f, s*0.56f), s*0.06f, s*0.06f, pt)
                pt.color = Color.WHITE
                c.drawCircle(s*0.55f, s*0.5f, s*0.12f, pt)
            }, getString(R.string.text_time_scrubber), getString(R.string.text_drag_the_slider_to_simulate_24_hours_releases_to_snap_back_to_now))
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
            text = getString(R.string.text_legend)
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
                marginEnd = dp(14f)
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
            text = getString(R.string.text_tap_anywhere_to_close)
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
            contentDescription = getString(R.string.companion_close)
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
        musicButton.text = if (musicEnabled) getString(R.string.text_music_on) else getString(R.string.text_music_off)
    }

    // ------------------------------------------------------------------
    // Read-aloud narration (for early readers)
    // ------------------------------------------------------------------

    private fun toggleNarration() {
        setNarrationEnabled(!narrateEnabled)
    }

    private fun setNarrationEnabled(enabled: Boolean) {
        narrateEnabled = enabled
        prefs.edit().putBoolean(PREF_NARRATE, narrateEnabled).apply()
        updateNarrateButton()
        if (narrateEnabled) {
            speak(getString(R.string.text_read_to_me_is_on_i_ll_read_the_cards_out_loud))
        } else taliaNarrator.stop()
    }

    private fun setDeviceNarrationEnabled(enabled: Boolean) {
        deviceNarrateEnabled = enabled
        prefs.edit().putBoolean(PREF_DEVICE_NARRATE, enabled).apply()
        if (!enabled) tts?.stop()
    }

    private fun updateNarrateButton() {
        narrateButton.text = if (narrateEnabled) getString(R.string.text_read_on) else getString(R.string.text_read_off)
    }

    /** Each voice has a separate opt-in; fixed cards never substitute Android speech. */
    private fun speak(text: String) {
        if (!narrateEnabled && !deviceNarrateEnabled) return
        stopNarration()
        if (taliaNarrator.clipsFor(text) != null) {
            if (narrateEnabled) taliaNarrator.speak(text)
        } else speakWithDevice(text)
    }

    private fun speakWithDevice(text: String) {
        if (!deviceNarrateEnabled || !ttsReady) return
        val clean = NarrationText.clean(text)
        if (clean.isNotEmpty()) tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "card")
    }

    private fun stopNarration() {
        taliaNarrator.stop()
        tts?.stop()
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
        val card = com.globe.app.ui.ReadingColumn(this, 560).apply {
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
            text = getString(R.string.text_a_living_window_onto_earth)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setLineSpacing(dpi(2f).toFloat(), 1f)
            setPadding(0, 0, 0, dpi(18f))
        })

        val tips = listOf(
            getString(R.string.text_drag_to_rotate),
            getString(R.string.text_pinch_to_zoom),
            getString(R.string.text_tap_the_globe_to_discover_more)
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

        card.addView(panelText(getString(R.string.narration_onboarding_prompt), 15f).apply {
            gravity = Gravity.CENTER
            setPadding(0, dpi(18f), 0, 0)
        })

        card.addView(makePillButton(getString(R.string.narration_explore_with_talia), ::dpi).apply {
            minHeight = dpi(48f)
            isFocusable = true
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                dismissOnboarding()
                setDeviceNarrationEnabled(false)
                setNarrationEnabled(true)
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dpi(20f)
        })

        card.addView(makePillButton(getString(R.string.narration_explore_quietly), ::dpi).apply {
            minHeight = dpi(48f)
            isFocusable = true
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                dismissOnboarding()
                setDeviceNarrationEnabled(false)
                setNarrationEnabled(false)
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dpi(12f) })

        val scroll = ScrollView(this).apply {
            addView(card, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL
            ))
        }
        overlay.addView(scroll, FrameLayout.LayoutParams(
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
        stopNarration()
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
        taliaNarrator.stop()
        tts?.shutdown()
        tts = null
    }
}
