package dev.icode.mobile.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.domain.GpgAnswer
import dev.icode.mobile.domain.GpgIntent
import dev.icode.mobile.domain.GpgPrompt
import dev.icode.mobile.domain.ICodeGateway

/**
 * What gpg-agent asks through iCode's pinentry (docs/gpg-signing-plan.md), in its own words and
 * with its own buttons: a key's passphrase, or a yes or no. gpg-agent keeps the passphrase it is
 * given; iCode keeps nothing.
 */
@Composable
internal fun GpgPromptDialog(prompt: GpgPrompt, gateway: ICodeGateway) {
    // Held while the dialog is shown, never saved with the screen's state.
    var passphrase by remember(prompt.id, prompt.error) { mutableStateOf("") }
    val answer = { value: GpgAnswer -> gateway.dispatch(GpgIntent.Answer(prompt.id, value)) }
    IcodeConfirmDialog(
        title = prompt.title ?: "GnuPG",
        confirmLabel = prompt.ok ?: "OK",
        dismissLabel = prompt.cancel ?: "Cancel",
        onConfirm = { answer(if (prompt.asksPassphrase) GpgAnswer.Passphrase(passphrase) else GpgAnswer.Ok) },
        onDismiss = { answer(GpgAnswer.Cancel) },
    ) {
        Text(prompt.description, style = IcodeType.Body, color = IcodeColors.TextSecondary)
        prompt.error?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.Error)) }
        if (prompt.asksPassphrase) {
            LabeledTextField(prompt.prompt?.removeSuffix(":") ?: "Passphrase", passphrase, { passphrase = it }, secret = true)
        }
    }
}
