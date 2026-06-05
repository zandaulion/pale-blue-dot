package com.globe.app.share

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.SurfaceView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.globe.app.TimeProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * One-call entry point for the "Share" button. Pipeline: capture the globe →
 * brand it → write to cache → hand a content URI to the Android share sheet.
 *
 * Everything happens on-device: no upload, no network, consistent with the
 * app's privacy posture. The user picks the destination app (Instagram, X,
 * Messages, …) from the system chooser, so no per-platform SDKs are needed.
 */
object ShareManager {

    private const val PLAY_URL =
        "https://play.google.com/store/apps/details?id=com.zandaulion.palebluedot"

    /** Must match the `android:authorities` in AndroidManifest.xml (applicationId + ".fileprovider"). */
    private const val FILE_PROVIDER_AUTHORITY = "com.zandaulion.palebluedot.fileprovider"

    private const val SHARE_DIR = "share"
    private const val SHARE_FILE = "pale_blue_dot.jpg"
    private const val JPEG_QUALITY = 92

    fun share(activity: Activity, surface: SurfaceView) {
        FrameCapturer.captureStill(surface) { captured ->
            // Runs on the FrameCapturer background thread.
            if (captured == null) {
                toast(activity, "Couldn't capture the view — try again.")
                return@captureStill
            }

            val branded = ShareWatermark.apply(captured)
            val uri = writeToCache(activity, branded)
            branded.recycle()

            if (uri == null) {
                toast(activity, "Couldn't prepare the image.")
            } else {
                activity.runOnUiThread { launchShareSheet(activity, uri) }
            }
        }
    }

    private fun writeToCache(activity: Activity, bitmap: Bitmap): Uri? = try {
        val dir = File(activity.cacheDir, SHARE_DIR).apply { mkdirs() }
        val file = File(dir, SHARE_FILE)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        FileProvider.getUriForFile(activity, FILE_PROVIDER_AUTHORITY, file)
    } catch (e: Exception) {
        null
    }

    private fun launchShareSheet(activity: Activity, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, buildCaption())
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, "Share your view"))
    }

    /** Uses the simulated time so a scrubbed view reports the time it actually shows. */
    private fun buildCaption(): String {
        val fmt = SimpleDateFormat("MMM d, yyyy HH:mm 'UTC'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val time = fmt.format(Date(TimeProvider.nowMs()))
        return "🌍 Earth — $time\nMade with Pale Blue Dot → $PLAY_URL"
    }

    private fun toast(activity: Activity, message: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }
}
