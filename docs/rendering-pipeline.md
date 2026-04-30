# Rendering Pipeline

## Per-Frame Draw Order

Each call to `GlobeRenderer.onDrawFrame()` executes the following sequence. The order is intentional — depth/blend requirements dictate grouping.

```
1.  glClear(COLOR | DEPTH)
2.  OrbitCamera — apply momentum, compute view matrix
3.  Stars          — GL_POINTS, depth test OFF, additive blend (~16,000 verts)
4.  Constellations — GL_LINES + GL_POINTS, same state as stars
5.  Sun            — billboard quad, no depth write, additive glow layers
6.  Moon           — sphere, depth test ON, depth write ON
7.  Earth          — sphere, depth test ON, backface culled, day/night/cloud blend
8.  Location pin   — depth test ON, cyan point + rings
9.  Earthquakes & volcanoes — pulsing point sprites, depth test ON
10. ISS orbit      — triangle-strip ribbon, depth test ON, RAAN rotation applied
11. Indicators     — 2D screen-space arrows, depth test OFF
12. Eclipse check  — dot product sun·moon vs thresholds → notify UI if needed
```

---

## OpenGL State Summary

| Pass | Depth test | Depth write | Blend mode | Cull |
|------|-----------|-------------|------------|------|
| Stars / constellations | OFF | OFF | Additive | OFF |
| Sun | ON | OFF | Additive | OFF |
| Moon | ON | ON | None (opaque) | Back |
| Earth | ON | ON | None (opaque) | Back |
| Events / ISS / pin | ON | ON | Alpha | OFF |
| Indicators | OFF | OFF | Alpha | OFF |

---

## Earth Shader

**Vertex shader** transforms each vertex with the MVP matrix and forwards world-space position, surface normal, and UV coordinates to the fragment stage.

**Fragment shader** composites the final color in five layers:

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

The cloud UV is rotated slightly each frame to simulate cloud drift.

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

The ISS ribbon is a `GL_TRIANGLE_STRIP` built from pairs of vertices offset ±width perpendicular to the orbital velocity vector. The ribbon is regenerated each frame because RAAN precession rotates the entire orbital plane continuously.

```
period    = 92.68 min
inclination = 51.6°
RAAN precession = −5°/day (nodal regression)
ribbon samples = 360 points around one full orbit
```

A separate `GL_POINTS` call renders the current position marker.

---

## 2D Indicator Arrows

Indicators are rendered in screen space after all 3D passes. The projection and view matrices are replaced with an identity-based orthographic transform covering [−1, 1] NDC. The arrow angle is computed from the projected sun/moon position: if off-screen, the arrow is placed at the edge of the viewport and rotated to point toward the body.
