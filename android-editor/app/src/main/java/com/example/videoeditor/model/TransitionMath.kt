package com.example.videoeditor.model

const val MAX_TRANSITION_MS = 500L

fun transitionDurationMs(clipMs: Long): Long = minOf(MAX_TRANSITION_MS, clipMs / 2)

/**
 * How visible a clip is during the transition window at its start ([isIn]) or end:
 * 0 = fully hidden, 1 = fully shown, eased with smoothstep.
 */
fun edgeProgress(timeMs: Long, clipMs: Long, isIn: Boolean): Float {
    val duration = transitionDurationMs(clipMs)
    if (duration <= 0) return 1f
    val position = if (isIn) timeMs else clipMs - timeMs
    val x = (position.toFloat() / duration).coerceIn(0f, 1f)
    return x * x * (3 - 2 * x)
}
