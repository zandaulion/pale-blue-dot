package com.globe.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import com.globe.app.MainActivity
import com.globe.app.R
import com.globe.app.data.EarthRepository
import com.globe.app.data.TodayBriefing
import com.globe.app.places.Daylight
import com.globe.app.places.PlaceStore
import com.globe.app.places.SavedPlace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

class EarthTodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        enqueue(context, ids, goAsync())
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        enqueue(context, intArrayOf(id), goAsync())
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val edit = context.getSharedPreferences("widget_places", Context.MODE_PRIVATE).edit()
        ids.forEach { edit.remove(it.toString()) }
        edit.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH || intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == Intent.ACTION_LOCALE_CHANGED || intent.action == Intent.ACTION_TIME_CHANGED) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, EarthTodayWidget::class.java))
            enqueue(context, ids, goAsync())
        } else super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_REFRESH = "com.globe.app.widget.REFRESH"
        private val executor = Executors.newSingleThreadExecutor()
        private val lock = Any()
        private val pendingIds = mutableSetOf<Int>()
        private val pendingResults = mutableListOf<PendingResult>()
        private var running = false
        private const val MAX_CACHED_BITMAPS = 4
        /** Worker-thread only. Access order makes the first key the least recently used. */
        private val bitmaps = LinkedHashMap<String, Bitmap>(8, 0.75f, true)

        fun requestUpdate(context: Context) {
            context.applicationContext.sendBroadcast(Intent(context, EarthTodayWidget::class.java).setAction(ACTION_REFRESH))
        }

        fun updateOne(context: Context, id: Int) { enqueue(context, intArrayOf(id), null) }

        private fun enqueue(context: Context, ids: IntArray, result: PendingResult?) {
            synchronized(lock) {
                pendingIds.addAll(ids.toList())
                if (result != null) pendingResults.add(result)
                if (running) return
                running = true
            }
            executor.execute {
                val app = context.applicationContext
                while (true) {
                    val work: List<Int>
                    val finishes: List<PendingResult>
                    synchronized(lock) {
                        work = pendingIds.toList(); pendingIds.clear()
                        finishes = pendingResults.toList(); pendingResults.clear()
                    }
                    try { renderAll(app, work) }
                    catch (e: Exception) { Log.w("EarthTodayWidget", "Widget refresh failed", e) }
                    finally { finishes.forEach { it.finish() } }
                    synchronized(lock) {
                        if (pendingIds.isEmpty() && pendingResults.isEmpty()) { running = false; break }
                    }
                }
            }
        }

        private fun renderAll(context: Context, ids: List<Int>) {
            if (ids.isEmpty()) return
            val manager = AppWidgetManager.getInstance(context)
            val repository = EarthRepository.get(context)
            repository.awaitCache(2_000L)
            val snapshot = repository.snapshot()
            val store = PlaceStore(context)
            val config = context.getSharedPreferences("widget_places", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val live = manager.getAppWidgetIds(ComponentName(context, EarthTodayWidget::class.java)).toSet()
            for (id in ids) {
                if (id !in live) continue
                val selected = config.getString(id.toString(), null)
                val place = store.all().firstOrNull { it.id == selected }
                val brief = TodayBriefing.build(now, place, snapshot)
                val size = WidgetSize.from(context, manager.getAppWidgetOptions(id))
                val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // The launcher picks per actual size, e.g. a Fold's cover vs inner screen.
                    RemoteViews(mapOf(
                        SizeF(180f, 90f) to build(context, id, now, place, brief, size, expanded = false),
                        SizeF(180f, 180f) to build(context, id, now, place, brief, size, expanded = true)
                    ))
                } else build(context, id, now, place, brief, size, expanded = size.heightDp >= 180)
                manager.updateAppWidget(id, views)
            }
        }

        private fun build(
            context: Context, id: Int, now: Long, place: SavedPlace?, brief: TodayBriefing.Briefing,
            size: WidgetSize, expanded: Boolean
        ): RemoteViews {
            val views = RemoteViews(context.packageName,
                if (expanded) R.layout.widget_earth_expanded else R.layout.widget_earth_compact)
            views.setTextViewText(R.id.widget_moon,
                "${brief.moon.emoji} ${brief.moon.name} · ${brief.moon.illuminationPercent}%")
            val placeLine = if (place == null) "Choose a place" else {
                val day = brief.daylight!!
                val whenText = when (day.state) {
                    Daylight.State.POLAR_DAY -> "polar day"
                    Daylight.State.POLAR_NIGHT -> "polar night"
                    Daylight.State.ZONE_UNSET -> "time zone unset"
                    Daylight.State.ORDINARY -> if (day.isDay) "daytime" else "nighttime"
                }
                "${place.name} · $whenText"
            }
            views.setTextViewText(R.id.widget_place, placeLine)
            views.setTextViewText(R.id.widget_updated,
                "Updated ${SimpleDateFormat("HH:mm z", Locale.getDefault()).format(Date(now))}")
            views.setOnClickPendingIntent(R.id.widget_root, destination(context, id, "open", null))
            views.setOnClickPendingIntent(R.id.widget_configure, destination(context, id, "configure", null))
            views.setOnClickPendingIntent(R.id.widget_place, destination(context, id,
                if (place == null) "configure" else "place", place?.id))
            if (!expanded) return views
            val rendered = snapshot(context, now, place, size)
            if (rendered != null) views.setImageViewBitmap(R.id.widget_globe, rendered)
            else views.setImageViewResource(R.id.widget_globe, R.mipmap.ic_launcher)
            val event = brief.event
            views.setTextViewText(R.id.widget_event,
                if (rendered == null) "Illustration · ${brief.eventMessage}"
                else event?.let { "${it.type.name.lowercase().replaceFirstChar { c -> c.uppercase() }} · ${it.title}" }
                    ?: brief.prompt)
            views.setOnClickPendingIntent(R.id.widget_event,
                destination(context, id, if (event == null) "journey" else "event", event?.id))
            return views
        }

        /** Cached per hour, place and pixel size; RemoteViews copies the bitmap, so eviction may recycle. */
        private fun snapshot(context: Context, now: Long, place: SavedPlace?, size: WidgetSize): Bitmap? {
            val (w, h) = size.globePixels(context)
            val key = "${now / 3_600_000L}:${place?.id ?: "none"}:${w}x$h"
            bitmaps[key]?.takeIf { !it.isRecycled }?.let { return it }
            return try {
                WidgetSnapshotRenderer.render(context, place, w, h).also {
                    bitmaps[key] = it
                    while (bitmaps.size > MAX_CACHED_BITMAPS) {
                        val eldest = bitmaps.keys.first()
                        bitmaps.remove(eldest)?.recycle()
                    }
                }
            } catch (e: Exception) {
                Log.w("EarthTodayWidget", "Offscreen globe unavailable; using illustration", e)
                null
            }
        }

        private fun destination(context: Context, id: Int, action: String, value: String?): PendingIntent {
            val intent = Intent(context, if (action == "configure") EarthWidgetConfigActivity::class.java else MainActivity::class.java)
                .setData(Uri.parse("pbd://widget/$id/$action/${Uri.encode(value ?: "")}"))
                .putExtra("widget_action", action).putExtra("widget_value", value)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(context, id * 17 + action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
