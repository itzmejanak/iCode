package dev.icode.mobile.data.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalLaunchTest {
    @Test fun `bash starts as a login shell, others as they are`() {
        assertEquals(listOf("/bin/bash", "-l"), TerminalLaunch.shellCommand("/bin/bash"))
        assertEquals(listOf("/usr/bin/zsh"), TerminalLaunch.shellCommand("/usr/bin/zsh"))
    }
}
