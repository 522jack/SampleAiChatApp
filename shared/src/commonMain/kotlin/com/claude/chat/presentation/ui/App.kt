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
import com.claude.chat.presentation.chat.ChatIntent
import com.claude.chat.presentation.chat.ChatScreen
import com.claude.chat.presentation.chat.ChatViewModel
import com.claude.chat.presentation.dataanalysis.DataAnalysisScreen
import com.claude.chat.presentation.dataanalysis.DataAnalysisViewModel
import com.claude.chat.presentation.settings.SettingsScreen
import com.claude.chat.presentation.settings.SettingsViewModel
import com.claude.chat.presentation.support.SupportScreen
import com.claude.chat.presentation.support.SupportViewModel
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

/**
 * Main application composable
 */
@Composable
fun App(
    appContainer: AppContainer = remember { AppContainer() }
) {
    // Initialize logging
    LaunchedEffect(Unit) {
        Napier.base(DebugAntilog())
    }

    // Initialize Ollama configuration and MCP servers
    LaunchedEffect(Unit) {
        try {
            Napier.i("Initializing Ollama configuration and MCP Tools...")

            // Initialize Ollama configuration for optimal performance
            appContainer.initializeOllamaConfiguration()

            // Enable MCP Tools
            appContainer.chatRepository.saveMcpEnabled(true)

            // Initialize built-in MCP tools
            appContainer.chatRepository.initializeMcpTools()

            // Initialize external MCP servers (Weather + Currency)
            appContainer.initializeExternalMcpServers()

            Napier.i("Ollama and MCP orchestration initialized successfully!")
        } catch (e: Exception) {
            Napier.e("Failed to initialize Ollama/MCP orchestration", e)
        }
    }

    val chatViewModel = viewModel {
        ChatViewModel(
            repository = appContainer.chatRepository,
            chatHistoryManager = appContainer.chatHistoryManager,
            messageSendingOrchestrator = appContainer.messageSendingOrchestrator,
            techSpecManager = appContainer.techSpecManager
        )
    }

    val settingsViewModel = viewModel {
        SettingsViewModel(
            repository = appContainer.chatRepository,
            appContainer = appContainer,
            apiConfigManager = appContainer.apiConfigurationManager,
            modelConfigManager = appContainer.modelConfigurationManager,
            ragConfigManager = appContainer.ragConfigurationManager
        )
    }

    val supportViewModel = viewModel {
        SupportViewModel(
            supportApiClient = appContainer.supportApiClient
        )
    }

    val dataAnalysisViewModel = viewModel {
        DataAnalysisViewModel(
            dataAnalysisService = appContainer.dataAnalysisService,
            chatRepository = appContainer.chatRepository
        )
    }

    val chatState by chatViewModel.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()

    var currentScreen by remember { mutableStateOf(Screen.Chat) }
    var chatScreenCounter by remember { mutableStateOf(0) }

    // Determine whether to use dark theme based on theme mode setting
    val isSystemDark = isSystemInDarkTheme()
    val useDarkTheme = when (settingsState.themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemDark // "SYSTEM" or unknown defaults to system theme
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) darkColorScheme() else lightColorScheme()
    ) {
        when (currentScreen) {
            Screen.Chat -> {
                // Reload settings when returning to chat screen
                LaunchedEffect(chatScreenCounter) {
                    chatViewModel.onIntent(ChatIntent.CheckApiKey)
                    chatViewModel.onIntent(ChatIntent.ReloadSettings)
                }

                ChatScreen(
                    state = chatState,
                    onIntent = chatViewModel::onIntent,
                    onNavigateToSettings = { currentScreen = Screen.Settings },
                    onNavigateToDataAnalysis = { currentScreen = Screen.DataAnalysis },
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
                    onNavigateToSupport = { currentScreen = Screen.Support },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Screen.Support -> {
                SupportScreen(
                    viewModel = supportViewModel,
                    onNavigateBack = {
                        currentScreen = Screen.Settings
                    }
                )
            }
            Screen.DataAnalysis -> {
                DataAnalysisScreen(
                    viewModel = dataAnalysisViewModel,
                    onNavigateBack = {
                        currentScreen = Screen.Chat
                    }
                )
            }
        }
    }
}

enum class Screen {
    Chat,
    Settings,
    Support,
    DataAnalysis
}
