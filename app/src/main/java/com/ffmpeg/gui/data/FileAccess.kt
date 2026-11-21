package com.ffmpeg.gui.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Path guard for tools. The app workspace is always available; the whole shared
 * storage becomes available after the user grants "all files access".
 */
class FileAccess(
    private val context: Context,
    private val workspace: Workspace,
) {
    val publicRoot: File = Environment.getExternalStorageDirectory()

    fun allFilesGranted(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
            // Android 10 enforces scoped storage for apps targeting 30+; the legacy
            // WRITE permission no longer grants arbitrary paths there.
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> false
            else -> ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun roots(): List<File> = buildList {
        add(workspace.root)
        if (allFilesGranted()) add(publicRoot)
    }.distinctBy { it.absolutePath }

    fun resolve(input: String, mustExist: Boolean = false): File? {
        val raw = input.trim()
        if (raw.isBlank()) return null
        val candidate = if (raw.startsWith("/")) File(raw) else workspace.resolve(raw) ?: return null
        val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        if (!isAllowed(canonical)) return null
        if (mustExist && !canonical.exists()) return null
        return canonical
    }

    fun isAllowed(file: File): Boolean {
        val path = runCatching { file.canonicalPath }.getOrNull() ?: return false
        return roots().any { root ->
            val rootPath = runCatching { root.canonicalPath }.getOrNull() ?: return@any false
            path == rootPath || path.startsWith(rootPath + File.separator)
        }
    }

    fun describe(file: File): String {
        val type = when {
            file.isDirectory -> "目录"
            else -> file.extension.lowercase().ifBlank { "文件" }
        }
        return "${file.absolutePath} | $type | ${if (file.isFile) file.length() else 0} 字节"
    }

    fun list(path: String, recursive: Boolean, max: Int): String {
        val base = if (path.isBlank()) workspace.root else resolve(path, mustExist = true) ?: return "错误：路径不存在或无权访问：$path"
        if (!base.isDirectory) return "错误：不是目录：${base.absolutePath}"
        val limit = max.coerceIn(1, 500)
        val rows = mutableListOf<String>()
        fun walk(dir: File, depth: Int) {
            if (rows.size >= limit || depth > 4) return
            dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))?.forEach { child ->
                if (rows.size >= limit || child.name.startsWith(".")) return@forEach
                rows += if (child.isDirectory) {
                    "[目录] ${child.absolutePath}（${child.listFiles()?.size ?: 0} 项）"
                } else {
                    "[文件] ${child.absolutePath}（${child.length()} 字节）"
                }
                if (recursive && child.isDirectory) walk(child, depth + 1)
            }
        }
        walk(base, 0)
        return if (rows.isEmpty()) "（空目录）${base.absolutePath}" else rows.joinToString("\n")
    }

    fun readText(file: File, maxBytes: Int = 600_000): String {
        if (!file.isFile) return "错误：不是文件：${file.absolutePath}"
        if (file.length() > maxBytes) return "错误：文件过大（${file.length()} 字节），请用 media_probe 或分段方式处理"
        val bytes = file.readBytes()
        if (bytes.take(4096).any { it == 0.toByte() }) {
            return "错误：这是二进制文件，不能按文本读取。请使用 media_probe 或 FFmpeg/Python 工具处理：${file.absolutePath}"
        }
        return file.readText()
    }

    fun writeText(input: String, content: String): File {
        val target = resolve(input) ?: error("路径不在允许范围内：$input")
        if (target == workspace.root || roots().any { it == target }) error("不能覆盖存储根目录")
        target.parentFile?.mkdirs()
        target.writeText(content)
        return target
    }

    fun copy(source: File, inputDestination: String): File {
        val destination = resolve(inputDestination) ?: error("目标路径不在允许范围内：$inputDestination")
        if (destination.isDirectory) {
            val target = workspace.uniqueFile(destination, source.name)
            source.copyTo(target, overwrite = false)
            return target
        }
        destination.parentFile?.mkdirs()
        source.copyTo(destination, overwrite = false)
        return destination
    }

    fun move(source: File, inputDestination: String): File {
        val destination = resolve(inputDestination) ?: error("目标路径不在允许范围内：$inputDestination")
        val target = if (destination.isDirectory) File(destination, source.name) else destination
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = false)
            source.delete()
        }
        return target
    }

    fun delete(file: File): Boolean {
        if (file == workspace.root || roots().any { it == file }) return false
        return if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    fun relativeLabel(file: File): String {
        return if (file.canonicalPath.startsWith(workspace.root.canonicalPath + File.separator)) {
            "工作区/" + workspace.relative(file)
        } else file.absolutePath
    }
}
