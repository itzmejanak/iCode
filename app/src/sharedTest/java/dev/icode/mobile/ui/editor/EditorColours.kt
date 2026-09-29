package dev.icode.mobile.ui.editor

import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.SyntaxRole
import dev.icode.mobile.designsystem.syntaxColor
import dev.icode.mobile.domain.FileTypes
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.core.grammar.IStateStack
import org.eclipse.tm4e.core.internal.grammar.tokenattrs.EncodedTokenAttributes
import org.eclipse.tm4e.core.internal.theme.FontStyle
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IRegistryOptions
import org.eclipse.tm4e.core.registry.IThemeSource
import org.eclipse.tm4e.core.registry.Registry

/**
 * The editor's colours as a person sees them, shared by the JVM test and the
 * device test: each language's sample is tokenized by the editor's own TextMate
 * engine with the shipped grammars and the built theme, on each palette, and
 * each kind of token must come out in its role's colour.
 *
 * The phone runs the grammars on native Oniguruma, the engine they are written
 * for; the JVM has only joni, which rejects a few constructs, so expectations
 * marked [oniguruma] are checked on the device only.
 */
internal object EditorColours {
    class Expect(val text: String, val role: SyntaxRole, val needsOniguruma: Boolean = false)

    class Sample(val file: String, val lines: List<String>, val expect: List<Expect>)

    /** Every token that is not drawn in its role's colour and style, as a readable line each. */
    fun wrong(grammars: (scopeName: String) -> IGrammarSource?, oniguruma: Boolean): List<String> =
        listOf(IcodePalette.Dark, IcodePalette.Light, IcodePalette.Amoled).flatMap { palette ->
            val registry = Registry(object : IRegistryOptions {
                override fun getGrammarSource(scopeName: String): IGrammarSource? = grammars(scopeName)
            })
            registry.setTheme(IThemeSource.fromString(IThemeSource.ContentType.JSON, editorTheme(palette).toString()))
            SAMPLES.flatMap { sample ->
                val grammar = registry.loadGrammar(checkNotNull(FileTypes.of(sample.file).grammar) { sample.file })
                    ?: return@flatMap listOf("${sample.file}: grammar not loaded")
                val tokens = tokenize(grammar, sample.lines)
                sample.expect.filter { oniguruma || !it.needsOniguruma }.mapNotNull { expect ->
                    val drawn = tokens.at(sample.lines, expect.text) ?: return@mapNotNull "${sample.file}: \"${expect.text}\" not in the sample"
                    // As the editor draws it: colour 0 is the palette's text (DEFAULT_COLOR_ID).
                    val colour = if (drawn.foreground == 0) hex(palette.textPrimary.toArgb()) else registry.colorMap[drawn.foreground].uppercase()
                    val wanted = hex(palette.syntaxColor(expect.role).toArgb())
                    val styled = (!expect.role.bold || drawn.fontStyle and FontStyle.Bold != 0) &&
                        (!expect.role.italic || drawn.fontStyle and FontStyle.Italic != 0)
                    if (colour == wanted && styled) null
                    else "${palette.kind} ${sample.file}: \"${expect.text}\" is $colour (${drawn.scopes}), wanted ${expect.role} $wanted"
                }
            }
        }

    private class Drawn(val foreground: Int, val fontStyle: Int, val scopes: String)

    private class Tokens(val lines: List<List<Pair<Int, Drawn>>>) {
        /** The token drawing the first character of [text], found on the first line holding it. */
        fun at(sample: List<String>, text: String): Drawn? {
            val line = sample.indexOfFirst { text in it }.takeIf { it >= 0 } ?: return null
            val column = sample[line].indexOf(text)
            return lines[line].last { it.first <= column }.second
        }
    }

    private fun tokenize(grammar: IGrammar, lines: List<String>): Tokens {
        var state: IStateStack? = null
        return Tokens(lines.map { line ->
            val binary = grammar.tokenizeLine2(line, state, null)
            val named = grammar.tokenizeLine(line, state, null).tokens
            state = binary.ruleStack
            val raw = binary.tokens
            (raw.indices step 2).map { i ->
                val start = raw[i]
                val scopes = named.last { it.startIndex <= start }.scopes.drop(1).joinToString(" ")
                start to Drawn(EncodedTokenAttributes.getForeground(raw[i + 1]), EncodedTokenAttributes.getFontStyle(raw[i + 1]), scopes)
            }
        })
    }

    private fun hex(argb: Int) = "#%06X".format(argb and 0xFFFFFF)

    /** What joni cannot draw as Oniguruma does: CSS's selector rule uses a variable-length lookbehind joni rejects. */
    private val NEEDS_ONIGURUMA = setOf("a.css" to "a {")

    private fun sample(file: String, vararg lines: String, expect: () -> List<Pair<String, SyntaxRole>>) = Sample(
        file,
        lines.toList(),
        expect().map { (text, role) -> Expect(text, role, needsOniguruma = (file to text) in NEEDS_ONIGURUMA) },
    )

    val SAMPLES = listOf(
        sample("Main.kt", "fun main() { val n = 42 // note", "    println(\"hi\")") {
            listOf("fun" to SyntaxRole.Keyword, "42" to SyntaxRole.Number, "// note" to SyntaxRole.Comment, "\"hi\"" to SyntaxRole.String)
        },
        sample("A.java", "public class A { int n = 42; } // note", "String s = \"hi\";") {
            listOf("public" to SyntaxRole.Keyword, "class" to SyntaxRole.Keyword, "42" to SyntaxRole.Number, "// note" to SyntaxRole.Comment, "\"hi\"" to SyntaxRole.String)
        },
        sample("a.py", "def f(x):  # note", "    return \"hi\" + 42") {
            listOf("def" to SyntaxRole.Keyword, "return" to SyntaxRole.Control, "# note" to SyntaxRole.Comment, "\"hi\"" to SyntaxRole.String, "42" to SyntaxRole.Number)
        },
        sample("a.js", "const n = 42; // note", "if (n) f(\"hi\");") {
            listOf("const" to SyntaxRole.Keyword, "if" to SyntaxRole.Control, "42" to SyntaxRole.Number, "// note" to SyntaxRole.Comment, "\"hi\"" to SyntaxRole.String, "f(" to SyntaxRole.Function, "= 42" to SyntaxRole.Plain)
        },
        sample("a.jsx", "const el = <div className=\"x\">hi</div>;") {
            listOf("div" to SyntaxRole.Tag, "className" to SyntaxRole.Attribute, "\"x\"" to SyntaxRole.String)
        },
        sample("a.ts", "interface A { n: number } // note", "let s: string = \"hi\";") {
            listOf("interface" to SyntaxRole.Keyword, "number" to SyntaxRole.Type, "// note" to SyntaxRole.Comment, "\"hi\"" to SyntaxRole.String)
        },
        sample("a.tsx", "const el = <App title=\"x\" />;") {
            listOf("App" to SyntaxRole.Type, "title" to SyntaxRole.Attribute, "\"x\"" to SyntaxRole.String)
        },
        sample("a.html", "<!-- note -->", "<a href=\"x\">hi</a>") {
            listOf("<!-- note" to SyntaxRole.Comment, "a href" to SyntaxRole.Tag, "href" to SyntaxRole.Attribute, "\"x\"" to SyntaxRole.String)
        },
        sample("a.xml", "<!-- note -->", "<item id=\"1\">text</item>") {
            listOf("<!-- note" to SyntaxRole.Comment, "item id" to SyntaxRole.Tag, "id=" to SyntaxRole.Attribute, "\"1\"" to SyntaxRole.String)
        },
        sample("a.xsl", "<xsl:template match=\"/\">") {
            listOf("xsl:template" to SyntaxRole.Tag, "match" to SyntaxRole.Attribute, "\"/\"" to SyntaxRole.String)
        },
        sample("a.css", "/* note */", "a { color: red; margin: 4px; }") {
            listOf("/* note" to SyntaxRole.Comment, "a {" to SyntaxRole.Tag, "color" to SyntaxRole.Attribute, "red" to SyntaxRole.Constant, "4" to SyntaxRole.Number)
        },
        sample("a.scss", "// note", ".a { color: \$gap; }") {
            listOf("// note" to SyntaxRole.Comment, ".a" to SyntaxRole.Tag, "color" to SyntaxRole.Attribute)
        },
        sample("a.less", "// note", ".a { color: @gap; }") {
            listOf("// note" to SyntaxRole.Comment, ".a" to SyntaxRole.Tag, "color" to SyntaxRole.Attribute)
        },
        sample("a.sh", "#!/usr/bin/env bash", "# note", "echo \"hi\"", "if [ -n \"\$x\" ]; then exit 1; fi") {
            listOf("#!/usr" to SyntaxRole.Comment, "# note" to SyntaxRole.Comment, "echo" to SyntaxRole.Function, "\"hi\"" to SyntaxRole.String, "if" to SyntaxRole.Control)
        },
        sample("a.cmd", "REM note", "set X=1") {
            // As VS Code draws it: REM is a keyword inside its comment.
            listOf("REM" to SyntaxRole.Keyword, " note" to SyntaxRole.Comment, "set" to SyntaxRole.Keyword)
        },
        sample("a.rs", "fn main() { let n = 42; } // note", "let s = \"hi\";") {
            listOf("fn" to SyntaxRole.Keyword, "42" to SyntaxRole.Number, "// note" to SyntaxRole.Comment, "\"hi\"" to SyntaxRole.String)
        },
        sample("a.go", "func main() { x := 42 } // note", "var s = \"hi\"") {
            listOf("func" to SyntaxRole.Keyword, "42" to SyntaxRole.Number, "// note" to SyntaxRole.Comment, "\"hi\"" to SyntaxRole.String)
        },
        sample("a.c", "#include <stdio.h>", "int main(void) { return 42; } // note") {
            listOf("int" to SyntaxRole.Keyword, "return" to SyntaxRole.Control, "42" to SyntaxRole.Number, "// note" to SyntaxRole.Comment, "<stdio.h>" to SyntaxRole.String)
        },
        sample("a.cpp", "class A { public: int n = 42; }; // note", "auto s = \"hi\";") {
            listOf("class" to SyntaxRole.Keyword, "42" to SyntaxRole.Number, "// note" to SyntaxRole.Comment, "\"hi\"" to SyntaxRole.String)
        },
        sample("A.cs", "class A { public int N = 42; } // note", "var s = \"hi\";") {
            listOf("class" to SyntaxRole.Keyword, "42" to SyntaxRole.Number, "// note" to SyntaxRole.Comment, "\"hi\"" to SyntaxRole.String)
        },
        sample("a.php", "<?php // note", "\$x = \"hi\";") {
            listOf("// note" to SyntaxRole.Comment, "\$x" to SyntaxRole.Variable, "\"hi\"" to SyntaxRole.String)
        },
        sample("a.rb", "# note", "def f(x) = \"hi\"", "puts 42") {
            listOf("# note" to SyntaxRole.Comment, "def" to SyntaxRole.Control, "\"hi\"" to SyntaxRole.String, "42" to SyntaxRole.Number)
        },
        sample("a.swift", "func f() -> Int { return 42 } // note", "let s = \"hi\"") {
            listOf("func" to SyntaxRole.Keyword, "42" to SyntaxRole.Number, "// note" to SyntaxRole.Comment, "\"hi\"" to SyntaxRole.String)
        },
        sample("a.dart", "void main() { var s = \"hi\"; } // note") {
            listOf("void" to SyntaxRole.Keyword, "// note" to SyntaxRole.Comment, "\"hi\"" to SyntaxRole.String)
        },
        sample("a.lua", "-- note", "local x = \"hi\"", "function f() return 42 end") {
            listOf("-- note" to SyntaxRole.Comment, "local" to SyntaxRole.Keyword, "\"hi\"" to SyntaxRole.String, "42" to SyntaxRole.Number)
        },
        sample("build.gradle", "def x = \"hi\" // note") {
            listOf("def" to SyntaxRole.Keyword, "\"hi\"" to SyntaxRole.String, "// note" to SyntaxRole.Comment)
        },
        sample("a.sql", "-- note", "SELECT name FROM users WHERE id = 42;") {
            listOf("-- note" to SyntaxRole.Comment, "SELECT" to SyntaxRole.Keyword, "42" to SyntaxRole.Number)
        },
        sample("Dockerfile", "# note", "FROM ubuntu:24.04", "RUN echo \"hi\"") {
            listOf("# note" to SyntaxRole.Comment, "FROM" to SyntaxRole.Keyword, "\"hi\"" to SyntaxRole.String)
        },
        sample("Makefile", "# note", "all: build") {
            listOf("# note" to SyntaxRole.Comment, "all" to SyntaxRole.Function)
        },
        sample("a.json", "{\"key\": \"value\", \"n\": 42, \"b\": true}") {
            listOf("\"key\"" to SyntaxRole.Attribute, "\"value\"" to SyntaxRole.String, "42" to SyntaxRole.Number, "true" to SyntaxRole.Keyword)
        },
        sample("a.jsonl", "{\"key\": \"value\", \"n\": 42}") {
            listOf("\"key\"" to SyntaxRole.Attribute, "\"value\"" to SyntaxRole.String, "42" to SyntaxRole.Number)
        },
        sample("a.yaml", "# note", "key: value", "n: 42") {
            listOf("# note" to SyntaxRole.Comment, "key" to SyntaxRole.Attribute, "value" to SyntaxRole.String, "42" to SyntaxRole.Number)
        },
        sample("a.ini", "; note", "[section]", "key=value") {
            listOf("; note" to SyntaxRole.Comment, "section" to SyntaxRole.Heading, "key" to SyntaxRole.Attribute)
        },
        sample("gradle.properties", "# note", "key=value") {
            listOf("# note" to SyntaxRole.Comment, "key" to SyntaxRole.Attribute)
        },
        sample(".env", "# note", "KEY=\"value\"") {
            listOf("# note" to SyntaxRole.Comment, "KEY" to SyntaxRole.Attribute, "\"value\"" to SyntaxRole.String)
        },
        sample(".gitignore", "# note", "build/") {
            listOf("# note" to SyntaxRole.Comment)
        },
        sample("a.md", "# Title", "**bold** and __strong__, *it* and _em_ and `code`", "- item", "[link](http://x)") {
            listOf("Title" to SyntaxRole.Heading, "bold" to SyntaxRole.Strong, "strong" to SyntaxRole.Strong, "it*" to SyntaxRole.Emphasis, "em_" to SyntaxRole.Emphasis, "code" to SyntaxRole.String, "- " to SyntaxRole.ListMarker, "link" to SyntaxRole.String, "http" to SyntaxRole.Link)
        },
        sample("a.diff", "--- a/x", "+++ b/x", "@@ -1 +1 @@", "-old", "+new") {
            listOf("-old" to SyntaxRole.Deleted, "+new" to SyntaxRole.Inserted)
        },
        sample("COMMIT_EDITMSG", "Subject", "", "# note") {
            listOf("# note" to SyntaxRole.Comment)
        },
        sample("git-rebase-todo", "pick abc1234 message") {
            listOf("abc1234" to SyntaxRole.Number)
        },
    )
}
