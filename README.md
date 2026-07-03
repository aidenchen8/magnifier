> [中文文档](README-ZH.md).

# Magnifier

A minimalist Android magnifier app designed for seniors.

- Open and use instantly, with a simple UI: large buttons, large text, and high contrast.
- Uses the rear camera to magnify distant or small text and objects in real time.
- Supports tap-to-focus, flashlight fill-light, freeze frame, and save screenshot.

## Download APK

This project is built in the cloud using GitHub Actions, so you don't need to install Android Studio locally.

1. Open the **Actions** tab of this repository.
2. Select the latest **Build Magnifier Debug APK** workflow.
3. Find the **Artifacts** section in the run results.
4. Download the archive named `magnifier-debug`, extract it, and you will get `app-debug.apk`.

## Install on Your Phone

1. Transfer the downloaded `app-debug.apk` to your Android phone.
2. Tap the APK in your phone's file manager.
3. If prompted "Install blocked for unknown sources", follow the on-screen instructions to allow installation from your browser or file manager.
4. Once installed, open the app and start using it.

> The app only requests **camera permission** and does not request any storage permissions. Screenshots are saved to the phone's `Pictures/Magnifier/` album directory (using Android 10+ Scoped Storage).

## Features

| Feature | Description |
|---------|-------------|
| **Magnify** | Uses the rear camera by default. Drag the bottom slider to adjust zoom from 1.0x up to the device's maximum zoom. |
| **Focus** | Tap the center of the preview to trigger a single autofocus; a yellow focus frame will appear as feedback. |
| **Flashlight** | Tap the "Light On / Light Off" button to keep the flashlight on for fill-light, making it easier to see in the dark. |
| **Freeze** | Tap the "Freeze" button to capture and hold the current frame for closer inspection; tap "Unfreeze" to resume live preview. |
| **Save** | The "Save" button is only available while frozen. Saves the current frame to the `Pictures/Magnifier/` album. |
| **Mode** | After freezing, switch between Normal / Black background with white text / Yellow background with black text color filters to help users with low vision read content. |

## Tech Stack

- Kotlin 2.0+
- Jetpack Compose (Material3)
- CameraX 1.3.4+
- minSdk 29 / targetSdk 35 / compileSdk 35
- Single Activity + single ViewModel, with no extra dependencies such as Hilt, Navigation, or Room

## License

MIT License — free to use, modify, and distribute.
