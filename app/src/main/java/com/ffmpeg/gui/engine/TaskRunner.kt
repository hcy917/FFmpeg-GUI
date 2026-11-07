package com.ffmpeg.gui.engine

import com.ffmpeg.gui.data.MediaRepository
import com.ffmpeg.gui.data.TaskRecord
import com.ffmpeg.gui.data.TaskStatus
import com.ffmpeg.gui.data.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class QueuedCommand(
    val title: String,
    val subtitle: String,
    val command: String,
    val expectedOutputs: List<File> = emptyList(),
    val outputDirectory: File? = null,
    val totalDurationMs: Long = 0,
    val addOutputsToLibrary: Boolean = true,
)

class TaskRunner(
    private val scope: CoroutineScope,
    private val engine: FFmpegEngine,
    private val workspace: Workspace,
    private val mediaRepository: MediaRepository,
) {
    private val _tasks = MutableStateFlow<List<TaskRecord>>(emptyList())
    val tasks: StateFlow<List<TaskRecord>> = _tasks.asStateFlow()

    private val _completed = MutableSharedFlow<TaskRecord>(extraBufferCapacity = 8)
    val completed: SharedFlow<TaskRecord> = _completed.asSharedFlow()

    private val queue = Channel<Pair<String, QueuedCommand>>(Channel.UNLIMITED)
    private val cancelledQueued = ConcurrentHashMap.newKeySet<String>()
    private var activeId: String? = null

    init {
        scope.launch {
            for ((id, request) in queue) {
                if (cancelledQueued.remove(id)) continue
                execute(id, request)
            }
        }
    }

    fun enqueue(request: QueuedCommand): String {
        val id = UUID.randomUUID().toString()
        val record = TaskRecord(
            id = id,
            title = request.title,
            subtitle = request.subtitle,
            command = request.command,
            status = TaskStatus.QUEUED,
            progress = 0f,
        )
        _tasks.value = listOf(record) + _tasks.value.take(49)
        queue.trySend(id to request)
        return id
    }

    fun cancel(id: String) {
        val record = _tasks.value.firstOrNull { it.id == id } ?: return
        when (record.status) {
            TaskStatus.QUEUED -> {
                cancelledQueued.add(id)
                update(id) { it.copy(status = TaskStatus.CANCELLED, finishedAt = System.currentTimeMillis(), error = "排队时已取消") }
            }
            TaskStatus.RUNNING -> {
                if (activeId == id) engine.cancelCurrent()
            }
            else -> Unit
        }
    }

    fun clearFinished() {
        _tasks.value = _tasks.value.filter { it.status == TaskStatus.RUNNING || it.status == TaskStatus.QUEUED }
    }

    fun dismiss(id: String) {
        _tasks.value = _tasks.value.filterNot { it.id == id }
    }

    private suspend fun execute(id: String, request: QueuedCommand) {
        activeId = id
        update(id) { it.copy(status = TaskStatus.RUNNING, progress = 0.01f, logs = "▶ ${request.command}\n") }
        val logBuffer = StringBuilder("▶ ${request.command}\n")
        var lastUiUpdate = 0L
        val result = try {
            engine.run(
                rawCommand = request.command,
                onLog = { line ->
                    synchronized(logBuffer) {
                        if (line.isNotBlank()) {
                            logBuffer.append(line).append('\n')
                            if (logBuffer.length > 40_000) {
                                logBuffer.delete(0, logBuffer.length - 30_000)
                            }
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastUiUpdate > 150) {
                            lastUiUpdate = now
                            update(id) { it.copy(logs = logBuffer.toString()) }
                        }
                    }
                },
                onProgress = { time, _, _ ->
                    val progress = if (request.totalDurationMs > 0) {
                        (time * 1000.0 / request.totalDurationMs).toFloat().coerceIn(0.02f, 0.98f)
                    } else {
                        0.5f
                    }
                    update(id) { it.copy(progress = progress) }
                },
            )
        } catch (t: Throwable) {
            FFmpegResult(false, false, -1, "", t.message ?: "未知 FFmpeg 错误")
        }

        val finishedAt = System.currentTimeMillis()
        if (result.success) {
            val outputs = collectOutputs(request)
            if (request.addOutputsToLibrary && outputs.isNotEmpty()) {
                mediaRepository.addFiles(outputs, isOutput = true, source = "ffmpeg")
            }
            synchronized(logBuffer) { logBuffer.append("\n✓ 用时 ${"%.1f".format(result.elapsedMs / 1000.0)} 秒") }
            update(id) {
                it.copy(
                    status = TaskStatus.COMPLETED,
                    progress = 1f,
                    logs = logBuffer.toString(),
                    outputPaths = outputs.map { file -> file.absolutePath },
                    finishedAt = finishedAt,
                )
            }
        } else {
            val cancelled = result.cancelled || !scope.isActive
            val detail = result.failStackTrace.ifBlank {
                logBuffer.toString().lineSequence().filter { it.isNotBlank() }.toList().takeLast(8).joinToString("\n")
            }
            update(id) {
                it.copy(
                    status = if (cancelled) TaskStatus.CANCELLED else TaskStatus.FAILED,
                    progress = 0f,
                    logs = logBuffer.toString(),
                    error = if (cancelled) "已取消" else detail.ifBlank { "FFmpeg 退出码：${result.returnCode}" },
                    finishedAt = finishedAt,
                )
            }
        }
        activeId = null
        _tasks.value.firstOrNull { it.id == id }?.let { _completed.tryEmit(it) }
    }

    private suspend fun collectOutputs(request: QueuedCommand): List<File> {
        val found = linkedSetOf<File>()
        request.expectedOutputs.filter { it.isFile && it.length() > 0 }.forEach { found += it }
        request.outputDirectory?.let { dir ->
            if (dir.isDirectory) {
                dir.listFiles()?.filter { it.isFile && it.length() > 0 }?.sortedBy { it.name }?.forEach { found += it }
            }
        }
        return found.toList()
    }

    private fun update(id: String, transform: (TaskRecord) -> TaskRecord) {
        _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
    }
}
