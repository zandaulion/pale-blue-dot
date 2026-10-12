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
coverage. Existing store screenshots predate these additions and should be
reviewed before the next upload.

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
