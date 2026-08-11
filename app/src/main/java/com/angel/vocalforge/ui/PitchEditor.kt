package com.angel.vocalforge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.angel.vocalforge.model.AnalysisFrame
import com.angel.vocalforge.model.PitchSettings
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@Composable
fun PitchEditor(
    samples: FloatArray?,
    sampleRate: Int,
    analysis: List<AnalysisFrame>,
    settings: PitchSettings,
    selectionStart: Float,
    selectionEnd: Float,
    onSelectionChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = max(0.1f, (samples?.size?.toFloat() ?: 0f) / max(1, sampleRate))
    val palette = remember {
        listOf(
            Color(0xFF55E0C1), Color(0xFFB9A8FF), Color(0xFFFFC978), Color(0xFF5A6070), Color(0xFF252934)
        )
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(330.dp)
            .pointerInput(duration) {
                detectDragGestures(
                    onDragStart = { start ->
                        val time = (start.x / size.width * duration).coerceIn(0f, duration)
                        onSelectionChanged(time, time)
                    },
                    onDrag = { change, _ ->
                        val time = (change.position.x / size.width * duration).coerceIn(0f, duration)
                        onSelectionChanged(selectionStart, time)
                    }
                )
            }
            .pointerInput(duration) {
                detectTapGestures { tap ->
                    val time = (tap.x / size.width * duration).coerceIn(0f, duration)
                    onSelectionChanged(time, time)
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val waveformHeight = height * .28f
        val pianoTop = waveformHeight + 12f
        val pianoBottom = height - 10f
        val midiLow = 36f
        val midiHigh = 84f
        fun xForTime(time: Float) = time / duration * width
        fun yForMidi(midi: Float) = pianoBottom - (midi - midiLow) / (midiHigh - midiLow) * (pianoBottom - pianoTop)

        drawRect(Color(0xFF0D0F14))
        // Waveform envelope, deliberately downsampled so large takes remain cheap to draw.
        samples?.let { audio ->
            val columns = width.toInt().coerceAtLeast(1)
            val path = Path()
            val lower = Path()
            val perColumn = max(1, audio.size / columns)
            for (column in 0 until columns) {
                val start = column * perColumn
                val end = min(audio.size, start + perColumn)
                var peak = 0f
                for (i in start until end) peak = max(peak, kotlin.math.abs(audio[i]))
                val y = waveformHeight * .5f - peak * waveformHeight * .43f
                val x = column.toFloat()
                if (column == 0) { path.moveTo(x, y); lower.moveTo(x, waveformHeight - (y - waveformHeight * .5f)) }
                else { path.lineTo(x, y); lower.lineTo(x, waveformHeight - (y - waveformHeight * .5f)) }
            }
            drawPath(path, Color(0xFF4C5362), style = androidx.compose.ui.graphics.drawscope.Stroke(1.4f))
            drawPath(lower, Color(0xFF303542), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        }
        drawLine(Color(0xFF303542), Offset(0f, waveformHeight), Offset(width, waveformHeight), 1f)

        // Piano-roll grid and note names at C boundaries.
        for (midi in midiLow.toInt()..midiHigh.toInt()) {
            val y = yForMidi(midi.toFloat())
            val isBlack = midi % 12 in intArrayOf(1, 3, 6, 8, 10)
            drawLine(if (isBlack) palette[4] else Color(0xFF303542), Offset(0f, y), Offset(width, y), if (isBlack) 1f else 1.5f)
            if (midi % 12 == 0) drawLine(Color(0xFF4A4E5B), Offset(0f, y), Offset(width, y), 2f)
        }
        val secondsPerGrid = if (duration > 30f) 5f else if (duration > 12f) 2f else 1f
        var second = 0f
        while (second <= duration) {
            val x = xForTime(second)
            drawLine(Color(0xFF22252E), Offset(x, pianoTop), Offset(x, pianoBottom), 1f)
            second += secondsPerGrid
        }

        if (selectionEnd > selectionStart) {
            drawRect(Color(0x3355E0C1), Offset(xForTime(selectionStart), 0f), Offset(xForTime(selectionEnd), height))
        }

        // Detected pitch is teal; the target contour is lilac. The target is a
        // visual preview of the same note-locking rule used by the native renderer.
        val sourcePath = Path()
        val targetPath = Path()
        var sourceOpen = false
        var targetOpen = false
        analysis.forEach { frame ->
            val sourceMidi = frame.midi
            if (sourceMidi == null || !frame.voiced || frame.confidence < .16f) {
                sourceOpen = false; targetOpen = false; return@forEach
            }
            val x = xForTime(frame.timeSeconds)
            val y = yForMidi(sourceMidi)
            if (!sourceOpen) { sourcePath.moveTo(x, y); sourceOpen = true } else sourcePath.lineTo(x, y)
            val target = nearestScaleMidi(sourceMidi, settings)
            val corrected = sourceMidi + (target - sourceMidi) * settings.correctionAmount
            val targetY = yForMidi(corrected)
            if (!targetOpen) { targetPath.moveTo(x, targetY); targetOpen = true } else targetPath.lineTo(x, targetY)
        }
        drawPath(sourcePath, palette[0], style = androidx.compose.ui.graphics.drawscope.Stroke(2.2f))
        drawPath(targetPath, palette[1], style = androidx.compose.ui.graphics.drawscope.Stroke(2.8f))

        // Time ruler.
        drawLine(Color(0xFF505563), Offset(0f, 0f), Offset(width, 0f), 1f)
        val tickCount = min(20, ceil(duration).toInt().coerceAtLeast(1))
        for (i in 0..tickCount) {
            val x = width * i / tickCount
            drawLine(Color(0xFF6B7080), Offset(x, 0f), Offset(x, 6f), 1f)
        }
    }
}

private fun nearestScaleMidi(source: Float, settings: PitchSettings): Float {
    val rounded = source.toInt()
    var best = rounded
    var bestDistance = Float.MAX_VALUE
    for (candidate in rounded - 12..rounded + 12) {
        if (settings.mask() and (1 shl ((candidate % 12 + 12) % 12)) == 0) continue
        val distance = kotlin.math.abs(candidate - source)
        if (distance < bestDistance) { best = candidate; bestDistance = distance }
    }
    return best.toFloat()
}
