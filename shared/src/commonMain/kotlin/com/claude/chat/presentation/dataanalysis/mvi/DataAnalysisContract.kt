package com.claude.chat.presentation.dataanalysis.mvi

import com.claude.chat.domain.model.DataFile
import com.claude.chat.domain.model.DataFileType
import com.claude.chat.domain.model.Message

/**
 * UI State для экрана анализа данных
 */
data class DataAnalysisUiState(
    val loadedFiles: List<DataFile> = emptyList(),
    val selectedFile: DataFile? = null,
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null
)

/**
 * User Intents для экрана анализа данных
 */
sealed class DataAnalysisIntent {
    data class LoadDataFile(
        val fileName: String,
        val content: String,
        val fileType: DataFileType? = null
    ) : DataAnalysisIntent()

    data class RemoveDataFile(val fileId: String) : DataAnalysisIntent()
    data class SelectFile(val fileId: String?) : DataAnalysisIntent()
    data class SendQuestion(val question: String) : DataAnalysisIntent()
    data object ClearChat : DataAnalysisIntent()
    data object ReloadFiles : DataAnalysisIntent()
}