package com.globe.app

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.MediaPlayer
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
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.View
import com.globe.app.eclipse.EclipseDetector
import com.globe.app.events.EarthEventsProvider
import com.globe.app.events.GlobePicker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val PREFS_NAME = "globe_prefs"
        private const val PREF_MUSIC_ENABLED = "music_enabled"
        private const val PREF_CLOUDS_VISIBLE = "clouds_visible"
        private const val PREF_CAM_AZ = "cam_az"
        private const val PREF_CAM_EL = "cam_el"
        private const val PREF_CAM_DIST = "cam_dist"
    }

    private lateinit var globeView: GlobeSurfaceView
    private lateinit var cloudLabel: TextView
    private lateinit var eclipseLabel: TextView
    private lateinit var timeLabel: TextView
    private lateinit var timeScrubber: SeekBar
    private lateinit var legendButton: TextView
    private lateinit var legendOverlay: FrameLayout
    private lateinit var musicButton: TextView
    private lateinit var shareButton: TextView
    private lateinit var locateButton: TextView
    private lateinit var eventCard: TextView
    private lateinit var prefs: SharedPreferences

    private val hideEventCardRunnable = Runnable { eventCard.visibility = View.GONE }

    // Latest known user location (for the "My location" fly-to button)
    private var userLat: Double? = null
    private var userLon: Double? = null
    private var mediaPlayer: MediaPlayer? = null
    private var musicEnabled = true
    private var scrubberAnimator: ValueAnimator? = null
    private var cloudTimestamp: String? = null
    private var lastEclipseState: EclipseDetector.EclipseState = EclipseDetector.EclipseState.NONE

    private var locationManager: LocationManager? = null
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            userLat = location.latitude
            userLon = location.longitude
            globeView.renderer.locationPinRenderer.setLocation(
                location.latitude, location.longitude
            )
        }
        @Deprecated("Deprecated in API level 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

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
        musicEnabled = prefs.getBoolean(PREF_MUSIC_ENABLED, true)

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

        shareButton = makePillButton("↑  Share", dp).apply {
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                shareCurrentView()
            }
        }

        locateButton = makePillButton("◎  My location", dp).apply {
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                flyToMyLocation()
            }
        }

        // Info card shown when the user taps an event marker on the globe
        eventCard = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
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

        // Music toggle — top right
        root.addView(musicButton, FrameLayout.LayoutParams(
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

        // Share + My location buttons — stacked top left
        val topLeftButtons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(shareButton)
            addView(locateButton, LinearLayout.LayoutParams(
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

        // Legend overlay — full screen, initially hidden
        root.addView(legendOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)

        // Request location permission for the location pin
        requestLocationPermission()
    }

    private fun toggleClouds() {
        val earth = globeView.renderer.earthRenderer
        earth.cloudsVisible = !earth.cloudsVisible
        updateCloudLabel()
    }

    /** Capture the current globe view (no UI chrome) and open the share sheet. */
    private fun shareCurrentView() {
        com.globe.app.share.ShareManager.share(this, globeView)
    }

    // ------------------------------------------------------------------
    // Tap-to-identify event markers
    // ------------------------------------------------------------------

    private fun onGlobeTapped(x: Float, y: Float) {
        val picked = GlobePicker.pick(globeView.camera, x, y, globeView.width, globeView.height)
        if (picked == null) {
            hideEventCard()
            return
        }

        val events = globeView.renderer.earthEventsRenderer.events
        if (events.isEmpty()) {
            hideEventCard()
            return
        }

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
            hideEventCard()
        }
    }

    private fun showEventCard(event: EarthEventsProvider.Event) {
        val emoji = when (event.type) {
            EarthEventsProvider.Event.Type.EARTHQUAKE -> "🔶"
            EarthEventsProvider.Event.Type.VOLCANO -> "🌋"
            EarthEventsProvider.Event.Type.WILDFIRE -> "🔥"
            EarthEventsProvider.Event.Type.STORM -> "🌀"
        }
        eventCard.text = "$emoji ${event.title}\n   ${relativeTime(event.timeMs)}"
        eventCard.visibility = View.VISIBLE
        eventCard.removeCallbacks(hideEventCardRunnable)
        eventCard.postDelayed(hideEventCardRunnable, 8_000L)
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

    /** Smoothly rotate the globe to center the user's location, if known. */
    private fun flyToMyLocation() {
        val lat = userLat
        val lon = userLon
        if (lat == null || lon == null) {
            Toast.makeText(this, "Location not available yet.", Toast.LENGTH_SHORT).show()
            return
        }
        globeView.camera.flyTo(lat, lon)
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
                // Location pin: cyan dot with ring
                pt.color = Color.rgb(0, 230, 180)
                c.drawCircle(s/2f, s/2f, s*0.15f, pt)
                pt.style = Paint.Style.STROKE
                pt.strokeWidth = s*0.04f
                pt.color = Color.argb(140, 0, 230, 180)
                c.drawCircle(s/2f, s/2f, s*0.3f, pt)
                c.drawCircle(s/2f, s/2f, s*0.42f, pt)
                pt.style = Paint.Style.FILL
            }, "Location pin", "Your GPS position on the globe (cyan dot)"),

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
    // Location
    // ------------------------------------------------------------------

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        }
        // If denied, just don't show the pin — no crash
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = lm

        // Use the last known location immediately if available
        val lastKnown = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (lastKnown != null) {
            userLat = lastKnown.latitude
            userLon = lastKnown.longitude
            globeView.renderer.locationPinRenderer.setLocation(
                lastKnown.latitude, lastKnown.longitude
            )
        }

        // Request updates from network provider (coarse); fall back to GPS if unavailable
        try {
            lm.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                60_000L, // min time between updates: 60 s
                1000f,   // min distance: 1 km
                locationListener
            )
        } catch (_: IllegalArgumentException) {
            // Network provider not available, try GPS
            try {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    60_000L,
                    1000f,
                    locationListener
                )
            } catch (_: IllegalArgumentException) {
                // No provider available
            }
        }
    }

    private fun stopLocationUpdates() {
        locationManager?.removeUpdates(locationListener)
    }

    override fun onResume() {
        super.onResume()
        globeView.onResume()
        if (musicEnabled) startMusic()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        saveViewState()
        globeView.onPause()
        stopMusic()
        stopLocationUpdates()
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
    }
}
