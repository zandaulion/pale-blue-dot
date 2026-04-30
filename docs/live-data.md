# Live Data Integration

## Overview

The app integrates three external data sources, all of which are free and require no API key. Every integration follows the same pattern: fetch on a background thread, store result in an `AtomicReference`, pick up on the GL thread at the next frame.

---

## Cloud Cover — NASA VIIRS

### Source
NASA Worldview Snapshot API serving VIIRS True Color imagery from the Terra/Aqua satellites.

### How It Works (`CloudMapProvider.kt`)

1. **Download** — fetches a PNG covering the full Earth in equirectangular projection.
2. **Extract clouds** — for each pixel, compute brightness and saturation. High brightness + low saturation → cloud (white/grey pixel). The result is a grayscale `Bitmap` where alpha encodes cloud density.
3. **Cache** — the extracted `Bitmap` is stored to internal storage with a timestamp. On the next app launch within 6 hours, the cached file is loaded instead of re-downloading.
4. **Upload** — the GL thread receives the `Bitmap` via `AtomicReference`, uploads it with `glTexImage2D`, and `EarthShader` blends it over the day-side.

### Fallback
If the download fails (no network, server error), a procedural noise `Bitmap` is generated in-process and used instead. The app is indistinguishable from a user perspective except for cloud accuracy.

### Refresh
The cache TTL is **6 hours**. The download is triggered once at app start and again after TTL expiry.

---

## Earthquakes — USGS GeoJSON

### Source
USGS Earthquake Hazards Program public GeoJSON feed.

```
Endpoint: https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_week.geojson
Filter:   M 4.5+ in the past 7 days
```

### How It Works (`EarthEventsProvider.kt`)

1. Fetch JSON on a background thread.
2. Parse `features[]` array: extract `geometry.coordinates[0]` (longitude), `geometry.coordinates[1]` (latitude), `properties.mag` (magnitude).
3. Return a `List<EarthEvent>` with type `EARTHQUAKE`.
4. `EarthEventsRenderer` uploads one `GL_POINTS` vertex per event; point size scales linearly with magnitude.

### Rendering
Earthquake markers pulse (alpha oscillates with `sin(time × speed + offset)`) to draw the eye. Color: **orange-red**.

---

## Volcanoes — NASA EONET

### Source
NASA Earth Observatory Natural Event Tracker (EONET) API v3.

```
Endpoint: https://eonet.gsfc.nasa.gov/api/v3/events?category=volcanoes&days=30&status=open
Filter:   active volcanic eruptions in the past 30 days
```

### How It Works (`EarthEventsProvider.kt`)

1. Fetch JSON on a background thread.
2. Parse `events[]` array: extract `geometry[0].coordinates[0]` (longitude), `geometry[0].coordinates[1]` (latitude).
3. Return a `List<EarthEvent>` with type `VOLCANO`.
4. Merged with earthquake list in `EarthEventsRenderer`.

### Rendering
Volcano markers use the same pulsing point-sprite approach. Color: **red**.

---

## Fetch Lifecycle

```
Activity.onCreate()
  └─ EarthEventsProvider.fetchAsync(coroutineScope)
       ├─ launch(Dispatchers.IO) { fetchEarthquakes() }
       └─ launch(Dispatchers.IO) { fetchVolcanoes() }
            └─ atomicEvents.set(merged list)

CloudMapProvider.fetchAsync(coroutineScope)
  └─ launch(Dispatchers.IO) { downloadOrLoadCache() }
       └─ atomicBitmap.set(bitmap)

GlobeRenderer.onDrawFrame()
  ├─ val bmp = cloudProvider.atomicBitmap.getAndSet(null) → upload texture if non-null
  └─ val events = eventsProvider.atomicEvents.get()       → render markers
```

---

## Error Handling

All network calls are wrapped in `try/catch`. On any exception:

- **Clouds**: procedural fallback texture is used.
- **Events**: empty list is used (no markers rendered).
- No crash, no dialog — silent degradation.

---

## Data Freshness Indicators

The status label in `MainActivity` shows the cloud data state:

| Label text | Meaning |
|------------|---------|
| "Clouds: Live" | Real VIIRS texture loaded |
| "Clouds: Cached" | Loaded from 6-hour cache |
| "Clouds: Procedural" | Network failed, using fallback |
| "Clouds: Off" | User toggled clouds off |
