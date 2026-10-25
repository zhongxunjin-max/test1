package com.example.videoeditor

import com.example.videoeditor.model.Clip
import com.example.videoeditor.model.MIN_CLIP_MS
import com.example.videoeditor.model.formatMs
import com.example.videoeditor.model.moved
import com.example.videoeditor.model.totalTrimmedMs
import com.example.videoeditor.model.withTrim
import com.example.videoeditor.model.edgeProgress
import com.example.videoeditor.model.transitionDurationMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {
    private fun clip(id: Long, duration: Long = 10_000L) = Clip(id, "content://clip/$id", duration)

    @Test
    fun trimIsClampedToClipBounds() {
        val trimmed = clip(1).withTrim(-500, 99_000)
        assertEquals(0L, trimmed.startMs)
        assertEquals(10_000L, trimmed.endMs)
    }

    @Test
    fun trimKeepsMinimumLength() {
        val trimmed = clip(1).withTrim(4_000, 4_100)
        assertEquals(4_000L, trimmed.startMs)
        assertEquals(4_000L + MIN_CLIP_MS, trimmed.endMs)
    }

    @Test
    fun trimNearEndShiftsStartBack() {
        val trimmed = clip(1).withTrim(9_900, 10_000)
        assertEquals(10_000L - MIN_CLIP_MS, trimmed.startMs)
        assertEquals(10_000L, trimmed.endMs)
    }

    @Test
    fun moveReordersAndClamps() {
        val clips = listOf(clip(1), clip(2), clip(3))
        assertEquals(listOf(2L, 1L, 3L), clips.moved(1, 1).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), clips.moved(1, -1).map { it.id })
        assertEquals(listOf(3L, 1L, 2L), clips.moved(3, -5).map { it.id })
    }

    @Test
    fun totalDurationSumsTrimmedClips() {
        val clips = listOf(clip(1).withTrim(0, 3_000), clip(2).withTrim(2_000, 7_000))
        assertEquals(8_000L, clips.totalTrimmedMs())
    }

    @Test
    fun formatsMinutesAndSeconds() {
        assertEquals("1:05", formatMs(65_400))
    }

    @Test
    fun transitionNeverExceedsHalfTheClip() {
        assertEquals(500L, transitionDurationMs(10_000))
        assertEquals(300L, transitionDurationMs(600))
    }

    @Test
    fun edgeProgressRampsAtStartAndEnd() {
        assertEquals(0f, edgeProgress(0, 10_000, isIn = true), 1e-6f)
        assertEquals(1f, edgeProgress(500, 10_000, isIn = true), 1e-6f)
        assertEquals(1f, edgeProgress(5_000, 10_000, isIn = true), 1e-6f)
        assertEquals(1f, edgeProgress(5_000, 10_000, isIn = false), 1e-6f)
        assertEquals(0f, edgeProgress(10_000, 10_000, isIn = false), 1e-6f)
    }

    @Test
    fun edgeProgressIsMonotonicWhileFadingIn() {
        val values = (0..500 step 50).map { edgeProgress(it.toLong(), 10_000, isIn = true) }
        assertTrue(values.zipWithNext().all { (a, b) -> a <= b })
    }
}
