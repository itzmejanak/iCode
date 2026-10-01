package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.io.readAtMost
import dev.icode.mobile.domain.ByteSize
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** POSIX single-quoting for a guest shell script: everything literal, a quote closed, escaped and reopened. */
internal fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

/**
 * One non-interactive command in the guest, with bounded streaming output.
 *
 * The process bridge would mix stderr into stdout, so stderr goes to a private
 * guest `/tmp` file of its own and is read back, bounded. Cancelling the caller
 * kills the process and every child it started; every process is reaped.
 */
internal class GuestCommand(private val layout: RuntimeLayout) {
    /** Output read whole; a command that printed more than its bound was stopped and throws instead. */
    data class Captured(val exitCode: Int, val stdout: String, val stderr: String)

    data class Result(val exitCode: Int, val stderr: String, val stoppedEarly: Boolean) {
        /** The stderr line that explains a failure, bounded; null when there is none. */
        fun failureDetail(): String? {
            val lines = stderr.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
            val explanatory = lines.lastOrNull { it.startsWith("error:", ignoreCase = true) }
                ?: lines.lastOrNull { line ->
                    listOf("permission denied", "not found", "failed", "unable", "error").any {
                        line.contains(it, ignoreCase = true)
                    }
                }
                ?: lines.lastOrNull()
            return explanatory?.removePrefix("error: ")?.take(MAX_ERROR_CHARS)
        }
    }

    /** Streams newline-delimited UTF-8 output. [onLine] returns false when enough was read. */
    suspend fun stream(
        location: String?,
        argv: List<String>,
        stderrName: String,
        environment: Map<String, String> = emptyMap(),
        /** A workspace's location: the command sees only it ([RuntimeLayout.prootCommand]). */
        workspace: String? = null,
        onLine: (String) -> Boolean,
    ): Result = streamRecords(
        location = location,
        argv = argv,
        stderrName = stderrName,
        delimiter = '\n'.code,
        environment = environment,
        workspace = workspace,
    ) { bytes ->
        val line = String(bytes, StandardCharsets.UTF_8).removeSuffix("\r")
        onLine(line)
    }

    /**
     * Streams byte records split on [delimiter]. NUL-delimited paths therefore
     * survive spaces, quotes and embedded newlines without reparsing text.
     */
    suspend fun streamRecords(
        location: String?,
        argv: List<String>,
        stderrName: String,
        delimiter: Int,
        environment: Map<String, String> = emptyMap(),
        workspace: String? = null,
        onRecord: (ByteArray) -> Boolean,
    ): Result {
        require(argv.isNotEmpty()) { "A guest command needs an executable" }
        require(delimiter in 0..255) { "Record delimiter must be one byte" }
        return run(location, argv.joinToString(" ", transform = ::shellQuote), stderrName, environment, null, workspace) { input ->
            readRecords(input, delimiter, onRecord)
        }
    }

    /**
     * Runs [argv] and returns its whole output, refusing more than
     * [maxStdoutBytes]. [onStderr] sees stderr as it grows — Git prints its
     * progress there — and once more when the command has ended.
     */
    suspend fun capture(
        location: String?,
        argv: List<String>,
        stderrName: String,
        environment: Map<String, String> = emptyMap(),
        maxStdoutBytes: Int = MAX_CAPTURE_BYTES,
        onStderr: ((String) -> Unit)? = null,
    ): Captured {
        require(argv.isNotEmpty()) { "A guest command needs an executable" }
        val command = argv.joinToString(" ", transform = ::shellQuote)
        return captureCommand(location, command, stderrName, environment, maxStdoutBytes, onStderr)
    }

    /** [capture] for a constant multi-command [script]; the stderr of all its commands is collected together. */
    suspend fun captureScript(
        location: String?,
        script: String,
        stderrName: String,
        environment: Map<String, String> = emptyMap(),
        maxStdoutBytes: Int = MAX_CAPTURE_BYTES,
    ): Captured = captureCommand(location, "{ $script\n}", stderrName, environment, maxStdoutBytes, null)

    private suspend fun captureCommand(
        location: String?,
        command: String,
        stderrName: String,
        environment: Map<String, String>,
        maxStdoutBytes: Int,
        onStderr: ((String) -> Unit)?,
    ): Captured {
        var stdout = ByteArray(0)
        val result = run(location, command, stderrName, environment, onStderr) { input ->
            stdout = input.readAtMost(maxStdoutBytes, "The command printed more than ${ByteSize.format(maxStdoutBytes.toLong())} and was stopped")
            false
        }
        return Captured(result.exitCode, String(stdout, StandardCharsets.UTF_8), result.stderr)
    }

    /**
     * Starts [command] in [location] — a folder in the workspaces, or the guest
     * home when null — with its stderr in a private guest `/tmp` file, hands
     * stdout to [read], and always kills and reaps the process: on cancellation,
     * on [read] stopping early (it returns true) and on [read] throwing.
     */
    private suspend fun run(
        location: String?,
        command: String,
        stderrName: String,
        environment: Map<String, String>,
        onStderr: ((String) -> Unit)?,
        workspace: String? = null,
        read: (InputStream) -> Boolean,
    ): Result = withContext(Dispatchers.IO) {
        val workingDirectory = location?.let { layout.workspaces.guestOf(it) ?: throw IOException("$it is not in iCode's workspaces") }
            ?: RuntimeLayout.GUEST_HOME
        val temp = File(layout.rootfs, "tmp").apply { mkdirs() }
        val prefix = "icode-${stderrName.replace(Regex("[^A-Za-z0-9_.-]"), "-")}-"
        val stderrFile = File.createTempFile(prefix, ".err", temp)
        val script = command + " 2>" + shellQuote("/tmp/${stderrFile.name}")
        val process = RuntimeProcess.start(
            argv = layout.prootCommand(
                guestCommand = listOf("/bin/sh", "-c", script),
                guestWorkingDirectory = workingDirectory,
                killOnExit = true,
                workspace = workspace,
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
                val tail = onStderr?.let { report -> launch { followStderr(stderrFile, report) } }
                try {
                    stopped = read(process.output)
                } finally {
                    readingDone.set(true)
                    watchdog.cancel()
                    tail?.cancel()
                }
            }
            if (stopped) process.sendSignal(PosixSignal.KILL)
            val exitCode = process.waitFor().also { reaped = true }
            val stderr = readTail(stderrFile)
            // The file is sampled, so its final state may never have been reported.
            onStderr?.invoke(stderr)
            Result(exitCode, stderr, stopped)
        } finally {
            if (!reaped) {
                process.sendSignal(PosixSignal.KILL)
                runCatching { process.waitFor() }
            }
            process.destroy()
            stderrFile.delete()
        }
    }

    /** Reports [file] whenever it has grown; a few samples a second match how often Git redraws. */
    private suspend fun followStderr(file: File, report: (String) -> Unit) {
        var reported = -1L
        while (true) {
            delay(STDERR_POLL_MILLIS)
            val length = file.length()
            if (length == reported) continue
            reported = length
            report(readTail(file))
        }
    }

    /** The last [MAX_STDERR_BYTES] of [file]: the explanation and the latest progress are at the end. */
    private fun readTail(file: File): String = runCatching {
        RandomAccessFile(file, "r").use { handle ->
            val length = handle.length()
            val start = (length - MAX_STDERR_BYTES).coerceAtLeast(0L)
            handle.seek(start)
            val bytes = ByteArray((length - start).toInt())
            handle.readFully(bytes)
            String(bytes, StandardCharsets.UTF_8)
        }
    }.getOrDefault("")

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
                        throw IOException("Guest command output contained a record larger than ${ByteSize.format(MAX_RECORD_BYTES.toLong())}")
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
        const val MAX_ERROR_CHARS = 300
        const val MAX_CAPTURE_BYTES = 8 * 1024 * 1024
        const val MAX_STDERR_BYTES = 1024 * 1024
        const val STDERR_POLL_MILLIS = 200L
    }
}
