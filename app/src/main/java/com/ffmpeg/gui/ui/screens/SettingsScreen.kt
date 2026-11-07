package com.ffmpeg.gui.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmpeg.gui.WorkspaceStats
import com.ffmpeg.gui.data.AiSettings
import com.ffmpeg.gui.data.AppSettings
import com.ffmpeg.gui.ui.components.BrandLogo
import com.ffmpeg.gui.ui.components.ControlCard
import com.ffmpeg.gui.ui.components.InfoRow
import com.ffmpeg.gui.ui.components.PillTabs
import com.ffmpeg.gui.ui.components.ScreenIntro
import com.ffmpeg.gui.ui.formatBytes

private data class ProviderPreset(
    val label: String,
    val baseUrl: String,
    val model: String,
    val note: String,
)

private val providers = listOf(
    ProviderPreset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "OpenAI-compatible"),
    ProviderPreset("OpenAI", "https://api.openai.com/v1", "gpt-4.1-mini", "OpenAI-compatible"),
    ProviderPreset("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4.1-mini", "OpenAI-compatible"),
    ProviderPreset("Ollama", "http://10.0.2.2:11434/v1", "llama3.2", "Local, no key needed"),
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    stats: WorkspaceStats,
    ffmpegVersion: String,
    pythonVersion: String,
    onThemeChange: (String) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onSaveAi: (AiSettings) -> Unit,
    onDemo: () -> Unit,
    onRefresh: () -> Unit,
    onInstallLibrary: () -> Unit,
    onBack: () -> Unit,
) {
    var aiDraft by remember(settings.ai) { mutableStateOf(settings.ai) }
    var keyVisible by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenIntro(
            eyebrow = "Preferences",
            title = "Tune the studio.",
            subtitle = "Theme, copilot connection, and environment details.",
        )

        ControlCard(title = "Appearance", subtitle = "Material 3 Expressive, edge to edge") {
            PillTabs(
                options = listOf("System", "Light", "Dark"),
                selected = when (settings.themeMode) {
                    "light" -> "Light"
                    "dark" -> "Dark"
                    else -> "System"
                },
                onSelect = { onThemeChange(it.lowercase()) },
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Icon(
                        Icons.Rounded.SettingsBrightness,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dynamic colour", style = MaterialTheme.typography.titleSmall)
                    Text("Use wallpaper colours on Android 12+", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.dynamicColor, onCheckedChange = onDynamicColorChange)
            }
        }

        ControlCard(
            title = "AI copilot",
            subtitle = "OpenAI-compatible chat completions with tool calling",
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                providers.forEach { provider ->
                    FilterChip(
                        selected = aiDraft.baseUrl == provider.baseUrl,
                        onClick = { aiDraft = aiDraft.copy(baseUrl = provider.baseUrl, model = provider.model) },
                        label = { Text(provider.label) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                providers.firstOrNull { it.baseUrl == aiDraft.baseUrl }?.note ?: "Custom OpenAI-compatible endpoint",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = aiDraft.baseUrl,
                onValueChange = { aiDraft = aiDraft.copy(baseUrl = it) },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = aiDraft.model,
                onValueChange = { aiDraft = aiDraft.copy(model = it) },
                label = { Text("Model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = aiDraft.apiKey,
                onValueChange = { aiDraft = aiDraft.copy(apiKey = it) },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Toggle key")
                    }
                },
                supportingText = { Text("Stored locally in app preferences. Leave empty for Ollama or local servers.") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text("Temperature · ${"%.1f".format(aiDraft.temperature)}", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = aiDraft.temperature.toFloat(),
                onValueChange = { aiDraft = aiDraft.copy(temperature = it.toDouble().coerceIn(0.0, 1.5)) },
                valueRange = 0f..1.5f,
                steps = 14,
            )
            Text("Tool iterations · ${aiDraft.maxIterations}", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = aiDraft.maxIterations.toFloat(),
                onValueChange = { aiDraft = aiDraft.copy(maxIterations = it.toInt()) },
                valueRange = 2f..12f,
                steps = 9,
            )
            OutlinedTextField(
                value = aiDraft.systemPrompt,
                onValueChange = { aiDraft = aiDraft.copy(systemPrompt = it) },
                label = { Text("System prompt") },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Tools are enabled: the copilot can list/read/write workspace files, probe media, run FFmpeg, and execute Python. Review prompts before sending.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onSaveAi(aiDraft) },
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save copilot settings")
            }
        }

        ControlCard(title = "Environment", subtitle = "Everything stays inside the app sandbox") {
            InfoRow("FFmpeg", ffmpegVersion)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            InfoRow("Python", pythonVersion)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            InfoRow("Media assets", "${stats.mediaCount} items · ${stats.outputCount} generated")
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            InfoRow("Workspace size", formatBytes(stats.totalBytes))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Refresh")
                }
                OutlinedButton(onClick = onDemo, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Demo clip")
                }
                OutlinedButton(onClick = onInstallLibrary, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Library")
                }
            }
        }

        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(18.dp)) {
                Surface(shape = RoundedCornerShape(18.dp), color = androidx.compose.ui.graphics.Color(0xFFF4FFF6)) {
                    BrandLogo(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 18.dp, vertical = 8.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("FFmpeg GUI", style = MaterialTheme.typography.titleLarge)
                Text("Version 1.0.0 · Android 8–17 (API 26–37)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Built with Jetpack Compose and Material 3 Expressive. FFmpeg is powered by FFmpegKit Full-GPL 6.0-2 (x264, x265, libvpx, libopus, LAME). Python is embedded with Chaquopy. The FFmpeg logo is the property of the FFmpeg project.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                InfoRow("License", "GPL v3 (FFmpeg components)")
                InfoRow("Storage", "App-private sandbox")
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onBack) { Text("Back to studio") }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
