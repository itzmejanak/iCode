package dev.icode.mobile.data.language

import dev.icode.mobile.data.language.jsonrpc.JsonRpcConnection
import dev.icode.mobile.domain.LanguageServicePresets

/** Why a language server could not do what was asked, in the user's words (docs/editor-and-language-services.md). */
internal sealed class LanguageFailure(message: String) : Exception(message) {
    /** Its program is not in the runtime; how its preset installs it, when one names it. */
    class Missing(val program: String) : LanguageFailure(
        "$program is not in the Linux runtime." + (installOf(program)?.let { " Install it in a terminal: $it" } ?: " Install it in a terminal first."),
    )

    class NotStarted(words: String) : LanguageFailure(words)

    class Exited(code: Int, tail: List<String>) : LanguageFailure(
        "The language server exited ($code)" + (tail.lastOrNull { it.isNotBlank() }?.let { ": ${it.take(240)}" } ?: ""),
    )

    class NotOffered(what: String, name: String) : LanguageFailure("$name does not offer $what")

    class NoServer(what: String) : LanguageFailure(what)

    /** How a server came to fail, for what the user is told: not installed, not started, or stopped after crashing. */
    enum class Kind { Missing, NotStarted, Crashed }

    companion object {
        /** The command that installs [program], from the preset that runs it; null when iCode knows none. */
        fun installOf(program: String): String? =
            LanguageServicePresets.all.firstOrNull { it.configuration.command.substringBefore(' ') == program }?.install

        /** A connection's failure, as a sentence. */
        fun wordsOf(failure: Throwable): String = when (failure) {
            is LanguageFailure -> failure.message.orEmpty()
            is JsonRpcConnection.TimeoutException -> "The language server did not answer ${failure.method} in ${failure.millis / 1000} s"
            is JsonRpcConnection.ClosedException -> "The language server stopped"
            is JsonRpcConnection.JsonRpcException -> "The language server refused: ${failure.message}"
            else -> failure.message ?: failure.javaClass.simpleName
        }
    }
}
