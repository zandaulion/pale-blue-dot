# Build & Release

## Requirements

| Tool | Version |
|------|---------|
| Android Studio | Hedgehog or later |
| JDK | 17 |
| Android Gradle Plugin | 8.7.3 |
| Gradle wrapper | 8.9 |
| Kotlin | 2.0.21 |

---

## Build Variants

| Variant | Description |
|---------|-------------|
| `debug` | Debuggable APK, no minification, uses debug signing |
| `release` | Minified + resource-shrunk, signed with release keystore |

---

## Dependencies

The dependency footprint is deliberately minimal:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
```

No third-party rendering or math libraries are used. All OpenGL math uses `android.opengl.Matrix`.

---

## SDK Targets

```kotlin
minSdk     = 24   // Android 7.0 — guarantees OpenGL ES 3.0 support
targetSdk  = 35   // Android 15
compileSdk = 35
```

`minSdk 24` is the lowest version that guarantees OpenGL ES 3.0 and GLSL 300 es across all device families.

---

## Release Signing

The release keystore is `globe-release.jks` at the project root. Signing credentials are read from `keystore.properties` (not committed to version control).

`keystore.properties` format:
```
storeFile=../globe-release.jks
storePassword=<password>
keyAlias=<alias>
keyPassword=<password>
```

The `app/build.gradle.kts` signingConfig block reads these at build time:
```kotlin
val keystoreProperties = Properties()
keystoreProperties.load(FileInputStream(rootProject.file("keystore.properties")))

signingConfigs {
    create("release") {
        storeFile     = file(keystoreProperties["storeFile"] as String)
        storePassword = keystoreProperties["storePassword"] as String
        keyAlias      = keystoreProperties["keyAlias"] as String
        keyPassword   = keystoreProperties["keyPassword"] as String
    }
}
```

---

## Building

### Debug APK
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Release APK
```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

### Install on connected device
```bash
./gradlew installDebug
```

---

## App Icon Generation

The launcher icons in `mipmap-*dpi/` are generated from `play_store_icon.png` using the Python script at the project root:

```bash
python generate_icons.py
```

This produces all density variants (mdpi through xxxhdpi) and the adaptive icon layers.

---

## ProGuard / R8

Release builds enable both code shrinking and resource shrinking. The default Android ProGuard rules are applied. No additional keep rules are required because the app uses no reflection-based deserialization or dynamic class loading.

```kotlin
buildTypes {
    release {
        isMinifyEnabled         = true
        isShrinkResources       = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```
