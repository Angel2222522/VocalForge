package com.angel.vocalforge.dsp

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Runs on a device/emulator because the real JNI DSP library is required. */
@RunWith(AndroidJUnit4::class)
class NativeDspInstrumentedTest {
    @Test
    fun detectsKnownMusicalTone() {
        val sampleRate = 48_000
        val frequency = 220f
        val audio = FloatArray(sampleRate * 2) { i -> (0.65 * sin(2.0 * PI * frequency * i / sampleRate)).toFloat() }
        val frames = NativeAudioEngine.decodeAnalysis(NativeAudioEngine.analyze(audio, sampleRate))
        val voiced = frames.filter { it.voiced && it.confidence > .3f }
        val median = voiced.map { it.f0Hz }.sorted()[voiced.size / 2]
        assertTrue("F0 median was $median", abs(median - frequency) < 3f)
    }
}
