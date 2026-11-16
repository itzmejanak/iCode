package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantParameterType
import dev.icode.mobile.data.assistant.AssistantStep
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.domain.AssistantQuestion
import dev.icode.mobile.domain.AssistantQuestionRules
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.AssistantToolName
import org.json.JSONArray
import org.json.JSONObject

/**
 * `ask_user` (docs/assistant-and-mcp.md): the model asks the user mid-turn — one to four
 * questions, each with its options — and the turn waits on [step] for the reply, which the model
 * reads as the call's result. Its parameters are Claude Code's AskUserQuestion's, so a model that
 * knows that tool uses this one. It reads nothing and changes nothing: no approval is asked for it.
 */
internal class AskUserTool(private val step: AssistantStep) : AssistantTool {
    override val name = AssistantToolName.AskUser.id
    override val description =
        "Asks the user 1 to ${AssistantQuestionRules.MAX_QUESTIONS} questions and waits for the answers, when a choice is theirs: " +
            "what they want, a decision between approaches, a preference the workspace does not show. Give each question " +
            "${AssistantQuestionRules.MIN_OPTIONS} to ${AssistantQuestionRules.MAX_OPTIONS} options, the one you recommend first " +
            "with \"(Recommended)\" after its label. The user can always answer in their own words, so add no \"Other\". " +
            "Not for permission: iCode asks before changes and commands itself."
    override val parameters = listOf(
        AssistantParameter(
            "questions",
            "The questions, asked together",
            AssistantParameterType.Items(
                AssistantParameterType.Fields(
                    listOf(
                        AssistantParameter("question", "The question, clear and specific, ending with a question mark"),
                        AssistantParameter("header", "Its name in a word or two, at most ${AssistantQuestionRules.MAX_HEADER} characters: \"Database\", \"Auth\""),
                        AssistantParameter(
                            "options",
                            "The answers to choose from",
                            AssistantParameterType.Items(
                                AssistantParameterType.Fields(
                                    listOf(
                                        AssistantParameter("label", "The answer, in a few words"),
                                        AssistantParameter("description", "What choosing it means, or what it costs"),
                                    ),
                                ),
                            ),
                        ),
                        AssistantParameter("multiSelect", "Several options may be chosen", AssistantParameterType.Boolean, required = false),
                    ),
                ),
            ),
        ),
    )
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String =
        runCatching { AssistantQuestionRules.summary(questionsOf(arguments)) }.getOrDefault(AssistantToolName.AskUser.title)

    override suspend fun run(arguments: JSONObject): String {
        val questions = runCatching { questionsOf(arguments) }.getOrElse { throw AssistantToolException(SHAPE) }
        AssistantQuestionRules.problem(questions)?.let { throw AssistantToolException(it) }
        val call = step.current ?: throw AssistantToolException("ask_user runs only as a call of a turn")
        return AssistantQuestionRules.result(questions, step.clarify(call, questions))
    }

    companion object {
        private const val SHAPE = "questions is a list of {question, header, options: [{label, description}], multiSelect}."

        /** The call's questions as the model wrote them; a list sent as text is read too. Throws on any other shape. */
        fun questionsOf(arguments: JSONObject): List<AssistantQuestion> {
            val list = arguments.optJSONArray("questions") ?: JSONArray(arguments.getString("questions"))
            return (0 until list.length()).map { index ->
                val question = list.getJSONObject(index)
                val options = question.optJSONArray("options") ?: JSONArray()
                AssistantQuestion(
                    header = question.optString("header").trim(),
                    text = question.optString("question").trim(),
                    options = (0 until options.length()).map { at ->
                        val option = options.getJSONObject(at)
                        AssistantQuestion.Option(option.optString("label").trim(), option.optString("description").trim())
                    },
                    multiple = question.optBoolean("multiSelect", false),
                )
            }
        }
    }
}
