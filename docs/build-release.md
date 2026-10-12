# Build, validation, and release

## Toolchain and variants

The checked-in configuration is authoritative:

| Setting | Value |
|---|---|
| Android Gradle Plugin | 9.4.1 |
| Gradle wrapper | 9.6.0 |
| Kotlin plugin | 2.2.10 |
| Gradle daemon JVM | JetBrains JDK 21 via `gradle/gradle-daemon-jvm.properties` |
| Java/Kotlin bytecode | 17 |
| compileSdk / targetSdk | 36 |
| minSdk | 24 |
| Application ID / namespace | `com.zandaulion.palebluedot` / `com.globe.app` |
| Version metadata | 3.10 / 12 |

Use Android Studio compatible with the configured AGP and install SDK Platform
36. Set the local SDK path through Android Studio or an uncommitted
`local.properties`. Initial setup may download the configured JDK, wrapper,
and dependencies. Android API level alone does not guarantee graphics support;
the manifest explicitly requires OpenGL ES 3.0.

Debug builds are debuggable, use debug signing, and do not shrink code. Release
builds enable R8 minification and resource shrinking with the checked-in
`proguard-rules.pro`. **There is no release signing configuration in the current
Gradle file.** A release task by itself does not produce a production-signed app.

Runtime dependencies are AndroidX core-ktx 1.15.0 and appcompat 1.7.0; instrumented
tests use AndroidX runner 1.6.2 and ext:junit 1.2.1. Rendering, network access,
MediaPlayer, and device TTS use platform/JDK APIs. There is no ElevenLabs SDK or
runtime API credential. All translations and Talia recordings are included;
AAB language splits are disabled for offline language switching.

## Build commands

From the repository root on macOS/Linux (use `gradlew.bat` on Windows):

```bash
./gradlew assembleDebug
./gradlew assembleDebugAndroidTest lintDebug
```

Outputs:

- App: `app/build/outputs/apk/debug/app-debug.apk`
- Instrumentation: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

```bash
./gradlew assembleRelease
./gradlew bundleRelease
```

Release outputs are under `app/build/outputs/apk/release/` and
`app/build/outputs/bundle/release/`; configure authorized signing separately.
Existing bundled narration needs no key or regeneration to build. To change
spoken text, use [the narration generator](voiceover.md#generating-recordings).

On an aarch64 development host where Android build tooling cannot execute, use
an x86_64 Android build host with an isolated checkout containing the exact
reviewed sources and assets. Keep SDK paths, host addresses, credentials, and
private signing material out of version control.

## Device tests without clearing existing data

Select the intended device explicitly. Updating an installed app requires the
same signing certificate; sign the app and test APK consistently before testing.
Do not uninstall an existing app to work around a certificate mismatch.

```bash
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$ANDROID_SERIAL" shell am instrument -w \
  -e class com.globe.app.kids.TaliaNarrationTest,com.globe.app.localization.LocalizationTest \
  com.zandaulion.palebluedot.test/androidx.test.runner.AndroidJUnitRunner
```

`ANDROID_SERIAL` is a locally chosen device selector, not a value to commit.
Use direct instrumentation on a device with data to preserve: an earlier Gradle
connected-test run removed the target app and its local data. Opt-in
[language layout](localization.md) and [Moon scene](moon-visibility.md) audits
require their documented arguments and restore changed preferences. Keep
screenshots, logs, and device details outside the repository.

Other instrumented checks cover event parsing, companion state/migration,
daylight/widget rendering, wallpaper setup, and Moon geometry. There is no
`app/src/test` JVM test suite; a Gradle `NO-SOURCE` result is not a test pass.

## Current validation and artifact status

The October 11 Talia build passed app/instrumentation assembly and lint. On the
Motorola Edge 30 Neo, two Talia and four localization tests passed. All 12
languages passed focused onboarding/Settings audits at 100% and 130% text size;
all 144 audio files decoded and Android metadata checks found playable audio.
See [voiceover](voiceover.md) for coverage and limitations.

The tablet-layout follow-up passed assembly/lint and 12 functional tests on the
Motorola. All 12 languages passed tablet-shaped portrait/landscape audits at
100% and focused audits at 130%, plus a 130% phone regression. A narrower
640×800dp profile passed English, French, and Arabic. These are virtual Android
window sizes on the physical phone; actual tablet performance remains untested.
See [tablet layout](tablet-layout.md) for the exact scope and restoration.

The October 12 globe-interaction update passed assembly/lint, nine functional
Motorola tests, and onboarding/Settings layouts for all 12 languages at 100%
and 130% text size. See [globe interactions](globe-interactions.md) for once-only
discoveries, deliberate holds, fixture isolation, and restoration checks.

The October 12 compact-journey follow-up passed assembly/lint and 19 functional
cases. Journey-specific visibility/gesture checks passed again after capture
harness corrections. All 12 languages passed normal/130% phone layout checks;
English, German and Arabic passed 130% phone landscape and 7-/10-inch landscape
profiles. Capture orientation, complete card visibility and original device
restoration are verified. See [tablet layout](tablet-layout.md#compact-journey-follow-up--october-12-2026).

The reviewed APK was installed as an update and saved in `~/Projects/APKs` on
the build host. Only the latest **PaleBlueDot** APK is retained there; other apps'
artifacts remain untouched. This is a signed sideload test build, not evidence
of Play production signing or publication. Version metadata remains 3.10/code 12.

For future authorized artifact replacement, verify build success, checksum,
signing identity, package/version, bundled audio, and absence of credentials.
Copy the final **app APK**, verify it in the destination, then remove older APKs
belonging to this app only. Never publish the instrumentation APK as the app.

## Release preparation

1. Review source/asset changes, translation and pronunciation quality, privacy,
   and the outstanding device checks in [progress](earth-companion-progress.md).
   Keep the separately hosted Play privacy policy consistent with [PRIVACY.md](../PRIVACY.md),
   provide a privacy link or notice within the app, and review Data safety
   disclosures against the version being distributed. Updating the repository
   document does not update the hosted policy or Play Console.
2. Run `python3 tools/check_localizations.py`, relevant Android checks, and lint;
   verify recording indexes still match localized resources.
3. Increment versionCode/versionName when preparing an actual new release. Use
   [the next-release draft](../store/release-notes/unreleased.md); historical
   release notes remain unchanged.
4. Sign with the intended distribution identity using private local signing
   material; inspect the exact outgoing artifacts and metadata before publishing.
5. Update Play listing/audience declarations and distribute only when authorized.

Building, generating audio, installing a test build, saving an APK, committing
source, and publishing to Play are separate actions. The current Talia changes
have not been published to Play.

## Store artwork

`generate_icons.py` creates launcher density variants from `play_store_icon.png`.
`tools/make_store_screenshots.py` and `tools/make_feature_graphic.py` prepare store
artwork. Use synthetic, non-identifying content for any publicly shared fixture
or capture. The [store screenshot workflow](../store/README.md#screenshots) covers
the eight current English images for each phone, 7-inch and 10-inch tablet slot,
the opt-in native capture harness, and restoring test-device settings. Store copy
and existing release history also live in [store](../store/README.md).

### Fixed educational narration coverage

Before packaging edited educational text, generate any changed passages and
run `python3 tools/check_narration.py`. The validator compares all 12 indexes
and MP3 sets with the shared resource-script catalog, without API calls or a
key. `TaliaNarrationTest` also verifies Android resource resolution, decodable
audio for every catalog entry, real event explanation playback, replay/close,
journey continuation, reference reading actions and voice independence.
The focused [learning layout audit](localization.md) checks new dialog/lesson
controls at normal and large text sizes. Keep the exact score and live values
dynamic; use a complete fixed Talia passage rather than splicing words/numbers
or substituting device speech.
