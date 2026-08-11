package com.angel.vocalforge.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.angel.vocalforge.model.AudioBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecorder {
    private var recorder: AudioRecord? = null
    private var job: Job? = null
    private val running = AtomicBoolean(false)

    fun start(scope: CoroutineScope, onFinished: (AudioBuffer?) -> Unit) {
        if (running.get()) return
        val configuration = listOf(48_000, 44_100, 32_000, 16_000).firstNotNullOfOrNull { rate ->
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) return@firstNotNullOfOrNull null
            val source = if (android.os.Build.VERSION.SDK_INT >= 24) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
            runCatching {
                AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, rate / 5))
                    .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
            }.getOrNull()?.let { rate to it }
        } ?: run { onFinished(null); return }

        val (sampleRate, audioRecord) = configuration
        recorder = audioRecord
        running.set(true)
        job = scope.launch(Dispatchers.IO) {
            val chunks = ArrayList<ShortArray>()
            val readBuffer = ShortArray(sampleRate / 10)
            try {
                audioRecord.startRecording()
                while (running.get()) {
                    val count = audioRecord.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                    if (count > 0) chunks += readBuffer.copyOf(count)
                }
            } catch (_: Throwable) {
                chunks.clear()
            } finally {
                runCatching { audioRecord.stop() }
                audioRecord.release()
                recorder = null
                val total = chunks.sumOf { it.size }
                val samples = FloatArray(total)
                var cursor = 0
                chunks.forEach { chunk ->
                    chunk.forEach { samples[cursor++] = it / 32768f }
                }
                withContext(Dispatchers.Main.immediate) { onFinished(if (samples.isNotEmpty()) AudioBuffer(samples, sampleRate) else null) }
            }
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        job?.cancel()
        job = null
    }
}
