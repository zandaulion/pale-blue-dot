# Tablet and window layout

The app responds to the available window width, keeping text and controls in
readable columns while OpenGL renders at the surface's aspect ratio.

## Current behavior

- Today, Layers, Explore, Settings, and their detail panels use a 420dp side
  panel at window widths of at least 600dp, and a centered panel on narrower
  windows. Placement follows layout direction, including Arabic RTL.
- Active journey lessons and feedback use a separate compact card: below the
  globe in portrait, or a 400dp column at the logical end in landscape windows
  at least 600dp wide. The globe viewport fills the remaining space below the
  top controls and remains draggable and zoomable. Lesson text scrolls while
  progress and primary controls stay visible; hiding the card exposes Resume.
- First-launch onboarding uses a centered card capped at 560dp. It fits its
  content vertically and scrolls when the available height or larger text
  requires it. Talia/quiet choices retain 48dp minimum targets.
- Wallpaper setup uses a preview beside a 420dp settings column at widths of
  at least 840dp. Narrower windows stack preview above controls. Tablet portrait
  previews can grow to 420dp high; phone previews remain capped at 250dp.
- Wallpaper prose/footer and widget configuration cap their reading width at
  600dp and center within a wider host. The wallpaper apply button remains
  outside the settings scroll area so it stays reachable.
- Window-size changes remeasure the reading columns and reflow the wallpaper
  host. Main scene projection/picking use the actual surface dimensions.

`ui/ReadingColumn.kt` supplies the shared width bound; MainActivity owns the
welcome card and companion panels, WallpaperConfigActivity owns preview/control
arrangement, and EarthWidgetConfigActivity hosts the centered setup form. Font
sizes remain in sp; the layout adds space without enlarging every control.

## Verification method

The Motorola Edge 30 Neo is the connected physical test phone. ADB display
size/density overrides provide tablet-shaped Android windows; this validates
layout on real Android hardware but does not measure tablet-specific GPU,
battery, physical touch reach, or foldable hardware behavior. No cloud device
was used.

On October 11, 2026, the English baseline and improved full-screen audits passed
in both orientations. Visual review identified overly wide welcome/wallpaper
controls, leading to the changes above. The layout build passed these profiles;
the final French correction was rechecked as described below:

| Android display profile | Text size | Languages | Audit scope | Result |
|---|---|---|---|---|
| 800×1280dp portrait | 100% | All 12 | Full panels and setup | Passed |
| 1280×800dp landscape | 100% | All 12 | Full panels and setup | Passed |
| 800×1280dp portrait | 130% | All 12 | Focused panels and setup | Passed |
| 1280×800dp landscape | 130% | All 12 | Focused panels and setup | Passed |
| 640×800dp narrower window | 130% | English, French, Arabic | Focused panels and setup | Passed |
| Original phone display, approximately 411×914dp | 130% | All 12 | Focused panels and setup | Passed |

The full pass covers Globe, Settings, Layers, Explore, Today, Places, Journeys,
Notebook, Guide, and Explore time, plus a journey step. Both full and focused
passes include welcome top/bottom, a learning card, simulated-time controls,
language picker, wallpaper setup/framing, and widget configuration. The focused
panel subset is Globe, Settings, and Layers. No text clipping, ellipsis, checked
control overlap, or unreachable primary action was reported. Representative
screenshots were reviewed, including every locale's tablet welcome and wallpaper
setup and Arabic RTL placement.

App/instrumentation assembly and lint passed. The same installed build passed
12 functional tests: two narration, four localization, three wallpaper setup,
and three daylight/widget checks. All 144 bundled recordings remain included;
no runtime voice-service credential is packaged.

Visual review also caught a French night-card translation that incorrectly said
the dark side faces the Sun. The text and its matching Talia clip were corrected;
the final APK reran French in all six profiles and all 12 functional tests. The
other 143 audio clips were reused unchanged. Original display size/density, font
scale, rotation, locale, and app preferences were restored after completion. Captures/logs remain outside version
control. The reviewed sideload APK replaces the previous PaleBlueDot artifact in
the authorized APK folder. Physical tablet performance and touch ergonomics,
foldable hardware, and full TalkBack traversal remain separate checks.

## Compact journey follow-up — October 12, 2026

Journey lessons and inline feedback passed the following focused checks on the
Motorola, including globe/card separation, the full card staying on screen,
wrapped text and fixed controls. A tall landscape header found during visual
review led to top-aligned side cards. The final capture harness waits for locale
delivery, uses the current Activity, and explicitly holds/asserts landscape
orientation during wide-profile captures.

| Profile | Text size | Languages | Result |
|---|---|---|---|
| Original phone portrait | 100%, 130% | All 12 | Passed |
| Original phone landscape | 130% | English, German, Arabic | Passed |
| 7-inch profile, 1920×1080px / 288dpi | 130% | English, German, Arabic | Passed |
| 10-inch profile, 2560×1440px / 320dpi | 130% | English, German, Arabic | Passed |

The same production changes passed 19 functional cases, including all 18
journey steps, drag/pinch delivery through the card overlay, hide/resume, and
lesson/feedback restoration. The two focused journey tests also passed with
local visibility checks that account for offset landscape windows. All 132
focused explanation/journey/feedback captures are retained outside Git;
original preferences, locale, rotation, display overrides and font were restored
and verified. Tablet profiles validate layout on the phone, with the hardware
limits described above.

## Repeat the audit

Build/install both the app and instrumentation APK with matching signatures,
select the test phone explicitly, and record its original display overrides,
font scale, rotation, locale, and app preferences before changing them.

```bash
adb -s "$ANDROID_SERIAL" shell am instrument -w \
  -e languageLayoutAudit true -e auditTablet true \
  -e auditOutput tablet-portrait \
  -e class com.globe.app.localization.LanguageLayoutTest \
  com.zandaulion.palebluedot.test/androidx.test.runner.AndroidJUnitRunner
```

`auditTablet=true` adds learning cards, welcome bottom, wallpaper framing, and
widget setup captures, plus checks for horizontal overflow, reachable primary
buttons, and preview/action overlap. A synthetic widget ID is used for opening
configuration only; the audit does not save a widget or apply wallpaper.
`auditCompact=true` narrows the companion-panel pass for larger-text checks;
`auditTags=en-US,fr-FR,ar` limits languages. Without `auditTags`, all 12 run.
`auditOutput` provides a unique suffix for captures to avoid mixing profiles.

Captures and findings stay in app external-files storage under
`language-layout-<fontScale>-<auditOutput>`; copy evidence outside the repository.
Restore the original display, font, rotation, locale, and preferences in a
`finally` path even after failure. In-place installation and direct
instrumentation preserve the installed app; do not uninstall it to test layouts.
See [build and validation](build-release.md) and [localization](localization.md).
