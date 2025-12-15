package com.claude.chat.presentation.dataanalysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.chat.data.model.RagDocument
import com.claude.chat.data.repository.ChatRepository
import com.claude.chat.domain.model.DataFile
import com.claude.chat.domain.model.DataFileType
import com.claude.chat.domain.model.Message
import com.claude.chat.domain.model.MessageRole
import com.claude.chat.domain.service.DataAnalysisService
import com.claude.chat.domain.service.data.ParseResult
import com.claude.chat.presentation.dataanalysis.mvi.DataAnalysisIntent
import com.claude.chat.presentation.dataanalysis.mvi.DataAnalysisUiState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ViewModel для экрана анализа данных
 */
@OptIn(ExperimentalUuidApi::class)
class DataAnalysisViewModel(
    private val dataAnalysisService: DataAnalysisService,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DataAnalysisUiState())
    val state: StateFlow<DataAnalysisUiState> = _state.asStateFlow()

    init {
        loadDataFiles()
    }

    fun onIntent(intent: DataAnalysisIntent) {
        when (intent) {
            is DataAnalysisIntent.LoadDataFile -> loadDataFile(intent.fileName, intent.content, intent.fileType)
            is DataAnalysisIntent.RemoveDataFile -> removeDataFile(intent.fileId)
            is DataAnalysisIntent.SelectFile -> selectFile(intent.fileId)
            is DataAnalysisIntent.SendQuestion -> sendQuestion(intent.question)
            is DataAnalysisIntent.ClearChat -> clearChat()
            is DataAnalysisIntent.ReloadFiles -> loadDataFiles()
        }
    }

    // ============================================================================
    // File Management
    // ============================================================================

    private fun loadDataFiles() {
        viewModelScope.launch {
            try {
                val files = dataAnalysisService.getLoadedFiles()
                _state.update { it.copy(loadedFiles = files) }
                Napier.d("Loaded ${files.size} data files")
            } catch (e: Exception) {
                Napier.e("Error loading data files", e)
                _state.update { it.copy(error = "Ошибка загрузки файлов: ${e.message}") }
            }
        }
    }

    private fun loadDataFile(fileName: String, content: String, fileType: DataFileType?) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null) }

                val result = dataAnalysisService.loadAndIndexFile(fileName, content, fileType)

                if (result.isSuccess) {
                    val dataFile = result.getOrThrow()
                    Napier.i("File loaded and indexed: ${dataFile.name}")

                    // Reload files list
                    loadDataFiles()

                    // Select the newly loaded file
                    _state.update {
                        it.copy(
                            isLoading = false,
                            selectedFile = dataFile,
                            error = null
                        )
                    }

                    // Add success message to chat
                    addSystemMessage("Файл «${dataFile.name}» успешно загружен и проиндексирован. Теперь вы можете задавать вопросы по этим данным.")
                } else {
                    val error = result.exceptionOrNull()
                    Napier.e("Error loading file", error)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Ошибка загрузки файла: ${error?.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                Napier.e("Error loading file", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Ошибка: ${e.message}"
                    )
                }
            }
        }
    }

    private fun removeDataFile(fileId: String) {
        viewModelScope.launch {
            try {
                val success = dataAnalysisService.removeFile(fileId)
                if (success) {
                    Napier.i("File removed: $fileId")

                    // Reload files
                    loadDataFiles()

                    // Clear selection if removed file was selected
                    _state.update {
                        if (it.selectedFile?.id == fileId) {
                            it.copy(selectedFile = null)
                        } else {
                            it
                        }
                    }
                }
            } catch (e: Exception) {
                Napier.e("Error removing file", e)
                _state.update { it.copy(error = "Ошибка удаления файла: ${e.message}") }
            }
        }
    }

    private fun selectFile(fileId: String?) {
        val file = if (fileId != null) {
            _state.value.loadedFiles.find { it.id == fileId }
        } else null

        _state.update { it.copy(selectedFile = file) }
    }

    // ============================================================================
    // Chat / Q&A
    // ============================================================================

    private fun sendQuestion(question: String) {
        if (question.isBlank()) return

        viewModelScope.launch {
            try {
                // Add user message
                val userMessage = Message(
                    id = Uuid.random().toString(),
                    content = question,
                    role = MessageRole.USER,
                    timestamp = Clock.System.now()
                )

                _state.update {
                    it.copy(
                        messages = it.messages + userMessage,
                        isProcessing = true,
                        error = null
                    )
                }

                // Build context from selected file or all files
                val contextFiles = if (_state.value.selectedFile != null) {
                    listOf(_state.value.selectedFile!!.id)
                } else {
                    _state.value.loadedFiles.map { it.id }
                }

                if (contextFiles.isEmpty()) {
                    addSystemMessage("Нет загруженных файлов для анализа. Пожалуйста, загрузите файл с данными.")
                    _state.update { it.copy(isProcessing = false) }
                    return@launch
                }

                // Выполняем RAG-поиск релевантных данных
                val queryResult = dataAnalysisService.queryData(
                    query = question,
                    fileIds = contextFiles,
                    topK = 5
                )

                if (queryResult.isFailure) {
                    Napier.e("Failed to query data", queryResult.exceptionOrNull())
                    addSystemMessage("Ошибка поиска данных: ${queryResult.exceptionOrNull()?.message}")
                    _state.update { it.copy(isProcessing = false) }
                    return@launch
                }

                val relevantData = queryResult.getOrThrow()
                Napier.d("Relevant data retrieved, length: ${relevantData.length}")

                // Формируем промпт с релевантными данными
                val dataAnalysisSystemPrompt = buildDataAnalysisPrompt(relevantData)

                // Collect responses
                val responseBuilder = StringBuilder()
                var hasError = false

                chatRepository.sendMessageWithUsage(
                    messages = listOf(userMessage),
                    systemPrompt = dataAnalysisSystemPrompt
                ).collect { chunk ->
                    chunk.text?.let { text ->
                        responseBuilder.append(text)
                    }
                }

                // Add assistant response
                val assistantMessage = Message(
                    id = Uuid.random().toString(),
                    content = responseBuilder.toString(),
                    role = MessageRole.ASSISTANT,
                    timestamp = Clock.System.now(),
                    isError = hasError,
                    isFromRag = true
                )

                _state.update {
                    it.copy(
                        messages = it.messages + assistantMessage,
                        isProcessing = false
                    )
                }

            } catch (e: Exception) {
                Napier.e("Error sending question", e)
                _state.update {
                    it.copy(
                        isProcessing = false,
                        error = "Ошибка отправки вопроса: ${e.message}"
                    )
                }
            }
        }
    }

    private fun clearChat() {
        _state.update { it.copy(messages = emptyList()) }
    }

    private fun addSystemMessage(text: String) {
        val message = Message(
            id = Uuid.random().toString(),
            content = text,
            role = MessageRole.SYSTEM,
            timestamp = Clock.System.now()
        )
        _state.update { it.copy(messages = it.messages + message) }
    }

    private fun buildDataAnalysisPrompt(relevantData: String): String {
        val filesInfo = _state.value.loadedFiles.joinToString("\n") { file ->
            buildString {
                append("- ${file.name} (${file.type.name})")
                file.metadata.rowCount?.let { append(", $it строк") }
                file.metadata.columns?.let { cols ->
                    append(", колонки: ${cols.joinToString(", ")}")
                }
            }
        }

        return """
            Ты - аналитик данных. Твоя задача - анализировать загруженные данные и отвечать на вопросы пользователя.

            Загруженные файлы:
            $filesInfo

            Релевантные данные для ответа на вопрос:
            $relevantData

            Инструкции:
            - ОБЯЗАТЕЛЬНО используй предоставленные данные для ответа
            - Анализируй данные внимательно
            - Давай конкретные ответы с числами и фактами из данных
            - Если нужны расчёты - показывай их
            - Если данных недостаточно - честно скажи об этом
            - Отвечай на русском языке
        """.trimIndent()
    }
}