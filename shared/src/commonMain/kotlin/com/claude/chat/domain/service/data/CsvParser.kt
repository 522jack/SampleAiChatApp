package com.claude.chat.domain.service.data

import com.claude.chat.domain.model.CsvData
import com.claude.chat.domain.model.DataFileType

/**
 * Парсер для CSV файлов
 */
class CsvParser : DataParser {
    override val supportedType: DataFileType = DataFileType.CSV

    override fun canParse(content: String): Boolean {
        if (content.isBlank()) return false

        // Проверяем наличие разделителей в первых строках
        val firstLines = content.lines().take(5)
        val hasDelimiters = firstLines.any { line ->
            line.contains(',') || line.contains(';') || line.contains('\t')
        }

        return hasDelimiters
    }

    override fun parse(content: String): ParseResult {
        return try {
            val lines = content.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                return ParseResult.Error("CSV файл пустой")
            }

            // Определяем разделитель
            val delimiter = detectDelimiter(lines.first())

            // Парсим заголовки
            val headers = parseLine(lines.first(), delimiter)
            if (headers.isEmpty()) {
                return ParseResult.Error("Не удалось найти заголовки в CSV")
            }

            // Парсим строки данных
            val rows = lines.drop(1).mapNotNull { line ->
                val parsed = parseLine(line, delimiter)
                // Пропускаем пустые строки или строки с неправильным количеством колонок
                if (parsed.isNotEmpty() && parsed.size == headers.size) {
                    parsed
                } else null
            }

            val csvData = CsvData(
                headers = headers,
                rows = rows,
                delimiter = delimiter
            )

            val summary = buildString {
                append("CSV файл успешно загружен:\n")
                append("• Строк: ${csvData.rowCount}\n")
                append("• Колонок: ${csvData.columnCount}\n")
                append("• Заголовки: ${headers.take(5).joinToString(", ")}")
                if (headers.size > 5) append("...")
            }

            ParseResult.Success(
                data = csvData,
                summary = summary
            )
        } catch (e: Exception) {
            ParseResult.Error(
                message = "Ошибка парсинга CSV: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Определить разделитель по первой строке
     */
    private fun detectDelimiter(firstLine: String): Char {
        val delimiters = listOf(',', ';', '\t')

        // Подсчитываем количество вхождений каждого разделителя
        val counts = delimiters.associateWith { delimiter ->
            firstLine.count { it == delimiter }
        }

        // Выбираем разделитель с максимальным количеством вхождений
        return counts.maxByOrNull { it.value }?.key ?: ','
    }

    /**
     * Парсить строку CSV с учетом кавычек
     */
    private fun parseLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val char = line[i]

            when {
                // Начало или конец кавычек
                char == '"' -> {
                    // Проверяем на двойные кавычки (escaped quote)
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++ // пропускаем следующую кавычку
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                // Разделитель (но не внутри кавычек)
                char == delimiter && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                // Обычный символ
                else -> {
                    current.append(char)
                }
            }
            i++
        }

        // Добавляем последнее поле
        result.add(current.toString().trim())

        return result
    }
}