# Features

## Earth and sky

| Feature | Behavior |
|---|---|
| Earth | Drag, pinch, momentum, gentle idle rotation, and a remembered viewpoint |
| Lighting | Clock-based sunlight, city lights, atmosphere, and optional amber terminator |
| Clouds | Off, Generated, or Satellite; satellite imagery is dated, cached, and approximate |
| Sky | 16,000 procedural stars, a Milky Way band, and 15 catalog-based constellations |
| Sun and Moon | Simplified astronomical positions driven by the scene clock |
| Faint Moon guide | Neutral 1dp outline: full below 3% illumination, fades to zero at 12%; localized New Moon label at up to 1%, otherwise Thin crescent |
| Sun/Moon arrows | Bring either body into view; the Moon camera framing lifts its disk above Earth |
| ISS | Tappable illustrative orbit and marker, using a simplified orbit rather than a live feed |
| Auroras | Optional procedural polar glow, not a forecast |
| Eclipse alerts | Approximate Sun/Moon alignment checks, not precise local eclipse predictions |
| Latitude guides | Equator, tropics and polar circles share a persisted Layers switch; independent of simulated time and journey steps |
| Plate boundaries | Offline PB2002-derived layer, optional in Layers |
| Places | Occluded pins for manually saved points and city-catalog selections; no GPS access |

The outline identifies an unlit silhouette without brightening the Moon's
surface. The native label avoids other controls and hides while another panel
is open or the Moon is occluded/off-screen. See [Moon visibility](moon-visibility.md).

Constellations: Ursa Major, Ursa Minor, Cassiopeia, Cygnus, Lyra, Aquila, Pegasus,
Leo, Gemini, Taurus, Scorpius, Crux, Canis Major, Centaurus, and Orion.

## Navigation and learning

| Entry point | Behavior |
|---|---|
| Today | Real-time Moon/daylight briefing, attributed recent event, and short prompt; opens on demand |
| Layers | Persisted cloud mode, event categories, constellations, ISS, aurora, terminator, latitude guides, and plate boundaries |
| Explore | Places, journeys, Field notebook, challenges, time exploration, and scene guide |
| Settings | Language, music, volume, Talia voice, Android voice, Reduce motion, wallpaper, guide, and sharing |
| Day/night lessons | First discovery opens once; touch and hold Earth to revisit or save a point; repeated ordinary taps stay quiet |
| Night-sky lesson | First sky tap opens once; touch and hold the sky to revisit; repeated ordinary taps stay quiet |
| Event cards | Persistent until dismissed/replaced; titles, dates, sources, known scientific facts, expandable explanation, save and read-aloud actions |
| Discoveries | Eight local discoveries retained and migrated into the Field notebook |
| Challenges | Day/night and available event-category targets; feedback and a local session score |
| Journeys | Follow sunrise, Explore the Ring of Fire, Understand the seasons; native predictions, feedback, resume/restart, and scene restoration |
| Time | Temporary ±24-hour scrub, or held date within ±1 year with play/pause, steps, SIMULATED label, seasons comparison, and Now |
| Sharing | Branded on-device capture, parental gate, then Android's share sheet |

Current clouds/events are hidden by default during historical/future time
exploration; an explicitly labelled comparison can show latest observations.
The simulated clock does not retrieve historical or forecast feed data.

See [globe interactions](globe-interactions.md) for tap/hold behavior and gesture checks.

## Language, accessibility, and audio

The 12 bundled locales are `en-US`, `de-DE`, `es-ES`, `fr-FR`, `ar`, `hi-IN`,
`ja-JP`, `ko-KR`, `pt-BR`, `ro`, `uk`, and `zh-CN`. Settings → Language overrides
the device preference, remembers the choice, and offers a return to device
language. Arabic uses RTL. Interface, lessons, city names, widget, and wallpaper
settings are translated; public feed titles and saved user text keep their
original content. See [localization and review status](localization.md).

Scrollable panels and wrapping labels support larger text. Onboarding and configuration forms use bounded reading widths; wide wallpaper windows place the preview beside controls. See [tablet layout](tablet-layout.md). Native event lists
provide an alternative to selecting globe pixels. Reduce motion suppresses
decorative motion. Full TalkBack traversal remains an outstanding acceptance
check; narration is not a substitute for it.

First launch asks whether to **Explore with Talia** (Talia only) or **Explore
quietly** (both voices off). Settings provides independent Talia voice and Android
voice switches. Talia reads fixed sky/day/night/ISS cards, event explanations,
all guided journey steps and feedback, place-saving instructions, and challenge
prompts/feedback offline in all 12 languages. Guide entries, seasons, plate
explanations, daily facts and unlocked notebook discoveries have Read aloud
actions. Live event facts use device TTS only after Android voice opt-in. Positive
challenge scores stay on screen; Talia gives a fixed congratulation, or Android
reads the exact score when Talia is off. Failed recordings stay silent. Speech
stops on close, disabling, background or audio-focus loss. New dialog/lesson
playback survives the previous dialog's dismissal.
Music is separately optional, with its own volume slider. See [voiceover](voiceover.md).

## Home screen and data

Earth Today widgets have per-instance place selection and responsive layouts.
Live wallpaper offers Whole Earth, Night Lights, and Earth's Horizon with saved
viewpoints and framing. These surfaces use real time and remain silent.

Public NASA/USGS feeds cover clouds, M4.5+ earthquakes from the past week, open
volcanoes, wildfires filtered to ten days, and severe storms filtered to seven
days. Shared caches preserve dated observations when refresh fails; fresh empty
feeds and unavailable/stale states remain distinct. See [live data](live-data.md).

The app requests only `INTERNET`. There are no accounts, ads, tracking SDKs, or
location/microphone permissions. See [privacy](../PRIVACY.md).

## Guided journey cards

Journey lessons use a compact bottom card on portrait screens and a side card
on wide landscape screens. The OpenGL viewport occupies separate space, so
Earth remains visible and supports dragging and pinch zoom. Time/Settings and
system/navigation bars have their own space. Journey overlays have no full-screen
scrim and do not intercept touches outside the card. Ordinary globe taps/holds
do not replace the current lesson; Sun/Moon direction controls remain available.

The card keeps its progress header and primary action visible while longer
lesson text, answer choices and dated sources scroll. The play control replays
Talia. The options menu contains Skip and Leave journey. Hiding the card exposes
a Resume button without changing the current step or globe pose. Quiz feedback,
time-advance explanations, plate reveals and completion messages use the same
card. Lesson and feedback state survive Activity recreation; leaving or finishing
a journey restores the captured previous globe scene.
