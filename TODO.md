# Pale Blue Dot — Backlog

## Low effort
- [x] Saved-place pins — manually choose a globe point or offline city; no GPS access
- [x] Solar/lunar eclipse indicator — highlight when sun-earth-moon alignment is close
- [x] Time scrubber — slider to move time forward/backward, watching sun, moon, ISS, and shadows animate

## Medium effort
- [x] Constellations — connect existing stars with constellation lines, toggle on/off
- [x] Aurora zones — procedural glow near the poles
- [x] Earthquake/volcano markers — fetch from USGS API and NASA EONET, show as pulsing dots
- [ ] Multiple satellites — parse TLE files to show GPS, Starlink, or Hubble orbits alongside ISS
- [ ] Flight tracker — show real-time aircraft using the OpenSky API (free)

## Ambitious
- [ ] AR mode — use the phone's gyroscope to look through the camera and overlay the globe aligned to real sky
- [ ] Other celestial bodies — Mars, Europa, Titan (NASA has free texture maps)
- [x] Day/night terminator line — visible boundary line on the surface

## Recent completed work

- [x] Twelve bundled languages, Arabic RTL, and Settings language switcher.
- [x] New Moon/thin-crescent outline and localized label without artificial lighting.
- [x] Talia fixed-card narration in all 12 languages, first-launch opt-in, and Settings control.
- [x] Motorola layout audits at normal and 130% text size; Talia playback/lifecycle checks.
- [x] Tablet-width welcome/wallpaper/widget improvements; multilingual portrait, landscape, narrower-window, and phone regression checks.

## Validation and maintenance follow-ups

- [ ] Native-speaker review of full translation catalogs and narrated pronunciation.
- [ ] Full TalkBack traversal, physical tablet/foldable performance and ergonomics, and minSdk checks.
- [ ] Complete remaining wallpaper coexistence, widget-host, and long-duration checks in [progress](docs/earth-companion-progress.md).
- [ ] Extract focused UI components from MainActivity as concrete maintenance work requires.
- [ ] Prepare the next Play release: new version metadata, signing, reviewed store copy, and release checks.

Dynamic facts/scores currently use device TTS; extending Talia to other panels
requires a separate coverage decision. Speculative feature ideas above are not
commitments to add services or permissions.
