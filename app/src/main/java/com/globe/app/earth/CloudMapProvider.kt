package com.globe.app.earth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Downloads near-real-time satellite imagery from NASA Worldview (VIIRS)
 * and extracts cloud coverage by brightness thresholding.
 *
 * The source image is a true-color equirectangular snapshot from the
 * NASA Worldview Snapshot API (VIIRS SNPP Corrected Reflectance).
 * Clouds appear as bright white pixels; land and ocean are darker.
 * We extract clouds by converting pixel brightness to a grayscale
 * cloud opacity value.
 */
class CloudMapProvider(private val context: Context) {

    data class Result(val bitmap: Bitmap, val timestamp: String)

    companion object {
        private const val TAG = "CloudMapProvider"
        private const val CACHE_FILENAME = "cloud_map.png"
        private const val CACHE_MAX_AGE_MS = 6 * 60 * 60 * 1000L // 6 hours
        private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        // Brightness threshold: pixels above this are considered cloud.
        // Range 0.0-1.0. Lower = more clouds detected, higher = fewer.
        private const val CLOUD_THRESHOLD_LOW = 0.45f
        private const val CLOUD_THRESHOLD_HIGH = 0.75f

        private fun buildUrl(): String {
            // Use yesterday's date (today's data may not be available yet)
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = dateFormat.format(cal.time)

            return "https://wvs.earthdata.nasa.gov/api/v1/snapshot" +
                "?REQUEST=GetSnapshot" +
                "&LAYERS=VIIRS_SNPP_CorrectedReflectance_TrueColor" +
                "&CRS=EPSG:4326" +
                "&BBOX=-90,-180,90,180" +
                "&WIDTH=2048&HEIGHT=1024" +
                "&FORMAT=image/jpeg" +
                "&TIME=$date"
        }
    }

    /**
     * Fetches the cloud map, using the disk cache if fresh enough.
     * Returns null on failure. Call from a background thread.
     */
    fun fetch(): Result? {
        val cacheFile = File(context.cacheDir, CACHE_FILENAME)

        // Use cache if fresh
        if (cacheFile.exists() &&
            System.currentTimeMillis() - cacheFile.lastModified() < CACHE_MAX_AGE_MS
        ) {
            val bmp = BitmapFactory.decodeFile(cacheFile.absolutePath)
            if (bmp != null) {
                Log.d(TAG, "Loaded cloud map from cache")
                return Result(bmp, formatTimestamp(cacheFile.lastModified()))
            }
        }

        // Download
        return try {
            val url = buildUrl()
            Log.d(TAG, "Downloading satellite imagery: $url")
            val connection = URL(url).openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            val bytes = connection.getInputStream().use { it.readBytes() }
            val sourceBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return null

            Log.d(TAG, "Downloaded: ${sourceBmp.width}x${sourceBmp.height}, extracting clouds...")
            val cloudBmp = extractClouds(sourceBmp)
            sourceBmp.recycle()

            // Cache the processed cloud map
            cacheFile.outputStream().use { out ->
                cloudBmp.compress(Bitmap.CompressFormat.PNG, 90, out)
            }

            Log.d(TAG, "Cloud map ready: ${cloudBmp.width}x${cloudBmp.height}")
            Result(cloudBmp, formatTimestamp(System.currentTimeMillis()))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download cloud map, using procedural fallback", e)
            // Try stale cache as last resort
            if (cacheFile.exists()) {
                val bmp = BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (bmp != null) Result(bmp, formatTimestamp(cacheFile.lastModified())) else null
            } else {
                null
            }
        }
    }

    /**
     * Extracts clouds from a true-color satellite image by brightness thresholding.
     * Returns a grayscale bitmap where white = cloud, black = clear sky.
     */
    private fun extractClouds(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

            // Perceived brightness (luminance)
            val brightness = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

            // Smooth threshold: ramp from 0 at CLOUD_THRESHOLD_LOW to 1 at CLOUD_THRESHOLD_HIGH
            val cloud = ((brightness - CLOUD_THRESHOLD_LOW) /
                (CLOUD_THRESHOLD_HIGH - CLOUD_THRESHOLD_LOW)).coerceIn(0f, 1f)

            // Also check saturation — clouds are whitish (low saturation).
            // Land can be bright but colorful.
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            val saturation = if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f
            val satFactor = (1f - saturation * 2f).coerceIn(0f, 1f)

            val value = (cloud * satFactor * 255f).toInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(value, value, value)
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun formatTimestamp(millis: Long): String = timestampFormat.format(Date(millis))
}
