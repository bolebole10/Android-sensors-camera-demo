# Sensors & Camera Demo

A small Android app built for a university Mobile App Development presentation.
It demonstrates the Android sensor framework and the Camera2 / CameraX APIs
through a series of focused, single-screen demos.

The app is built with Kotlin and Jetpack Compose (Material 3) on a single
activity with bottom navigation.

## Build and run

You need **Android Studio Hedgehog (2023.1.1) or newer** and a physical
Android device running **Android 7.0 (API 24)** or higher.

1. Open the project in Android Studio.
2. Let Gradle sync (it will download the Compose, CameraX, and ML Kit
   dependencies on first run).
3. Plug in an Android device with USB debugging enabled.
4. Press **Run ▶**.

> **Use a physical device.** The Android emulator does not provide useful
> sensor data and has limited camera support. The bubble level and
> compass will sit at zero, the light sensor will not fire, and the
> Camera2 manual-control screen will likely report no usable back camera.
> Plug a real phone in.

## What's in the app

The bottom navigation has two tabs: **Sensors** and **Camera**. Each tab
opens to a menu of demo cards.

### Sensors tab

| # | Screen | What it shows |
|---|--------|---------------|
| 1 | **Bubble Level** | Reads `TYPE_GRAVITY` (or accelerometer fallback) and draws a 2D bubble that moves with device tilt. Bubble turns green when level. |
| 2 | **Compass** | Reads `TYPE_ROTATION_VECTOR`, derives the azimuth, and rotates a compass dial so the red arrow always points to magnetic north. |
| 3 | **Shake to Clear** | Reads `TYPE_LINEAR_ACCELERATION`, counts shakes via a magnitude threshold + 500 ms cooldown, with haptic feedback. |
| 4 | **Light → Auto Theme** | Reads `TYPE_LIGHT` (lux) and animates the screen between light and dark color schemes when crossing a threshold. |

### Camera tab

| # | Screen | What it shows |
|---|--------|---------------|
| 1 | **Video Recording (CameraX)** | `Preview` + `VideoCapture` use cases. Front/back flip, MediaStore output. |
| 2 | **Barcode & QR Scanner** | `Preview` + `ImageAnalysis` with ML Kit's on-device barcode detector and a green box overlay. |
| 3 | **Manual Controls (Camera2)** | Manual ISO, exposure time, and focus distance via raw Camera2. Falls back to "Auto only" on devices without `MANUAL_SENSOR` capability. |

### Note on the basic CameraX photo demo

The original presentation outline listed a "basic CameraX photo capture"
as a separate camera demo. Rather than build a third CameraX screen that
would be 90% identical to the video one, we **document** what that screen
would look like as a header comment in
[`VideoRecordingScreen.kt`](app/src/main/java/com/example/sensorscamerademo/camera/VideoRecordingScreen.kt).
Read those comments aloud during the presentation — they explain that
swapping `VideoCapture` for `ImageCapture` and calling `takePicture()`
instead of starting a `Recording` is all that changes.

## Project layout

```
app/src/main/java/com/example/sensorscamerademo/
├── MainActivity.kt
├── navigation/
│   ├── AppNavigation.kt        # Bottom nav + per-tab NavHosts
│   ├── SensorRoutes.kt
│   └── CameraRoutes.kt
├── sensors/
│   ├── SensorsMenuScreen.kt
│   ├── BubbleLevelScreen.kt
│   ├── CompassScreen.kt
│   ├── ShakeScreen.kt
│   ├── LightSensorScreen.kt
│   └── SensorHelpers.kt        # rememberSensorValues, low-pass filter
├── camera/
│   ├── CameraMenuScreen.kt
│   ├── VideoRecordingScreen.kt
│   ├── BarcodeScannerScreen.kt
│   ├── Camera2ManualScreen.kt
│   └── PermissionGate.kt
└── ui/
    ├── CommonUi.kt             # DemoScaffold, ExplanationCard
    └── theme/{Theme, Color, Type}.kt
```

## Permissions

Declared in the manifest and requested at runtime when the relevant
screen opens:

- `CAMERA` — required by all camera demos
- `RECORD_AUDIO` — required by the video recording demo

`MediaStore` writes do **not** require `WRITE_EXTERNAL_STORAGE` on
Android 10+. Saved videos appear in `Movies/SensorsCameraDemo/`; saved
photos in `Pictures/SensorsCameraDemo/`.

## Sensor lifecycle pattern

Every sensor demo registers its `SensorEventListener` inside a
`DisposableEffect` and unregisters it in `onDispose`. This is the Compose
equivalent of the classic `onResume` / `onPause` pair: the listener runs
only while the screen is visible, so the sensor is not draining the
battery in the background.

The shared helper is
[`rememberSensorValues`](app/src/main/java/com/example/sensorscamerademo/sensors/SensorHelpers.kt),
used by the bubble level, shake, and light demos. The compass screen
uses an inline variant because it needs to convert the rotation vector
into an azimuth on every event.

## Known emulator limitations

- **Sensors**: the emulator supports a *Virtual Sensors* panel for
  accelerometer / gyro / magnetometer, but most readings are static and
  the gravity / linear-acceleration fused sensors may not be exposed.
- **Camera**: the emulator's virtual scene is a flat image; barcode
  scanning won't pick up real codes, and Camera2 manual-control queries
  often fail.
- Use a physical phone for any meaningful demo.
