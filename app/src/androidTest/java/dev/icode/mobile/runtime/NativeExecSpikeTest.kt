package dev.icode.mobile.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cycle 1 packaging spike.
 *
 * Proves the one assumption the whole Linux runtime rests on: that this app can
 * execute a packaged native binary on this device and Android version. Android
 * forbids executing anything from the app data directory, so the binary has to
 * run from the native library directory instead. If this test fails, the
 * runtime packaging approach has to change before any runtime code is written.
 */
@RunWith(AndroidJUnit4::class)
class NativeExecSpikeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nativeLibraryDir: String get() = context.applicationInfo.nativeLibraryDir

    @Test
    fun packagedBinaryIsExtractedToDiskAndExecutable() {
        val binary = File(nativeLibraryDir, BINARY_NAME)
        assertTrue(
            "Not extracted to $nativeLibraryDir — check packaging.jniLibs.useLegacyPackaging",
            binary.exists(),
        )
        assertTrue("Packaged binary is not executable: $binary", binary.canExecute())
    }

    @Test
    fun packagedBinaryExecutesFromNativeLibraryDirectory() {
        val binary = File(nativeLibraryDir, BINARY_NAME)
        val process = ProcessBuilder(binary.absolutePath, "spike")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertTrue("Process did not exit in time", process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals("Non-zero exit. Output: $output", 0, process.exitValue())
        assertTrue("Unexpected output: $output", output.contains("icode-exec-ok"))
        assertTrue("Arguments were not passed through. Output: $output", output.contains("arg1=spike"))
    }

    @Test
    fun dataDirectoryCopyIsRefusedByThePlatform() {
        // Documents *why* runtime executables may not be staged in app storage:
        // a copy in the data directory must not become runnable.
        val source = File(nativeLibraryDir, BINARY_NAME)
        val copy = File(context.filesDir, "exectest-copy")
        source.copyTo(copy, overwrite = true)
        copy.setExecutable(true)

        val refused = runCatching {
            val process = ProcessBuilder(copy.absolutePath).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            process.exitValue() != 0 || !output.contains("icode-exec-ok")
        }.getOrElse { true }

        copy.delete()
        assertTrue(
            "Executing from the data directory succeeded. The W^X assumption behind the " +
                "runtime packaging does not hold on this device — re-check the approach.",
            refused,
        )
    }

    private companion object {
        const val BINARY_NAME = "libexectest.so"
        const val TIMEOUT_SECONDS = 20L
    }
}
