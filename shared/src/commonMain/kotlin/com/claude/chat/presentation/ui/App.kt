package com.claude.chat.presentation.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.claude.chat.di.AppContainer
import com.claude.chat.platform.isSystemInDarkTheme
import com.claude.chat.presentation.chat.ChatViewModel
import com.claude.chat.presentation.settings.SettingsViewModel
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

/**
 * Main application composable
 */
@Composable
fun App(
    appContainer: AppContainer = remember { AppContainer() },
    useDarkTheme: Boolean = isSystemInDarkTheme()
) {
    // Initialize logging
    LaunchedEffect(Unit) {
        Napier.base(DebugAntilog())
    }

    val chatViewModel = viewModel {
        ChatViewModel(appContainer.chatRepository)
    }

    val settingsViewModel = viewModel {
        SettingsViewModel(appContainer.chatRepository)
    }

    val chatState by chatViewModel.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()

    var currentScreen by remember { mutableStateOf(Screen.Chat) }
    var chatScreenCounter by remember { mutableStateOf(0) }

    MaterialTheme(
        colorScheme = if (useDarkTheme) darkColorScheme() else lightColorScheme()
    ) {
        when (currentScreen) {
            Screen.Chat -> {
                // Reload settings when returning to chat screen
                LaunchedEffect(chatScreenCounter) {
                    chatViewModel.onIntent(com.claude.chat.presentation.chat.ChatIntent.CheckApiKey)
                    chatViewModel.onIntent(com.claude.chat.presentation.chat.ChatIntent.ReloadSettings)
                }

                ChatScreen(
                    state = chatState,
                    onIntent = chatViewModel::onIntent,
                    onNavigateToSettings = { currentScreen = Screen.Settings },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Screen.Settings -> {
                SettingsScreen(
                    state = settingsState,
                    onIntent = settingsViewModel::onIntent,
                    onNavigateBack = {
                        currentScreen = Screen.Chat
                        chatScreenCounter++ // Trigger settings reload
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

enum class Screen {
    Chat,
    Settings
}
