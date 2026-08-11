package com.angel.vocalforge.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.angel.vocalforge.model.AudioBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioPlayer {
    private var track: AudioTrack? = null
    private var job: Job? = null

    fun play(scope: CoroutineScope, audio: AudioBuffer, onFinished: () -> Unit) {
        stop()
        val bufferSize = AudioTrack.getMinBufferSize(audio.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            .coerceAtLeast(4096)
        val newTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(audio.sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = newTrack
        job = scope.launch(Dispatchers.IO) {
            try {
                newTrack.play()
                val block = ShortArray(4096)
                var cursor = 0
                while (isActive && cursor < audio.samples.size) {
                    val count = minOf(block.size, audio.samples.size - cursor)
                    for (i in 0 until count) block[i] = (audio.samples[cursor + i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    var written = 0
                    while (written < count && isActive) {
                        val n = newTrack.write(block, written, count - written)
                        if (n <= 0) break else written += n
                    }
                    cursor += count
                }
                if (isActive) newTrack.stop()
            } finally {
                newTrack.release()
                withContext(Dispatchers.Main.immediate) { onFinished() }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        track?.let { runCatching { it.pause(); it.flush(); it.release() } }
        track = null
    }
}
