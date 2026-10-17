package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.BottomBarItem
import dev.icode.mobile.designsystem.IcodeBottomBar
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSelect
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.RuntimeToolWork
import dev.icode.mobile.domain.GitAuthMethod
import dev.icode.mobile.domain.GitAuthState
import dev.icode.mobile.domain.GitProvider

/** Compact progress header; tapping a completed step returns to it. */
@Composable
internal fun GitAuthStepBar(
    current: GitAuthStep,
    onSelect: (GitAuthStep) -> Unit,
    identityValid: Boolean,
) {
    IcodeBottomBar(
        items = GitAuthStep.entries.mapIndexed { index, entry ->
            BottomBarItem(
                "${index + 1} ${entry.short}",
                onClick = { onSelect(entry) },
                active = entry == current,
                // Later steps need a commit identity first.
                enabled = entry == GitAuthStep.Identity || identityValid,
            )
        },
    )
}

@Composable
internal fun IdentitySection(
    name: String,
    email: String,
    description: String,
    onName: (String) -> Unit,
    onEmail: (String) -> Unit,
) {
    LabeledTextField("Name", name, onName, placeholder = "Your name", modifier = Modifier.fillMaxWidth())
    LabeledTextField("Email", email, onEmail, placeholder = "you@example.com", modifier = Modifier.fillMaxWidth(), literal = true)
    MonoLine(
        description,
        color = IcodeColors.TextMuted,
        style = IcodeType.MonoSmall,
    )
}

@Composable
internal fun AuthenticationSection(
    auth: GitAuthState,
    host: String,
    username: String,
    token: String,
    onProvider: (GitProvider) -> Unit,
    onMethod: (GitAuthMethod) -> Unit,
    onHost: (String) -> Unit,
    onUsername: (String) -> Unit,
    onToken: (String) -> Unit,
    onSaveToken: () -> Unit,
    onClearToken: () -> Unit,
    onGenerateSsh: () -> Unit,
    onOpenHost: (String) -> Unit,
    /** The SSH client's install, while Generate waits on it. */
    sshClientInstall: RuntimeToolWork? = null,
) {
    IcodeSelect(
        options = GitProvider.entries,
        selected = auth.provider,
        optionLabel = { it.displayName },
        optionDescription = { it.host.ifEmpty { "Enter the host yourself" } },
        onSelect = onProvider,
        modifier = Modifier.fillMaxWidth(),
        label = "Git Provider",
    )
    IcodeSelect(
        options = GitAuthMethod.entries,
        selected = auth.method,
        optionLabel = { method ->
            when (method) {
                GitAuthMethod.PersonalAccessToken -> "Personal Access Token (HTTPS)"
                GitAuthMethod.SshKey -> "SSH Key"
            }
        },
        optionDescription = { method ->
            when (method) {
                GitAuthMethod.PersonalAccessToken -> "Stored encrypted on this device"
                GitAuthMethod.SshKey -> "Key pair generated in the runtime"
            }
        },
        onSelect = onMethod,
        modifier = Modifier.fillMaxWidth(),
        label = "Method",
    )
    when (auth.method) {
        GitAuthMethod.PersonalAccessToken -> TokenSection(
            auth = auth,
            host = host,
            username = username,
            token = token,
            onHost = onHost,
            onUsername = onUsername,
            onToken = onToken,
            onSave = onSaveToken,
            onClear = onClearToken,
            onOpenHost = onOpenHost,
        )
        GitAuthMethod.SshKey -> SshSection(auth, sshClientInstall, onGenerateSsh, onOpenHost)
    }
}

@Composable
internal fun VerifySection(auth: GitAuthState, url: String, onUrl: (String) -> Unit) {
    val credential = when {
        auth.hasSshKey && auth.method == GitAuthMethod.SshKey -> "Using the generated SSH key"
        auth.hasHttpsCredential -> "Using the token saved for ${auth.httpsAccount}"
        else -> "No credential configured yet"
    }
    NoticeCard(
        tone = if (auth.hasSshKey || auth.hasHttpsCredential) IcodeTone.Info else IcodeTone.Warning,
        modifier = Modifier.fillMaxWidth(),
    ) {
        StatusLabel(credential, IcodeColors.TextSecondary)
    }
    LabeledTextField(
        "Repository URL",
        url,
        onUrl,
        placeholder = "git@host:owner/repo.git",
        modifier = Modifier.fillMaxWidth(),
        literal = true,
    )
    MonoLine(
        "Runs a read-only ls-remote. Nothing is written to the repository.",
        color = IcodeColors.TextMuted,
        style = IcodeType.MonoSmall,
    )
    auth.lastTest?.let { result ->
        NoticeCard(
            tone = if (result.succeeded) IcodeTone.Success else IcodeTone.Error,
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatusLabel(result.message, if (result.succeeded) IcodeColors.Green else IcodeColors.Error)
        }
    }
}
