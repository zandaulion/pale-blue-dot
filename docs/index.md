# Globe — Documentation Index

**Globe** (package `com.zandaulion.palebluedot`) is a real-time 3D Earth viewer for Android built entirely with raw OpenGL ES 3.0 and Kotlin — no game engine, no third-party rendering library.

---

## Contents

| Document | Description |
|----------|-------------|
| [Architecture](architecture.md) | Project structure, design principles, class responsibilities |
| [Features](features.md) | Full list of visual and interactive features |
| [Rendering Pipeline](rendering-pipeline.md) | Per-frame OpenGL draw order, shader details, GL state management |
| [Astronomy](astronomy.md) | Sun, Moon, ISS, and eclipse calculations |
| [Live Data](live-data.md) | Cloud, earthquake, and volcano API integrations |
| [API Reference](api-reference.md) | Every public class — constructor, key methods, thread safety |
| [Build & Release](build-release.md) | Gradle setup, signing, build variants |

---

## Quick Facts

| Property | Value |
|----------|-------|
| App ID | `com.zandaulion.palebluedot` |
| Version | 2.0 (versionCode 2) |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 35 (Android 15) |
| Rendering | OpenGL ES 3.0 (GLSL 300 es) |
| Language | Kotlin 2.0.21 |
| Build system | AGP 8.7.3 / Gradle 8.9 |
| Permissions | `INTERNET`, `ACCESS_COARSE_LOCATION` |

---

## Repository Layout

```
Globe/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/globe/app/        ← all Kotlin source
│   │   ├── MainActivity.kt
│   │   ├── GlobeRenderer.kt
│   │   ├── GlobeSurfaceView.kt
│   │   ├── TimeProvider.kt
│   │   ├── camera/
│   │   ├── earth/
│   │   ├── moon/
│   │   ├── sun/
│   │   ├── stars/
│   │   ├── iss/
│   │   ├── location/
│   │   ├── eclipse/
│   │   ├── events/
│   │   └── indicators/
│   └── res/
│       ├── drawable-nodpi/        ← earth_day.jpg, earth_night.jpg, moon.jpg
│       ├── raw/                   ← ambient_space.wav
│       └── mipmap-*/              ← launcher icons
├── docs/                          ← this documentation
├── ARCHITECTURE.md
├── README.md
├── TODO.md
├── CREDITS.md
└── build.gradle.kts
```
