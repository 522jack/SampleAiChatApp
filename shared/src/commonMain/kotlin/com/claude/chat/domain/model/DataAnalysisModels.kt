package com.claude.chat.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Тип файла данных
 */
@Serializable
enum class DataFileType {
    CSV,
    JSON,
    LOG
}

/**
 * Метаданные файла данных
 */
@Serializable
data class DataMetadata(
    val rowCount: Int? = null,
    val columnCount: Int? = null,
    val columns: List<String>? = null,
    val sampleRows: List<Map<String, String>>? = null, // первые 5 строк для preview
    val fileSize: Long? = null, // размер файла в байтах
    val encoding: String = "UTF-8"
)

/**
 * Основная модель файла данных
 */
@Serializable
data class DataFile(
    val id: String,
    val name: String,
    val type: DataFileType,
    val contentPath: String, // путь к файлу с контентом (храним отдельно)
    val metadata: DataMetadata,
    val createdAt: Instant,
    val isIndexed: Boolean = false // проиндексирован ли файл в RAG
)

/**
 * Структурированные CSV данные
 */
data class CsvData(
    val headers: List<String>,
    val rows: List<List<String>>,
    val delimiter: Char = ','
) {
    val rowCount: Int get() = rows.size
    val columnCount: Int get() = headers.size

    /**
     * Получить строку по индексу как Map
     */
    fun getRowAsMap(index: Int): Map<String, String> {
        if (index !in rows.indices) return emptyMap()
        return headers.zip(rows[index]).toMap()
    }

    /**
     * Получить все строки как List<Map>
     */
    fun getAllRowsAsMaps(): List<Map<String, String>> {
        return rows.map { row ->
            headers.zip(row).toMap()
        }
    }

    /**
     * Получить колонку по имени
     */
    fun getColumn(columnName: String): List<String>? {
        val index = headers.indexOf(columnName)
        if (index == -1) return null
        return rows.map { it.getOrNull(index) ?: "" }
    }
}

/**
 * Структурированные JSON данные
 */
data class JsonData(
    val data: Any, // может быть Map, List, или примитивный тип
    val isArray: Boolean,
    val itemCount: Int
) {
    /**
     * Попытаться получить данные как список объектов
     */
    @Suppress("UNCHECKED_CAST")
    fun getAsListOfMaps(): List<Map<String, Any>>? {
        return when {
            isArray && data is List<*> -> {
                data.filterIsInstance<Map<String, Any>>()
            }
            data is Map<*, *> -> {
                listOf(data as Map<String, Any>)
            }
            else -> null
        }
    }
}

/**
 * Запись лога
 */
@Serializable
data class LogEntry(
    val timestamp: String?,
    val level: String?, // DEBUG, INFO, WARN, ERROR, etc.
    val tag: String?,
    val message: String,
    val stackTrace: String? = null,
    val lineNumber: Int
)

/**
 * Структурированные данные логов
 */
data class LogData(
    val entries: List<LogEntry>,
    val format: LogFormat
) {
    val entryCount: Int get() = entries.size

    /**
     * Получить записи по уровню логирования
     */
    fun getByLevel(level: String): List<LogEntry> {
        return entries.filter { it.level?.equals(level, ignoreCase = true) == true }
    }

    /**
     * Получить записи с ошибками
     */
    fun getErrors(): List<LogEntry> {
        return entries.filter {
            it.level?.uppercase() in listOf("ERROR", "FATAL", "SEVERE")
        }
    }

    /**
     * Группировать по уровню логирования
     */
    fun groupByLevel(): Map<String, List<LogEntry>> {
        return entries.groupBy { it.level ?: "UNKNOWN" }
    }
}

/**
 * Формат логов
 */
enum class LogFormat {
    KOTLIN_STANDARD,    // timestamp [level] tag: message
    LOGCAT,            // Android logcat format
    SIMPLE,            // простой формат без timestamp
    CUSTOM             // кастомный формат
}

/**
 * Конфигурация для индексации данных в RAG
 */
data class DataIndexConfig(
    val chunkSize: Int = 500,      // размер чанка в символах
    val chunkOverlap: Int = 50,    // перекрытие чанков
    val includeMetadata: Boolean = true, // включать ли метаданные в чанки
    val maxRowsPerChunk: Int = 20  // макс. количество строк данных в одном чанке
)