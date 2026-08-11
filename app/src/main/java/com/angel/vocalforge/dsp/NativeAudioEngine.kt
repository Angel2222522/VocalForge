package com.angel.vocalforge.dsp

import com.angel.vocalforge.model.AnalysisFrame
import com.angel.vocalforge.model.PitchEdit

object NativeAudioEngine {
    init { System.loadLibrary("vocalforge_dsp") }

    fun interface ProgressListener { fun onProgress(fraction: Float) }

    @JvmStatic external fun analyze(samples: FloatArray, sampleRate: Int): FloatArray

    @JvmStatic
    external fun render(
        samples: FloatArray,
        sampleRate: Int,
        analysis: FloatArray,
        scaleMask: Int,
        rootMidi: Int,
        correctionAmount: Float,
        retuneSpeedMs: Float,
        humanize: Float,
        formantPreservation: Float,
        vibratoPreservation: Float,
        transition: Float,
        dryWet: Float,
        edits: FloatArray,
        listener: ProgressListener?
    ): FloatArray

    fun decodeAnalysis(packed: FloatArray): List<AnalysisFrame> {
        if (packed.size < 2) return emptyList()
        val count = packed[0].toInt().coerceIn(0, (packed.size - 2) / 5)
        return buildList(count) {
            for (i in 0 until count) {
                val index = 2 + i * 5
                add(AnalysisFrame(
                    timeSeconds = packed[index],
                    f0Hz = packed[index + 1],
                    confidence = packed[index + 2],
                    voiced = packed[index + 3] > .5f,
                    energy = packed[index + 4]
                ))
            }
        }
    }

    fun packEdits(edits: List<PitchEdit>): FloatArray {
        val packed = FloatArray(edits.size * EDIT_FIELDS)
        edits.forEachIndexed { index, edit ->
            val base = index * EDIT_FIELDS
            packed[base] = edit.startSeconds
            packed[base + 1] = edit.endSeconds
            packed[base + 2] = (edit.targetMidi ?: -1).toFloat()
            packed[base + 3] = edit.correctionAmount ?: -1f
            packed[base + 4] = if (edit.disabled) 1f else 0f
            packed[base + 5] = edit.transition ?: -1f
            packed[base + 6] = edit.vibratoPreservation ?: -1f
        }
        return packed
    }

    private const val EDIT_FIELDS = 7
}
