package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CommitMessageRulesTest {
    @Test fun `the request names the repository's style, the files and the diff`() {
        assertEquals(
            "The repository's recent commit subjects, newest first:\n- feat(auth): add login screen\n- fix: trim names\n\n" +
                "The files staged:\n12\t3\tsrc/Login.kt\n\nThe staged diff:\ndiff --git a/src/Login.kt b/src/Login.kt",
            CommitMessageRules.request(listOf("feat(auth): add login screen", "fix: trim names"), "12\t3\tsrc/Login.kt\n", "diff --git a/src/Login.kt b/src/Login.kt\n"),
        )
        assertEquals(
            "This repository has no commits yet.\n\nThe files staged:\n1\t0\tREADME.md\n\nThe staged diff:\n+# App",
            CommitMessageRules.request(emptyList(), "1\t0\tREADME.md", "+# App"),
        )
    }

    @Test fun `the message is what the model wrote, without thinking, a code block or quotes around it`() {
        assertEquals("feat(auth): add login validation", CommitMessageRules.cleaned("  feat(auth): add login validation\n"))
        assertEquals("fix: trim names", CommitMessageRules.cleaned("<think>\nThe diff trims names.\n</think>\n\nfix: trim names"))
        assertEquals("fix: trim names\n\nNames had trailing spaces.", CommitMessageRules.cleaned("```text\nfix: trim names\n\nNames had trailing spaces.\n```"))
        assertEquals("fix: trim names", CommitMessageRules.cleaned("```\nfix: trim names\n```"))
        assertEquals("fix: trim names", CommitMessageRules.cleaned("\"fix: trim names\""))
        assertEquals("fix: trim names", CommitMessageRules.cleaned("`fix: trim names`"))
        // Quotes that belong to the message stay.
        assertEquals("`a` renamed to `b`", CommitMessageRules.cleaned("`a` renamed to `b`"))
        assertEquals("\"", CommitMessageRules.cleaned("\""))
        assertEquals("", CommitMessageRules.cleaned("<think>only thinking</think>"))
    }
}
