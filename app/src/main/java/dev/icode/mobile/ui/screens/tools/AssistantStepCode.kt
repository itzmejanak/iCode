package dev.icode.mobile.ui.screens.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.domain.AssistantCode
import dev.icode.mobile.domain.AssistantEntry
import dev.icode.mobile.domain.AssistantToolName
import dev.icode.mobile.domain.NumberedLines
import dev.icode.mobile.ui.syntax.SyntaxPlace
import dev.icode.mobile.ui.syntax.languageOf
import dev.icode.mobile.ui.syntax.rememberSyntax
import dev.icode.mobile.ui.syntax.rememberSyntaxLines

// A step's code, coloured as its file's language as the editor colours it (docs/editor-and-language-services.md).

/** The text a change waiting for its answer writes, under its label. */
@Composable
internal fun StepCode(step: AssistantEntry.Step, code: AssistantCode) =
    StepText(code.label, code.text, shown = rememberSyntax(languageOfStep(step, code.text), code.text))

/** What a step answered: for read_file, the file's lines coloured, their numbers muted beside them; else as written. */
@Composable
internal fun StepOutput(step: AssistantEntry.Step, output: String) {
    if (step.call.tool != AssistantToolName.ReadFile.id) return StepText("Output", output)
    val lines = remember(output) { NumberedLines.read(output) }
    val code = remember(lines) { listOf(lines.filter { it.first != null }.joinToString("\n") { it.second }) }
    val colouring = rememberSyntaxLines(languageOfStep(step, code.single()), code)
    val muted = IcodeColors.TextMuted
    val shown = remember(lines, colouring, muted) {
        val width = lines.maxOf { it.first?.toString()?.length ?: 0 }
        var at = 0
        buildAnnotatedString {
            lines.forEachIndexed { index, (number, text) ->
                if (index > 0) append('\n')
                if (number == null) {
                    append(text)
                } else {
                    withStyle(SpanStyle(color = muted)) { append(number.toString().padStart(width) + "  ") }
                    append(colouring.line(SyntaxPlace(0, at++), text))
                }
            }
        }
    }
    StepText("Output", output, shown = shown)
}

/** The language of [step]'s file, whose text starts with [text]; null for a step with no file. */
private fun languageOfStep(step: AssistantEntry.Step, text: String): String? =
    step.call.file?.let { languageOf(it.substringAfterLast('/'), text) }
