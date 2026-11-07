package com.ffmpeg.gui.python

import android.content.Context
import android.net.Uri
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.ffmpeg.gui.data.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

data class PythonRunResult(
    val success: Boolean,
    val result: String,
    val error: String = "",
    val durationMs: Long = 0,
)

data class PythonLibrary(
    val name: String,
    val version: String,
    val path: String,
    val kind: String,
)

class PythonRunner(
    private val context: Context,
    private val workspace: Workspace,
) {
    private val mutex = Mutex()
    @Volatile private var started = false

    fun ensureStarted() {
        if (!Python.isStarted()) {
            synchronized(this) {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(context.applicationContext))
                }
            }
        }
        started = true
        copyExampleScripts()
    }

    fun pythonVersion(): String = runCatching {
        ensureStarted()
        Python.getInstance().getModule("sys").get("version").toString()
    }.getOrDefault("Python（未启动）")

    suspend fun runScript(relativePath: String, args: String, onLog: (String) -> Unit): PythonRunResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val file = workspace.resolve(relativePath)
                ?: return@withContext PythonRunResult(false, "", "脚本路径不在工作区内")
            if (!file.isFile) return@withContext PythonRunResult(false, "", "找不到脚本：$relativePath")
            try {
                ensureStarted()
                workspace.libsDir.mkdirs()
                PythonBridge.begin { line -> onLog(line) }
                onLog("▶ Python · ${file.name}${if (args.isBlank()) "" else " $args"}")
                val writer = CallbackWriter { line -> onLog(line) }
                val module = Python.getInstance().getModule("ffmpeg_gui_runner")
                val writerObject = PyObject.fromJava(writer)
                val result = module.callAttr(
                    "run_file",
                    file.absolutePath,
                    args,
                    workspace.libsDir.absolutePath,
                    writerObject,
                )
                runCatching { writer.flush() }
                PythonRunResult(
                    success = true,
                    result = result?.toString() ?: "",
                    durationMs = System.currentTimeMillis() - start,
                )
            } catch (t: Throwable) {
                val detail = t.message ?: t.toString()
                onLog("✗ Python 错误：$detail")
                PythonRunResult(false, "", detail, System.currentTimeMillis() - start)
            } finally {
                PythonBridge.end()
            }
        }
    }

    fun scripts(): List<File> {
        copyExampleScripts()
        return workspace.pythonDir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() == "py" }
            .filterNot { it.canonicalPath.startsWith(workspace.libsDir.canonicalPath) }
            .sortedBy { workspace.relative(it) }
            .toList()
    }

    fun readScript(relativePath: String): String {
        val file = workspace.resolve(relativePath) ?: return ""
        return runCatching { file.readText() }.getOrDefault("")
    }

    fun saveScript(name: String, content: String, subFolder: String = ""): File {
        val safeName = workspace.sanitize(name).let { if (it.endsWith(".py")) it else "$it.py" }
        val dir = if (subFolder.isBlank()) workspace.pythonDir else File(workspace.pythonDir, subFolder).apply { mkdirs() }
        val file = workspace.uniqueFile(dir, safeName)
        file.writeText(content)
        return file
    }

    fun deleteScript(relativePath: String): Boolean {
        val file = workspace.resolve(relativePath) ?: return false
        if (file.extension != "py") return false
        return file.delete()
    }

    suspend fun installLibrary(uri: Uri, onProgress: (String) -> Unit = {}): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val name = workspace.queryDisplayName(uri) ?: "library_${System.currentTimeMillis()}.zip"
            val temp = File(workspace.tempDir, workspace.sanitize(name))
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("无法读取所选库文件")
            workspace.libsDir.mkdirs()
            val lower = name.lowercase()
            if (lower.endsWith(".py")) {
                val target = workspace.uniqueFile(workspace.libsDir, workspace.sanitize(name))
                temp.copyTo(target, overwrite = true)
                onProgress("已添加单文件模块 ${target.name}")
                temp.delete()
                return@runCatching target.name
            }
            if (!lower.endsWith(".zip") && !lower.endsWith(".whl")) {
                error("请选择 .whl、.zip 或 .py 库文件")
            }
            var count = 0
            ZipInputStream(temp.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val entryName = entry.name.replace('\\', '/')
                    if (entry.isDirectory) continue
                    if (entryName.startsWith("/") || entryName.split('/').any { it == ".." }) {
                        throw SecurityException("库压缩包中存在不安全路径：${entry.name}")
                    }
                    val target = File(workspace.libsDir, entryName)
                    if (!target.canonicalPath.startsWith(workspace.libsDir.canonicalPath + File.separator)) {
                        throw SecurityException("库压缩包中存在不安全路径：${entry.name}")
                    }
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output -> zip.copyTo(output) }
                    count++
                    if (count % 20 == 0) onProgress("正在解压 $count 个文件…")
                }
            }
            temp.delete()
            onProgress("已将 ${count.coerceAtLeast(1)} 个文件安装到 Python 库路径")
            "已安装 $name（$count 个文件）。重新运行脚本即可导入。"
        }
    }

    fun libraries(): List<PythonLibrary> {
        workspace.libsDir.mkdirs()
        val result = mutableListOf<PythonLibrary>()
        workspace.libsDir.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { entry ->
            when {
                entry.isDirectory && entry.name.endsWith(".dist-info") -> {
                    val meta = File(entry, "METADATA")
                    val name = meta.takeIf { it.isFile }?.readLines()
                        ?.firstOrNull { it.startsWith("Name:") }?.removePrefix("Name:")?.trim()
                    val version = meta.takeIf { it.isFile }?.readLines()
                        ?.firstOrNull { it.startsWith("Version:") }?.removePrefix("Version:")?.trim()
                    if (name != null) {
                        result += PythonLibrary(name, version ?: "", entry.name, "wheel 元数据")
                    }
                }
                entry.isDirectory -> result += PythonLibrary(entry.name, "", entry.name, "包")
                entry.extension == "py" -> result += PythonLibrary(entry.nameWithoutExtension, "", entry.name, "模块")
            }
        }
        return result.distinctBy { it.name }
    }

    fun deleteLibrary(name: String): Boolean {
        val targets = workspace.libsDir.listFiles()?.filter {
            it.name == name || it.name == "$name.py" || it.name.startsWith("$name-") ||
                (it.isDirectory && it.name.endsWith(".dist-info") && it.name.startsWith("$name-"))
        } ?: return false
        var deleted = false
        targets.forEach { file ->
            deleted = if (file.isDirectory) file.deleteRecursively() else file.delete() || deleted
        }
        return deleted
    }

    private fun copyExampleScripts() {
        val examples = File(workspace.pythonDir, "examples").apply { mkdirs() }
        val marker = File(examples, ".seeded_v2")
        if (marker.exists()) return
        runCatching {
            listOf("probe_library.py", "batch_convert.py", "smart_report.py").forEach { name ->
                val target = File(examples, name)
                context.assets.open("python_examples/$name").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            marker.writeText("ok")
        }
    }
}
