package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** An agent's permission request answered where only Allow and Deny are shown. */
class AssistantApprovalTest {
    private val call = AssistantCall("c1", "execute", "{}", "Run rm -rf build", AssistantToolKind.Command)

    private fun asked(vararg options: AssistantOption) = AssistantApproval(call, call.summary, always = true, options = options.toList())

    @Test fun `Allow and Deny stand for the agent's answers for this time, not from now on`() {
        val approval = asked(
            AssistantOption("always", "Always Allow", allows = true, always = true),
            AssistantOption("allow", "Allow", allows = true),
            AssistantOption("reject", "Reject", allows = false),
        )
        assertEquals("allow", approval.optionFor(AssistantAnswer.Allow))
        assertEquals("allow", approval.optionFor(AssistantAnswer.AllowInChat))
        assertEquals("reject", approval.optionFor(AssistantAnswer.Deny))
    }

    @Test fun `with only a lasting answer that one is given, and with none of the kind the request is cancelled`() {
        val approval = asked(AssistantOption("always", "Always Allow", allows = true, always = true))
        assertEquals("always", approval.optionFor(AssistantAnswer.Allow))
        assertNull(approval.optionFor(AssistantAnswer.Deny))
    }
}
