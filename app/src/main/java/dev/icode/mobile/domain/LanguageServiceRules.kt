package dev.icode.mobile.domain

/**
 * A language server as the user (or the assistant) set it up (docs/lsp-plan.md §5.1, architecture
 * §10.8). Each field is kept as the form edits it; [LanguageServiceRules] reads them. Process state is
 * never persisted.
 */
data class LanguageServiceConfiguration(
    val id: String,
    val name: String,
    /** The program and its arguments, as words ([LanguageServiceRules.commandArguments]). */
    val command: String,
    /** LSP language identifiers it serves (`python`, `typescriptreact`), separated by commas or spaces. */
    val languages: String = "",
    /** File-name patterns it serves (`*.py`, `Dockerfile`), separated by commas or spaces. */
    val patterns: String = "",
    /** Files or folders that mark a project's top (`pyproject.toml`), separated by commas or spaces. */
    val rootMarkers: String = "",
    /** Variables over the login environment, one `NAME=value` a line. */
    val environment: String = "",
    /** The JSON object sent as `initializationOptions`. */
    val initializationOptions: String = "{}",
    /** The JSON object `workspace/configuration` is answered from, by section. */
    val settings: String = "{}",
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
            row.copy(status = live.status, processId = live.processId, statusMessage = live.statusMessage, capabilities = live.capabilities)
        }

    fun row(config: LanguageServiceConfiguration) = LanguageService(
        initials = config.name.split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercase() }
            .joinToString("").ifBlank { "LS" },
        name = config.name,
        patterns = config.patterns,
        id = config.id,
        command = config.command,
        rootMarkers = config.rootMarkers,
        initializationOptions = config.initializationOptions,
        languages = config.languages,
        environment = config.environment,
        settings = config.settings,
    )

    /** What the form's fields would save as, for [id] ("" for a new one). */
    fun configurationOf(service: LanguageService) = LanguageServiceConfiguration(
        service.id, service.name, service.command, service.languages, service.patterns, service.rootMarkers,
        service.environment, service.initializationOptions, service.settings,
    )

    /** Why [config] cannot be saved, in the user's words; null when it can. JSON is checked by the owner. */
    fun validate(config: LanguageServiceConfiguration): String? = when {
        config.name.isBlank() -> "Enter a service name"
        config.name.length > 80 -> "Service name is too long"
        config.command.isBlank() -> "Enter a server command"
        config.command.length > 1024 -> "Server command is too long"
        config.languages.length > 1024 -> "Languages are too long"
        config.patterns.length > 1024 -> "File patterns are too long"
        config.rootMarkers.length > 1024 -> "Root markers are too long"
        config.environment.length > 8192 -> "Environment is too long"
        config.initializationOptions.length > 8192 -> "Initialization options are too large"
        config.settings.length > 32768 -> "Settings are too large"
        else -> runCatching { commandArguments(config.command); environmentOf(config.environment) }.exceptionOrNull()?.message
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

    /** A list field's entries: separated by commas, spaces or lines; blanks dropped. */
    fun words(list: String): List<String> {
        val words = mutableListOf<String>()
        val word = StringBuilder()
        var braces = 0
        for (char in list) {
            when {
                char == '{' -> { braces++; word.append(char) }
                char == '}' -> { braces = (braces - 1).coerceAtLeast(0); word.append(char) }
                // A comma inside braces separates a pattern's choices (`*.{ts,tsx}`), not two entries.
                char.isWhitespace() || (char == ',' && braces == 0) -> {
                    if (word.isNotEmpty()) words += word.toString()
                    word.clear()
                }
                else -> word.append(char)
            }
        }
        if (word.isNotEmpty()) words += word.toString()
        return words
    }

    /** `NAME=value` lines as variables; blank lines and `#` comments skipped. */
    fun environmentOf(text: String): Map<String, String> = text.lines().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .associate { line ->
            val name = line.substringBefore('=', missingDelimiterValue = "")
            require(NAME.matches(name)) { "Write each variable as NAME=value: $line" }
            name to line.substringAfter('=')
        }

    /**
     * Whether [config] serves a file named [fileName] of [languageId] (LSP's identifier). A service
     * that names neither languages nor patterns starts only from its page.
     */
    fun serves(config: LanguageServiceConfiguration, fileName: String, languageId: String): Boolean =
        languageId in words(config.languages) || words(config.patterns).any { glob(it).matches(fileName) }

    /** A file-name pattern: `*` any run of characters, `?` one, `{a,b}` either; case-sensitive, as Linux names are. */
    fun glob(pattern: String): Regex = Regex(buildString {
        var inGroup = false
        for (char in pattern) when {
            char == '*' -> append(".*")
            char == '?' -> append('.')
            char == '{' -> { inGroup = true; append("(?:") }
            char == '}' && inGroup -> { inGroup = false; append(')') }
            char == ',' && inGroup -> append('|')
            else -> append(Regex.escape(char.toString()))
        }
    })

    /** LSP's language identifier for a file of [type] (spec 3.17, "Text Document Item"): iCode's ids are VS Code's, but for shell scripts. */
    fun languageIdOf(type: FileType): String = when (type.id) {
        "shell" -> "shellscript"
        else -> type.id
    }

    private val NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
}
