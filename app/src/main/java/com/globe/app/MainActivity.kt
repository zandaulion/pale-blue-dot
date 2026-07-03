package com.globe.app

import android.content.Context
import android.content.SharedPreferences
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
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import com.globe.app.eclipse.EclipseDetector
import com.globe.app.events.EarthEventsProvider
import com.globe.app.events.GlobePicker
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
        private const val PREF_CLOUDS_VISIBLE = "clouds_visible"
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
    private lateinit var onboardingOverlay: FrameLayout
    private lateinit var shareButton: TextView
    private lateinit var journalButton: TextView
    private lateinit var journalOverlay: FrameLayout
    private lateinit var journalColumn: LinearLayout
    private lateinit var todayButton: TextView
    private lateinit var todayOverlay: FrameLayout
    private lateinit var todayColumn: LinearLayout
    private lateinit var eventCard: TextView
    private lateinit var challengeBanner: TextView
    private lateinit var prefs: SharedPreferences
    private lateinit var journal: DiscoveryJournal

    // Challenge (quiz) mode state
    private var challengeKind: ChallengeKind? = null
    private var challengeScore = 0

    private val hideEventCardRunnable = Runnable { eventCard.visibility = View.GONE }

    private var mediaPlayer: MediaPlayer? = null
    private var musicEnabled = true
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
            text = "\u2601 Clouds: procedural"
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                toggleClouds()
            }
            isClickable = true
        }

        timeLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
            gravity = Gravity.CENTER
            text = "\u23f0 Now"
        }

        timeScrubber = SeekBar(this).apply {
            max = 1000
            progress = 500 // center = now
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val fraction = (progress - 500) / 500.0
                    TimeProvider.offsetMs = (fraction * scrubberRangeMs).toLong()
                    // Invalidate position caches so they recompute immediately
                    com.globe.app.earth.SunPosition.invalidateCache()
                    com.globe.app.moon.MoonPosition.invalidateCache()
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
                            TimeProvider.offsetMs = (fraction * scrubberRangeMs).toLong()
                            com.globe.app.earth.SunPosition.invalidateCache()
                            com.globe.app.moon.MoonPosition.invalidateCache()
                            updateTimeLabel()
                        }
                        start()
                    }
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
        journal = DiscoveryJournal(this)
        journalOverlay = createJournalOverlay()
        todayOverlay = createTodayOverlay()
        musicEnabled = prefs.getBoolean(PREF_MUSIC_ENABLED, true)
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
        globeView.renderer.earthRenderer.cloudsVisible =
            prefs.getBoolean(PREF_CLOUDS_VISIBLE, true)

        val margin = dp(12f)

        val root = FrameLayout(this)
        root.addView(globeView)

        // Cloud label — bottom left
        root.addView(cloudLabel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.START
        ).apply { setMargins(margin, margin, margin, margin) })

        // Eclipse label — bottom right
        root.addView(eclipseLabel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END
        ).apply { setMargins(margin, margin, margin, margin) })

        // Music + Narration toggles — stacked top right
        val topRightButtons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(musicButton)
            addView(narrateButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12f) })
        }
        root.addView(topRightButtons, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply { setMargins(margin, dp(24f), margin, 0) })

        // Time label — top center
        root.addView(timeLabel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { setMargins(margin, dp(24f), margin, 0) })

        // Time scrubber — above the indicator arrows (~20% from bottom)
        root.addView(timeScrubber, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply { setMargins(margin, 0, margin, dp(100f)) })

        // Legend button — bottom right, above eclipse label
        root.addView(legendButton, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END
        ).apply { setMargins(margin, margin, margin, dp(32f)) })

        // Today + Journal + Share buttons — stacked top left
        val topLeftButtons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(todayButton)
            addView(journalButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10f) })
            addView(shareButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10f) })
        }
        root.addView(topLeftButtons, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        ).apply { setMargins(margin, dp(24f), margin, 0) })

        // Event info card — bottom center, above the time scrubber
        root.addView(eventCard, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { setMargins(margin, 0, margin, dp(150f)) })

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

        setContentView(root)

        if (!prefs.getBoolean(PREF_ONBOARDED, false)) {
            // First ever launch: welcome tutorial only (don't stack the Today panel).
            prefs.edit()
                .putLong(PREF_TODAY_SHOWN_DAY, System.currentTimeMillis() / 86_400_000L)
                .apply()
            onboardingOverlay.visibility = View.VISIBLE
        } else {
            // Greet returning kids with today's panel once per calendar day.
            maybeShowTodayOnLaunch()
        }
    }

    private fun toggleClouds() {
        val earth = globeView.renderer.earthRenderer
        earth.cloudsVisible = !earth.cloudsVisible
        updateCloudLabel()
    }

    /**
     * Capture the current globe view and share it. Sharing leaves the app and
     * carries a store link, so per Google Play Families policy it is placed
     * behind a parental gate.
     */
    private fun shareCurrentView() {
        ParentalGate.show(this) {
            com.globe.app.share.ShareManager.share(this, globeView)
        }
    }

    // ------------------------------------------------------------------
    // Tap-to-learn: identify event markers, or explain day/night anywhere
    // ------------------------------------------------------------------

    private fun onGlobeTapped(x: Float, y: Float) {
        if (challengeKind != null) {
            evaluateChallenge(x, y)
            return
        }

        val picked = GlobePicker.pick(globeView.camera, x, y, globeView.width, globeView.height)
        if (picked == null) {
            // Tapped the sky beyond Earth — a stargazing moment.
            globeView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showCard(
                "🌟 The night sky!\nBeyond Earth are thousands of stars, planets, and whole galaxies.",
                Discovery.STARGAZER
            )
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
        val (emoji, explain) = when (event.type) {
            EarthEventsProvider.Event.Type.EARTHQUAKE -> "🔶" to
                "The ground shook here. Earthquakes happen when giant slabs of rock deep underground suddenly slip past each other."
            EarthEventsProvider.Event.Type.VOLCANO -> "🌋" to
                "A volcano is erupting here — hot melted rock called lava is pushing up from deep inside the Earth."
            EarthEventsProvider.Event.Type.WILDFIRE -> "🔥" to
                "A large wildfire is burning across the land here. Satellites can spot the heat from space."
            EarthEventsProvider.Event.Type.STORM -> "🌀" to
                "A powerful swirling storm is here. The biggest ones are called hurricanes, typhoons, or cyclones."
        }
        val discovery = when (event.type) {
            EarthEventsProvider.Event.Type.EARTHQUAKE -> Discovery.EARTHQUAKE
            EarthEventsProvider.Event.Type.VOLCANO -> Discovery.VOLCANO
            EarthEventsProvider.Event.Type.WILDFIRE -> Discovery.WILDFIRE
            EarthEventsProvider.Event.Type.STORM -> Discovery.STORM
        }
        showCard("$emoji ${event.title}\n$explain\n· ${relativeTime(event.timeMs)}", discovery)
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
        val sun = com.globe.app.earth.SunPosition.calculate()
        return nx * sun[0] + ny * sun[1] + nz * sun[2]
    }

    /** Tap any land or ocean to learn whether it's day or night there right now. */
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
    }

    /**
     * Shows the info card. If [discovery] is provided and unlocked for the first
     * time, the card is prefixed with a celebration and the journal updates.
     */
    private fun showCard(text: String, discovery: Discovery? = null) {
        var body = text
        if (discovery != null && journal.unlock(discovery)) {
            body = "✨ New discovery!  (${journal.unlockedCount()}/${journal.total})\n\n$text"
            globeView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            refreshJournalButton()
        }
        eventCard.text = body
        eventCard.visibility = View.VISIBLE
        eventCard.removeCallbacks(hideEventCardRunnable)
        eventCard.postDelayed(hideEventCardRunnable, 10_000L)
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
        val picked = GlobePicker.pick(globeView.camera, x, y, globeView.width, globeView.height)
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
        eventCard.removeCallbacks(hideEventCardRunnable)
        eventCard.visibility = View.GONE
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
        val visible = globeView.renderer.earthRenderer.cloudsVisible
        cloudLabel.text = if (!visible) {
            "\u2601 Clouds: off"
        } else if (cloudTimestamp != null) {
            "\u2601 Clouds: live (NASA VIIRS)\n    Updated: $cloudTimestamp"
        } else {
            "\u2601 Clouds: procedural"
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
        val offsetMs = TimeProvider.offsetMs
        if (offsetMs == 0L) {
            timeLabel.text = "\u23f0 Now"
        } else {
            val simTime = timeFormat.format(Date(TimeProvider.nowMs()))
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
                setVolume(0.4f, 0.4f)
            }
        }
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
            setBackgroundColor(Color.argb(240, 0, 2, 12))
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
            text = "Welcome to\nPale Blue Dot!"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setLineSpacing(dpi(2f).toFloat(), 1f)
            setPadding(0, 0, 0, dpi(18f))
        })

        val tips = listOf(
            "\uD83D\uDD90  Spin the globe with your finger",
            "\uD83D\uDC46  Tap the glowing dots to see what's happening on Earth right now",
            "\uD83D\uDD50  Slide the time bar to turn day into night",
            "\uD83D\uDCD6  Collect discoveries and come back each day"
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

        card.addView(makePillButton("Let's explore!  \uD83D\uDE80", ::dpi).apply {
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

    override fun onResume() {
        super.onResume()
        globeView.onResume()
        if (musicEnabled) startMusic()
    }

    override fun onPause() {
        super.onPause()
        saveViewState()
        globeView.onPause()
        stopMusic()
        tts?.stop()
    }

    /** Persist the camera pose and cloud visibility so the app reopens as left. */
    private fun saveViewState() {
        prefs.edit()
            .putFloat(PREF_CAM_AZ, globeView.camera.azimuth)
            .putFloat(PREF_CAM_EL, globeView.camera.elevation)
            .putFloat(PREF_CAM_DIST, globeView.camera.distance)
            .putBoolean(PREF_CLOUDS_VISIBLE, globeView.renderer.earthRenderer.cloudsVisible)
            .apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMusic()
        tts?.shutdown()
        tts = null
    }
}
