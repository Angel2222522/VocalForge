package com.angel.vocalforge.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.angel.vocalforge.model.PitchSettings
import com.angel.vocalforge.model.ScaleKind
import com.angel.vocalforge.model.midiToName
import com.angel.vocalforge.project.AppScreen
import com.angel.vocalforge.project.EditorState
import com.angel.vocalforge.project.VocalForgeViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocalForgeApp(viewModel: VocalForgeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var createDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importAudio) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri -> uri?.let { viewModel.export(it) } }
    val recordLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) viewModel.startRecording() else scope.launch { snackbar.showSnackbar("Το μικρόφωνο χρειάζεται άδεια για εγγραφή") }
    }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it.text); viewModel.dismissMessage() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding), color = MaterialTheme.colorScheme.background) {
            when (state.screen) {
                AppScreen.HOME -> HomeScreen(
                    state = state,
                    onCreate = { createDialog = true },
                    onImport = { importLauncher.launch(arrayOf("audio/*")) },
                    onRecord = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) viewModel.startRecording()
                        else recordLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onOpen = viewModel::openProject,
                    onDelete = viewModel::deleteProject,
                    onDuplicate = viewModel::duplicateProject
                )
                AppScreen.EDITOR -> EditorScreen(
                    state = state,
                    onBack = viewModel::closeProject,
                    onImport = { importLauncher.launch(arrayOf("audio/*")) },
                    onRecord = {
                        if (state.isRecording) viewModel.stopRecording()
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) viewModel.startRecording()
                        else recordLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onAnalyze = viewModel::analyze,
                    onRender = viewModel::render,
                    onOriginal = viewModel::playOriginal,
                    onProcessed = viewModel::playProcessed,
                    onExport = { exportLauncher.launch("${state.selectedProject?.summary?.name ?: "vocalforge"}.wav") },
                    onSelection = viewModel::setSelection,
                    onSettings = viewModel::updateSettings,
                    onManualEdit = viewModel::addManualEdit,
                    onClearEdits = viewModel::clearEdits,
                    onRename = viewModel::renameProject
                )
            }
        }
    }
    if (createDialog) {
        CreateProjectDialog(onDismiss = { createDialog = false }, onCreate = { name -> createDialog = false; viewModel.createProject(name) })
    }
}

@Composable
private fun HomeScreen(
    state: EditorState,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onRecord: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDuplicate: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("VOCALFORGE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("Offline vocal studio", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(onClick = onCreate) { Icon(Icons.Default.Add, "New project") }
        }
        Spacer(Modifier.height(26.dp))
        Text("Your sessions", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Original audio stays untouched on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("Import audio", Icons.Default.AudioFile, onImport, Modifier.weight(1f))
            ActionTile("Record", Icons.Default.FiberManualRecord, onRecord, Modifier.weight(1f), accent = Color(0xFFFF778D))
        }
        Spacer(Modifier.height(24.dp))
        if (state.projects.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MusicNote, null, tint = Color(0xFF414653), modifier = Modifier.size(54.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Create a project to begin", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Import a take or record a clean vocal", color = Color(0xFF666A77), fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(state.projects, key = { it.id }) { project ->
                    ProjectCard(project.name, project.durationSeconds, project.updatedAt, project.hasAudio, onClick = { onOpen(project.id) }, onDuplicate = { onDuplicate(project.id) }, onDelete = { onDelete(project.id) })
                }
            }
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier, accent: Color = MaterialTheme.colorScheme.primary) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(10.dp)); Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProjectCard(name: String, duration: Float, updatedAt: Long, hasAudio: Boolean, onClick: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C2730)), contentAlignment = Alignment.Center) {
                Icon(if (hasAudio) Icons.Default.MusicNote else Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (hasAudio) "${formatDuration(duration)}  ·  ${SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(updatedAt))}" else "Empty project", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(onClick = onDuplicate) { Icon(Icons.Default.ContentCopy, "Duplicate", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun EditorScreen(
    state: EditorState,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onRecord: () -> Unit,
    onAnalyze: () -> Unit,
    onRender: () -> Unit,
    onOriginal: () -> Unit,
    onProcessed: () -> Unit,
    onExport: () -> Unit,
    onSelection: (Float, Float) -> Unit,
    onSettings: (PitchSettings, Boolean) -> Unit,
    onManualEdit: (Int?, Boolean) -> Unit,
    onClearEdits: () -> Unit,
    onRename: (String) -> Unit
) {
    var nameDialog by remember { mutableStateOf(false) }
    val settings = state.settings
    val audio = state.original
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(state.selectedProject?.summary?.name ?: "Project", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (audio != null) "${audio.sampleRate} Hz  ·  ${formatDuration(audio.durationSeconds)}" else "Add a vocal take", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            IconButton(onClick = { nameDialog = true }) { Icon(Icons.Default.Settings, "Project settings") }
            IconButton(onClick = onImport) { Icon(Icons.Default.AudioFile, "Import") }
            IconButton(onClick = onRecord) { Icon(if (state.isRecording) Icons.Default.Pause else Icons.Default.FiberManualRecord, "Record", tint = if (state.isRecording) MaterialTheme.colorScheme.primary else Color(0xFFFF778D)) }
        }
        Divider(color = Color(0xFF242832))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TransportButton("Original", state.playing == "original", onOriginal, Modifier.weight(1f))
            TransportButton("Processed", state.playing == "processed", onProcessed, Modifier.weight(1f), enabled = state.processed != null)
            OutlinedButton(onClick = onExport, enabled = state.processed != null, modifier = Modifier.weight(1f)) { Icon(Icons.Default.FileDownload, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Export WAV") }
        }
        Spacer(Modifier.height(10.dp))
        PitchEditor(audio?.samples, audio?.sampleRate ?: 44100, state.analysis, settings, state.selectionStart, state.selectionEnd, onSelection, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Detected", color = Color(0xFF55E0C1), fontSize = 11.sp)
            Text("Target preview", color = Color(0xFFB9A8FF), fontSize = 11.sp)
            Text(if (state.selectionEnd > state.selectionStart) "Selection ${formatDuration(state.selectionEnd - state.selectionStart)}" else "Drag to select a region", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAnalyze, enabled = audio != null && !state.isBusy, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Refresh, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Analyze") }
            Button(onClick = onRender, enabled = audio != null && state.analysis.isNotEmpty() && !state.isBusy, modifier = Modifier.weight(1f)) { Icon(Icons.Default.GraphicEq, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Render correction") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item { ControlSection("Pitch correction") {
                SettingSlider("Correction amount", settings.correctionAmount, "${(settings.correctionAmount * 100).toInt()}%") { onSettings(settings.copy(correctionAmount = it), false) }
                SettingSlider("Retune speed", settings.retuneSpeedMs, "${settings.retuneSpeedMs.toInt()} ms", 0f..400f) { onSettings(settings.copy(retuneSpeedMs = it), false) }
                SettingSlider("Humanize", settings.humanize, "${(settings.humanize * 100).toInt()}%") { onSettings(settings.copy(humanize = it), false) }
                SettingSlider("Formant preservation", settings.formantPreservation, "${(settings.formantPreservation * 100).toInt()}%") { onSettings(settings.copy(formantPreservation = it), false) }
                SettingSlider("Vibrato preservation", settings.vibratoPreservation, "${(settings.vibratoPreservation * 100).toInt()}%") { onSettings(settings.copy(vibratoPreservation = it), false) }
                SettingSlider("Transition smoothing", settings.transition, "${(settings.transition * 100).toInt()}%") { onSettings(settings.copy(transition = it), false) }
                SettingSlider("Dry / wet", settings.dryWet, "${(settings.dryWet * 100).toInt()}%") { onSettings(settings.copy(dryWet = it), false) }
            } }
            item { ScaleSection(settings, onChange = { onSettings(it, false) }) }
            item {
                ControlSection("Manual editing") {
                    Text("Select a region in the editor, then apply a target note or bypass it.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(60, 62, 64, 65, 67, 69, 71).forEach { midi ->
                            OutlinedButton(onClick = { onManualEdit(midi, false) }) { Text(midiToName(midi)) }
                        }
                        OutlinedButton(onClick = { onManualEdit(null, true) }) { Text("Bypass") }
                        TextButton(onClick = onClearEdits) { Text("Clear edits") }
                    }
                }
            }
        }
        if (state.isBusy) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { state.progress?.fraction?.coerceIn(0f, 1f) ?: 0f }, modifier = Modifier.fillMaxWidth())
            Text("${state.busyLabel}  ${(state.progress?.fraction?.times(100f)?.toInt() ?: 0)}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
    if (nameDialog) RenameDialog(state.selectedProject?.summary?.name ?: "", { nameDialog = false }, { nameDialog = false; onRename(it) })
}

@Composable
private fun TransportButton(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(if (active) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text(label)
    }
}

@Composable
private fun ControlSection(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp)) { Text(title, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); content() }
    }
}

@Composable
private fun SettingSlider(label: String, value: Float, valueLabel: String, range: ClosedFloatingPointRange<Float> = 0f..1f, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontSize = 13.sp); Text(valueLabel, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ScaleSection(settings: PitchSettings, onChange: (PitchSettings) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ControlSection("Key & scale") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Root", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text(midiToName(settings.rootMidi)) }
                DropdownMenu(expanded, { expanded = false }) {
                    (48..72).forEach { midi -> DropdownMenuItem(text = { Text(midiToName(midi)) }, onClick = { expanded = false; onChange(settings.copy(rootMidi = midi)) }) }
                }
            }
            Text("Scale", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text(settings.scaleKind.label) }
                DropdownMenu(expanded, { expanded = false }) {
                    ScaleKind.entries.forEach { kind -> DropdownMenuItem(text = { Text(kind.label) }, onClick = { expanded = false; onChange(settings.copy(scaleKind = kind)) }) }
                }
            }
        }
    }
}

@Composable
private fun CreateProjectDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("Untitled take") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New project") }, text = { OutlinedTextField(name, { name = it }, label = { Text("Project name") }, singleLine = true) }, confirmButton = { TextButton(onClick = { onCreate(name) }) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember(current) { mutableStateOf(current) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Project name") }, text = { OutlinedTextField(name, { name = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { onRename(name) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private fun formatDuration(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(Locale.US, total / 60, total % 60)
}
