# Pale Blue Dot — Architecture Decisions

This record describes the current design. Package details are in
[architecture](docs/architecture.md); build settings are in
[build and release](docs/build-release.md).

## Kotlin, native Views, and OpenGL ES 3.0

Kotlin coordinates Android lifecycle, programmatic native Views, persistence,
and CPU calculations. Raw OpenGL ES 3.0 gives direct control over the relatively
small Earth/sky scene without a game engine. The manifest requires GLES 3.0;
minSdk 24 does not itself guarantee a GPU feature. GLSL is embedded in Kotlin.
AndroidX core and AppCompat are the only runtime library dependencies.

Earth is a 64×128 UV sphere with an interleaved position/normal/UV buffer;
Moon reuses the mesh generator at 24×48. Day, night, and cloud textures feed a
shader with sunlight, atmosphere, terminator, and illustrative auroras.
16,000 procedural stars and catalog-based constellation lines form the sky.
OpenGL handles belong to a renderer/context, are created at surface creation,
and are released with that surface. The shared CPU data can be uploaded again
after context loss. Texture quality is bounded separately for app, wallpaper,
and widget to reduce memory pressure.

## Explicit scene clocks and shared projections

Each frame snapshots a supplied `SceneClock` and passes the instant to Sun,
Moon, ISS, Earth, indicators, and alignment checks. `AppSceneClock` supports a
temporary offset and held/playing Explore time; wallpaper and widget use real
time. Astronomy calculates from explicit instants without global position
caches. This keeps a time simulation from changing other surfaces.

`SceneProjection` supplies the matrices used for rendering and picking,
including wallpaper framing. `OrbitCamera` owns drag/pinch, elapsed-time
momentum, idle rotation, fly-to, and remembered viewpoint. Reduce motion disables
decorative animation while preserving direct input.

## Shared observations with honest freshness

`EarthRepository` owns application-context background workers, coalesced
requests, immutable feed snapshots, and versioned atomic event caches. NASA
cloud extraction and USGS/EONET parsing are separate providers. Scenes consume
cached results; GL recreation does not start network requests. States distinguish
loading, fresh empty feeds, stale retained data, and unavailable data.

Event identity, timestamps, source, optional scientific values, and marker size
are separate fields. A reported observation is not a claim that an event is
happening at this exact moment. Historical/future exploration hides current
observations by default. See [live data](docs/live-data.md).

## Local data and independent home-screen surfaces

Saved places are manually chosen from the globe or an offline city catalog;
there is no location permission. Discovery migration, journeys, saved
observations, and preferences use local stable IDs. Widgets have independent
place selections and offscreen snapshots. Each wallpaper engine owns its EGL
context/thread and separate view preferences. Home-screen surfaces stay silent
and do not depend on a running Activity.

## Bundled localization

Android resources cover 12 languages, including Arabic RTL. `AppLanguage`
unifies Settings and system app-locale choices, while widgets use an explicitly
localized context. Journey and city text is mapped by stable IDs; locale changes
invalidate lesson caches without changing progress. All language resources ship
in APK/AAB installs, allowing offline switching. Source titles and user-authored
saved text are preserved. See [localization](docs/localization.md).

## Faint Moon visibility without artificial lighting

A neutral 1dp silhouette guide is separate from the lunar surface lighting.
It is strongest below 3% illumination and fades away by 12%, with a New Moon
label at up to 1%. Depth testing preserves Earth occlusion; a native localized
label avoids controls and disappears when hidden or another panel is open.
The existing Moon arrow remains usable at every phase. See
[Moon visibility](docs/moon-visibility.md).

## Offline Talia narration with first-launch choice

Fixed narration is generated with ElevenLabs during development and bundled in
12 locale catalogs, 144 MP3s total. The APK contains no key and makes no runtime
ElevenLabs request. `TaliaNarrator` matches cleaned app text and owns MediaPlayer
and transient audio focus. Composite discoveries play whole acknowledgment/card
clips. Dynamic event facts and scores use Android TTS when a voice is available.

First launch explicitly offers narration or quiet exploration. Existing users'
choices are preserved; Settings always exposes Read aloud. Closing, disabling,
backgrounding, or focus loss stops playback without automatic resume. This
supports listening without surprising users or introducing a runtime backend.
See [voiceover](docs/voiceover.md) for coverage, generation, and validation.

## Current constraints

`MainActivity` still constructs substantial panel content alongside lifecycle
coordination; extracting focused UI components remains maintenance work.
Astronomy, cloud extraction, auroras, and ISS motion are educational
approximations. Native-speaker translation/pronunciation review, full TalkBack
traversal, and remaining multi-surface/device checks are recorded as incomplete
in [progress](docs/earth-companion-progress.md), rather than implied by passing
build or layout checks.
