package com.angel.vocalforge.model

import kotlin.math.roundToInt

enum class ScaleKind(val label: String) {
    CHROMATIC("Chromatic"),
    MAJOR("Major"),
    NATURAL_MINOR("Natural minor"),
    CUSTOM("Custom")
}

data class PitchSettings(
    val rootMidi: Int = 60,
    val scaleKind: ScaleKind = ScaleKind.CHROMATIC,
    val customMask: Int = 0xFFF,
    val correctionAmount: Float = 0.72f,
    val retuneSpeedMs: Float = 85f,
    val humanize: Float = 0.35f,
    val formantPreservation: Float = 0.9f,
    val vibratoPreservation: Float = 0.85f,
    val transition: Float = 0.55f,
    val dryWet: Float = 1f
) {
    fun mask(): Int = when (scaleKind) {
        ScaleKind.CHROMATIC -> 0xFFF
        ScaleKind.MAJOR -> intArrayOf(0, 2, 4, 5, 7, 9, 11).fold(0) { a, n -> a or (1 shl ((n + rootMidi) % 12)) }
        ScaleKind.NATURAL_MINOR -> intArrayOf(0, 2, 3, 5, 7, 8, 10).fold(0) { a, n -> a or (1 shl ((n + rootMidi) % 12)) }
        ScaleKind.CUSTOM -> customMask
    }

    fun withAmount(value: Float) = copy(correctionAmount = value.coerceIn(0f, 1f))
    fun withRetune(value: Float) = copy(retuneSpeedMs = value.coerceIn(0f, 800f))
}

data class PitchEdit(
    val startSeconds: Float,
    val endSeconds: Float,
    val targetMidi: Int? = null,
    val correctionAmount: Float? = null,
    val disabled: Boolean = false,
    val transition: Float? = null,
    val vibratoPreservation: Float? = null
)

data class AnalysisFrame(
    val timeSeconds: Float,
    val f0Hz: Float,
    val confidence: Float,
    val voiced: Boolean,
    val energy: Float
) {
    val midi: Float?
        get() = if (voiced && f0Hz > 0f) (69f + 12f * kotlin.math.log2(f0Hz / 440f)) else null
}

data class ProjectSummary(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sampleRate: Int = 0,
    val durationSeconds: Float = 0f,
    val hasAudio: Boolean = false,
    val hasAnalysis: Boolean = false
)

data class ProjectSnapshot(
    val summary: ProjectSummary,
    val settings: PitchSettings = PitchSettings(),
    val edits: List<PitchEdit> = emptyList()
)

data class AudioBuffer(
    val samples: FloatArray,
    val sampleRate: Int,
    val channels: Int = 1
) {
    val durationSeconds: Float get() = samples.size.toFloat() / sampleRate.toFloat()
}

data class RenderProgress(val fraction: Float, val stage: String)

data class AppMessage(val text: String, val isError: Boolean = false)

fun midiToName(midi: Int): String {
    val names = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    val octave = midi / 12 - 1
    return "${names[midi.mod(12)]}$octave"
}

fun frequencyToMidi(frequency: Float): Float? =
    if (frequency > 0f) 69f + 12f * kotlin.math.log2(frequency / 440f) else null

fun centsFromMidi(midi: Float): Int = ((midi - midi.roundToInt()) * 100f).roundToInt()
