package dev.icode.mobile.domain.markdown

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownLinksTest {
    private val file = MarkdownPlace.ofFile("/w/app/docs/guide.md", root = "/w/app")
    private val answer = MarkdownPlace.ofAnswer("/w/app")

    @Test fun `a file's links start from its folder, an answer's from its workspace`() {
        assertEquals(MarkdownPlace(folder = "/w/app/docs", root = "/w/app", runtimeRoot = "/workspace/app", slashFromRoot = true), file)
        assertEquals(MarkdownPlace(folder = "/w/app", root = "/w/app", runtimeRoot = "/workspace/app", slashFromRoot = false), answer)
        assertNull(MarkdownPlace.ofFile("/w/other/a.md", root = "/w/app"))
        assertNull(MarkdownPlace.ofFile("/w/app/a.md", root = null))
        assertNull(MarkdownPlace.ofAnswer(null))
    }

    @Test fun `slugs as GitHub makes them`() {
        assertEquals("time-and-work--wage", MarkdownLinks.slug("Time and Work — Wage"))
        assertEquals("whats-new-in-v20", MarkdownLinks.slug("What's new in v2.0?"))
        assertEquals("über_größe-1", MarkdownLinks.slug("Über_Größe 1"))
        val taken = mutableMapOf<String, Int>()
        assertEquals(listOf("a", "a-1", "a-2", "b"), listOf("a", "a", "a", "b").map { MarkdownLinks.unique(it, taken) })
    }

    @Test fun `reference labels match as CommonMark matches them`() {
        assertEquals("foo bar", MarkdownLinks.label("  Foo \n  BAR "))
    }

    @Test fun `web links, anchors and what is not followed`() {
        assertEquals(MarkdownTarget.Web("https://a.b/c"), MarkdownLinks.target("https://a.b/c", file))
        assertEquals(MarkdownTarget.Web("mailto:me@x.com"), MarkdownLinks.target("mailto:me@x.com", null))
        assertEquals(MarkdownTarget.Anchor("set up"), MarkdownLinks.target("#set%20up", file))
        listOf("javascript:alert(1)", "file:///etc/passwd", "intent://x", "  ").forEach { assertNull(it, MarkdownLinks.target(it, file)) }
    }

    @Test fun `paths resolve in the workspace and never out of it`() {
        assertEquals(MarkdownTarget.File("/w/app/docs/guide.md", 10), MarkdownLinks.target("./guide.md#L10", file))
        assertEquals(MarkdownTarget.File("/w/app/src/My File.kt", 3), MarkdownLinks.target("../src/My%20File.kt#L3-L9", file))
        assertEquals(MarkdownTarget.File("/w/app/README.md", null), MarkdownLinks.target("/README.md", file))
        assertEquals(MarkdownTarget.File("/w/app/src/A.kt", null), MarkdownLinks.target("/workspace/app/src/A.kt", answer))
        assertEquals(MarkdownTarget.File("/w/app/src/A.kt", null), MarkdownLinks.target("src/A.kt#section", answer))
        assertNull(MarkdownLinks.target("/etc/hosts", answer))
        assertNull(MarkdownLinks.target("../../outside.md", file))
        assertNull(MarkdownLinks.target("src/A.kt", null))
    }

    @Test fun `images local, inline and remote, never http or svg`() {
        assertEquals(MarkdownImageSource.Local("/w/app/docs/img/a.png"), MarkdownLinks.image("img/a.png", file))
        assertEquals(MarkdownImageSource.Remote("https://x.org/a.png"), MarkdownLinks.image("https://x.org/a.png", file))
        assertArrayEquals(byteArrayOf(1, 2, 3), (MarkdownLinks.image("data:image/png;base64,AQID", null) as MarkdownImageSource.Inline).bytes)
        listOf("http://x.org/a.png", "logo.svg", "https://x.org/badge.svg?style=flat", "data:image/svg+xml;base64,AQID", "data:text/html;base64,AQID", "../../a.png")
            .forEach { assertNull(it, MarkdownLinks.image(it, file)) }
    }
}
