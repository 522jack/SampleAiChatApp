package com.claude.chat.domain.service.data

import com.claude.chat.domain.model.CsvData
import com.claude.chat.domain.model.DataFileType
import com.claude.chat.domain.model.JsonData
import com.claude.chat.domain.model.LogData

/**
 * Интерфейс для парсинга файлов данных
 */
interface DataParser {
    /**
     * Тип данных, который парсит этот парсер
     */
    val supportedType: DataFileType

    /**
     * Проверить, может ли парсер обработать этот контент
     */
    fun canParse(content: String): Boolean

    /**
     * Парсить контент в структурированный формат
     */
    fun parse(content: String): ParseResult
}

/**
 * Результат парсинга
 */
sealed class ParseResult {
    data class Success(
        val data: Any, // CsvData, JsonData, или LogData
        val summary: String // краткое описание данных для пользователя
    ) : ParseResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : ParseResult()
}

/**
 * Фабрика для создания парсеров
 */
object DataParserFactory {
    private val parsers = mutableListOf<DataParser>()

    init {
        // Регистрируем все парсеры
        register(CsvParser())
        register(JsonParser())
        register(LogParser())
    }

    fun register(parser: DataParser) {
        parsers.add(parser)
    }

    /**
     * Получить парсер по типу файла
     */
    fun getParser(type: DataFileType): DataParser? {
        return parsers.firstOrNull { it.supportedType == type }
    }

    /**
     * Автоматически определить парсер по контенту
     */
    fun detectParser(content: String): DataParser? {
        return parsers.firstOrNull { it.canParse(content) }
    }
}