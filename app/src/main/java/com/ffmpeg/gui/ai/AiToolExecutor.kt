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
            "列出应用工作区中的文件和文件夹。操作文件前先使用此工具。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "相对文件夹路径；留空表示工作区根目录") }
                }
            },
        ),
        tool(
            "workspace_read",
            "读取工作区中的 UTF-8 文本或 JSON 文件。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "相对文件路径") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("path")) }
            },
        ),
        tool(
            "workspace_write",
            "在工作区创建或覆盖文本文件，自动创建父文件夹。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "相对文件路径") }
                    putJsonObject("content") { put("type", "string"); put("description", "完整文件内容") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("path")); add(kotlinx.serialization.json.JsonPrimitive("content")) }
            },
        ),
        tool(
            "media_library",
            "返回媒体库中全部导入和生成的媒体，包括类型、时长、分辨率和绝对路径。",
            buildJsonObject { putJsonObject("properties") {} },
        ),
        tool(
            "media_probe",
            "用 FFprobe 探测媒体文件，返回封装、时长和流信息。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "工作区相对路径") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("path")) }
            },
        ),
        tool(
            "ffmpeg_run",
            "使用自定义参数运行 FFmpeg。请使用工作区内的绝对路径；系统提示中已给出工作区根目录。覆盖文件时请加入 -y。返回 FFmpeg 日志末尾。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("arguments") {
                        put("type", "string")
                        put("description", "不含开头 ffmpeg 的参数，例如 -y -i \"/root/inbox/a.mp4\" -c copy \"/root/outputs/a.mkv\"")
                    }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("arguments")) }
            },
        ),
        tool(
            "python_run",
            "使用内置 Python 解释器执行工作区中现有的 .py 脚本，返回日志输出。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("script") { put("type", "string"); put("description", "相对脚本路径，例如 python/examples/smart_report.py") }
                    putJsonObject("args") { put("type", "string"); put("description", "可选的命令行参数") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("script")) }
            },
        ),
        tool(
            "python_list",
            "列出可用的 Python 脚本和已安装库。",
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
                else -> "未知工具：$name"
            }
        } catch (t: Throwable) {
            "错误：${t.message ?: t::class.java.simpleName}"
        }
    }

    private fun workspaceList(path: String): String {
        val entries = workspace.listTree(path)
        if (entries.isEmpty()) return "(empty) at ${if (path.isBlank()) "workspace root" else path}"
        return entries.joinToString("\n") {
            if (it.isDirectory) "[目录] ${it.relativePath}（${it.childCount} 项）"
            else "[文件] ${it.relativePath}（${it.sizeBytes} 字节）"
        }
    }

    private fun workspaceRead(path: String): String {
        if (path.isBlank()) return "错误：必须填写路径"
        val file = workspace.resolve(path) ?: return "错误：路径不在工作区内"
        if (!file.isFile) return "错误：不是文件：$path"
        if (file.length() > 2_000_000) return "错误：文件过大（${file.length()} 字节）"
        return file.readText()
    }

    private fun workspaceWrite(path: String, content: String): String {
        if (path.isBlank()) return "错误：必须填写路径"
        val file = workspace.resolve(path) ?: return "错误：路径不在工作区内"
        file.parentFile?.mkdirs()
        file.writeText(content)
        return "已写入 ${workspace.relative(file)}（${content.length} 字符）"
    }

    private fun mediaLibrary(): String {
        val items = mediaRepository.items.value
        if (items.isEmpty()) return "媒体库为空。"
        return items.joinToString("\n") {
            "${it.name} | ${it.kind} | ${it.resolution} | ${it.displayDuration} | ${it.videoCodec}/${it.audioCodec} | ${it.path}"
        }
    }

    private suspend fun mediaProbe(path: String): String {
        val file = if (path.startsWith("/")) File(path) else workspace.resolve(path)
        if (file == null || !file.isFile) return "错误：找不到文件：$path"
        if (!file.canonicalPath.startsWith(workspace.root.canonicalPath)) return "错误：路径不在工作区内"
        val info = MediaProbe.probe(file)
        return buildString {
            appendLine("文件：${file.absolutePath}")
            appendLine("封装：${info.formatLong.ifBlank { info.format }}")
            appendLine("时长毫秒：${info.durationMs}")
            appendLine("大小字节：${info.sizeBytes}")
            info.streams.forEach { stream ->
                appendLine("流 ${stream.index}：${stream.type}，编码=${stream.codec}，${stream.width}x${stream.height}，采样率=${stream.sampleRate}")
            }
        }
    }

    private fun ffmpegRun(arguments: String): String {
        if (arguments.isBlank()) return "错误：必须提供参数"
        if (arguments.contains("\u0000")) return "错误：参数无效"
        val output = engine.runBlocking(arguments)
        return output.lineSequence()
            .filter { it.isNotBlank() }
            .toList()
            .takeLast(100)
            .joinToString("\n")
            .ifBlank { "FFmpeg finished with no output." }
    }

    private suspend fun pythonRun(script: String, args: String): String {
        if (script.isBlank()) return "错误：必须提供脚本"
        val logs = StringBuilder()
        val result = pythonRunner.runScript(script, args) { line ->
            synchronized(logs) {
                logs.append(line).append('\n')
                if (logs.length > 20_000) logs.delete(0, logs.length - 15_000)
            }
        }
        val tail = logs.toString().takeLast(12_000)
        return if (result.success) {
            "Python 运行完成，用时 ${result.durationMs} 毫秒。\n$tail\n结果：${result.result}"
        } else {
            "Python 运行失败：${result.error}\n$tail"
        }
    }

    private fun pythonList(): String {
        val scripts = pythonRunner.scripts().joinToString("\n") { "  ${workspace.relative(it)}" }
        val libs = pythonRunner.libraries().joinToString("\n") { "  ${it.name} ${it.version}（${it.kind}）" }
        return "脚本：\n${scripts.ifBlank { "  （无）" }}\n库：\n${libs.ifBlank { "  （无）" }}"
    }

    private fun JsonObject.string(key: String): String =
        runCatching { this[key]?.jsonPrimitive?.content ?: "" }.getOrDefault("")
}
