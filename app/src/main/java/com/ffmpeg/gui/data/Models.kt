package com.ffmpeg.gui.data

import kotlinx.serialization.Serializable

@Serializable
enum class MediaKind { VIDEO, AUDIO, IMAGE, OTHER }

@Serializable
data class MediaItem(
    val id: String,
    val name: String,
    val path: String,
    val mime: String = "",
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val videoCodec: String = "",
    val audioCodec: String = "",
    val kind: MediaKind = MediaKind.OTHER,
    val addedAt: Long = 0L,
    val isOutput: Boolean = false,
    val source: String = "import",
) {
    val displayDuration: String
        get() = when {
            durationMs <= 0 -> "—"
            durationMs >= 3_600_000 -> "%d:%02d:%02d".format(durationMs / 3_600_000, (durationMs / 60_000) % 60, (durationMs / 1000) % 60)
            else -> "%d:%02d".format(durationMs / 60_000, (durationMs / 1000) % 60)
        }

    val resolution: String
        get() = if (width > 0 && height > 0) "${width}×${height}" else ""

    val formatLabel: String
        get() = name.substringAfterLast('.', "").uppercase().ifBlank { "媒体" }
}

@Serializable
data class WorkspaceEntry(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val childCount: Int = 0,
)

@Serializable
enum class TaskStatus { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }

@Serializable
data class TaskRecord(
    val id: String,
    val title: String,
    val subtitle: String,
    val command: String = "",
    val status: TaskStatus = TaskStatus.QUEUED,
    val progress: Float = 0f,
    val logs: String = "",
    val outputPaths: List<String> = emptyList(),
    val error: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long = 0L,
)

@Serializable
data class MergeOptions(
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val videoBitrate: String = "6M",
    val audioBitrate: String = "192k",
    val container: String = "mp4",
)

@Serializable
data class SplitOptions(
    val mode: SplitMode = SplitMode.DURATION,
    val secondsPerPart: Int = 60,
    val parts: Int = 2,
    val outputFormat: String = "mp4",
    val reencode: Boolean = true,
)

@Serializable
enum class SplitMode { DURATION, PARTS }

@Serializable
data class TrackInfo(
    val index: Int = 0,
    val type: String = "",
    val codec: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val sampleRate: String = "",
    val channels: String = "",
    val durationMs: Long = 0,
)

@Serializable
data class ProbeInfo(
    val format: String = "",
    val formatLong: String = "",
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val bitrate: Long = 0,
    val streams: List<TrackInfo> = emptyList(),
) {
    val hasVideo: Boolean get() = streams.any { it.type == "video" }
    val hasAudio: Boolean get() = streams.any { it.type == "audio" }
    val video: TrackInfo? get() = streams.firstOrNull { it.type == "video" }
    val audio: TrackInfo? get() = streams.firstOrNull { it.type == "audio" }
}

@Serializable
data class AiSettings(
    val baseUrl: String = "https://api.deepseek.com/v1",
    val model: String = "deepseek-chat",
    val apiKey: String = "",
    val temperature: Double = 0.3,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val autoRunTools: Boolean = true,
    val maxIterations: Int = 8,
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "你是 FFmpeg GUI 的本地媒体智能助手，在用户的应用工作区内工作。" +
                "你可以查看全部文件、探测媒体、运行 FFmpeg，并执行 Python 脚本。" +
                "请始终使用简体中文回答，语气简洁、务实。" +
                "优先使用工作区内的路径；执行有破坏性的操作前先说明将要做什么；完成后报告准确的输出路径。"
    }
}

@Serializable
enum class StorageMode { INTERNAL, EXTERNAL, CUSTOM }

@Serializable
data class StorageSettings(
    val mode: StorageMode = StorageMode.INTERNAL,
    val customPath: String = "",
)

@Serializable
data class AppSettings(
    val themeMode: String = "system",
    val dynamicColor: Boolean = false,
    val ai: AiSettings = AiSettings(),
    val storage: StorageSettings = StorageSettings(),
)

@Serializable
data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val toolName: String = "",
    val toolArgs: String = "",
    val toolResult: String = "",
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
data class ToolTrace(
    val name: String,
    val arguments: String,
    val result: String,
)

@Serializable
data class ChatTurn(
    val assistant: String,
    val traces: List<ToolTrace> = emptyList(),
    val error: String = "",
)
