# Architecture

## Design Principles

1. **One class, one concern** — every renderer, model, and shader class owns exactly one visual element.
2. **GlobeRenderer orchestrates** — it calls each sub-renderer in order but does not contain draw code itself.
3. **Shaders as Kotlin string constants** — avoids runtime asset loading for a small shader count.
4. **GL state created in `onSurfaceCreated`** — handles EGL context loss correctly on resume.
5. **Procedural placeholders** — the app is fully functional without any downloaded assets; real textures and live data enhance but are never required.
6. **Thread safety via `@Volatile`** — camera state is written from the UI thread and read from the GL thread; atomic references pass bitmaps between threads.
7. **Graceful degradation** — network failures fall back to procedural clouds/empty event lists silently.

---

## Package Map

```
com.globe.app
├── MainActivity           — activity lifecycle, UI overlays, permissions
├── GlobeSurfaceView       — GLSurfaceView, touch input
├── GlobeRenderer          — top-level render orchestrator
├── TimeProvider           — central scrubbable clock
│
├── camera/
│   └── OrbitCamera        — spherical-coordinate orbit camera
│
├── earth/
│   ├── EarthModel         — UV-sphere mesh (GPU upload)
│   ├── EarthShader        — GLSL source + uniform binding
│   ├── EarthRenderer      — mesh + shader + texture orchestration
│   ├── SunPosition        — low-precision solar position
│   └── CloudMapProvider   — NASA VIIRS cloud download + extraction
│
├── moon/
│   ├── MoonPosition       — lunar position calculation
│   ├── MoonRenderer       — moon sphere rendering
│   └── MoonShader         — moon GLSL
│
├── sun/
│   ├── SunRenderer        — billboard sun with glow
│   └── SunShader          — sun GLSL
│
├── stars/
│   ├── StarsModel         — 16,000 procedural star positions
│   ├── StarsShader        — point-sprite twinkle shader
│   ├── StarsRenderer      — GL_POINTS draw call
│   └── ConstellationRenderer — 15 constellations, J2000 positions
│
├── iss/
│   └── ISSOrbitRenderer   — ISS ribbon orbit + position marker
│
├── location/
│   └── LocationPinRenderer — GPS location dot on globe
│
├── eclipse/
│   └── EclipseDetector    — solar/lunar alignment detection
│
├── events/
│   ├── EarthEventsProvider — USGS earthquakes + NASA EONET volcanoes
│   └── EarthEventsRenderer — pulsing point-sprite markers
│
└── indicators/
    ├── IndicatorRenderer   — 2D overlay arrows for sun & moon
    └── IndicatorShader     — indicator GLSL
```

---

## Thread Model

| Thread | Responsibilities |
|--------|-----------------|
| **Main/UI thread** | Activity lifecycle, touch events, overlay updates, location callbacks |
| **GL thread** (`GLSurfaceView` internal) | All OpenGL calls — `onSurfaceCreated`, `onSurfaceChanged`, `onDrawFrame` |
| **Background coroutines / worker threads** | Network downloads (clouds, earthquakes, volcanoes) |

Camera azimuth/elevation/distance fields are `@Volatile`; bitmaps are passed to the GL thread via `AtomicReference`.

---

## Key Data Flows

### Time
```
User drags scrubber → MainActivity updates TimeProvider.offsetMillis
TimeProvider.now()  → used by SunPosition, MoonPosition, ISSOrbitRenderer, EarthShader
```

### Camera
```
Touch event (GlobeSurfaceView) → OrbitCamera.{azimuth,elevation,distance}
onDrawFrame → OrbitCamera.getViewMatrix() → all renderer uniforms
```

### Cloud Texture
```
App start → CloudMapProvider.fetchAsync()
  → download VIIRS PNG → extract alpha by brightness/saturation
  → AtomicReference<Bitmap> → EarthRenderer picks up on next frame → glTexImage2D
Fallback: procedural noise Bitmap if download fails
```

### Live Events
```
App start → EarthEventsProvider.fetchAsync()
  → USGS JSON + NASA EONET JSON → List<EarthEvent> (lat/lon/type/magnitude)
  → EarthEventsRenderer uploads GL_POINTS per event
```

---

## Mesh Format

The Earth and Moon share the same UV-sphere generation strategy (different segment counts).

```
Per vertex (8 floats × 4 bytes = 32 bytes):
  position:  vec3  (x, y, z)
  normal:    vec3  (same as position on unit sphere)
  texCoord:  vec2  (u, v)

Earth segments: 64 latitude × 128 longitude
Indices: CCW winding, GL_TRIANGLES via EBO

Coordinate convention:
  +Y = North Pole
  -X = prime meridian (Greenwich)
  +Z = 90°E longitude
```

---

## Shader Architecture

Each visual element has its own shader class holding the GLSL source as `const val` strings and a compiled program handle. Uniform locations are cached at surface-creation time.

```
EarthShader
  vert: position → clip space via MVP; pass normal, texCoord, worldPos to frag
  frag: day/night blend (dot(normal, sunDir))
        + cloud overlay (cloud alpha × cloud tint)
        + atmosphere rim (Fresnel)
        + aurora animation (time-based sine waves near poles)
        + terminator smoothstep
```

All shaders use `#version 300 es` with `in`/`out` varyings.
