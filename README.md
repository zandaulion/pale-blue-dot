# Globe

A real-time 3D Earth viewer for Android, built with raw OpenGL ES 3.0 and Kotlin. No game engine, no third-party rendering libraries — just the Android SDK.

## Features

- **Day/night lighting** synced to the phone's clock and timezone, with a smooth terminator transition and city lights on the night side
- **Live cloud overlay** downloaded from NASA VIIRS satellite imagery via the Worldview Snapshot API, with procedural fallback when offline
- **Starfield** with 16,000 procedurally generated stars, spectral-class colors, Milky Way clustering, and per-star twinkle animation
- **Sun and Moon** positioned using simplified astronomical algorithms (~1 degree accuracy) with 10-minute caching
- **ISS orbit track** rendered as a ribbon with real-time position marker, 51.6 degree inclination, and RAAN precession
- **Location pin** showing the user's GPS position on the globe
- **Time scrubber** to move time forward/backward by up to 24 hours, animating sun, moon, ISS, and day/night in real time
- **Eclipse detection** highlights solar and lunar eclipses when the sun-earth-moon alignment is close
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
├── MainActivity.kt           # Entry point, UI overlays, location/permissions
├── GlobeRenderer.kt          # Orchestrates draw order
├── GlobeSurfaceView.kt       # Touch handling
├── TimeProvider.kt           # Central time source for time scrubber
├── camera/
│   └── OrbitCamera.kt        # Azimuth/elevation/distance orbit camera
├── earth/
│   ├── EarthModel.kt         # UV-sphere mesh generation
│   ├── EarthShader.kt        # Day/night/cloud GLSL shaders
│   ├── EarthRenderer.kt      # Earth rendering + cloud texture management
│   ├── SunPosition.kt        # Astronomical sun direction calculation
│   └── CloudMapProvider.kt   # Downloads and processes NASA VIIRS cloud imagery
├── moon/
│   ├── MoonRenderer.kt       # Moon sphere rendering with texture
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
├── location/
│   └── LocationPinRenderer.kt # GPS location pin on the globe
├── eclipse/
│   └── EclipseDetector.kt    # Sun-Earth-Moon alignment detection
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
5. **Location pin** — user's GPS position, depth tested
6. **ISS orbit** — triangle-strip ribbon + point marker, depth tested
7. **Indicators** — 2D overlay arrows pointing toward sun and moon
8. **Eclipse detection** — notifies UI of alignment state

## Textures

The app ships with three texture assets in `res/drawable-nodpi/`:

- `earth_day.jpg` — standard equirectangular daytime Earth
- `earth_night.jpg` — city lights at night
- `moon.jpg` — lunar surface

Cloud cover is downloaded from NASA VIIRS satellite imagery at startup, with clouds extracted by brightness thresholding. Falls back to procedural clouds when offline. The cloud layer can be toggled on/off by tapping the status label.

## Permissions

- `INTERNET` — downloading live cloud cover from NASA
- `ACCESS_COARSE_LOCATION` — showing the user's position on the globe (requested at runtime, optional)

## Dependencies

Only standard Android SDK libraries:

- `androidx.appcompat` — AppCompatActivity
- `androidx.core:core-ktx` — Kotlin extensions
- `android.opengl.*` — OpenGL ES 3.0

No third-party libraries for rendering, math, or image loading.

## License

This project is licensed under the [MIT License](LICENSE).

Texture assets are sourced from NASA and are in the public domain. See [CREDITS.md](CREDITS.md) for details.
