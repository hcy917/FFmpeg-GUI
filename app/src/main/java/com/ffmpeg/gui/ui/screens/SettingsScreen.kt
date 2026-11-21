package com.ffmpeg.gui.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ffmpeg.gui.BuildConfig
import com.ffmpeg.gui.MainViewModel
import com.ffmpeg.gui.data.AiSettings
import com.ffmpeg.gui.ui.components.BrandLogo
import com.ffmpeg.gui.ui.formatBytes
import kotlinx.coroutines.launch

private enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    Ai("AI 模型与接口", "接口地址、模型、密钥、生成行为与系统提示词", Icons.Rounded.AutoAwesome),
    Appearance("外观", "主题模式与动态取色", Icons.Rounded.Palette),
    Storage("工作区与权限", "固定外部目录、文件访问权限与空间", Icons.Rounded.Storage),
    Python("Python 运行环境", "版本、库与脚本目录", Icons.Rounded.Terminal),
    About("关于", "版本、组件与开源许可", Icons.Rounded.Info),
}

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val category = SettingsCategory.entries.firstOrNull { it.name == categoryName }
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val granted by viewModel.allFilesGranted.collectAsStateWithLifecycle()
    val libraries by viewModel.libraries.collectAsStateWithLifecycle()
    val pythonVersion by viewModel.pythonVersion.collectAsStateWithLifecycle()
    val libraryMessage by viewModel.libraryMessage.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    var savedHint by remember { mutableStateOf("") }
    var aiDraft by remember { mutableStateOf(settings.ai) }
    LaunchedEffect(settings.ai) { aiDraft = settings.ai }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshStorageAccess()
    }
    val allFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshStorageAccess()
    }
    val libraryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.installLibrary(uri)
    }
    val requestAllFiles: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            runCatching { allFilesLauncher.launch(intent) }.onFailure {
                runCatching { allFilesLauncher.launch(Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                    .onFailure { Toast.makeText(context, "无法打开权限页面", Toast.LENGTH_SHORT).show() }
            }
        } else {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    BackHandler(enabled = category != null) { categoryName = null }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (category == null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF4FFF6)) {
                    BrandLogo(Modifier.height(30.dp).width(104.dp).padding(6.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("设置", style = MaterialTheme.typography.titleLarge)
                    Text("按类别管理智能体与应用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SettingsHome(
                settings = settings,
                stats = stats,
                workspace = viewModel.workspacePath,
                onOpen = { categoryName = it.name },
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { categoryName = null }) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                }
                Column(Modifier.weight(1f)) {
                    Text(category.title, style = MaterialTheme.typography.titleLarge)
                    Text(category.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            when (category) {
                SettingsCategory.Ai -> AiSettingsDetail(
                    draft = aiDraft,
                    onDraftChange = { aiDraft = it },
                    testing = testing,
                    testResult = testResult,
                    savedHint = savedHint,
                    onTest = {
                        testing = true
                        testResult = ""
                        scope.launch {
                            testResult = viewModel.testAiConnection(aiDraft)
                            testing = false
                        }
                    },
                    onSave = {
                        viewModel.updateAi(aiDraft)
                        savedHint = "已保存 AI 设置"
                    },
                    onResetPrompt = viewModel::resetSystemPrompt,
                )
                SettingsCategory.Appearance -> AppearanceDetail(
                    settings = settings,
                    onTheme = { mode -> viewModel.updateTheme(mode, settings.dynamicColor) },
                    onDynamic = { enabled -> viewModel.updateTheme(settings.themeMode, enabled) },
                )
                SettingsCategory.Storage -> StorageDetail(
                    workspace = viewModel.workspacePath,
                    inbox = viewModel.inboxPath,
                    outputs = viewModel.outputsPath,
                    scripts = viewModel.scriptsPath,
                    stats = stats,
                    granted = granted,
                    onRequest = requestAllFiles,
                    onRefresh = { viewModel.refreshStorageAccess(); viewModel.refreshStats() },
                )
                SettingsCategory.Python -> PythonDetail(
                    version = pythonVersion,
                    libraries = libraries.map { Triple(it.name, it.version, it.kind) },
                    message = libraryMessage,
                    scriptsCount = stats.scriptCount,
                    onInstall = { libraryPicker.launch(arrayOf("*/*")) },
                    onDismissMessage = viewModel::dismissLibraryMessage,
                )
                SettingsCategory.About -> AboutDetail(context)
            }
        }
    }
}

@Composable
private fun SettingsHome(
    settings: com.ffmpeg.gui.data.AppSettings,
    stats: com.ffmpeg.gui.WorkspaceStats,
    workspace: String,
    onOpen: (SettingsCategory) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("overview") {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.padding(9.dp).size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("当前配置", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${settings.ai.model.ifBlank { "未配置模型" }} · ${if (settings.ai.apiKey.isBlank()) "未填写密钥" else "密钥已填写"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        workspace,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${stats.mediaCount} 个输入 · ${stats.outputCount} 个输出 · ${formatBytes(stats.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        items(SettingsCategory.entries, key = { it.name }) { category ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(category) },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Icon(category.icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(category.title, style = MaterialTheme.typography.titleMedium)
                        Text(category.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item("footer") {
            Text(
                "FFmpeg GUI · Agent 优先的本地媒体工作台",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun AiSettingsDetail(
    draft: AiSettings,
    onDraftChange: (AiSettings) -> Unit,
    testing: Boolean,
    testResult: String,
    savedHint: String,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onResetPrompt: () -> Unit,
) {
    var showKey by remember { mutableStateOf(false) }
    val presets = listOf(
        Triple("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
        Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
        Triple("硅基流动", "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V3"),
        Triple("Ollama", "http://127.0.0.1:11434/v1", "qwen2.5:7b"),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("presets") {
            Text("快速填充", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                items(presets) { preset ->
                    AssistChip(
                        onClick = {
                            onDraftChange(draft.copy(baseUrl = preset.second, model = preset.third))
                        },
                        label = { Text(preset.first) },
                    )
                }
            }
        }
        item("endpoint") {
            OutlinedTextField(
                value = draft.baseUrl,
                onValueChange = { onDraftChange(draft.copy(baseUrl = it)) },
                label = { Text("接口地址（OpenAI 兼容）") },
                supportingText = { Text("例如 https://api.deepseek.com/v1；也支持完整 /chat/completions 地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item("model") {
            OutlinedTextField(
                value = draft.model,
                onValueChange = { onDraftChange(draft.copy(model = it)) },
                label = { Text("模型名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item("key") {
            OutlinedTextField(
                value = draft.apiKey,
                onValueChange = { onDraftChange(draft.copy(apiKey = it)) },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "显示密钥")
                    }
                },
                supportingText = { Text("仅保存在本机应用私有设置中，不会上传到其他服务。") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item("temperature") {
            SettingSlider(
                title = "温度",
                valueLabel = String.format("%.1f", draft.temperature),
                value = draft.temperature.toFloat(),
                range = 0f..2f,
                steps = 19,
                onChange = { onDraftChange(draft.copy(temperature = (Math.round(it * 10f) / 10f).toDouble())) },
            )
        }
        item("iterations") {
            SettingSlider(
                title = "最大工具轮数",
                valueLabel = "${draft.maxIterations} 轮",
                value = draft.maxIterations.toFloat(),
                range = 1f..24f,
                steps = 22,
                onChange = { onDraftChange(draft.copy(maxIterations = it.toInt())) },
            )
        }
        item("prompt") {
            OutlinedTextField(
                value = draft.systemPrompt,
                onValueChange = { onDraftChange(draft.copy(systemPrompt = it)) },
                label = { Text("系统提示词") },
                minLines = 6,
                maxLines = 12,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                TextButton(onClick = onResetPrompt) { Text("恢复默认提示词") }
                Spacer(Modifier.weight(1f))
                Text(
                    "${draft.systemPrompt.length} 字符",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item("actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onTest, enabled = !testing) {
                    if (testing) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Language, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (testing) "测试中…" else "测试连接")
                }
                Button(onClick = onSave) { Text("保存设置") }
            }
            if (savedHint.isNotBlank()) {
                Text(savedHint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (testResult.isNotBlank()) {
            item("test") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (testResult.startsWith("连接成功")) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(testResult, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }
        }
        item("spacer") { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun SettingSlider(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
        }
    }
}

@Composable
private fun AppearanceDetail(
    settings: com.ffmpeg.gui.data.AppSettings,
    onTheme: (String) -> Unit,
    onDynamic: (Boolean) -> Unit,
) {
    val modes = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("主题模式", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { (key, label) ->
                FilterChip(
                    selected = settings.themeMode == key,
                    onClick = { onTheme(key) },
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("动态取色", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "使用系统壁纸颜色生成 Material 3 配色（Android 12+）" else "当前系统版本不支持动态取色",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    onCheckedChange = onDynamic,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("界面风格", style = MaterialTheme.typography.titleSmall)
                Text(
                    "采用 Material 3 Expressive 风格：大圆角、分层表面、品牌绿色与清晰的信息层级。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("28dp 圆角", "动态色", "响应式").forEach { label ->
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageDetail(
    workspace: String,
    inbox: String,
    outputs: String,
    scripts: String,
    stats: com.ffmpeg.gui.WorkspaceStats,
    granted: Boolean,
    onRequest: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("info") {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("固定外部工作区", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "应用始终使用 Android/data 下的外部专用目录，卸载应用会删除该目录中的文件。存储位置不再提供切换。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item("paths") {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PathRow("工作区", workspace, context)
                    PathRow("导入目录", inbox, context)
                    PathRow("输出目录", outputs, context)
                    PathRow("AI 脚本", scripts, context)
                }
            }
        }
        item("stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("输入文件", "${stats.mediaCount}", Modifier.weight(1f))
                StatTile("输出文件", "${stats.outputCount}", Modifier.weight(1f))
                StatTile("占用空间", formatBytes(stats.totalBytes), Modifier.weight(1f))
            }
        }
        item("permission") {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("所有文件访问", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (granted) "已授权，AI 可读写共享存储中的其他文件。" else "未授权，AI 只能访问应用工作区。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val scopedTen = Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!granted && !scopedTen) Button(onClick = onRequest) { Text("去授权") }
                        OutlinedButton(onClick = onRefresh) { Text("重新检查") }
                    }
                    Text(
                        if (scopedTen) {
                            "说明：Android 10 的分区存储不允许应用直接遍历共享存储；请用聊天页的“导入文件/文件夹”通过系统选择器添加文件，AI 仍可处理工作区内的全部文件。"
                        } else {
                            "说明：Android 11+ 需要“所有文件访问”才能处理工作区以外的文件；App 专用外部目录无需此权限。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        item("storageState") {
            Text(
                if (granted) "系统状态：所有文件访问已开启" else "系统状态：仅应用专用目录",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PathRow(label: String, path: String, context: Context) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(path, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, path))
            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PythonDetail(
    version: String,
    libraries: List<Triple<String, String, String>>,
    message: String,
    scriptsCount: Int,
    onInstall: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("version") {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                        Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.padding(9.dp).size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("内置 Python", style = MaterialTheme.typography.titleMedium)
                        Text(version, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item("install") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Python 库", style = MaterialTheme.typography.titleSmall)
                    Text("支持 .whl / .zip / .py，安装到工作区库目录并自动加入 import 路径。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onInstall) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导入库")
                }
            }
        }
        if (message.isNotBlank()) {
            item("message") {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismissMessage) { Text("关闭") }
                    }
                }
            }
        }
        item("scripts") {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("AI 脚本目录", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("$scriptsCount 个脚本", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (libraries.isEmpty()) {
            item("empty") {
                Text("尚未安装第三方库。纯 Python wheel 可直接使用；含原生扩展的包需要与 Chaquopy/ABI 兼容。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(libraries) { library ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(library.first, style = MaterialTheme.typography.bodyMedium)
                            Text("${library.second.ifBlank { "未知版本" }} · ${library.third}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        item("tip") {
            Text(
                "提示：让 AI 写脚本时，脚本会自动保存到“已保存脚本”并在同一 Python 环境中运行。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutDetail(context: Context) {
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: BuildConfig.VERSION_NAME
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("hero") {
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                        BrandLogo(Modifier.fillMaxWidth().height(56.dp).padding(12.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("FFmpeg GUI", style = MaterialTheme.typography.headlineSmall)
                    Text("Agent 优先的本地媒体工作台 · $version", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item("components") {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("内置组件", style = MaterialTheme.typography.titleSmall)
                    FactRow("FFmpeg", "6.0 Full-GPL（x264/x265/libvpx/libopus/LAME）")
                    FactRow("Python", "3.11 · Chaquopy 17.2")
                    FactRow("界面", "Jetpack Compose · Material 3 Expressive")
                    FactRow("兼容", "Android 8 – 17（minSdk 26）")
                }
            }
        }
        item("repo") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().clickable {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hcy917/FFmpeg-GUI")))
                    }
                },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Language, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("GitHub 仓库", style = MaterialTheme.typography.bodyMedium)
                        Text("hcy917/FFmpeg-GUI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
        item("license") {
            Text(
                "FFmpeg 以 GPL v3 授权，本应用随之以 GPL v3 分发。Python 脚本与用户媒体文件归用户所有。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}
