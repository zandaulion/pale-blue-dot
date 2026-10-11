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
wrap; onboarding scrolls for larger text. Narration uses the app language when
a corresponding TTS voice is installed and preserves combining marks in Hindi
and Arabic. Selecting a language also refreshes existing widgets.

## Maintaining translations

Edit the default English resource and every corresponding `values-*` resource.
Keep numbered placeholders such as `%1$s` and `%2$d`, literal percent escapes
(`%%` in formatted strings), and line breaks intact. Dynamic numbers use integer
placeholders so Android can format the digits for the app locale.

Lesson text is mapped by stable journey/step IDs in `JourneyStrings.kt`; city
names are mapped by stable city IDs in `CityNames.kt`. These IDs, lesson choices,
scene coordinates, correct-answer indices, time-zone identifiers, URLs, and
saved progress must not be translated. Lesson caches are keyed by locale.

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

## Translation review status

The additional languages began as automated drafts from Google's translation
service using the public repository's app text. Navigation and astronomy
terminology, including the eight Moon-phase labels, received targeted manual
corrections. Resource coverage and placeholder checks are automated. The complete
translation catalogs have not been independently reviewed by native speakers;
that review remains appropriate before a public release.

## Validation of this change

The app and Android instrumentation tests compile successfully, and Android lint
passes. The source validator passes for all 12 locales and all 788 translatable
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
