package com.example.videoeditor.export

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix

/** Scales mono or stereo audio by [volume]; a fresh processor per item since they hold state. */
fun volumeProcessors(volume: Float): List<AudioProcessor> {
    if (volume == 1f) return emptyList()
    val mixer = ChannelMixingAudioProcessor()
    for (channels in 1..2) {
        mixer.putChannelMixingMatrix(ChannelMixingMatrix.create(channels, channels).scaleBy(volume))
    }
    return listOf(mixer)
}
