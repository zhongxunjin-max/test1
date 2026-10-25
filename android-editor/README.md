# Video Editor (Android)

Kotlin + Jetpack Compose + Media3 skeleton for a simple mobile video editor.

## Features
- Pick several videos (system photo picker, no storage permission needed)
- Timeline of clips: select, reorder, delete
- Per-clip trim (range slider) with live preview (ExoPlayer)
- Per-clip text caption burned into the export (Media3 `TextOverlay`)
- Export to `Movies/VideoEditor` as H.264/AAC MP4 (Media3 Transformer)

## Layout
- `model/Timeline.kt` – clip/editor state and pure timeline operations (unit tested)
- `export/Exporter.kt` – Transformer composition, progress, MediaStore save
- `EditorViewModel.kt` – state holder
- `ui/EditorScreen.kt` – Compose UI

## Build
Requires JDK 17 and the Android SDK (platform 35).

```
./gradlew testDebugUnitTest assembleDebug
```

The `Android` GitHub Actions workflow runs the same command and uploads the debug APK.

## Not yet done
Audio tracks, filters/transitions, caption timing and styling, thumbnails, project save/restore.
