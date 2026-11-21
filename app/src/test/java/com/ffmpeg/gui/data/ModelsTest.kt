package com.ffmpeg.gui.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun appSettingsRoundTripKeepsAiConfig() {
        val original = AppSettings(
            themeMode = "dark",
            dynamicColor = true,
            ai = AiSettings(
                baseUrl = "https://example.com/v1",
                model = "demo-model",
                apiKey = "test-key",
                temperature = 0.7,
                systemPrompt = "测试提示词",
                maxIterations = 12,
            ),
        )
        val decoded = json.decodeFromString<AppSettings>(json.encodeToString(original))
        assertEquals(original, decoded)
        assertEquals(12, decoded.ai.maxIterations)
        assertEquals("测试提示词", decoded.ai.systemPrompt)
    }

    @Test
    fun oldSettingsWithoutNewFieldsStillLoad() {
        val decoded = json.decodeFromString<AppSettings>("""{"themeMode":"light"}""")
        assertEquals("light", decoded.themeMode)
        assertTrue(decoded.ai.baseUrl.contains("deepseek"))
    }

    @Test
    fun savedScriptDefaultsToAiGenerated() {
        val script = SavedScript(id = "1", name = "demo.py", relativePath = "ai_scripts/demo.py")
        assertTrue(script.generatedByAi)
        assertEquals(0L, script.lastRunAt)
    }

    @Test
    fun mediaItemLabelsAreReadable() {
        val item = MediaItem(id = "1", name = "旅行.MP4", path = "/tmp/旅行.MP4", durationMs = 65_000)
        assertEquals("MP4", item.formatLabel)
        assertEquals("1:05", item.displayDuration)
    }
}
