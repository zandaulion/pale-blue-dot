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
| Layers | Persisted cloud mode, event categories, constellations, ISS, aurora, terminator, and plate boundaries |
| Explore | Places, journeys, Field notebook, challenges, time exploration, and scene guide |
| Settings | Language, music, volume, Read aloud, Reduce motion, wallpaper, guide, and sharing |
| Event cards | Persistent until dismissed/replaced; titles, dates, sources, known scientific facts, expandable explanation, save and read-aloud actions |
| Discoveries | Eight local discoveries retained and migrated into the Field notebook |
| Challenges | Day/night and available event-category targets; feedback and a local session score |
| Journeys | Follow sunrise, Explore the Ring of Fire, Understand the seasons; native predictions, feedback, resume/restart, and scene restoration |
| Time | Temporary ±24-hour scrub, or held date within ±1 year with play/pause, steps, SIMULATED label, seasons comparison, and Now |
| Sharing | Branded on-device capture, parental gate, then Android's share sheet |

Current clouds/events are hidden by default during historical/future time
exploration; an explicitly labelled comparison can show latest observations.
The simulated clock does not retrieve historical or forecast feed data.

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

First launch asks whether to **Explore with Talia** or **Explore quietly**. Read
aloud stays available in Settings. Talia reads fixed sky/day/night/ISS cards,
Time Traveler, notebook acknowledgment, and fixed challenge feedback offline in
all 12 languages. Live event facts and changing scores use device TTS when a
suitable voice is available. Today, journeys, and other panels are not all read
automatically. Speech stops on close, disabling, or background; Talia also stops
on audio-focus loss.
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
