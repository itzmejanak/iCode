package dev.icode.mobile.data.language

import dev.icode.mobile.data.runtime.StdioPrograms
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Each language service's log (docs/lsp-plan.md §5.2): what iCode saw of it — started, initialized,
 * exited, the server's own `window/logMessage` — at most [EVENTS] lines, then its stderr's last
 * [STDERR] lines from its log in the runtime.
 */
internal class LanguageLogs(private val stdio: StdioPrograms) {
    private val events = ConcurrentHashMap<String, ArrayDeque<String>>()
    private val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

    fun add(id: String, line: String) {
        val stamped = synchronized(time) { time.format(Date()) } + "  " + line.take(MAX_LINE)
        val lines = events.getOrPut(id) { ArrayDeque() }
        synchronized(lines) {
            lines.addLast(stamped)
            while (lines.size > EVENTS) lines.removeFirst()
        }
    }

    fun of(id: String): List<String> {
        val seen = events[id]?.let { synchronized(it) { it.toList() } }.orEmpty()
        val stderr = stdio.tail(id, STDERR)
        return seen + if (stderr.isEmpty()) emptyList() else listOf("— stderr —") + stderr.map { it.take(MAX_LINE) }
    }

    private companion object {
        const val EVENTS = 200
        const val STDERR = 100
        const val MAX_LINE = 500
    }
}
