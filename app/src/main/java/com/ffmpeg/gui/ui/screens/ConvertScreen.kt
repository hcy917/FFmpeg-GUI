package com.ffmpeg.gui.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.ffmpeg.gui.engine.Preset
import com.ffmpeg.gui.engine.PresetCategory
import com.ffmpeg.gui.engine.Presets
import com.ffmpeg.gui.ui.components.EmptyMediaState
import com.ffmpeg.gui.ui.components.ImportActions
import com.ffmpeg.gui.ui.components.MediaRow
import com.ffmpeg.gui.ui.components.PillTabs
import com.ffmpeg.gui.ui.components.ScreenIntro
import com.ffmpeg.gui.ui.components.SectionHeader

@Composable
fun ConvertScreen(
    media: List<MediaItem>,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onDemo: () -> Unit,
    onConvert: (List<MediaItem>, String) -> Unit,
) {
    var category by rememberSaveable { mutableStateOf(PresetCategory.VIDEO.label) }
    var presetId by rememberSaveable { mutableStateOf(Presets.video.first().id) }
    val selectedIds = remember { mutableStateListOf<String>() }
    val categories = listOf(PresetCategory.VIDEO.label, PresetCategory.AUDIO.label, PresetCategory.IMAGE.label, PresetCategory.UTILITY.label)
    val visiblePresets = when (category) {
        PresetCategory.AUDIO.label -> Presets.audio
        PresetCategory.IMAGE.label -> Presets.image
        PresetCategory.UTILITY.label -> Presets.utility
        else -> Presets.video
    }
    val selectedPreset = Presets.byId(presetId).takeIf { it in visiblePresets } ?: visiblePresets.first()
    val selectedItems = media.filter { it.id in selectedIds }

    Column(Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScreenIntro(
                    eyebrow = "批量引擎",
                    title = "一种格式。\n全部文件。",
                    subtitle = "选择预设，勾选任意媒体，让 FFmpeg 把整批文件统一处理。",
                )
            }
            item {
                ImportActions(onFiles = onImportFiles, onFolder = onImportFolder)
            }
            if (media.isEmpty()) {
                item {
                    EmptyMediaState(
                        onImportFiles = onImportFiles,
                        onImportFolder = onImportFolder,
                        onDemo = onDemo,
                        title = "没有源文件",
                        body = "添加文件或文件夹，也可以生成演示片段来测试转换。",
                    )
                }
            } else {
                item {
                    SectionHeader("预设模板", "${Presets.all.size} 个预设，覆盖视频、音频、图像与封装")
                }
                item {
                    PillTabs(options = categories, selected = category, onSelect = { newCategory ->
                        category = newCategory
                        presetId = when (newCategory) {
                            PresetCategory.AUDIO.label -> Presets.audio.first().id
                            PresetCategory.IMAGE.label -> Presets.image.first().id
                            PresetCategory.UTILITY.label -> Presets.utility.first().id
                            else -> Presets.video.first().id
                        }
                    })
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(visiblePresets, key = { it.id }) { preset ->
                            PresetCard(preset = preset, selected = preset.id == selectedPreset.id, onClick = { presetId = preset.id })
                        }
                    }
                }
                item {
                    PresetDetail(selectedPreset)
                }
                item {
                    SectionHeader(
                        title = "源文件",
                        subtitle = "已选 ${selectedItems.size} / ${media.size}",
                        trailing = {
                            TextButton(onClick = {
                                if (selectedIds.size == media.size) selectedIds.clear()
                                else {
                                    selectedIds.clear()
                                    selectedIds.addAll(media.map { it.id })
                                }
                            }) {
                                Text(if (selectedIds.size == media.size) "清空" else "全选")
                            }
                        },
                    )
                }
                items(media, key = { it.id }) { item ->
                    MediaRow(
                        item = item,
                        selected = item.id in selectedIds,
                        onClick = {
                            if (selectedIds.contains(item.id)) selectedIds.remove(item.id) else selectedIds.add(item.id)
                        },
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
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (selectedItems.isEmpty()) "选择要转换的文件" else "${selectedItems.size} 个文件 · ${selectedPreset.extension.uppercase()}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            selectedPreset.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = {
                            onConvert(selectedItems, selectedPreset.id)
                            selectedIds.clear()
                        },
                        enabled = selectedItems.isNotEmpty(),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("转换 ${selectedItems.size} 个".trim())
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetCard(preset: Preset, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(212.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        preset.badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(preset.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                preset.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PresetDetail(preset: Preset) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(
                preset.subtitle + " · 输出保存在应用的 outputs 目录，并自动加入媒体库。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
