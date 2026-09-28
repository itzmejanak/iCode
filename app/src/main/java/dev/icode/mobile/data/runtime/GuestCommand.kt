package dev.icode.mobile.data.runtime

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** POSIX single-quoting for a guest shell script: everything literal, a quote closed, escaped and reopened. */
internal fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

/**
 * One non-interactive command in the guest, with bounded streaming output.
 *
 * The process bridge would mix stderr into stdout, so stderr goes to a private
 * guest `/tmp` file and is read afterwards. Cancelling the caller kills the
 * process; every process started here is reaped.
 */
internal class GuestCommand(private val layout: RuntimeLayout) {
    data class Result(val exitCode: Int, val stderr: String, val stoppedEarly: Boolean)

    /** Streams newline-delimited UTF-8 output. [onLine] returns false when enough was read. */
    suspend fun stream(
        workspaceHostPath: String?,
        argv: List<String>,
        stderrName: String,
        environment: Map<String, String> = emptyMap(),
        onLine: (String) -> Boolean,
    ): Result = streamRecords(
        workspaceHostPath = workspaceHostPath,
        argv = argv,
        stderrName = stderrName,
        delimiter = '\n'.code,
        environment = environment,
    ) { bytes ->
        val line = String(bytes, StandardCharsets.UTF_8).removeSuffix("\r")
        onLine(line)
    }

    /**
     * Streams byte records split on [delimiter]. NUL-delimited paths therefore
     * survive spaces, quotes and embedded newlines without reparsing text.
     */
    suspend fun streamRecords(
        workspaceHostPath: String?,
        argv: List<String>,
        stderrName: String,
        delimiter: Int,
        environment: Map<String, String> = emptyMap(),
        onRecord: (ByteArray) -> Boolean,
    ): Result = withContext(Dispatchers.IO) {
        require(argv.isNotEmpty()) { "A guest command needs an executable" }
        require(delimiter in 0..255) { "Record delimiter must be one byte" }
        val temp = File(layout.rootfs, "tmp").apply { mkdirs() }
        val prefix = "icode-${stderrName.replace(Regex("[^A-Za-z0-9_.-]"), "-")}-"
        val stderrFile = File.createTempFile(prefix, ".err", temp)
        val guestStderr = "/tmp/${stderrFile.name}"
        val script = argv.joinToString(" ", transform = ::shellQuote) + " 2>" + shellQuote(guestStderr)
        val process = RuntimeProcess.start(
            argv = layout.prootCommand(
                guestCommand = listOf("/bin/sh", "-c", script),
                workspaceHostPath = workspaceHostPath,
                guestWorkingDirectory = workspaceHostPath?.let(RuntimeLayout::guestWorkspace) ?: RuntimeLayout.GUEST_HOME,
                killOnExit = true,
            ),
            environment = layout.prootEnvironment(environment),
            workingDirectory = layout.runtimeRoot.absolutePath,
        )
        var reaped = false
        try {
            var stopped = false
            val readingDone = AtomicBoolean(false)
            coroutineScope {
                // Cancellation cannot interrupt a pipe read. Killing its writer does.
                val watchdog = launch {
                    try {
                        awaitCancellation()
                    } finally {
                        if (!readingDone.get()) process.sendSignal(PosixSignal.KILL)
                    }
                }
                try {
                    stopped = readRecords(process.output, delimiter, onRecord)
                } finally {
                    readingDone.set(true)
                    watchdog.cancel()
                }
            }
            if (stopped) process.sendSignal(PosixSignal.KILL)
            val exitCode = process.waitFor().also { reaped = true }
            Result(exitCode, runCatching { stderrFile.readText() }.getOrDefault(""), stopped)
        } finally {
            if (!reaped) {
                process.sendSignal(PosixSignal.KILL)
                runCatching { process.waitFor() }
            }
            process.destroy()
            stderrFile.delete()
        }
    }

    /** Returns true when the consumer stopped before EOF. */
    private fun readRecords(input: InputStream, delimiter: Int, onRecord: (ByteArray) -> Boolean): Boolean {
        val chunk = ByteArray(BUFFER_BYTES)
        val record = ByteArrayOutputStream()
        while (true) {
            val count = input.read(chunk)
            if (count < 0) break
            for (index in 0 until count) {
                val byte = chunk[index].toInt() and 0xFF
                if (byte == delimiter) {
                    if (!onRecord(record.toByteArray())) return true
                    record.reset()
                } else {
                    if (record.size() >= MAX_RECORD_BYTES) {
                        throw IOException("Guest command output contained a record larger than ${MAX_RECORD_BYTES / (1024 * 1024)} MiB")
                    }
                    record.write(byte)
                }
            }
        }
        return record.size() > 0 && !onRecord(record.toByteArray())
    }

    private companion object {
        const val BUFFER_BYTES = 8192
        const val MAX_RECORD_BYTES = 16 * 1024 * 1024
    }
}
