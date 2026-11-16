package dev.icode.mobile.ui.editor

import java.io.File
import java.time.Duration
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.core.grammar.IStateStack
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Syntax performance measurements (docs/editor-and-language-services.md): grammar load time per shipped
 * scope, tokenize time for large files and for a 1 MB minified line, and whether a grammar shared by
 * two threads fails. Not part of the normal run; run it with
 *
 *     ./gradlew :app:testDebugUnitTest --tests 'dev.icode.mobile.ui.editor.SyntaxBenchmark' -Picode.benchmark=true
 *
 * The report is printed and written to app/build/reports/syntax-benchmark.txt. These are JVM
 * numbers with tm4e's joni regex engine; the phone runs native Oniguruma.
 */
class SyntaxBenchmark {
    private val report = StringBuilder()

    @Test fun measure() {
        assumeTrue("set -Picode.benchmark=true to run", System.getProperty("icode.benchmark") == "true")
        line("JVM ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}, joni regex engine")
        loads()
        files()
        minified()
        shared()
        File("build/reports").mkdirs()
        File("build/reports/syntax-benchmark.txt").writeText(report.toString())
        println(report)
    }

    /** Parse (loadGrammar, with its include closure) and first line (root rules compiled), each cold. */
    private fun loads() {
        val scopes = ShippedGrammars.files.keys
        repeat(2) { scopes.forEach { coldLoad(it) } } // JVM warm-up
        line("\n## Grammar load, cold registry each time, median of 5 (ms): parse + first line")
        var parse = 0.0
        var first = 0.0
        scopes.map { scope -> scope to (1..5).map { coldLoad(scope) } }
            .sortedByDescending { (_, runs) -> runs.map { it.first + it.second }.median() }
            .forEach { (scope, runs) ->
                val p = runs.map { it.first }.median()
                val f = runs.map { it.second }.median()
                parse += p
                first += f
                line("%-28s %7.1f + %6.1f".format(scope, p, f))
            }
        line("sum of medians: parse %.0f ms, first line %.0f ms".format(parse, first))
        val all = (1..5).map {
            val registry = ShippedGrammars.registry()
            millis { scopes.forEach { ShippedGrammars.load(registry, it) } }
        }.median()
        line("all ${scopes.size} into one registry, as the app did at start before S3: %.0f ms (median of 5)".format(all))
    }

    private fun coldLoad(scope: String): Pair<Double, Double> {
        val registry = ShippedGrammars.registry()
        lateinit var grammar: IGrammar
        val parse = millis { grammar = ShippedGrammars.load(registry, scope) }
        return parse to millis { grammar.tokenizeLine("") }
    }

    private fun files() {
        line("\n## Large files, line by line with state carried (ms)")
        for ((scope, name, lines) in listOf(
            Triple("source.kotlin", "Kotlin 10 000 lines", kotlinLines(10_000)),
            Triple("source.json", "JSON 10 000 lines", jsonLines(10_000)),
            Triple("source.kotlin", "Kotlin 50 000 lines", kotlinLines(50_000)),
        )) {
            val chars = lines.sumOf { it.length + 1 }
            val cold = ShippedGrammars.load(ShippedGrammars.registry(), scope)
            val first = millis { tokenizeAll(cold, lines) }
            val grammar = ShippedGrammars.load(ShippedGrammars.registry(), scope)
            tokenizeAll(grammar, lines)
            val warm = (1..3).map { millis { tokenizeAll(grammar, lines) } }.median()
            val slowest = tokenizeAll(grammar, lines)
            line(
                "%-20s %8d chars: cold grammar %6.0f, warm %6.0f (%.1f µs a line, %.0f chars a ms), slowest line %.2f"
                    .format(name, chars, first, warm, warm * 1000 / lines.size, chars / warm, slowest),
            )
        }
    }

    /** Tokenizes every line; returns the slowest line's time in ms. */
    private fun tokenizeAll(grammar: IGrammar, lines: List<String>): Double {
        var state: IStateStack? = null
        var slowest = 0.0
        for (line in lines) {
            val start = System.nanoTime()
            state = grammar.tokenizeLine2(line, state, null).ruleStack
            slowest = maxOf(slowest, (System.nanoTime() - start) / 1e6)
        }
        return slowest
    }

    private fun minified() {
        line("\n## One minified line of 1 MiB")
        for ((scope, text) in listOf("source.js" to minifiedJs(), "source.json" to minifiedJson())) {
            val grammar = ShippedGrammars.load(ShippedGrammars.registry(), scope)
            grammar.tokenizeLine2(text.take(20_000), null, null) // warm
            for (length in listOf(10_000, 20_000, 100_000)) {
                val ms = (1..3).map { millis { grammar.tokenizeLine2(text.take(length), null, null) } }.median()
                line("$scope first %7d chars, no time limit: %7.1f ms".format(length, ms))
            }
            for (length in listOf(10_000, 20_000)) {
                val cut = text.take(length)
                val ms = millis { grammar.tokenizeLine2(cut, null, Duration.ofMillis(100)) }
                val result = grammar.tokenizeLine2(cut, null, Duration.ofMillis(100))
                line("$scope cut at %6d chars, limit 100 ms: took %6.1f ms, isStoppedEarly=${result.isStoppedEarly}".format(length, ms))
            }
            // A 1 ms limit stops after the first scan, so this is what one scan of the whole line costs.
            line("$scope one scan of all %7d chars: %7.1f ms".format(text.length, millis { grammar.tokenizeLine2(text, null, Duration.ofMillis(1)) }))
            for (limit in listOf(Duration.ofMillis(100), Duration.ofSeconds(2))) {
                lateinit var stoppedEarly: String
                val ms = millis {
                    val result = grammar.tokenizeLine2(text, null, limit)
                    val tokens = result.tokens
                    val reached = if (tokens.size >= 2) tokens[tokens.size - 2] else 0
                    stoppedEarly = "isStoppedEarly=${result.isStoppedEarly}, last token at char $reached"
                }
                line("$scope all %7d chars, limit %5d ms: took %7.1f ms, %s".format(text.length, limit.toMillis(), ms, stoppedEarly))
            }
        }
    }

    private fun shared() {
        line("\n## Two threads, one grammar (G8): 20 cold rounds x 2 threads x 10 documents")
        for (scope in GrammarThreadsTest.SAMPLES.keys) {
            val race = GrammarThreadsTest.race(scope, shared = true, rounds = 20, repeats = 10)
            line("$scope: ${race.wrong} of ${race.runs} documents wrong, ${race.errors.size} threads threw ${race.errors.distinct()}")
        }
    }

    private fun kotlinLines(count: Int) = generateSequence(0) { it + 1 }.flatMap { i ->
        sequenceOf(
            "/** Holds item $i. */",
            "data class Item$i(val id: Int = $i, val name: String = \"item-\$id\") : Base<Item$i>() {",
            "    override fun render(scale: Double): String = when {",
            "        scale > 1.5 -> name.uppercase() // large",
            "        else -> \"\${name.take(3)}/\${id * 2}\"",
            "    }",
            "}",
            "",
        )
    }.take(count).toList()

    private fun jsonLines(count: Int) = listOf("[") + generateSequence(0) { it + 1 }.flatMap { i ->
        sequenceOf("  {", "    \"id\": $i,", "    \"name\": \"item $i\",", "    \"tags\": [\"a\", \"b\"],", "    \"ok\": true,", "    \"v\": 1.5e3", "  },")
    }.take(count - 2).toList() + listOf("]")

    private fun minifiedJs() = buildString {
        var i = 0
        while (length < MIB) append("function a$i(b,c){return b+c*2>3?\"s$i\":[1,2,{k:'v'}]};var x$i=/re[a-z]+/g;").also { i++ }
    }

    private fun minifiedJson() = buildString {
        append('[')
        var i = 0
        while (length < MIB) append("{\"id\":$i,\"name\":\"n$i\",\"tags\":[\"a\",\"b\"],\"ok\":true,\"v\":1.5e3},").also { i++ }
        append("{}]")
    }

    private fun line(text: String) {
        report.append(text).append('\n')
    }

    private inline fun millis(block: () -> Unit): Double {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1e6
    }

    private fun List<Double>.median() = sorted()[size / 2]

    private companion object {
        const val MIB = 1024 * 1024
    }
}
