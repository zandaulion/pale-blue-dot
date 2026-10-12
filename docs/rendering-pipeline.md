# Rendering Pipeline

## Per-Frame Draw Order

Each call to `GlobeRenderer.onDrawFrame()` executes the following sequence. The order is intentional — depth/blend requirements dictate grouping.

```
1.  glClear(COLOR | DEPTH)
2.  SceneClock / OrbitCamera — snapshot frame time, update camera, compute shared matrices
3.  Stars          — GL_POINTS, depth test OFF, additive blend (~16,000 verts)
4.  Constellations — GL_LINES + GL_POINTS, same state as stars
5.  Sun            — billboard quad, no depth write, additive glow layers
6.  Moon           — faint-phase outline, then shaded sphere; depth test ON
7.  Earth          — sphere, depth test ON, backface culled, day/night/cloud blend
8.  Surface overlays — events, plate boundaries, latitude guides, saved-place pins; depth test ON
9.  ISS orbit      — triangle-strip ribbon, depth test ON, RAAN rotation applied
10. Indicators     — 2D screen-space arrows, depth test OFF
11. Eclipse check  — detect(frameTime) → report approximate alignment state
```

---

## OpenGL State Summary

| Pass | Depth test | Depth write | Blend mode | Cull |
|------|-----------|-------------|------------|------|
| Stars / constellations | OFF | OFF | Additive | OFF |
| Sun | OFF | OFF | Additive | OFF |
| Moon outline | ON | OFF | Alpha | Back |
| Moon surface | ON | ON | None (opaque) | Back |
| Earth | ON | ON | None (opaque) | Back |
| Events / ISS / saved-place pins | ON | OFF | Alpha | OFF |
| Indicators | OFF | OFF | Alpha | OFF |

---

## Earth Shader

**Vertex shader** transforms each vertex with the MVP matrix and forwards world-space position, surface normal, and UV coordinates to the fragment stage.

**Fragment shader** combines textures with sunlight, atmosphere, and optional decorative layers. The following is schematic rather than a literal GLSL listing:

```
1. day_color   = texture(earth_day,   texCoord)
2. night_color = texture(earth_night, texCoord)
3. dayFactor   = smoothstep(-0.05, 0.15, dot(normal, sunDir))
   base_color  = mix(night_color, day_color, dayFactor)

4. cloud_alpha = texture(cloud_map, rotated_uv).a × cloudEnabled
   base_color  = mix(base_color, cloudTint, cloud_alpha × dayFactor)

5. atmosphere  = fresnel rim (pow(1 - dot(normal, viewDir), 3.5)) × blue
6. aurora      = sin(time)-based glow near poles, night side only

output = base_color + atmosphere + aurora
```

Generated cloud UVs drift when decorative motion is enabled. Satellite imagery stays fixed; Off removes the overlay. If satellite imagery is unavailable, generated clouds remain as a visible fallback. The shader also controls the optional terminator line. Reduce motion suppresses cloud drift and aurora animation.

---

## Star Shader

Stars use `GL_POINTS` with GLSL `gl_PointSize` set per star proportional to magnitude. The fragment shader:
- Discards fragments outside a circular disc (distance from `gl_PointCoord` center > 0.5)
- Applies a time-varying twinkle by modulating alpha with `sin(time + perStarSeed)`
- Colors are spectral-class RGB constants (O=blue-white … M=deep red)

The view matrix has its translation column zeroed before upload so stars follow camera rotation but not position — creating an infinite background.

---

## Mesh Generation (EarthModel)

```kotlin
segments_lat  = 64
segments_lon  = 128
vertices      = (segments_lat + 1) × (segments_lon + 1)   // ~8,385
indices       = segments_lat × segments_lon × 6            // ~49,152 (2 triangles/quad)

per vertex (32 bytes):
  float3 position  — point on unit sphere
  float3 normal    — same as position (unit sphere)
  float2 texCoord  — (u, v) in [0,1]

uploaded once via:
  glGenVertexArrays / glGenBuffers (VBO + EBO)
  glBufferData(GL_STATIC_DRAW)
```

Coordinate orientation: +Y North Pole, Greenwich meridian at −X, east-positive toward +Z.

---

## ISS Orbit Ribbon

The ISS ribbon is a `GL_TRIANGLE_STRIP` built from pairs of vertices offset ±width perpendicular to the orbital velocity vector. Canonical ribbon geometry is uploaded at initialization. Each frame applies the RAAN rotation to the orbit plane and calculates the marker position from the same frame instant.

```
period    = 92.68 min
inclination = 51.6°
RAAN precession = −5°/day (nodal regression)
ribbon samples = 360 points around one full orbit
```

A separate `GL_POINTS` call renders the current position marker.

---

## 2D Indicator Arrows

Indicators are rendered in screen space after all 3D passes. The Sun and Moon
arrows sit at fixed bottom positions, with a UI-aware vertical offset. Each
rotates toward its body's projected direction. Both remain available at every
phase, including while the body is visible; tapping an arrow frames that body.

## Faint Moon outline and native label

`MoonRenderer` draws an expanded silhouette pass before the existing textured
surface. Screen-space expansion keeps the guide about 1dp wide. It uses alpha
blending with depth testing on and depth writes off; the surface then overwrites
the interior, and Earth occludes it later in the frame. The surface lighting is
unchanged. Guide strength is full at 0–3% illumination and fades smoothly to
zero at 12%.

`GlobeRenderer.moonGuideFrame` is projected from the same matrices/time.
`MoonGuideOverlay` places a native, localized New Moon/Thin crescent label
around UI exclusion areas. This is an Android view above the GL surface, not a
shader-drawn text pass. Occluded/off-screen positions and open panels suppress
the label. See [Moon visibility](moon-visibility.md).

## Independent rendering surfaces

The app, wallpaper engines, previews, and widget snapshots each own graphics
resources/context. `TextureQuality` limits texture sizes by surface. Shared
repository CPU data is reuploaded after context recreation; surface creation
does not trigger its own downloads. Wallpaper and widget clocks remain real
time while the app simulates another date. Their controllers disable app-only
markers/indicators and never invoke narration. See [architecture](architecture.md).
