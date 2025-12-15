package com.claude.chat.domain.service.data

import com.claude.chat.domain.model.DataFileType
import com.claude.chat.domain.model.LogData
import com.claude.chat.domain.model.LogEntry
import com.claude.chat.domain.model.LogFormat

/**
 * Парсер для файлов логов
 * Поддерживает форматы:
 * - Kotlin standard: 2024-01-15 10:30:45.123 [ERROR] MyTag: Error message
 * - Android logcat: 2024-01-15 10:30:45.123 E/MyTag: Error message
 * - Simple: [ERROR] MyTag: Error message
 * - Basic: ERROR: Error message
 */
class LogParser : DataParser {
    override val supportedType: DataFileType = DataFileType.LOG

    // Регулярные выражения для разных форматов логов
    private val kotlinStandardRegex =
        Regex("""^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d{3})?)\s+\[(\w+)\]\s+(\w+):\s+(.+)$""")

    private val logcatRegex =
        Regex("""^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d{3})?)\s+([A-Z])/(\w+):\s+(.+)$""")

    private val simpleRegex =
        Regex("""^\[(\w+)\]\s+(\w+):\s+(.+)$""")

    private val basicRegex =
        Regex("""^(\w+):\s+(.+)$""")

    override fun canParse(content: String): Boolean {
        if (content.isBlank()) return false

        val lines = content.lines().take(10).filter { it.isNotBlank() }

        // Проверяем, что хотя бы несколько строк соответствуют формату логов
        val matchCount = lines.count { line ->
            kotlinStandardRegex.matches(line) ||
            logcatRegex.matches(line) ||
            simpleRegex.matches(line) ||
            basicRegex.matches(line) ||
            line.trim().startsWith("at ") || // stacktrace
            line.trim().startsWith("\tat ") // stacktrace с табуляцией
        }

        return matchCount >= lines.size / 2 // хотя бы половина строк должна быть логами
    }

    override fun parse(content: String): ParseResult {
        return try {
            val lines = content.lines()
            val entries = mutableListOf<LogEntry>()
            var currentEntry: LogEntry? = null
            var currentStackTrace = StringBuilder()
            var detectedFormat: LogFormat? = null

            lines.forEachIndexed { index, line ->
                if (line.isBlank()) return@forEachIndexed

                // Проверяем, является ли строка частью stacktrace
                if (line.trim().startsWith("at ") || line.trim().startsWith("\tat ")) {
                    currentStackTrace.appendLine(line.trim())
                    return@forEachIndexed
                }

                // Если есть накопленный stacktrace, добавляем его к предыдущей записи
                if (currentStackTrace.isNotEmpty() && currentEntry != null) {
                    val entryWithStackTrace = currentEntry.copy(
                        stackTrace = currentStackTrace.toString().trim()
                    )
                    entries.removeLastOrNull()
                    entries.add(entryWithStackTrace)
                    currentStackTrace = StringBuilder()
                }

                // Пытаемся распарсить новую запись лога
                val parsedEntry = parseLogLine(line, index + 1)
                if (parsedEntry != null) {
                    if (detectedFormat == null) {
                        detectedFormat = detectFormatFromLine(line)
                    }
                    currentEntry = parsedEntry
                    entries.add(parsedEntry)
                } else {
                    // Если не удалось распарсить, возможно это продолжение предыдущего сообщения
                    if (currentEntry != null) {
                        val updatedEntry = currentEntry.copy(
                            message = currentEntry.message + "\n" + line
                        )
                        entries.removeLastOrNull()
                        entries.add(updatedEntry)
                        currentEntry = updatedEntry
                    }
                }
            }

            // Обрабатываем оставшийся stacktrace
            if (currentStackTrace.isNotEmpty() && currentEntry != null) {
                val entryWithStackTrace = currentEntry.copy(
                    stackTrace = currentStackTrace.toString().trim()
                )
                entries.removeLastOrNull()
                entries.add(entryWithStackTrace)
            }

            if (entries.isEmpty()) {
                return ParseResult.Error("Не удалось распарсить ни одной записи лога")
            }

            val logData = LogData(
                entries = entries,
                format = detectedFormat ?: LogFormat.CUSTOM
            )

            val summary = buildSummary(logData)

            ParseResult.Success(
                data = logData,
                summary = summary
            )
        } catch (e: Exception) {
            ParseResult.Error(
                message = "Ошибка парсинга логов: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Распарсить одну строку лога
     */
    private fun parseLogLine(line: String, lineNumber: Int): LogEntry? {
        // Пробуем Kotlin standard format
        kotlinStandardRegex.matchEntire(line)?.let { match ->
            val (timestamp, level, tag, message) = match.destructured
            return LogEntry(
                timestamp = timestamp,
                level = level,
                tag = tag,
                message = message,
                lineNumber = lineNumber
            )
        }

        // Пробуем Android logcat format
        logcatRegex.matchEntire(line)?.let { match ->
            val (timestamp, levelChar, tag, message) = match.destructured
            return LogEntry(
                timestamp = timestamp,
                level = expandLogcatLevel(levelChar),
                tag = tag,
                message = message,
                lineNumber = lineNumber
            )
        }

        // Пробуем simple format
        simpleRegex.matchEntire(line)?.let { match ->
            val (level, tag, message) = match.destructured
            return LogEntry(
                timestamp = null,
                level = level,
                tag = tag,
                message = message,
                lineNumber = lineNumber
            )
        }

        // Пробуем basic format
        basicRegex.matchEntire(line)?.let { match ->
            val (level, message) = match.destructured
            return LogEntry(
                timestamp = null,
                level = level,
                tag = null,
                message = message,
                lineNumber = lineNumber
            )
        }

        return null
    }

    /**
     * Определить формат лога по строке
     */
    private fun detectFormatFromLine(line: String): LogFormat {
        return when {
            kotlinStandardRegex.matches(line) -> LogFormat.KOTLIN_STANDARD
            logcatRegex.matches(line) -> LogFormat.LOGCAT
            simpleRegex.matches(line) -> LogFormat.SIMPLE
            else -> LogFormat.CUSTOM
        }
    }

    /**
     * Расшифровать символ уровня логирования Android logcat
     */
    private fun expandLogcatLevel(levelChar: String): String {
        return when (levelChar) {
            "V" -> "VERBOSE"
            "D" -> "DEBUG"
            "I" -> "INFO"
            "W" -> "WARN"
            "E" -> "ERROR"
            "F" -> "FATAL"
            else -> levelChar
        }
    }

    /**
     * Построить summary описание данных логов
     */
    private fun buildSummary(logData: LogData): String {
        val levelCounts = logData.groupByLevel()
        val errorCount = logData.getErrors().size

        return buildString {
            append("Файл логов успешно загружен:\n")
            append("• Записей: ${logData.entryCount}\n")
            append("• Формат: ${logData.format.name}\n")
            append("• Ошибок: $errorCount\n")

            if (levelCounts.isNotEmpty()) {
                append("• Распределение по уровням:\n")
                levelCounts.entries
                    .sortedByDescending { it.value.size }
                    .take(5)
                    .forEach { (level, entries) ->
                        append("  - $level: ${entries.size}\n")
                    }
            }
        }
    }
}