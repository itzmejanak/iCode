package dev.icode.mobile.data.runtime

import android.os.ParcelFileDescriptor
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** JNI bindings for the runtime process bridge. */
internal object NativeProcess {
    init {
        System.loadLibrary("icodespawn")
    }

    /** Returns `[pid, readFd, writeFd]`, or null when the process could not start. */
    @JvmStatic
    external fun nativeSpawn(
        argv: Array<String>,
        environment: Array<String>,
        workingDirectory: String,
    ): IntArray?

    /** Blocks until the process ends; returns its exit code, or 128+signal. */
    @JvmStatic
    external fun nativeWaitFor(pid: Int): Int

    @JvmStatic
    external fun nativeSendSignal(pid: Int, signalNumber: Int): Int

    @JvmStatic
    external fun nativeClose(fd: Int)
}

/** POSIX signals the runtime sends; named so call sites do not carry magic numbers. */
internal object PosixSignal {
    const val INT = 2
    const val KILL = 9
    const val TERM = 15
}

/**
 * A live runtime process, connected by pipes.
 *
 * Owns its descriptors: [output] and [input] are closed with the process, and
 * closing them twice is harmless.
 */
internal class RuntimeProcess private constructor(
    val pid: Int,
    readFd: Int,
    writeFd: Int,
) {
    // These must stay referenced for the life of the process. A
    // ParcelFileDescriptor closes its descriptor when it is finalized, so
    // letting one become garbage closes the pipe out from under an in-flight
    // read ("read interrupted by close() on another thread").
    private val readDescriptor: ParcelFileDescriptor = ParcelFileDescriptor.adoptFd(readFd)
    private val writeDescriptor: ParcelFileDescriptor = ParcelFileDescriptor.adoptFd(writeFd)

    val output: InputStream = ParcelFileDescriptor.AutoCloseInputStream(readDescriptor)
    val input: OutputStream = ParcelFileDescriptor.AutoCloseOutputStream(writeDescriptor)

    @Volatile
    private var closed = false
    @Volatile
    private var exited = false

    /** Blocks the calling thread until the process ends. Never call on the main thread. */
    fun waitFor(): Int = NativeProcess.nativeWaitFor(pid).also { exited = true }

    fun sendSignal(signalNumber: Int) {
        if (!closed && !exited) NativeProcess.nativeSendSignal(pid, signalNumber)
    }

    /**
     * Asks the process to stop, then stops insisting.
     *
     * SIGTERM gives a shell the chance to clean up; the caller escalates to
     * [PosixSignal.KILL] only after the grace period it chooses.
     */
    fun terminate() {
        sendSignal(PosixSignal.TERM)
    }

    fun destroy() {
        if (closed) return
        if (!exited) sendSignal(PosixSignal.KILL)
        closed = true
        // Closing the streams closes the descriptors they adopted.
        runCatching { output.close() }
        runCatching { input.close() }
    }

    companion object {
        /** Starts [argv] inside [workingDirectory], with pipes for its input and output. */
        fun start(
            argv: List<String>,
            environment: Map<String, String>,
            workingDirectory: String,
        ): RuntimeProcess {
            require(argv.isNotEmpty()) { "A process needs an executable" }
            val result = NativeProcess.nativeSpawn(
                argv = argv.toTypedArray(),
                environment = environment.map { "${it.key}=${it.value}" }.toTypedArray(),
                workingDirectory = workingDirectory,
            ) ?: throw IOException("Unable to start ${argv.first()}")
            return RuntimeProcess(pid = result[0], readFd = result[1], writeFd = result[2])
        }
    }
}
