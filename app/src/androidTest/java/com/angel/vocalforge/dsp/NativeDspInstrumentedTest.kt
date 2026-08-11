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
        assertTrue("No voiced frames were detected", voiced.isNotEmpty())
        val median = voiced.map { it.f0Hz }.sorted()[voiced.size / 2]
        assertTrue("F0 median was $median", abs(median - frequency) < 3f)
    }

    @Test
    fun rendersKnownToneWithFiniteSamples() {
        val sampleRate = 48_000
        val audio = FloatArray(sampleRate) { i -> (0.5 * sin(2.0 * PI * 220.0 * i / sampleRate)).toFloat() }
        val analysis = NativeAudioEngine.analyze(audio, sampleRate)
        val rendered = NativeAudioEngine.render(
            audio, sampleRate, analysis, 0xFFF, 60,
            .8f, 60f, .2f, .9f, .8f, .5f, 1f,
            FloatArray(0), null
        )
        assertTrue(rendered.size == audio.size)
        assertTrue(rendered.all { it.isFinite() && it in -1f..1f })
    }
}
