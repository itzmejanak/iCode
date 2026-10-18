package dev.icode.mobile.data.runtime

import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch

/** A process a test drives: it prints what [say] writes, ends with [exit], and dies by a signal it is sent unless told otherwise. */
internal class FakeSpawnedProcess(private val diesBy: Set<Int> = setOf(PosixSignal.TERM, PosixSignal.KILL)) : SpawnedProcess {
    private val writer = PipedOutputStream()
    override val output = PipedInputStream(writer, 64 * 1024)
    override val input = ByteArrayOutputStream()
    val signals: MutableList<Int> = Collections.synchronizedList(mutableListOf())
    private val ended = CountDownLatch(1)
    @Volatile private var code = 0

    fun say(text: String) {
        writer.write(text.toByteArray())
        writer.flush()
    }

    fun exit(status: Int) {
        code = status
        writer.close()
        ended.countDown()
    }

    override fun signal(number: Int) {
        signals += number
        if (number in diesBy && ended.count > 0) exit(128 + number)
    }

    override fun waitFor(): Int {
        ended.await()
        return code
    }

    override fun close() = Unit
}
