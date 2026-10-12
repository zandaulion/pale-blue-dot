# Store assets

Play Store listing materials for Pale Blue Dot
(https://play.google.com/store/apps/details?id=com.zandaulion.palebluedot).

## Release notes

One file per release in `release-notes/`, named `<versionName>.txt`, in the
Play Console paste format:

```
<en-US>
Notes for this release (max 500 characters)
</en-US>
```

Historical numbered files describe their original releases. Keep them unchanged.
[unreleased.md](release-notes/unreleased.md) is the next-release draft; choose a
new versionName/versionCode and create its numbered notes during actual release
preparation. Current version metadata is still 3.10/code 12.

Add a `<locale>` block per language when preparing translated Play metadata.
The app's 12 bundled languages do not automatically localize the store listing;
`listing.md` and the next-release notes are currently English drafts.

## Store listing

`listing.md` holds the title, short description, and full description
(en-US) to paste into Play Console. Update it whenever the app's
positioning changes. The current draft includes the language picker, faint-Moon
guide, and Talia opt-in. These additions have been tested in a sideload build;
the narration has not been published to Play. See [build/release](../docs/build-release.md)
for signing and release checks, and [voiceover](../docs/voiceover.md) for exact
coverage. The refreshed English screenshot sets below show the current sideload
build, including its language and independent voice settings and touch-and-hold lessons.

| versionName | versionCode | Highlights |
|---|---|---|
| 3.1 | 3 | Last release before repo housekeeping |
| 3.2 | 4 | Share-to-social, package identity fix |
| 3.3 | 5 | Fly-to-location, idle auto-rotation, haptics, persistence, scrubber flicker fix |
| 3.4 | 6 | Tap-to-identify markers, wildfire + storm layers, seamless UI buttons |
| 3.5 | 7 | Teacher Approved kids update: tap-to-learn, discovery journal, Today panel, challenges, onboarding, read-aloud; location removed, parental-gated sharing |
| 3.6 | 8 | Large-screen support, legend close (X), resource shrinking (uploaded early / superseded by 3.7) |
| 3.7 | 9 | Tappable ISS/Sun/Moon, working music + volume slider, cloud cycling (off default), volcano fetch fix, large-screen support, legend close (X), resource shrinking |
| 3.8 | 10 | Target Android 16 (API 36) to satisfy Play's target-API policy |
| 3.9 | 11 | Earth Today widget, live wallpaper (parallax, Material You colors), saved places with a 303-city offline catalog and time-zone suggestions, guided journeys, Field notebook, plate boundaries, explore time/seasons; lower texture memory for widget and wallpaper |
| 3.10 | 12 | Full widget Earth background with readable overlays and responsive layouts; wallpaper location selection from saved places or city search, remembered viewpoints, expandable framing controls, and an always-visible preview button |

## Screenshots

Eight English screenshots per Play Console device slot, regenerated on 2026-10-12:

| Upload slot | Folder | Dimensions | Presentation |
|---|---|---|---|
| Phone | [screenshots/phone](screenshots/phone/) | 1080 × 1920 | Native portrait captures with short captions |
| 7-inch tablet | [screenshots/tablet-7](screenshots/tablet-7/) | 1920 × 1080 | Native landscape app UI |
| 10-inch tablet | [screenshots/tablet-10](screenshots/tablet-10/) | 2560 × 1440 | Native landscape app UI |

Upload the eight numbered PNGs from each folder to its matching slot, in filename
order. All are RGB (24-bit) PNGs without alpha or metadata. The tablet images
contain no marketing captions or frames, following the
[Play preview-asset guidance](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en).
These are English listing assets; the app's other 11 languages need separate
localized listing captures if desired.

The sequence shows Earth, an event card, a day/night lesson, seasons, the unlit
Moon guide, language and Talia settings, live wallpaper setup, and Today. The
event is explicitly labelled “Example earthquake” / “Sample data · illustration”;
“Sample place” is fictional. Today may include public observations from the
app's normal feeds. No personal saved places, notebook records, notifications,
accounts, device identifiers, or infrastructure details are included.

### Reproduce

Captures run on the authorized Android test phone with these logical display
profiles. Tablet sets demonstrate native tablet layouts using display overrides;
they are not captures from physical 7-inch or 10-inch tablets.

| Capture profile | Display pixels | Density | Approximate window dp |
|---|---|---|---|
| phone | 1080 × 2400 | 420 dpi | 411 × 914 |
| tablet-7 | 1920 × 1080 | 288 dpi | 1067 × 600 |
| tablet-10 | 2560 × 1440 | 320 dpi | 1280 × 720 |

1. Build/install the current app and its instrumentation APK using the
   [build workflow](../docs/build-release.md). Preserve existing data with an
   in-place install.
2. Back up the app preferences and the device's display size, density, rotation,
   font scale, app locale, and the system `immersive_mode_confirmations` setting
   outside the repository. Temporarily set that setting to `confirmed` to suppress
   Android’s first-use full-screen hint during captures. Set font scale to 1.0,
   automatic rotation off, rotation 0, and the profile's display size/density.
3. For each profile run the opt-in capture test, replacing `PROFILE` with the
   table's profile name:

   ```sh
   adb shell am instrument -w \
     -e storeScreenshots true -e storeProfile PROFILE \
     -e class com.globe.app.store.StoreScreenshotTest \
     com.zandaulion.palebluedot.test/androidx.test.runner.AndroidJUnitRunner
   ```

   The harness uses English and a fixed UTC display timezone, temporarily hides
   system bars, protects its task against navigation interruptions, seeds only
   fictional place/event fixtures, checks visible text layout, and restores
   preferences, locale and process timezone in `finally`. Capture tests are opt-in
   and do nothing in the normal instrumentation suite.
4. Pull `Android/data/com.zandaulion.palebluedot/files/store-screenshots/PROFILE`
   into a private `RAW_ROOT/PROFILE` directory outside the Git checkout. Always
   restore the original display/rotation/font/full-screen-hint settings and
   backed-up preferences in an outer `finally`, including on a failed capture.
   Never uninstall or run
   `pm clear` to prepare a screenshot.
5. Compose and review the complete set:

   ```sh
   python tools/make_store_screenshots.py RAW_ROOT
   ```

   The composer requires Python 3 and Pillow. Font discovery supports Android
   Studio on Windows and common DejaVu font
   locations on Linux. Other hosts can pass `--font-bold` and `--font-regular`.
   The phone composer scales the entire capture proportionally under the caption;
   tablet captures are exported at their original resolution.
6. Inspect every image for clipping, overlays and private content, and verify
   dimensions, RGB mode and absence of metadata before upload or a GitHub write.
   Keep raw captures, preference backups, logs and contact sheets outside Git.
