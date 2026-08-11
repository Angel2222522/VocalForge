package com.angel.vocalforge.audio

import com.angel.vocalforge.model.AudioBuffer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/** Small, strict WAV reader/writer used to keep project audio lossless and self-contained. */
object WavCodec {
    private const val RIFF = 0x46464952
    private const val WAVE = 0x45564157
    private const val FMT = 0x20746D66
    private const val DATA = 0x61746164

    fun read(file: File): AudioBuffer {
        BufferedInputStream(file.inputStream(), 64 * 1024).use { input ->
            require(input.readIntLE() == RIFF) { "Not a RIFF file" }
            input.readIntLE() // RIFF chunk size
            require(input.readIntLE() == WAVE) { "Not a WAVE file" }

            var format = 1
            var channels = 1
            var sampleRate = 44100
            var bits = 16
            var dataBytes: ByteArray? = null

            while (true) {
                val chunkId = try { input.readIntLE() } catch (_: EOFException) { break }
                val size = input.readIntLE()
                require(size >= 0) { "Invalid WAV chunk" }
                when (chunkId) {
                    FMT -> {
                        val bytes = input.readBytesExact(size)
                        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                        format = b.short.toInt() and 0xFFFF
                        channels = b.short.toInt() and 0xFFFF
                        sampleRate = b.int
                        b.int // byte rate
                        b.short // block align
                        bits = b.short.toInt() and 0xFFFF
                        if (format == 0xFFFE && bytes.size >= 40) {
                            format = b.position(24).short.toInt() and 0xFFFF
                        }
                    }
                    DATA -> dataBytes = input.readBytesExact(size)
                    else -> input.skipBytesExact(size)
                }
                if ((size and 1) != 0) input.skipBytesExact(1)
            }

            val bytes = requireNotNull(dataBytes) { "WAV has no data chunk" }
            require(channels in 1..8 && sampleRate in 8000..192000) { "Unsupported WAV format" }
            require(format == 1 || format == 3) { "Only PCM and IEEE float WAV are supported" }
            require(bits == 16 || bits == 24 || bits == 32) { "Unsupported WAV bit depth" }

            val bytesPerSample = bits / 8
            val frameBytes = bytesPerSample * channels
            val frameCount = bytes.size / frameBytes
            val output = FloatArray(frameCount)
            var offset = 0
            for (frame in 0 until frameCount) {
                var sum = 0.0
                repeat(channels) {
                    sum += when {
                        format == 3 && bits == 32 -> ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).float.toDouble()
                        bits == 16 -> {
                            val value = (bytes[offset].toInt() and 0xFF) or (bytes[offset + 1].toInt() shl 8)
                            ((if (value and 0x8000 != 0) value - 0x10000 else value) / 32768.0)
                        }
                        bits == 24 -> {
                            val value = (bytes[offset].toInt() and 0xFF) or
                                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                                (bytes[offset + 2].toInt() shl 16)
                            val signed = if (value and 0x800000 != 0) value - 0x1000000 else value
                            signed / 8388608.0
                        }
                        else -> {
                            val value = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
                            value / 2147483648.0
                        }
                    }
                    offset += bytesPerSample
                }
                output[frame] = (sum / channels).toFloat().coerceIn(-1f, 1f)
            }
            return AudioBuffer(output, sampleRate, 1)
        }
    }

    fun write(file: File, audio: AudioBuffer, bitDepth: Int = 32) {
        require(bitDepth == 16 || bitDepth == 24 || bitDepth == 32)
        val floatFormat = bitDepth == 32
        val bytesPerSample = bitDepth / 8
        val dataSize = audio.samples.size * bytesPerSample
        BufferedOutputStream(file.outputStream(), 64 * 1024).use { out ->
            out.writeIntLE(RIFF)
            out.writeIntLE(36 + dataSize)
            out.writeIntLE(WAVE)
            out.writeIntLE(FMT)
            out.writeIntLE(16)
            out.writeShortLE(if (floatFormat) 3 else 1)
            out.writeShortLE(1)
            out.writeIntLE(audio.sampleRate)
            out.writeIntLE(audio.sampleRate * bytesPerSample)
            out.writeShortLE(bytesPerSample)
            out.writeShortLE(bitDepth)
            out.writeIntLE(DATA)
            out.writeIntLE(dataSize)
            audio.samples.forEach { sample ->
                val x = sample.coerceIn(-1f, 1f)
                when (bitDepth) {
                    16 -> out.writeShortLE((x * 32767f).toInt())
                    24 -> {
                        val value = (x * 8388607f).toInt()
                        out.write(value and 0xFF); out.write((value ushr 8) and 0xFF); out.write((value ushr 16) and 0xFF)
                    }
                    32 -> out.writeIntLE(java.lang.Float.floatToIntBits(x))
                }
            }
        }
    }

    private fun java.io.InputStream.readIntLE(): Int {
        val a = read(); val b = read(); val c = read(); val d = read()
        if ((a or b or c or d) < 0) throw EOFException()
        return a or (b shl 8) or (c shl 16) or (d shl 24)
    }

    private fun java.io.InputStream.readBytesExact(size: Int): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = read(bytes, offset, size - offset)
            if (count < 0) throw EOFException()
            offset += count
        }
        return bytes
    }

    private fun java.io.InputStream.skipBytesExact(size: Int) {
        var remaining = size.toLong()
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) remaining -= skipped else if (read() >= 0) remaining-- else throw EOFException()
        }
    }

    private fun java.io.OutputStream.writeIntLE(value: Int) {
        write(value and 0xFF); write((value ushr 8) and 0xFF); write((value ushr 16) and 0xFF); write((value ushr 24) and 0xFF)
    }

    private fun java.io.OutputStream.writeShortLE(value: Int) {
        write(value and 0xFF); write((value ushr 8) and 0xFF)
    }
}
