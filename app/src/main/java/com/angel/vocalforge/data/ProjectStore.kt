package com.angel.vocalforge.data

import android.content.Context
import com.angel.vocalforge.model.PitchEdit
import com.angel.vocalforge.model.PitchSettings
import com.angel.vocalforge.model.ProjectSnapshot
import com.angel.vocalforge.model.ProjectSummary
import com.angel.vocalforge.model.ScaleKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class ProjectStore(context: Context) {
    private val root = File(context.filesDir, "vocalforge/projects").apply { mkdirs() }

    fun list(): List<ProjectSummary> = root.listFiles()
        ?.asSequence()
        ?.filter { it.isDirectory }
        ?.mapNotNull { readSummary(it) }
        ?.sortedByDescending { it.updatedAt }
        ?.toList()
        ?: emptyList()

    fun create(name: String): ProjectSnapshot {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val dir = File(root, id).apply { mkdirs() }
        val snapshot = ProjectSnapshot(ProjectSummary(id, name.trim().ifBlank { "Untitled take" }, now, now))
        writeSnapshot(dir, snapshot)
        return snapshot
    }

    fun load(id: String): ProjectSnapshot? {
        val dir = File(root, id)
        if (!dir.isDirectory) return null
        val summary = readSummary(dir) ?: return null
        val settings = readSettings(File(dir, SETTINGS_FILE))
        val edits = readEdits(File(dir, EDITS_FILE))
        return ProjectSnapshot(summary, settings, edits)
    }

    fun rename(id: String, name: String) {
        val dir = File(root, id)
        val current = load(id) ?: return
        writeSummary(dir, current.summary.copy(name = name.trim().ifBlank { current.summary.name }, updatedAt = System.currentTimeMillis()))
    }

    fun delete(id: String) {
        File(root, id).deleteRecursively()
    }

    fun duplicate(id: String): ProjectSnapshot? {
        val source = File(root, id)
        val original = load(id) ?: return null
        val now = System.currentTimeMillis()
        val newId = UUID.randomUUID().toString()
        val target = File(root, newId).apply { mkdirs() }
        source.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(source)
            val destination = File(target, relative.path).apply { parentFile?.mkdirs() }
            file.copyTo(destination, overwrite = true)
        }
        val result = original.copy(summary = original.summary.copy(id = newId, name = "${original.summary.name} copy", createdAt = now, updatedAt = now))
        writeSnapshot(target, result)
        return result
    }

    fun originalFile(id: String): File = File(File(root, id), ORIGINAL_FILE)
    fun processedFile(id: String): File = File(File(root, id), PROCESSED_FILE)

    fun saveOriginal(id: String, bytesWriter: (File) -> Unit): File {
        val dir = File(root, id).apply { mkdirs() }
        val temp = File.createTempFile("original-", ".tmp", dir)
        bytesWriter(temp)
        val destination = File(dir, ORIGINAL_FILE)
        check(temp.renameTo(destination)) { "Could not commit original audio" }
        touch(id)
        return destination
    }

    fun saveProcessed(id: String, bytesWriter: (File) -> Unit): File {
        val dir = File(root, id).apply { mkdirs() }
        val temp = File.createTempFile("processed-", ".tmp", dir)
        bytesWriter(temp)
        val destination = File(dir, PROCESSED_FILE)
        check(temp.renameTo(destination)) { "Could not commit processed audio" }
        touch(id)
        return destination
    }

    fun updateAudioMetadata(id: String, sampleRate: Int, durationSeconds: Float) {
        val current = load(id) ?: return
        writeSummary(File(root, id), current.summary.copy(
            updatedAt = System.currentTimeMillis(),
            sampleRate = sampleRate,
            durationSeconds = durationSeconds,
            hasAudio = originalFile(id).isFile,
            hasAnalysis = File(File(root, id), ANALYSIS_FILE).isFile
        ))
    }

    fun saveAnalysis(id: String, packed: FloatArray) {
        val dir = File(root, id).apply { mkdirs() }
        val temp = File.createTempFile("analysis-", ".tmp", dir)
        DataOutputStream(BufferedOutputStream(temp.outputStream())).use { out ->
            out.writeInt(ANALYSIS_MAGIC)
            out.writeInt(packed.size)
            packed.forEach(out::writeFloat)
        }
        check(temp.renameTo(File(dir, ANALYSIS_FILE)))
        touch(id)
    }

    fun loadAnalysis(id: String): FloatArray? {
        val file = File(File(root, id), ANALYSIS_FILE)
        if (!file.isFile) return null
        return runCatching {
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                check(input.readInt() == ANALYSIS_MAGIC) { "Unsupported analysis cache" }
                val count = input.readInt()
                require(count in 5..10_000_000)
                FloatArray(count) { input.readFloat() }
            }
        }.getOrNull()
    }

    fun saveSettings(id: String, settings: PitchSettings) {
        val dir = File(root, id).apply { mkdirs() }
        atomicText(File(dir, SETTINGS_FILE), settings.toJson().toString())
        touch(id)
    }

    fun saveEdits(id: String, edits: List<PitchEdit>) {
        val dir = File(root, id).apply { mkdirs() }
        val array = JSONArray()
        edits.forEach { array.put(it.toJson()) }
        atomicText(File(dir, EDITS_FILE), array.toString())
        touch(id)
    }

    fun snapshot(id: String): ProjectSnapshot? = load(id)

    private fun touch(id: String) {
        val current = load(id) ?: return
        writeSummary(File(root, id), current.summary.copy(
            updatedAt = System.currentTimeMillis(),
            sampleRate = current.summary.sampleRate,
            durationSeconds = current.summary.durationSeconds,
            hasAudio = originalFile(id).isFile,
            hasAnalysis = File(File(root, id), ANALYSIS_FILE).isFile
        ))
    }

    private fun writeSnapshot(dir: File, snapshot: ProjectSnapshot) {
        dir.mkdirs()
        writeSummary(dir, snapshot.summary)
        atomicText(File(dir, SETTINGS_FILE), snapshot.settings.toJson().toString())
        val edits = JSONArray()
        snapshot.edits.forEach { edits.put(it.toJson()) }
        atomicText(File(dir, EDITS_FILE), edits.toString())
    }

    private fun writeSummary(dir: File, summary: ProjectSummary) {
        atomicText(File(dir, PROJECT_FILE), summary.toJson().toString())
    }

    private fun readSummary(dir: File): ProjectSummary? = runCatching {
        val json = JSONObject(File(dir, PROJECT_FILE).readText())
        ProjectSummary(
            id = json.getString("id"),
            name = json.getString("name"),
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
            sampleRate = json.optInt("sampleRate", 0),
            durationSeconds = json.optDouble("durationSeconds", 0.0).toFloat(),
            hasAudio = json.optBoolean("hasAudio", originalFile(json.getString("id")).isFile),
            hasAnalysis = json.optBoolean("hasAnalysis", File(dir, ANALYSIS_FILE).isFile)
        )
    }.getOrNull()

    private fun readSettings(file: File): PitchSettings = runCatching {
        val j = JSONObject(file.readText())
        PitchSettings(
            rootMidi = j.optInt("rootMidi", 60),
            scaleKind = runCatching { ScaleKind.valueOf(j.optString("scaleKind", ScaleKind.CHROMATIC.name)) }.getOrDefault(ScaleKind.CHROMATIC),
            customMask = j.optInt("customMask", 0xFFF),
            correctionAmount = j.optDouble("correctionAmount", .72).toFloat(),
            retuneSpeedMs = j.optDouble("retuneSpeedMs", 85.0).toFloat(),
            humanize = j.optDouble("humanize", .35).toFloat(),
            formantPreservation = j.optDouble("formantPreservation", .9).toFloat(),
            vibratoPreservation = j.optDouble("vibratoPreservation", .85).toFloat(),
            transition = j.optDouble("transition", .55).toFloat(),
            dryWet = j.optDouble("dryWet", 1.0).toFloat()
        )
    }.getOrDefault(PitchSettings())

    private fun readEdits(file: File): List<PitchEdit> = runCatching {
        val array = JSONArray(file.readText())
        buildList {
            for (i in 0 until array.length()) {
                val j = array.getJSONObject(i)
                add(PitchEdit(
                    startSeconds = j.getDouble("start").toFloat(),
                    endSeconds = j.getDouble("end").toFloat(),
                    targetMidi = if (j.isNull("targetMidi")) null else j.optInt("targetMidi"),
                    correctionAmount = if (j.isNull("correctionAmount")) null else j.optDouble("correctionAmount").toFloat(),
                    disabled = j.optBoolean("disabled", false),
                    transition = if (j.isNull("transition")) null else j.optDouble("transition").toFloat(),
                    vibratoPreservation = if (j.isNull("vibratoPreservation")) null else j.optDouble("vibratoPreservation").toFloat()
                ))
            }
        }
    }.getOrDefault(emptyList())

    private fun atomicText(destination: File, text: String) {
        val temp = File.createTempFile("write-", ".tmp", destination.parentFile)
        temp.writeText(text)
        check(temp.renameTo(destination)) { "Could not atomically write ${destination.name}" }
    }

    private fun ProjectSummary.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("createdAt", createdAt); put("updatedAt", updatedAt)
        put("sampleRate", sampleRate); put("durationSeconds", durationSeconds)
        put("hasAudio", hasAudio); put("hasAnalysis", hasAnalysis)
    }

    private fun PitchSettings.toJson() = JSONObject().apply {
        put("rootMidi", rootMidi); put("scaleKind", scaleKind.name); put("customMask", customMask)
        put("correctionAmount", correctionAmount); put("retuneSpeedMs", retuneSpeedMs)
        put("humanize", humanize); put("formantPreservation", formantPreservation)
        put("vibratoPreservation", vibratoPreservation); put("transition", transition); put("dryWet", dryWet)
    }

    private fun PitchEdit.toJson() = JSONObject().apply {
        put("start", startSeconds); put("end", endSeconds)
        if (targetMidi == null) put("targetMidi", JSONObject.NULL) else put("targetMidi", targetMidi)
        if (correctionAmount == null) put("correctionAmount", JSONObject.NULL) else put("correctionAmount", correctionAmount)
        put("disabled", disabled)
        if (transition == null) put("transition", JSONObject.NULL) else put("transition", transition)
        if (vibratoPreservation == null) put("vibratoPreservation", JSONObject.NULL) else put("vibratoPreservation", vibratoPreservation)
    }

    companion object {
        private const val PROJECT_FILE = "project.json"
        private const val SETTINGS_FILE = "settings.json"
        private const val EDITS_FILE = "edits.json"
        private const val ORIGINAL_FILE = "original.wav"
        private const val PROCESSED_FILE = "processed.wav"
        private const val ANALYSIS_FILE = "analysis.bin"
        private const val ANALYSIS_MAGIC = 0x56464131
    }
}
