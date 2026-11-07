package com.ffmpeg.gui.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ffmpeg.gui.data.MediaItem
import com.ffmpeg.gui.data.SplitMode
import com.ffmpeg.gui.data.SplitOptions
import com.ffmpeg.gui.ui.components.EmptyMediaState
import com.ffmpeg.gui.ui.components.ImportActions
import com.ffmpeg.gui.ui.components.MediaRow
import com.ffmpeg.gui.ui.components.PillTabs
import com.ffmpeg.gui.ui.components.ScreenIntro
import com.ffmpeg.gui.ui.components.SectionHeader
import com.ffmpeg.gui.ui.formatClock
import kotlin.math.ceil
import kotlin.math.max

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitScreen(
    media: List<MediaItem>,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onSplit: (List<MediaItem>, SplitOptions) -> Unit,
) {
    val selectedIds = remember { mutableStateListOf<String>() }
    var mode by rememberSaveable { mutableStateOf(SplitMode.DURATION.name) }
    var seconds by rememberSaveable { mutableStateOf("60") }
    var parts by rememberSaveable { mutableStateOf(3f) }
    var format by rememberSaveable { mutableStateOf("mp4") }
    var reencode by rememberSaveable { mutableStateOf(true) }
    val selected = media.filter { it.id in selectedIds }
    val splitMode = if (mode == SplitMode.PARTS.name) SplitMode.PARTS else SplitMode.DURATION

    Column(Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScreenIntro(
                    eyebrow = "Segment cutter",
                    title = "Cut once.\nShip everywhere.",
                    subtitle = "Slice any media by duration or by part count, then choose the output container. Each source becomes its own folder.",
                )
            }
            item { ImportActions(onFiles = onImportFiles, onFolder = onImportFolder) }
            if (media.isEmpty()) {
                item {
                    EmptyMediaState(
                        onImportFiles = onImportFiles,
                        onImportFolder = onImportFolder,
                        onDemo = {},
                        title = "Nothing to split",
                        body = "Import a file or folder to start slicing.",
                    )
                }
            } else {
                item { SectionHeader("Cut mode", "Choose how the timeline is divided") }
                item {
                    PillTabs(
                        options = listOf("By duration", "By parts"),
                        selected = if (splitMode == SplitMode.PARTS) "By parts" else "By duration",
                        onSelect = { mode = if (it == "By parts") SplitMode.PARTS.name else SplitMode.DURATION.name },
                    )
                }
                item {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (splitMode == SplitMode.DURATION) {
                                Text("Segment length (seconds)", style = MaterialTheme.typography.labelLarge)
                                OutlinedTextField(
                                    value = seconds,
                                    onValueChange = { value -> seconds = value.filter { it.isDigit() }.take(6) },
                                    label = { Text("Seconds per part") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(15, 30, 60, 120, 300).forEach { preset ->
                                        FilterChip(
                                            selected = seconds == preset.toString(),
                                            onClick = { seconds = preset.toString() },
                                            label = { Text(if (preset < 60) "${preset}s" else "${preset / 60}m") },
                                        )
                                    }
                                }
                            } else {
                                Text("Number of parts · ${parts.toInt()}", style = MaterialTheme.typography.labelLarge)
                                Slider(
                                    value = parts,
                                    onValueChange = { parts = it },
                                    valueRange = 2f..12f,
                                    steps = 9,
                                )
                                Text(
                                    "Each selected source is divided into ${parts.toInt()} equal segments.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item { SectionHeader("Output format", "The container and codecs for every part") }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("mp4", "mkv", "webm", "mov", "mp3", "m4a", "wav", "flac").forEach { ext ->
                            FilterChip(selected = format == ext, onClick = { format = ext }, label = { Text(ext.uppercase()) })
                        }
                    }
                }
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Re-encode segments", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Off = stream copy. Fast, but the source codecs must fit the selected container.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = reencode, onCheckedChange = { reencode = it })
                        }
                    }
                }
                if (!reencode) {
                    item {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Stream copy only works when source codecs are compatible with ${format.uppercase()}. Use re-encode for mixed inputs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }
                item {
                    SectionHeader(
                        "Sources",
                        "${selected.size} selected",
                        trailing = {
                            TextButton(onClick = {
                                if (selectedIds.size == media.size) selectedIds.clear()
                                else {
                                    selectedIds.clear(); selectedIds.addAll(media.map { it.id })
                                }
                            }) { Text(if (selectedIds.size == media.size) "Clear" else "Select all") }
                        },
                    )
                }
                items(media, key = { it.id }) { item ->
                    val estimate = if (splitMode == SplitMode.PARTS) parts.toInt()
                    else max(1, ceil((item.durationMs / 1000.0) / (seconds.toIntOrNull() ?: 60)).toInt())
                    MediaRow(
                        item = item,
                        selected = item.id in selectedIds,
                        onClick = { if (selectedIds.contains(item.id)) selectedIds.remove(item.id) else selectedIds.add(item.id) },
                        badge = if (item.id in selectedIds) "$estimate" else null,
                    )
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 3.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (selected.isEmpty()) "Select sources to split" else "${selected.size} source(s)",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        buildString {
                            append(if (splitMode == SplitMode.PARTS) "$parts parts" else "${seconds.toIntOrNull() ?: 60}s parts")
                            append(" · ")
                            append(format.uppercase())
                            append(if (reencode) " · re-encode" else " · copy")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = {
                        onSplit(
                            selected,
                            SplitOptions(
                                mode = splitMode,
                                secondsPerPart = seconds.toIntOrNull() ?: 60,
                                parts = parts.toInt(),
                                outputFormat = format,
                                reencode = reencode,
                            ),
                        )
                        selectedIds.clear()
                    },
                    enabled = selected.isNotEmpty(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.ContentCut, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Split")
                }
            }
        }
    }
}
