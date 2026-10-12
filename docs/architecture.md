# Architecture

## Ownership and packages

All Kotlin source is under `app/src/main/java/com/globe/app/`.

| Package / class | Responsibility |
|---|---|
| `MainActivity` | Native panels, navigation, learning, preferences, app audio, lifecycle |
| `GlobeSurfaceView` | Touch/scale input and the app GL surface |
| `GlobeRenderer` | Frame clock snapshot, scene orchestration, projection/picking, resource release |
| `AppLanguage` | Locale selection, persistence, system app-language bridge, localized contexts |
| `camera/` | Orbit, momentum, fly-to, viewpoint and Reduce motion |
| `earth/` | Sphere mesh, textures/shaders, cloud extraction, solar calculation |
| `moon/` | Lunar calculation, shaded sphere, faint-phase outline and native label |
| `sun/`, `stars/`, `iss/`, `indicators/`, `eclipse/` | Sky objects, illustrative orbit, arrows, approximate alignments |
| `data/`, `events/` | Shared repository, feed parsing, Today briefing, markers and selection |
| `time/` | App exploration clock and independent real-time clocks |
| `render/` | Shared projection, texture limits, layers, plate boundaries, latitude guides |
| `places/` | Local place storage, city names/catalog, time-zone suggestions, daylight, pins |
| `kids/` | Discoveries, challenges, facts, Moon phase, parental gate, text cleanup, Talia player |
| `explore/` | Journey content/localization, scene snapshots, Field notebook migration/storage |
| `ui/` | ReadingColumn bounds prose/control width on wide windows |
| `share/` | Capture, branded image, cache files and Android share intent |
| `wallpaper/` | Settings/preview, real-time scene controller, per-engine EGL render thread |
| `widget/` | Per-widget place configuration, RemoteViews, offscreen globe snapshots |

Rendering programs and GPU buffers belong to their surface/context. The
repository holds application context and CPU data, not Activity references.
Native panels own text/layout/accessibility; OpenGL draws the scene. The source
has no active GPS/location package or permission.

## Threads and lifetime

| Thread | Responsibilities |
|---|---|
| Main/UI | Activity and native views, input, locale changes, MediaPlayer/TTS coordination, repository observers |
| App GL thread | All GL calls, frame calculation, resource uploads/releases |
| Wallpaper render thread | Each engine's EGL context and visibility-driven frame scheduling |
| Widget worker | Real-time offscreen EGL snapshot and widget update work |
| Repository workers | Two bounded workers for feed/cloud downloads and cache work |

Camera/layer snapshots cross threads through volatile fields and owned copies.
Cloud bitmaps transfer to each scene's upload queue and are recycled after
upload. The repository sends immutable snapshots through main-thread observers;
observers are removed with their consumer lifecycle.

## Data flows

### Time and drawing

```text
App time controls → AppSceneClock (offset or held/playing instant)
Wallpaper/widget → RealTimeSceneClock
SceneClock.nowMs() → one frame instant → Sun/Moon/Earth/ISS/indicators/eclipse
SceneProjection → same view/projection for drawing, ray picking, and Moon label
```

Astronomy functions accept explicit UTC milliseconds. They have no global
10-minute position cache. Today and widget briefings use real current time even
when the app explores another date.

### Observations

```text
Foreground consumers → EarthRepository.refresh / requestClouds
  → background providers → atomic disk caches + immutable per-feed state
  → UI observers → scene data / owned cloud bitmap
  → GL upload → event markers / cloud texture
```

GL surface creation only rebuilds graphics resources and reuploads available
data. Failed refresh keeps previous successful observations with stale status;
an unavailable feed is distinct from a successful empty one.
See [live data](live-data.md).

### Locale

```text
Settings or Android app-language choice → AppLanguage → resource locale
  → native panels, JourneyStrings/CityNames, wallpaper config, widget contexts
  → Talia catalog locale and device TTS locale
```

Android 13+ uses system app locales; older Android restores the AppCompat
preference. Unsupported device languages resolve to English. Stable content IDs
and user-entered text do not change with locale. See [localization](localization.md).

### Narration

```text
First-launch choice / Settings → persisted Read aloud preference
Card text → NarrationText.clean → TaliaNarrator catalog lookup
  → matching clip(s): local MediaPlayer + transient speech audio focus
  → unmatched changing text / playback failure: available device TTS
Close / off / background / focus loss → stop, no automatic restart
```

144 generated clips live in `assets/narration/<locale>/`. Development-time
ElevenLabs generation is outside runtime architecture; no SDK, key, or backend
is used by the APK. Audio tokens discard stale asynchronous callbacks. Wallpaper
and widget cannot invoke app narration. See [voiceover](voiceover.md).

### Moon visibility

`MoonGuide` derives illumination and fade from frame Sun/Moon directions.
`MoonRenderer` draws a thin expanded silhouette before the shaded surface;
Earth's depth hides it when occluded. `MoonGuideOverlay` consumes projected
frame data and places a translated native label around UI exclusion areas.
See [Moon visibility](moon-visibility.md).

## Geometry and shaders

Earth uses 64 latitude × 128 longitude segments; Moon uses 24 × 48. Vertices
interleave position3, normal3, and UV2 (32 bytes), with indexed CCW triangles.
Coordinates are +Y north, −X Greenwich, +Z 90°E. GLSL 300 es shaders hold
per-context handles and cached uniform locations; Android's Matrix API supplies
transforms. Full details: [rendering pipeline](rendering-pipeline.md).

Design rationale: [architecture decisions](../ARCHITECTURE.md). `MainActivity`
remains large and still builds many panels; the package split is not a claim that
all UI has already been extracted.

Tablet/window adaptation and its device audit are described in [tablet layout](tablet-layout.md).
