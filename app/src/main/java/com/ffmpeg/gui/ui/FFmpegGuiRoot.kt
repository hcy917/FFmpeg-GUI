package com.ffmpeg.gui.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ffmpeg.gui.MainViewModel
import com.ffmpeg.gui.ui.screens.AiChatScreen
import com.ffmpeg.gui.ui.screens.ScriptsScreen
import com.ffmpeg.gui.ui.screens.SettingsScreen

enum class AppScreen(val label: String, val icon: ImageVector) {
    Ai("AI", Icons.Rounded.AutoAwesome),
    Scripts("已保存脚本", Icons.Rounded.Code),
    Settings("设置", Icons.Rounded.Settings),
}

@Composable
fun FFmpegGuiRoot(viewModel: MainViewModel) {
    var screenName by rememberSaveable { mutableStateOf(AppScreen.Ai.name) }
    val screen = AppScreen.entries.firstOrNull { it.name == screenName } ?: AppScreen.Ai
    val requestedTab by viewModel.requestedTab.collectAsStateWithLifecycle()

    LaunchedEffect(requestedTab) {
        val target = requestedTab ?: return@LaunchedEffect
        screenName = when (target) {
            "scripts" -> AppScreen.Scripts.name
            "settings" -> AppScreen.Settings.name
            else -> AppScreen.Ai.name
        }
        viewModel.consumeTabRequest()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
            ) {
                NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                    AppScreen.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = screen == destination,
                            onClick = { screenName = destination.name },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 20 }) togetherWith
                        (fadeOut() + slideOutHorizontally { -it / 20 })
                },
                label = "main",
            ) { destination ->
                when (destination) {
                    AppScreen.Ai -> AiChatScreen(viewModel)
                    AppScreen.Scripts -> ScriptsScreen(viewModel)
                    AppScreen.Settings -> SettingsScreen(viewModel)
                }
            }
        }
    }
}
