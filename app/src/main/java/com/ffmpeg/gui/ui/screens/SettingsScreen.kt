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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmpeg.gui.BuildConfig
import com.ffmpeg.gui.StorageUiState
import com.ffmpeg.gui.WorkspaceStats
import com.ffmpeg.gui.data.AiSettings
import com.ffmpeg.gui.data.AppSettings
import com.ffmpeg.gui.ui.components.BrandLogo
import com.ffmpeg.gui.ui.components.ControlCard
import com.ffmpeg.gui.ui.components.InfoRow
import com.ffmpeg.gui.ui.components.PillTabs
import com.ffmpeg.gui.ui.components.ScreenIntro
import com.ffmpeg.gui.ui.formatBytes

private data class ProviderPreset(
    val label: String,
    val baseUrl: String,
    val model: String,
    val note: String,
)

private val providers = listOf(
    ProviderPreset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "兼容 OpenAI"),
    ProviderPreset("OpenAI", "https://api.openai.com/v1", "gpt-4.1-mini", "兼容 OpenAI"),
    ProviderPreset("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4.1-mini", "兼容 OpenAI"),
    ProviderPreset("Ollama", "http://10.0.2.2:11434/v1", "llama3.2", "本地模型，无需密钥"),
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    stats: WorkspaceStats,
    storageState: StorageUiState,
    ffmpegVersion: String,
    pythonVersion: String,
    onThemeChange: (String) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onStorageModeChange: (String, String) -> Unit,
    onApplyCustomStorage: (String) -> Unit,
    onPickStorageFolder: () -> Unit,
    onRequestStoragePermission: () -> Unit,
    onSaveAi: (AiSettings) -> Unit,
    onDemo: () -> Unit,
    onRefresh: () -> Unit,
    onInstallLibrary: () -> Unit,
    onBack: () -> Unit,
) {
    var aiDraft by remember(settings.ai) { mutableStateOf(settings.ai) }
    var keyVisible by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    var storageChoice by rememberSaveable { mutableStateOf(storageState.mode) }
    var storagePath by rememberSaveable(storageState.customPath) {
        mutableStateOf(storageState.customPath.ifBlank { "/storage/emulated/0/FFmpegGUI" })
    }
    val storageModeLabel = when (storageChoice) {
        "EXTERNAL" -> "外部"
        "CUSTOM" -> "自定义"
        else -> "内部"
    }
    LaunchedEffect(storageState.mode) { storageChoice = storageState.mode }
    LaunchedEffect(storageState.customPath) {
        if (storageState.customPath.isNotBlank()) storagePath = storageState.customPath
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenIntro(
            eyebrow = "偏好设置",
            title = "调校你的工作台。",
            subtitle = "主题、智能助手连接与环境信息。",
        )

        ControlCard(title = "外观", subtitle = "Material 3 Expressive，全面屏设计") {
            PillTabs(
                options = listOf("跟随系统", "浅色", "深色"),
                selected = when (settings.themeMode) {
                    "light" -> "浅色"
                    "dark" -> "深色"
                    else -> "跟随系统"
                },
                onSelect = { label ->
                    onThemeChange(
                        when (label) {
                            "浅色" -> "light"
                            "深色" -> "dark"
                            else -> "system"
                        }
                    )
                },
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Icon(
                        Icons.Rounded.SettingsBrightness,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("动态取色", style = MaterialTheme.typography.titleSmall)
                    Text("Android 12+ 使用壁纸取色", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.dynamicColor, onCheckedChange = onDynamicColorChange)
            }
        }

        ControlCard(
            title = "存储位置",
            subtitle = "工作区、导入文件和生成文件保存的位置",
        ) {
            PillTabs(
                options = listOf("内部", "外部", "自定义"),
                selected = storageModeLabel,
                onSelect = { label ->
                    when (label) {
                        "内部" -> {
                            storageChoice = "INTERNAL"
                            onStorageModeChange("INTERNAL", "")
                        }
                        "外部" -> {
                            storageChoice = "EXTERNAL"
                            onStorageModeChange("EXTERNAL", "")
                        }
                        else -> storageChoice = "CUSTOM"
                    }
                },
            )
            if (storageState.migrating) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            Text("当前工作区", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    storageState.currentPath,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (storageModeLabel == "自定义") {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = storagePath,
                    onValueChange = { storagePath = it },
                    label = { Text("自定义目录路径") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPickStorageFolder, shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("选择文件夹")
                    }
                    Button(
                        onClick = { onApplyCustomStorage(storagePath) },
                        enabled = !storageState.migrating,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("应用目录")
                    }
                }
                if (!storageState.permissionGranted) {
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Android 11+ 写入自定义公共目录需要“所有文件访问权限”。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                            TextButton(onClick = onRequestStoragePermission) { Text("授予存储权限") }
                        }
                    }
                }
            }
            if (storageState.message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    storageState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (storageState.message.contains("失败") || storageState.message.contains("需要")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "切换时会复制当前工作区文件到新目录，旧目录保留为备份。建议在任务完成后操作。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ControlCard(
            title = "AI 智能助手",
            subtitle = "兼容 OpenAI 的对话接口，支持工具调用",
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                providers.forEach { provider ->
                    FilterChip(
                        selected = aiDraft.baseUrl == provider.baseUrl,
                        onClick = { aiDraft = aiDraft.copy(baseUrl = provider.baseUrl, model = provider.model) },
                        label = { Text(provider.label) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                providers.firstOrNull { it.baseUrl == aiDraft.baseUrl }?.note ?: "自定义 OpenAI 兼容接口",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = aiDraft.baseUrl,
                onValueChange = { aiDraft = aiDraft.copy(baseUrl = it) },
                label = { Text("接口地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = aiDraft.model,
                onValueChange = { aiDraft = aiDraft.copy(model = it) },
                label = { Text("模型") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = aiDraft.apiKey,
                onValueChange = { aiDraft = aiDraft.copy(apiKey = it) },
                label = { Text("API 密钥") },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Toggle key")
                    }
                },
                supportingText = { Text("仅保存在本机应用内。Ollama 或本地服务可留空。") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text("温度 · ${"%.1f".format(aiDraft.temperature)}", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = aiDraft.temperature.toFloat(),
                onValueChange = { aiDraft = aiDraft.copy(temperature = it.toDouble().coerceIn(0.0, 1.5)) },
                valueRange = 0f..1.5f,
                steps = 14,
            )
            Text("工具迭代上限 · ${aiDraft.maxIterations}", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = aiDraft.maxIterations.toFloat(),
                onValueChange = { aiDraft = aiDraft.copy(maxIterations = it.toInt()) },
                valueRange = 2f..12f,
                steps = 9,
            )
            OutlinedTextField(
                value = aiDraft.systemPrompt,
                onValueChange = { aiDraft = aiDraft.copy(systemPrompt = it) },
                label = { Text("系统提示词") },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "工具已启用：智能助手可以列出、读取、写入工作区文件，探测媒体，运行 FFmpeg，并执行 Python。发送前请确认指令。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onSaveAi(aiDraft) },
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("保存智能助手设置")
            }
        }

        ControlCard(title = "运行环境", subtitle = "所有文件都保存在应用沙盒内") {
            InfoRow("FFmpeg", ffmpegVersion)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            InfoRow("Python", pythonVersion)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            InfoRow("媒体资源", "${stats.mediaCount} 个媒体 · ${stats.outputCount} 个生成文件")
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            InfoRow("工作区大小", formatBytes(stats.totalBytes))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("刷新")
                }
                OutlinedButton(onClick = onDemo, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("演示片段")
                }
                OutlinedButton(onClick = onInstallLibrary, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("库")
                }
            }
        }

        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(18.dp)) {
                Surface(shape = RoundedCornerShape(18.dp), color = androidx.compose.ui.graphics.Color(0xFFF4FFF6)) {
                    BrandLogo(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 18.dp, vertical = 8.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("FFmpeg GUI", style = MaterialTheme.typography.titleLarge)
                Text("版本 ${BuildConfig.VERSION_NAME} · Android 8–17 (API 26–37)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text(
                    "使用 Jetpack Compose 与 Material 3 Expressive 构建。FFmpeg 由 FFmpegKit Full-GPL 6.0-2 提供（x264、x265、libvpx、libopus、LAME）。Python 由 Chaquopy 嵌入。FFmpeg 商标与标志归 FFmpeg 项目所有。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                InfoRow("许可证", "GPL v3（FFmpeg 组件）")
                InfoRow("存储", "应用私有沙盒")
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onBack) { Text("返回工作台") }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
