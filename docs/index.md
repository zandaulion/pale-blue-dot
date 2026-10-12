# Pale Blue Dot — Documentation Index

Pale Blue Dot is an Android Earth and astronomy app built with Kotlin, native
Views, and OpenGL ES 3.0. Application ID: `com.zandaulion.palebluedot`; source
namespace: `com.globe.app`.

## Contents

| Document | Description |
|---|---|
| [README](../README.md) | Product overview, setup, and code map |
| [Features](features.md) | Current interface, learning, audio, and scene features |
| [Localization](localization.md) | 12 locales, Settings language switcher, translation maintenance, layout audits |
| [Globe Interactions](globe-interactions.md) | One-time day/night discoveries, deliberate holds, and gesture behavior |
| [Tablet Layout](tablet-layout.md) | Window adaptation, tablet-sized device audits, and limits |
| [Moon Visibility](moon-visibility.md) | Faint-phase outline, labels, placement, and validation |
| [Voiceover](voiceover.md) | Talia opt-in, educational narration coverage, shared scripts, credentials, and generation |
| [Architecture Decisions](../ARCHITECTURE.md) | Rendering, shared data, localization, and audio rationale |
| [Architecture](architecture.md) | Packages, ownership, threads, and data flows |
| [Rendering Pipeline](rendering-pipeline.md) | Frame order, GL state, surfaces, and Moon outline |
| [Astronomy](astronomy.md) | Sun, Moon, ISS, daylight, and approximate alignment calculations |
| [Live Data](live-data.md) | NASA/USGS feeds, cache ownership, freshness, and failures |
| [API Reference](api-reference.md) | Key implementation entry points and lifecycle requirements |
| [Build & Release](build-release.md) | Toolchain, signing, validation, and APK handling |
| [Data Credits](data-credits.md) | Offline city, time-zone, and plate-boundary provenance |
| [Credits](../CREDITS.md) | Textures, live data, music, and generated narration |
| [Privacy](../PRIVACY.md) | On-device storage, public-data downloads, sharing, and speech engines |
| [Earth Companion Guide](earth-companion-implementation-guide.md) | Original milestone specification with current follow-up requirements |
| [Earth Companion Progress](earth-companion-progress.md) | Dated implementation/test history and outstanding acceptance checks |
| [Backlog](../TODO.md) | Completed additions and follow-up ideas |
| [Store Assets](../store/README.md) | Listing draft, release history, and next-release notes |

## Current configuration

| Property | Value |
|---|---|
| Version metadata | 3.11 / versionCode 13; not published to Play |
| minSdk | 24 (Android 7.0) |
| compileSdk / targetSdk | 36 (Android 16) |
| Rendering | OpenGL ES 3.0, explicitly required by the manifest |
| Kotlin plugin | 2.2.10 |
| Build system | AGP 9.4.1 / Gradle 9.6.0 |
| Gradle daemon / bytecode | JetBrains JDK 21 / Java and Kotlin target 17 |
| Requested permission | `INTERNET` |
| Localization | 12 bundled locales, 800 translatable resources per locale |
| Narration | 1,212 bundled Talia clips; separate Android opt-in for changing facts/scores |

## Repository layout

```text
app/src/main/
├── AndroidManifest.xml
├── java/com/globe/app/
│   ├── MainActivity.kt, AppLanguage.kt
│   ├── GlobeRenderer.kt, GlobeSurfaceView.kt
│   ├── camera/, earth/, moon/, sun/, stars/, iss/, indicators/, eclipse/
│   ├── data/, events/, render/, time/
│   └── kids/, explore/, places/, share/, wallpaper/, widget/
├── assets/                    # journeys, offline datasets, narration/<locale>/
└── res/                       # values + 11 locale directories, textures, music, widgets
app/src/androidTest/           # instrumented state, rendering, and layout checks
tools/                         # localization validator, narration and asset generators
docs/                          # technical and product documentation
store/                         # listing, artwork, released notes, next-release draft
```

The source and Gradle files are authoritative. Dated milestone records describe
what was tested at that time, rather than promising that every acceptance item
has been verified on every device.
