package com.ffmpeg.gui.data

import android.content.Context
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

class MediaRepository(
    private val context: Context,
    private val workspace: Workspace,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val storeFile = File(context.filesDir, "media_library.json")
    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    init {
        _items.value = load().filter { File(it.path).exists() }
    }

    suspend fun addFiles(files: List<File>, isOutput: Boolean = false, source: String = "import"): List<MediaItem> {
        val additions = files.filter { it.isFile }.map { file ->
            val probe = MediaProbeCache.get(file)
            MediaItem(
                id = UUID.randomUUID().toString(),
                name = file.name,
                path = file.absolutePath,
                mime = workspace.mimeOf(file),
                sizeBytes = file.length(),
                durationMs = probe.durationMs,
                width = probe.video?.width ?: 0,
                height = probe.video?.height ?: 0,
                videoCodec = probe.video?.codec ?: "",
                audioCodec = probe.audio?.codec ?: "",
                kind = detectKind(file.extension, probe),
                addedAt = System.currentTimeMillis(),
                isOutput = isOutput,
                source = source,
            )
        }
        if (additions.isEmpty()) return emptyList()
        _items.value = merge(_items.value + additions)
        persist()
        return additions
    }

    suspend fun refresh() {
        val alive = _items.value.filter { File(it.path).exists() }
        if (alive.size != _items.value.size) {
            _items.value = alive
            persist()
        }
    }

    suspend fun remove(ids: Set<String>, deleteFiles: Boolean) {
        val removed = _items.value.filter { it.id in ids }
        _items.value = _items.value.filterNot { it.id in ids }
        if (deleteFiles) {
            withContext(Dispatchers.IO) {
                removed.forEach { item -> runCatching { File(item.path).delete() } }
            }
        }
        persist()
    }

    fun find(id: String): MediaItem? = _items.value.firstOrNull { it.id == id }

    private fun merge(all: List<MediaItem>): List<MediaItem> {
        return all.associateBy { it.path }.values.sortedByDescending { it.addedAt }
    }

    private fun detectKind(extension: String, probe: ProbeInfo): MediaKind {
        if (probe.hasVideo) return MediaKind.VIDEO
        if (probe.hasAudio) return MediaKind.AUDIO
        return when (extension.lowercase()) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "avif" -> MediaKind.IMAGE
            "mp4", "mkv", "mov", "webm", "avi", "m4v", "ts", "3gp" -> MediaKind.VIDEO
            "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus" -> MediaKind.AUDIO
            else -> MediaKind.OTHER
        }
    }

    private fun load(): List<MediaItem> = runCatching {
        if (!storeFile.exists()) emptyList()
        else json.decodeFromString<List<MediaItem>>(storeFile.readText())
    }.getOrDefault(emptyList())

    private fun persist() {
        runCatching { storeFile.writeText(json.encodeToString(_items.value)) }
    }
}

object MediaProbeCache {
    private val cache = mutableMapOf<String, ProbeInfo>()

    suspend fun get(file: File): ProbeInfo {
        val key = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
        cache[key]?.let { return it }
        val probe = com.ffmpeg.gui.engine.MediaProbe.probe(file)
        cache[key] = probe
        if (cache.size > 300) {
            cache.keys.take(50).forEach { cache.remove(it) }
        }
        return probe
    }
}
