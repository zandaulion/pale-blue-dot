# Implementation reference

Key entry points below live under `app/src/main/java/com/globe/app/` unless
noted. This is an implementation map, not an exhaustive or versioned public SDK.
See [architecture](architecture.md) for ownership and threads.

## Activity, scene, and language

| Class / file | Entry points and requirements |
|---|---|
| `MainActivity.kt` | `onCreate` restores preferences, locale, panels, MediaPlayer/TTS, and first-launch choice; `onResume/onPause` manage scene and stop narration; `onDestroy` releases audio and observers. No location permission callback is used. |
| `GlobeSurfaceView.kt` | Owns `camera`, `sceneClock`, and `renderer`; `onTouchEvent` handles tap/drag/pinch and passes taps to the Activity. |
| `GlobeRenderer.kt` | `onSurfaceCreated` rebuilds GL resources and reuploads cached scene data without fetching; `onSurfaceChanged` updates projection; `onDrawFrame` snapshots time and draws; `release()` requires the owning GL context/thread. |
| `GlobeRenderer.kt` | `setAllEvents`, `setPlaces`, `applyLayers`, `setCloudResult`, `setFraming`, `pick`, and `projectWorldToScreen` provide scene/picking integration. `lastFrameTimeMs` and `moonGuideFrame` expose current frame information. |
| `AppLanguage.kt` | `tags`/`nativeNames` define supported locales; `selected`, `restore`, and `select` manage the choice; `context` localizes independent consumers; `locale` resolves a supported language or English fallback. |
| `time/SceneClock.kt` | `SceneClock.nowMs()`, `RealTimeSceneClock`, and `AppSceneClock` replace the earlier global TimeProvider. App clock exposes `offsetMs`, `enter`, `seek`, `setPlaying`, `exit`, `isExploring`, and `isPlaying`. |
| `camera/OrbitCamera.kt` | `rotate`, `startDrag`, `endDrag`, `update`, `zoom`, `flyTo`, `faceSky`, `restore`, `getViewMatrix`, and `getPosition`. Distance clamps to 1.15–20 Earth radii. |
| `ui/ReadingColumn.kt` | LinearLayout with an optional maximum width in dp; preserves native font scaling and parent height/scroll behavior. |
| `render/SceneProjection.kt` | `perspective(width, height, frameX, frameY)` is shared by drawing and picking, with framing offsets. |

Selecting a locale can recreate the Activity. Recreate locale-specific narration
and text contexts accordingly; stable saved IDs/progress must survive the change.

## Graphics and astronomy

| File | Key API |
|---|---|
| `earth/EarthModel.kt` | Constructor segment counts; `uploadToGpu()` returns `GpuBuffers` (VAO/VBO/IBO/index count) with `release()`. |
| `earth/EarthShader.kt` | `create`, `use`, `release`; per-instance GL program and resolved uniforms. |
| `earth/EarthRenderer.kt` | `onSurfaceCreated`, `setMatrices`, `setCloudBitmap`, `onDrawFrame(timeMs)`, `release`; `CloudMode` is `OFF`, `GENERATED`, `LIVE`. |
| `earth/SunPosition.kt`, `moon/MoonPosition.kt` | `calculate(timeMs): FloatArray` gives a model-coordinate unit vector for the supplied UTC instant; no global position cache. |
| `moon/MoonGuide.kt` | `illumination(moon, sun)`, `strength(illumination)`, `project(...)`; constants share the visual lunar distance/radius; nullable `Frame` includes screen bounds, Earth bounds, illumination, and fade strength. |
| `moon/MoonRenderer.kt` | `init`, `onSurfaceChanged`, `draw(viewMatrix, projectionMatrix, timeMs)`, `destroy`; renders outline then shaded sphere. |
| `moon/MoonGuideOverlay.kt` | Native localized label consumes current projected frame, respects visibility/occlusion and control exclusion areas. |
| `stars/StarsModel.kt` | `generateVertexData()` generates 16,000 stars with position/color/size/twinkle seed. |
| `stars/ConstellationRenderer.kt` | `init`, `draw(viewMatrix, projMatrix)`, `destroy`; 15 catalog-based constellations. |
| `iss/ISSOrbitRenderer.kt` | `init`, `draw(viewMatrix, projectionMatrix, timeMs)`, `currentWorldPosition(timeMs)`, `destroy`; illustrative orbit and marker. |
| `indicators/IndicatorRenderer.kt` | `init`, `onSurfaceChanged`, `draw(viewMatrix, projectionMatrix, timeMs)`, `destroy`; screen-space sky-body targets. |
| `eclipse/EclipseDetector.kt` | `detect(timeMs): EclipseState`; approximate solar/lunar and near-alignment states. |
| `render/TextureLoader.kt` | `load(context, resourceId, maxWidth, wrapS)` bounds decoded textures; `TextureQuality` selects surface limits. |

All GPU creation/draw/destruction happens with the owning GL context current.
See [rendering](rendering-pipeline.md), [astronomy](astronomy.md), and
[Moon guide](moon-visibility.md) for behavior and approximation limits.

## Observations and local state

| File | Key API / role |
|---|---|
| `data/EarthRepository.kt` | `get(context)`, `snapshot`, `awaitCache`, `observe/removeObserver`, `refresh(manual)`, `requestClouds`, `observeClouds/removeCloudObserver`, `copyCloudResult`. Shared app-context workers, caches and freshness. |
| `events/EarthEventsProvider.kt` | `fetchRaw(type)` downloads a bounded response; `parse(type, json)` preserves valid observations while skipping malformed individual records. |
| `earth/CloudMapProvider.kt` | `fetch(): Result?`; result owns bitmap, imagery-date timestamp and stale flag. Six-hour processed-image cache. |
| `events/EarthEventsRenderer.kt` | Scene marker data used by rendering and selection; filtered through renderer layer settings. |
| `places/PlaceStore.kt` | Local saved places with stable IDs, coordinates, optional zone, primary choice, rename/remove/restore. |
| `places/Daylight.kt` | `at(place, nowMs): Summary`; ordinary, polar-day, polar-night, and zone-unset states. |
| `data/TodayBriefing.kt` | Real-time briefing shared by Today/widget using a selected place and attributed cached event. |
| `explore/Journeys.kt` | `JourneyContent`, `Journey`, `JourneyStep`, `JourneyStore`, and `JourneySceneSnapshot`: localized lessons, active step, completion and scene restoration. |
| `explore/FieldNotebook.kt` | Idempotent legacy discovery migration and bounded saved observations with source/time/facts. |
| `kids/MoonPhase.kt` | `current(nowMs)` estimates real-time phase/illumination for briefings; phase names resolve from resources. |

`EarthEventsProvider.Event` has `id`, `lat`, `lon`, `title`, `type`,
`observedAtMs`, `updatedAtMs`, `source`, `sourceUrl`, nullable `magnitude` and
`depthKm`, `geometry`, and `markerSize`. Categories are `EARTHQUAKE`, `VOLCANO`,
`WILDFIRE`, and `STORM`. Scientific values are not inferred from marker size.
Repository feed states are `NOT_LOADED`, `LOADING`, `READY`, `STALE`, and
`UNAVAILABLE`. See [live data](live-data.md).

## Narration

| File | Key API / role |
|---|---|
| `kids/NarrationText.kt` | `clean(text)` normalizes Android escapes/whitespace and removes emoji/symbols without dropping combining marks. |
| `kids/TaliaNarrator.kt` | Constructor takes locale-aware `Context` and `onPlaybackFailure(String)` callback. `clipsFor(text)` resolves one or composed recordings; `speak(text)` handles lookup, focus, async playback; `stop()` invalidates pending callbacks and releases playback/focus. |
| `MainActivity.kt` | Private `setNarrationEnabled`, `speak`, `speakWithDevice`, and `stopNarration` coordinate the stored choice, recording lookup and device TTS. |
| `assets/narration/<locale>/index.json` | Cleaned spoken text → MP3 basename, 12 entries per supported locale. |
| `tools/generate_narration.py` | Development-only generator; `--prepare-only` makes no API requests; `--key-file` overrides the outside-repository credential path. |

`TaliaNarrator.speak` returns false for unmatched text, allowing device TTS.
It returns true for a matched recording even when audio focus is denied, so the
caller cannot bypass denial by speaking through TTS. Playback errors invoke the
fallback callback; ordinary stop/focus loss does not. See [voiceover](voiceover.md)
for exact narrated surfaces and lifecycle behavior.

## Home screen and sharing

`wallpaper/EarthWallpaperService.kt` owns independent engines, render threads,
and EGL contexts; `WallpaperSceneController` applies real-time preferences.
`WallpaperConfigActivity` hosts settings/preview; `WallpaperSettings` persists
presets, motion, chosen viewpoint and framing separately from app state.

`widget/EarthTodayWidget.kt` handles updates, options, deletion and clock/locale
broadcasts. `EarthWidgetConfigActivity`, `WidgetSize`, and `WidgetSnapshotRenderer`
provide per-instance place choices, size selection, and independent offscreen
rendering. Widget contexts use the app locale. Neither surface starts narration.

`share/FrameCapturer`, `ShareWatermark`, and `ShareManager` produce/cache branded
captures and open the Android share sheet after `kids/ParentalGate`.

## Tests

Instrumented tests live under `app/src/androidTest/java/com/globe/app/` and cover
provider parsing, companion state, daylight/widget snapshots, wallpaper setup,
localization, Talia, and Moon geometry. Language/Moon visual audits are opt-in.
See [build and test commands](build-release.md) and the feature-specific audit
docs; current results and outstanding checks are in
[progress](earth-companion-progress.md).
