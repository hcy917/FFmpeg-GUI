package com.ffmpeg.gui.python

import com.ffmpeg.gui.data.MediaRepository
import com.ffmpeg.gui.data.Workspace
import com.ffmpeg.gui.engine.FFmpegEngine
import com.ffmpeg.gui.engine.MediaProbe
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.Writer

/**
 * Methods exposed to embedded Python scripts via Chaquopy.
 * Python usage:
 *   from ffmpeg_gui import run_ffmpeg, list_files, read_text, write_text, probe
 */
object PythonBridge {
    @Volatile private var workspace: Workspace? = null
    @Volatile private var engine: FFmpegEngine? = null
    @Volatile private var mediaRepository: MediaRepository? = null
    private val listener = ThreadLocal<(String) -> Unit?>()

    fun initialize(workspace: Workspace, engine: FFmpegEngine, mediaRepository: MediaRepository) {
        this.workspace = workspace
        this.engine = engine
        this.mediaRepository = mediaRepository
    }

    fun begin(listener: (String) -> Unit) {
        this.listener.set(listener)
    }

    fun end() {
        listener.remove()
    }

    private fun emit(message: String) {
        listener.get()?.invoke(message)
    }

    @JvmStatic
    fun log(message: String) {
        emit(message)
    }

    @JvmStatic
    fun workspaceRoot(): String = workspace?.root?.absolutePath ?: ""

    @JvmStatic
    fun listFiles(relative: String = ""): String {
        val ws = workspace ?: return "workspace unavailable"
        val entries = ws.listTree(relative, limit = 500)
        if (entries.isEmpty()) return "(empty)"
        return entries.joinToString("\n") { entry ->
            if (entry.isDirectory) "[dir] ${entry.relativePath}/ (${entry.childCount} items)"
            else "[file] ${entry.relativePath} (${entry.sizeBytes} bytes)"
        }
    }

    @JvmStatic
    fun readText(relative: String): String {
        val ws = workspace ?: return ""
        val file = ws.resolve(relative) ?: return "Path is outside the workspace"
        if (!file.isFile) return "Not a file: $relative"
        if (file.length() > 1_000_000) return "File is too large to read as text (${file.length()} bytes)"
        return file.readText()
    }

    @JvmStatic
    fun writeText(relative: String, content: String): String {
        val ws = workspace ?: return "workspace unavailable"
        val file = ws.writeTextFile(relative, content) ?: return "Path is outside the workspace"
        return "Wrote ${file.absolutePath} (${content.length} chars)"
    }

    @JvmStatic
    fun runFfmpeg(args: String): String {
        val eng = engine ?: return "FFmpeg engine unavailable"
        emit("▶ ffmpeg $args")
        return try {
            val output = eng.runBlocking(args) { line -> if (line.isNotBlank()) emit(line) }
            val success = !output.contains("Conversion failed") && !output.contains("Error")
            emit(if (success) "✓ FFmpeg finished" else "✗ FFmpeg command may have failed")
            output
        } catch (t: Throwable) {
            emit("✗ ${t.message}")
            "ERROR: ${t.message}"
        }
    }

    @JvmStatic
    fun probe(relative: String): String {
        val ws = workspace ?: return "workspace unavailable"
        val file = ws.resolve(relative) ?: return "Path is outside the workspace"
        if (!file.isFile) return "Not a file: $relative"
        return runBlocking {
            val info = MediaProbe.probe(file)
            buildString {
                appendLine("file: ${file.name}")
                appendLine("format: ${info.format}")
                appendLine("duration_ms: ${info.durationMs}")
                appendLine("size_bytes: ${info.sizeBytes}")
                info.streams.forEachIndexed { index, stream ->
                    appendLine("stream[$index]: ${stream.type} codec=${stream.codec} ${stream.width}x${stream.height} sample_rate=${stream.sampleRate}")
                }
            }
        }
    }

    @JvmStatic
    fun listMedia(): String {
        val repo = mediaRepository ?: return ""
        return repo.items.value.joinToString("\n") { "${it.name} | ${it.kind} | ${it.durationMs}ms | ${it.path}" }
    }

    @JvmStatic
    fun refreshLibrary(): String {
        runBlocking { mediaRepository?.refresh() }
        return "Library refreshed"
    }
}

class CallbackWriter(
    private val callback: (String) -> Unit,
) : Writer() {
    private val buffer = StringBuilder()

    override fun write(cbuf: CharArray, off: Int, len: Int) {
        synchronized(buffer) {
            for (i in off until off + len) {
                val c = cbuf[i]
                if (c == '\n') {
                    flushBuffer()
                } else {
                    buffer.append(c)
                }
            }
        }
    }

    override fun flush() {
        synchronized(buffer) { flushBuffer() }
    }

    override fun close() {
        flush()
    }

    private fun flushBuffer() {
        if (buffer.isNotEmpty()) {
            val line = buffer.toString()
            buffer.clear()
            callback(line)
        }
    }
}
