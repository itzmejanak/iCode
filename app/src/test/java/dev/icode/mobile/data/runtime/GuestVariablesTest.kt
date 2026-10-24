package dev.icode.mobile.data.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GuestVariablesTest {
    private val profile = mapOf(
        "PATH" to "/root/.nvm/versions/node/v22/bin:/usr/bin",
        "LANG" to "en_GB.UTF-8",
        "LD_LIBRARY_PATH" to "/opt/lib",
        "https_proxy" to "http://proxy:3128",
        "TERM" to "xterm",
    )

    @Test fun `every context starts from what a Linux login sets, and is named`() {
        GuestContext.entries.forEach { context ->
            val variables = GuestVariables.of(context, "/usr/bin/zsh")
            assertEquals("/root", variables["HOME"])
            assertEquals("root", variables["USER"])
            assertEquals("root", variables["LOGNAME"])
            assertEquals("/usr/bin/zsh", variables["SHELL"])
            assertEquals(RuntimeLayout.GUEST_PATH, variables["PATH"])
            assertEquals("glibc.pthread.rseq=0", variables["GLIBC_TUNABLES"])
            assertEquals(context.id, variables[GuestVariables.CONTEXT])
        }
    }

    @Test fun `the profile is over the base, and the context's own over both`() {
        val command = GuestVariables.of(GuestContext.Command, "/bin/bash", profile)
        assertEquals(profile["PATH"], command["PATH"])
        assertEquals("en_GB.UTF-8", command["LANG"])
        assertEquals("/opt/lib", command["LD_LIBRARY_PATH"])
        assertEquals("http://proxy:3128", command["https_proxy"])
        // Nothing is attached: whatever the profile exports, programs are told so.
        assertEquals("dumb", command["TERM"])
        assertEquals("cat", command["PAGER"])
        assertEquals("0", command["GIT_TERMINAL_PROMPT"])
    }

    @Test fun `contexts without a terminal differ in their name alone`() {
        val command = GuestVariables.of(GuestContext.Command, "/bin/bash", profile)
        listOf(GuestContext.Git, GuestContext.LanguageServer, GuestContext.Search).forEach { context ->
            assertEquals(command - GuestVariables.CONTEXT, GuestVariables.of(context, "/bin/bash", profile) - GuestVariables.CONTEXT)
        }
    }

    @Test fun `a terminal is a terminal, and the install context has the base alone`() {
        val terminal = GuestVariables.of(GuestContext.Terminal, "/bin/bash")
        assertEquals("xterm-256color", terminal["TERM"])
        assertEquals("truecolor", terminal["COLORTERM"])
        assertFalse("PAGER" in terminal)
        assertEquals(GuestVariables.base("/bin/bash") + (GuestVariables.CONTEXT to "install"), GuestVariables.of(GuestContext.Install, "/bin/bash"))
    }
}
