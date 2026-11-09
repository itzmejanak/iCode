package dev.icode.mobile.domain

/**
 * What the model asks the user mid-turn with its `ask_user` call (docs/assistant-questions-plan.md):
 * one to four [questions], answered together. The turn waits until the user replies or stops it.
 */
data class AssistantClarification(val call: AssistantCall, val questions: List<AssistantQuestion>)

/**
 * One question: [header] names it in a word or two (its step, when there are several), [text] asks
 * it, and [options] answer it — one of them, or several when [multiple]. The user may always answer
 * in their own words instead.
 */
data class AssistantQuestion(val header: String, val text: String, val options: List<Option>, val multiple: Boolean) {
    data class Option(val label: String, val description: String)

    /** Its answer: the options [chosen], by label, and the user's [own] words, when they wrote any. */
    data class Answer(val chosen: List<String> = emptyList(), val own: String? = null) {
        val given: Boolean get() = chosen.isNotEmpty() || !own.isNullOrBlank()
    }
}

/** The user's reply to an [AssistantClarification]. */
sealed interface AssistantReply {
    /** Each question's answer, in the questions' order. */
    data class Answered(val answers: List<AssistantQuestion.Answer>) : AssistantReply

    /** A message instead of the answers: what the user typed or said while the questions waited. */
    data class Said(val text: String) : AssistantReply

    /** Closed without an answer. */
    data object Skipped : AssistantReply
}

/** What an `ask_user` call may ask, and how it and its reply are said (docs/assistant-questions-plan.md D3, D4). */
object AssistantQuestionRules {
    const val MAX_QUESTIONS = 4
    const val MIN_OPTIONS = 2
    const val MAX_OPTIONS = 4
    const val MAX_HEADER = 12

    /** Why [questions] cannot be asked, in the words the model fixes its call by; null when they can. */
    fun problem(questions: List<AssistantQuestion>): String? {
        if (questions.isEmpty() || questions.size > MAX_QUESTIONS) return "Ask 1 to $MAX_QUESTIONS questions in one call, not ${questions.size}."
        questions.forEachIndexed { index, question ->
            val which = "Question ${index + 1}"
            when {
                question.text.isBlank() -> return "$which has no question text."
                question.header.isBlank() -> return "$which has no header: name it in a word or two."
                question.header.length > MAX_HEADER -> return "$which's header \"${question.header}\" is longer than $MAX_HEADER characters."
                question.options.size !in MIN_OPTIONS..MAX_OPTIONS ->
                    return "$which has ${question.options.size} options: give $MIN_OPTIONS to $MAX_OPTIONS. The user can always answer in their own words."
                question.options.any { it.label.isBlank() } -> return "$which has an option with no label."
                question.options.map { it.label.trim() }.distinct().size != question.options.size -> return "$which has two options with the same label."
            }
        }
        if (questions.map { it.text.trim() }.distinct().size != questions.size) return "Two questions ask the same thing."
        return null
    }

    /** Whether [answers] answer each of [questions]: Submit is offered then. */
    fun complete(questions: List<AssistantQuestion>, answers: List<AssistantQuestion.Answer>): Boolean =
        answers.size == questions.size && answers.all { it.given }

    /** The step's words: the first question, and how many more. */
    fun summary(questions: List<AssistantQuestion>): String {
        val first = questions.firstOrNull()?.text?.trim().orEmpty()
        return if (questions.size > 1) "Asked: $first (+${questions.size - 1} more)" else "Asked: $first"
    }

    /** What the model reads back: each question with its answer, the user's message, or that they did not answer. */
    fun result(questions: List<AssistantQuestion>, reply: AssistantReply): String = when (reply) {
        is AssistantReply.Answered -> buildString {
            append("The user answered:")
            questions.forEachIndexed { index, question ->
                val answer = reply.answers.getOrNull(index) ?: AssistantQuestion.Answer()
                append("\n- ").append(question.text.trim()).append(" → ").append(said(answer))
            }
        }
        is AssistantReply.Said -> "The user replied instead of choosing: \"${reply.text.trim()}\""
        AssistantReply.Skipped ->
            "The user closed the questions without answering. Go on with your own judgement and say what you assumed, or stop if you cannot."
    }

    private fun said(answer: AssistantQuestion.Answer): String {
        val own = answer.own?.trim()?.takeIf { it.isNotEmpty() }?.let { "in their own words: \"$it\"" }
        return (answer.chosen + listOfNotNull(own)).joinToString(", ").ifEmpty { "(no answer)" }
    }

    /** What a surface away from the chat says it asks — the notification, the bubble, the voice: the first question, and how many there are. */
    fun asked(clarification: AssistantClarification): String {
        val first = clarification.questions.firstOrNull()?.text?.trim().orEmpty()
        val more = clarification.questions.size - 1
        return if (more > 0) "$first (1 of ${clarification.questions.size})" else first
    }

    /** What is said aloud: the first question and its options, as one would ask it. */
    fun spoken(clarification: AssistantClarification): String {
        val question = clarification.questions.firstOrNull() ?: return ""
        val labels = question.options.map { it.label.trim() }
        val choices = if (labels.size > 1) labels.dropLast(1).joinToString(", ") + " or " + labels.last() else labels.joinToString()
        val more = clarification.questions.size - 1
        val rest = if (more > 0) " And $more more on the screen." else ""
        return "${question.text.trim()} $choices?$rest"
    }

    /** The title of every surface's question. */
    const val TITLE = "The assistant asks"
}
