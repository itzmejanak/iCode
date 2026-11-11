package dev.icode.mobile.ui.syntax

import dev.icode.mobile.designsystem.SyntaxRole
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.syntax.Language
import dev.icode.mobile.ui.editor.ShippedGrammars
import java.io.File
import java.time.Duration
import org.eclipse.tm4e.core.grammar.IStateStack
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Each shipped language colours its sample fully (docs/syntax-highlighting-plan.md A10): one sample per
 * language that opens files, in src/test/resources/syntax-samples (iCode's own in icode/, else Shiki's,
 * which tools/import-grammars.py writes to shiki/), tokenized on the JVM as the editor tokenizes and
 * resolved by the same [SyntaxRoles]. A word left without a role is a gap, unless it is a name the code
 * chose (plain in VS Code's themes too, where the grammar does not call it a variable), or the language's
 * free text is prose, or [SyntaxSamples.EXCEPTIONS] names it with its reason. A name the sample colours
 * as a keyword elsewhere is a gap too: the grammar lost its place.
 */
class SyntaxSamplesTest {
    private class Word(val language: String, val line: Int, val word: String, val token: String, val scopes: List<String>) {
        override fun toString() = "$language:$line `$word` in `${token.trim()}` (${scopes.drop(1).joinToString(" ")})"
    }

    private val folder = File("src/test/resources/syntax-samples")

    private val samples: Map<String, File> by lazy {
        val own = File(folder, "icode").listFiles().orEmpty().associateBy { it.name.substringBeforeLast('.') }
        val shiki = File(folder, "shiki").listFiles().orEmpty().associateBy { it.name.substringBeforeLast('.') }
        shiki + own
    }

    /** The languages that open files: each needs a sample. */
    private val opening: List<Language> by lazy {
        val opened = FileTypes.rules.filter { it.extensions.isNotEmpty() || it.names.isNotEmpty() || it.prefixes.isNotEmpty() || it.firstLine != null }
            .mapNotNull { it.type.language }.toSet()
        ShippedGrammars.catalog.all.filter { it.id in opened }
    }

    /** Every word the samples leave without a role and that no rule allows: the gaps. */
    private val gaps: List<Word> by lazy {
        opening.filter { it.id in samples }.flatMap { language ->
            val grammar = ShippedGrammars.load(ShippedGrammars.registry(), language.scope)
            val keywords = mutableSetOf<String>()
            val plain = mutableListOf<Word>()
            var state: IStateStack? = null
            samples.getValue(language.id).readText().replace("\r\n", "\n").split('\n').forEachIndexed { index, line ->
                val result = grammar.tokenizeLine(line, state, Duration.ofSeconds(LINE_SECONDS))
                state = result.ruleStack
                result.tokens.forEach { token ->
                    val text = line.substring(minOf(token.startIndex, line.length), minOf(token.endIndex, line.length))
                    val role = SyntaxRoles.shipped.named(token.scopes)
                    if (role == SyntaxRole.Keyword || role == SyntaxRole.Control) keywords += text.trim()
                    if (role == null) WORD.findAll(text).forEach { plain += Word(language.id, index + 1, it.value, text, token.scopes) }
                }
            }
            plain.filter { word ->
                val name = NAME.matches(word.word)
                when {
                    language.id in SyntaxSamples.PROSE -> false
                    name && word.word !in keywords -> false
                    else -> true
                }
            }
        }
    }

    /** Each line's tokens of [id]'s sample, as `line text` to the role a rule gives it (the first, where a text repeats). */
    private fun roles(id: String): Map<String, SyntaxRole?> {
        val grammar = ShippedGrammars.load(ShippedGrammars.registry(), ShippedGrammars.catalog.byId(id)!!.scope)
        val roles = mutableMapOf<String, SyntaxRole?>()
        var state: IStateStack? = null
        samples.getValue(id).readLines().forEachIndexed { index, line ->
            val result = grammar.tokenizeLine(line, state, null)
            state = result.ruleStack
            result.tokens.forEach { token ->
                val text = line.substring(minOf(token.startIndex, line.length), minOf(token.endIndex, line.length)).trim()
                roles.putIfAbsent("${index + 1} $text", SyntaxRoles.shipped.named(token.scopes))
            }
        }
        return roles
    }

    @Test fun `a Dockerfile colours its image, stage, keys, values, flags, ports, variables and shell commands`() {
        val expected = mapOf(
            "3 BASE" to SyntaxRole.Attribute, "3 ubuntu:24.04" to SyntaxRole.String,
            "4 --platform" to SyntaxRole.Keyword, "4 BUILDPLATFORM" to SyntaxRole.Variable, "4 golang" to SyntaxRole.Type,
            "4 1.22-alpine" to SyntaxRole.Constant, "4 AS" to SyntaxRole.Keyword, "4 build" to SyntaxRole.Type,
            "5 VERSION" to SyntaxRole.Attribute, "5 1.0" to SyntaxRole.Number,
            "6 APP_HOME" to SyntaxRole.Attribute, "6 /app" to SyntaxRole.String, "7 false" to SyntaxRole.Keyword,
            "9 APP_HOME" to SyntaxRole.Variable, "10 --chown" to SyntaxRole.Keyword, "10 build" to SyntaxRole.Type,
            "12 --mount" to SyntaxRole.Keyword, "12 apt-get" to SyntaxRole.Function, "12 update" to SyntaxRole.String,
            "15 echo" to SyntaxRole.Function, "17 8080" to SyntaxRole.Number, "17 tcp" to SyntaxRole.Keyword,
            "17 9090" to SyntaxRole.Number, "21 30s" to SyntaxRole.Number, "21 curl" to SyntaxRole.Function,
            "22 ./server" to SyntaxRole.String, "23 exec" to SyntaxRole.Function,
            "24 org.opencontainers.image.source" to SyntaxRole.Attribute, "26 scratch" to SyntaxRole.Type,
        )
        val roles = roles("dockerfile")
        assertEquals(expected, expected.mapValues { roles[it.key] })
    }

    @Test fun `a dotenv file colours its keys, values, numbers, booleans, variables and comments`() {
        val expected = mapOf(
            "1 Database settings" to SyntaxRole.Comment, "2 export" to SyntaxRole.Keyword, "2 DATABASE_URL" to SyntaxRole.Attribute,
            "3 localhost" to SyntaxRole.String, "4 5432" to SyntaxRole.Number, "5 true" to SyntaxRole.Keyword,
            "6 False" to SyntaxRole.Keyword, "7 0.75" to SyntaxRole.Number, "8 literal \$NOT_EXPANDED" to SyntaxRole.String,
            "9 \\n" to SyntaxRole.Escape, "9 USER" to SyntaxRole.Variable, "9 friend" to SyntaxRole.String,
            "9 HOME" to SyntaxRole.Variable, "11 trailing comment" to SyntaxRole.Comment, "14 abc123" to SyntaxRole.String,
        )
        val roles = roles("dotenv")
        assertEquals(expected, expected.mapValues { roles[it.key] })
    }

    @Test fun `every language that opens files has one sample`() {
        assertEquals(emptyList<String>(), opening.map { it.id }.filter { it !in samples })
        assertEquals(emptySet<String>(), samples.keys - opening.map { it.id }.toSet())
    }

    @Test fun `every sample's words have a role, or a reason they have none`() {
        val missing = gaps.filter { word -> SyntaxSamples.EXCEPTIONS[word.language]?.second?.contains(word.word) != true }
        assertEquals("", missing.joinToString("\n"))
    }

    @Test fun `every exception is still needed`() {
        val found = gaps.groupBy({ it.language }, { it.word })
        val stale = SyntaxSamples.EXCEPTIONS.flatMap { (language, exception) ->
            exception.second.filter { it !in found[language].orEmpty() }.map { "$language `$it`" }
        }
        assertEquals(emptyList<String>(), stale)
        assertEquals(emptySet<String>(), SyntaxSamples.PROSE.keys - samples.keys)
    }

    private companion object {
        /** Letters or digits, with what names are written with: a word. */
        val WORD = Regex("[\\p{L}\\p{N}_$]*[\\p{L}\\p{N}][\\p{L}\\p{N}_$]*")

        /** A word that is a name (`count`, `_tmp`, `$name`), not a number. */
        val NAME = Regex("[\\p{L}_$][\\p{L}\\p{N}_$]*")

        /** Joni compiles each of a line's patterns once; a slow one stops the line, not the test. */
        const val LINE_SECONDS = 5L
    }
}
