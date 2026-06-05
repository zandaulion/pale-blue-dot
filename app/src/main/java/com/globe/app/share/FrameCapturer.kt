package com.globe.app.share

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.SurfaceView

/**
 * Grabs the rendered pixels from a [SurfaceView] (the GLSurfaceView globe).
 *
 * Uses [PixelCopy] (API 24+, matching the app's minSdk), which copies ONLY the
 * surface contents. The Android View overlays — the time scrubber, buttons, and
 * labels — live in a FrameLayout *above* the surface, so they are naturally
 * excluded and the captured frame shows a clean globe with no UI chrome.
 *
 * v1 captures a single still. The same surface-read mechanism can later back a
 * `captureSequence(...)` that drives N frames for an MP4 export (v2); the
 * watermark and share-plumbing stages downstream stay unchanged.
 */
object FrameCapturer {

    /**
     * Asynchronously copies the current surface contents into a Bitmap.
     *
     * @param onResult invoked on a background thread with the captured bitmap,
     *                 or null on failure. Callers must marshal back to the UI
     *                 thread themselves before touching UI.
     */
    fun captureStill(surface: SurfaceView, onResult: (Bitmap?) -> Unit) {
        val width = surface.width
        val height = surface.height
        if (width <= 0 || height <= 0 || !surface.holder.surface.isValid) {
            onResult(null)
            return
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val thread = HandlerThread("FrameCapturer").apply { start() }
        val handler = Handler(thread.looper)

        try {
            PixelCopy.request(surface, bitmap, { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    onResult(bitmap)
                } else {
                    bitmap.recycle()
                    onResult(null)
                }
                thread.quitSafely()
            }, handler)
        } catch (e: IllegalArgumentException) {
            // Surface became invalid between the check and the request.
            bitmap.recycle()
            thread.quitSafely()
            onResult(null)
        }
    }
}
