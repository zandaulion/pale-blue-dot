package com.globe.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.View
import com.globe.app.eclipse.EclipseDetector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    private lateinit var globeView: GlobeSurfaceView
    private lateinit var cloudLabel: TextView
    private lateinit var eclipseLabel: TextView
    private lateinit var timeLabel: TextView
    private lateinit var timeScrubber: SeekBar
    private lateinit var legendButton: TextView
    private lateinit var legendOverlay: FrameLayout
    private var cloudTimestamp: String? = null
    private var lastEclipseState: EclipseDetector.EclipseState = EclipseDetector.EclipseState.NONE

    private var locationManager: LocationManager? = null
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
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
            setOnClickListener { toggleClouds() }
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

                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    // Snap back to "now" when released
                    seekBar.progress = 500
                    TimeProvider.offsetMs = 0L
                    com.globe.app.earth.SunPosition.invalidateCache()
                    com.globe.app.moon.MoonPosition.invalidateCache()
                    updateTimeLabel()
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
            setOnClickListener { showLegend() }
        }

        legendOverlay = createLegendOverlay(dp)

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
            }
        )

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
                // Earthquakes: orange-red pulsing dot
                pt.shader = RadialGradient(s/2f, s/2f, s*0.4f,
                    intArrayOf(Color.rgb(255,100,50), Color.rgb(255,70,20), Color.argb(0,255,50,10)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(s/2f, s/2f, s*0.4f, pt)
                pt.shader = null
            }, "Earthquakes", "Pulsing orange-red dots (M4.5+ from USGS, past 7 days)"),

            LegendEntry(drawLegendIcon(iconSize, p) { c, s, pt ->
                // Volcanoes: yellow-orange pulsing dot
                pt.shader = RadialGradient(s/2f, s/2f, s*0.4f,
                    intArrayOf(Color.rgb(255,220,50), Color.rgb(255,160,30), Color.argb(0,255,130,10)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(s/2f, s/2f, s*0.4f, pt)
                pt.shader = null
            }, "Volcanoes", "Pulsing yellow dots (active eruptions from NASA EONET)"),

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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        globeView.onPause()
        stopLocationUpdates()
    }
}
