package com.claude.chat.domain.model

/**
 * Theme mode options for the application
 */
enum class ThemeMode {
    /**
     * Follow system theme settings
     */
    SYSTEM,

    /**
     * Always use light theme
     */
    LIGHT,

    /**
     * Always use dark theme
     */
    DARK;

    companion object {
        /**
         * Parse theme mode from string, defaulting to SYSTEM if invalid
         */
        fun fromString(value: String?): ThemeMode {
            return when (value?.uppercase()) {
                "LIGHT" -> LIGHT
                "DARK" -> DARK
                "SYSTEM" -> SYSTEM
                else -> SYSTEM
            }
        }
    }
}