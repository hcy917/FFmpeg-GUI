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
            "你是 FFmpeg GUI 的媒体智能体，可以直接操作本机文件、FFmpeg 和 Python。" +
                "你的目标是把用户的自然语言需求完整执行到产出文件，而不是只给建议。" +
                "请始终使用简体中文，先理解需求，再调用工具，最后报告准确的输入与输出绝对路径。" +
                "处理多个文件时逐个探测并批量执行；合并时注意音轨和分辨率差异；拆分时允许指定输出格式。" +
                "执行破坏性操作前先用一句话说明，重要步骤要保留可复现的 FFmpeg 命令。"
    }
}

@Serializable
data class AppSettings(
    val themeMode: String = "system",
    val dynamicColor: Boolean = false,
    val ai: AiSettings = AiSettings(),
)

@Serializable
data class SavedScript(
    val id: String,
    val name: String,
    val relativePath: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long = 0L,
    val lastResult: String = "",
    val generatedByAi: Boolean = true,
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
