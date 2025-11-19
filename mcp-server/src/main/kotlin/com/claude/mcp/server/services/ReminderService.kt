package com.claude.mcp.server.services

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

@Serializable
data class Task(
    val id: Long,
    val title: String,
    val description: String? = null,
    val createdAt: String,
    val completedAt: String? = null,
    val isCompleted: Boolean = false
)

/**
 * Service for managing reminders and tasks with SQLite storage
 */
class ReminderService(
    private val dbPath: String = "reminders.db",
    private val notificationIntervalSeconds: Long = 60 // 1 minute
) {
    private val connection: Connection
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _notifications = MutableStateFlow<String?>(null)
    val notifications: StateFlow<String?> = _notifications

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    init {
        // Initialize SQLite connection
        connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        initializeDatabase()
        startNotificationTimer()
        logger.info { "ReminderService initialized with database at $dbPath" }
    }

    private fun initializeDatabase() {
        connection.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    description TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    completed_at DATETIME,
                    is_completed BOOLEAN DEFAULT 0
                )
                """.trimIndent()
            )
        }
        logger.info { "Database initialized" }
    }

    private fun startNotificationTimer() {
        scope.launch {
            while (isActive) {
                delay(notificationIntervalSeconds * 1000)
                try {
                    val summary = generateSummary()
                    _notifications.value = summary
                    logger.info { "Notification sent: $summary" }
                } catch (e: Exception) {
                    logger.error(e) { "Error generating notification" }
                }
            }
        }
    }

    suspend fun addTask(title: String, description: String? = null): Result<Task> {
        return withContext(Dispatchers.IO) {
            try {
                val stmt = connection.prepareStatement(
                    "INSERT INTO tasks (title, description) VALUES (?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
                )
                stmt.setString(1, title)
                stmt.setString(2, description)
                stmt.executeUpdate()

                val generatedKeys = stmt.generatedKeys
                if (generatedKeys.next()) {
                    val id = generatedKeys.getLong(1)
                    val task = getTaskById(id)
                    if (task != null) {
                        logger.info { "Task added: $title (ID: $id)" }
                        Result.success(task)
                    } else {
                        Result.failure(Exception("Failed to retrieve created task"))
                    }
                } else {
                    Result.failure(Exception("Failed to create task"))
                }
            } catch (e: Exception) {
                logger.error(e) { "Error adding task" }
                Result.failure(e)
            }
        }
    }

    suspend fun completeTask(id: Long): Result<Task> {
        return withContext(Dispatchers.IO) {
            try {
                val stmt = connection.prepareStatement(
                    "UPDATE tasks SET is_completed = 1, completed_at = CURRENT_TIMESTAMP WHERE id = ?"
                )
                stmt.setLong(1, id)
                val updated = stmt.executeUpdate()

                if (updated > 0) {
                    val task = getTaskById(id)
                    if (task != null) {
                        logger.info { "Task completed: ${task.title} (ID: $id)" }
                        Result.success(task)
                    } else {
                        Result.failure(Exception("Task not found after update"))
                    }
                } else {
                    Result.failure(Exception("Task not found with ID: $id"))
                }
            } catch (e: Exception) {
                logger.error(e) { "Error completing task" }
                Result.failure(e)
            }
        }
    }

    suspend fun getTasks(includeCompleted: Boolean = true): Result<List<Task>> {
        return withContext(Dispatchers.IO) {
            try {
                val query = if (includeCompleted) {
                    "SELECT * FROM tasks ORDER BY created_at DESC"
                } else {
                    "SELECT * FROM tasks WHERE is_completed = 0 ORDER BY created_at DESC"
                }

                val tasks = mutableListOf<Task>()
                connection.createStatement().use { stmt ->
                    val rs = stmt.executeQuery(query)
                    while (rs.next()) {
                        tasks.add(rs.toTask())
                    }
                }

                logger.debug { "Retrieved ${tasks.size} tasks" }
                Result.success(tasks)
            } catch (e: Exception) {
                logger.error(e) { "Error retrieving tasks" }
                Result.failure(e)
            }
        }
    }

    suspend fun deleteTask(id: Long): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val stmt = connection.prepareStatement("DELETE FROM tasks WHERE id = ?")
                stmt.setLong(1, id)
                val deleted = stmt.executeUpdate()

                if (deleted > 0) {
                    logger.info { "Task deleted (ID: $id)" }
                    Result.success(true)
                } else {
                    Result.failure(Exception("Task not found with ID: $id"))
                }
            } catch (e: Exception) {
                logger.error(e) { "Error deleting task" }
                Result.failure(e)
            }
        }
    }

    private fun getTaskById(id: Long): Task? {
        val stmt = connection.prepareStatement("SELECT * FROM tasks WHERE id = ?")
        stmt.setLong(1, id)
        val rs = stmt.executeQuery()
        return if (rs.next()) rs.toTask() else null
    }

    private fun ResultSet.toTask(): Task {
        return Task(
            id = getLong("id"),
            title = getString("title"),
            description = getString("description"),
            createdAt = getString("created_at"),
            completedAt = getString("completed_at"),
            isCompleted = getBoolean("is_completed")
        )
    }

    private suspend fun generateSummary(): String {
        val allTasks = getTasks().getOrNull() ?: emptyList()
        val activeTasks = allTasks.filter { !it.isCompleted }

        // Get tasks completed today
        val today = LocalDateTime.now().toLocalDate()
        val completedToday = allTasks.filter { task ->
            task.completedAt != null &&
            LocalDateTime.parse(task.completedAt, dateFormatter).toLocalDate() == today
        }

        return buildString {
            appendLine("📋 Task Summary Report")
            appendLine("━━━━━━━━━━━━━━━━━━━━")
            appendLine()

            appendLine("📌 Active Tasks (${activeTasks.size}):")
            if (activeTasks.isEmpty()) {
                appendLine("   ✨ No active tasks - you're all caught up!")
            } else {
                activeTasks.forEachIndexed { index, task ->
                    appendLine("   ${index + 1}. [ID: ${task.id}] ${task.title}")
                    task.description?.let { desc ->
                        appendLine("      📝 $desc")
                    }
                }
            }

            appendLine()
            appendLine("✅ Completed Today (${completedToday.size}):")
            if (completedToday.isEmpty()) {
                appendLine("   No tasks completed today yet")
            } else {
                completedToday.forEachIndexed { index, task ->
                    appendLine("   ${index + 1}. ${task.title}")
                    task.completedAt?.let { completedAt ->
                        val time = LocalDateTime.parse(completedAt, dateFormatter)
                            .format(DateTimeFormatter.ofPattern("HH:mm"))
                        appendLine("      ⏰ Completed at: $time")
                    }
                }
            }

            appendLine()
            appendLine("📊 Statistics:")
            appendLine("   • Total tasks: ${allTasks.size}")
            appendLine("   • Active: ${activeTasks.size}")
            appendLine("   • Completed: ${allTasks.size - activeTasks.size}")
            appendLine()
            appendLine("⏰ Next update in ${notificationIntervalSeconds}s")
        }
    }

    fun getLatestNotification(): String? {
        return _notifications.value
    }

    fun close() {
        scope.cancel()
        connection.close()
        logger.info { "ReminderService closed" }
    }
}