package dev.icode.mobile.ui.editor

import dev.icode.mobile.domain.FileTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class GrammarAssetsTest {
    private val shipped = ShippedGrammars.all.map { it.scope }.toSet()

    /**
     * A grammar that pulls in one the APK does not ship loses whatever that one
     * colours — YAML shipped without its YAML 1.2 grammar was plain for that
     * reason. So each include is either shipped or a language named here as not
     * shipped, and this list is exactly those (a stale name fails too).
     */
    @Test fun `every grammar a grammar pulls in is shipped, or named as not shipped`() {
        val missing = ShippedGrammars.all.flatMap { it.includes }.filter { it !in shipped }.toSet()
        assertEquals(NOT_SHIPPED, missing.toSortedSet())
    }

    @Test fun `every shipped grammar is opened by a file type or pulled in by another`() {
        val opened = FileTypes.rules.mapNotNull { it.type.grammar }.toSet()
        val included = ShippedGrammars.all.flatMap { it.includes }.toSet()
        assertEquals(emptySet<String>(), shipped - opened - included)
    }

    private companion object {
        /** Languages other grammars can embed (a Markdown fence, an assembly block in C) that iCode does not ship. */
        val NOT_SHIPPED = sortedSetOf(
            "source.asp.vb.net", "source.batchfile", "source.c", "source.clojure", "source.coffee",
            "source.cpp", "source.cs", "source.css.less", "source.css.scss", "source.dart",
            "source.diff", "source.dockerfile", "source.elixir", "source.erlang", "source.fsharp",
            "source.groovy", "source.ini", "source.js.regexp", "source.json.comments", "source.julia",
            "source.lua", "source.makefile", "source.objc", "source.perl", "source.perl.6",
            "source.php", "source.powershell", "source.r", "source.regexp.python", "source.ruby",
            "source.scala", "source.sql", "source.swift", "text.bibtex", "text.git-commit",
            "text.git-rebase", "text.html.derivative", "text.html.handlebars", "text.log", "text.pug",
            "text.tex.latex", "text.xml.xsl",
        )
    }
}
