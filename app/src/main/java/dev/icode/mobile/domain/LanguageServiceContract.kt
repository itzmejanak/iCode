package dev.icode.mobile.domain

// Language services (S8): configured servers and their process state.

data class LanguageService(
    val initials: String,
    val name: String,
    val patterns: String,
    val id: String = name,
    val command: String = "",
    val rootMarkers: String = "",
    val initializationOptions: String = "{}",
    val status: LanguageServiceStatus = LanguageServiceStatus.Stopped,
    val processId: Int? = null,
    val statusMessage: String? = null,
)

enum class LanguageServiceStatus { Stopped, Starting, Running, Stopping, Failed }
