package com.example.videoeditor.export

import android.graphics.Matrix
import androidx.media3.common.Effect
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import com.example.videoeditor.model.Filter
import com.example.videoeditor.model.Transition
import com.example.videoeditor.model.edgeProgress

private fun rgbScale(red: Float, green: Float, blue: Float): Effect =
    RgbAdjustment.Builder().setRedScale(red).setGreenScale(green).setBlueScale(blue).build()

fun Filter.toEffects(): List<Effect> = when (this) {
    Filter.ORIGINAL -> emptyList()
    Filter.MONO -> listOf(RgbFilter.createGrayscaleFilter())
    Filter.SEPIA -> listOf(RgbFilter.createGrayscaleFilter(), rgbScale(1.1f, 0.9f, 0.65f))
    Filter.WARM -> listOf(rgbScale(1.1f, 1.0f, 0.85f))
    Filter.COOL -> listOf(rgbScale(0.9f, 1.0f, 1.15f))
    Filter.VIVID -> listOf(HslAdjustment.Builder().adjustSaturation(35f).build(), Contrast(0.15f))
}

/**
 * Effects that animate the start ([isIn]) or end of a clip. These are "dip" transitions: each clip
 * animates in/out on its own (fade to black, slide, zoom) rather than blending with its neighbour.
 *
 * [offsetUs] is where the clip starts in the whole timeline. Frame timestamps seen by effects may
 * be relative to the clip or to the timeline, so both are handled.
 */
fun transitionEffects(type: Transition, isIn: Boolean, clipMs: Long, offsetUs: Long): List<Effect> {
    if (type == Transition.NONE) return emptyList()

    fun progress(presentationTimeUs: Long): Float {
        val relativeUs = (presentationTimeUs - offsetUs).takeIf { it >= 0 } ?: presentationTimeUs
        return edgeProgress(relativeUs / 1000, clipMs, isIn)
    }

    val fade = RgbMatrix { presentationTimeUs, _ ->
        val p = progress(presentationTimeUs)
        floatArrayOf(
            p, 0f, 0f, 0f,
            0f, p, 0f, 0f,
            0f, 0f, p, 0f,
            0f, 0f, 0f, 1f,
        )
    }
    return when (type) {
        Transition.NONE -> emptyList()
        Transition.FADE -> listOf(fade)
        Transition.SLIDE -> listOf(
            MatrixTransformation { presentationTimeUs ->
                val direction = if (isIn) 1f else -1f
                Matrix().apply { postTranslate(direction * (1f - progress(presentationTimeUs)) * 2f, 0f) }
            }
        )
        Transition.ZOOM -> listOf(
            MatrixTransformation { presentationTimeUs ->
                val scale = 0.4f + 0.6f * progress(presentationTimeUs)
                Matrix().apply { postScale(scale, scale) }
            },
            fade,
        )
    }
}
