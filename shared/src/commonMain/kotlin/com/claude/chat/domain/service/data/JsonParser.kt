package com.claude.chat.domain.service.data

import com.claude.chat.domain.model.DataFileType
import com.claude.chat.domain.model.JsonData
import kotlinx.serialization.json.*

/**
 * Парсер для JSON файлов
 */
class JsonParser : DataParser {
    override val supportedType: DataFileType = DataFileType.JSON

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun canParse(content: String): Boolean {
        if (content.isBlank()) return false

        val trimmed = content.trim()
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]"))
    }

    override fun parse(content: String): ParseResult {
        return try {
            val jsonElement = json.parseToJsonElement(content)

            val (data, isArray, itemCount) = when (jsonElement) {
                is JsonArray -> {
                    Triple(
                        jsonElement.toList(),
                        true,
                        jsonElement.size
                    )
                }
                is JsonObject -> {
                    Triple(
                        jsonElement.toMap(),
                        false,
                        1
                    )
                }
                else -> {
                    return ParseResult.Error("JSON содержит примитивное значение вместо объекта или массива")
                }
            }

            val jsonData = JsonData(
                data = data,
                isArray = isArray,
                itemCount = itemCount
            )

            val summary = buildSummary(jsonData, jsonElement)

            ParseResult.Success(
                data = jsonData,
                summary = summary
            )
        } catch (e: Exception) {
            ParseResult.Error(
                message = "Ошибка парсинга JSON: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Построить summary описание JSON данных
     */
    private fun buildSummary(jsonData: JsonData, jsonElement: JsonElement): String {
        return buildString {
            append("JSON файл успешно загружен:\n")

            when {
                jsonData.isArray -> {
                    append("• Тип: Массив\n")
                    append("• Элементов: ${jsonData.itemCount}\n")

                    // Попытка определить структуру элементов массива
                    if (jsonElement is JsonArray && jsonElement.isNotEmpty()) {
                        val firstElement = jsonElement.first()
                        if (firstElement is JsonObject) {
                            val keys = firstElement.keys.take(5).joinToString(", ")
                            append("• Поля объектов: $keys")
                            if (firstElement.keys.size > 5) append("...")
                        } else {
                            append("• Тип элементов: ${getJsonElementType(firstElement)}")
                        }
                    }
                }
                else -> {
                    append("• Тип: Объект\n")
                    if (jsonElement is JsonObject) {
                        append("• Полей: ${jsonElement.keys.size}\n")
                        val keys = jsonElement.keys.take(5).joinToString(", ")
                        append("• Ключи: $keys")
                        if (jsonElement.keys.size > 5) append("...")
                    }
                }
            }
        }
    }

    /**
     * Получить тип JsonElement
     */
    private fun getJsonElementType(element: JsonElement): String {
        return when (element) {
            is JsonObject -> "Объект"
            is JsonArray -> "Массив"
            is JsonPrimitive -> {
                when {
                    element.isString -> "Строка"
                    element.booleanOrNull != null -> "Boolean"
                    element.intOrNull != null -> "Число (Int)"
                    element.longOrNull != null -> "Число (Long)"
                    element.doubleOrNull != null -> "Число (Double)"
                    else -> "Примитив"
                }
            }
            else -> "Неизвестный"
        }
    }

    /**
     * Конвертировать JsonArray в List<Any>
     */
    private fun JsonArray.toList(): List<Any> {
        return this.map { element ->
            when (element) {
                is JsonObject -> element.toMap()
                is JsonArray -> element.toList()
                is JsonPrimitive -> element.toPrimitive()
                else -> element.toString()
            }
        }
    }

    /**
     * Конвертировать JsonObject в Map<String, Any>
     */
    private fun JsonObject.toMap(): Map<String, Any> {
        return this.mapValues { (_, value) ->
            when (value) {
                is JsonObject -> value.toMap()
                is JsonArray -> value.toList()
                is JsonPrimitive -> value.toPrimitive()
                else -> value.toString()
            }
        }
    }

    /**
     * Конвертировать JsonPrimitive в примитивный тип Kotlin
     */
    private fun JsonPrimitive.toPrimitive(): Any {
        return when {
            this.isString -> this.content
            this.booleanOrNull != null -> this.booleanOrNull!!
            this.intOrNull != null -> this.intOrNull!!
            this.longOrNull != null -> this.longOrNull!!
            this.doubleOrNull != null -> this.doubleOrNull!!
            else -> this.content
        }
    }
}