# App localization

Pale Blue Dot bundles US English, German (Germany), Spanish (Spain), French
(France), Arabic, Hindi (India), Japanese, Korean, Brazilian Portuguese,
Romanian, Ukrainian, and Simplified Chinese (China).

The app follows the device language by default. Choose **Settings → Language**
to select a language or return to the device preference. The button shows the
current choice; selecting another language applies it immediately and keeps
Settings open. The choice persists across app restarts. Android 13 and newer
also expose the supported languages in the system's app settings.

## Coverage

The interface, accessibility descriptions, discoveries, challenges, daily facts,
Moon phases, all three guided journeys, city catalog, wallpaper setup, widget
configuration, widget text, and share caption use Android string resources.
Translations are bundled and require no network access at runtime.

US English is the complete fallback in `app/src/main/res/values/strings.xml`.
The app name remains **Pale Blue Dot**. Public observation titles and attribution
from NASA/USGS remain exactly as supplied; these remote feeds are not translated.
Saved place names and earlier saved observations remain user data and are not
rewritten when changing language. City search accepts both the original catalog
name and its localized spelling, with accent-insensitive matching.

Arabic uses right-to-left layout. Navigation labels and wallpaper buttons can
wrap; onboarding scrolls for larger text. Talia's fixed-card recordings follow
the app language and need no installed TTS voice. Changing facts and scores use
the app locale with device TTS when a corresponding voice is available. Speech
cleanup preserves combining marks in Hindi and Arabic. Selecting a language
also refreshes existing widgets. See [voiceover](voiceover.md) for exact coverage.

| Locale tag | Android resource directory |
|---|---|
| `en-US` | `values` (complete fallback) |
| `de-DE` | `values-de-rDE` |
| `es-ES` | `values-es-rES` |
| `fr-FR` | `values-fr-rFR` |
| `ar` | `values-ar` |
| `hi-IN` | `values-hi-rIN` |
| `ja-JP` | `values-ja-rJP` |
| `ko-KR` | `values-ko-rKR` |
| `pt-BR` | `values-pt-rBR` |
| `ro` | `values-ro` |
| `uk` | `values-uk` |
| `zh-CN` | `values-zh-rCN` |

`AppLanguage.kt` resolves regional variants to the supported language; unsupported
device languages fall back to US English. Android 13+ uses the system app locale;
older versions restore the saved preference through AppCompat. The manifest's
locale configuration declares all 12 tags. AAB language splitting is disabled
so switching languages also works offline after a Play installation.

## Maintaining translations

Edit the default English resource and every corresponding `values-*` resource.
Keep numbered placeholders such as `%1$s` and `%2$d`, literal percent escapes
(`%%` in formatted strings), and line breaks intact. Dynamic numbers use integer
placeholders so Android can format the digits for the app locale.

Lesson text is mapped by stable journey/step IDs in `JourneyStrings.kt`; city
names are mapped by stable city IDs in `CityNames.kt`. These IDs, lesson choices,
scene coordinates, correct-answer indices, time-zone identifiers, URLs, and
saved progress must not be translated. Lesson caches are keyed by locale.

When editing narrated text, update the corresponding Talia recordings and
indexes with [the generator](voiceover.md#generating-recordings). Otherwise a
changed card will no longer match its recording and will use device TTS.

Run the source checks from the repository root:

```bash
python3 tools/check_localizations.py
```

Compile the app and instrumentation tests, and run Android lint:

```bash
./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug
```

`LocalizationTest` checks label resolution for all 12 locales, Arabic layout
direction, lesson-cache invalidation without changes to scene/progress IDs,
localized city search, and preservation of narration marks. Run it on an Android
device with the other instrumentation tests before a release. Also visually
check large text, narrow screens, Arabic mixed-script observation cards,
widgets, and the installed TTS voices.

`LanguageLayoutTest` provides an opt-in physical-device layout audit on Android
13 and newer. Install the app and its instrumentation APK, select the intended
test device with `ANDROID_SERIAL`, then run:

```bash
adb -s "$ANDROID_SERIAL" shell am instrument -w \
  -e languageLayoutAudit true \
  -e class com.globe.app.localization.LanguageLayoutTest \
  com.zandaulion.palebluedot.test/androidx.test.runner.AndroidJUnitRunner
```

The audit captures onboarding, the globe, Settings, Layers, Explore, Today,
Places, Journeys, Notebook, Guide, time exploration, a lesson step, simulated
time, the language picker, and wallpaper setup in all 12 languages. It checks
text dimensions, ellipsized content, and intersecting time/Settings controls.
Captures wait for a committed UI frame. Screenshots and a findings file stay in
the app's external-files directory under `language-layout-<fontScale>`; keep
device captures outside the repository. The original app language is restored.

For a focused larger-text pass, add `-e auditCompact true`, temporarily set the
chosen device's `font_scale` to `1.3`, and restore its original value afterward.
This checks onboarding, the globe, Settings, Layers, simulated time, the picker,
and wallpaper setup. `-e auditTags en-US,fr-FR` limits an audit to selected tags.
The test remains inactive during ordinary instrumentation runs.

For the Talia onboarding and Settings screens specifically, add
`-e auditNarrationOnly true -e auditCompact true`. This captures the top and bottom
of each scrollable screen. Run at both normal and larger text sizes.

For tablet-shaped windows, `-e auditTablet true` adds configuration/card captures and action/preview bounds checks; `-e auditOutput tablet-portrait` isolates evidence per profile. See [tablet layout](tablet-layout.md) for the matrix and restoration procedure.

## Translation review status

The additional languages began as automated drafts from Google's translation
service using the public repository's app text. Navigation and astronomy
terminology, including the eight Moon-phase labels, received targeted manual
corrections. Resource coverage and placeholder checks are automated. The complete
translation catalogs have not been independently reviewed by native speakers;
that review remains appropriate before a public release.

## Validation of this change

The app and Android instrumentation tests compile successfully, and Android lint
passes. The source validator passes for all 12 locales and all 795 translatable
resources. The lesson mappings cover all 71 text fields, and the city mappings
cover all 303 catalog entries. JVM narration checks pass for Hindi, Arabic,
Japanese, Korean, Romanian, and Ukrainian, including combining marks and
whitespace cleanup. A physical-device normal-size audit passed for all 12
languages, and all four `LocalizationTest` cases passed on Android 14. The device
audit identified welcome content drawn beneath globe controls and cramped
French time/Settings labels with larger text. Onboarding now covers those
controls, and time, Settings, and challenge prompts share a layout that reserves
space and wraps text instead of positioning those elements independently.
The final focused audit also passes in all 12 languages at 130% font size,
including measurements and screenshots taken after a committed UI frame. The
device's original font setting was restored after testing. These checks cover
the listed screens; they do not replace testing other screen sizes, installed
TTS voices, or widgets placed in a launcher.

The October 11 Talia update added four translated onboarding/Settings strings.
All four `LocalizationTest` cases and both `TaliaNarrationTest` cases passed on
the Motorola Edge 30 Neo. The focused narration layout audit passed in all 12
languages at 100% and 130% text size. The larger-text pass required a retry of
Romanian, Ukrainian, and Chinese after the app lost foreground focus; the retry
reported no clipping or overlap. Pronunciation quality still requires listening
and native-speaker review; layout and playback checks do not establish it.

The separate Talia/Android voice controls add three translated resources (795
total). Their October 12 Motorola audit covered all 12 languages at 100% and
130% text size. Korean at 100% and Spanish at 130% were repeated after system
Recents interrupted captures. The opt-in `-e auditPinTask true` protects the Main
activity's task; captures now require foreground window focus. Task pinning is
released before cleanup, allowing the system unlock transition to finish.

The fixed-learning narration catalog uses the same translated resources as the
cards and lessons. The challenge-completion voice message adds one translated
resource (796 per locale). `TaliaNarrationTest` compares all 101 scripts in each
language with Android's actual resource values, including every journey step,
choice, outcome, daily fact and discovery. The opt-in
`LearningNarrationLayoutTest` captures event explanations, journey choices and
feedback with the Talia reading actions visible:

```bash
adb -s <serial> shell am instrument -w \
  -e learningNarrationAudit true \
  -e class com.globe.app.kids.LearningNarrationLayoutTest \
  com.zandaulion.palebluedot.test/androidx.test.runner.AndroidJUnitRunner
```

Run at normal and 130% font size, retaining captures outside Git and restoring
font, app locale and preference state afterwards.

The fixed-learning audit passed on the Motorola Edge 30 Neo at 100% and 130%
text size in all 12 languages. All 96 explanation/journey/feedback captures
passed clipping checks; the reviewed dialog and lesson layouts showed readable
wrapped text and no overlap. Original app preferences, locale, font and display
settings were restored and verified. This is a layout check, not a native-speaker
translation/pronunciation review.

Compact journey cards add two translated menu labels, bringing current coverage
to 798 resources per language. The same opt-in learning audit checks that the
globe and journey card occupy separate rectangles, with at least a quarter of
the available height reserved for the globe. `-e auditTags en-US,de-DE,ar` selects
representative long text and RTL layouts for landscape/tablet profiles; omitting
it checks all 12 languages. `JourneyLayoutTest` separately covers all 18 lesson
steps, fixed footer visibility, actual globe drag/pinch delivery, hide/resume,
and lesson/feedback restoration across Activity recreation.

The capture harness waits for the requested locale and rebuilds the Activity's
views before testing. Each capture resolves the current Activity through
`ActivityScenario`, avoiding stale view/text references after language changes.
Journey checks include the full card rectangle within the screen, as well as
globe/card separation and text measurements.

The compact-card follow-up passed all 12 locales at 100% and 130% on the phone.
English, German and Arabic also passed at 130% in actual phone landscape and
both tablet landscape profiles, with `-e auditLandscape true` holding and
asserting landscape orientation. See [tablet layout](tablet-layout.md) for
profiles, capture scope and hardware limits.
