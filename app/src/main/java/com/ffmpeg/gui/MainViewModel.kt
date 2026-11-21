package com.ffmpeg.gui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ffmpeg.gui.ai.AiAgent
import com.ffmpeg.gui.ai.AiToolExecutor
import com.ffmpeg.gui.ai.OpenAiClient
import com.ffmpeg.gui.data.AiSettings
import com.ffmpeg.gui.data.AppSettings
import com.ffmpeg.gui.data.ChatMessage
import com.ffmpeg.gui.data.FileAccess
import com.ffmpeg.gui.data.MediaRepository
import com.ffmpeg.gui.data.SavedScript
import com.ffmpeg.gui.data.ScriptRepository
import com.ffmpeg.gui.data.SettingsRepository
import com.ffmpeg.gui.data.Workspace
import com.ffmpeg.gui.engine.FFmpegEngine
import com.ffmpeg.gui.python.PythonBridge
import com.ffmpeg.gui.python.PythonLibrary
import com.ffmpeg.gui.python.PythonRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settingsRepository = SettingsRepository(context)
    val workspace = Workspace(context)
    val mediaRepository = MediaRepository(context, workspace)
    val engine = FFmpegEngine()
    val fileAccess = FileAccess(context, workspace)
    val pythonRunner = PythonRunner(context, workspace)
    val scripts = ScriptRepository(context, workspace)
    val aiClient = OpenAiClient()
    val aiToolExecutor = AiToolExecutor(context, workspace, engine, pythonRunner, mediaRepository, scripts)
    val aiAgent = AiAgent(aiClient, aiToolExecutor, workspace)

    init {
        PythonBridge.initialize(workspace, engine, mediaRepository)
    }
}

class FFmpegGuiApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

data class ImportUiState(
    val active: Boolean = false,
    val message: String = "",
    val copied: Int = 0,
)

data class ScriptRunUiState(
    val running: Boolean = false,
    val scriptId: String = "",
    val output: String = "",
    val result: String = "",
    val failed: Boolean = false,
)

data class WorkspaceStats(
    val mediaCount: Int = 0,
    val totalBytes: Long = 0,
    val outputCount: Int = 0,
    val scriptCount: Int = 0,
)

class MainViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {
    private val workspace = container.workspace
    private val settingsRepo = container.settingsRepository
    private val mediaRepository = container.mediaRepository
    private val agent = container.aiAgent
    private val scriptsRepo = container.scripts
    private val pythonRunner = container.pythonRunner
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    val settings: StateFlow<AppSettings> = settingsRepo.settings
    val scripts: StateFlow<List<SavedScript>> = scriptsRepo.scripts

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _agentBusy = MutableStateFlow(false)
    val agentBusy: StateFlow<Boolean> = _agentBusy.asStateFlow()

    private val _agentStatus = MutableStateFlow("")
    val agentStatus: StateFlow<String> = _agentStatus.asStateFlow()

    private val _agentError = MutableStateFlow("")
    val agentError: StateFlow<String> = _agentError.asStateFlow()

    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    private val _stats = MutableStateFlow(WorkspaceStats())
    val stats: StateFlow<WorkspaceStats> = _stats.asStateFlow()

    private val _scriptRun = MutableStateFlow(ScriptRunUiState())
    val scriptRun: StateFlow<ScriptRunUiState> = _scriptRun.asStateFlow()

    private val _libraries = MutableStateFlow<List<PythonLibrary>>(emptyList())
    val libraries: StateFlow<List<PythonLibrary>> = _libraries.asStateFlow()

    private val _pythonVersion = MutableStateFlow("Python 3.11（Chaquopy）")
    val pythonVersion: StateFlow<String> = _pythonVersion.asStateFlow()

    private val _libraryMessage = MutableStateFlow("")
    val libraryMessage: StateFlow<String> = _libraryMessage.asStateFlow()

    private val _allFilesGranted = MutableStateFlow(container.fileAccess.allFilesGranted())
    val allFilesGranted: StateFlow<Boolean> = _allFilesGranted.asStateFlow()

    private val _requestedTab = MutableStateFlow<String?>(null)
    val requestedTab: StateFlow<String?> = _requestedTab.asStateFlow()

    private var activeJob: Job? = null

    init {
        loadChat()
        refreshStats()
        refreshLibraries()
        scriptsRepo.refresh()
        viewModelScope.launch {
            _pythonVersion.value = withContext(Dispatchers.IO) {
                runCatching { pythonRunner.pythonVersion() }.getOrDefault("Python 3.11（Chaquopy）")
            }
        }
    }

    val workspacePath: String get() = workspace.root.absolutePath
    val inboxPath: String get() = workspace.inbox.absolutePath
    val outputsPath: String get() = workspace.outputs.absolutePath
    val scriptsPath: String get() = workspace.aiScriptsDir.absolutePath

    // ---------------- Chat / Agent ----------------

    fun requestTab(tab: String) {
        _requestedTab.value = tab
    }

    fun consumeTabRequest() {
        _requestedTab.value = null
    }

    fun openSettingsRequested() {
        requestTab("settings")
    }

    fun clearAgentError() {
        _agentError.value = ""
    }

    fun sendChat(text: String, attachments: List<Uri> = emptyList()) {
        val prompt = text.trim()
        if (prompt.isBlank() && attachments.isEmpty()) return
        if (_agentBusy.value) return
        val ai = settings.value.ai
        if (ai.baseUrl.isBlank() || ai.model.isBlank()) {
            _agentError.value = "请先在“设置 → AI 模型”中填写接口地址和模型名。"
            return
        }
        _agentError.value = ""
        _agentBusy.value = true
        _agentStatus.value = "正在准备…"
        val history = _messages.value.toList()
        activeJob = viewModelScope.launch {
            var userContent = prompt
            try {
                if (attachments.isNotEmpty()) {
                    _agentStatus.value = "正在导入附件…"
                    val imported = withContext(Dispatchers.IO) {
                        attachments.mapNotNull { uri ->
                            runCatching { workspace.importUri(uri) }.getOrNull()
                        }
                    }
                    if (imported.isNotEmpty()) {
                        withContext(Dispatchers.IO) { mediaRepository.addFiles(imported) }
                        userContent = buildString {
                            if (prompt.isNotBlank()) appendLine(prompt)
                            appendLine()
                            appendLine("[用户附带的文件，已导入工作区]")
                            imported.forEach { appendLine("- ${it.absolutePath}") }
                        }
                    }
                }
                val userMessage = ChatMessage(id = UUID.randomUUID().toString(), role = "user", content = userContent)
                updateMessages(_messages.value + userMessage)
                val turn = agent.run(
                    history = history,
                    userMessage = userContent,
                    settings = ai,
                    onStatus = { _agentStatus.value = it },
                )
                val traceMessages = turn.traces.map { trace ->
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = "tool",
                        content = "调用 ${trace.name}",
                        toolName = trace.name,
                        toolArgs = trace.arguments,
                        toolResult = trace.result,
                    )
                }
                val assistant = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = turn.assistant,
                )
                updateMessages(_messages.value + traceMessages + assistant)
                refreshStats()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                val detail = t.message ?: t.toString()
                _agentError.value = detail
                updateMessages(
                    _messages.value + ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = "任务失败：$detail",
                        isError = true,
                    ),
                )
            } finally {
                _agentBusy.value = false
                _agentStatus.value = ""
                saveChat()
            }
        }
    }

    fun stopAgent() {
        activeJob?.cancel()
        activeJob = null
        _agentBusy.value = false
        _agentStatus.value = ""
        _agentError.value = "已停止当前任务。"
    }

    fun clearChat() {
        if (_agentBusy.value) return
        _messages.value = emptyList()
        _agentError.value = ""
        saveChat()
    }

    private fun updateMessages(value: List<ChatMessage>) {
        _messages.value = value
        saveChat()
    }

    private fun chatFile(): File = File(workspace.root, "chat/history.json")

    private fun loadChat() {
        runCatching {
            if (chatFile().isFile) {
                _messages.value = json.decodeFromString<List<ChatMessage>>(chatFile().readText())
                    .filter { it.role in setOf("user", "assistant", "tool") }
            }
        }
    }

    private fun saveChat() {
        runCatching {
            chatFile().parentFile?.mkdirs()
            chatFile().writeText(json.encodeToString(_messages.value))
        }
    }

    // ---------------- Settings ----------------

    fun updateAi(ai: AiSettings) {
        settingsRepo.updateAi(
            ai.copy(
                baseUrl = ai.baseUrl.trim(),
                model = ai.model.trim(),
                apiKey = ai.apiKey.trim(),
                temperature = ai.temperature.coerceIn(0.0, 2.0),
                maxIterations = ai.maxIterations.coerceIn(1, 24),
            ),
        )
    }

    fun updateTheme(themeMode: String, dynamicColor: Boolean) {
        settingsRepo.updateTheme(themeMode, dynamicColor)
    }

    fun resetSystemPrompt() {
        settingsRepo.updateAi(settings.value.ai.copy(systemPrompt = AiSettings.DEFAULT_SYSTEM_PROMPT))
    }

    suspend fun testAiConnection(ai: AiSettings): String = withContext(Dispatchers.IO) {
        runCatching {
            val completion = container.aiClient.complete(
                settings = ai,
                messages = listOf(com.ffmpeg.gui.ai.ChatMsg(role = "user", content = "只回复：连接成功")),
                tools = emptyList(),
            )
            "连接成功：${completion.content.take(120).ifBlank { "（空回复）" }}"
        }.getOrElse { "连接失败：${it.message ?: it}" }
    }

    // ---------------- Import / storage ----------------

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importState.value = ImportUiState(active = true, message = "正在导入 ${uris.size} 个文件…")
            val imported = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> runCatching { workspace.importUri(uri) }.getOrNull() }
            }
            if (imported.isNotEmpty()) {
                withContext(Dispatchers.IO) { mediaRepository.addFiles(imported) }
            }
            refreshStats()
            _importState.value = ImportUiState(
                active = false,
                message = if (imported.isEmpty()) "导入失败或文件为空。" else "已导入 ${imported.size} 个文件到 ${workspace.inbox.absolutePath}",
                copied = imported.size,
            )
        }
    }

    fun importFolder(treeUri: Uri) {
        viewModelScope.launch {
            _importState.value = ImportUiState(active = true, message = "正在导入文件夹…")
            val files = withContext(Dispatchers.IO) { workspace.importTree(treeUri) { line -> _importState.value = ImportUiState(active = true, message = line) } }
            if (files.isNotEmpty()) {
                withContext(Dispatchers.IO) { mediaRepository.addFiles(files) }
            }
            refreshStats()
            _importState.value = ImportUiState(
                active = false,
                message = "文件夹导入完成：${files.size} 个文件，位于 ${workspace.inbox.absolutePath}",
                copied = files.size,
            )
        }
    }

    fun clearImportMessage() {
        if (!_importState.value.active) _importState.value = ImportUiState()
    }

    fun refreshStorageAccess() {
        _allFilesGranted.value = container.fileAccess.allFilesGranted()
    }

    fun refreshStats() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                var media = 0
                var bytes = 0L
                workspace.inbox.walkTopDown().forEach { if (it.isFile) { media++; bytes += it.length() } }
                workspace.outputs.walkTopDown().forEach { if (it.isFile) bytes += it.length() }
                WorkspaceStats(
                    mediaCount = media,
                    totalBytes = bytes,
                    outputCount = workspace.outputs.walkTopDown().count { it.isFile },
                    scriptCount = scriptsRepo.scripts.value.size,
                )
            }
            _stats.value = result
        }
    }

    fun openOutputsFolder() {
        // UI launches the system file manager with the workspace path.
    }

    // ---------------- Python scripts ----------------

    fun refreshScripts() {
        scriptsRepo.refresh()
        refreshStats()
    }

    fun importScript(uri: Uri) {
        viewModelScope.launch {
            val script = withContext(Dispatchers.IO) { scriptsRepo.importUri(uri) }
            _libraryMessage.value = if (script == null) "脚本导入失败。" else "已导入脚本：${script.name}"
            refreshStats()
        }
    }

    fun deleteScript(script: SavedScript) {
        val ok = scriptsRepo.delete(script.id)
        _libraryMessage.value = if (ok) "已删除 ${script.name}" else "删除失败：${script.name}"
        refreshStats()
    }

    fun createScript(
        name: String = "新脚本.py",
        content: String = NEW_SCRIPT_TEMPLATE,
        description: String = "手动创建",
    ): SavedScript? {
        val script = scriptsRepo.save(name, content, description, generatedByAi = false)
        _libraryMessage.value = "已创建 ${script.name}"
        refreshStats()
        return script
    }

    fun saveScript(script: SavedScript, name: String, content: String, description: String): SavedScript? {
        val updated = scriptsRepo.update(script.id, name, content, description)
        _libraryMessage.value = if (updated == null) "保存失败。" else "已保存 ${updated.name}"
        return updated
    }

    fun readScript(script: SavedScript): String = scriptsRepo.read(script)

    fun runScript(script: SavedScript, args: String = "") {
        if (_scriptRun.value.running) return
        _scriptRun.value = ScriptRunUiState(running = true, scriptId = script.id, output = "正在启动 Python…\n")
        viewModelScope.launch {
            val output = StringBuilder()
            val result = withContext(Dispatchers.IO) {
                pythonRunner.runScript(script.relativePath, args) { line ->
                    synchronized(output) {
                        output.append(line).append('\n')
                        _scriptRun.value = ScriptRunUiState(
                            running = true,
                            scriptId = script.id,
                            output = output.toString().takeLast(30_000),
                        )
                    }
                }
            }
            synchronized(output) {
                if (output.isEmpty()) output.append("（无日志）\n")
            }
            scriptsRepo.markRun(script.id, result.error.ifBlank { result.result }, result.success)
            _scriptRun.value = ScriptRunUiState(
                running = false,
                scriptId = script.id,
                output = output.toString().takeLast(30_000),
                result = result.result,
                failed = !result.success,
            )
            refreshStats()
        }
    }

    fun clearScriptRun() {
        if (!_scriptRun.value.running) _scriptRun.value = ScriptRunUiState()
    }

    fun exportScript(script: SavedScript, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = scriptsRepo.read(script)
            val ok = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray())
                    true
                } ?: false
            }.getOrDefault(false)
            _libraryMessage.value = if (ok) "已导出 ${script.name}" else "导出失败：${script.name}"
        }
    }

    fun refreshLibraries() {
        viewModelScope.launch {
            _libraries.value = withContext(Dispatchers.IO) { pythonRunner.libraries() }
        }
    }

    fun installLibrary(uri: Uri) {
        viewModelScope.launch {
            _libraryMessage.value = "正在安装 Python 库…"
            val result = withContext(Dispatchers.IO) {
                pythonRunner.installLibrary(uri) { line -> _libraryMessage.value = line }
            }
            _libraryMessage.value = result.fold(
                onSuccess = { "$it\n重启应用后可在脚本中 import。" },
                onFailure = { "安装失败：${it.message}" },
            )
            refreshLibraries()
        }
    }

    fun dismissLibraryMessage() {
        _libraryMessage.value = ""
    }

    override fun onCleared() {
        activeJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            val app = application as FFmpegGuiApp
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(application, app.container) as T
                }
            }
        }
    }
}


private val NEW_SCRIPT_TEMPLATE = "#!/usr/bin/env python3\n# -*- coding: utf-8 -*-\n\"\"\"在此编写你的批处理脚本。\"\"\"\n\nprint(\"Hello from FFmpeg GUI\")\n"
