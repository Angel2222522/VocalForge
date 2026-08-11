package com.angel.vocalforge.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.angel.vocalforge.model.AudioBuffer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

object AudioDecoder {
    fun decode(context: Context, uri: Uri): AudioBuffer {
        val temporary = File.createTempFile("vocalforge-import-", ".bin", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open selected audio" }
                temporary.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
            if (looksLikeWav(temporary)) return WavCodec.read(temporary)
            return decodeWithMediaCodec(temporary)
        } finally {
            temporary.delete()
        }
    }

    private fun looksLikeWav(file: File): Boolean {
        if (file.length() < 12) return false
        file.inputStream().use { input ->
            val header = ByteArray(12)
            if (input.read(header) != 12) return false
            return header.copyOfRange(0, 4).contentEquals(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte())) &&
                header.copyOfRange(8, 12).contentEquals(byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()))
        }
    }

    private fun decodeWithMediaCodec(file: File): AudioBuffer {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var track = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
        }
        require(track >= 0) { "No audio track found" }
        extractor.selectTrack(track)
        val sourceFormat = extractor.getTrackFormat(track)
        val mime = requireNotNull(sourceFormat.getString(MediaFormat.KEY_MIME))
        val sampleRate = sourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = sourceFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(sourceFormat, null, null, 0)
        codec.start()

        val samples = FloatAccumulator()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex) ?: continue
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> if (inputDone) Thread.yield()
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) appendPcm(output, info.offset, info.size, channels, samples, sourceFormat)
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
        return AudioBuffer(samples.toArray(), sampleRate, 1)
    }

    private fun appendPcm(
        buffer: ByteBuffer,
        offset: Int,
        size: Int,
        channels: Int,
        out: FloatAccumulator,
        format: MediaFormat
    ) {
        val pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) format.getInteger(MediaFormat.KEY_PCM_ENCODING) else 2
        val bytesPerSample = if (pcmEncoding == 4 /* PCM_FLOAT */) 4 else 2
        val duplicate = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        duplicate.position(offset.coerceAtLeast(0))
        duplicate.limit((offset + size).coerceAtMost(buffer.capacity()))
        while (duplicate.remaining() >= bytesPerSample * channels) {
            var sum = 0f
            repeat(channels) {
                sum += if (bytesPerSample == 4) duplicate.float else duplicate.short / 32768f
            }
            out.add((sum / channels).coerceIn(-1f, 1f))
        }
    }

    private class FloatAccumulator(initial: Int = 16 * 1024) {
        private var values = FloatArray(initial)
        private var size = 0
        fun add(value: Float) {
            if (size == values.size) values = values.copyOf(values.size * 2)
            values[size++] = value
        }
        fun toArray(): FloatArray = values.copyOf(size)
    }
}
