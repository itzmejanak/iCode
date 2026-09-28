package dev.icode.mobile.data.runtime

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimePackageInstallerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val installed = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val running = AtomicInteger()
    private val mostAtOnce = AtomicInteger()

    @After fun stop() = scope.cancel()

    /** A fake apt: records each command; `install` succeeds for packages in [available]. */
    private fun installer(
        runtimeReady: Boolean = true,
        available: Set<String> = setOf("git", "ripgrep", "openssh-client"),
        listsFresh: Boolean = true,
        updateFails: Boolean = false,
        gate: CompletableDeferred<Unit>? = null,
    ): RuntimePackageInstaller {
        var fresh = listsFresh
        return RuntimePackageInstaller(scope, { runtimeReady }) { argv, onLine ->
            val now = running.incrementAndGet()
            mostAtOnce.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            try {
                calls += argv.drop(1).joinToString(" ")
                gate?.await()
                delay(20)
                when {
                    argv[1] == "update" && updateFails ->
                        GuestCommand.Result(100, "E: Failed to fetch http://ports.ubuntu.com  Temporary failure resolving", false)
                    argv[1] == "update" -> GuestCommand.Result(0, "", false).also { fresh = true }
                    fresh && argv.last() in available -> {
                        onLine("Setting up ${argv.last()} ...")
                        installed += argv.last()
                        GuestCommand.Result(0, "", false)
                    }
                    else -> GuestCommand.Result(100, "E: Unable to locate package ${argv.last()}", false)
                }
            } finally {
                running.decrementAndGet()
            }
        }
    }

    private suspend fun RuntimePackageInstaller.ensure(name: String) = ensure(name) { name in installed }

    @Test fun `an installed package needs no apt`() = runBlocking {
        installed += "git"
        assertEquals(RuntimePackageInstaller.Outcome.Ready, installer().ensure("git"))
        assertEquals(emptyList<String>(), calls)
    }

    @Test fun `without the runtime nothing is attempted`() = runBlocking {
        assertEquals(
            RuntimePackageInstaller.Outcome.Failed("Install the Linux runtime first"),
            installer(runtimeReady = false).ensure("git"),
        )
        assertEquals(emptyList<String>(), calls)
    }

    @Test fun `fresh package lists install in one step`() = runBlocking {
        assertEquals(RuntimePackageInstaller.Outcome.Ready, installer().ensure("ripgrep"))
        assertEquals(listOf("install -y --no-install-recommends ripgrep"), calls)
    }

    @Test fun `missing lists are read before trying again`() = runBlocking {
        assertEquals(RuntimePackageInstaller.Outcome.Ready, installer(listsFresh = false).ensure("git"))
        assertEquals(
            listOf("install -y --no-install-recommends git", "update", "install -y --no-install-recommends git"),
            calls,
        )
    }

    @Test fun `a failed update says what apt said`() = runBlocking {
        val outcome = installer(listsFresh = false, updateFails = true).ensure("git")
        assertEquals(
            RuntimePackageInstaller.Outcome.Failed(
                "Could not update runtime packages: E: Failed to fetch http://ports.ubuntu.com  Temporary failure resolving",
            ),
            outcome,
        )
    }

    @Test fun `an unknown package is reported by name`() = runBlocking {
        val outcome = installer(available = emptySet()).ensure("nope")
        assertEquals(RuntimePackageInstaller.Outcome.Failed("Could not install nope: E: Unable to locate package nope"), outcome)
    }

    @Test fun `requests for the same package share one install`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val packages = installer(gate = gate)
        val requests = List(3) { async { packages.ensure("git") } }
        delay(100)
        gate.complete(Unit)
        val outcomes = withTimeout(2_000) { requests.awaitAll() }
        assertEquals(List(3) { RuntimePackageInstaller.Outcome.Ready }, outcomes)
        assertEquals(listOf("install -y --no-install-recommends git"), calls)
    }

    @Test fun `different packages never run apt at the same time`() = runBlocking {
        val packages = installer()
        val outcomes = withTimeout(2_000) {
            listOf("git", "ripgrep", "openssh-client").map { name -> async { packages.ensure(name) } }.awaitAll()
        }
        assertEquals(List(3) { RuntimePackageInstaller.Outcome.Ready }, outcomes)
        assertEquals(1, mostAtOnce.get())
        assertEquals(3, calls.size)
    }

    @Test fun `a cancelled request does not stop the install`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val packages = installer(gate = gate)
        val request = launch { packages.ensure("ripgrep") }
        delay(100)
        request.cancelAndJoin()
        gate.complete(Unit)
        withTimeout(2_000) { while ("ripgrep" !in installed) delay(10) }
        assertEquals(RuntimePackageInstaller.Outcome.Ready, packages.ensure("ripgrep"))
        assertEquals(listOf("install -y --no-install-recommends ripgrep"), calls)
    }
}
