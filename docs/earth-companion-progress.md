# Earth companion implementation record

## Current status — October 12, 2026

M0–M4 and the subsequent language, Moon visibility, and narration additions are
implemented. The milestone entries below are historical test records; baseline
catalog sizes, literal-string debt, and build settings describe their recorded
date. Current behavior is documented in [features](features.md),
[architecture](architecture.md), and [build/release](build-release.md).

- **Globe interactions:** each day/night lesson opens once per persisted
  discovery; later taps stay quiet, and touch and hold deliberately reopens the
  lesson and save-point action. Marker/place taps and challenge answers retain
  their actions. Nine Motorola tests and all 12 updated hint layouts at 100%
  and 130% passed; original settings/preferences were restored. See
  [globe interactions](globe-interactions.md).
- **Localization:** 12 bundled locales, persistent Settings picker, device-language
  option, Arabic RTL, localized journeys/city search/widget/wallpaper. All 798
  translatable resources and placeholders pass the source validator; mappings
  cover 71 lesson text fields and 303 catalog cities. Normal and focused 130%
  Motorola layout audits passed. See [localization](localization.md).
- **Guided journey cards:** compact lesson and feedback cards occupy separate
  space from the interactive globe. Fixed progress/actions surround a scrolling
  body; hide/resume preserves the active step and view. Wide side cards are
  anchored to the top to keep tall progress headers inside the screen. The
  combined Motorola regression passed 19 cases, including two journey-specific
  tests covering all 18 steps, gestures and Activity recreation. See
  [features](features.md#guided-journey-cards) and [tablet layout](tablet-layout.md).
- **Moon:** 1dp outline for new Moons/small crescents, full through 3% illumination
  and faded out by 12%; localized New Moon/Thin crescent label and corrected Moon
  arrow framing. Six phase scenes and both labels in all 12 languages were
  verified at 100% and 130% text size. See [Moon visibility](moon-visibility.md).
- **Narration:** Talia selected from three multilingual voice auditions. The APK
  bundles 144 fixed-card clips; first launch offers Talia or quiet exploration,
  Settings provides separate Talia and Android switches; first launch enables only
  Talia, and existing users are not re-prompted. Changing facts/scores require
  explicit Android voice opt-in. No runtime ElevenLabs requests or API key.
  See [voiceover](voiceover.md).
- **Tablet layouts:** welcome text is capped at 560dp; wallpaper/widget controls
  at 600dp. Wallpaper uses a larger preview beside a 420dp settings column in
  windows at least 840dp wide, and stacks on narrower windows. All 12 languages
  passed tablet-shaped portrait/landscape at 100% and focused 130% audits on the
  Motorola; a narrower profile passed English/French/Arabic, and the original
  phone display passed all 12 at 130%. Twelve functional tests also passed.
  These virtual dimensions verify layout, not physical tablet performance.
  Original device settings and app preferences were restored; evidence remains
  outside version control. See [tablet layout](tablet-layout.md).
- **Narration validation:** app and instrumented-test builds and lint passed. Two
  Talia and four localization tests passed on the Motorola Edge 30 Neo. All clips
  decoded locally and passed Android audio metadata checks. Narration-focused
  onboarding/Settings audits passed for all 12 languages at 100% and 130% text
  size. The larger-text run retried three locales after foreground focus was
  interrupted; no clipping/overlap was reported on completion. Test locale/font
  settings were restored, and new captures/results remain outside the repository.
- **Artifact:** the signed app APK was installed as an update and saved to the
  authorized build-host `~/Projects/APKs` folder, retaining only the latest
  PaleBlueDot APK and preserving other apps' artifacts. Version metadata remains
  3.10/code 12. Talia has not been published to Play.

Open checks remain: independent native-speaker translation/pronunciation review,
full TalkBack traversal, physical tablet/foldable and minSdk coverage, active
wallpaper/app/preview coexistence, compact widget-host resizing, longer battery
behavior, and the unexecuted cache/network scenarios noted by milestone.
Passing playback/layout checks do not close those acceptance gaps.

## Historical companion milestones

## M0 — baseline complete (2026-09-23)

- Starting checkout: `master`; existing edits to `README.md`, `build.gradle.kts`, `docs/index.md`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`, and `settings.gradle.kts`; existing untracked guide and `gradle/gradle-daemon-jvm.properties`. No reset or overwrite of these files.
- No `AGENTS.md` or existing `app/src/test` / `app/src/androidTest` tree was found.
- `gradlew.bat assembleDebug`: **BUILD SUCCESSFUL**, 34 tasks (7 executed, 27 up-to-date). Gradle emitted AGP/Kotlin deprecation warnings from the existing build stack. Initial sandboxed launch returned `Access is denied`; the same command succeeded with the approved execution escalation. No build-file changes were needed.
- USB device: Samsung SM-F936B, Android 16 / API 36, 904×2316 outer display. The installed app was 3.6/code 8; `adb install -r` of the checkout's 3.8 debug APK succeeded, preserving its app data. Existing 8/8 discoveries, music-on, narration-off, and camera/cloud preferences remained visible.
- Device captures: [launch](evidence/m0-launch.png), [Today](evidence/m0-today.png), [journal](evidence/m0-journal.png), [legend](evidence/m0-legend.png), [day/night card](evidence/m0-event.png), [sharing gate](evidence/m0-share.png). The event-tap sample reached a day/night card; a specific natural event marker was not captured. The sharing gate was opened and dismissed without sharing.
- Device interaction: drag rotated the globe; time scrub moved the control; Today, journal, legend, share gate, and Back were exercised. The baseline Today panel interrupts returning users once daily (source and device). The legend extends below the phone viewport and lacks scrolling. Event/day-night cards expire after ten seconds (source). Baseline event fetches are tied to GL surface creation, and feed failures produce empty lists (source).
- Pending baseline runtime checks: multi-touch pinch automation, cloud mode changes, orientation/split-screen, pause/resume visual comparison, TalkBack, and an event marker selected by tapping. No test suite existed to run. These remain regression checks during implementation.

## M1 — implemented and checked (2026-09-23)

- Users now land on the globe without an automatic Today modal. Bottom navigation opens scrollable Today, Layers, and Explore panels; Settings is labelled at the top. One panel is active at a time, Back closes detail/panels, and the time scrub is compact until tapped. The existing first-run flag is honored. Music defaults off only when no stored choice exists. Sharing still enters the parental gate.
- Layers persist cloud mode and individual event, constellation, ISS illustration, aurora illustration, and terminator choices. Event visibility is applied to GPU markers, globe picking, Explore/Today event lists, and challenge options. Reduce motion stops idle spin, star twinkle, aurora motion, cloud drift, and event pulses.
- EarthRepository owns bounded background fetching and versioned atomic event caches by feed. Its immutable snapshots distinguish not loaded, loading, ready (including empty), stale cached, and unavailable. The repository coalesces concurrent requests, reuses data fetched within 30 minutes, and guards manual refresh with a 15-second cooldown. GlobeRenderer.onSurfaceCreated() now only rebuilds GL state and reuploads cached CPU observations; it starts no network tasks.
- Event parsing preserves stable USGS/EONET IDs, source, optional source URL, observation/update time, magnitude/depth where reported, dated point geometries, and an independent marker size. It skips unsupported shapes and malformed individual records. Cards show local observation and feed-check times, scientific values only when present, source, and expandable explanations. NASA EONET copy no longer asserts an eruption/fire is happening right now. The cloud cache records the requested satellite imagery date separately from its file age; unknown dates stay unknown.
- **Device checks:** Samsung SM-F936B / Android 16. [Launch](evidence/m1-launch.png), [Today](evidence/m1-today.png), [Layers](evidence/m1-layers.png), [Explore](evidence/m1-explore.png), [event card](evidence/m1-event-card.png), [wide window](evidence/m1-wide.png), [1.5× text](evidence/m1-large-text.png), [Generated clouds](evidence/m1-generated.png), [Satellite clouds](evidence/m1-satellite.png), and [offline cached feeds](evidence/m1-offline.png). Bottom panels scrolled, native event card stayed until dismissed, Back returned to the globe, and earthquake layer off removed its category from Explore; the original on setting was restored. Airplane mode was restored to off after the offline test. Phone size and font scale were restored to 904×2316 and 1.0.
- **Validation:** assembleDebug and lintDebug passed after the final M1 edits. testDebugUnitTest reported NO-SOURCE (there are no local unit tests). Three parser instrumentation tests passed on the USB phone using connectedDebugAndroidTest; the first lint run failed on the old onBackPressed override, which was removed in favor of OnBackPressedDispatcher, and the next lint run passed. Baseline and final M1 debug installs were successful.
- **Device-test data incident:** Gradle's connected Android test task uninstalled the target app after testing. Its local app data was removed. The M0 captures documented 8/8 discoveries, music on, narration off, and clouds off; these values were restored through the debug app's preferences and 8/8 was verified in Explore ([recovery capture](evidence/m1-recovered.png)). The original exact camera pose and music volume were unavailable after removal; they were reconstructed approximately (camera aimed toward the Americas, volume 0.4). Further phone testing used only in-place adb install -r and manual interaction. No connected Android test task was rerun on that phone.
- **Remaining M1 checks/limits:** TalkBack traversal, true physical tablet/folded-unfolded comparison, multi-touch pinch automation, and a controlled one-feed-only failure were not executed. A wide virtual window and large text were checked. Cloud classification remains an approximate visual extraction from NASA imagery, not a meteorological product. New panel prose still has some Kotlin literals and needs resource extraction. Exact camera and volume recovery is impossible from available baseline evidence.

## M2 — implemented; system preview checked (2026-09-23)

- Users can open Earth live wallpaper settings from the app, preview Whole Earth, Night Lights, and Earth's Horizon, adjust view/motion/clouds/framing, and launch Android's wallpaper apply flow. Wallpaper preferences live separately from the app camera and layers. Night Lights follows the real night side by moving the camera; the Sun stays physically calculated. The wallpaper scene omits observation markers, indicators, ISS illustration, constellation lines, music, and narration.
- `SceneClock` separates the app's temporary offset from real-time wallpaper clocks. The renderers and astronomical calculations take an explicit frame time. Star GL programs are per renderer. Camera motion uses elapsed time. The projection matrices used for drawing are also supplied to picking. A wallpaper Engine owns an EGL context and render thread; each engine releases them on destruction. Cached event/cloud CPU data can be re-uploaded after GL recreation.
- `gradlew.bat assembleDebug lintDebug` passed after M2 implementation. `adb install -r app/build/outputs/apk/debug/app-debug.apk` succeeded without clearing restored app data. Samsung SM-F936B / Android 16: in-app [Whole Earth preview](evidence/m2-config.png), [Night Lights](evidence/m2-night.png), and Android [system wallpaper preview](evidence/m2-system-preview.png) rendered. The initial [Horizon preview](evidence/m2-horizon.png) was too cropped; default camera distance was corrected from 2.15 to 3.0 before the final build.
- System preview log showed an independent 904×2316 EGL context; visible interval 31 frames at 1.02 fps, mean draw 5.34 ms; after hiding, 26 frames at 0.98 fps, mean draw 5.09 ms; engine destruction logged `frames=0`. No AndroidRuntime error or EGL error appeared in the filtered log. These are short preview measurements, not battery estimates. The preview was cancelled without applying wallpaper.
- **Unexecuted M2 checks:** applying the wallpaper and testing active wallpaper + app + preview coexistence, screen off/on on an active wallpaper, multiple simultaneous active/preview engines, and repeated preview cycles. Applying would replace the personal phone's existing wallpaper; no reliable restoration path was available. The Android system apply flow is reachable, but applying and process-restart restoration are unverified. In-app Horizon after the final camera-distance adjustment still needs a recapture. No minSdk-24 device was available. The wallpaper stars remain brighter than the guide's subdued target.

## M3 — implemented; core device checks passed (2026-09-23)

- Places in Explore support a searchable ten-city offline catalog, arbitrary named globe taps, stable IDs, explicit IANA time zones or an unset state, rename/delete with immediate Undo, primary selection, fly-to, and cyan pins with globe occlusion. `PlaceStore` persists local JSON and leaves the existing discovery preferences intact. The city subset was checked against GeoNames `cities15000.zip` (CC BY 4.0); see [data credits](data-credits.md).
- `Daylight` calculates approximate apparent sunrise/sunset at NOAA's 90.833° zenith on the place's local calendar date, including the time zone's DST rules. Polar day/night and unset zones receive named states. Today and the widget use `TodayBriefing`, which uses real time, estimated Moon illumination, the primary/selected place, a deterministically rotated recent observation with its date/source/stale message, and a prompt. Today event taps resolve exact cached IDs or explain expiry.
- An `AppWidgetProvider` and per-widget configuration store implement compact and expanded RemoteViews, distinct tap intents, hourly requested updates, resize/time-zone/locale handling, and background coalesced updates from the shared repository. Expanded mode uses a single 320×180 EGL pbuffer frame with a per-hour/configuration memory cache and an honestly labelled bundled illustration fallback. Widget rendering does not require the Activity or a wallpaper.
- `gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug` passed. An initial compile failed on a shadowed `id` property in the widget config Activity; this was fixed and the rerun passed. Direct `adb install -r` for target and test APKs followed by `adb shell am instrument -w -e class com.globe.app.places.DaylightAndWidgetTest ...` passed **3 tests** without uninstalling the target app. The offscreen bitmap was nonblack and 320×180. The Bucharest 2026-09-23 sunrise/sunset test was within 10 minutes of the [independent reference](https://www.timeanddate.com/sun/romania/bucharest?month=9) (device calculated 07:02/19:13; reference 07:03/19:11). Tromsø summer/winter and unset-zone tests passed.
- Samsung SM-F936B / Android 16: [Explore](evidence/m3-explore.png), [Bucharest place actions](evidence/m3-places.png), [Today briefing](evidence/m3-today.png). Searched Bucharest, saved it, confirmed primary local daylight and action list, and confirmed the saved place survived an in-place reinstall. No AndroidRuntime error was seen during this check.
- **Launcher host checks:** Samsung launcher created an expanded widget with Bucharest and rendered the independent globe image, Moon, place, dated event, and updated time ([cropped widget capture](evidence/m3-widget-expanded.png)). Root, place, and event taps from the running app/home reached Today, Bucharest details, and the exact EONET wildfire card. A second simultaneous widget chose “No place yet”; `dumpsys appwidget` showed IDs 19 and 20 and local configuration contained `19=city:bucharest`, `20=""`. Removing widget 20 left 19 intact; removing 19 emptied the per-widget configuration. Both temporary widgets were removed from the user's launcher after testing. The first launcher render clipped the updated line; reducing the image height to 72dp fixed it before the final capture.
- **Reconfiguration follow-up:** Samsung's widget menu did not expose Settings, so an explicit accessible gear control was added to both RemoteViews layouts. On the final APK, tapping it opened `EarthWidgetConfigActivity`; changing widget ID 21 from Bucharest to “No place yet” changed its local selection and rendered line. Removing ID 21 emptied the configuration. The final `assembleDebug assembleDebugAndroidTest lintDebug` build passed, the app/test APKs installed in place, and all **10** instrumentation tests passed again.
- **Cold widget tap:** A normal process-death check used `run-as ... kill -9` after the launcher settled. One root tap launched a new app PID into Today, showing Bucharest; the widget was removed afterward. An earlier immediate tap during launcher transitions was ignored, and `am force-stop` also gave an unreliable tap, so neither is treated as the normal cold-launch result.
- **Remaining M3 checks:** Samsung exposed resize handles, but scripted drags did not change the widget size, so compact layout remains unverified on a real host. MinSdk-24 compatibility and additional DST/date-boundary references were not tested on another device. City search uses a query dialog rather than live filtering; arbitrary places accept a typed, validated IANA zone. Some new UI prose remains in Kotlin literals.

## M4 — implemented; main device paths checked (2026-09-23)

- **Plate boundaries:** Bundled `plate_boundaries_v1.json` is a simplified PB2002 dataset (2,491 points, 46 KB) generated by `tools/preprocess_plate_boundaries.py` from the Open Data Commons Attribution License 1.0 tectonicplates source. [Data credits](data-credits.md) record source, license, checksum, and preprocessing. `PlateBoundariesRenderer` geodesically subdivides segments, offsets them above Earth, and uploads once per GL context. The gold layer has a separate Layers toggle and legend. [Phone capture](evidence/m4-plates.png) shows the lines over the globe with event markers; they remained attached during manual rotation, including the Pacific seam. The bundled layer worked with the phone offline. It does not claim boundary type or a causal explanation for any individual quake.
- **Explore time and seasons:** `AppSceneClock` holds an explicit date within ±1 year, with date/time pickers, a year timeline, play/pause, hour/day steps, a visible SIMULATED label, and one-tap Now. An axial-tilt diagram, equator/tropic/polar-circle guides, and London/Sydney daylight comparisons use the existing astronomy transform. Historical/future scenes hide current event/cloud observations by default; a labelled comparison switch can restore the latest observations. Wallpaper, Today, and widget use real time. [Phone capture](evidence/m4-explore-time.png) shows the mode. After a final persistence fix, the USB phone showed `SIMULATED · 2026-09-24 00:01 EEST` both before and after an abrupt `am force-stop`; Now restored `Time · Now` and was left active.
- **Guided journeys:** Three bundled JSON journeys provide ordered scene states, native interactions, predictions, explanations for both answers, Skip/Leave, Resume/Restart, and completion. Scene snapshots restore camera, layers, and prior app time on exit. On the phone, Follow sunrise resumed step 2 after process death, accepted both incorrect and correct answers with feedback, advanced time, completed, and restored real time. Explore the Ring of Fire completed all seven steps, displayed a labelled recent Japan observation alongside its dated historical fallback, and revealed plates. Understand the seasons completed all six steps. The fallback examples stay in lesson assets and never enter the live repository.
- **Field notebook:** `FieldNotebook` migrates the eight discovery flags to stable `legacy:` IDs idempotently while retaining old preferences. It displays completed journeys and bounded locally saved observations with source, observation time, and facts. The phone still showed 8/8 earlier discoveries, all three completed journeys, and a saved USGS quake after restart ([notebook capture](evidence/m4-notebook.png)). The event Save action and quiet acknowledgment worked; the original sharing parental gate remains in place.
- **Final validation:** `gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug testDebugUnitTest` succeeded (74 tasks; unit-test task NO-SOURCE). After the widget settings addition, `assembleDebug assembleDebugAndroidTest lintDebug` succeeded again, and in-place `adb install -r` of app and test APKs succeeded. Direct instrumentation on Samsung SM-F936B / Android 16 passed **10 tests** twice in the final phase: three event parser, four companion state/migration/time, and three daylight/widget renderer tests. Direct instrumentation was used because the earlier Gradle connected-test task uninstalled the app and its local data. The final installed app retained the restored 8/8 discoveries and saved Bucharest place. A filtered recent logcat check found no `FATAL EXCEPTION`, `AndroidRuntime`, EGL error, or widget-render failure. No temporary test widget remains on the home screen.
- **Meaningful deviations and limits:** The city subset, journey examples, and plate geometry are bundled offline; current observations remain attributed public-feed data. Some newly written native UI strings remain Kotlin literals instead of resources, and `MainActivity` still coordinates substantial panel content; these are engineering debt against the guide's string/resource and thin-Activity goals. The final device did not validate active live wallpaper application/coexistence, compact widget resize, a minSdk-24 device, TalkBack, or longer battery behavior. Do not describe those paths as device-verified. The system wallpaper preview, expanded widget/reconfiguration/cold root tap, three journeys, plate rendering, persistence, and final real-time return were exercised on hardware. No store release, signing, or publication occurred.
- **Final artifact check:** A final resource-only widget icon edit was followed by `gradlew.bat assembleDebug lintDebug` (**BUILD SUCCESSFUL**) and an in-place `adb install -r` (**Success**). The widget configuration file remained empty after all temporary host tests. The final icon edit does not alter the previously tested widget action code.

## Handoff

Use the current-status section and feature-specific documents for the latest
handoff. The historical entries record milestone evidence, including remaining
checks; they do not establish full guide acceptance. Dataset provenance is in
[data credits](data-credits.md). Do not repeat completed migrations or reset
existing app data to restart the guide.

The October 12 independent voice controls passed four voice-specific tests and
the 11-case combined voice/globe/localization suite on the Motorola. Talia-only
first launch, separate persistence/routing, and silent recording failure are
covered. Both switches were visually checked in all 12 locales at normal and
130% text size; two interrupted locale/scale captures were repeated with task
protection and foreground checks. See [voiceover](voiceover.md).

## Fixed educational narration audit

The event explanation dialog previously displayed fixed text without a recording
or a narration call. The coverage update adds all four explanations, every
journey step/choice/outcome/completion, fixed challenge prompts and completion
feedback, and place-saving help. Reference reading actions cover the Guide,
seasons/plate explanations, daily facts and unlocked discovery facts. A shared
resource-script manifest defines 101 passages per language. The original 144
recordings are reused. Dialog transitions clear stale asynchronous dismissal
callbacks before starting the next lesson, and live Today feed refreshes preserve
fixed-passage playback. Exact scores remain visible, with device narration only
when separately enabled and Talia is off. See [voiceover](voiceover.md).

Validation: the shared-catalog validator passes for all 1,212 files/index entries;
all MP3s decode locally, and the original 144 recordings are unchanged. Motorola
checks passed all 13 functional/localization cases plus both opt-in learning
layout audits (all 12 languages at 100% and 130% text). App/test assembly and
Android lint passed. Voice playback is still optional and separate from TalkBack.

## Compact guided journey cards

Active lessons and quiz/reveal/completion feedback now use a capped bottom card
in portrait or a top-aligned side card in wide landscape. The globe viewport is
separate and interactive, with fixed progress/actions, a scrolling lesson body,
reading replay, an options menu and hide/resume. Assembly/lint and 19 combined
functional checks passed; both focused journey tests passed again after local
visibility assertions were added. All 12 languages passed normal/130% phone
captures, and English/German/Arabic passed 130% phone landscape and both tablet
profiles. Original state was restored and verified. See [tablet layout](tablet-layout.md)
for exact scope and [features](features.md#guided-journey-cards) for behavior.
