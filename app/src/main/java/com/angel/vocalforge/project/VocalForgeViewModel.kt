package com.angel.vocalforge.project

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.angel.vocalforge.audio.AudioDecoder
import com.angel.vocalforge.audio.AudioPlayer
import com.angel.vocalforge.audio.AudioRecorder
import com.angel.vocalforge.audio.WavCodec
import com.angel.vocalforge.data.ProjectStore
import com.angel.vocalforge.dsp.NativeAudioEngine
import com.angel.vocalforge.model.AnalysisFrame
import com.angel.vocalforge.model.AppMessage
import com.angel.vocalforge.model.AudioBuffer
import com.angel.vocalforge.model.PitchEdit
import com.angel.vocalforge.model.PitchSettings
import com.angel.vocalforge.model.ProjectSnapshot
import com.angel.vocalforge.model.ProjectSummary
import com.angel.vocalforge.model.RenderProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class AppScreen { HOME, EDITOR }

data class EditorState(
    val screen: AppScreen = AppScreen.HOME,
    val projects: List<ProjectSummary> = emptyList(),
    val selectedProject: ProjectSnapshot? = null,
    val original: AudioBuffer? = null,
    val processed: AudioBuffer? = null,
    val analysis: List<AnalysisFrame> = emptyList(),
    val settings: PitchSettings = PitchSettings(),
    val edits: List<PitchEdit> = emptyList(),
    val selectionStart: Float = 0f,
    val selectionEnd: Float = 0f,
    val isBusy: Boolean = false,
    val busyLabel: String = "",
    val progress: RenderProgress? = null,
    val isRecording: Boolean = false,
    val playing: String? = null,
    val message: AppMessage? = null
)

class VocalForgeViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)
    private val recorder = AudioRecorder()
    private val player = AudioPlayer()
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()
    private var busyJob: Job? = null

    init { refreshProjects() }

    fun refreshProjects() { _state.update { it.copy(projects = store.list()) } }

    fun createProject(name: String = "Untitled take") {
        val snapshot = store.create(name)
        openLoaded(snapshot)
    }

    fun renameProject(name: String) {
        val id = _state.value.selectedProject?.summary?.id ?: return
        store.rename(id, name)
        openProject(id)
    }

    fun deleteProject(id: String) {
        if (_state.value.selectedProject?.summary?.id == id) closeProject()
        store.delete(id)
        refreshProjects()
    }

    fun duplicateProject(id: String) {
        store.duplicate(id)?.let { refreshProjects(); openProject(it.summary.id) }
    }

    fun openProject(id: String) {
        val snapshot = store.load(id) ?: return showError("Το project δεν βρέθηκε")
        openLoaded(snapshot)
        viewModelScope.launch(Dispatchers.IO) {
            val original = runCatching { store.originalFile(id).takeIf(File::isFile)?.let(WavCodec::read) }.getOrNull()
            val processed = runCatching { store.processedFile(id).takeIf(File::isFile)?.let(WavCodec::read) }.getOrNull()
            val packed = store.loadAnalysis(id)
            withContext(Dispatchers.Main.immediate) {
                _state.update { it.copy(original = original, processed = processed, analysis = packed?.let(NativeAudioEngine::decodeAnalysis).orEmpty()) }
            }
        }
    }

    private fun openLoaded(snapshot: ProjectSnapshot) {
        player.stop()
        _state.update {
            it.copy(
                screen = AppScreen.EDITOR,
                selectedProject = snapshot,
                settings = snapshot.settings,
                edits = snapshot.edits,
                original = null,
                processed = null,
                analysis = emptyList(),
                playing = null,
                message = null
            )
        }
        refreshProjects()
    }

    fun closeProject() {
        player.stop()
        _state.update { it.copy(screen = AppScreen.HOME, selectedProject = null, original = null, processed = null, analysis = emptyList(), playing = null) }
        refreshProjects()
    }

    fun importAudio(uri: Uri) {
        if (_state.value.selectedProject == null) createProject("Imported take")
        val id = _state.value.selectedProject?.summary?.id ?: return
        runBusy("Importing audio…") {
            val audio = AudioDecoder.decode(getApplication(), uri)
            store.saveOriginal(id) { WavCodec.write(it, audio, 32) }
            store.updateAudioMetadata(id, audio.sampleRate, audio.durationSeconds)
            _state.update { it.copy(original = audio, processed = null, analysis = emptyList()) }
            refreshProjects()
            analyzeInternal(id, audio)
        }
    }

    fun startRecording() {
        if (_state.value.isRecording) return
        if (_state.value.selectedProject == null) createProject("Recorded take")
        _state.update { it.copy(isRecording = true, message = null) }
        recorder.start(viewModelScope) { audio ->
            _state.update { it.copy(isRecording = false) }
            if (audio == null) return@start
            val id = _state.value.selectedProject?.summary?.id ?: return@start
            runBusy("Saving recording…") {
                store.saveOriginal(id) { WavCodec.write(it, audio, 32) }
                store.updateAudioMetadata(id, audio.sampleRate, audio.durationSeconds)
                _state.update { it.copy(original = audio, processed = null, analysis = emptyList()) }
                refreshProjects()
                analyzeInternal(id, audio)
            }
        }
    }

    fun stopRecording() { recorder.stop() }

    fun analyze() {
        val id = _state.value.selectedProject?.summary?.id ?: return
        val audio = _state.value.original ?: return showError("Δεν υπάρχει ηχητικό αρχείο")
        runBusy("Analyzing pitch…") { analyzeInternal(id, audio) }
    }

    private suspend fun analyzeInternal(id: String, audio: AudioBuffer) {
        _state.update { it.copy(progress = RenderProgress(0.02f, "Finding vocal regions")) }
        val packed = withContext(Dispatchers.Default) { NativeAudioEngine.analyze(audio.samples, audio.sampleRate) }
        store.saveAnalysis(id, packed)
        val frames = NativeAudioEngine.decodeAnalysis(packed)
        _state.update { it.copy(analysis = frames, progress = RenderProgress(1f, "Analysis complete")) }
    }

    fun render() {
        val id = _state.value.selectedProject?.summary?.id ?: return
        val audio = _state.value.original ?: return showError("Δεν υπάρχει ηχητικό αρχείο")
        runBusy("Rendering correction…") {
            var packed = store.loadAnalysis(id)
            if (packed == null) {
                packed = withContext(Dispatchers.Default) { NativeAudioEngine.analyze(audio.samples, audio.sampleRate) }
                store.saveAnalysis(id, packed)
                _state.update { it.copy(analysis = NativeAudioEngine.decodeAnalysis(packed!!)) }
            }
            val settings = _state.value.settings
            val edits = NativeAudioEngine.packEdits(_state.value.edits)
            val output = withContext(Dispatchers.Default) {
                NativeAudioEngine.render(
                    audio.samples, audio.sampleRate, packed!!, settings.mask(), settings.rootMidi,
                    settings.correctionAmount, settings.retuneSpeedMs, settings.humanize,
                    settings.formantPreservation, settings.vibratoPreservation, settings.transition,
                    settings.dryWet, edits
                ) { fraction -> _state.update { it.copy(progress = RenderProgress(fraction, "Rendering correction")) } }
            }
            store.saveProcessed(id) { WavCodec.write(it, AudioBuffer(output, audio.sampleRate), 32) }
            _state.update { it.copy(processed = AudioBuffer(output, audio.sampleRate), progress = RenderProgress(1f, "Render complete")) }
            refreshProjects()
        }
    }

    fun updateSettings(settings: PitchSettings, rerender: Boolean = false) {
        val id = _state.value.selectedProject?.summary?.id ?: return
        store.saveSettings(id, settings)
        _state.update { it.copy(settings = settings, processed = if (rerender) it.processed else null) }
    }

    fun setSelection(start: Float, end: Float) {
        _state.update { it.copy(selectionStart = minOf(start, end).coerceAtLeast(0f), selectionEnd = maxOf(start, end).coerceAtLeast(0f)) }
    }

    fun addManualEdit(targetMidi: Int?, disabled: Boolean = false) {
        val state = _state.value
        val id = state.selectedProject?.summary?.id ?: return
        if (state.selectionEnd <= state.selectionStart) return showError("Επίλεξε πρώτα περιοχή στο editor")
        val edit = PitchEdit(state.selectionStart, state.selectionEnd, targetMidi = targetMidi, disabled = disabled)
        val edits = state.edits.filterNot { it.startSeconds < edit.endSeconds && edit.startSeconds < it.endSeconds } + edit
        store.saveEdits(id, edits)
        _state.update { it.copy(edits = edits, processed = null) }
    }

    fun clearEdits() {
        val id = _state.value.selectedProject?.summary?.id ?: return
        store.saveEdits(id, emptyList())
        _state.update { it.copy(edits = emptyList(), processed = null) }
    }

    fun playOriginal() = play("original", _state.value.original)
    fun playProcessed() = play("processed", _state.value.processed)

    private fun play(kind: String, audio: AudioBuffer?) {
        if (audio == null) return showError(if (kind == "processed") "Κάνε πρώτα render" else "Δεν υπάρχει αρχικό audio")
        if (_state.value.playing == kind) { player.stop(); _state.update { it.copy(playing = null) }; return }
        player.play(viewModelScope, audio) { _state.update { it.copy(playing = null) } }
        _state.update { it.copy(playing = kind) }
    }

    fun stopPlayback() { player.stop(); _state.update { it.copy(playing = null) } }

    fun export(uri: Uri, processed: Boolean = true, bitDepth: Int = 32) {
        val audio = if (processed) _state.value.processed else _state.value.original
        if (audio == null) return showError(if (processed) "Κάνε πρώτα render" else "Δεν υπάρχει αρχικό audio")
        runBusy("Exporting WAV…") {
            val temp = File.createTempFile("vocalforge-export-", ".wav", getApplication<Application>().cacheDir)
            WavCodec.write(temp, audio, bitDepth)
            getApplication<Application>().contentResolver.openOutputStream(uri).use { output ->
                requireNotNull(output) { "Cannot open export destination" }
                temp.inputStream().use { it.copyTo(output, 64 * 1024) }
            }
            temp.delete()
        }
    }

    private fun runBusy(label: String, action: suspend () -> Unit) {
        busyJob?.cancel()
        busyJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isBusy = true, busyLabel = label, progress = RenderProgress(0f, label), message = null) }
            try {
                action()
                _state.update { it.copy(isBusy = false, busyLabel = "") }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _state.update { it.copy(isBusy = false, busyLabel = "", progress = null) }
            } catch (error: Throwable) {
                _state.update { it.copy(isBusy = false, busyLabel = "", progress = null, message = AppMessage(error.message ?: "Η λειτουργία απέτυχε", true)) }
            }
        }
    }

    private fun showError(text: String) { _state.update { it.copy(message = AppMessage(text, true)) } }

    fun dismissMessage() { _state.update { it.copy(message = null) } }

    override fun onCleared() {
        recorder.stop()
        player.stop()
        busyJob?.cancel()
        super.onCleared()
    }
}
