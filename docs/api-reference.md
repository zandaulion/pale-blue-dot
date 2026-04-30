# API Reference

All classes live under `com.globe.app` unless otherwise noted.

---

## MainActivity

**File:** `MainActivity.kt`  
**Extends:** `AppCompatActivity`

Entry point. Manages:
- `GlobeSurfaceView` lifecycle (pause/resume GL rendering)
- UI overlays: status label, time scrubber, legend sheet, eclipse alerts, music button
- Runtime location permission request
- `MediaPlayer` for ambient audio

### Key Methods

| Method | Description |
|--------|-------------|
| `onCreate` | Inflates layout, wires scrubber listener, requests location permission |
| `onResume / onPause` | Delegates to `GlobeSurfaceView.onResume/onPause` |
| `onRequestPermissionsResult` | Handles location permission grant; passes coordinates to renderer |
| `showEclipseAlert(type)` | Called by `EclipseDetector` listener; posts alert to UI thread |

---

## GlobeSurfaceView

**File:** `GlobeSurfaceView.kt`  
**Extends:** `GLSurfaceView`

Owns touch event processing and creates `GlobeRenderer`.

### Touch Handling

| Gesture | Handler |
|---------|---------|
| Single-finger move | `onScroll` → `OrbitCamera.rotate(dx, dy)` |
| Two-finger pinch | `ScaleGestureDetector` → `OrbitCamera.zoom(scale)` |
| Release | Stores final velocity for momentum in `OrbitCamera` |

---

## GlobeRenderer

**File:** `GlobeRenderer.kt`  
**Implements:** `GLSurfaceView.Renderer`

Orchestrates the entire render pipeline. Holds references to all sub-renderers and calls them in draw order.

### Lifecycle

| Method | Actions |
|--------|---------|
| `onSurfaceCreated` | Creates all sub-renderers, starts async data fetches |
| `onSurfaceChanged` | Updates viewport, recomputes projection matrix |
| `onDrawFrame` | Clears, applies camera, calls each sub-renderer in order, checks eclipse |

### Sub-renderer References

`starsRenderer`, `constellationRenderer`, `sunRenderer`, `moonRenderer`, `earthRenderer`, `locationPinRenderer`, `earthEventsRenderer`, `issOrbitRenderer`, `indicatorRenderer`

---

## TimeProvider

**File:** `TimeProvider.kt`  
**Pattern:** Singleton object

Central clock. All astronomical calculators read from this rather than `System.currentTimeMillis()` so the time scrubber affects all computations uniformly.

| Property / Method | Type | Description |
|-------------------|------|-------------|
| `offsetMillis` | `Long` (volatile) | Offset added to real time; set by scrubber |
| `now()` | `Long` | `System.currentTimeMillis() + offsetMillis` |
| `reset()` | `Unit` | Sets `offsetMillis = 0` |

---

## OrbitCamera

**File:** `camera/OrbitCamera.kt`

Spherical-coordinate camera. All fields are `@Volatile` for cross-thread access.

| Property | Type | Range | Default |
|----------|------|-------|---------|
| `azimuth` | Float (radians) | [0, 2π] | 0 |
| `elevation` | Float (radians) | [−π/2, π/2] | 0.4 |
| `distance` | Float (Earth radii) | [1.15, 20.0] | 3.0 |
| `velocityAz` | Float | — | momentum storage |
| `velocityEl` | Float | — | momentum storage |

| Method | Description |
|--------|-------------|
| `rotate(dx, dy)` | Adjusts azimuth/elevation; stores velocity for momentum |
| `zoom(factor)` | Multiplies distance by `1/factor`, clamped |
| `applyMomentum()` | Called each frame; multiplies velocity by friction (0.985) |
| `getViewMatrix()` | Returns `FloatArray(16)` view matrix |

---

## EarthModel

**File:** `earth/EarthModel.kt`

Generates and GPU-uploads the Earth UV-sphere mesh.

| Method | Description |
|--------|-------------|
| `create(gl)` | Generates vertex/index arrays, uploads VBO/EBO, returns VAO handle |
| `draw()` | `glBindVertexArray` + `glDrawElements(GL_TRIANGLES)` |
| `destroy()` | Deletes VAO/VBO/EBO |

---

## EarthShader

**File:** `earth/EarthShader.kt`

Holds GLSL source (as `const val` strings) and the compiled program.

| Method | Description |
|--------|-------------|
| `compile()` | Compiles + links vertex and fragment shaders; caches all uniform locations |
| `use()` | `glUseProgram` |
| `setUniforms(...)` | Uploads MVP matrix, sun direction, time, cloud enabled flag, etc. |

**Uniform names:** `uMVP`, `uNormalMatrix`, `uSunDir`, `uTime`, `uCloudEnabled`, `uDayTex`, `uNightTex`, `uCloudTex`

---

## EarthRenderer

**File:** `earth/EarthRenderer.kt`

Ties together `EarthModel`, `EarthShader`, and three textures (day, night, cloud).

| Method | Description |
|--------|-------------|
| `init(context)` | Loads `earth_day.jpg` and `earth_night.jpg` from `drawable-nodpi`; creates procedural cloud texture |
| `setCloudBitmap(bmp)` | Uploads real cloud texture when available |
| `draw(camera, sunPos, time)` | Binds textures, sets uniforms, calls `EarthModel.draw()` |
| `toggleClouds()` | Flips `cloudEnabled` boolean |

---

## SunPosition

**File:** `earth/SunPosition.kt`  
**Pattern:** Singleton object

| Method | Description |
|--------|-------------|
| `getEcef(timeMillis)` | Returns `FloatArray(3)` normalized ECEF direction to Sun; result cached for 10 min |

---

## MoonPosition

**File:** `moon/MoonPosition.kt`  
**Pattern:** Singleton object

| Method | Description |
|--------|-------------|
| `getEcef(timeMillis)` | Returns `FloatArray(3)` normalized ECEF direction to Moon; result cached for 10 min |

---

## CloudMapProvider

**File:** `earth/CloudMapProvider.kt`

| Property / Method | Description |
|-------------------|-------------|
| `atomicBitmap` | `AtomicReference<Bitmap?>` — set when download completes |
| `fetchAsync(scope, context)` | Launches coroutine; downloads/loads cache; sets `atomicBitmap` |

---

## StarsModel

**File:** `stars/StarsModel.kt`

Procedurally generates 16,000 stars.

| Method | Description |
|--------|-------------|
| `generate()` | Returns `FloatArray` of (x, y, z, r, g, b, size, seed) per star |

Star distribution: 30% clustered in a band simulating the Milky Way, 70% uniform sphere. Sizes follow a power-law distribution. Colors map to spectral classes O/B/A/F/G/K/M.

---

## ConstellationRenderer

**File:** `stars/ConstellationRenderer.kt`

Hardcodes 15 constellations with J2000 RA/Dec positions. Renders as `GL_LINES` (stick figures) + `GL_POINTS` (star dots).

| Method | Description |
|--------|-------------|
| `init()` | Converts all RA/Dec to unit-sphere Cartesian; uploads VBO |
| `draw(viewMatrix, projMatrix)` | Draws lines and points with depth off, additive blend |

---

## ISSOrbitRenderer

**File:** `iss/ISSOrbitRenderer.kt`

| Method | Description |
|--------|-------------|
| `init()` | Allocates ribbon VBO |
| `draw(camera, timeMillis)` | Recomputes ribbon and position each frame, draws ribbon + marker |

---

## EarthEventsProvider

**File:** `events/EarthEventsProvider.kt`

| Property / Method | Description |
|-------------------|-------------|
| `atomicEvents` | `AtomicReference<List<EarthEvent>>` |
| `fetchAsync(scope)` | Launches two coroutines (USGS + EONET); merges results into `atomicEvents` |

**EarthEvent data class:**
```kotlin
data class EarthEvent(
    val lat: Float,
    val lon: Float,
    val type: EventType,   // EARTHQUAKE or VOLCANO
    val magnitude: Float   // 0.0 for volcanoes
)
```

---

## EclipseDetector

**File:** `eclipse/EclipseDetector.kt`

| Method | Description |
|--------|-------------|
| `check(sunEcef, moonEcef, listener)` | Computes `dot(sunDir, moonDir)`; calls `listener.onEclipse(type)` if threshold exceeded |

**Thresholds:**

| Condition | Dot product |
|-----------|-------------|
| Solar eclipse | > 0.9998 |
| Lunar eclipse | < −0.9994 |

---

## LocationPinRenderer

**File:** `location/LocationPinRenderer.kt`

| Method | Description |
|--------|-------------|
| `setLocation(lat, lon)` | Updates ECEF position from GPS coordinates |
| `draw(camera)` | Draws cyan point + expanding ring animations at the pin location |

---

## IndicatorRenderer

**File:** `indicators/IndicatorRenderer.kt`

| Method | Description |
|--------|-------------|
| `draw(sunEcef, moonEcef, viewMatrix, projMatrix, viewport)` | Projects sun/moon positions; if off-screen, draws edge arrow; if on-screen, draws small dot |
