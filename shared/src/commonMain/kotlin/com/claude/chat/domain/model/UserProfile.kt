package com.claude.chat.domain.model

import kotlinx.serialization.Serializable

/**
 * User profile model for personalizing AI agent behavior
 */
@Serializable
data class UserProfile(
    val name: String,
    val bio: String? = null,
    val location: String? = null,
    val gender: String? = null,
    val habits: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val workingHours: WorkingHours? = null,
    val goals: List<String> = emptyList(),
    val communicationTone: String? = null,
    val avoidTopics: List<String> = emptyList()
)

/**
 * Working hours specification with timezone
 */
@Serializable
data class WorkingHours(
    val start: String, // Format: "HH:mm"
    val end: String,   // Format: "HH:mm"
    val timezone: String // IANA timezone, e.g. "Europe/Moscow"
)