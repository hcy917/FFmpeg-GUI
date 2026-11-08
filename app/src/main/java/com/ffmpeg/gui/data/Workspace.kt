package com.ffmpeg.gui.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class Workspace(
    private val context: Context,
    storage: StorageSettings = StorageSettings(),
) {
    @Volatile
    private var rootDir: File = initialRoot(context, storage)

    val root: File get() = rootDir
    val inbox: File get() = ensureDir(File(root, "inbox"))
    val outputs: File get() = ensureDir(File(root, "outputs"))
    val pythonDir: File get() = ensureDir(File(root, "python"))
    val libsDir: File get() = ensureDir(File(pythonDir, "libs"))
    val tempDir: File get() = ensureDir(File(context.cacheDir, "work"))

    init {
        ensureLayout()
    }

    private fun ensureLayout() {
        ensureDir(root)
        ensureDir(File(root, "inbox"))
        ensureDir(File(root, "outputs"))
        ensureDir(File(root, "python"))
        ensureDir(File(root, "python/libs"))
        ensureDir(File(root, "python/examples"))
        ensureDir(File(root, "sandbox"))
        ensureDir(File(root, "chat"))
    }

    private fun ensureDir(dir: File): File {
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isWritableDirectory(dir: File): Boolean {
        return runCatching {
            if (!dir.exists()) dir.mkdirs()
            if (!dir.isDirectory) return false
            val probe = File(dir, ".ffmpeg_gui_write_test")
            probe.writeText("ok")
            probe.delete()
            true
        }.getOrDefault(false)
    }

    /**
     * Switch the whole workspace to another filesystem directory.
     * Existing files are merged into the new directory; the old directory is kept as a backup.
     */
    suspend fun switchRoot(target: File, onProgress: (String) -> Unit = {}): Result<File> = withContext(Dispatchers.IO) {
        val old = root
        runCatching {
            val oldCanonical = old.canonicalFile
            val newCanonical = target.canonicalFile
            if (oldCanonical == newCanonical) return@runCatching old
            if (newCanonical.path.startsWith(oldCanonical.path + File.separator) ||
                oldCanonical.path.startsWith(newCanonical.path + File.separator)
            ) {
                error("新目录不能位于当前工作区内部，也不能包含当前工作区")
            }
            if (!isWritableDirectory(newCanonical)) {
                error("目标目录不可写。请检查存储权限或换一个目录")
            }
            onProgress("正在复制工作区文件…")
            var copied = 0
            fun copyTree(source: File, destination: File) {
                if (source.isFile) {
                    if (!destination.exists()) {
                        destination.parentFile?.mkdirs()
                        source.copyTo(destination, overwrite = false)
                        copied++
                        if (copied % 25 == 0) onProgress("已复制 $copied 个文件…")
                    } else if (source.length() != destination.length()) {
                        val alt = uniqueFile(destination.parentFile ?: destination, destination.name)
                        source.copyTo(alt, overwrite = false)
                        copied++
                    }
                    return
                }
                destination.mkdirs()
                source.listFiles()?.forEach { child ->
                    copyTree(child, File(destination, child.name))
                }
            }
            copyTree(old, newCanonical)
            rootDir = newCanonical
            ensureLayout()
            onProgress("已切换并复制 $copied 个文件")
            old
        }
    }

    companion object {
        fun internalRoot(context: Context): File = File(context.filesDir, "workspace")

        fun externalRoot(context: Context): File =
            File(context.getExternalFilesDir(null) ?: File(context.filesDir, "external"), "FFmpegGUI/workspace")

        fun rootFor(context: Context, storage: StorageSettings): File {
            return when (storage.mode) {
                StorageMode.INTERNAL -> internalRoot(context)
                StorageMode.EXTERNAL -> externalRoot(context)
                StorageMode.CUSTOM -> storage.customPath.trim().takeIf { it.isNotBlank() }?.let { File(it) }
                    ?: internalRoot(context)
            }
        }

        private fun initialRoot(context: Context, storage: StorageSettings): File {
            val candidate = rootFor(context, storage)
            val fallback = internalRoot(context)
            return if (candidate == fallback || runCatching {
                    candidate.mkdirs()
                    File(candidate, ".write_test").apply { writeText("ok"); delete() }
                    true
                }.getOrDefault(false)
            ) candidate else fallback
        }
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
                        onProgress("正在扫描 $childName…")
                        walk(child, dir)
                    } else if (child.isFile) {
                        val file = uniqueFile(target, childName)
                        runCatching {
                            context.contentResolver.openInputStream(child.uri)?.use { input ->
                                file.outputStream().use { output -> input.copyTo(output, 1 shl 20) }
                            }
                            if (file.exists() && file.length() > 0) {
                                imported += file
                                onProgress("已导入 ${file.name}")
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

object StoragePaths {
    /** Convert a SAF tree URI from the built-in external storage provider into a real filesystem path. */
    fun treeUriToPath(context: Context, uri: Uri): String? {
        return runCatching {
            if (uri.authority != "com.android.externalstorage.documents") return null
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            val parts = documentId.split(":", limit = 2)
            if (parts.size != 2) return null
            val volume = parts[0]
            val relative = parts[1].trim('/')
            val base = when {
                volume.equals("primary", ignoreCase = true) -> Environment.getExternalStorageDirectory()
                else -> File("/storage", volume)
            }
            val target = if (relative.isBlank()) base else File(base, relative)
            target.absolutePath
        }.getOrNull()
    }

    fun isInsideAppStorage(context: Context, path: String): Boolean {
        val candidate = runCatching { File(path).canonicalPath }.getOrNull() ?: return false
        val roots = listOfNotNull(
            context.filesDir,
            context.cacheDir,
            context.getExternalFilesDir(null),
            context.getExternalCacheDir(),
        ).mapNotNull { runCatching { it.canonicalPath }.getOrNull() }
        return roots.any { candidate == it || candidate.startsWith(it + File.separator) }
    }
}
