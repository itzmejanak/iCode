package dev.icode.mobile.ui.editor

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Gap G8: can two threads tokenize with one tm4e grammar? They cannot. tm4e 0.24.6 keeps
 * unsynchronized state in a grammar: its rule table is a HashMap, each rule caches its compiled
 * patterns, an end pattern with back-references is rewritten in that cache on every line it
 * opens, and on the JVM each joni regex caches its last search. A shared grammar gives wrong
 * scopes and throws, cold or warm; [SyntaxBenchmark] counts how often. What is safe, and checked
 * here, is one grammar instance per thread: each thread's own registry, loaded cold.
 */
class GrammarThreadsTest {
    @Test fun `two threads, each with its own grammar, tokenize as one thread does`() {
        val wrong = SAMPLES.keys.map { scope -> scope to race(scope, shared = false, rounds = 6, repeats = 5) }
            .filter { (_, race) -> race.wrong > 0 || race.errors.isNotEmpty() }
            .map { (scope, race) -> "$scope: ${race.wrong} of ${race.runs} runs wrong, errors ${race.errors}" }
        assertEquals("", wrong.joinToString("\n"))
    }

    /** What [race] saw: documents tokenized, how many came out unlike the one-thread run, and what was thrown. */
    class Race(val runs: Int, val wrong: Int, val errors: List<String>)

    companion object {
        /**
         * Two threads, released together, each tokenizing its own document [repeats] times and
         * comparing every line's scopes with a one-thread run; [rounds] times, each from a cold grammar.
         * With [shared] both threads use one grammar; without, each loads its own.
         */
        fun race(scope: String, shared: Boolean, rounds: Int, repeats: Int): Race {
            val lines = SAMPLES.getValue(scope)
            // Each thread starts at a different place, so their states differ line by line.
            val documents = listOf(lines, lines.drop(7) + lines.take(7))
            val expected = documents.map { ShippedGrammars.scopes(ShippedGrammars.load(ShippedGrammars.registry(), scope), it) }
            val pool = Executors.newFixedThreadPool(2)
            var wrong = 0
            val errors = mutableListOf<String>()
            try {
                repeat(rounds) {
                    val one = if (shared) ShippedGrammars.load(ShippedGrammars.registry(), scope) else null
                    val start = CyclicBarrier(2)
                    val threads = documents.indices.map { index ->
                        pool.submit<Int> {
                            val grammar = one ?: ShippedGrammars.load(ShippedGrammars.registry(), scope)
                            start.await()
                            (1..repeats).count { ShippedGrammars.scopes(grammar, documents[index]) != expected[index] }
                        }
                    }
                    threads.forEach { thread ->
                        try {
                            wrong += thread.get()
                        } catch (e: ExecutionException) {
                            errors += e.cause.toString().lineSequence().first()
                        }
                    }
                }
            } finally {
                pool.shutdown()
                pool.awaitTermination(1, TimeUnit.MINUTES)
            }
            return Race(rounds * documents.size * repeats, wrong, errors)
        }

        /** Grammars whose rules change per line or embed others: Kotlin, Markdown with fences, shell heredocs. */
        val SAMPLES: Map<String, List<String>> = mapOf(
            "source.kotlin" to (0 until 8).flatMap { i ->
                listOf(
                    "/** Doc $i */", "class C$i(val n: Int) {", "    val s = \"\"\"raw \${n + $i}", "    still raw\"\"\"",
                    "    fun f(): String = \"n=\$n\" // note", "}",
                )
            },
            "text.html.markdown" to (0 until 6).flatMap { i ->
                listOf(
                    "# Heading $i", "", "Some *text* and `code` and [a link](https://x.dev/$i).", "",
                    "```kotlin", "fun f$i(x: Int) = \"\${x + $i}\"", "```", "",
                    "~~~~js", "const a$i = { b: [1, 2, '$i'] };", "```", "still js", "~~~~", "",
                    "````json", "{\"k$i\": [true, null, $i]}", "````", "",
                    "```bash", "cat <<EOF$i", "hello", "EOF$i", "```", "- item $i", "",
                )
            },
            "source.shell" to (0 until 8).flatMap { i ->
                listOf(
                    "cat <<EOF$i", "line \$x $i", "EOF$i", "cat <<-'END$i'", "\tliteral $i", "\tEND$i",
                    "if [ -n \"\$y\" ]; then echo \"$i\"; fi",
                )
            },
        )
    }
}
