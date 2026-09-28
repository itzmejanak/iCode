package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.GitRemoteRules

/** Where "No remote configured" leads: the remote Git will fetch from and push to. */
@Composable
internal fun AddRemoteSection(onAdd: (name: String, url: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("origin") }
    var url by rememberSaveable { mutableStateOf("") }
    val nameError = GitRemoteRules.nameError(name.trim())
    val urlError = GitRemoteRules.urlError(url.trim())
    IcodeCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
            StatusLabel("No remote configured. Add one to fetch, pull and push.", IcodeColors.TextSecondary)
            LabeledTextField("Remote name", name, { name = it }, modifier = Modifier.fillMaxWidth())
            LabeledTextField(
                "Repository URL",
                url,
                { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "https://host/owner/repo.git or git@host:owner/repo.git",
            )
            val shown = nameError ?: urlError.takeIf { url.isNotEmpty() }
            if (shown != null) Text(shown, style = IcodeType.Caption, color = IcodeColors.Error)
            PrimaryButton(
                "Add Remote",
                { onAdd(name.trim(), url.trim()) },
                Modifier.fillMaxWidth(),
                enabled = nameError == null && urlError == null,
            )
        }
    }
}
