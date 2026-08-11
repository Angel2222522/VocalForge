package com.angel.vocalforge.audio

import com.angel.vocalforge.model.AudioBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sin

class WavCodecTest {
    @Test
    fun floatWavRoundTripsWithoutLossyCodec() {
        val source = FloatArray(4096) { i -> (sin(i * 0.031) * 0.72).toFloat() }
        val file = File.createTempFile("vocalforge-test-", ".wav")
        try {
            WavCodec.write(file, AudioBuffer(source, 48_000), 32)
            val decoded = WavCodec.read(file)
            assertEquals(48_000, decoded.sampleRate)
            assertEquals(source.size, decoded.samples.size)
            assertTrue(decoded.samples.indices.all { abs(decoded.samples[it] - source[it]) < 1e-6f })
        } finally { file.delete() }
    }

    @Test
    fun pcm24RoundTripsWithinQuantizationError() {
        val source = floatArrayOf(-1f, -.5f, -.12345f, 0f, .12345f, .5f, 1f)
        val file = File.createTempFile("vocalforge-test-", ".wav")
        try {
            WavCodec.write(file, AudioBuffer(source, 44_100), 24)
            val decoded = WavCodec.read(file)
            assertEquals(source.size, decoded.samples.size)
            assertTrue(decoded.samples.indices.all { abs(decoded.samples[it] - source[it]) < 2e-6f })
        } finally { file.delete() }
    }
}
