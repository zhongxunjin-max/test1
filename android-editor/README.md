# Video Editor (Android)

Kotlin + Jetpack Compose + Media3 skeleton for a simple mobile video editor.

## Features
- Pick several videos (system photo picker, no storage permission needed)
- Timeline of clips: select, reorder, delete
- Per-clip trim (range slider) with live preview (ExoPlayer)
- Per-clip filters (B&W, sepia, warm, cool, vivid), previewed live and applied on export
- Per-clip transition into the clip (fade, slide, zoom); these are "dip" transitions where each clip
  animates in/out on its own, not a cross-dissolve with the neighbouring clip
- One background music track (any audio file) that loops or is cut to the video length, with volume
  sliders for the music and for the clips' own audio; mixed in on export (no audio preview yet)
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
Multiple audio tracks, music trimming and fades, cross-dissolve transitions, caption timing and styling, thumbnails, project save/restore.
