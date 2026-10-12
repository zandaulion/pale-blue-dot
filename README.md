# Pale Blue Dot

Pale Blue Dot is an interactive 3D Earth and astronomy app for Android, designed for curious kids ages 9–12 and grown-ups. Spin the globe, explore recent events, and learn about Earth and space through discoveries and challenges.

The Gradle project is named **Globe**. The app is built with Kotlin and raw OpenGL ES 3.0, with native Android views for the interface and no game engine or third-party rendering library.

## Explore and learn

- **Interactive Earth** — drag to rotate, pinch to zoom, and watch momentum and gentle idle rotation. The camera position is saved between sessions.
- **Day and night** — clock-based sunlight, city lights, an amber terminator line, and a blue atmosphere rim. Tap the surface for an explanation of why that spot is in daylight or darkness.
- **Time slider and Explore time** — briefly scrub ±24 hours, or choose and hold a date within one year of today for a labelled simulation, seasons comparison, and one-tap return to Now.
- **Latitude guides** — use Layers → Latitude guides to show or hide the equator, tropics and polar circles together. The choice is remembered independently of real or simulated time and seasons journeys.
- **Cloud modes** — choose off, generated clouds, or NASA satellite-derived clouds in Layers.
- **Earth events** — tap markers for earthquakes, volcanoes, wildfires, and severe storms to see their titles, ages, and explanations written for kids.
- **Sky and space** — 16,000 procedural stars, a Milky Way band, 15 constellations using J2000 star positions, the Sun and Moon, and an illustrative ISS orbit. A thin outline and translated label make new Moons and small crescents visible without lighting their dark surface. Tap the Sun/Moon indicator arrows to bring those bodies into view, or tap the ISS marker for an explanation.
- **Visual effects** — animated aurora zones and approximate solar/lunar eclipse alignment alerts.
- **Places and Field notebook** — save a named point from the globe or an offline city catalog, set its time zone and primary place, and keep existing discoveries, completed journeys, and saved event observations on-device.
- **Today and Earth Today widget** — see the Moon, local daylight at a chosen place, a recent attributed event when available, and a short prompt. Each home-screen widget can choose its own place.
- **Live wallpaper** — configure Whole Earth, Night Lights, or Earth's Horizon, preview it, and launch Android's apply flow.
- **Deeper exploration** — show the bundled plate-boundary layer and take three guided journeys through sunrise, the Ring of Fire, and seasons. Compact lesson and feedback cards keep Earth visible and interactive; hide a card and resume from the same step.
- **Find-it challenges** — find daytime, night-time, or a particular event type. Event challenges are offered only when that type is present in the downloaded data.
- **Audio** — optional offline Talia narration for fixed cards, event explanations and journeys in all 12 languages, plus reading actions for reference sections; a separate Android voice switch opts into changing event facts and scores. Ambient music has a separate switch and volume slider.
- **Onboarding and legend** — first launch offers “Explore with Talia” or “Explore quietly” and remembers the choice. Settings has separate Talia voice and Android voice switches; choosing Talia enables only the natural voice. An illustrated guide explains the scene.
- **Sharing** — capture a branded image of the globe and share it through Android's share sheet after a parental gate.

Day/night and night-sky lessons appear once as new discoveries. Touch and hold Earth or the sky to revisit its lesson; holding Earth also lets you save that point. Later ordinary taps stay quiet. See [globe interactions](docs/globe-interactions.md).

The app adapts to orientation and window-size changes, with side panels and bounded reading widths on larger screens. See [tablet layout and validation](docs/tablet-layout.md) for tested sizes and hardware limits.

The app supports 12 languages: English, German, Spanish, French, Arabic, Hindi, Japanese, Korean, Brazilian Portuguese, Romanian, Ukrainian, and Simplified Chinese. It follows your device language, with an override in Settings → Language. See [localization coverage and review status](docs/localization.md).

The current checkout includes the Talia integration tested on a Motorola Edge 30 Neo. Version metadata remains 3.10/code 12; the integration has not been published to Play. Existing onboarded users keep their audio preference and are not prompted again.

## Data and simulation

The scene combines downloaded observations with calculated and illustrative elements:

| Layer | Source and behavior |
|-------|---------------------|
| Clouds | NASA Worldview Snapshot API, using the previous day's VIIRS SNPP true-color imagery. Cloud opacity is estimated from brightness and saturation. |
| Earthquakes | USGS GeoJSON feed for magnitude 4.5+ earthquakes in the past seven days. |
| Volcanoes | NASA EONET events with open status, without a day filter. |
| Wildfires | NASA EONET open events with a ten-day filter. |
| Severe storms | NASA EONET open events with a seven-day filter; the latest reported position is used. |
| Sun and Moon | Simplified astronomical calculations driven by the simulated clock. |
| ISS | A simplified circular orbit with fixed orbital parameters and precession, rather than a live position feed or updated TLE data. |
| Auroras and background stars | Procedural visuals. Constellation lines and their star markers use catalog coordinates. |
| Eclipse alerts | Approximate alignment checks between Sun and Moon directions, rather than precise eclipse predictions or local visibility calculations. |

Cloud and event fetches run through a shared background repository. The processed cloud image and versioned event feeds are cached locally, and stale cached feeds remain labelled when refreshes fail. Rendering surfaces re-upload cached data without starting their own downloads.

The app's public-data requests require no API keys. ElevenLabs credentials are used only by the development-time narration generator. The time slider changes the astronomical simulation; it does not retrieve historical or forecast clouds or Earth events.

## Privacy and permissions

- No accounts, ads, analytics, or tracking SDKs.
- The only requested permission is `INTERNET`, for public NASA and USGS data.
- No location permission or GPS access.
- Preferences, saved places, widget selections, notebook entries, and discovery/journey progress are stored locally in app-private storage.
- Talia recordings ship in the APK; playback sends nothing to ElevenLabs and requires no microphone access. Device text-to-speech behavior depends on the selected engine and installed voices.
- Shared images are generated in the app cache on-device. A parental gate precedes the Android share sheet, where the user chooses a destination.

See [PRIVACY.md](PRIVACY.md) for details.

## Build

Open the project in an Android Studio version compatible with the configured Android Gradle Plugin, install Android SDK Platform 36, and sync Gradle. Configure the SDK path through Android Studio or `sdk.dir` in your local `local.properties`.

The current workspace configuration uses:

| Setting | Value |
|---------|-------|
| Android Gradle Plugin | 9.4.1 |
| Gradle wrapper | 9.6.0 |
| Kotlin plugin | 2.2.10 |
| Gradle daemon JVM | JetBrains JDK 21, selected by `gradle/gradle-daemon-jvm.properties` |
| Java/Kotlin bytecode target | 17 |
| compileSdk / targetSdk | 36 (Android 16) |
| minSdk | 24 (Android 7.0) |
| Required graphics support | OpenGL ES 3.0, explicitly required by the manifest |
| Application ID | `com.zandaulion.palebluedot` |
| App version | 3.10 (version code 12) |

Build a debug APK on Windows:

```powershell
.\gradlew.bat assembleDebug
```

On macOS or Linux:

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Run it on an Android device or emulator with OpenGL ES 3.0 support. Initial setup may need network access to provision the configured JDK and download Gradle and dependencies.

## Code structure

Sources live in `app/src/main/java/com/globe/app/`:

| File or package | Responsibility |
|-----------------|----------------|
| `MainActivity.kt` | Native UI overlays, onboarding, learning cards, challenges, audio, preferences, and lifecycle handling |
| `GlobeSurfaceView.kt` | OpenGL surface and drag, pinch, and tap handling |
| `GlobeRenderer.kt` | Render lifecycle, draw order, and cached scene data |
| `time/` | Independent app exploration and real-time surface clocks |
| `camera/` | Orbit camera, momentum, idle rotation, and fly-to animations |
| `earth/` | Earth mesh, shaders, textures, cloud download/cache, and Sun position calculations |
| `moon/`, `sun/` | Moon and Sun rendering, plus Moon position calculations |
| `stars/` | Procedural starfield and constellation rendering |
| `iss/` | Simplified ISS orbit and marker rendering |
| `events/` | NASA/USGS providers, event markers, and screen-to-globe picking |
| `eclipse/` | Approximate Sun–Earth–Moon alignment detection |
| `indicators/` | Screen-space Sun and Moon indicator arrows |
| `kids/` | Discoveries, challenges, daily facts, Moon phase, parental gate, narration text cleanup, and offline Talia playback |
| `AppLanguage.kt` | Persisted app locale, system locale integration, and localized contexts |
| `share/` | Frame capture, branding, cache output, and Android sharing |
| `data/` | Shared event repository and deterministic Today briefing |
| `places/` | Local saved places, city catalog, sunrise/sunset, and pins |
| `wallpaper/`, `widget/` | Independent wallpaper engine and home-screen widget |
| `explore/`, `render/` | Journeys, notebook, plate boundaries, and seasonal guides |

Most UI is constructed programmatically in `MainActivity`; GLSL shaders are embedded in Kotlin source. Rendering components own their OpenGL resources, initialized per surface. App, wallpaper, and widget use separate scene clocks.

### Render pipeline

Each frame snapshots the simulated time, updates the camera, and draws:

1. Stars
2. Constellation lines and markers
3. Sun billboard and glow
4. Moon, including the faint-phase outline
5. Earth, including clouds, atmosphere, terminator, and aurora effects
6. Earth event markers, plate boundaries, seasonal guides, and saved-place pins
7. ISS orbit and marker
8. Sun and Moon indicator arrows

The renderer then checks eclipse alignment and reports changes to the UI. Native Android views display controls and learning overlays above the OpenGL surface.

### Assets and dependencies

Bundled textures in `app/src/main/res/drawable-nodpi/` are `earth_day.jpg`, `earth_night.jpg`, and `moon.jpg`. Ambient music is bundled as `app/src/main/res/raw/ambient_space.mp3`. Talia's 1,212 MP3s, shared resource-script catalog and 12 text-to-clip indexes live in `app/src/main/assets/narration/` (about 124 MiB of audio). Building and playing the existing clips requires no ElevenLabs key.

The app's declared library dependencies are AndroidX `core-ktx` and `appcompat`. Rendering, matrix math, networking, image handling, media playback, and narration use Android/JDK APIs.

## Further documentation

- [Earth companion implementation guide](docs/earth-companion-implementation-guide.md) — the phased product and engineering specification for GPT-6 SOL, with acceptance criteria and a starting instruction.
- [Earth companion progress](docs/earth-companion-progress.md) — implemented milestones, USB device checks, screenshots, and unexecuted acceptance checks.
- [Data credits](docs/data-credits.md) — bundled city and plate-boundary provenance and licenses.
- [Localization](docs/localization.md) — exact locale tags, translation maintenance, and layout audit coverage.
- [Moon visibility](docs/moon-visibility.md) — outline thresholds, label placement, and phase checks.
- [Voiceover](docs/voiceover.md) — narration coverage, local credentials, clip generation, and device validation.
- [Build and release](docs/build-release.md) — current toolchain, signing, testing, and APK handling.
- [Documentation index](docs/index.md) — technical background and current implementation references.
- [Architecture decisions](ARCHITECTURE.md) — rendering, localization, and audio design rationale.
- [Feature ideas](TODO.md) — an existing backlog of possible additions.
- [Store listing](store/listing.md) and [release notes](store/release-notes/) — product description and version history.

## License and credits

The source code is licensed under the [MIT License](LICENSE). See [CREDITS.md](CREDITS.md) for NASA texture attribution and the bundled music's source and license.
