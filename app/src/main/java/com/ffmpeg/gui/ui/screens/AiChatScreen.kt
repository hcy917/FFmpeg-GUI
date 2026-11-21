package com.ffmpeg.gui.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ffmpeg.gui.MainViewModel
import com.ffmpeg.gui.data.ChatMessage
import com.ffmpeg.gui.ui.components.BrandLogo
import com.ffmpeg.gui.ui.formatBytes

private val suggestions = listOf(
    "把 inbox 里的视频批量转成 MP4（H.264 + AAC）",
    "把多个视频合并，并统一分辨率和音轨",
    "把这个视频按每段 60 秒拆分，输出 MKV",
    "写一个 Python 脚本，统计工作区所有媒体的时长",
)

@Composable
fun AiChatScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val busy by viewModel.agentBusy.collectAsStateWithLifecycle()
    val status by viewModel.agentStatus.collectAsStateWithLifecycle()
    val error by viewModel.agentError.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val ai by viewModel.settings.collectAsStateWithLifecycle()

    var input by rememberSaveable { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val listState = rememberLazyListState()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) attachments = (attachments + uris).distinct()
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.importFolder(uri)
    }

    LaunchedEffect(messages.size, busy, importState.active) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val send = {
        if (!busy && (input.isNotBlank() || attachments.isNotEmpty())) {
            viewModel.sendChat(input, attachments)
            input = ""
            attachments = emptyList()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ChatHeader(
            aiLabel = ai.ai.model.ifBlank { "未配置模型" },
            mediaCount = stats.mediaCount,
            onImportFiles = { filePicker.launch(arrayOf("*/*")) },
            onImportFolder = { folderPicker.launch(null) },
            onOpenSettings = { viewModel.openSettingsRequested() },
        )

        if (importState.message.isNotBlank() && !importState.active) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                onClick = viewModel::clearImportMessage,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(importState.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.Close, contentDescription = "关闭", modifier = Modifier.size(16.dp))
                }
            }
        }
        if (importState.active) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(importState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                WelcomePanel(
                    workspace = viewModel.workspacePath,
                    statsLabel = "${stats.mediaCount} 个媒体 · ${formatBytes(stats.totalBytes)} · ${stats.outputCount} 个输出",
                    onSuggestion = { text -> viewModel.sendChat(text) },
                    onImportFiles = { filePicker.launch(arrayOf("*/*")) },
                    onOpenScripts = { viewModel.requestTab("scripts") },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        when (message.role) {
                            "tool" -> ToolMessageCard(message)
                            "user" -> UserMessageCard(message)
                            else -> AssistantMessageCard(message, context)
                        }
                    }
                    if (busy) {
                        item("status") {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    status.ifBlank { "正在工作…" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (error.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::clearAgentError, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        ChatComposer(
            value = input,
            onValueChange = { input = it },
            attachments = attachments,
            busy = busy,
            onPickFiles = { filePicker.launch(arrayOf("*/*")) },
            onRemoveAttachment = { uri -> attachments = attachments - uri },
            onSend = send,
            onStop = viewModel::stopAgent,
            onClear = viewModel::clearChat,
        )
    }
}

@Composable
private fun ChatHeader(
    aiLabel: String,
    mediaCount: Int,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF4FFF6)) {
            BrandLogo(Modifier.height(30.dp).width(104.dp).padding(6.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("媒体智能体", style = MaterialTheme.typography.titleLarge)
            Text(
                "$aiLabel · 工作区 $mediaCount 个媒体",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onImportFiles) { Icon(Icons.Rounded.UploadFile, contentDescription = "导入文件") }
        IconButton(onClick = onImportFolder) { Icon(Icons.Rounded.FolderOpen, contentDescription = "导入文件夹") }
        IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI 设置") }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun WelcomePanel(
    workspace: String,
    statsLabel: String,
    onSuggestion: (String) -> Unit,
    onImportFiles: () -> Unit,
    onOpenScripts: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(22.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(10.dp).size(26.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("交给智能体。", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "它能直接读写文件、探测媒体、调用 FFmpeg 和 Python，并把结果保存到工作区。导入素材，然后说出你要的结果。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(statsLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onImportFiles, shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("导入素材")
                        }
                        OutlinedButton(onClick = onOpenScripts, shape = RoundedCornerShape(16.dp)) {
                            Text("已保存脚本")
                        }
                    }
                }
            }
        }
        item {
            Text("试试这样说", style = MaterialTheme.typography.titleMedium)
        }
        items(suggestions) { text ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().clickable { onSuggestion(text) },
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
        item {
            Text(
                "工作区：$workspace",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun UserMessageCard(message: ChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Text(
                message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun AssistantMessageCard(message: ChatMessage, context: Context) {
    Surface(
        shape = RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp),
        color = if (message.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(0.94f),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (message.isError) Icons.Rounded.ErrorOutline else Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = if (message.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    if (message.isError) "执行失败" else "FFmpeg GUI Agent",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("回复", message.content))
                        Toast.makeText(context, "已复制回复", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "复制", modifier = Modifier.size(15.dp))
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(message.content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ToolMessageCard(message: ChatMessage) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val failed = message.toolResult.startsWith("错误") || message.toolResult.contains("失败") && message.toolResult.length < 120
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = !expanded },
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(
                        if (failed) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.padding(5.dp).size(14.dp),
                        tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    toolLabel(message.toolName),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (expanded) "收起" else "详情",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    if (message.toolArgs.isNotBlank()) {
                        Text("参数", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            message.toolArgs.take(1200),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("结果", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        message.toolResult.take(5000) + if (message.toolResult.length > 5000) "\n…（已截断显示）" else "",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

private fun toolLabel(name: String): String = when (name) {
    "ffmpeg_run" -> "FFmpeg 执行"
    "ffmpeg_capabilities" -> "FFmpeg 能力"
    "python_run" -> "Python 运行"
    "python_save_script" -> "保存 Python 脚本"
    "python_install_library" -> "安装 Python 库"
    "media_probe" -> "媒体探测"
    "media_library" -> "媒体库"
    "download_file" -> "下载文件"
    "fs_list" -> "浏览文件"
    "fs_read" -> "读取文件"
    "fs_write" -> "写入文件"
    "fs_copy" -> "复制文件"
    "fs_move" -> "移动文件"
    "fs_delete" -> "删除文件"
    "fs_mkdir" -> "新建目录"
    "workspace_info" -> "工作区信息"
    else -> name
}

@Composable
private fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    attachments: List<Uri>,
    busy: Boolean,
    onPickFiles: () -> Unit,
    onRemoveAttachment: (Uri) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().imePadding(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
            if (attachments.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    items(attachments) { uri ->
                        AssistChip(
                            onClick = { onRemoveAttachment(uri) },
                            label = { Text(displayName(context, uri), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "移除", modifier = Modifier.size(14.dp)) },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onPickFiles, enabled = !busy) {
                    Icon(Icons.Rounded.Add, contentDescription = "添加文件")
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("描述你要的结果，或询问媒体信息…") },
                    maxLines = 5,
                    shape = RoundedCornerShape(22.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                )
                Spacer(Modifier.width(8.dp))
                if (busy) {
                    FilledIconButton(
                        onClick = onStop,
                        modifier = Modifier.size(48.dp),
                        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = "停止")
                    }
                } else {
                    FilledIconButton(
                        onClick = onSend,
                        enabled = value.isNotBlank() || attachments.isNotEmpty(),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Rounded.Send, contentDescription = "发送")
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClear, enabled = !busy) {
                    Text("清空对话", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "AI 会自动调用工具 · 输出保存在 outputs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun displayName(context: Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "附件"
}
