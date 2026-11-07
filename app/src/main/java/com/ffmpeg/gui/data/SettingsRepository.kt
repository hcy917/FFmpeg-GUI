package com.ffmpeg.gui.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val prefs = context.getSharedPreferences("ffmpeg_gui_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(settings: AppSettings) {
        _settings.value = settings
        runCatching { prefs.edit().putString(KEY, json.encodeToString(settings)).apply() }
    }

    fun updateAi(ai: AiSettings) = update(_settings.value.copy(ai = ai))

    fun updateTheme(themeMode: String, dynamicColor: Boolean = _settings.value.dynamicColor) {
        update(_settings.value.copy(themeMode = themeMode, dynamicColor = dynamicColor))
    }

    private fun load(): AppSettings = runCatching {
        prefs.getString(KEY, null)?.let { json.decodeFromString<AppSettings>(it) } ?: AppSettings()
    }.getOrDefault(AppSettings())

    private companion object {
        const val KEY = "settings_json"
    }
}
