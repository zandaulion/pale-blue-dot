# Globe taps and holds

An ordinary tap on bare Earth introduces the daytime or night lesson only if
that discovery is new. Each lesson appears once, using the existing persisted
`DiscoveryJournal` flags. Tapping other points on the same side, restarting the
app, or changing language does not repeat an already discovered lesson. Existing
installs with those discoveries already unlocked become quiet immediately.

Touch and hold a point on Earth to reopen its current day/night lesson at any
time. The card still offers **+ Save this place** for that point. The scene clock
determines day/night, including during Explore time. A repeated ordinary tap
leaves an open card and its narration alone; dismissing it keeps subsequent taps
quiet. Deliberately holding can reopen and read the lesson again.

## Other interactions

- Short taps on event markers, saved places, the ISS, and Sun/Moon arrows retain
  their existing targeted actions.
- Find-it challenges use short taps to answer. Holding during a challenge does
  not open a lesson or submit an answer.
- A short tap beyond Earth retains the sky card. Holding beyond Earth does not
  open a day/night card or turn into a short tap when released.
- Drag to rotate and pinch to zoom. Movement beyond Android's touch slop or a
  second finger cancels the pending hold. Small movement within tap slop does
  not rotate the camera or impart momentum. Cancelled touches open no lesson.

The onboarding hint and **Explore → Guide** explain discovery/revisiting in all
12 languages. **Explore → Places → Add a place from the globe** explains holding
before saving. These instructions are not recorded Talia cards; existing audio
clips/indexes remain unchanged.

## Implementation

`GlobeSurfaceView` uses Android `GestureDetector` for the system long-press
threshold, alongside the existing scale detector and short-tap callback. A hold
consumes the short-tap candidate; callbacks require the app window to have focus.
`MainActivity.onGlobeTapped` gates only the generic Earth lesson on discovery
state. `onGlobeHeld` explicitly inspects an Earth point, and `showDayNightCard`
uses the same existing localized lesson and save-place controls.

`GlobeInteractionTest` exercises real MotionEvents, persistent discovery state,
reopening by hold, movement/pinch/cancel suppression, and marker/place/challenge
actions with clearly synthetic fixtures stored in the instrumentation package.
See [build and validation](build-release.md) for test commands. On October 12, 2026, the Motorola Edge 30 Neo passed all three interaction
tests, two Talia tests, and four localization tests (nine total). Native
touchscreen taps/holds verified first discoveries, repeated quiet taps,
intentional reopening, saved-point actions, and behavior after Activity
recreation. Jitter, drag, pinch, and cancellation checks also passed. The marker
fixtures are isolated from live feed refreshes.

All 12 languages passed onboarding/Settings layout audits at 100% and 130% text
size, with no reported clipping or overlap. App/test assembly and lint passed;
all 144 narration indexes remain synchronized without regenerating audio.
Original font, locale, and six app preference files were restored and verified.
The reviewed sideload APK was installed in place and saved as the latest
PaleBlueDot artifact. Captures and preference backups stay outside version
control. Full TalkBack traversal remains a separate check.
