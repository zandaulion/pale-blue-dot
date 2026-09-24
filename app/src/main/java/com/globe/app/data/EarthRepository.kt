package com.globe.app.data

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.util.Log
import com.globe.app.earth.CloudMapProvider
import com.globe.app.events.EarthEventsProvider
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.globe.app.widget.EarthTodayWidget

/** Application-owned, bounded fetch and cache for every foreground scene consumer. */
class EarthRepository private constructor(context: Context) {
    enum class State { NOT_LOADED, LOADING, READY, STALE, UNAVAILABLE }
    data class Feed(
        val state: State = State.NOT_LOADED,
        val events: List<EarthEventsProvider.Event> = emptyList(),
        val fetchedAtMs: Long? = null
    )
    data class Snapshot(val feeds: Map<EarthEventsProvider.Event.Type, Feed>) {
        val events: List<EarthEventsProvider.Event>
            get() = feeds.values.flatMap { it.events }
    }

    private val appContext = context.applicationContext
    private val provider = EarthEventsProvider()
    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val observers = CopyOnWriteArraySet<(Snapshot) -> Unit>()
    private val cloudObservers = CopyOnWriteArraySet<(CloudMapProvider.Result?, State) -> Unit>()
    private val lock = Any()
    private val feeds = EarthEventsProvider.Event.Type.values().associateWith { Feed() }.toMutableMap()
    private val pending = mutableSetOf<EarthEventsProvider.Event.Type>()
    private var cacheLoaded = false
    private val cacheReady = CountDownLatch(1)
    private var queuedRefresh = false
    private var queuedManual = false
    private var cloudPending = false
    private var cloudFetchedAt = 0L
    private var lastManualAt = 0L
    @Volatile var cloudState: State = State.NOT_LOADED
        private set
    private var cloudResult: CloudMapProvider.Result? = null

    init {
        executor.execute {
            for (type in EarthEventsProvider.Event.Type.values()) {
                val cached = readCache(type) ?: continue
                synchronized(lock) { feeds[type] = cached }
                publish()
            }
            val requested: Boolean
            val manual: Boolean
            synchronized(lock) {
                cacheLoaded = true
                cacheReady.countDown()
                requested = queuedRefresh
                manual = queuedManual
                queuedRefresh = false
                queuedManual = false
            }
            if (requested) refresh(manual)
        }
    }

    fun snapshot(): Snapshot = synchronized(lock) { Snapshot(feeds.toMap()) }

    fun awaitCache(timeoutMs: Long): Boolean = cacheReady.await(timeoutMs, TimeUnit.MILLISECONDS)

    fun observe(observer: (Snapshot) -> Unit) {
        observers.add(observer)
        main.post { observer(snapshot()) }
    }

    fun removeObserver(observer: (Snapshot) -> Unit) { observers.remove(observer) }

    /** Each caller receives its own bitmap and transfers it to its GL upload queue. */
    fun copyCloudResult(): CloudMapProvider.Result? = synchronized(lock) {
        cloudResult?.let { result ->
            result.copy(bitmap = result.bitmap.copy(result.bitmap.config ?: Bitmap.Config.ARGB_8888, false))
        }
    }

    fun observeClouds(observer: (CloudMapProvider.Result?, State) -> Unit) {
        cloudObservers.add(observer)
        main.post { observer(copyCloudResult(), cloudState) }
    }

    fun removeCloudObserver(observer: (CloudMapProvider.Result?, State) -> Unit) {
        cloudObservers.remove(observer)
    }

    fun requestClouds() = refreshClouds(false, System.currentTimeMillis())

    fun refresh(manual: Boolean = false) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (!cacheLoaded) {
                queuedRefresh = true
                queuedManual = queuedManual || manual
                return
            }
            if (manual) {
                if (now - lastManualAt < 15_000L) return
                lastManualAt = now
            }
        }
        for (type in EarthEventsProvider.Event.Type.values()) {
            val shouldFetch = synchronized(lock) {
                val feed = feeds.getValue(type)
                if (type in pending || (!manual && feed.fetchedAtMs != null && now - feed.fetchedAtMs < 30 * 60_000L)) false
                else {
                    pending.add(type)
                    feeds[type] = feed.copy(state = State.LOADING)
                    true
                }
            }
            if (!shouldFetch) continue
            publish()
            executor.execute {
                try {
                    val raw = provider.fetchRaw(type)
                    val events = provider.parse(type, raw)
                    val fetched = System.currentTimeMillis()
                    writeCache(type, raw, fetched)
                    synchronized(lock) {
                        feeds[type] = Feed(State.READY, events, fetched)
                        pending.remove(type)
                    }
                } catch (error: Exception) {
                    Log.w("EarthRepository", "Refresh failed for $type", error)
                    synchronized(lock) {
                        val previous = feeds.getValue(type)
                        feeds[type] = previous.copy(
                            state = if (previous.fetchedAtMs != null) State.STALE else State.UNAVAILABLE
                        )
                        pending.remove(type)
                    }
                }
                publish()
            }
        }
        refreshClouds(manual, now)
    }

    private fun refreshClouds(manual: Boolean, now: Long) {
        synchronized(lock) {
            if (cloudPending || (!manual && cloudFetchedAt > 0 && now - cloudFetchedAt < 6 * 60 * 60_000L)) return
            cloudPending = true
            cloudState = State.LOADING
        }
        executor.execute {
            val result = CloudMapProvider(appContext).fetch()
            synchronized(lock) {
                cloudPending = false
                if (result == null) {
                    cloudState = if (cloudResult == null) State.UNAVAILABLE else State.STALE
                } else {
                    cloudResult?.bitmap?.recycle()
                    cloudResult = result
                    cloudFetchedAt = System.currentTimeMillis()
                    cloudState = if (result.stale) State.STALE else State.READY
                }
            }
            main.post {
                cloudObservers.forEach { observer -> observer(copyCloudResult(), cloudState) }
            }
        }
    }

    private fun publish() {
        val current = snapshot()
        main.post { observers.forEach { it(current) } }
        EarthTodayWidget.requestUpdate(appContext)
    }

    private fun cacheFile(type: EarthEventsProvider.Event.Type) =
        AtomicFile(File(appContext.filesDir, "events_v1_${type.name.lowercase()}.json"))

    private fun readCache(type: EarthEventsProvider.Event.Type): Feed? {
      return try {
        val bytes = cacheFile(type).openRead().use { it.readBytes() }
        if (bytes.size > 8 * 1024 * 1024) return null
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        if (root.getInt("schema") != 1) return null
        val fetched = root.getLong("fetchedAt")
        if (fetched <= 0L || fetched > System.currentTimeMillis() + 60_000L) return null
        val events = provider.parse(type, root.getString("payload"))
        val state = if (System.currentTimeMillis() - fetched < 30 * 60_000L) State.READY else State.STALE
        Feed(state, events, fetched)
      } catch (_: Exception) { null }
    }

    private fun writeCache(type: EarthEventsProvider.Event.Type, raw: String, fetched: Long) {
        val file = cacheFile(type)
        var stream: java.io.FileOutputStream? = null
        try {
            val body = JSONObject().put("schema", 1).put("fetchedAt", fetched).put("payload", raw)
            stream = file.startWrite()
            stream.write(body.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            if (stream != null) file.failWrite(stream)
            Log.w("EarthRepository", "Cache write failed for $type", error)
        }
    }

    companion object {
        @Volatile private var instance: EarthRepository? = null
        fun get(context: Context): EarthRepository =
            instance ?: synchronized(this) {
                instance ?: EarthRepository(context).also { instance = it }
            }
    }
}
