package com.claude.chat.domain.service

import com.claude.chat.domain.model.UserProfile
import com.claude.chat.platform.FileStorage
import io.github.aakira.napier.Napier
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Service for managing user profile
 */
class UserProfileService(
    private val fileStorage: FileStorage
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private var cachedProfile: UserProfile? = null

    companion object {
        private const val USER_PROFILE_FILE_NAME = "user_profile.json"
    }

    /**
     * Load profile from JSON content
     */
    suspend fun loadProfile(jsonContent: String): Result<UserProfile> {
        return try {
            val profile = json.decodeFromString<UserProfile>(jsonContent)

            // Validate required fields
            if (profile.name.isBlank()) {
                return Result.failure(Exception("Profile name is required"))
            }

            // Save to storage
            val saved = fileStorage.writeTextFile(USER_PROFILE_FILE_NAME, jsonContent)
            if (!saved) {
                return Result.failure(Exception("Failed to save profile to storage"))
            }

            // Cache the profile
            cachedProfile = profile

            Napier.d("User profile loaded successfully: ${profile.name}")
            Result.success(profile)
        } catch (e: SerializationException) {
            Napier.e("Error parsing user profile JSON", e)
            Result.failure(Exception("Invalid JSON format: ${e.message}"))
        } catch (e: Exception) {
            Napier.e("Error loading user profile", e)
            Result.failure(e)
        }
    }

    /**
     * Get current profile from cache or storage
     */
    suspend fun getProfile(): UserProfile? {
        // Return cached profile if available
        if (cachedProfile != null) {
            return cachedProfile
        }

        // Try to load from storage
        return try {
            val jsonContent = fileStorage.readTextFile(USER_PROFILE_FILE_NAME)
            if (jsonContent != null) {
                val profile = json.decodeFromString<UserProfile>(jsonContent)
                cachedProfile = profile
                profile
            } else {
                null
            }
        } catch (e: Exception) {
            Napier.e("Error reading user profile from storage", e)
            null
        }
    }

    /**
     * Clear current profile
     */
    suspend fun clearProfile(): Boolean {
        return try {
            cachedProfile = null
            val deleted = fileStorage.deleteFile(USER_PROFILE_FILE_NAME)
            if (deleted) {
                Napier.d("User profile cleared successfully")
            }
            deleted
        } catch (e: Exception) {
            Napier.e("Error clearing user profile", e)
            false
        }
    }

    /**
     * Build profile context for system prompt
     */
    fun buildProfileContext(profile: UserProfile): String {
        return buildString {
            appendLine("=== ИНФОРМАЦИЯ О ПОЛЬЗОВАТЕЛЕ ===")
            appendLine()

            // Basic info
            appendLine("Имя: ${profile.name}")

            if (!profile.bio.isNullOrBlank()) {
                appendLine("О себе: ${profile.bio}")
            }

            if (!profile.location.isNullOrBlank()) {
                appendLine("Местоположение: ${profile.location}")
            }

            if (!profile.gender.isNullOrBlank()) {
                appendLine("Пол: ${profile.gender}")
            }

            // Habits
            if (profile.habits.isNotEmpty()) {
                appendLine()
                appendLine("Привычки и предпочтения:")
                profile.habits.forEach { habit ->
                    appendLine("  • $habit")
                }
            }

            // Interests
            if (profile.interests.isNotEmpty()) {
                appendLine()
                appendLine("Интересы:")
                profile.interests.forEach { interest ->
                    appendLine("  • $interest")
                }
            }

            // Working hours
            if (profile.workingHours != null) {
                appendLine()
                appendLine("Рабочее время: ${profile.workingHours.start} - ${profile.workingHours.end} (${profile.workingHours.timezone})")
            }

            // Goals
            if (profile.goals.isNotEmpty()) {
                appendLine()
                appendLine("Цели взаимодействия с агентом:")
                profile.goals.forEach { goal ->
                    appendLine("  • $goal")
                }
            }

            // Communication tone
            if (!profile.communicationTone.isNullOrBlank()) {
                appendLine()
                appendLine("Предпочитаемый тон общения: ${profile.communicationTone}")
            }

            // Avoid topics
            if (profile.avoidTopics.isNotEmpty()) {
                appendLine()
                appendLine("Чего избегать при общении:")
                profile.avoidTopics.forEach { topic ->
                    appendLine("  • $topic")
                }
            }

            appendLine()
            appendLine("=== КОНЕЦ ИНФОРМАЦИИ О ПОЛЬЗОВАТЕЛЕ ===")
            appendLine()
            appendLine("ВАЖНО: Используй эту информацию для персонализации общения. Учитывай предпочтения, цели и стиль коммуникации пользователя во всех ответах.")
        }
    }
}
