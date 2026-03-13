# Globe — Architecture Decision Record

## Overview

A single-screen Android app that renders Earth from space with real-time day/night
lighting based on the current UTC time, set against a starfield background.

---

## 1. Language: Kotlin

**Decision**: Kotlin, targeting JVM/Android.

**Rationale**: Kotlin is the default language for new Android projects. Extension
functions, coroutines, null safety, and concise syntax all help. There is no
meaningful performance penalty — the hot path is GPU shader code (GLSL), not
host-side Kotlin.

---

## 2. 3D Rendering: Raw OpenGL ES 3.0

**Decision**: OpenGL ES 3.0, no external engine or framework.

**Rationale**:

- The scene is simple: one textured sphere, one light source, one starfield.
  A full engine (Rajawali, libGDX, SceneForm) would add hundreds of kilobytes
  of dependency for features we will never use.
- OpenGL ES 3.0 is available on 97%+ of active Android devices. It gives us
  GLSL 300 es (in/out varyings, VAOs, instanced draw if needed) while staying
  far simpler than Vulkan.
- Vulkan would increase code complexity by 5-10x for no visible benefit in a
  scene with two draw calls.
- Using raw GL keeps the APK small and gives full control over the render pipeline.

---

## 3. Earth Model

**Approach**: UV-sphere geometry + multi-texture fragment shader.

### Geometry (`EarthModel`)
A UV-sphere generated on the CPU at startup (64 latitude x 128 longitude segments).
Positions, normals, and UV coordinates are stored in a single interleaved VBO
(8 floats/vertex: pos3 + normal3 + uv2) with an IBO for indexed triangles.
Uploaded once to a VAO via `EarthModel.uploadToGpu()`, never modified.

### Textures
| Slot | Uniform         | Purpose                              |
|------|-----------------|--------------------------------------|
| 0    | uDayTexture     | Diffuse colour (day side)            |
| 1    | uNightTexture   | City-lights emissive (night side)    |

Textures can be loaded from drawable resources or generated as procedural
placeholders (the app runs without any texture assets). When real textures are
added, they should be 4096x2048 equirectangular maps with mipmaps.

### Fragment Shader Logic (`EarthShader`)
```glsl
NdotL     = dot(normal, sunDir)
dayFactor = smoothstep(-0.15, 0.15, NdotL)     // gradual terminator
litDay    = dayColor * (0.1 + 0.9 * max(NdotL, 0))
surface   = mix(nightColor, litDay, dayFactor)
```
The `smoothstep` creates a soft terminator rather than a hard day/night edge.
A fresnel-based atmosphere rim glow tints the limb blue, stronger on the dayside.

---

## 4. Starfield Background

**Decision**: Point sprites rendered as `GL_POINTS` on a large surrounding sphere.

**Implementation** (in `stars/` package):

- **`StarsModel`**: Generates 2500 stars at startup with:
  - Positions on a sphere of radius 500 (well beyond the far plane of the Earth).
  - 30% clustered near a galactic band for a subtle Milky Way effect;
    70% uniformly distributed.
  - Power-law size distribution (most dim, few bright).
  - Spectral-class colours (blue-white, white, yellow, orange, red-orange).

- **`StarsShader`**: GLSL 300 es shaders that:
  - Set `gl_PointSize` from the per-vertex size attribute.
  - Animate a per-star twinkle using a hash-based phase + sine wave driven
    by a `uTime` uniform.
  - Render each point sprite as a soft circular glow (core + halo falloff)
    in the fragment shader.

- **`StarsRenderer`**: Draws stars as `GL_POINTS` with:
  - Translation stripped from the view matrix (stars at "infinity").
  - Depth test and depth writes disabled (drawn before Earth).
  - Additive blending (`GL_SRC_ALPHA, GL_ONE`) for natural star bloom.

---

## 5. Sun Position / Lighting

**Decision**: Simplified astronomical algorithm computed on the CPU.

**Implementation** (`earth/SunPosition`):

The sun's ecliptic longitude is computed from the Julian Date using the
Astronomical Almanac approximation (~1 degree accuracy):

```
n = JulianDate(now_utc) - 2451545.0        // days since J2000.0
L = 280.460 + 0.9856474 * n                // mean longitude
g = 357.528 + 0.9856003 * n                // mean anomaly
lambda = L + 1.915*sin(g) + 0.020*sin(2g)  // ecliptic longitude
```

The ecliptic longitude is converted to equatorial RA/Dec, then rotated into the
Earth-fixed frame using Greenwich Mean Sidereal Time (GMST). This yields a unit
vector (`uSunDirection`) that correctly accounts for:
- Earth's orbital position (season/time of year)
- Earth's axial tilt (23.44 degrees)
- Earth's rotation (hour of day via GMST)

No external library needed — approximately 50 lines of Kotlin math.

---

## 6. Camera & Interaction

**Implementation** (`camera/OrbitCamera`):

- Default: camera at 3.0 Earth-radii, 20 degrees elevation, azimuth 0.
- Single-finger drag: orbit (rotate azimuth/elevation around Earth).
- Pinch-to-zoom: adjust distance (clamped 1.5R to 6.0R).
- Camera parameters are `@Volatile` for thread safety between UI and GL threads.
- `getViewMatrix()` computes a `setLookAtM` each frame from spherical coords.
- `getPosition()` returns the eye position for atmosphere fresnel calculations.

---

## 7. Project Structure

```
Globe/
├── ARCHITECTURE.md
├── build.gradle.kts              # project-level (AGP + Kotlin plugin versions)
├── settings.gradle.kts           # module includes
├── gradle.properties             # JVM args, AndroidX flags
├── app/
│   ├── build.gradle.kts          # android app config
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/               # (future: texture files)
│       ├── res/values/
│       │   ├── strings.xml
│       │   └── themes.xml
│       └── java/com/globe/app/
│           ├── MainActivity.kt          # entry point, sets GlobeSurfaceView as content
│           ├── GlobeRenderer.kt         # GLSurfaceView.Renderer — orchestrates draw order
│           ├── GlobeSurfaceView.kt      # custom GLSurfaceView — touch handling
│           ├── camera/
│           │   └── OrbitCamera.kt       # azimuth/elevation/distance → view matrix
│           ├── earth/
│           │   ├── EarthModel.kt        # UV-sphere geometry generation + GPU upload
│           │   ├── EarthShader.kt       # GLSL compile/link, uniform management
│           │   ├── EarthRenderer.kt     # ties mesh + shader + textures together
│           │   └── SunPosition.kt       # astronomical sun-direction calculation
│           └── stars/
│               ├── StarsModel.kt        # procedural star vertex data generation
│               ├── StarsShader.kt       # GLSL point-sprite shader with twinkle
│               └── StarsRenderer.kt     # draw call + GL state management
```

### Render pipeline (per frame)

```
GlobeRenderer.onDrawFrame()
  ├── glClear(COLOR | DEPTH)
  ├── camera.getViewMatrix() + camera.getPosition()
  ├── starsRenderer.draw(view, projection)    // depth off, additive blend
  └── earthRenderer.setMatrices(view, proj, camPos)
      └── earthRenderer.onDrawFrame()         // depth on, backface cull
```

### Key design rules

1. **No singletons / god objects.** Each class owns one concern.
   `StarsShader` is an `object` only because it holds no mutable state beyond
   GL handles initialized once.
2. **GlobeRenderer is the orchestrator.** It calls into earth and stars renderers;
   it does not contain GL draw code beyond `glClear` and draw ordering.
3. **Shaders are embedded as Kotlin string constants** (`const val` in companion
   objects). This avoids file-loading boilerplate for a small number of shaders.
   If the shader count grows, move them to `assets/shaders/`.
4. **All GL state is created in `onSurfaceCreated`**, never in `onCreate`.
   This correctly handles EGL context loss on Android.
5. **Placeholder textures** are generated procedurally so the app can run
   immediately without any external texture assets.

---

## 8. Dependencies

Only standard Android SDK:

- `androidx.appcompat` — AppCompatActivity (theme compatibility)
- `androidx.core:core-ktx` — Kotlin extensions
- Android SDK OpenGL ES 3.0 (`android.opengl.*`)

No third-party libraries for rendering, math, or image loading.
`android.opengl.Matrix` covers all matrix operations. `BitmapFactory` covers
texture loading.

---

## 9. Build & Target

| Setting     | Value       | Rationale                                   |
|-------------|-------------|---------------------------------------------|
| minSdk      | 24          | Android 7.0 — guarantees GLES 3.0, 97%+ coverage |
| targetSdk   | 35          | Current Android 15                          |
| compileSdk  | 35          | Latest stable SDK                           |
| Kotlin      | 2.0.21      | Latest stable                               |
| AGP         | 8.7.3       | Latest stable                               |
| JVM target  | 17          | Required by AGP 8.x                         |
