package com.claude.chat.domain.service

import com.claude.chat.domain.model.*
import com.claude.chat.domain.service.data.DataParserFactory
import com.claude.chat.domain.service.data.ParseResult
import com.claude.chat.platform.FileStorage
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Сервис для анализа данных через локальную LLM
 */
@OptIn(ExperimentalUuidApi::class)
class DataAnalysisService(
    private val ragService: RagService,
    private val fileStorage: FileStorage
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val loadedFiles = mutableListOf<DataFile>()

    companion object {
        private const val DATA_FILES_INDEX_NAME = "data_files_index.json"
        private const val DATA_CONTENT_PREFIX = "data_content_"
    }

    /**
     * Загрузить и проиндексировать файл данных
     */
    suspend fun loadAndIndexFile(
        fileName: String,
        content: String,
        fileType: DataFileType? = null
    ): Result<DataFile> {
        return try {
            Napier.d("Loading file: $fileName")

            // Определяем парсер
            val parser = if (fileType != null) {
                DataParserFactory.getParser(fileType)
            } else {
                DataParserFactory.detectParser(content)
            }

            if (parser == null) {
                return Result.failure(Exception("Не удалось определить формат файла"))
            }

            Napier.d("Using parser: ${parser.supportedType}")

            // Парсим файл
            val parseResult = parser.parse(content)
            if (parseResult is ParseResult.Error) {
                return Result.failure(
                    parseResult.cause ?: Exception(parseResult.message)
                )
            }

            val successResult = parseResult as ParseResult.Success
            val parsedData = successResult.data

            // Создаём метаданные
            val metadata = createMetadata(parsedData, parser.supportedType)

            // Создаём DataFile
            val dataFile = DataFile(
                id = Uuid.random().toString(),
                name = fileName,
                type = parser.supportedType,
                contentPath = "$DATA_CONTENT_PREFIX${Uuid.random()}.txt",
                metadata = metadata,
                createdAt = Clock.System.now(),
                isIndexed = false
            )

            // Сохраняем контент файла
            fileStorage.writeTextFile(dataFile.contentPath, content)

            // Индексируем в RAG
            val indexResult = indexDataInRag(dataFile, content, parsedData)
            if (indexResult.isFailure) {
                return Result.failure(
                    indexResult.exceptionOrNull() ?: Exception("Ошибка индексации")
                )
            }

            // Обновляем статус индексации
            val indexedFile = dataFile.copy(isIndexed = true)
            loadedFiles.add(indexedFile)

            // Сохраняем индекс файлов
            saveFilesIndex()

            Napier.d("File loaded and indexed successfully: ${dataFile.name}")
            Result.success(indexedFile)

        } catch (e: Exception) {
            Napier.e("Error loading file", e)
            Result.failure(e)
        }
    }

    /**
     * Индексировать данные в RAG
     */
    private suspend fun indexDataInRag(
        dataFile: DataFile,
        content: String,
        parsedData: Any
    ): Result<Unit> {
        return try {
            // Создаём chunки в зависимости от типа данных
            val chunks = when (dataFile.type) {
                DataFileType.CSV -> createCsvChunks(parsedData as CsvData, dataFile)
                DataFileType.JSON -> createJsonChunks(parsedData as JsonData, dataFile)
                DataFileType.LOG -> createLogChunks(parsedData as LogData, dataFile)
            }

            Napier.d("Created ${chunks.size} chunks for indexing")

            // Индексируем каждый чанк как отдельный документ
            chunks.forEach { chunk ->
                val indexResult = ragService.indexDocument(
                    title = "${dataFile.name} - ${chunk.title}",
                    content = chunk.content,
                    metadata = mapOf(
                        "dataFileId" to dataFile.id,
                        "dataFileName" to dataFile.name,
                        "dataFileType" to dataFile.type.name,
                        "chunkType" to chunk.type
                    ) + chunk.metadata
                )

                if (indexResult.isFailure) {
                    Napier.w("Failed to index chunk: ${chunk.title}")
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error indexing data in RAG", e)
            Result.failure(e)
        }
    }

    /**
     * Создать chunки для CSV данных
     */
    private fun createCsvChunks(
        csvData: CsvData,
        dataFile: DataFile,
        rowsPerChunk: Int = 20
    ): List<DataChunk> {
        val chunks = mutableListOf<DataChunk>()

        // Chunk 1: Сводная информация
        val summaryContent = buildString {
            appendLine("CSV файл: ${dataFile.name}")
            appendLine("Строк: ${csvData.rowCount}")
            appendLine("Колонок: ${csvData.columnCount}")
            appendLine("Заголовки: ${csvData.headers.joinToString(", ")}")
            appendLine()
            appendLine("Первые 3 строки:")
            csvData.getAllRowsAsMaps().take(3).forEach { row ->
                appendLine(row.entries.joinToString(" | ") { "${it.key}: ${it.value}" })
            }
        }

        chunks.add(
            DataChunk(
                title = "Сводка",
                content = summaryContent,
                type = "summary",
                metadata = mapOf(
                    "totalRows" to csvData.rowCount.toString(),
                    "columns" to csvData.headers.joinToString(",")
                )
            )
        )

        // Chunks 2+: Группы строк
        csvData.getAllRowsAsMaps().chunked(rowsPerChunk).forEachIndexed { index, rowGroup ->
            val chunkContent = buildString {
                appendLine("Строки ${index * rowsPerChunk + 1} - ${index * rowsPerChunk + rowGroup.size}:")
                appendLine()
                rowGroup.forEach { row ->
                    appendLine(row.entries.joinToString(" | ") { "${it.key}: ${it.value}" })
                }
            }

            chunks.add(
                DataChunk(
                    title = "Chunk ${index + 1}",
                    content = chunkContent,
                    type = "data",
                    metadata = mapOf(
                        "startRow" to (index * rowsPerChunk + 1).toString(),
                        "endRow" to (index * rowsPerChunk + rowGroup.size).toString()
                    )
                )
            )
        }

        return chunks
    }

    /**
     * Создать chunки для JSON данных
     */
    private fun createJsonChunks(
        jsonData: JsonData,
        dataFile: DataFile
    ): List<DataChunk> {
        val chunks = mutableListOf<DataChunk>()

        // Chunk 1: Сводная информация
        val summaryContent = buildString {
            appendLine("JSON файл: ${dataFile.name}")
            appendLine("Тип: ${if (jsonData.isArray) "Массив" else "Объект"}")
            appendLine("Элементов: ${jsonData.itemCount}")

            // Попробуем получить структуру
            val asMaps = jsonData.getAsListOfMaps()
            if (asMaps != null && asMaps.isNotEmpty()) {
                appendLine("Ключи первого объекта: ${asMaps.first().keys.joinToString(", ")}")
                appendLine()
                appendLine("Первый элемент:")
                appendLine(formatMapAsText(asMaps.first()))
            }
        }

        chunks.add(
            DataChunk(
                title = "Сводка",
                content = summaryContent,
                type = "summary",
                metadata = mapOf(
                    "isArray" to jsonData.isArray.toString(),
                    "itemCount" to jsonData.itemCount.toString()
                )
            )
        )

        // Chunks 2+: Элементы данных (по 10 элементов на chunk)
        val asMaps = jsonData.getAsListOfMaps()
        if (asMaps != null) {
            asMaps.chunked(10).forEachIndexed { index, mapGroup ->
                val chunkContent = buildString {
                    appendLine("Элементы ${index * 10 + 1} - ${index * 10 + mapGroup.size}:")
                    appendLine()
                    mapGroup.forEachIndexed { i, map ->
                        appendLine("--- Элемент ${index * 10 + i + 1} ---")
                        appendLine(formatMapAsText(map))
                        appendLine()
                    }
                }

                chunks.add(
                    DataChunk(
                        title = "Chunk ${index + 1}",
                        content = chunkContent,
                        type = "data",
                        metadata = emptyMap()
                    )
                )
            }
        }

        return chunks
    }

    /**
     * Создать chunки для логов
     */
    private fun createLogChunks(
        logData: LogData,
        dataFile: DataFile,
        entriesPerChunk: Int = 50
    ): List<DataChunk> {
        val chunks = mutableListOf<DataChunk>()

        // Chunk 1: Сводная информация
        val levelCounts = logData.groupByLevel()
        val errorEntries = logData.getErrors()

        val summaryContent = buildString {
            appendLine("Файл логов: ${dataFile.name}")
            appendLine("Записей: ${logData.entryCount}")
            appendLine("Формат: ${logData.format.name}")
            appendLine()
            appendLine("Распределение по уровням:")
            levelCounts.forEach { (level, entries) ->
                appendLine("  $level: ${entries.size}")
            }
            appendLine()
            if (errorEntries.isNotEmpty()) {
                appendLine("Ошибки (первые 5):")
                errorEntries.take(5).forEach { entry ->
                    appendLine("  [${entry.level}] ${entry.tag ?: "NO_TAG"}: ${entry.message.take(100)}")
                }
            }
        }

        chunks.add(
            DataChunk(
                title = "Сводка",
                content = summaryContent,
                type = "summary",
                metadata = mapOf(
                    "totalEntries" to logData.entryCount.toString(),
                    "errorCount" to errorEntries.size.toString()
                )
            )
        )

        // Chunk 2: Все ошибки отдельно (для быстрого поиска)
        if (errorEntries.isNotEmpty()) {
            val errorsContent = buildString {
                appendLine("Все ошибки в логах:")
                appendLine()
                errorEntries.forEach { entry ->
                    appendLine("Строка ${entry.lineNumber}: [${entry.level}] ${entry.tag ?: "NO_TAG"}")
                    appendLine("  ${entry.message}")
                    if (entry.stackTrace != null) {
                        appendLine("  Stacktrace:")
                        entry.stackTrace.lines().take(5).forEach { line ->
                            appendLine("    $line")
                        }
                    }
                    appendLine()
                }
            }

            chunks.add(
                DataChunk(
                    title = "Ошибки",
                    content = errorsContent,
                    type = "errors",
                    metadata = mapOf("errorCount" to errorEntries.size.toString())
                )
            )
        }

        // Chunks 3+: Группы записей
        logData.entries.chunked(entriesPerChunk).forEachIndexed { index, entryGroup ->
            val chunkContent = buildString {
                appendLine("Записи ${index * entriesPerChunk + 1} - ${index * entriesPerChunk + entryGroup.size}:")
                appendLine()
                entryGroup.forEach { entry ->
                    val timestamp = entry.timestamp?.let { "[$it] " } ?: ""
                    appendLine("$timestamp[${entry.level}] ${entry.tag ?: ""}: ${entry.message}")
                }
            }

            chunks.add(
                DataChunk(
                    title = "Chunk ${index + 1}",
                    content = chunkContent,
                    type = "data",
                    metadata = emptyMap()
                )
            )
        }

        return chunks
    }

    /**
     * Получить все загруженные файлы
     */
    fun getLoadedFiles(): List<DataFile> = loadedFiles.toList()

    /**
     * Удалить файл
     */
    suspend fun removeFile(fileId: String): Boolean {
        val file = loadedFiles.find { it.id == fileId } ?: return false

        // Удаляем из RAG (удаляем все документы, связанные с этим файлом)
        ragService.getIndexedDocuments()
            .filter { it.metadata["dataFileId"] == fileId }
            .forEach { ragService.removeDocument(it.id) }

        // Удаляем контент файла
        fileStorage.deleteFile(file.contentPath)

        // Удаляем из списка
        loadedFiles.removeAll { it.id == fileId }

        // Сохраняем индекс
        saveFilesIndex()

        Napier.d("File removed: ${file.name}")
        return true
    }

    /**
     * Сохранить индекс файлов
     */
    suspend fun saveFilesIndex(): Boolean {
        return try {
            val index = DataFilesIndex(files = loadedFiles)
            val jsonString = json.encodeToString(index)
            fileStorage.writeTextFile(DATA_FILES_INDEX_NAME, jsonString)
            Napier.d("Files index saved")
            true
        } catch (e: Exception) {
            Napier.e("Error saving files index", e)
            false
        }
    }

    /**
     * Загрузить индекс файлов
     */
    suspend fun loadFilesIndex(): Boolean {
        return try {
            val jsonString = fileStorage.readTextFile(DATA_FILES_INDEX_NAME) ?: return false
            val index = json.decodeFromString<DataFilesIndex>(jsonString)
            loadedFiles.clear()
            loadedFiles.addAll(index.files)
            Napier.d("Files index loaded: ${loadedFiles.size} files")
            true
        } catch (e: Exception) {
            Napier.e("Error loading files index", e)
            false
        }
    }

    /**
     * Создать метаданные для файла
     */
    private fun createMetadata(parsedData: Any, type: DataFileType): DataMetadata {
        return when (type) {
            DataFileType.CSV -> {
                val csvData = parsedData as CsvData
                DataMetadata(
                    rowCount = csvData.rowCount,
                    columnCount = csvData.columnCount,
                    columns = csvData.headers,
                    sampleRows = csvData.getAllRowsAsMaps().take(5)
                )
            }
            DataFileType.JSON -> {
                val jsonData = parsedData as JsonData
                val asMaps = jsonData.getAsListOfMaps()
                DataMetadata(
                    rowCount = jsonData.itemCount,
                    columnCount = asMaps?.firstOrNull()?.size,
                    columns = asMaps?.firstOrNull()?.keys?.toList(),
                    sampleRows = asMaps?.take(5)?.map { map ->
                        map.mapValues { it.value.toString() }
                    }
                )
            }
            DataFileType.LOG -> {
                val logData = parsedData as LogData
                DataMetadata(
                    rowCount = logData.entryCount,
                    columns = listOf("timestamp", "level", "tag", "message"),
                    sampleRows = logData.entries.take(5).map { entry ->
                        mapOf(
                            "timestamp" to (entry.timestamp ?: ""),
                            "level" to (entry.level ?: ""),
                            "tag" to (entry.tag ?: ""),
                            "message" to entry.message
                        )
                    }
                )
            }
        }
    }

    /**
     * Форматировать Map как текст
     */
    private fun formatMapAsText(map: Map<String, Any>, indent: String = ""): String {
        return map.entries.joinToString("\n") { (key, value) ->
            when (value) {
                is Map<*, *> -> {
                    "$indent$key:\n${formatMapAsText(value as Map<String, Any>, "$indent  ")}"
                }
                is List<*> -> {
                    "$indent$key: [${value.joinToString(", ") { it.toString() }}]"
                }
                else -> {
                    "$indent$key: $value"
                }
            }
        }
    }

    /**
     * Поиск релевантных данных для вопроса пользователя
     * @param query Вопрос пользователя
     * @param fileIds Список ID файлов для поиска (если пустой - поиск по всем файлам)
     * @param topK Количество релевантных чанков
     * @return Текст с релевантными данными для добавления в контекст
     */
    suspend fun queryData(
        query: String,
        fileIds: List<String> = emptyList(),
        topK: Int = 5
    ): Result<String> {
        return try {
            Napier.d("Querying data for: $query (fileIds: $fileIds, topK: $topK)")

            // Выполняем поиск в RAG
            val searchResult = ragService.search(
                query = query,
                topK = topK * 2, // Берем больше результатов для фильтрации
                minSimilarity = 0.3
            )

            if (searchResult.isFailure) {
                return Result.failure(searchResult.exceptionOrNull() ?: Exception("Search failed"))
            }

            val results = searchResult.getOrThrow()

            // Получаем все документы из RAG для доступа к metadata
            val allDocuments = ragService.getIndexedDocuments()

            // Фильтруем по fileIds, если указаны
            val filteredResults = if (fileIds.isNotEmpty()) {
                results.mapNotNull { result ->
                    val document = allDocuments.find { it.id == result.chunk.documentId }
                    if (document != null && document.metadata["dataFileId"] in fileIds) {
                        Pair(result, document)
                    } else {
                        null
                    }
                }
            } else {
                results.mapNotNull { result ->
                    val document = allDocuments.find { it.id == result.chunk.documentId }
                    if (document != null) {
                        Pair(result, document)
                    } else {
                        null
                    }
                }
            }.take(topK)

            if (filteredResults.isEmpty()) {
                return Result.success("Релевантные данные не найдены.")
            }

            // Формируем контекст из найденных чанков
            val contextText = buildString {
                appendLine("=== РЕЛЕВАНТНЫЕ ДАННЫЕ ===")
                appendLine()

                filteredResults.forEachIndexed { index, (result, document) ->
                    val fileName = document.metadata["dataFileName"] ?: "Unknown"
                    val fileType = document.metadata["dataFileType"] ?: "Unknown"
                    val similarity = ((result.similarity * 100 * 100).toInt() / 100.0).toString()

                    appendLine("--- Документ ${index + 1} ($fileName, $fileType, релевантность: $similarity%) ---")
                    appendLine(result.chunk.content)
                    appendLine()
                }

                appendLine("=== КОНЕЦ ДАННЫХ ===")
            }

            Napier.d("Found ${filteredResults.size} relevant chunks for query")
            Result.success(contextText)
        } catch (e: Exception) {
            Napier.e("Error querying data", e)
            Result.failure(e)
        }
    }
}

/**
 * Чанк данных для индексации
 */
private data class DataChunk(
    val title: String,
    val content: String,
    val type: String, // "summary", "data", "errors"
    val metadata: Map<String, String>
)

/**
 * Индекс загруженных файлов данных
 */
@Serializable
private data class DataFilesIndex(
    val files: List<DataFile>
)