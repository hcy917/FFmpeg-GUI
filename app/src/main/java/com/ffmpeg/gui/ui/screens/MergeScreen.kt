package com.ffmpeg.gui.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ffmpeg.gui.data.MediaItem
import com.ffmpeg.gui.data.MediaKind
import com.ffmpeg.gui.data.MergeOptions
import com.ffmpeg.gui.ui.components.EmptyMediaState
import com.ffmpeg.gui.ui.components.ImportActions
import com.ffmpeg.gui.ui.components.InfoRow
import com.ffmpeg.gui.ui.components.MediaKindIcon
import com.ffmpeg.gui.ui.components.MediaRow
import com.ffmpeg.gui.ui.components.PillTabs
import com.ffmpeg.gui.ui.components.ScreenIntro
import com.ffmpeg.gui.ui.components.SectionHeader
import com.ffmpeg.gui.ui.formatClock

@Composable
fun MergeScreen(
    media: List<MediaItem>,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onMerge: (List<MediaItem>, MergeOptions) -> Unit,
) {
    val order = remember { mutableStateListOf<String>() }
    var resolution by rememberSaveable { mutableStateOf("1080p") }
    var fps by rememberSaveable { mutableStateOf(30) }
    var container by rememberSaveable { mutableStateOf("mp4") }
    val selected = order.mapNotNull { id -> media.firstOrNull { it.id == id } }
    val resolutionPairs = listOf("1080p" to (1920 to 1080), "720p" to (1280 to 720), "4K" to (3840 to 2160), "Vertical" to (1080 to 1920))
    val (width, height) = resolutionPairs.firstOrNull { it.first == resolution }?.second ?: (1920 to 1080)
    val allAudio = selected.isNotEmpty() && selected.all { it.kind == MediaKind.AUDIO }
    val hasImages = selected.any { it.kind == MediaKind.IMAGE }
    val totalDuration = selected.sumOf { it.durationMs }

    Column(Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScreenIntro(
                    eyebrow = "Timeline merge",
                    title = "Different sources.\nOne story.",
                    subtitle = "Audio, video and stills are normalised to a shared timeline: resolution, frame rate and 48 kHz stereo audio.",
                )
            }
            item { ImportActions(onFiles = onImportFiles, onFolder = onImportFolder) }
            if (media.isEmpty()) {
                item {
                    EmptyMediaState(
                        onImportFiles = onImportFiles,
                        onImportFolder = onImportFolder,
                        onDemo = {},
                        title = "Add clips to merge",
                        body = "Bring in two or more videos, audio tracks, or images and arrange them below.",
                    )
                }
            } else {
                if (order.isNotEmpty()) {
                    item {
                        SectionHeader(
                            "Sequence",
                            "${order.size} part(s) · ${formatClock(totalDuration)}",
                            trailing = { TextButton(onClick = { order.clear() }) { Text("Clear") } },
                        )
                    }
                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                selected.forEachIndexed { index, item ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                                            Text(
                                                "${index + 1}",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        MediaKindIcon(item.kind, Modifier.size(32.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(item.displayDuration, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = {
                                            if (index > 0) {
                                                val tmp = order[index - 1]; order[index - 1] = order[index]; order[index] = tmp
                                            }
                                        }, enabled = index > 0) {
                                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = {
                                            if (index < order.lastIndex) {
                                                val tmp = order[index + 1]; order[index + 1] = order[index]; order[index] = tmp
                                            }
                                        }, enabled = index < order.lastIndex) {
                                            Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { order.removeAt(index) }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    SectionHeader(
                        "Output settings",
                        "All inputs are re-encoded for seamless joins",
                    )
                }
                item {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Container", style = MaterialTheme.typography.labelLarge)
                            PillTabs(options = listOf("mp4", "mkv", "mov", "webm"), selected = container, onSelect = { container = it })
                            Spacer(Modifier.height(2.dp))
                            Text("Canvas", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                resolutionPairs.forEach { (label, _) ->
                                    FilterChip(selected = resolution == label, onClick = { resolution = label }, label = { Text(label) })
                                }
                            }
                            Text("Frame rate", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(24, 25, 30, 60).forEach { value ->
                                    FilterChip(selected = fps == value, onClick = { fps = value }, label = { Text("$value fps") })
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            InfoRow("Resolution", "${width}×${height}")
                            InfoRow("Pipelines", if (allAudio) "Audio concat (AAC)" else "Video concat + stereo AAC")
                            InfoRow("Parts", selected.size.toString())
                        }
                    }
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (allAudio) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (allAudio) Icons.Rounded.GraphicEq else Icons.Rounded.CallMerge,
                                contentDescription = null,
                                tint = if (allAudio) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (allAudio) "Audio-only sources detected. A single audio file will be produced."
                                else if (hasImages) "Images become still segments. Missing audio becomes silence."
                                else "Every file is scaled, padded, retimed and joined in one FFmpeg pass.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (allAudio) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                item { SectionHeader("Add to the timeline", "Tap in the order you want them to play") }
                items(media, key = { it.id }) { item ->
                    val index = order.indexOf(item.id)
                    MediaRow(
                        item = item,
                        selected = index >= 0,
                        onClick = {
                            if (index >= 0) order.remove(item.id) else order.add(item.id)
                        },
                        badge = if (index >= 0) "${index + 1}" else null,
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
                        if (selected.isEmpty()) "Select two or more parts" else "${selected.size} parts · ${formatClock(totalDuration)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (allAudio) "Audio · ${container.uppercase()}" else "${resolution} · ${fps} fps · ${container.uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = {
                        onMerge(selected, MergeOptions(width = width, height = height, fps = fps, container = container))
                        order.clear()
                    },
                    enabled = selected.size >= 2,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.CallMerge, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Merge")
                }
            }
        }
    }
}
