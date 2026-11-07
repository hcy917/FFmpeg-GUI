package com.ffmpeg.gui.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmpeg.gui.PythonUiState
import com.ffmpeg.gui.ScriptEntry
import com.ffmpeg.gui.data.AiSettings
import com.ffmpeg.gui.data.ChatMessage
import com.ffmpeg.gui.data.TaskRecord
import com.ffmpeg.gui.python.PythonLibrary
import com.ffmpeg.gui.ui.components.PillTabs
import com.ffmpeg.gui.ui.components.SectionHeader

private val labTabs = listOf("智能助手", "Python", "库管理", "控制台")

@Composable
fun LabScreen(
    messages: List<ChatMessage>,
    busy: Boolean,
    aiSettings: AiSettings,
    tasks: List<TaskRecord>,
    pythonState: PythonUiState,
    pythonLibraries: List<PythonLibrary>,
    scripts: List<ScriptEntry>,
    onSend: (String) -> Unit,
    onClearChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onRunPython: (String, String) -> Unit,
    onInstallLibrary: () -> Unit,
    onDeleteLibrary: (String) -> Unit,
    onSaveScript: (String, String) -> Unit,
    onDeleteScript: (String) -> Unit,
    onReadScript: (String) -> String,
    onRunConsole: (String) -> Unit,
    onOpenTask: (TaskRecord) -> Unit,
    onRefreshPython: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(labTabs.first()) }
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(top = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                "工作台",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text("智能助手与代码", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                if (tab == "智能助手" && messages.isNotEmpty()) {
                    TextButton(onClick = onClearChat) { Text("清空") }
                }
            }
            Spacer(Modifier.height(12.dp))
            PillTabs(options = labTabs, selected = tab, onSelect = { tab = it })
            Spacer(Modifier.height(10.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                "智能助手" -> CopilotTab(messages, busy, aiSettings, onSend, onOpenSettings)
                "Python" -> PythonTab(pythonState, scripts, onRunPython, onSaveScript, onDeleteScript, onReadScript, onInstallLibrary, onRefreshPython)
                "库管理" -> LibrariesTab(pythonLibraries, onInstallLibrary, onDeleteLibrary, onRefreshPython)
                else -> ConsoleTab(tasks, onRunConsole, onOpenTask)
            }
        }
    }
}

@Composable
private fun CopilotTab(
    messages: List<ChatMessage>,
    busy: Boolean,
    aiSettings: AiSettings,
    onSend: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val suggestions = listOf(
        "把媒体库里所有视频转成 720p MP4",
        "按顺序把我的文件合并成一个 MP4",
        "写一个 Python 脚本并运行，生成工作区报告",
    )
    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SmartToy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(aiSettings.model, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
            Spacer(Modifier.width(6.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    if (aiSettings.autoRunTools) "工具自动执行" else "工具需确认",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "AI 设置", modifier = Modifier.size(20.dp))
            }
        }
        if (aiSettings.apiKey.isBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "在设置中填入 API 密钥即可启用智能助手。本地 Ollama 无需密钥。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenSettings) { Text("去设置") }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    CopilotWelcome(suggestions, onSend)
                }
            }
            items(messages, key = { it.id }) { message ->
                when (message.role) {
                    "user" -> UserBubble(message.content)
                    "tool" -> ToolCard(message)
                    else -> AssistantBubble(message)
                }
            }
            if (busy) {
                item { ThinkingBubble() }
            }
            item { Spacer(Modifier.height(6.dp)) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.weight(1f)) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("让智能助手剪辑、转换或写脚本…") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank() && !busy) {
                            onSend(input); input = ""
                        }
                    }),
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = {
                    if (input.isNotBlank() && !busy) {
                        onSend(input); input = ""
                    }
                },
                enabled = input.isNotBlank() && !busy,
                shape = CircleShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            ) {
                Icon(Icons.Rounded.Send, contentDescription = "发送")
            }
        }
    }
}

@Composable
private fun CopilotWelcome(suggestions: List<String>, onSend: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(9.dp).size(22.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("你的本地媒体智能助手", style = MaterialTheme.typography.titleLarge)
                    Text("可查看全部文件 · 运行 FFmpeg · 执行 Python", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            suggestions.forEach { prompt ->
                Surface(
                    onClick = { onSend(prompt) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                ) {
                    Text(
                        prompt,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(0.86f),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
private fun AssistantBubble(message: ChatMessage) {
    Surface(
        shape = RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp),
        color = if (message.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(0.92f),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (message.isError) Icons.Rounded.ErrorOutline else Icons.Rounded.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (message.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (message.isError) "智能助手出错" else "智能助手",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (message.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ToolCard(message: ChatMessage) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(Color.Transparent),
            ) {
                Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(6.dp))
                Text(
                    message.toolName,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
                val ok = !message.toolResult.startsWith("ERROR")
                Icon(
                    if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Text(
                if (expanded) message.toolArgs else message.toolArgs.take(110),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                maxLines = if (expanded) 12 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedVisibility(expanded) {
                Column {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
                    Text(
                        message.toolResult.take(3000),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("正在调用工具…", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PythonTab(
    state: PythonUiState,
    scripts: List<ScriptEntry>,
    onRun: (String, String) -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onRead: (String) -> String,
    onInstallLibrary: () -> Unit,
    onRefresh: () -> Unit,
) {
    var editorOpen by remember { mutableStateOf(false) }
    var runTarget by remember { mutableStateOf<ScriptEntry?>(null) }
    var editTarget by remember { mutableStateOf<ScriptEntry?>(null) }
    var draftName by remember { mutableStateOf("my_script.py") }
    var draftContent by remember { mutableStateOf(DEFAULT_PYTHON) }
    val scroll = rememberScrollState()

    LaunchedEffect(state.logs.length) {
        if (state.logs.isNotEmpty()) scroll.animateScrollTo(scroll.maxValue)
    }
    Column(Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Code, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("内置 Python 3.11", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text("脚本在本地运行，可直接调用 FFmpeg。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                            }
                            IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, contentDescription = "刷新") }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        draftName = "my_script.py"
                        draftContent = DEFAULT_PYTHON
                        editorOpen = true
                    }, shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("新建脚本")
                    }
                    OutlinedButton(onClick = onInstallLibrary, shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Rounded.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("添加库")
                    }
                }
            }
            item { SectionHeader("脚本", "${scripts.size} 个可用 · 点击运行或编辑") }
            if (scripts.isEmpty()) {
                item { Text("还没有脚本。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(scripts, key = { it.relativePath }) { script ->
                ScriptCard(
                    script = script,
                    onRun = { runTarget = script },
                    onEdit = {
                        editTarget = script
                        draftName = script.name
                        draftContent = onRead(script.relativePath)
                        editorOpen = true
                    },
                    onDelete = { onDelete(script.relativePath) },
                )
            }
            if (state.logs.isNotBlank()) {
                item {
                    SectionHeader(
                        "输出",
                        if (state.running) "正在运行 ${state.script}" else if (state.failed) "上次运行失败" else "上次运行",
                    )
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF0D1712),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            if (state.running) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(6.dp))
                            }
                            Box(Modifier.heightIn(max = 240.dp).verticalScroll(scroll)) {
                                Text(
                                    state.logs,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 15.sp),
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFB9F6CA),
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (editorOpen) {
        AlertDialog(
            onDismissRequest = { editorOpen = false },
            title = { Text(if (editTarget == null) "新建 Python 脚本" else "编辑 ${editTarget?.name}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        label = { Text("文件名") },
                        singleLine = true,
                        enabled = editTarget == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draftContent,
                        onValueChange = { draftContent = it },
                        label = { Text("Python 源码") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth().height(340.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSave(draftName, draftContent)
                    editorOpen = false
                    editTarget = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editorOpen = false; editTarget = null }) { Text("取消") } },
        )
    }
    runTarget?.let { script ->
        var args by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { runTarget = null },
            title = { Text("运行 ${script.name}") },
            text = {
                OutlinedTextField(
                    value = args,
                    onValueChange = { args = it },
                    label = { Text("参数（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRun(script.relativePath, args)
                    runTarget = null
                }) { Text("运行") }
            },
            dismissButton = { TextButton(onClick = { runTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ScriptCard(script: ScriptEntry, onRun: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.padding(9.dp).size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(script.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(script.relativePath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Code, contentDescription = "Edit", modifier = Modifier.size(19.dp)) }
            IconButton(onClick = onRun) { Icon(Icons.Rounded.PlayArrow, contentDescription = "运行", modifier = Modifier.size(22.dp)) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Delete", modifier = Modifier.size(19.dp)) }
        }
    }
}

@Composable
private fun LibrariesTab(
    libraries: List<PythonLibrary>,
    onInstall: () -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(16.dp)) {
                    Text("添加 Python 库", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "导入纯 Python 的 .whl 或 .zip 包。暂不支持包含原生扩展的 wheel，标准库模块始终可用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onInstall, shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("导入库")
                        }
                        OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(14.dp)) { Text("刷新") }
                    }
                }
            }
        }
        item { SectionHeader("已安装", "${libraries.size} 个包已加入 sys.path") }
        if (libraries.isEmpty()) {
            item {
                Text(
                    "还没有第三方包。导入 wheel 或 zip 后，所有脚本都可以 import。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(libraries, key = { it.name }) { library ->
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LibraryBooks, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(library.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${library.version.ifBlank { "本地" }} · ${library.kind}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDelete(library.name) }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
                }
            }
        }
    }
}

@Composable
private fun ConsoleTab(tasks: List<TaskRecord>, onRun: (String) -> Unit, onOpenTask: (TaskRecord) -> Unit) {
    var command by rememberSaveable { mutableStateOf("-version") }
    val last = tasks.firstOrNull { it.title == "FFmpeg 控制台" }
    val examples = listOf(
        "-version",
        "-f lavfi -i testsrc2=size=1280x720:rate=30:duration=3 -c:v libx264 -pix_fmt yuv420p outputs/console_test.mp4",
        "-i inbox/example.mp4 -c copy -movflags +faststart outputs/copy.mp4",
    )
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("FFmpeg 控制台", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "完整命令权限。相对路径会尽量在应用沙盒内解析，最稳妥的方式是使用工作区绝对路径。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text("ffmpeg 命令…") },
                minLines = 3,
                maxLines = 8,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = { onRun(command) },
                enabled = command.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("执行")
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                examples.forEach { example ->
                    Surface(
                        onClick = { command = example },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            example.take(38),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        if (last != null) {
            item {
                SectionHeader(
                    "最近一次控制台运行",
                    last.status.name.lowercase(),
                    trailing = { TextButton(onClick = { onOpenTask(last) }) { Text("完整详情") } },
                )
            }
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF0D1712), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        last.logs.takeLast(5000).ifBlank { "等待输出…" },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFB9F6CA),
                        modifier = Modifier.padding(12.dp).heightIn(max = 320.dp),
                    )
                }
            }
        }
        item {
            Text(
                "高级功能：命令在应用的 FFmpeg 沙盒中运行。导入文件位于 inbox/，生成的媒体应放在 outputs/。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}

private val DEFAULT_PYTHON = """
import ffmpeg_gui

def main(argv):
    ffmpeg_gui.log("你好，来自 Python " + str(argv))
    for item in ffmpeg_gui.media_entries():
        print(f"{item['name']} · {item['kind']} · {item['duration_ms']} 毫秒")
    return "完成"

if __name__ == "__main__":
    main([])
""".trimIndent()
