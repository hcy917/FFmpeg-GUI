package com.ffmpeg.gui.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * The single home for Python scripts saved by the AI (or imported by the user).
 * Scripts live in workspace/ai_scripts and metadata is kept in index.json.
 */
class ScriptRepository(
    private val context: Context,
    private val workspace: Workspace,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val indexFile = File(workspace.aiScriptsDir, "index.json")
    private val _scripts = MutableStateFlow<List<SavedScript>>(emptyList())
    val scripts: StateFlow<List<SavedScript>> = _scripts.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val discovered = workspace.aiScriptsDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("py", true) }
            ?.associateBy { workspace.relative(it) }
            ?: emptyMap()
        val stored = loadIndex().filter { discovered.containsKey(it.relativePath) }
        val known = stored.map { it.relativePath }.toSet()
        val additions = discovered.values
            .filterNot { workspace.relative(it) in known }
            .map { file -> scriptFromFile(file) }
        _scripts.value = (stored + additions).sortedByDescending { it.updatedAt }
        persistIndex()
    }

    fun read(script: SavedScript): String {
        val file = workspace.resolve(script.relativePath) ?: return ""
        return runCatching { file.readText() }.getOrDefault("")
    }

    fun save(
        name: String,
        content: String,
        description: String = "",
        generatedByAi: Boolean = true,
    ): SavedScript {
        val safe = workspace.sanitize(name).let { if (it.endsWith(".py", true)) it else "$it.py" }
        val file = workspace.uniqueFile(workspace.aiScriptsDir, safe)
        file.writeText(content)
        val script = SavedScript(
            id = UUID.randomUUID().toString(),
            name = file.name,
            relativePath = workspace.relative(file),
            description = description,
            generatedByAi = generatedByAi,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        _scripts.value = listOf(script) + _scripts.value
        persistIndex()
        return script
    }

    fun update(scriptId: String, name: String, content: String, description: String): SavedScript? {
        val script = _scripts.value.firstOrNull { it.id == scriptId } ?: return null
        val file = workspace.resolve(script.relativePath) ?: return null
        val safe = workspace.sanitize(name).let { if (it.endsWith(".py", true)) it else "$it.py" }
        var target = file
        if (safe != file.name) {
            target = workspace.uniqueFile(workspace.aiScriptsDir, safe)
            file.renameTo(target)
        }
        target.writeText(content)
        val updated = script.copy(
            name = target.name,
            relativePath = workspace.relative(target),
            description = description,
            updatedAt = System.currentTimeMillis(),
        )
        _scripts.value = _scripts.value.map { if (it.id == scriptId) updated else it }
        persistIndex()
        return updated
    }

    suspend fun importUri(uri: Uri): SavedScript? = withContext(Dispatchers.IO) {
        val file = workspace.importUri(uri, "ai_scripts") ?: return@withContext null
        if (!file.name.endsWith(".py", true)) {
            val renamed = workspace.uniqueFile(workspace.aiScriptsDir, "${file.name}.py")
            if (!file.renameTo(renamed)) return@withContext null
            return@withContext register(renamed, "从设备导入")
        }
        register(file, "从设备导入")
    }

    fun register(file: File, description: String = "从设备导入"): SavedScript {
        val script = scriptFromFile(file).copy(description = description, generatedByAi = false)
        _scripts.value = listOf(script) + _scripts.value.filterNot { it.relativePath == script.relativePath }
        persistIndex()
        return script
    }

    fun delete(scriptId: String): Boolean {
        val script = _scripts.value.firstOrNull { it.id == scriptId } ?: return false
        val file = workspace.resolve(script.relativePath) ?: return false
        val deleted = file.delete()
        if (deleted) {
            _scripts.value = _scripts.value.filterNot { it.id == scriptId }
            persistIndex()
        }
        return deleted
    }

    fun markRun(scriptId: String, result: String, success: Boolean) {
        _scripts.value = _scripts.value.map {
            if (it.id == scriptId) it.copy(
                lastRunAt = System.currentTimeMillis(),
                lastResult = (if (success) "成功 · " else "失败 · ") + result.take(240),
                updatedAt = it.updatedAt,
            ) else it
        }
        persistIndex()
    }

    fun folderPath(): String = workspace.aiScriptsDir.absolutePath

    private fun scriptFromFile(file: File): SavedScript {
        val firstDoc = file.useLines { lines ->
            lines.take(8).firstOrNull { it.startsWith("#") && !it.startsWith("#!") }
        }?.removePrefix("#")?.trim().orEmpty()
        return SavedScript(
            id = UUID.randomUUID().toString(),
            name = file.name,
            relativePath = workspace.relative(file),
            description = firstDoc,
            createdAt = file.lastModified(),
            updatedAt = file.lastModified(),
            generatedByAi = false,
        )
    }

    private fun loadIndex(): List<SavedScript> = runCatching {
        if (!indexFile.isFile) emptyList()
        else json.decodeFromString<List<SavedScript>>(indexFile.readText())
    }.getOrDefault(emptyList())

    private fun persistIndex() {
        runCatching { indexFile.writeText(json.encodeToString(_scripts.value)) }
    }
}
