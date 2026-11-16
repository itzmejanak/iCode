package dev.icode.mobile.domain

// Signed commits (docs/source-control.md): gpg-agent asks for a key's passphrase, or a yes or no,
// through iCode's pinentry, and iCode asks in its own sheet. The passphrase is kept by gpg-agent alone.

/** One question gpg-agent asks, in its own words. */
data class GpgPrompt(
    /** The pinentry's request: its process ID. */
    val id: String,
    /** A passphrase to type; otherwise a yes or no. */
    val asksPassphrase: Boolean,
    val title: String?,
    val description: String,
    /** "Passphrase:". */
    val prompt: String?,
    /** Why it asks again: "Bad Passphrase (try 2 of 3)". */
    val error: String?,
    val ok: String?,
    val cancel: String?,
)

/** What the sheet answers. */
sealed interface GpgAnswer {
    data class Passphrase(val value: String) : GpgAnswer {
        /** Never written out, should an answer ever be. */
        override fun toString(): String = "Passphrase(***)"
    }

    data object Ok : GpgAnswer

    data object Cancel : GpgAnswer
}

/** What iCode's guest script hands over ([GpgRequests.parse]). */
sealed interface GpgRequest {
    /** gpg found no agent: one is started where it outlives the command, for [homedir]. */
    data class StartAgent(val homedir: String) : GpgRequest

    data class Ask(val prompt: GpgPrompt) : GpgRequest
}

/** The sheet's answers. Never behind the mutation lock: the commit waiting on a passphrase holds it. */
sealed interface GpgIntent : ICodeIntent {
    data class Answer(val id: String, val answer: GpgAnswer) : GpgIntent
}

object GpgRequests {
    // A request's kinds and the agent request's field: iCode's own words, written into the guest script from here.
    const val AGENT = "agent"
    const val PIN = "pin"
    const val CONFIRM = "confirm"
    const val HOMEDIR = "HOMEDIR"

    /**
     * A request as the guest script writes it: its kind on the first line — [AGENT], [PIN] or
     * [CONFIRM] — then [HOMEDIR] and a path, or gpg-agent's `SET…` commands as it sent them. Null for
     * a kind this build does not know.
     */
    fun parse(id: String, text: String): GpgRequest? {
        val lines = text.lines().filter(String::isNotEmpty)
        val fields = lines.drop(1).associate { it.substringBefore(' ') to it.substringAfter(' ', "") }
        val kind = lines.firstOrNull()
        return when (kind) {
            AGENT -> fields[HOMEDIR]?.let(GpgRequest::StartAgent)
            PIN, CONFIRM -> GpgRequest.Ask(
                GpgPrompt(
                    id = id,
                    asksPassphrase = kind == PIN,
                    title = fields["SETTITLE"]?.let(::unescape),
                    description = fields["SETDESC"]?.let(::unescape)?.trim().orEmpty(),
                    prompt = fields["SETPROMPT"]?.let(::unescape),
                    error = fields["SETERROR"]?.let(::unescape),
                    ok = fields["SETOK"]?.let(::label),
                    cancel = fields["SETCANCEL"]?.let(::label),
                ),
            )
            else -> null
        }
    }

    /**
     * The line the pinentry passes on to gpg-agent: `D` and the passphrase — `%`, CR and LF escaped,
     * as an Assuan data line wants — `OK`, which is also an empty passphrase, or `CANCEL`.
     */
    fun answerLine(answer: GpgAnswer): String = when (answer) {
        is GpgAnswer.Passphrase ->
            if (answer.value.isEmpty()) "OK" else "D " + answer.value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
        GpgAnswer.Ok -> "OK"
        GpgAnswer.Cancel -> "CANCEL"
    }

    /** Assuan's percent-escapes (`%0A`, `%22`), decoded as the UTF-8 bytes they stand for. */
    private fun unescape(text: String): String {
        val bytes = text.encodeToByteArray()
        val decoded = ByteArray(bytes.size)
        var size = 0
        var i = 0
        while (i < bytes.size) {
            val high = bytes.getOrNull(i + 1)?.let(::hexValue)
            val low = bytes.getOrNull(i + 2)?.let(::hexValue)
            if (bytes[i] == PERCENT && high != null && low != null) {
                decoded[size++] = (high * HEX + low).toByte()
                i += 3
            } else {
                decoded[size++] = bytes[i++]
            }
        }
        return decoded.decodeToString(0, size)
    }

    private fun hexValue(byte: Byte): Int? = when (val c = byte.toInt().toChar()) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + DECIMAL
        in 'A'..'F' -> c - 'A' + DECIMAL
        else -> null
    }

    /** A button's label without its mnemonic's underscore: `_OK` reads OK, and `__` is an underscore. */
    private fun label(text: String): String = unescape(text).split("__").joinToString("_") { it.replace("_", "") }

    private const val PERCENT = '%'.code.toByte()
    private const val HEX = 16
    private const val DECIMAL = 10
}
