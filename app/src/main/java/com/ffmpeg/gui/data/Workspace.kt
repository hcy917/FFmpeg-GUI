package com.ffmpeg.gui.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class Workspace(private val context: Context) {

    val root: File = File(context.filesDir, "workspace").apply { mkdirs() }
    val inbox: File = File(root, "inbox").apply { mkdirs() }
    val outputs: File = File(root, "outputs").apply { mkdirs() }
    val pythonDir: File = File(root, "python").apply { mkdirs() }
    val libsDir: File = File(pythonDir, "libs").apply { mkdirs() }
    val tempDir: File = File(context.cacheDir, "work").apply { mkdirs() }

    init {
        File(pythonDir, "examples").mkdirs()
        File(root, "sandbox").mkdirs()
    }

    fun resolve(relative: String): File? {
        val cleaned = relative.trim().trimStart('/', '\\').replace('\\', '/')
        if (cleaned.split('/').any { it == ".." }) return null
        val file = File(root, cleaned)
        val canonicalRoot = root.canonicalPath + File.separator
        return if (file.canonicalPath == root.canonicalPath || file.canonicalPath.startsWith(canonicalRoot)) file else null
    }

    fun relative(file: File): String {
        return try {
            file.canonicalPath.removePrefix(root.canonicalPath + File.separator).replace(File.separatorChar, '/')
        } catch (_: Exception) {
            file.name
        }
    }

    fun ensureOutputDir(name: String): File {
        val safe = sanitize(name).ifBlank { "output" }
        var dir = File(outputs, safe)
        var index = 2
        while (dir.exists()) {
            dir = File(outputs, "${safe}_$index")
            index++
        }
        dir.mkdirs()
        return dir
    }

    fun uniqueFile(dir: File, name: String): File {
        val safe = sanitize(name).ifBlank { "media" }
        var target = File(dir, safe)
        if (!target.exists()) return target
        val base = safe.substringBeforeLast('.', safe)
        val ext = safe.substringAfterLast('.', "")
        var index = 2
        while (true) {
            val candidateName = if (ext.isBlank()) "${base}_$index" else "${base}_$index.$ext"
            target = File(dir, candidateName)
            if (!target.exists()) return target
            index++
        }
    }

    fun sanitize(name: String): String {
        val cleaned = name
            .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
        return cleaned.take(120).ifBlank { "file" }
    }

    suspend fun importUri(uri: Uri, subFolder: String = "inbox"): File? = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(uri) ?: "import_${System.currentTimeMillis()}"
            val dir = File(root, subFolder).apply { mkdirs() }
            val target = uniqueFile(dir, displayName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output, 1 shl 20) }
            } ?: return@runCatching null
            target
        }.getOrNull()
    }

    suspend fun importTree(treeUri: Uri, onProgress: (String) -> Unit = {}): List<File> = withContext(Dispatchers.IO) {
        val imported = mutableListOf<File>()
        runCatching {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching
            val folderName = sanitize(tree.name ?: "folder_${System.currentTimeMillis()}")
            val destinationRoot = uniqueFile(inbox, folderName).apply { mkdirs() }
            fun walk(doc: DocumentFile, target: File) {
                val children = doc.listFiles()
                children.forEach { child ->
                    val childName = sanitize(child.name ?: "item")
                    if (child.isDirectory) {
                        val dir = File(target, childName).apply { mkdirs() }
                        onProgress("Scanning $childName…")
                        walk(child, dir)
                    } else if (child.isFile) {
                        val file = uniqueFile(target, childName)
                        runCatching {
                            context.contentResolver.openInputStream(child.uri)?.use { input ->
                                file.outputStream().use { output -> input.copyTo(output, 1 shl 20) }
                            }
                            if (file.exists() && file.length() > 0) {
                                imported += file
                                onProgress("Imported ${file.name}")
                            }
                        }
                    }
                }
            }
            walk(tree, destinationRoot)
        }
        imported
    }

    fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    fun listTree(relative: String = "", limit: Int = 400): List<WorkspaceEntry> {
        val base = if (relative.isBlank()) root else resolve(relative) ?: return emptyList()
        if (!base.exists()) return emptyList()
        val result = mutableListOf<WorkspaceEntry>()
        fun walk(file: File, depth: Int) {
            if (result.size >= limit || depth > 5) return
            val children = file.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) })) ?: return
            for (child in children) {
                if (child.name.startsWith(".")) continue
                result += WorkspaceEntry(
                    name = child.name,
                    relativePath = relative(child),
                    isDirectory = child.isDirectory,
                    sizeBytes = if (child.isFile) child.length() else 0L,
                    childCount = if (child.isDirectory) child.listFiles()?.size ?: 0 else 0,
                )
                if (child.isDirectory) walk(child, depth + 1)
                if (result.size >= limit) break
            }
        }
        walk(base, 0)
        return result
    }

    fun workspaceSize(): Long {
        fun sizeOf(file: File): Long = if (file.isFile) file.length() else file.listFiles()?.sumOf { sizeOf(it) } ?: 0L
        return sizeOf(root)
    }

    fun mediaFiles(): List<File> {
        return (inbox.walkTopDown() + outputs.walkTopDown())
            .filter { it.isFile && !it.name.startsWith(".") }
            .toList()
    }

    fun mimeOf(file: File): String {
        return context.contentResolver.getType(Uri.fromFile(file))
            ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: ""
    }

    fun createExportUriIntent(file: File): Uri? {
        return runCatching {
            val authority = "${context.packageName}.files"
            androidx.core.content.FileProvider.getUriForFile(context, authority, file)
        }.getOrNull()
    }

    fun writeTextFile(relative: String, content: String): File? {
        val file = resolve(relative) ?: return null
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }
}
