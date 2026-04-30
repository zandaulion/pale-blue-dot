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

Precision: ~1°. Results are cached for 10 minutes.

| Step | Formula |
|------|---------|
| Julian Date | `JD = 2440587.5 + epochMillis / 86400000` |
| Days since J2000 | `n = JD − 2451545.0` |
| Mean longitude | `L = 280.460° + 0.9856474° × n` |
| Mean anomaly | `g = 357.528° + 0.9856003° × n` |
| Ecliptic longitude | `λ = L + 1.915° sin g + 0.020° sin 2g` |
| Obliquity | `ε = 23.439° − 0.0000004° × n` |
| Right Ascension | `RA = atan2(cos ε × sin λ, cos λ)` |
| Declination | `Dec = asin(sin ε × sin λ)` |
| GMST | `θ = 280.46061837° + 360.98564736629° × n` |
| Hour angle | `HA = θ − RA` |
| ECEF X/Y/Z | standard spherical → Cartesian |

---

## Moon Position (`MoonPosition.kt`)

Precision: ~1°. Results are cached for 10 minutes.

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

The ribbon visualizes one complete orbit (360 sample points) with the RAAN rotation applied to the entire orbital plane.

---

## Eclipse Detection (`EclipseDetector.kt`)

Eclipses are detected geometrically using the angular separation between the Sun and Moon as seen from Earth's center.

```
sunDir  = normalize(sunEcef)
moonDir = normalize(moonEcef)
dot     = dot(sunDir, moonDir)

Solar eclipse  (Moon between Sun & Earth): dot > 0.9998  — bodies nearly aligned, Moon on Sun side
Lunar eclipse  (Earth between Sun & Moon): dot < −0.9994 — bodies nearly anti-aligned
Near-eclipse warning: slightly relaxed thresholds
```

When an eclipse is detected, `EclipseDetector` calls a listener on the main thread to display an alert overlay.

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

## Caching Strategy

| Calculator | Cache duration | Reason |
|------------|---------------|--------|
| SunPosition | 10 minutes | Changes ~0.17°/min; imperceptible within window |
| MoonPosition | 10 minutes | Moves ~0.009°/min; imperceptible within window |
| ISS position | Per-frame | High angular velocity — must update continuously |
| EclipseDetector | Per-frame | Triggered by live sun/moon positions |
