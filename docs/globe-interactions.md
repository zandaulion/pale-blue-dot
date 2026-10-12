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

An ordinary tap beyond Earth likewise introduces **The night sky** only when
Star Gazer is new. Later sky taps leave the current card and narration alone.
Touch and hold the sky to revisit that lesson and hear Talia again. The
discovery remains unlocked across restarts and language changes.

## Other interactions

- Short taps on event markers, saved places, the ISS, and Sun/Moon arrows retain
  their existing targeted actions.
- Find-it challenges use short taps to answer. Holding during a challenge does
  not open a lesson or submit an answer.
- The first short tap beyond Earth opens the sky card. Later short taps are quiet;
  a sky hold deliberately reopens it without triggering a second tap on release.
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
`MainActivity.onGlobeTapped` gates the generic Earth and sky lessons on their
discovery state. `onGlobeHeld` distinguishes sky from Earth: sky reopens the
stargazing card, while Earth opens its day/night lesson and save-place controls.

`GlobeInteractionTest` exercises real MotionEvents, persistent discovery state,
reopening by hold, sky tap suppression, movement/pinch/cancel suppression, and marker/place/challenge
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

The later Star Gazer follow-up passed all four `GlobeInteractionTest` cases on
the Motorola. The new case covers a first sky tap, repeated taps while open and
after dismissal, deliberate sky holds, and persistence after Activity
recreation. App/test assembly and lint passed; original device and app state
was restored and verified.
