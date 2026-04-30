# Features

## Visual Features

### Earth

| Feature | Detail |
|---------|--------|
| Day/night lighting | Smooth Lambertian + Fresnel terminator synced to real clock |
| City lights | NASA Black Marble texture visible on night side |
| Cloud overlay | NASA VIIRS satellite image downloaded at startup, refreshed every 6 hours; procedural fallback when offline |
| Atmosphere | Fresnel rim glow (blue halo at Earth's edge) |
| Terminator line | Amber boundary drawn at the day/night transition |
| Aurora zones | Animated green/purple glow near geomagnetic poles, visible only on night side |

### Sky

| Feature | Detail |
|---------|--------|
| Stars | 16,000 procedurally generated point sprites with spectral-class colors (O/B/A/F/G/K/M), power-law size distribution, per-star twinkle animation |
| Milky Way | 30% of stars clustered along galactic plane band |
| Sun | Billboard quad with layered glow, positioned by astronomical calculation |
| Moon | Textured sphere (NASA lunar photo) positioned by astronomical calculation |
| Constellations | 15 major constellations rendered as line-segment stick figures using real J2000 RA/Dec star positions |

**Constellations included:** Ursa Major, Ursa Minor, Cassiopeia, Cygnus, Lyra, Aquila, Pegasus, Leo, Gemini, Taurus, Scorpius, Crux, Canis Major, Centaurus, Orion.

### Overlaid Data

| Feature | Detail |
|---------|--------|
| ISS orbit | Triangle-strip ribbon at 51.6° inclination with real-time position marker; orbital period 92.68 min, RAAN precession −5°/day |
| Location pin | Cyan dot with concentric rings at user's GPS coordinates |
| Earthquakes | Pulsing point sprites for USGS M4.5+ events in the past 7 days; size scales with magnitude |
| Volcanoes | Pulsing point sprites for NASA EONET active eruptions in the past 30 days |
| Sun indicator | 2D screen-space arrow pointing toward the Sun when it is off-screen |
| Moon indicator | 2D screen-space arrow pointing toward the Moon when it is off-screen |

---

## Interactive Features

### Camera Controls

| Gesture | Action |
|---------|--------|
| Single-finger drag | Rotate globe (changes camera azimuth and elevation) |
| Pinch | Zoom in/out (camera distance 1.15 R → 20 R) |
| Scroll wheel | Zoom in/out |
| Release | Momentum / inertia continues rotation with friction factor 0.985 |

### UI Controls

| Control | Action |
|---------|--------|
| Time scrubber (SeekBar) | Offset current time ±24 hours; automatically snaps back to "now" after 1 second of release |
| Status label tap | Toggle cloud overlay on/off |
| Music button | Toggle looping ambient space audio |
| Legend button | Open scrollable overlay showing icon legend for all visual elements |
| Eclipse alert | Popup notification when solar or lunar eclipse alignment is detected |

---

## Live Data Sources

| Data | Source | Refresh | Fallback |
|------|--------|---------|----------|
| Cloud cover | NASA Worldview Snapshot API (VIIRS true-color) | 6 hours | Procedural noise texture |
| Earthquakes | USGS GeoJSON feed (M4.5+, past 7 days) | App start | Empty list |
| Volcanoes | NASA EONET API (active eruptions, past 30 days) | App start | Empty list |

All APIs are public and require no authentication key.

---

## Permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Cloud texture download, earthquake/volcano feeds |
| `ACCESS_COARSE_LOCATION` | GPS location pin (runtime-requested, optional — app works without it) |
