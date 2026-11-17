package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeOptionRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.KeyChip
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.AssistantClarification
import dev.icode.mobile.domain.AssistantQuestion
import dev.icode.mobile.domain.AssistantQuestionRules
import dev.icode.mobile.domain.AssistantReply
import dev.icode.mobile.domain.AssistantTurn
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.ui.LocalICodeGateway

/**
 * The questions of [turn]'s model (docs/assistant-and-mcp.md), in a box as the approval's, above the
 * composer: the question, its options with what each means, and Other for the user's own words —
 * one question at a time, their names as steps when there are several. Submit sends every answer;
 * × closes them unanswered. A message sent from the composer meanwhile is the reply instead.
 */
@Composable
internal fun AssistantQuestionsCard(turn: AssistantTurn, clarification: AssistantClarification, modifier: Modifier) {
    val gateway = LocalICodeGateway.current
    val questions = clarification.questions
    val callId = clarification.call.id
    var at by remember(callId) { mutableIntStateOf(0) }
    // Never saveable: what is typed goes to the model, and only to it.
    var answers by remember(callId) { mutableStateOf(List(questions.size) { AssistantQuestion.Answer() }) }
    val question = questions[at.coerceIn(questions.indices)]
    val answer = answers[at.coerceIn(answers.indices)]
    val reply = { it: AssistantReply -> gateway.dispatch(ICodeIntent.ReplyAssistant(turn.workspace, turn.session, callId, it)) }
    val change = { next: AssistantQuestion.Answer -> answers = answers.toMutableList().also { it[at] = next } }
    IcodeCard(modifier.fillMaxWidth(), padding = PaddingValues(IcodeSpacing.Space2)) {
        Column(Modifier.padding(horizontal = IcodeSpacing.Space1), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = IcodeColors.Amber, modifier = Modifier.size(IcodeSizes.IconSm))
                Text(question.text, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                IconButton(onClick = { reply(AssistantReply.Skipped) }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Skip the questions", tint = IcodeColors.TextSecondary)
                }
            }
            if (questions.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                    questions.forEachIndexed { index, step ->
                        val done = answers[index].given
                        KeyChip(
                            if (done) "${step.header} ✓" else step.header,
                            active = index == at,
                            compact = true,
                            description = "${step.header}, question ${index + 1} of ${questions.size}${if (done) ", answered" else ""}",
                            onClick = { at = index },
                        )
                    }
                }
            }
            Column(
                Modifier.heightIn(max = IcodeSizes.QuestionAnswers).verticalScroll(rememberScrollState())
                    .then(if (question.multiple) Modifier else Modifier.selectableGroup()),
            ) {
                question.options.forEach { option ->
                    val chosen = option.label in answer.chosen
                    IcodeOptionRow(option.label, option.description, chosen, multiple = question.multiple, onSelect = {
                        change(
                            when {
                                !question.multiple -> AssistantQuestion.Answer(chosen = listOf(option.label))
                                chosen -> answer.copy(chosen = answer.chosen - option.label)
                                else -> answer.copy(chosen = answer.chosen + option.label)
                            },
                        )
                    })
                }
                val own = answer.own != null
                IcodeOptionRow(OTHER, "In your own words", own, multiple = question.multiple, onSelect = {
                    change(
                        when {
                            !question.multiple -> AssistantQuestion.Answer(own = answer.own.orEmpty())
                            own -> answer.copy(own = null)
                            else -> answer.copy(own = "")
                        },
                    )
                })
                if (own) {
                    IcodeTextField(
                        answer.own.orEmpty(),
                        { change(answer.copy(own = it)) },
                        Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space1),
                        placeholder = "Your answer",
                        textStyle = IcodeType.Body,
                        singleLine = false,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2, Alignment.End)) {
                if (at > 0) SecondaryButton("Back", { at-- }, compact = true)
                if (at < questions.lastIndex) {
                    PrimaryButton("Next", { at++ }, compact = true, enabled = answer.given)
                } else {
                    PrimaryButton(
                        "Submit",
                        { reply(AssistantReply.Answered(answers)) },
                        compact = true,
                        enabled = AssistantQuestionRules.complete(questions, answers),
                    )
                }
            }
        }
    }
}

private const val OTHER = "Other"
