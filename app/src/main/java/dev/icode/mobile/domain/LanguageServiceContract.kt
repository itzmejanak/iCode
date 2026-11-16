package dev.icode.mobile.domain

// Language services (docs/editor-and-language-services.md): configured servers, their process state and what each reported it can do.

data class LanguageService(
    val initials: String,
    val name: String,
    val patterns: String,
    val id: String = name,
    val command: String = "",
    val rootMarkers: String = "",
    val initializationOptions: String = "{}",
    val languages: String = "",
    val environment: String = "",
    val settings: String = "{}",
    val status: LanguageServiceStatus = LanguageServiceStatus.Stopped,
    val processId: Int? = null,
    val statusMessage: String? = null,
    /** What the server said it can do, after its handshake: "Completion", "Hover"… Empty until then. */
    val capabilities: List<String> = emptyList(),
    /** Its program is not in the runtime: it failed for that, and [install] is how to put it there when iCode knows. */
    val notInstalled: Boolean = false,
    val install: String? = null,
) {
    /** What the list shows it serves; a service serving nothing starts only from its page. */
    val servesLabel: String get() = (LanguageServiceRules.words(languages) + LanguageServiceRules.words(patterns))
        .joinToString(", ").ifBlank { "Started from its page" }
}

enum class LanguageServiceStatus { Stopped, Starting, Running, Stopping, Failed }

/** What the Languages pages and the editor ask of the language services' owner (docs/editor-and-language-services.md). */
sealed interface LanguageIntent : ICodeIntent {
    /** [id]'s page opens; "" for a new one. Its log is read for it. */
    data class Select(val id: String) : LanguageIntent

    /** Adds [configuration] (ID "") or changes it; a running server starts again with it. */
    data class Save(val configuration: LanguageServiceConfiguration) : LanguageIntent

    data class Remove(val id: String) : LanguageIntent

    data class Start(val id: String) : LanguageIntent

    data class Stop(val id: String) : LanguageIntent

    data class Restart(val id: String) : LanguageIntent

    /** The selected service's log, read again. */
    data object RefreshLog : LanguageIntent

    /** The command that installs [command]'s program typed in a new terminal tab, to run there as the user watches. */
    data class Install(val command: String) : LanguageIntent
}
