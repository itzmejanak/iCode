package dev.icode.mobile.data.runtime

import java.io.ByteArrayOutputStream

/**
 * Shell integration's marks in what a terminal prints (docs/assistant-and-mcp.md): FinalTerm's
 * OSC 133 — A the prompt starts, B it ends and input begins, C the command's output starts, D;code it
 * ended — and OSC 7, the directory the shell is in. Every other byte goes on, in order, to
 * [Listener.output], OSC sequences that are not these whole as they came; the marks themselves go
 * nowhere else, as the emulator draws nothing for them. A sequence cut between two reads is held
 * until it ends. Not thread-safe: its session's main thread feeds it.
 */
internal class TerminalMarks(private val listener: Listener) {
    interface Listener {
        /** Bytes the terminal shows, [count] of them from [from] in [data] (an array reused by the caller). */
        fun output(data: ByteArray, from: Int, count: Int)

        fun mark(mark: Mark)
    }

    sealed interface Mark {
        data object PromptStart : Mark
        data object InputStart : Mark
        data object OutputStart : Mark

        /** [exitCode] null when the shell gave none. */
        data class CommandEnd(val exitCode: Int?) : Mark

        /** OSC 7: the shell's directory, its path decoded. */
        data class Directory(val path: String) : Mark
    }

    private var state = State.Text
    private val sequence = ByteArrayOutputStream()

    fun feed(data: ByteArray, count: Int) {
        var runStart = 0
        var index = 0
        while (index < count) {
            val byte = data[index].toInt() and 0xFF
            when (state) {
                State.Text -> if (byte == ESC) {
                    flush(data, runStart, index)
                    sequence.reset()
                    sequence.write(byte)
                    state = State.Escape
                }
                State.Escape -> when (byte) {
                    OSC_INTRODUCER -> {
                        sequence.write(byte)
                        state = State.Osc
                    }
                    // An escape alone, then another, which may open an OSC.
                    ESC -> {
                        passSequence()
                        sequence.reset()
                        sequence.write(byte)
                    }
                    // Not an OSC: an escape the emulator reads, passed on whole.
                    else -> {
                        sequence.write(byte)
                        passSequence()
                        state = State.Text
                        runStart = index + 1
                    }
                }
                State.Osc -> when {
                    byte == BEL -> {
                        sequence.write(byte)
                        endOsc()
                        runStart = index + 1
                    }
                    byte == ESC -> {
                        sequence.write(byte)
                        state = State.OscEscape
                    }
                    sequence.size() >= MAX_SEQUENCE -> {
                        // Not one of ours, whatever it is: passed on as it came.
                        sequence.write(byte)
                        passSequence()
                        state = State.Text
                        runStart = index + 1
                    }
                    else -> sequence.write(byte)
                }
                State.OscEscape -> {
                    sequence.write(byte)
                    if (byte == STRING_TERMINATOR) {
                        endOsc()
                    } else {
                        passSequence()
                        state = State.Text
                    }
                    runStart = index + 1
                }
            }
            index++
        }
        if (state == State.Text) flush(data, runStart, count)
    }

    private fun flush(data: ByteArray, from: Int, to: Int) {
        if (to > from) listener.output(data, from, to - from)
    }

    /** A whole OSC: ours is a mark; any other goes on as the emulator expects it. */
    private fun endOsc() {
        state = State.Text
        val bytes = sequence.toByteArray()
        val end = if (bytes.last().toInt() == BEL) bytes.size - 1 else bytes.size - 2
        val body = String(bytes, 2, (end - 2).coerceAtLeast(0), Charsets.UTF_8)
        val mark = markOf(body)
        if (mark != null) listener.mark(mark) else listener.output(bytes, 0, bytes.size)
    }

    private fun passSequence() {
        val bytes = sequence.toByteArray()
        listener.output(bytes, 0, bytes.size)
    }

    private enum class State { Text, Escape, Osc, OscEscape }

    internal companion object {
        private const val ESC = 0x1B
        private const val BEL = 0x07
        private const val OSC_INTRODUCER = ']'.code
        private const val STRING_TERMINATOR = '\\'.code
        private const val MAX_SEQUENCE = 8 * 1024

        /** "133;D;1" or "7;file://host/path" as its mark; null for any other OSC. */
        fun markOf(body: String): Mark? {
            val number = body.substringBefore(';')
            val rest = body.substringAfter(';', "")
            return when (number) {
                "133" -> when (rest.substringBefore(';')) {
                    "A" -> Mark.PromptStart
                    "B" -> Mark.InputStart
                    "C" -> Mark.OutputStart
                    "D" -> Mark.CommandEnd(rest.substringAfter(';', "").substringBefore(';').toIntOrNull())
                    else -> null
                }
                "7" -> directory(rest)?.let(Mark::Directory)
                else -> null
            }
        }

        /** `file://host/path`, its percent-escapes decoded; a path written raw is taken as it is. */
        private fun directory(url: String): String? {
            if (!url.startsWith("file://")) return null
            val path = "/" + url.removePrefix("file://").substringAfter('/', "")
            val bytes = ByteArrayOutputStream()
            var index = 0
            while (index < path.length) {
                val char = path[index]
                val escaped = path.getOrNull(index + 1)?.digitToIntOrNull(HEX)?.let { high -> path.getOrNull(index + 2)?.digitToIntOrNull(HEX)?.let { high * HEX + it } }
                if (char == '%' && escaped != null) {
                    bytes.write(escaped)
                    index += ESCAPED_LENGTH
                } else {
                    bytes.write(char.toString().toByteArray(Charsets.UTF_8))
                    index++
                }
            }
            return bytes.toString(Charsets.UTF_8.name())
        }

        private const val HEX = 16
        private const val ESCAPED_LENGTH = 3
    }
}
