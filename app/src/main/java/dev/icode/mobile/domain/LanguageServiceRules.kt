package dev.icode.mobile.domain

/** Configuration is separate from process state, which is never persisted. */
data class LanguageServiceConfiguration(
    val id: String,
    val name: String,
    val command: String,
    val patterns: String,
    val rootMarkers: String,
    val initializationOptions: String,
)

object LanguageServiceRules {
    const val MAX_SERVICES = 16

    /**
     * The rows for [configs], in their order. Each keeps the process state [shown] for it — a
     * server keeps running while another service is saved — except [changed], whose
     * configuration was just saved: it starts from Stopped, as a service that has not run yet.
     */
    fun rows(configs: List<LanguageServiceConfiguration>, shown: List<LanguageService>, changed: String?): List<LanguageService> =
        configs.map { config ->
            val row = row(config)
            val live = shown.firstOrNull { it.id == config.id }?.takeIf { config.id != changed } ?: return@map row
            row.copy(status = live.status, processId = live.processId, statusMessage = live.statusMessage)
        }

    private fun row(config: LanguageServiceConfiguration) = LanguageService(
        initials = config.name.split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercase() }
            .joinToString("").ifBlank { "LS" },
        name = config.name,
        patterns = config.patterns.ifBlank { "All files" },
        id = config.id,
        command = config.command,
        rootMarkers = config.rootMarkers,
        initializationOptions = config.initializationOptions,
    )

    fun validate(name: String, command: String, patterns: String, options: String): String? = when {
        name.isBlank() -> "Enter a service name"
        name.length > 80 -> "Service name is too long"
        command.isBlank() -> "Enter a server command"
        command.length > 1024 -> "Server command is too long"
        patterns.length > 1024 -> "File patterns are too long"
        options.length > 8192 -> "Initialization options are too large"
        else -> null
    }

    /** No shell interpolation: each quoted word becomes exactly one process argument. */
    fun commandArguments(command: String): List<String> {
        val args = mutableListOf<String>()
        val word = StringBuilder()
        var quote: Char? = null
        var escape = false
        var inWord = false
        for (char in command) {
            when {
                escape -> { word.append(char); escape = false; inWord = true }
                char == '\\' && quote != '\'' -> { escape = true; inWord = true }
                quote != null && char == quote -> { quote = null; inWord = true }
                quote != null -> { word.append(char); inWord = true }
                char == '\'' || char == '"' -> { quote = char; inWord = true }
                char.isWhitespace() -> if (inWord) {
                    args += word.toString(); word.clear(); inWord = false
                }
                else -> { word.append(char); inWord = true }
            }
        }
        require(!escape && quote == null) { "Finish the quoted command argument" }
        if (inWord) args += word.toString()
        require(args.firstOrNull()?.isNotBlank() == true) { "Enter a server command" }
        return args
    }
}
