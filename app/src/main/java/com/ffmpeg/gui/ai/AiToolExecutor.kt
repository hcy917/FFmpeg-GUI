package com.ffmpeg.gui.ai

import android.content.Context
import com.ffmpeg.gui.data.FileAccess
import com.ffmpeg.gui.data.MediaRepository
import com.ffmpeg.gui.data.ScriptRepository
import com.ffmpeg.gui.data.Workspace
import com.ffmpeg.gui.engine.FFmpegEngine
import com.ffmpeg.gui.engine.MediaProbe
import com.ffmpeg.gui.python.PythonRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class AiToolExecutor(
    private val context: Context,
    private val workspace: Workspace,
    private val engine: FFmpegEngine,
    private val pythonRunner: PythonRunner,
    private val mediaRepository: MediaRepository,
    private val scripts: ScriptRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val files = FileAccess(context, workspace)
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    val specs: List<ToolSpec> = listOf(
        tool(
            "fs_list",
            "列出文件或目录。工作区相对路径和绝对路径都支持；递归时最多 4 层、500 项。所有文件操作前先查看。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "目录路径；留空表示工作区根目录") }
                    putJsonObject("recursive") { put("type", "boolean"); put("description", "是否递归，默认 true") }
                    putJsonObject("max") { put("type", "integer"); put("description", "最大条目数，默认 200") }
                }
            },
        ),
        tool(
            "fs_read",
            "读取 UTF-8 文本、字幕、JSON 或 Python 源码。二进制媒体请用 media_probe。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "文件路径") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("path")) }
            },
        ),
        tool(
            "fs_write",
            "创建或覆盖 UTF-8 文本文件（字幕、CSV、配置、Python 源码等），自动创建父目录。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "目标路径") }
                    putJsonObject("content") { put("type", "string"); put("description", "完整文件内容") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("path")); add(kotlinx.serialization.json.JsonPrimitive("content")) }
            },
        ),
        tool(
            "fs_copy",
            "复制文件或目录。dest 可以是目录（自动保留文件名）或完整目标路径。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("source") { put("type", "string"); put("description", "源路径") }
                    putJsonObject("destination") { put("type", "string"); put("description", "目标路径") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("source")); add(kotlinx.serialization.json.JsonPrimitive("destination")) }
            },
        ),
        tool(
            "fs_move",
            "移动或重命名文件/目录。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("source") { put("type", "string"); put("description", "源路径") }
                    putJsonObject("destination") { put("type", "string"); put("description", "目标路径") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("source")); add(kotlinx.serialization.json.JsonPrimitive("destination")) }
            },
        ),
        tool(
            "fs_delete",
            "删除文件或目录（目录会递归删除）。不可恢复，执行前必须确认路径。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "要删除的路径") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("path")) }
            },
        ),
        tool(
            "fs_mkdir",
            "创建目录（可递归）。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "目录路径") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("path")) }
            },
        ),
        tool(
            "download_file",
            "从 HTTP(S) 下载文件到工作区。path 省略时保存到 inbox 并按 URL 文件名命名。适合获取字体、字幕、测试媒体或 Python 库。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("url") { put("type", "string"); put("description", "完整 HTTP(S) 地址") }
                    putJsonObject("path") { put("type", "string"); put("description", "可选目标路径或目录") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("url")) }
            },
        ),
        tool(
            "workspace_info",
            "返回工作区绝对路径、各目录、存储权限、媒体库数量、AI 脚本和 Python 库概况。",
            buildJsonObject { putJsonObject("properties") {} },
        ),
        tool(
            "media_library",
            "列出媒体库全部文件（导入与生成），包含类型、时长、分辨率、编码与绝对路径。",
            buildJsonObject { putJsonObject("properties") {} },
        ),
        tool(
            "media_probe",
            "用 FFprobe 探测任意可访问音视频/图片，返回封装、时长、码率和每条流的编码、分辨率、采样率。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "媒体文件路径") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("path")) }
            },
        ),
        tool(
            "ffmpeg_run",
            "执行 FFmpeg 命令。arguments 不包含开头 ffmpeg；输入输出必须使用绝对路径；覆盖文件加 -y。支持任意格式转换、批量、合并、拆分、滤镜、抽帧、字幕等。返回退出状态和日志末尾。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("arguments") {
                        put("type", "string")
                        put("description", "例如 -y -i \"/path/in.mp4\" -c:v libx264 -crf 22 -c:a aac \"/path/out.mkv\"")
                    }
                    putJsonObject("timeout_seconds") { put("type", "integer"); put("description", "超时秒数，默认 1800，最大 7200") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("arguments")) }
            },
        ),
        tool(
            "ffmpeg_capabilities",
            "查看当前 FFmpeg 版本、支持的封装格式、视频/音频编码器和滤镜。规划不常见格式转换前调用。",
            buildJsonObject { putJsonObject("properties") {} },
        ),
        tool(
            "python_save_script",
            "把 AI 生成的 Python 脚本保存到专用的 AI 脚本目录，供用户之后在“已保存脚本”中运行、编辑或导出。返回脚本相对路径。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("name") { put("type", "string"); put("description", "脚本文件名，例如 batch_rename.py") }
                    putJsonObject("code") { put("type", "string"); put("description", "完整 Python 源码") }
                    putJsonObject("description") { put("type", "string"); put("description", "一句话说明用途") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("name")); add(kotlinx.serialization.json.JsonPrimitive("code")) }
            },
        ),
        tool(
            "python_run",
            "用内置 Python 3.11 执行 .py 脚本，返回输出。脚本可使用工作区文件、FFmpeg 命令和已安装的 Python 库。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("script") { put("type", "string"); put("description", "脚本路径或文件名（会在 AI 脚本目录中查找）") }
                    putJsonObject("args") { put("type", "string"); put("description", "可选命令行参数") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("script")) }
            },
        ),
        tool(
            "python_list",
            "列出 AI 脚本、全部 Python 脚本和已安装的 Python 库。",
            buildJsonObject { putJsonObject("properties") {} },
        ),
        tool(
            "python_install_library",
            "从工作区中的 .whl、.zip 或 .py 文件安装 Python 库，安装到脚本可导入的库目录。",
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "库文件路径") }
                }
                putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("path")) }
            },
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

    suspend fun execute(name: String, arguments: String): String = withContext(Dispatchers.IO) {
        try {
            val args = if (arguments.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(arguments) as? JsonObject ?: JsonObject(emptyMap())
            when (name) {
                "fs_list" -> files.list(args.string("path"), args.bool("recursive", true), args.int("max", 200))
                "fs_read" -> readFile(args.string("path"))
                "fs_write" -> writeFile(args.string("path"), args.string("content"))
                "fs_copy" -> copyFile(args.string("source"), args.string("destination"))
                "fs_move" -> moveFile(args.string("source"), args.string("destination"))
                "fs_delete" -> deleteFile(args.string("path"))
                "fs_mkdir" -> makeDirectory(args.string("path"))
                "download_file" -> downloadFile(args.string("url"), args.string("path"))
                "workspace_info" -> workspaceInfo()
                "media_library" -> mediaLibrary()
                "media_probe" -> mediaProbe(args.string("path"))
                "ffmpeg_run" -> ffmpegRun(args.string("arguments"), args.int("timeout_seconds", 1800))
                "ffmpeg_capabilities" -> ffmpegCapabilities()
                "python_save_script" -> savePythonScript(args.string("name"), args.string("code"), args.string("description"))
                "python_run" -> pythonRun(args.string("script"), args.string("args"))
                "python_list" -> pythonList()
                "python_install_library" -> installPythonLibrary(args.string("path"))
                else -> "未知工具：$name"
            }
        } catch (t: Throwable) {
            "错误：${t.message ?: t::class.java.simpleName}"
        }
    }

    private fun readFile(path: String): String {
        val file = files.resolve(path, mustExist = true) ?: return "错误：文件不存在或无权访问：$path"
        if (file.isDirectory) return "错误：这是目录，请使用 fs_list：$path"
        return files.readText(file)
    }

    private fun writeFile(path: String, content: String): String {
        if (path.isBlank()) return "错误：必须填写路径"
        val file = files.writeText(path, content)
        return "已写入 ${file.absolutePath}（${content.length} 字符）"
    }

    private fun copyFile(source: String, destination: String): String {
        val from = files.resolve(source, mustExist = true) ?: return "错误：源文件不存在或无权访问：$source"
        val target = files.copy(from, destination)
        return "已复制到 ${target.absolutePath}（${target.length()} 字节）"
    }

    private fun moveFile(source: String, destination: String): String {
        val from = files.resolve(source, mustExist = true) ?: return "错误：源文件不存在或无权访问：$source"
        val target = files.move(from, destination)
        return "已移动到 ${target.absolutePath}"
    }

    private fun deleteFile(path: String): String {
        val file = files.resolve(path, mustExist = true) ?: return "错误：文件不存在或无权访问：$path"
        return if (files.delete(file)) "已删除 ${file.absolutePath}" else "错误：无法删除 ${file.absolutePath}"
    }

    private fun makeDirectory(path: String): String {
        val dir = files.resolve(path) ?: return "错误：路径不在允许范围内：$path"
        return if (dir.mkdirs() || dir.isDirectory) "已创建 ${dir.absolutePath}" else "错误：无法创建 ${dir.absolutePath}"
    }

    private suspend fun downloadFile(url: String, rawPath: String): String = withContext(Dispatchers.IO) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return@withContext "错误：只支持 HTTP(S) 地址"
        val nameFromUrl = url.substringBefore('?').substringAfterLast('/').ifBlank { "download_${System.currentTimeMillis()}" }
        val target = when {
            rawPath.isBlank() -> workspace.uniqueFile(workspace.inbox, workspace.sanitize(nameFromUrl))
            files.resolve(rawPath)?.isDirectory == true -> workspace.uniqueFile(files.resolve(rawPath)!!, workspace.sanitize(nameFromUrl))
            else -> files.resolve(rawPath) ?: return@withContext "错误：目标路径不在允许范围内：$rawPath"
        }
        val request = Request.Builder().url(url).header("User-Agent", "FFmpeg-GUI/1.1").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext "错误：下载失败 HTTP ${response.code}"
            val body = response.body ?: return@withContext "错误：响应为空"
            if (body.contentLength() > 1_500_000_000L) return@withContext "错误：文件超过 1.5GB"
            target.parentFile?.mkdirs()
            body.byteStream().use { input -> target.outputStream().use { output -> input.copyTo(output, 1 shl 20) } }
        }
        "已下载到 ${target.absolutePath}（${target.length()} 字节）"
    }

    private fun workspaceInfo(): String = buildString {
        appendLine("工作区：${workspace.root.absolutePath}")
        appendLine("导入目录：${workspace.inbox.absolutePath}")
        appendLine("输出目录：${workspace.outputs.absolutePath}")
        appendLine("AI 脚本目录：${workspace.aiScriptsDir.absolutePath}")
        appendLine("Python 库目录：${workspace.libsDir.absolutePath}")
        appendLine("所有文件访问：${if (files.allFilesGranted()) "已授权，可读写共享存储" else "未授权，只能访问工作区；可在设置中开启"}")
        appendLine("媒体库：${mediaRepository.items.value.size} 个文件")
        appendLine("AI 脚本：${scripts.scripts.value.size} 个")
        appendLine("Python 库：${pythonRunner.libraries().size} 个")
    }

    private fun mediaLibrary(): String {
        val items = mediaRepository.items.value
        if (items.isEmpty()) return "媒体库为空。可以使用 fs_list 查看工作区，或让用户导入文件。"
        return items.joinToString("\n") {
            "${it.name} | ${it.kind} | ${it.resolution} | ${it.displayDuration} | ${it.videoCodec}/${it.audioCodec} | ${it.path}"
        }
    }

    private suspend fun mediaProbe(path: String): String {
        val file = files.resolve(path, mustExist = true) ?: return "错误：找不到文件或无权访问：$path"
        if (file.isDirectory) return "错误：这是目录：${file.absolutePath}"
        val info = MediaProbe.probe(file)
        return buildString {
            appendLine("文件：${file.absolutePath}")
            appendLine("大小：${file.length()} 字节")
            appendLine("封装：${info.formatLong.ifBlank { info.format }}")
            appendLine("时长毫秒：${info.durationMs}")
            appendLine("码率：${info.bitrate}")
            info.streams.forEach { stream ->
                appendLine("流 ${stream.index}：${stream.type}，编码=${stream.codec}，${stream.width}x${stream.height}，采样率=${stream.sampleRate}，声道=${stream.channels}")
            }
        }
    }

    private suspend fun ffmpegRun(arguments: String, timeoutSeconds: Int): String {
        if (arguments.isBlank()) return "错误：必须提供参数"
        if (arguments.contains("\u0000")) return "错误：参数无效"
        val logs = StringBuilder()
        val result = withTimeoutOrNull(timeoutSeconds.coerceIn(10, 7200) * 1000L) {
            engine.run(arguments, onLog = { line ->
                synchronized(logs) {
                    if (line.isNotBlank()) logs.append(line).append('\n')
                    if (logs.length > 40_000) logs.delete(0, logs.length - 30_000)
                }
            })
        }
        if (result == null) {
            engine.cancelCurrent()
            return "FFmpeg 执行超时，已请求取消。\n${logs.takeLast(4000)}"
        }
        val tail = logs.toString().lineSequence().filter { it.isNotBlank() }.toList().takeLast(100).joinToString("\n")
        return buildString {
            appendLine(if (result.success) "FFmpeg 成功，用时 ${result.elapsedMs / 1000} 秒。" else "FFmpeg 失败，退出码 ${result.returnCode}。")
            if (result.failStackTrace.isNotBlank()) appendLine(result.failStackTrace)
            appendLine(tail.ifBlank { result.output.takeLast(4000) })
        }
    }

    private fun ffmpegCapabilities(): String {
        val version = engine.runBlocking("-hide_banner -version")
        val formats = engine.runBlocking("-hide_banner -formats")
        val formatLines = formats.lineSequence()
            .filter { it.startsWith(" DE") || it.startsWith(" D ") || it.startsWith("  E") || it.startsWith("DE") || it.startsWith("D ") || it.startsWith(" E") }
            .take(160)
            .joinToString("\n")
        val encoders = engine.runBlocking("-hide_banner -encoders").lineSequence()
            .filter { Regex("^\\s*[VAS][A-Z.]{5}\\s").containsMatchIn(it) }
            .take(100)
            .joinToString("\n")
        return "${version.lineSequence().firstOrNull { it.contains("ffmpeg version") } ?: "FFmpeg"}\n\n封装格式：\n$formatLines\n\n编码器：\n$encoders"
    }

    private fun savePythonScript(name: String, code: String, description: String): String {
        if (code.isBlank()) return "错误：脚本内容为空"
        val script = scripts.save(name.ifBlank { "ai_script.py" }, code, description, generatedByAi = true)
        return "已保存到 ${script.relativePath}\n绝对路径：${workspace.resolve(script.relativePath)?.absolutePath}\n之后可在“已保存脚本”中运行。"
    }

    private suspend fun pythonRun(script: String, args: String): String {
        if (script.isBlank()) return "错误：必须提供脚本"
        val file = resolveScript(script) ?: return "错误：找不到脚本：$script"
        val logs = StringBuilder()
        val result = pythonRunner.runScript(workspace.relative(file), args) { line ->
            synchronized(logs) {
                logs.append(line).append('\n')
                if (logs.length > 30_000) logs.delete(0, logs.length - 20_000)
            }
        }
        val scriptEntry = scripts.scripts.value.firstOrNull { it.relativePath == workspace.relative(file) }
        if (scriptEntry != null) scripts.markRun(scriptEntry.id, result.error.ifBlank { result.result }, result.success)
        val tail = logs.toString().takeLast(12_000)
        return if (result.success) {
            "Python 运行完成，用时 ${result.durationMs} 毫秒。\n$tail\n结果：${result.result}"
        } else {
            "Python 运行失败：${result.error}\n$tail"
        }
    }

    private fun resolveScript(input: String): File? {
        val direct = files.resolve(input, mustExist = true)
        if (direct?.isFile == true) return direct
        val name = input.substringAfterLast('/')
        return workspace.aiScriptsDir.listFiles()?.firstOrNull { it.name == name && it.isFile }
            ?: workspace.pythonDir.walkTopDown().firstOrNull { it.isFile && it.name == name }
    }

    private fun pythonList(): String {
        val saved = scripts.scripts.value.joinToString("\n") { "  ${it.name} — ${it.description.ifBlank { "无说明" }} (${it.relativePath})" }
        val all = pythonRunner.scripts().joinToString("\n") { "  ${workspace.relative(it)}" }
        val libs = pythonRunner.libraries().joinToString("\n") { "  ${it.name} ${it.version}（${it.kind}）" }
        return "AI 脚本：\n${saved.ifBlank { "  （无）" }}\n全部脚本：\n${all.ifBlank { "  （无）" }}\n库：\n${libs.ifBlank { "  （无）" }}"
    }

    private suspend fun installPythonLibrary(path: String): String {
        val file = files.resolve(path, mustExist = true) ?: return "错误：找不到库文件或无权访问：$path"
        if (file.isDirectory) return "错误：请选择 .whl、.zip 或 .py 文件"
        val logs = StringBuilder()
        val result = pythonRunner.installLibraryFile(file) { message ->
            logs.append(message).append('\n')
        }
        return result.fold(
            onSuccess = { "$it\n$logs" },
            onFailure = { "安装失败：${it.message}\n$logs" },
        )
    }

    private fun JsonObject.string(key: String): String =
        runCatching { this[key]?.jsonPrimitive?.content ?: "" }.getOrDefault("")

    private fun JsonObject.bool(key: String, default: Boolean): Boolean =
        runCatching { this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: default }.getOrDefault(default)

    private fun JsonObject.int(key: String, default: Int): Int =
        runCatching { this[key]?.jsonPrimitive?.int ?: default }.getOrDefault(default)
}
