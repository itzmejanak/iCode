package dev.icode.mobile.data.assistant.acp

import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.LoggerFactory
import org.slf4j.helpers.NOPLoggerFactory

/**
 * The ACP SDK logs through SLF4J, which iCode gives no provider: what it would log — the protocol's
 * messages, chats among them — goes nowhere (docs/architecture.md). A provider added later fails this.
 */
class AcpLoggingTest {
    @Test fun `the protocol's own logging goes nowhere`() {
        val factory = LoggerFactory.getILoggerFactory()
        assertTrue("SLF4J has a provider: ${factory.javaClass.name}", factory is NOPLoggerFactory)
    }
}
