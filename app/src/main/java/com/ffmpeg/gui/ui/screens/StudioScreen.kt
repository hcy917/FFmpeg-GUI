package com.ffmpeg.gui.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmpeg.gui.ui.AppScreen
import com.ffmpeg.gui.WorkspaceStats
import com.ffmpeg.gui.data.MediaItem
import com.ffmpeg.gui.data.TaskRecord
import com.ffmpeg.gui.data.TaskStatus
import com.ffmpeg.gui.ui.TaskStatusPill
import com.ffmpeg.gui.ui.components.EmptyMediaState
import com.ffmpeg.gui.ui.components.MediaKindIcon
import com.ffmpeg.gui.ui.components.MediaRow
import com.ffmpeg.gui.ui.components.SectionHeader
import com.ffmpeg.gui.ui.components.StudioHero
import com.ffmpeg.gui.ui.formatBytes
import com.ffmpeg.gui.ui.theme.FfmpegAmber
import com.ffmpeg.gui.ui.theme.FfmpegCoral
import com.ffmpeg.gui.ui.theme.FfmpegSky

@Composable
fun StudioScreen(
    media: List<MediaItem>,
    tasks: List<TaskRecord>,
    stats: WorkspaceStats,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onDemo: () -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onOpenTask: (TaskRecord) -> Unit,
    onOpenSettings: () -> Unit,
    onNewChat: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            StudioHero(
                mediaCount = stats.mediaCount,
                totalBytes = stats.totalBytes,
                onImportFiles = onImportFiles,
                onImportFolder = onImportFolder,
                presetCount = com.ffmpeg.gui.engine.Presets.all.size,
            )
        }

        item {
            SectionHeader(
                title = "Quick actions",
                subtitle = "Everything happens on-device",
                trailing = {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            "OFFLINE FIRST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                },
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(
                        title = "Convert",
                        subtitle = "Batch + presets",
                        icon = Icons.Rounded.SwapHoriz,
                        accent = MaterialTheme.colorScheme.primaryContainer,
                        onAccent = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = { onNavigate(AppScreen.Convert) },
                        modifier = Modifier.weight(1f),
                    )
                    QuickAction(
                        title = "Merge",
                        subtitle = "All formats",
                        icon = Icons.Rounded.CallMerge,
                        accent = FfmpegAmber.copy(alpha = 0.35f),
                        onAccent = MaterialTheme.colorScheme.onSurface,
                        onClick = { onNavigate(AppScreen.Merge) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(
                        title = "Split",
                        subtitle = "Any output",
                        icon = Icons.Rounded.ContentCut,
                        accent = FfmpegCoral.copy(alpha = 0.30f),
                        onAccent = MaterialTheme.colorScheme.onSurface,
                        onClick = { onNavigate(AppScreen.Split) },
                        modifier = Modifier.weight(1f),
                    )
                    QuickAction(
                        title = "Lab",
                        subtitle = "Python + AI",
                        icon = Icons.Rounded.Science,
                        accent = FfmpegSky.copy(alpha = 0.32f),
                        onAccent = MaterialTheme.colorScheme.onSurface,
                        onClick = { onNavigate(AppScreen.Lab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (tasks.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Activity",
                    subtitle = "Live FFmpeg sessions",
                    trailing = {
                        TextButton(onClick = { tasks.firstOrNull()?.let(onOpenTask) }) { Text("Details") }
                    },
                )
            }
            items(tasks.take(3), key = { it.id }) { task ->
                TaskSummaryCard(task, onClick = { onOpenTask(task) })
            }
        }

        item {
            SectionHeader(
                title = "Media library",
                subtitle = if (media.isEmpty()) "Nothing here yet" else "${media.size} assets · ${formatBytes(stats.totalBytes)}",
                trailing = if (media.isNotEmpty()) {
                    { TextButton(onClick = { onNavigate(AppScreen.Convert) }) { Text("Convert") } }
                } else null,
            )
        }

        if (media.isEmpty()) {
            item {
                EmptyMediaState(
                    onImportFiles = onImportFiles,
                    onImportFolder = onImportFolder,
                    onDemo = onDemo,
                )
            }
        } else {
            items(media.take(5), key = { it.id }) { item ->
                MediaRow(
                    item = item,
                    selected = false,
                    onClick = { onNavigate(AppScreen.Convert) },
                    trailing = { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
            if (media.size > 5) {
                item {
                    TextButton(onClick = { onNavigate(AppScreen.Convert) }, modifier = Modifier.fillMaxWidth()) {
                        Text("See all ${media.size} assets")
                    }
                }
            }
        }

        item {
            AiBanner(onClick = onNewChat, onSettings = onOpenSettings)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaKindIcon(com.ffmpeg.gui.data.MediaKind.OTHER, Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "FFmpeg runs locally · Python 3.11 embedded · no cloud required",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuickAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onAccent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = accent),
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(40.dp).background(onAccent.copy(alpha = 0.12f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = onAccent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = onAccent)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = onAccent.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun TaskSummaryCard(task: TaskRecord, onClick: () -> Unit) {
    val progress by animateFloatAsState(task.progress, label = "taskProgress")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TaskStatusPill(task.status)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                task.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (task.status == TaskStatus.RUNNING) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AiBanner(onClick: () -> Unit, onSettings: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "MEDIA COPILOT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        letterSpacing = 1.4.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Ask for a cut, a conversion, or a script.",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "The copilot can inspect every workspace file, run FFmpeg, and execute Python through guarded tools.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)) {
                        Text(
                            "Open copilot",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onSettings) {
                        Text("Configure", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f))
                    }
                }
            }
        }
    }
}
