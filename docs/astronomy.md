# Astronomy & Orbital Mechanics

## Coordinate Systems

All positions are ultimately expressed in **Earth-Centered Earth-Fixed (ECEF)** coordinates so they can be placed on the rotating globe mesh.

```
Step 1: Julian Date (JD) from current time
Step 2: Sun/Moon → Ecliptic longitude/latitude
Step 3: Ecliptic → Equatorial (RA, Dec)   [apply obliquity of ecliptic ε ≈ 23.45°]
Step 4: Equatorial → ECEF via GMST        [Greenwich Mean Sidereal Time]
```

---

## Sun Position (`SunPosition.kt`)

Simplified educational calculation via `calculate(timeMs)`, using an explicit UTC instant. Results are calculated without a global position cache.

| Step | Formula |
|------|---------|
| Julian Date | `JD = 2440587.5 + epochMillis / 86400000` |
| Days since J2000 | `n = JD − 2451545.0` |
| Mean longitude | `L = 280.460° + 0.9856474° × n` |
| Mean anomaly | `g = 357.528° + 0.9856003° × n` |
| Ecliptic longitude | `λ = L + 1.915° sin g + 0.020° sin 2g` |
| Obliquity | `ε = 23.44°` (fixed axial tilt in the implementation) |
| Right Ascension | `RA = atan2(cos ε × sin λ, cos λ)` |
| Declination | `Dec = asin(sin ε × sin λ)` |
| GMST | `θ = 280.46061837° + 360.98564736629° × n` |
| Hour angle | `HA = θ − RA` |
| ECEF X/Y/Z | standard spherical → Cartesian |

---

## Moon Position (`MoonPosition.kt`)

Simplified educational calculation via `calculate(timeMs)`, using an explicit UTC instant. Results are calculated without a global position cache.

Uses simplified lunar elements:

| Element | Formula |
|---------|---------|
| Mean longitude | `L₀ = 218.316° + 13.176396° × n` |
| Mean anomaly | `M = 134.963° + 13.064993° × n` |
| Argument of latitude | `F = 93.272° + 13.229350° × n` |
| Ecliptic longitude | `λ = L₀ + 6.289° sin M` |
| Ecliptic latitude | `β = 5.128° sin F` |

Then follows the same ecliptic → equatorial → ECEF conversion as the sun.

---

## ISS Orbit (`ISSOrbitRenderer.kt`)

The ISS is modeled as a circular Keplerian orbit (no drag or J2 perturbation) with one empirically-derived correction: nodal regression.

| Parameter | Value |
|-----------|-------|
| Orbital period | 92.68 min |
| Semi-major axis | ~6,778 km (≈ 400 km altitude) |
| Inclination | 51.6° |
| RAAN precession | −5° / day |

**Per-frame calculation:**
```
elapsedMin  = (now − epoch) / 60000
meanAnomaly = 2π × (elapsedMin / 92.68)          // position along orbit
RAAN        = raan₀ + (elapsedDays × −5°)         // nodal regression
orbitalPos  = rotate(meanAnomaly around inclination axis)
ecefPos     = rotate(orbitalPos around polar axis by RAAN)
```

The ribbon visualizes a complete canonical orbit with a model rotation applied to the orbital plane. Its fixed parameters and reference epoch make it illustrative; no live ISS feed or updated TLE is used.

---

## Eclipse Detection (`EclipseDetector.kt`)

Eclipses are detected geometrically using the angular separation between the Sun and Moon as seen from Earth's center.

```
sunDir  = normalize(sunEcef)
moonDir = normalize(moonEcef)
dot     = dot(sunDir, moonDir)

Solar alignment (Moon on Sun side): dot > 0.9995  — bodies nearly aligned, Moon on Sun side
Lunar alignment (Moon opposite Sun): dot < −0.9995 — bodies nearly anti-aligned
Near-alignment warning: dot > 0.998 (solar) or dot < −0.998 (lunar)
```

`EclipseDetector.detect(timeMs)` returns a state during the GL frame; the Activity updates the native alignment label. These checks are approximate geometry, not precise eclipse prediction or local visibility calculations.

---

## GMST Calculation

Greenwich Mean Sidereal Time converts from inertial (equatorial) to Earth-fixed (ECEF) coordinates by giving the rotation angle of the prime meridian relative to the vernal equinox.

```
θ_GMST (degrees) = 280.46061837
                 + 360.98564736629 × n
                 + 0.000387933 × T²
                 − T³ / 38710000
```

where `n` = Julian days since J2000, `T` = Julian centuries since J2000. The simplified form (first two terms) gives ~0.1° accuracy, sufficient for the app's ~1° sun/moon precision.

---

## Scene time and phase readouts

`GlobeRenderer` snapshots its supplied `SceneClock` once per frame. Sun, Moon,
lighting, ISS, arrows, and eclipse calculations receive that same instant.
`AppSceneClock` supports the temporary time offset and held/playing Explore
time; independent wallpaper/widget clocks use real time. There is no global
10-minute Sun/Moon cache to mix simulated and current scenes.

The faint-phase guide uses `MoonGuide.illumination = (1 − dot(moon, sun)) / 2`
(clamped input). It identifies New Moon at up to 1%, keeps a full outline through
3%, and fades the guide away by 12%. It preserves the existing surface sunlight.
See [Moon visibility](moon-visibility.md).

Today and widget phase names/percentages use `kids/MoonPhase`: a synodic-age
approximation referenced to Julian date 2451550.1 and a 29.530588853-day month.
This real-time briefing estimate is separate from the direction-based scene
guide; near a threshold their rounded labels/percentages can differ. Both are
educational approximations, not an ephemeris.

Saved-place sunrise/sunset uses the NOAA approximation with a 90.833° apparent
zenith and the place's time zone/local date, including DST. Polar day/night and
an unset zone are named states. Horizon and atmospheric conditions can change
observed times. See [data credits](data-credits.md) for the source.
