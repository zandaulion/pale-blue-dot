package com.globe.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
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
