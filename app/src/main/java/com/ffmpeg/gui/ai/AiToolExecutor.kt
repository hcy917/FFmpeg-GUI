package com.ffmpeg.gui.ai

import com.ffmpeg.gui.data.MediaRepository
import com.ffmpeg.gui.data.Workspace
import com.ffmpeg.gui.engine.FFmpegEngine
import com.ffmpeg.gui.engine.MediaProbe
import com.ffmpeg.gui.python.PythonRunner
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

class AiToolExecutor(
    private val workspace: Workspace,
    private val engine: FFmpegEngine,
    private val pythonRunner: PythonRunner,
    private val mediaRepository: MediaRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    val specs: List<ToolSpec> = listOf(
        tool(
            "workspace_list",
            "List files and folders in the app workspace. Use this before acting on files.",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "Relative folder path, empty for workspace root") }
                }
            },
        ),
        tool(
            "workspace_read",
            "Read a UTF-8 text or JSON file from the workspace.",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "Relative file path") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("path")) }
            },
        ),
        tool(
            "workspace_write",
            "Create or replace a text file in the workspace. Parent folders are created automatically.",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "Relative file path") }
                    putJsonObject("content") { put("type", "string"); put("description", "Full file content") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("path")); add(kotlinx.serialization.json.JsonPrimitive("content")) }
            },
        ),
        tool(
            "media_library",
            "Return all imported and generated media in the library with kind, duration, resolution, and absolute path.",
            buildJsonObject { putJsonObject("properties") {} },
        ),
        tool(
            "media_probe",
            "Probe a media file with FFprobe. Returns format, duration, and streams.",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "Relative workspace path") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("path")) }
            },
        ),
        tool(
            "ffmpeg_run",
            "Run FFmpeg with custom arguments. Prefer absolute paths inside the workspace; the workspace root is provided in the system prompt. Always include -y for overwrite. Returns the FFmpeg log tail.",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("arguments") {
                        put("type", "string")
                        put("description", "FFmpeg arguments without the leading 'ffmpeg', e.g. -y -i \"/root/inbox/a.mp4\" -c copy \"/root/outputs/a.mkv\"")
                    }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("arguments")) }
            },
        ),
        tool(
            "python_run",
            "Execute an existing .py script from the workspace with the embedded Python interpreter. Returns its log output.",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("script") { put("type", "string"); put("description", "Relative script path, e.g. python/examples/smart_report.py") }
                    putJsonObject("args") { put("type", "string"); put("description", "Optional command-line arguments") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("script")) }
            },
        ),
        tool(
            "python_list",
            "List available Python scripts and installed libraries.",
            buildJsonObject { putJsonObject("properties") {} },
        ),
    )

    private fun tool(name: String, description: String, parameters: JsonObject): ToolSpec {
        val schema = buildJsonObject {
            put("type", "object")
            parameters.forEach { (key, value) -> put(key, value) }
            if (!parameters.containsKey("properties")) putJsonObject("properties") {}
        }
        return ToolSpec(function = ToolFunctionSpec(name = name, description = description, parameters = schema))
    }

    suspend fun execute(name: String, arguments: String): String {
        return try {
            val args = if (arguments.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(arguments) as? JsonObject ?: JsonObject(emptyMap())
            when (name) {
                "workspace_list" -> workspaceList(args.string("path"))
                "workspace_read" -> workspaceRead(args.string("path"))
                "workspace_write" -> workspaceWrite(args.string("path"), args.string("content"))
                "media_library" -> mediaLibrary()
                "media_probe" -> mediaProbe(args.string("path"))
                "ffmpeg_run" -> ffmpegRun(args.string("arguments"))
                "python_run" -> pythonRun(args.string("script"), args.string("args"))
                "python_list" -> pythonList()
                else -> "Unknown tool: $name"
            }
        } catch (t: Throwable) {
            "ERROR: ${t.message ?: t::class.java.simpleName}"
        }
    }

    private fun workspaceList(path: String): String {
        val entries = workspace.listTree(path)
        if (entries.isEmpty()) return "(empty) at ${if (path.isBlank()) "workspace root" else path}"
        return entries.joinToString("\n") {
            if (it.isDirectory) "[dir] ${it.relativePath} (${it.childCount} entries)"
            else "[file] ${it.relativePath} (${it.sizeBytes} bytes)"
        }
    }

    private fun workspaceRead(path: String): String {
        if (path.isBlank()) return "ERROR: path is required"
        val file = workspace.resolve(path) ?: return "ERROR: path outside workspace"
        if (!file.isFile) return "ERROR: not a file: $path"
        if (file.length() > 2_000_000) return "ERROR: file too large (${file.length()} bytes)"
        return file.readText()
    }

    private fun workspaceWrite(path: String, content: String): String {
        if (path.isBlank()) return "ERROR: path is required"
        val file = workspace.resolve(path) ?: return "ERROR: path outside workspace"
        file.parentFile?.mkdirs()
        file.writeText(content)
        return "Wrote ${workspace.relative(file)} (${content.length} chars)"
    }

    private fun mediaLibrary(): String {
        val items = mediaRepository.items.value
        if (items.isEmpty()) return "The media library is empty."
        return items.joinToString("\n") {
            "${it.name} | ${it.kind} | ${it.resolution} | ${it.displayDuration} | ${it.videoCodec}/${it.audioCodec} | ${it.path}"
        }
    }

    private suspend fun mediaProbe(path: String): String {
        val file = if (path.startsWith("/")) File(path) else workspace.resolve(path)
        if (file == null || !file.isFile) return "ERROR: file not found: $path"
        if (!file.canonicalPath.startsWith(workspace.root.canonicalPath)) return "ERROR: path outside workspace"
        val info = MediaProbe.probe(file)
        return buildString {
            appendLine("file: ${file.absolutePath}")
            appendLine("format: ${info.formatLong.ifBlank { info.format }}")
            appendLine("duration_ms: ${info.durationMs}")
            appendLine("size_bytes: ${info.sizeBytes}")
            info.streams.forEach { stream ->
                appendLine("stream ${stream.index}: ${stream.type}, codec=${stream.codec}, ${stream.width}x${stream.height}, sample_rate=${stream.sampleRate}")
            }
        }
    }

    private fun ffmpegRun(arguments: String): String {
        if (arguments.isBlank()) return "ERROR: arguments are required"
        if (arguments.contains("\u0000")) return "ERROR: invalid arguments"
        val output = engine.runBlocking(arguments)
        return output.lineSequence()
            .filter { it.isNotBlank() }
            .toList()
            .takeLast(100)
            .joinToString("\n")
            .ifBlank { "FFmpeg finished with no output." }
    }

    private suspend fun pythonRun(script: String, args: String): String {
        if (script.isBlank()) return "ERROR: script is required"
        val logs = StringBuilder()
        val result = pythonRunner.runScript(script, args) { line ->
            synchronized(logs) {
                logs.append(line).append('\n')
                if (logs.length > 20_000) logs.delete(0, logs.length - 15_000)
            }
        }
        val tail = logs.toString().takeLast(12_000)
        return if (result.success) {
            "Python finished in ${result.durationMs} ms.\n$tail\nResult: ${result.result}"
        } else {
            "Python failed: ${result.error}\n$tail"
        }
    }

    private fun pythonList(): String {
        val scripts = pythonRunner.scripts().joinToString("\n") { "  ${workspace.relative(it)}" }
        val libs = pythonRunner.libraries().joinToString("\n") { "  ${it.name} ${it.version} (${it.kind})" }
        return "Scripts:\n${scripts.ifBlank { "  (none)" }}\nLibraries:\n${libs.ifBlank { "  (none)" }}"
    }

    private fun JsonObject.string(key: String): String =
        runCatching { this[key]?.jsonPrimitive?.content ?: "" }.getOrDefault("")
}
