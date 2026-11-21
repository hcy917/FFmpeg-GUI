package com.ffmpeg.gui.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ffmpeg.gui.MainViewModel
import com.ffmpeg.gui.data.SavedScript
import com.ffmpeg.gui.ui.components.BrandLogo
import com.ffmpeg.gui.ui.formatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scripts by viewModel.scripts.collectAsStateWithLifecycle()
    val runState by viewModel.scriptRun.collectAsStateWithLifecycle()
    val message by viewModel.libraryMessage.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<SavedScript?>(null) }
    var showRunSheet by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<SavedScript?>(null) }
    var pendingDelete by remember { mutableStateOf<SavedScript?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val newDraft = remember {
        SavedScript(
            id = "unsaved-new",
            name = "新脚本.py",
            relativePath = "",
            description = "手动创建",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importScript(uri)
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-python")) { uri ->
        val script = pendingExport
        if (uri != null && script != null) viewModel.exportScript(script, uri)
        pendingExport = null
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = androidx.compose.ui.graphics.Color(0xFFF4FFF6)) {
                BrandLogo(Modifier.height(30.dp).width(104.dp).padding(6.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("已保存脚本", style = MaterialTheme.typography.titleLarge)
                Text("AI 生成与手动导入的 Python 批处理", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { importer.launch(arrayOf("text/x-python", "text/plain", "*/*")) }) {
                Icon(Icons.Rounded.FileDownload, contentDescription = "导入脚本")
            }
            FilledTonalButton(onClick = { editing = newDraft }) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("新建")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        if (message.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::dismissLibraryMessage, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (scripts.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.padding(18.dp).size(34.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("还没有脚本", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "让 AI 生成批处理脚本，或从设备导入 .py 文件。每个脚本都会保存在专用目录中。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { editing = newDraft }) { Text("新建脚本") }
                    OutlinedButton(onClick = { importer.launch(arrayOf("text/x-python", "text/plain", "*/*")) }) { Text("导入 .py") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item("path") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            viewModel.scriptsPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            copyText(context, viewModel.scriptsPath)
                        }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "复制路径", modifier = Modifier.size(15.dp))
                        }
                    }
                }
                items(scripts, key = { it.id }) { script ->
                    ScriptCard(
                        script = script,
                        running = runState.running && runState.scriptId == script.id,
                        runBusy = runState.running,
                        menuOpen = menuFor == script.id,
                        onRun = {
                            showRunSheet = true
                            viewModel.runScript(script)
                        },
                        onEdit = { editing = script },
                        onMenu = { menuFor = if (menuFor == script.id) null else script.id },
                        onExport = {
                            menuFor = null
                            pendingExport = script
                            exporter.launch(script.name)
                        },
                        onDelete = {
                            menuFor = null
                            pendingDelete = script
                        },
                    )
                }
                item("footer") {
                    Text(
                        "共 ${scripts.size} 个脚本 · 脚本以 UTF-8 保存",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }

    editing?.let { script ->
        val isNew = script.relativePath.isBlank()
        ScriptEditorDialog(
            script = script,
            initialContent = if (isNew) NEW_SCRIPT_TEMPLATE else viewModel.readScript(script),
            onDismiss = { editing = null },
            onSave = { name, description, content ->
                if (isNew) viewModel.createScript(name, content, description)
                else viewModel.saveScript(script, name, content, description)
                editing = null
            },
        )
    }

    pendingDelete?.let { script ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除脚本？") },
            text = { Text("${script.name} 将从脚本目录永久删除，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteScript(script)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    if (showRunSheet && (runState.running || runState.output.isNotBlank())) {
        ModalBottomSheet(
            onDismissRequest = {
                showRunSheet = false
                viewModel.clearScriptRun()
            },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("脚本运行", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (runState.running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        runState.running -> "正在执行，请保持应用在前台。"
                        runState.failed -> "执行失败，请查看日志定位错误。"
                        else -> "执行完成。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (runState.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        runState.output.ifBlank { "暂无输出。" },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp).height(320.dp).verticalScroll(rememberScrollState()),
                    )
                }
                if (runState.result.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text("返回值", style = MaterialTheme.typography.titleSmall)
                    Text(runState.result.take(2000), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        copyText(context, runState.output)
                    }) { Text("复制日志") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        showRunSheet = false
                        viewModel.clearScriptRun()
                    }) { Text("关闭") }
                }
            }
        }
    }
}

@Composable
private fun ScriptCard(
    script: SavedScript,
    running: Boolean,
    runBusy: Boolean,
    menuOpen: Boolean,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onMenu: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (script.generatedByAi) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.padding(9.dp).size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(script.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (script.description.isBlank()) "无说明" else script.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = onMenu) { Icon(Icons.Rounded.MoreVert, contentDescription = "更多") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = onMenu) {
                        DropdownMenuItem(
                            text = { Text("导出脚本") },
                            leadingIcon = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
                            onClick = onExport,
                        )
                        DropdownMenuItem(
                            text = { Text("删除脚本") },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                            onClick = onDelete,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append(if (script.generatedByAi) "AI 生成" else "手动导入")
                        append(" · ")
                        append(SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(script.updatedAt)))
                        if (script.lastRunAt > 0) append(" · 最近运行")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("编辑")
                }
                FilledTonalButton(onClick = onRun, enabled = !runBusy) {
                    if (running) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(if (running) "运行中" else "运行")
                }
            }
        }
    }
}

@Composable
private fun ScriptEditorDialog(
    script: SavedScript,
    initialContent: String,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, content: String) -> Unit,
) {
    var name by remember(script.id) { mutableStateOf(script.name) }
    var description by remember(script.id) { mutableStateOf(script.description) }
    var content by remember(script.id) { mutableStateOf(initialContent) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize().imePadding(),
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (script.relativePath.isBlank()) "新建脚本" else "编辑脚本",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        onClick = { onSave(name, description, content) },
                        enabled = name.isNotBlank(),
                    ) { Text("保存") }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("文件名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("说明（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Python 源码") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("内容", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

private val NEW_SCRIPT_TEMPLATE = "#!/usr/bin/env python3\n# -*- coding: utf-8 -*-\n\"\"\"在此编写你的批处理脚本。\"\"\"\n\nprint(\"Hello from FFmpeg GUI\")\n"
