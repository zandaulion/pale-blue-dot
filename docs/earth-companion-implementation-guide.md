# Pale Blue Dot: Earth Companion Implementation Guide

Prepared for GPT-6 SOL on September 23, 2026.

## 1. Assignment and product direction

Evolve the existing Pale Blue Dot Android app into an **Earth companion for all ages**. Its promise is **“A living window onto Earth.”** Preserve the beautiful globe, approachable science, peaceful atmosphere, and local-first design. Deliver the work incrementally in the sequence below, with working, verified features at each milestone.

This is a product and engineering specification, not a request to replace the app from scratch. Read the current source before changing it; repository state may have advanced since this guide was written. The implementation choices below are defaults that make the approved direction concrete. Resolve routine details yourself and record meaningful deviations with their reasons.

The experience has three parts:

1. **Earth now:** sunlight, clouds, recent natural events, and an uncluttered globe.
2. **Your daily perspective:** live wallpaper, saved places, a useful Today panel, and a widget.
3. **Explore and understand:** optional layers, explanations, guided journeys, and a personal field notebook.

### Principles

- The globe is enjoyable without completing a task. Avoid streak pressure, compulsory quizzes, intrusive celebrations, and automatic daily popups.
- Use clear language accessible to children and adults. Put useful facts first and offer deeper explanations on demand.
- Keep no accounts, ads, tracking, backend, or mandatory location access. Saved places are chosen manually.
- Distinguish observations, forecasts, calculations, and illustrative visuals. Display the relevant times and sources.
- Preserve existing preferences and earned discoveries. Broader positioning must not erase progress or silently change existing audio choices.
- Preserve the current sharing gate during implementation. Changing the intended audience does not by itself settle store declarations or distribution requirements; do not remove safeguards based on that assumption.
- Do not add an AI service, subscription, social feed, push notifications, flight tracker, AR mode, or additional planets in this scope.

### Delivery sequence

| Milestone | Deliverable | Depends on |
|-----------|-------------|------------|
| M0 | Baseline and regression evidence | Current checkout |
| M1 | Companion interface, data clarity, event cards, layer controls | M0 |
| M2 | Shared rendering foundation and live wallpaper | M1 |
| M3 | Saved places, richer Today, Earth Today widget | M2 |
| M4 | Tectonic plates, seasons, guided journeys, field notebook | M3 |

Finish and validate each milestone before moving on. If assigned only one milestone, limit implementation to that milestone and its necessary prerequisites. If assigned the entire guide, proceed through all milestones without asking for repeated approval of routine implementation decisions. Publishing, production signing, and store-console changes are separate from implementation.

## 2. Current code and constraints

The source of truth is the Kotlin code and Gradle configuration, followed by the recently updated [README](../README.md). Several older pages under `docs/` describe features or settings that no longer exist.

| Current entry point | What to inspect or change |
|---------------------|--------------------------|
| `MainActivity.kt` | Roughly 1,670 lines: programmatic views, overlays, learning, audio, preferences, and lifecycle |
| `GlobeSurfaceView.kt` | Drag/pinch/tap handling and the activity's GL surface |
| `GlobeRenderer.kt` | Draw ordering, data fetches launched from surface creation, eclipse callbacks |
| `TimeProvider.kt` | Global mutable simulated time and per-frame snapshot |
| `camera/OrbitCamera.kt` | Camera state, fly-to, and frame-dependent momentum/rotation |
| `earth/EarthRenderer.kt` | GL textures, cloud modes, terminator, and procedural auroras |
| `earth/CloudMapProvider.kt` | Previous-day imagery extraction and six-hour processed-image cache |
| `earth/SunPosition.kt`, `moon/MoonPosition.kt` | Global position caches using simulated time |
| `stars/StarsShader.kt` | Singleton containing mutable GL program/uniform handles |
| `events/EarthEventsProvider.kt` | USGS/EONET download and parsing; failures currently become empty lists |
| `events/EarthEventsRenderer.kt` | Marker upload/draw and event list used by hit testing |
| `events/GlobePicker.kt` | Ray/sphere picking; projection assumptions must match rendering |
| `kids/` | Eight discoveries, six challenge types, daily facts, Moon phase, and sharing gate |
| `share/` | Surface capture, branded image, and Android share sheet |

Paths above are relative to `app/src/main/java/com/globe/app/`.

At preparation time: app version 3.8/code 10, app ID `com.zandaulion.palebluedot`, namespace `com.globe.app`, minSdk 24, target/compile SDK 36. The working tree also contains existing Gradle changes: AGP 9.3.1, Gradle 9.5.0, Kotlin 2.2.10, and a JetBrains JDK 21 daemon selection; app bytecode targets 17. Inspect these files rather than trusting old build documentation.

**Existing uncommitted work:** `README.md`, `build.gradle.kts`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`, `settings.gradle.kts`, and `gradle/gradle-daemon-jvm.properties` were modified or untracked before implementation. Preserve them. Do not reset the working tree or downgrade the build stack to match stale documentation.

Retain Kotlin, native Android Views, and OpenGL ES 3.0. Extract focused components as work requires; do not introduce Compose, a rendering engine, a dependency-injection framework, or a multi-module rewrite just to reorganize the code. Small dependencies are acceptable when justified by a concrete requirement and supported on minSdk 24.

## 3. M0: Establish the baseline

1. Read repository instructions, inspect `git status`, and record pre-existing changes.
2. Build with the configured wrapper and local SDK. Record toolchain errors separately from application failures. Make only necessary, explained build fixes.
3. Identify available devices/emulators. Capture the current globe, Today, journal, event card, and legend where execution is possible.
4. Exercise rotation, pinch, tapping, time scrubbing, audio, cloud modes, sharing, orientation changes, and pause/resume.
5. Record where tests exist. Add a focused test harness when implementing logic that needs it; do not invent a passing baseline if no tests exist.
6. Keep a concise implementation record in `docs/earth-companion-progress.md`: milestone status, decisions, changed behavior, validation evidence, and unresolved blockers.

**Exit criteria:** baseline build result and runtime coverage are explicit, user changes are preserved, and regressions can be distinguished from pre-existing problems. If device access is unavailable, continue code work while identifying runtime checks as pending.

## 4. M1: Companion foundation

### 4.1 Main experience and navigation

The launch screen shows the globe immediately, with a compact top status/time area and bottom entry points for **Today**, **Layers**, and **Explore**. Put **Settings** in a clearly labelled overflow or secondary control. Avoid a collection of floating emoji buttons around every edge.

- Today opens on demand. Remove its automatic once-per-day modal launch behavior.
- Replace the existing first-run full-screen introduction with a short dismissible introduction to rotation, zoom, and tapping. Respect the existing onboarded flag for upgrades.
- Today, Layers, Explore, and Settings use one consistent panel pattern. Only one major panel is open at once; Back closes the current detail or panel before leaving the app.
- Keep the time control readily available but compact when inactive. Preserve the current temporary +/-24-hour scrub and return-to-now behavior until M4 adds explicit exploration time.
- Move music, volume, narration, wallpaper configuration, legend/help, and sharing to appropriate panels. Share remains easy to find and keeps the existing gate.
- Make event cards persist until dismissed or replaced, rather than expiring while being read. Offer an obvious close control and optional narration.
- Default audio off for new installs; preserve stored choices for existing users. Wallpaper and widgets must never play audio or invoke narration.

Use dark neutral panels, restrained blue/amber accents, readable type, consistent spacing, and meaningful icons. Render the globe behind system bars if appropriate while applying insets to controls. Use scrollable panels on small screens and a side panel on wide layouts. Adapt to actual available width, including split-screen; do not rely on device labels or hard-coded orientation alone.

Meet basic accessibility needs: labelled controls, at least 48dp touch targets, sufficient contrast, large text support, and no color-only meanings. Provide a native list of events in Today or Explore so information is accessible without tapping pixels on the globe. Include a Reduce motion setting that stops idle spin and suppresses decorative pulses/twinkle while retaining useful direct manipulation.

### 4.2 Layers

Provide persisted controls for:

- Clouds: Off / Generated / Satellite.
- Earthquakes, volcanoes, wildfires, and storms: separate switches.
- Constellations, ISS illustration, aurora illustration, and terminator line.

Keep lighting, atmosphere, and the basic star background part of the scene. Explain illustrative layers in their descriptions. Apply filters consistently to rendering, picking, event lists, and challenge eligibility; invisible events must not capture taps. Cloud loading/failure must not block other layers.

### 4.3 Event cards and data truthfulness

Replace the minimal event model with stable identity, category, title, position, source, observation/update time, optional source URL, optional magnitude and depth, and any available dated geometry. Preserve unknown values as unknown. A zero timestamp must not be displayed as an event from 1970.

For earthquakes, show magnitude and depth when reported. Do not manufacture magnitude for non-earthquake events just because markers previously used a fixed magnitude value for sizing. Separate visual marker size from scientific fields.

Suggested card structure:

1. Category and event title/location.
2. Relevant facts and observation date/time.
3. Source and fetch/freshness status.
4. An expandable **Why does this happen?** explanation.
5. Save action, completed with the field notebook in M4; omit the button until it works.

Use calm wording. An EONET event marked open is not proof of an eruption or fire occurring at this exact moment. Say “Reported by NASA EONET” and show its date rather than asserting “erupting right now.” Label clouds **Satellite imagery — [source date]**; store the requested imagery date separately from download time.

### 4.4 Data ownership and cache

Move fetching out of `GlobeRenderer.onSurfaceCreated()` into a repository with application context and bounded background execution. Recreating a GL surface must not initiate duplicate downloads. Publish immutable snapshots to UI and rendering consumers.

Model each feed's status explicitly: not loaded, loading, ready (including a valid empty result), stale cached result, or unavailable. Keep successful categories visible when another feed fails. Cache the last successful event response/snapshot by category with schema version and timestamps; use atomic writes and recover safely from corrupt cache.

Default refresh policy: reuse events fetched within 30 minutes and cloud images fetched within six hours. Refresh stale data when a foreground consumer requests it; coalesce concurrent requests. Offer a manual refresh with an in-flight guard and a short cooldown. Do not add a continuous background poller. Distinguish “checked recently” from “event happened recently.” These are product defaults, not guarantees of source update frequency.

Close network streams, set timeouts, bound response size, and skip malformed individual records without dropping an otherwise usable feed. Handle unsupported geometry explicitly. Keep provenance alongside data and verify attribution requirements for newly bundled assets.

### 4.5 M1 acceptance

- The globe is the first view on fresh and returning launches; Today never interrupts it.
- Every panel can be opened, read, scrolled, and dismissed on phone and tablet layouts.
- Existing discovery/audio/camera/cloud preferences survive an upgrade.
- All layer switches work immediately and persist; hidden markers cannot be selected.
- Event cards expose available facts, source, and honest times, including partial-failure and offline states.
- Cached observations remain usable offline with stale labels; a valid empty feed is distinguishable from failure.
- Large text, TalkBack navigation through native controls, Back behavior, and reduce-motion behavior are checked.

## 5. M2: Rendering foundation and live wallpaper

### 5.1 Refactor for multiple independent renderers

This is a prerequisite, not optional cleanup. The activity, wallpaper preview, active wallpaper, and later widget snapshot renderer can exist together.

- Introduce an instance-owned scene renderer accepting camera state, layer settings, immutable data, and a per-frame time snapshot.
- Replace global mutable simulated time with an app exploration clock and separate real-time clocks for wallpaper/widget consumers. Pass explicit times to astronomy calculations; avoid global cache invalidation affecting other consumers.
- Eliminate shared mutable GL handles, starting with `StarsShader`. GL programs, textures, VAOs, buffers, and uniform locations belong to a renderer/context. Share immutable shader source and CPU data only.
- Add clear create/resize/draw/release operations. Recreate every GL resource after context loss and re-upload cached CPU data without requiring network access.
- Audit `EarthRenderer` live-cloud texture IDs/availability and event buffer state on recreation. No state may refer to a destroyed context.
- Keep network work outside GL lifecycle callbacks. Define bitmap ownership so one consumer cannot recycle an image while another uploads it; release superseded images.
- Replace frame-count-based camera motion with elapsed-time-based motion. A 15fps wallpaper and 60fps activity should rotate at the same angular speed.
- Centralize projection parameters for rendering, picking, and overlays. If framing shifts, supply the actual matrices to picking rather than copying approximate formulas.

Keep the existing coordinate convention: +Y north, -X Greenwich, +Z 90 degrees east. Add fixtures for known positions before refactoring conversions. Avoid an unnecessary coordinate-system rewrite.

Suggested boundaries are `render/GlobeSceneRenderer`, `render/SceneState`, `time/SceneClock`, `data/EarthRepository`, and separate activity/wallpaper rendering hosts. Exact names are flexible; ownership is mandatory.

### 5.2 Wallpaper product

Add a live wallpaper reachable through Settings and Android's wallpaper picker. Present three presets:

| Preset | Default composition |
|--------|---------------------|
| Whole Earth | Complete globe, current sunlight, subdued stars |
| Night Lights | A view favoring the night side while retaining real lighting |
| Earth's Horizon | Close limb framing with atmosphere and part of the surface |

Allow fixed viewpoint or slow orbit, cloud mode, globe scale, and horizontal/vertical framing. Preview these changes before applying. A night-side-following preset must describe that camera behavior; do not falsify the Sun direction to keep a saved viewpoint dark. Keep wallpaper settings separate from the app camera and exploration layers.

Default to a quiet composition with no event markers, labels, indicators, challenges, music, or narration. Use reduced decorative effects. Satellite clouds are optional and use the shared cache. Cloud failures must leave a usable scene.

### 5.3 Android implementation and lifecycle

Implement a `WallpaperService` with an Engine and a rendering thread/EGL host for the engine surface. Keep the activity's `GLSurfaceView` as one host if that remains simplest; do not attach its view hierarchy to the wallpaper.

Register the wallpaper service, system binding permission, intent filter, and wallpaper metadata required by Android. This service-level binding permission does not request a new runtime permission from the user. Use the system preview/apply flow; do not silently replace the user's wallpaper.

Android can create multiple engines, including previews. Each needs independent rendering state and teardown. Stop frame scheduling when invisible or without a valid surface. On visibility return, render the current real time and reuse cached data. Handle surface resize, destruction, context loss, preview cancellation, and screen off/on without leaking threads or resurrecting released engines. See the [Wallpaper Engine API](https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine).

Suggested scheduling targets: fixed view about one frame per second with decorative animations disabled; slow orbit up to 15fps. Reduce animation further in battery saver. Measure actual performance; these targets are not promises of battery savings. No wake locks, continuous foreground service, or hidden render loop. Do not promise independent home/lock-screen behavior on every launcher/device; use the system's supported choices.

### 5.4 M2 acceptance

- All three presets can be previewed, customized, applied, and restored after process restart.
- App time scrubbing never changes wallpaper time; app camera/settings never unintentionally modify wallpaper settings.
- Active wallpaper, system preview, and foreground app can coexist without wrong textures, GL errors, or time contamination.
- Hidden/destroyed engines stop drawing; repeated preview/open/close cycles leave no accumulating render threads.
- Context loss and screen off/on recover using cached data offline.
- Record frame rate, frame time, and a bounded visible/hidden profiling comparison on available hardware. Report observed results rather than an unmeasured battery claim.

## 6. M3: Your Earth — places, Today, and widget

### 6.1 Saved places

Add a Places section to Explore. Users can choose from a small bundled searchable city catalog or tap a point on the globe and name it. Use a properly licensed catalog with documented provenance; do not quietly add a third-party geocoding service.

Store stable ID, display name, latitude, longitude, and an IANA time-zone ID. Bundled cities include time zones. For arbitrary map points, require explicit time-zone selection or mark the zone unset; do not derive civil time from longitude or silently use the phone's zone. A place with an unset zone can still be saved and viewed, but local clock/sunrise features should invite zone selection.

Support create, rename, delete, set primary, and fly-to. Offer an undo for deletion. The primary place drives Today and can be chosen independently per widget. No automatic location permission request is needed.

For a selected place show local time/date, day/night state, approximate sunrise and sunset, and daylight duration. Compute these for the place's local calendar date, including daylight-saving transitions. Handle polar day/night as named states instead of fabricated rise/set times. Define the sunrise convention and accuracy honestly; test against independently sourced reference values within a documented tolerance before presenting minute-level times. If using `java.time`, account for minSdk 24 with supported desugaring or another compatible approach.

Keep these calculations separate from decorative render effects and coarse frame caches. Saved-place pins must be distinguishable from event markers and have correct occlusion and tap behavior.

### 6.2 Today as a planetary briefing

Build a deterministic on-device Today model shared by the app and widget:

- Moon phase and estimated illumination for real current time.
- Primary place's local time/daylight information, or a useful “Choose a place” invitation.
- One recent event from available data, with category, date, and source.
- One short exploration prompt or journey suggestion.

Define event selection plainly: prefer a recent valid observation, break ties deterministically, and rotate among available categories without presenting severity as entertainment. Do not claim a selected event is globally “the most important.” If only stale data exists, state that. With no events, show astronomical content and an offline/unavailable message instead.

Tapping a place flies to it. Tapping an event opens that exact event and location; if it has expired from the cache, explain and offer the current event list. Tapping a journey opens its introduction. Keep Today tied to real time even while the globe is in explicit simulation mode, and label that distinction.

### 6.3 Earth Today widget

Use a conventional `AppWidgetProvider`/`RemoteViews` implementation as the default, fitting the existing native Views stack. The widget is a periodically refreshed summary, not an embedded interactive GL surface. Android widgets support limited interactions and layouts; see the [widget overview](https://developer.android.com/develop/ui/views/appwidgets/overview).

Deliver compact and expanded responsive layouts:

- Compact: Moon phase, chosen place/daylight summary, and a clear open action.
- Expanded: add a rendered globe snapshot and a short event or exploration prompt.

Generate the globe bitmap through a bounded offscreen render using the shared scene and real-time clock. Do not require the activity to be open or a wallpaper to be installed. Use modest dimensions matched to widget size; cap bitmap memory and reuse cached renders across equivalent configurations. If rendering fails, use a bundled globe image with an honest “Illustration” label or omit it; do not present an old image as a fresh observation.

Persist configuration by widget ID, support multiple independent instances, and clean it up when a widget is removed. Treat cancelled configuration as cancellation. Use explicit intents and distinct PendingIntent identities for different widget actions/instances; handle cold and warm app launches and repeated taps correctly.

Default scheduled updates to once per hour, plus immediate updates after a relevant app refresh or configuration change. The system may delay delivery. Standard `updatePeriodMillis` updates cannot run more often than every 30 minutes; do not promise exact cadence or use exact alarms to keep a decorative image current. Show “Updated [time]” when freshness matters. See [widget update guidance](https://developer.android.com/develop/ui/views/appwidgets/advanced).

Perform image generation and any required I/O away from the broadcast receiver's synchronous callback, using a bounded platform-compatible worker. Coalesce refresh requests, read the shared cache first, and avoid adding a second download system. Handle locale/time-zone changes and host resize updates. A widget must not start audio, hold a wake lock indefinitely, or maintain a GL animation loop.

### 6.4 M3 acceptance

- Places persist across process death and can be edited, deleted/undone, and selected as primary.
- Correct civil time is shown across DST and date boundaries; missing zones are explicit.
- Sunrise/sunset and polar states pass reference checks; no NaN or invented times reach the UI.
- Today works with fresh, partial, stale, and absent network data.
- Widget creation, resize, reconfiguration, removal, and two simultaneous instances work.
- Widget taps reach the intended destination from a stopped or running app.
- Background snapshot generation does not change the foreground app or wallpaper scene.
- Widget generation has bounded runtime/memory and a useful fallback if EGL creation fails.

## 7. M4: Deeper exploration

### 7.1 Tectonic plate boundaries

Add a selectable plate-boundary layer with a legend and short explanations. Acquire and bundle an appropriately licensed, simplified dataset with provenance, version, and preprocessing instructions. The [USGS boundary service](https://earthquake.usgs.gov/arcgis/rest/services/eq/map_plateboundaries/MapServer) is a research starting point; verify its underlying dataset attribution and redistribution terms before bundling.

Render lines slightly above the Earth surface with depth testing and correct antimeridian/polar handling. Use geodesic interpolation or sufficient subdivision so lines follow the globe rather than cutting through it. Keep geometry modest and upload it once per context. Distinguish boundary types only when the source reliably supplies them. Do not infer a specific earthquake's cause simply because it is close to a line.

The layer must work offline and combine with existing earthquake/volcano data. Use the pattern to explain a broad relationship, while acknowledging that some events occur away from plate boundaries. See the [USGS explanation](https://pubs.usgs.gov/gip/earthq1/where.html).

### 7.2 Explicit time exploration and seasons

Add an explicit **Explore time** mode with date/time selection, play/pause, a visible simulated-time label, and a one-tap **Now** action. Preserve the quick temporary scrub for casual use; explicit time mode holds the selected time until the user exits. Constrain the initial date range to a validated interval, defaulting to one year either side of the current date, rather than claiming arbitrary historical accuracy.

For seasons, provide equator/tropics/polar-circle guides, a year timeline, and marked places for comparing daylight. Explain axial tilt with a clear diagram or dedicated view. Existing Earth-fixed Sun calculations already include tilt effects: do not add a second tilt transform that breaks the terminator or marker coordinates.

Keep current observational layers clearly separate from simulated dates. Default to hiding event/cloud observations in explicit historical/future lessons, with a labelled option to restore the latest observations for comparison. Wallpaper, Today, and widgets remain on real time.

Do not extend the existing approximate eclipse detector into a precise prediction feature. If eclipse illustrations appear in a lesson, label them as such and validate the chosen scene independently.

### 7.3 Guided journeys

Create a small data-driven journey engine with ordered steps, instructions, required scene state, allowed interactions, success conditions, and explanatory feedback. Bundle content locally. A journey can request a camera move or temporary layer/time settings; on exit restore the user's previous settings. Persist progress after meaningful steps and offer Resume or Restart after process death.

Implement three complete journeys before expanding the catalog:

| Journey | Interaction and learning outcome |
|---------|----------------------------------|
| Follow sunrise | Observe a marked place, predict where dawn will arrive, advance time, and explain the changing sunlight |
| Explore the Ring of Fire | Visit several earthquake/volcano locations, predict the pattern, reveal plate boundaries, and explain the relationship |
| Understand the seasons | Compare daylight at northern/southern marked places on two dates, predict the change, and reveal the axial-tilt explanation |

For the tectonic journey, use recent events when suitable. Bundle sourced, dated historical examples as an explicitly labelled offline lesson fallback. Never pass them off as current events. Journeys should take roughly two to five minutes, be skippable, and include useful explanation after both correct and incorrect answers. Avoid relying solely on color or precision tapping; provide native controls for equivalent actions.

Keep existing quick challenges accessible under Explore, with calmer wording. Do not build a competitive score system or daily streak requirement. It is acceptable for old challenge types to remain simple while journeys introduce richer reasoning.

### 7.4 Field notebook and migration

Evolve the journal into a Field notebook with completed journeys, existing discoveries, and saved event observations. Places remain accessible from their dedicated list rather than being duplicated as independent records. Store a saved event's source, observation time, and minimal facts so it remains understandable after it leaves a live feed.

Migrate all eight existing discovery IDs without resetting progress. The migration must be idempotent and survive interruption; retain the old data until the new representation is safely written. Do not persist enum ordinals as new identifiers. Existing discoveries appear as legacy entries with approachable descriptions. Completion acknowledgements should be quiet and optional rather than large reward popups.

### 7.5 M4 acceptance

- Plate boundaries render continuously across the antimeridian, remain attached while rotating, and work offline.
- Time exploration is clearly labelled and independent of other real-time surfaces.
- Seasonal daylight patterns are correct in both hemispheres at reference dates.
- All three journeys have working start, interaction, feedback, completion, exit, and resume paths.
- Offline lesson examples have visible dates/sources and cannot enter the live-events repository.
- Journey exit restores the prior scene; migration preserves all existing discoveries across repeated launches.

## 8. Verification strategy

### Automated checks

Use focused tests for meaningful behavior, not snapshots that merely repeat implementation constants:

- Event parsing: valid/empty/malformed records, absent magnitude/depth/time, multiple geometries, stable IDs, and partial feed failure.
- Cache behavior: fresh/stale/corrupt data, failed refresh preserving successful data, atomic replacement, and concurrent-request coalescing.
- State: layer filters agree with picking/challenges, migrations are idempotent, clocks are independent, and journey exit restores state.
- Astronomy/places: known coordinate conversions, daylight reference cases, DST/date boundaries, polar conditions, and simulation vs real-time separation.
- Widget configuration/intents: separate instance identities, correct destination, and stale event handling.

Use deterministic clocks and bundled fixtures; ordinary tests must not depend on a live NASA/USGS response. Put GL-dependent and Android lifecycle checks in appropriate instrumented/device coverage. Instrumented checks supplement, rather than replace, visual inspection.

Typical Windows commands, adapting task names to the actual project:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

Run the last command only when instrumentation tests and a device are available. Report executed tasks and their results exactly; “no tests found” is not a passing test suite. A debug build is not evidence of release signing or device correctness.

### Manual/device matrix

| Area | Minimum scenarios |
|------|-------------------|
| Layout | Small phone portrait/landscape, tablet or wide window, split-screen, large font, system insets |
| Compatibility | minSdk behavior where an emulator/device is available and a modern target-SDK device |
| Lifecycle | Background/foreground, process recreation, orientation/resize, GL context loss, screen off/on |
| Network | Fresh launch offline, cached offline launch, one feed failing, slow fetch, valid empty response |
| Input | Tap vs drag vs pinch, hidden layers, Back, accessibility navigation |
| Multi-surface | App + active wallpaper + preview, then widget snapshot rendering |
| Upgrade | Existing preferences/discoveries, repeated migration, new-install defaults |
| Time | DST boundary, midnight in a saved place, polar day/night, held simulation, return to now |

For target SDK 36, explicitly verify system-bar insets and current Back behavior against [Android edge-to-edge guidance](https://developer.android.com/develop/ui/views/layout/edge-to-edge); old fullscreen flags alone are not an adequate layout strategy. Guard platform-specific API calls while preserving minSdk 24.

Capture before/after screenshots for the main globe, panels, event details, wallpaper presets, and widget sizes. Record hardware/emulator and limitations for performance evidence. Never mark unexecuted device checks complete.

## 9. Engineering and scope guardrails

- Avoid doing all structural changes before delivering any visible behavior. Extract UI/data components during M1 and renderer hosting during M2.
- Keep only lifecycle/navigation coordination in `MainActivity` as features move out. Suggested packages: `ui/`, `data/`, `render/`, `time/`, `wallpaper/`, `places/`, `widget/`, and `explore/`; create only what is used.
- Move new and edited user-facing strings into resources. Support plurals and locale-sensitive formatting; complete translation is outside this scope.
- Use stable persisted IDs and schema versions. Audit migration from current ordinal-based cloud preferences before changing enum order.
- Keep a single authoritative coordinate/projection path for rendering and selection, and a single data repository for all consumers.
- Close/release network streams, textures, buffers, bitmaps, media/TTS, jobs, and rendering threads according to their owning lifecycle.
- Do not keep an Activity in an application repository, wallpaper service, or widget worker.
- Preserve app ID, namespace, and users' existing data. Change version metadata only for an actual release preparation step.
- Do not add placeholder controls, fabricated live data, broken menu destinations, or unfinished feature toggles to the shipped UI.
- NOAA forecast auroras, storm trails, precise satellite tracking, and a full geographic label system are follow-up candidates, not required to complete these milestones.
- Keep the sharing gate and current external-action behavior unless a separate explicit requirement changes them. No store publication or signing-secret handling is needed to implement this guide.

## 10. Completion and handoff

At each milestone, update `docs/earth-companion-progress.md` and relevant documentation to describe implemented behavior only. Update README, privacy wording, credits, and draft store copy when affected, but do not claim planned features are available. In particular, broader audience wording should not imply that store-console audience declarations have already changed.

Each milestone report must include:

1. What users can now do and the code areas changed.
2. Important decisions/deviations and why they were necessary.
3. Commands run, test results, device checks, and visual evidence paths.
4. Known limitations, remaining acceptance items, and any external blocker.
5. The next incomplete milestone, with enough context to resume without repeating finished work.

The full project is complete when M1–M4 acceptance criteria pass, the app builds, migrations preserve existing data, all implemented surfaces behave independently, and documentation accurately reflects the result. If external conditions prevent validation, list the outstanding checks and their blockers. Code-complete work with unverified device behavior must be labelled that way, not presented as fully validated.

## 11. Copyable starting instruction for GPT-6 SOL

> Implement the Pale Blue Dot Earth companion evolution described in `docs/earth-companion-implementation-guide.md`. Read current repository instructions and source, preserve existing uncommitted changes, and start with M0. Then implement M1 through M4 in dependency order, completing and checking each milestone before continuing. Use the guide's product defaults and acceptance criteria; make routine implementation decisions autonomously and record meaningful deviations. Keep progress and validation evidence in `docs/earth-companion-progress.md`. Preserve the app's identity, local data, privacy posture, and existing sharing safeguard. Do not publish, reset unrelated work, or introduce out-of-scope features. Report real build/test/device results and clearly identify any checks you could not execute. Deliver working behavior, not only a plan or UI placeholders.
