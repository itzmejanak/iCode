package dev.icode.mobile.data.assistant.acp

import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.LoggerFactory
import org.slf4j.helpers.NOPLoggerFactory

/**
 * The ACP SDK, Koog, Ktor and the MCP SDK log through SLF4J, whose only provider in iCode is its no-op
 * one: what they would log — the protocol's messages, chats among them — goes nowhere, not to logcat
 * (docs/architecture.md). Another provider brought by a dependency, as Koog brings slf4j-simple, fails this.
 */
class AcpLoggingTest {
    @Test fun `the protocol's own logging goes nowhere`() {
        val factory = LoggerFactory.getILoggerFactory()
        assertTrue("SLF4J has a provider: ${factory.javaClass.name}", factory is NOPLoggerFactory)
    }
}
