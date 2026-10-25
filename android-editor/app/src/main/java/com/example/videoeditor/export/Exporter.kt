package com.example.videoeditor.export

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.text.SpannableString
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.videoeditor.model.Clip
import com.example.videoeditor.model.Transition
import com.google.common.collect.ImmutableList
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun Clip.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setUri(Uri.parse(uri))
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build()
        )
        .build()

/** Renders the timeline to an MP4 with Media3 Transformer and saves it to Movies/VideoEditor. */
class Exporter(private val context: Context) {

    suspend fun export(clips: List<Clip>, onProgress: (Float) -> Unit): Uri =
        withContext(Dispatchers.Main) {
            val output = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")
            val composition = Composition.Builder(
                EditedMediaItemSequence(clips.toEditedMediaItems())
            ).build()

            val finished = CompletableDeferred<Unit>()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        finished.complete(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        finished.completeExceptionally(exportException)
                    }
                })
                .build()

            try {
                coroutineScope {
                    transformer.start(composition, output.absolutePath)
                    val poller = launch {
                        val holder = ProgressHolder()
                        while (isActive) {
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress(holder.progress / 100f)
                            }
                            delay(200)
                        }
                    }
                    try {
                        finished.await()
                    } finally {
                        poller.cancel()
                    }
                }
            } catch (e: CancellationException) {
                transformer.cancel()
                output.delete()
                throw e
            } catch (e: Exception) {
                output.delete()
                throw e
            }

            withContext(Dispatchers.IO) { saveToGallery(output) }
        }

    private fun List<Clip>.toEditedMediaItems(): List<EditedMediaItem> {
        var offsetUs = 0L
        return mapIndexed { index, clip ->
            val transitionIn = if (index > 0) clip.transition else Transition.NONE
            val transitionOut = getOrNull(index + 1)?.transition ?: Transition.NONE
            val item = clip.toEditedMediaItem(transitionIn, transitionOut, offsetUs)
            offsetUs += clip.trimmedMs * 1000
            item
        }
    }

    private fun Clip.toEditedMediaItem(
        transitionIn: Transition,
        transitionOut: Transition,
        offsetUs: Long,
    ): EditedMediaItem {
        val videoEffects = buildList<Effect> {
            addAll(filter.toEffects())
            if (caption.isNotBlank()) {
                val settings = OverlaySettings.Builder()
                    .setBackgroundFrameAnchor(0f, -0.8f)
                    .build()
                val overlay = TextOverlay.createStaticTextOverlay(SpannableString(caption), settings)
                add(OverlayEffect(ImmutableList.of(overlay)))
            }
            addAll(transitionEffects(transitionIn, isIn = true, trimmedMs, offsetUs))
            addAll(transitionEffects(transitionOut, isIn = false, trimmedMs, offsetUs))
        }
        return EditedMediaItem.Builder(toMediaItem())
            .setEffects(Effects(emptyList(), videoEffects))
            .build()
    }

    private fun saveToGallery(file: File): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, MimeTypes.VIDEO_MP4)
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VideoEditor")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: error("Cannot open output stream")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        } finally {
            file.delete()
        }
        return uri
    }
}
