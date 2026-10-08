package dev.icode.mobile.domain

/**
 * What the model is asked for a commit message, and what of its answer is the message
 * (docs/commit-message-generator-plan.md M2, M3).
 */
object CommitMessageRules {
    const val INSTRUCTIONS =
        "You write Git commit messages. From the staged changes you are given, write the message that commits them:\n" +
            "- A subject line in the imperative mood (\"Add\", \"Fix\", not \"Added\"), at most 72 characters, with no period at its end.\n" +
            "- Follow the style of the repository's recent commits where they show one, such as Conventional Commits " +
            "(\"feat(auth): add login validation\"); otherwise a plain subject.\n" +
            "- A body, after a blank line and wrapped at 72 characters, only when the subject cannot say why the change was made.\n" +
            "- Say what the change does, not each file's name.\n" +
            "Write only the message: no quotes, no code block, no explanation."

    /** The request: the repository's recent subjects, newest first, the files staged, and their diff. */
    fun request(subjects: List<String>, files: String, diff: String): String = buildString {
        if (subjects.isEmpty()) {
            appendLine("This repository has no commits yet.")
        } else {
            appendLine("The repository's recent commit subjects, newest first:")
            subjects.forEach { appendLine("- $it") }
        }
        appendLine()
        appendLine("The files staged:")
        appendLine(files.trimEnd())
        appendLine()
        appendLine("The staged diff:")
        append(diff.trimEnd())
    }

    /**
     * [answer] as a message to commit: without the thinking some servers put before a reasoning
     * model's text, and without a code block or quotes around it.
     */
    fun cleaned(answer: String): String {
        var text = THINKING.replaceFirst(answer.trim(), "").trim()
        FENCE.matchEntire(text)?.let { text = it.groupValues[1].trim() }
        val quote = text.firstOrNull()
        if (quote in QUOTES && text.length > 2 && text.last() == quote && text.count { it == quote } == 2) {
            text = text.substring(1, text.length - 1).trim()
        }
        return text
    }

    private val THINKING = Regex("^<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)
    private val FENCE = Regex("^```[\\w-]*\\n(.*?)\\n?```$", RegexOption.DOT_MATCHES_ALL)
    private val QUOTES = setOf('"', '\'', '`')
}
