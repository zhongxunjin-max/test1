package com.example.videoeditor.model

const val MIN_CLIP_MS = 500L

data class Clip(
    val id: Long,
    val uri: String,
    val durationMs: Long,
    val startMs: Long = 0L,
    val endMs: Long = durationMs,
    val caption: String = "",
) {
    val trimmedMs: Long get() = endMs - startMs
}

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float) : ExportState
    data object Done : ExportState
    data class Failed(val message: String) : ExportState
}

data class EditorState(
    val clips: List<Clip> = emptyList(),
    val selectedId: Long? = null,
    val export: ExportState = ExportState.Idle,
) {
    val selected: Clip? get() = clips.firstOrNull { it.id == selectedId }
}

fun Clip.withTrim(start: Long, end: Long): Clip {
    val minLength = minOf(MIN_CLIP_MS, durationMs)
    val newStart = start.coerceIn(0L, durationMs - minLength)
    val newEnd = end.coerceIn(newStart + minLength, durationMs)
    return copy(startMs = newStart, endMs = newEnd)
}

fun List<Clip>.moved(id: Long, delta: Int): List<Clip> {
    val from = indexOfFirst { it.id == id }
    if (from < 0) return this
    val to = (from + delta).coerceIn(0, lastIndex)
    if (to == from) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

fun List<Clip>.totalTrimmedMs(): Long = sumOf { it.trimmedMs }

fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
