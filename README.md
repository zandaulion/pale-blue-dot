# Globe

A real-time 3D Earth viewer for Android, built with raw OpenGL ES 3.0 and Kotlin. No game engine, no third-party rendering libraries — just the Android SDK.

## Features

- **Day/night lighting** synced to the phone's clock and timezone, with a smooth terminator transition and city lights on the night side
- **Procedural cloud layer** with tileable noise, slow drift animation, and latitude-based fade at the poles
- **Starfield** with 16,000 procedurally generated stars, spectral-class colors, Milky Way clustering, and per-star twinkle animation
- **Sun and Moon** positioned using simplified astronomical algorithms (~1 degree accuracy) with 10-minute caching
- **ISS orbit track** rendered as a ribbon with real-time position marker, 51.6 degree inclination, and RAAN precession
- **Orbit camera** with touch-to-rotate, pinch-to-zoom, and momentum/inertia
- **Fresnel atmosphere rim glow** that's stronger on the dayside
- **Sun and Moon indicator arrows** as 2D overlay

## Screenshots

*Coming soon*

## Build

Open in Android Studio and run, or build from the command line:

```bash
./gradlew assembleDebug
```

### Requirements

- Android Studio Ladybug or later
- JDK 17
- Android SDK 35

### Target Devices

| Setting | Value | Notes |
|---------|-------|-------|
| minSdk | 24 | Android 7.0 — guarantees OpenGL ES 3.0 |
| targetSdk | 35 | Android 15 |

## Project Structure

```
app/src/main/java/com/globe/app/
├── MainActivity.kt           # Entry point
├── GlobeRenderer.kt          # Orchestrates draw order
├── GlobeSurfaceView.kt       # Touch handling
├── camera/
│   └── OrbitCamera.kt        # Azimuth/elevation/distance orbit camera
├── earth/
│   ├── EarthModel.kt         # UV-sphere mesh generation
│   ├── EarthShader.kt        # Day/night/cloud GLSL shaders
│   ├── EarthRenderer.kt      # Earth rendering + procedural textures
│   └── SunPosition.kt        # Astronomical sun direction calculation
├── moon/
│   ├── MoonRenderer.kt       # Moon billboard rendering
│   ├── MoonShader.kt         # Moon GLSL shaders
│   └── MoonPosition.kt       # Astronomical moon direction calculation
├── sun/
│   ├── SunRenderer.kt        # Sun billboard with glow
│   └── SunShader.kt          # Sun GLSL shaders
├── stars/
│   ├── StarsModel.kt         # Procedural star vertex data
│   ├── StarsShader.kt        # Point-sprite shader with twinkle
│   └── StarsRenderer.kt      # Star draw calls + GL state
├── iss/
│   └── ISSOrbitRenderer.kt   # ISS orbit ribbon + position marker
└── indicators/
    ├── IndicatorRenderer.kt   # 2D arrow overlays
    └── IndicatorShader.kt     # Indicator GLSL shaders
```

## Render Pipeline

Each frame draws in this order:

1. **Stars** — depth off, additive blend (infinite background)
2. **Sun** — billboard with glow, no depth write
3. **Moon** — depth tested, drawn behind Earth
4. **Earth** — depth on, backface culled, day/night/cloud shaders
5. **ISS orbit** — triangle-strip ribbon + point marker, depth tested
6. **Indicators** — 2D overlay arrows pointing toward sun and moon

## Textures

The app ships with three texture assets in `res/drawable-nodpi/`:

- `earth_day.jpg` — standard equirectangular daytime Earth
- `earth_night.jpg` — city lights at night
- `moon.jpg` — lunar surface

Clouds are generated procedurally at startup. If texture assets are missing, procedural placeholders are used.

## Dependencies

Only standard Android SDK libraries:

- `androidx.appcompat` — AppCompatActivity
- `androidx.core:core-ktx` — Kotlin extensions
- `android.opengl.*` — OpenGL ES 3.0

No third-party libraries for rendering, math, or image loading.

## License

All rights reserved.
