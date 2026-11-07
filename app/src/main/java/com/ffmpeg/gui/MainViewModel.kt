package com.ffmpeg.gui

import android.app.Application
import android.content.Context
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
import com.ffmpeg.gui.data.MediaItem
import com.ffmpeg.gui.data.MediaRepository
import com.ffmpeg.gui.data.MergeOptions
import com.ffmpeg.gui.data.SettingsRepository
import com.ffmpeg.gui.data.SplitOptions
import com.ffmpeg.gui.data.TaskRecord
import com.ffmpeg.gui.data.TaskStatus
import com.ffmpeg.gui.data.Workspace
import com.ffmpeg.gui.engine.CommandBuilder
import com.ffmpeg.gui.engine.FFmpegEngine
import com.ffmpeg.gui.engine.Presets
import com.ffmpeg.gui.engine.QueuedCommand
import com.ffmpeg.gui.engine.TaskRunner
import com.ffmpeg.gui.python.PythonBridge
import com.ffmpeg.gui.python.PythonLibrary
import com.ffmpeg.gui.python.PythonRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val workspace = Workspace(context)
    val mediaRepository = MediaRepository(context, workspace)
    val settingsRepository = SettingsRepository(context)
    val engine = FFmpegEngine()
    val taskRunner = TaskRunner(appScope, engine, workspace, mediaRepository)
    val pythonRunner = PythonRunner(context, workspace)
    val aiClient = OpenAiClient()
    val aiToolExecutor = AiToolExecutor(workspace, engine, pythonRunner, mediaRepository)
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
    val label: String = "",
    val copied: Int = 0,
)

data class PythonUiState(
    val running: Boolean = false,
    val script: String = "",
    val logs: String = "",
    val lastResult: String = "",
    val failed: Boolean = false,
)

data class WorkspaceStats(
    val mediaCount: Int = 0,
    val totalBytes: Long = 0,
    val outputCount: Int = 0,
)

data class ScriptEntry(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
)

data class PythonLibraryState(
    val libraries: List<PythonLibrary> = emptyList(),
    val scripts: List<ScriptEntry> = emptyList(),
)

class MainViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {
    private val workspace: Workspace = container.workspace
    private val mediaRepository: MediaRepository = container.mediaRepository
    private val settingsRepository: SettingsRepository = container.settingsRepository
    private val taskRunner: TaskRunner = container.taskRunner
    private val pythonRunner: PythonRunner = container.pythonRunner
    private val engine: FFmpegEngine = container.engine
    private val aiAgent: AiAgent = container.aiAgent
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    val media: StateFlow<List<MediaItem>> = mediaRepository.items
    val tasks: StateFlow<List<TaskRecord>> = taskRunner.tasks
    val settings: StateFlow<AppSettings> = settingsRepository.settings

    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    private val _pythonState = MutableStateFlow(PythonUiState())
    val pythonState: StateFlow<PythonUiState> = _pythonState.asStateFlow()

    private val _pythonLibraryState = MutableStateFlow(PythonLibraryState())
    val pythonLibraryState: StateFlow<PythonLibraryState> = _pythonLibraryState.asStateFlow()

    private val _stats = MutableStateFlow(WorkspaceStats())
    val stats: StateFlow<WorkspaceStats> = _stats.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val ffmpegVersion: String = runCatching { engine.version() }.getOrDefault("unknown")
    val pythonVersion: String = "Python 3.11"

    private val chatFile = File(workspace.root, "chat/history.json")

    init {
        loadChat()
        refreshStats()
        refreshPython()
        viewModelScope.launch {
            mediaRepository.refresh()
            refreshStats()
        }
        viewModelScope.launch {
            taskRunner.completed.collectLatest { task ->
                if (task.status == TaskStatus.COMPLETED) {
                    _events.emit("${task.title} completed")
                    syncOutputs()
                } else if (task.status == TaskStatus.FAILED) {
                    _events.emit("${task.title} failed")
                }
                refreshStats()
            }
        }
    }

    fun importUris(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importState.value = ImportUiState(active = true, label = "Copying ${uris.size} file(s)…")
            val files = mutableListOf<File>()
            uris.forEachIndexed { index, uri ->
                _importState.value = ImportUiState(active = true, label = "Importing ${index + 1}/${uris.size}…", copied = files.size)
                workspace.importUri(uri)?.let { files += it }
            }
            val added = mediaRepository.addFiles(files, source = "import")
            _importState.value = ImportUiState()
            _events.emit(if (added.isEmpty()) "Nothing imported" else "Imported ${added.size} file(s)")
            refreshStats()
            syncOutputs()
        }
    }

    fun importTree(uri: android.net.Uri) {
        viewModelScope.launch {
            _importState.value = ImportUiState(active = true, label = "Scanning folder…")
            val files = workspace.importTree(uri) { message ->
                _importState.value = _importState.value.copy(label = message, copied = _importState.value.copied + 1)
            }
            val added = mediaRepository.addFiles(files, source = "folder")
            _importState.value = ImportUiState()
            _events.emit(if (added.isEmpty()) "No media found in folder" else "Imported ${added.size} file(s) from folder")
            refreshStats()
        }
    }

    fun removeMedia(items: List<MediaItem>, deleteFiles: Boolean = true) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            mediaRepository.remove(items.map { it.id }.toSet(), deleteFiles)
            _events.emit("Removed ${items.size} item(s)")
            refreshStats()
        }
    }

    fun convert(items: List<MediaItem>, presetId: String) {
        if (items.isEmpty()) return
        val preset = Presets.byId(presetId)
        items.forEach { item ->
            val output = workspace.uniqueFile(
                workspace.outputs,
                "${item.name.substringBeforeLast('.')}_${preset.id}.${preset.extension}",
            )
            val command = runCatching { CommandBuilder.convert(item, output, preset) }.getOrElse {
                _events.tryEmit("Could not build command: ${it.message}")
                return@forEach
            }
            taskRunner.enqueue(
                QueuedCommand(
                    title = "Convert ${item.name}",
                    subtitle = preset.title,
                    command = command,
                    expectedOutputs = listOf(output),
                    totalDurationMs = item.durationMs,
                )
            )
        }
        _events.tryEmit("Queued ${items.size} conversion task(s)")
    }

    fun merge(items: List<MediaItem>, options: MergeOptions) {
        if (items.size < 2) {
            _events.tryEmit("Select at least two items to merge")
            return
        }
        val output = workspace.uniqueFile(workspace.outputs, CommandBuilder.mergeOutputName(items, options.container))
        val command = runCatching { CommandBuilder.merge(items, options, output) }.getOrElse {
            _events.tryEmit("Could not build merge: ${it.message}")
            return
        }
        taskRunner.enqueue(
            QueuedCommand(
                title = "Merge ${items.size} items",
                subtitle = "→ ${output.name}",
                command = command,
                expectedOutputs = listOf(output),
                totalDurationMs = items.sumOf { it.durationMs },
            )
        )
        _events.tryEmit("Merge queued")
    }

    fun split(items: List<MediaItem>, options: SplitOptions) {
        if (items.isEmpty()) return
        items.forEach { item ->
            val dir = workspace.ensureOutputDir(CommandBuilder.splitOutputName(item))
            val command = runCatching { CommandBuilder.split(item, options, dir) }.getOrElse {
                _events.tryEmit("Could not build split: ${it.message}")
                return@forEach
            }
            taskRunner.enqueue(
                QueuedCommand(
                    title = "Split ${item.name}",
                    subtitle = "${options.outputFormat.uppercase()} segments",
                    command = command,
                    outputDirectory = dir,
                    totalDurationMs = item.durationMs,
                )
            )
        }
        _events.tryEmit("Queued ${items.size} split task(s)")
    }

    fun runConsole(commandText: String) {
        if (commandText.isBlank()) return
        var processed = commandText.trim()
        // Make common workspace-relative paths usable from FFmpeg's process cwd.
        listOf("inbox/", "outputs/", "python/", "sandbox/").forEach { prefix ->
            processed = processed.replace(Regex("(?<![A-Za-z0-9_./-])" + Regex.escape(prefix)), workspace.root.absolutePath + "/" + prefix)
        }
        val command = if (processed.startsWith("ffmpeg")) processed else "ffmpeg $processed"
        taskRunner.enqueue(
            QueuedCommand(
                title = "FFmpeg console",
                subtitle = command.take(90),
                command = command,
                addOutputsToLibrary = false,
            )
        )
    }

    fun cancelTask(id: String) = taskRunner.cancel(id)
    fun dismissTask(id: String) = taskRunner.dismiss(id)
    fun clearFinishedTasks() = taskRunner.clearFinished()

    fun createDemoMedia() {
        val video = workspace.uniqueFile(workspace.inbox, "ffmpeg_demo_${System.currentTimeMillis() % 100000}.mp4")
        val audio = workspace.uniqueFile(workspace.inbox, "ffmpeg_tone_${System.currentTimeMillis() % 100000}.m4a")
        taskRunner.enqueue(
            QueuedCommand(
                title = "Create demo clip",
                subtitle = "Testsrc2 + 440 Hz tone",
                command = "ffmpeg -hide_banner -y -f lavfi -i testsrc2=size=1280x720:rate=30:duration=5 " +
                    "-f lavfi -i sine=frequency=440:sample_rate=48000:duration=5 " +
                    "-c:v libx264 -preset veryfast -crf 23 -pix_fmt yuv420p -c:a aac -b:a 160k -shortest ${CommandBuilder.quote(video.absolutePath)}",
                expectedOutputs = listOf(video),
                totalDurationMs = 5000,
            )
        )
        taskRunner.enqueue(
            QueuedCommand(
                title = "Create demo audio",
                subtitle = "3-second 660 Hz tone",
                command = "ffmpeg -hide_banner -y -f lavfi -i sine=frequency=660:sample_rate=48000:duration=3 " +
                    "-c:a aac -b:a 192k ${CommandBuilder.quote(audio.absolutePath)}",
                expectedOutputs = listOf(audio),
                totalDurationMs = 3000,
            )
        )
        _events.tryEmit("Generating demo media…")
    }

    fun syncOutputs() {
        viewModelScope.launch {
            val files = workspace.outputs.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.toList()
            if (files.isNotEmpty()) {
                mediaRepository.addFiles(files, isOutput = true, source = "output")
            }
            mediaRepository.refresh()
            refreshStats()
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            mediaRepository.refresh()
            syncOutputs()
            refreshStats()
            refreshPython()
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            val items = mediaRepository.items.value
            val total = withContext(Dispatchers.IO) { workspace.workspaceSize() }
            _stats.value = WorkspaceStats(mediaCount = items.size, totalBytes = total, outputCount = items.count { it.isOutput })
        }
    }

    fun refreshPython() {
        viewModelScope.launch {
            val scripts = withContext(Dispatchers.IO) {
                pythonRunner.scripts().map { file ->
                    ScriptEntry(file.name, workspace.relative(file), file.absolutePath)
                }
            }
            val libs = withContext(Dispatchers.IO) { pythonRunner.libraries() }
            _pythonLibraryState.value = PythonLibraryState(libraries = libs, scripts = scripts)
        }
    }

    fun runPython(relativePath: String, args: String) {
        if (_pythonState.value.running) return
        viewModelScope.launch {
            _pythonState.value = PythonUiState(running = true, script = relativePath, logs = "Starting Python…\n")
            val builder = StringBuilder("Starting Python…\n")
            val result = pythonRunner.runScript(relativePath, args) { line ->
                synchronized(builder) {
                    builder.append(line).append('\n')
                    if (builder.length > 50_000) builder.delete(0, builder.length - 40_000)
                }
                _pythonState.value = _pythonState.value.copy(logs = builder.toString())
            }
            builder.append('\n').append(if (result.success) "✓ ${result.result}" else "✗ ${result.error}")
            _pythonState.value = PythonUiState(
                running = false,
                script = relativePath,
                logs = builder.toString(),
                lastResult = if (result.success) result.result else result.error,
                failed = !result.success,
            )
            refreshPython()
            syncOutputs()
            _events.tryEmit(if (result.success) "Python script finished" else "Python script failed")
        }
    }

    fun installPythonLibrary(uri: android.net.Uri) {
        viewModelScope.launch {
            _pythonState.value = _pythonState.value.copy(running = true, logs = "Installing Python library…\n")
            val result = pythonRunner.installLibrary(uri) { message ->
                _pythonState.value = _pythonState.value.copy(logs = _pythonState.value.logs + message + "\n")
            }
            _pythonState.value = _pythonState.value.copy(
                running = false,
                logs = _pythonState.value.logs + (result.fold({ "✓ $it" }, { "✗ ${it.message}" })),
                failed = result.isFailure,
            )
            refreshPython()
            _events.tryEmit(result.fold({ "Python library installed" }, { "Library import failed: ${it.message}" }))
        }
    }

    fun deletePythonLibrary(name: String) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { pythonRunner.deleteLibrary(name) }
            refreshPython()
            _events.tryEmit(if (deleted) "Removed $name" else "Could not remove $name")
        }
    }

    fun savePythonScript(name: String, content: String) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { pythonRunner.saveScript(name, content) }
            refreshPython()
            _events.tryEmit("Saved ${file.name}")
        }
    }

    fun deletePythonScript(relativePath: String) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { pythonRunner.deleteScript(relativePath) }
            refreshPython()
            _events.tryEmit(if (deleted) "Script deleted" else "Could not delete script")
        }
    }

    fun readPythonScript(relativePath: String): String = pythonRunner.readScript(relativePath)

    fun sendChat(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _aiBusy.value) return
        val aiSettings = settingsRepository.settings.value.ai
        val history = _messages.value
        _messages.value = _messages.value + ChatMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = trimmed,
        )
        saveChat()
        _aiBusy.value = true
        viewModelScope.launch {
            try {
                val turn = aiAgent.run(history, trimmed, aiSettings) { trace ->
                    val toolMessage = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = "tool",
                        content = "",
                        toolName = trace.name,
                        toolArgs = trace.arguments,
                        toolResult = trace.result,
                    )
                    _messages.value = _messages.value + toolMessage
                    saveChat()
                }
                _messages.value = _messages.value + ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = turn.assistant.ifBlank { "Done." },
                )
            } catch (t: Throwable) {
                _messages.value = _messages.value + ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = "Something went wrong: ${t.message ?: t::class.java.simpleName}",
                    isError = true,
                )
            } finally {
                _aiBusy.value = false
                saveChat()
                refreshStats()
                syncOutputs()
            }
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        saveChat()
    }

    fun saveAiSettings(ai: AiSettings) {
        settingsRepository.updateAi(ai)
        _events.tryEmit("AI settings saved")
    }

    fun setTheme(themeMode: String) {
        settingsRepository.updateTheme(themeMode)
    }

    fun setDynamicColor(enabled: Boolean) {
        settingsRepository.update(settingsRepository.settings.value.copy(dynamicColor = enabled))
    }

    fun refreshLibraryOnly() {
        viewModelScope.launch {
            mediaRepository.refresh()
            refreshStats()
        }
    }

    private fun loadChat() {
        runCatching {
            if (chatFile.isFile) {
                _messages.value = json.decodeFromString<List<ChatMessage>>(chatFile.readText())
            }
        }
    }

    private fun saveChat() {
        runCatching {
            chatFile.parentFile?.mkdirs()
            chatFile.writeText(json.encodeToString(_messages.value))
        }
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
