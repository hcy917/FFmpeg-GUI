package com.ffmpeg.gui.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ffmpeg.gui.MainViewModel
import com.ffmpeg.gui.data.TaskRecord
import com.ffmpeg.gui.data.TaskStatus
import com.ffmpeg.gui.ui.components.BrandLogo
import com.ffmpeg.gui.ui.screens.ConvertScreen
import com.ffmpeg.gui.ui.screens.LabScreen
import com.ffmpeg.gui.ui.screens.MergeScreen
import com.ffmpeg.gui.ui.screens.SettingsScreen
import com.ffmpeg.gui.ui.screens.SplitScreen
import com.ffmpeg.gui.ui.screens.StudioScreen
import kotlinx.coroutines.launch
import java.io.File

enum class AppScreen(val label: String, val icon: ImageVector) {
    Studio("工作台", Icons.Rounded.Home),
    Convert("转换", Icons.Rounded.SwapHoriz),
    Merge("合并", Icons.Rounded.CallMerge),
    Split("拆分", Icons.Rounded.ContentCut),
    Lab("实验室", Icons.Rounded.Science),
    Settings("设置", Icons.Rounded.Settings),
}

private val bottomScreens = listOf(AppScreen.Studio, AppScreen.Convert, AppScreen.Merge, AppScreen.Split, AppScreen.Lab)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FFmpegGuiRoot(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val media by viewModel.media.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val pythonState by viewModel.pythonState.collectAsStateWithLifecycle()
    val pythonLibraryState by viewModel.pythonLibraryState.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val storageState by viewModel.storageState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val aiBusy by viewModel.aiBusy.collectAsStateWithLifecycle()

    var screenName by rememberSaveable { mutableStateOf(AppScreen.Studio.name) }
    val screen = AppScreen.entries.firstOrNull { it.name == screenName } ?: AppScreen.Studio
    var selectedTask by remember { mutableStateOf<TaskRecord?>(null) }
    val snackbar = remember { SnackbarHostState() }

    val importFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importUris(uris)
    }
    val importFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.importTree(uri)
    }
    val libraryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.installPythonLibrary(uri)
    }
    val storageFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.useStorageTree(uri)
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onStoragePermissionResult(granted)
    }
    val allFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onStoragePermissionResult(Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager())
    }

    val onImportFiles: () -> Unit = { importFilesLauncher.launch(arrayOf("*/*")) }
    val onImportFolder: () -> Unit = { importFolderLauncher.launch(null) }
    val onInstallLibrary: () -> Unit = { libraryLauncher.launch(arrayOf("*/*")) }
    val onOpenSettings: () -> Unit = { screenName = AppScreen.Settings.name }
    val onPickStorageFolder: () -> Unit = { storageFolderLauncher.launch(null) }
    val onRequestStoragePermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            runCatching { allFilesLauncher.launch(intent) }.onFailure {
                runCatching { allFilesLauncher.launch(Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        } else {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    val runningTask = tasks.firstOrNull { it.status == TaskStatus.RUNNING }
    val queuedCount = tasks.count { it.status == TaskStatus.QUEUED }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message -> snackbar.showSnackbar(message) }
    }
    LaunchedEffect(tasks) {
        selectedTask?.let { current ->
            tasks.firstOrNull { it.id == current.id }?.let { selectedTask = it }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= 720.dp
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        if (screen == AppScreen.Settings) {
                            IconButton(onClick = { screenName = AppScreen.Studio.name }) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                            }
                        } else {
                            Box(Modifier.padding(start = 16.dp, end = 4.dp)) {
                                BrandLogo(Modifier.height(22.dp).width(82.dp))
                            }
                        }
                    },
                    title = {
                        if (screen != AppScreen.Studio) {
                            Text(screen.label, style = MaterialTheme.typography.titleLarge)
                        } else {
                            Text("FFmpeg GUI", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        if (runningTask != null) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(end = 4.dp),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "${(runningTask.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        } else if (queuedCount > 0) {
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text(
                                    "${queuedCount} 个排队",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                        IconButton(onClick = { screenName = AppScreen.Settings.name }) {
                            Icon(Icons.Rounded.Settings, contentDescription = "设置")
                        }
                    },
                )
            },
            bottomBar = {
                if (!wide) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 2.dp,
                    ) {
                        NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                            bottomScreens.forEach { destination ->
                                NavigationBarItem(
                                    selected = screen == destination,
                                    onClick = { screenName = destination.name },
                                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                                    label = { Text(destination.label) },
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            if (importState.active) {
                ImportingBanner(importState.label, importState.copied, modifier = Modifier.padding(top = padding.calculateTopPadding()))
            }
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (wide) {
                    Surface(
                        shape = RoundedCornerShape(0.dp, 28.dp, 28.dp, 0.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxHeight().width(94.dp),
                    ) {
                        NavigationRail(
                            containerColor = Color.Transparent,
                            header = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 16.dp)) {
                                    BrandLogo(Modifier.height(22.dp).width(72.dp))
                                }
                            },
                        ) {
                            Spacer(Modifier.height(8.dp))
                            bottomScreens.forEach { destination ->
                                NavigationRailItem(
                                    selected = screen == destination,
                                    onClick = { screenName = destination.name },
                                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                                    label = { Text(destination.label) },
                                )
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxSize()) {
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            (fadeIn() + slideInHorizontally { it / 16 }) togetherWith (fadeOut() + slideOutHorizontally { -it / 16 })
                        },
                        label = "screen",
                    ) { destination ->
                        when (destination) {
                            AppScreen.Studio -> StudioScreen(
                                media = media,
                                tasks = tasks,
                                stats = stats,
                                onImportFiles = onImportFiles,
                                onImportFolder = onImportFolder,
                                onDemo = viewModel::createDemoMedia,
                                onNavigate = { screenName = it.name },
                                onOpenTask = { selectedTask = it },
                                onOpenSettings = onOpenSettings,
                                onNewChat = { screenName = AppScreen.Lab.name },
                            )
                            AppScreen.Convert -> ConvertScreen(
                                media = media,
                                onImportFiles = onImportFiles,
                                onImportFolder = onImportFolder,
                                onDemo = viewModel::createDemoMedia,
                                onConvert = viewModel::convert,
                            )
                            AppScreen.Merge -> MergeScreen(
                                media = media,
                                onImportFiles = onImportFiles,
                                onImportFolder = onImportFolder,
                                onMerge = viewModel::merge,
                            )
                            AppScreen.Split -> SplitScreen(
                                media = media,
                                onImportFiles = onImportFiles,
                                onImportFolder = onImportFolder,
                                onSplit = viewModel::split,
                            )
                            AppScreen.Lab -> LabScreen(
                                messages = messages,
                                busy = aiBusy,
                                aiSettings = settings.ai,
                                tasks = tasks,
                                pythonState = pythonState,
                                pythonLibraries = pythonLibraryState.libraries,
                                scripts = pythonLibraryState.scripts,
                                onSend = viewModel::sendChat,
                                onClearChat = viewModel::clearChat,
                                onOpenSettings = onOpenSettings,
                                onRunPython = viewModel::runPython,
                                onInstallLibrary = onInstallLibrary,
                                onDeleteLibrary = viewModel::deletePythonLibrary,
                                onSaveScript = viewModel::savePythonScript,
                                onDeleteScript = viewModel::deletePythonScript,
                                onReadScript = viewModel::readPythonScript,
                                onRunConsole = viewModel::runConsole,
                                onOpenTask = { selectedTask = it },
                                onRefreshPython = viewModel::refreshPython,
                            )
                            AppScreen.Settings -> SettingsScreen(
                                settings = settings,
                                stats = stats,
                                storageState = storageState,
                                ffmpegVersion = viewModel.ffmpegVersion,
                                pythonVersion = viewModel.pythonVersion,
                                onThemeChange = viewModel::setTheme,
                                onDynamicColorChange = viewModel::setDynamicColor,
                                onStorageModeChange = viewModel::applyStorageMode,
                                onApplyCustomStorage = { path -> viewModel.applyStorageMode("CUSTOM", path) },
                                onPickStorageFolder = onPickStorageFolder,
                                onRequestStoragePermission = onRequestStoragePermission,
                                onSaveAi = viewModel::saveAiSettings,
                                onDemo = viewModel::createDemoMedia,
                                onRefresh = viewModel::refreshAll,
                                onInstallLibrary = onInstallLibrary,
                                onBack = { screenName = AppScreen.Studio.name },
                            )
                        }
                    }
                }
            }
        }
    }

    selectedTask?.let { task ->
        TaskDetailSheet(
            task = task,
            onDismiss = { selectedTask = null },
            onCancel = { viewModel.cancelTask(task.id) },
            onDismissTask = {
                viewModel.dismissTask(task.id)
                selectedTask = null
            },
        )
    }
}

@Composable
private fun ImportingBanner(label: String, copied: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                "$label${if (copied > 0) " · 已复制 $copied 个" else ""}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
    task: TaskRecord,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onDismissTask: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Text(task.title, style = MaterialTheme.typography.headlineMedium)
            Text(task.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TaskStatusPill(task.status)
                Spacer(Modifier.width(10.dp))
                Text(
                    when (task.status) {
                        TaskStatus.RUNNING -> "${(task.progress * 100).toInt()}% 完成"
                        TaskStatus.COMPLETED -> "用时 ${"%.1f".format((task.finishedAt - task.createdAt) / 1000.0)} 秒"
                        TaskStatus.FAILED -> "失败"
                        TaskStatus.CANCELLED -> "已取消"
                        TaskStatus.QUEUED -> "排队等待"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (task.status == TaskStatus.RUNNING) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth())
            }
            if (task.outputPaths.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("输出文件", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                task.outputPaths.forEach { path ->
                    val file = File(path)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatBytes(file.length()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { shareFile(context, file) }) { Text("分享") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("命令", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("FFmpeg 命令", task.command))
                    Toast.makeText(context, "命令已复制", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "复制命令")
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    task.command,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (task.error.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(task.error, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("日志", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    task.logs.ifBlank { "暂无日志输出。" },
                    modifier = Modifier.padding(12.dp).height(280.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.QUEUED) {
                    TextButton(onClick = onCancel) { Text("取消任务") }
                }
                if (task.status != TaskStatus.RUNNING) {
                    TextButton(onClick = onDismissTask) { Text("关闭") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    }
}

@Composable
fun TaskStatusPill(status: TaskStatus) {
    val label = when (status) {
        TaskStatus.QUEUED -> "排队中"
        TaskStatus.RUNNING -> "运行中"
        TaskStatus.COMPLETED -> "完成"
        TaskStatus.FAILED -> "失败"
        TaskStatus.CANCELLED -> "已取消"
    }
    val color = when (status) {
        TaskStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
        TaskStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        TaskStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceContainerHighest
        TaskStatus.RUNNING -> MaterialTheme.colorScheme.tertiaryContainer
        TaskStatus.QUEUED -> MaterialTheme.colorScheme.secondaryContainer
    }
    val onColor = when (status) {
        TaskStatus.COMPLETED -> MaterialTheme.colorScheme.onPrimaryContainer
        TaskStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        TaskStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
        TaskStatus.RUNNING -> MaterialTheme.colorScheme.onTertiaryContainer
        TaskStatus.QUEUED -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(shape = RoundedCornerShape(50), color = color) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = onColor,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

fun shareFile(context: Context, file: File) {
    if (!file.isFile) {
        Toast.makeText(context, "文件不可用", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(uri) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享 ${file.name}"))
    }.onFailure {
        Toast.makeText(context, "无法分享文件：${it.message}", Toast.LENGTH_SHORT).show()
    }
}
