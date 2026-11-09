package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.domain.BusyAction
import dev.icode.mobile.domain.GitAuthState
import dev.icode.mobile.domain.GitAuthWork
import dev.icode.mobile.domain.GitRemoteRules
import dev.icode.mobile.domain.RuntimeToolWork
import dev.icode.mobile.domain.isBusy
import dev.icode.mobile.ui.LocalICodeSnapshot

@Composable
internal fun TokenSection(
    auth: GitAuthState,
    host: String,
    username: String,
    token: String,
    onHost: (String) -> Unit,
    onUsername: (String) -> Unit,
    onToken: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onOpenHost: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
        if (auth.hasHttpsCredential) {
            NoticeCard(tone = IcodeTone.Success, modifier = Modifier.fillMaxWidth()) {
                StatusLabel("Token saved for ${auth.httpsAccount}", IcodeColors.Green)
            }
            SecondaryButton("Remove Saved Token", onClear, Modifier.fillMaxWidth(), busy = LocalICodeSnapshot.current.isBusy(BusyAction.GitToken))
        }
        // GitHub, GitLab and Bitbucket each have one host; only a self-hosted server needs it typed.
        if (auth.provider.host.isEmpty()) {
            LabeledTextField("Host", host, onHost, placeholder = "git.example.com", modifier = Modifier.fillMaxWidth(), literal = true)
        }
        LabeledTextField("Account", username, onUsername, placeholder = "your-username", modifier = Modifier.fillMaxWidth(), literal = true)
        LabeledTextField(
            "Personal Access Token",
            token,
            onToken,
            placeholder = "Paste the token",
            modifier = Modifier.fillMaxWidth(),
            // A secret: masked while typing and never shown again once saved.
            secret = true,
        )
        MonoLine(
            "Encrypted by the Android Keystore; never shown again after saving. Used by iCode's clone, fetch, pull and push; Git in the terminal asks for its own.",
            color = IcodeColors.TextMuted,
            style = IcodeType.MonoSmall,
        )
        val hostError = GitRemoteRules.hostError(host.trim()).takeIf { host.isNotEmpty() }
        hostError?.let { Text(it, style = IcodeType.Caption, color = IcodeColors.Error) }
        val canSave = host.isNotBlank() && hostError == null && username.isNotBlank() && token.isNotBlank()
        val saving = LocalICodeSnapshot.current.isBusy(BusyAction.GitToken)
        val tokenUrl = auth.provider.tokenUrl
        if (tokenUrl == null) {
            SecondaryButton("Save Token", onSave, Modifier.fillMaxWidth(), enabled = canSave, busy = saving)
        } else {
            TwoColumnActions(
                left = {
                    SecondaryButton("Create on ${auth.provider.displayName}", { onOpenHost(tokenUrl) }, Modifier.fillMaxWidth())
                },
                right = {
                    PrimaryButton("Save Token", onSave, Modifier.fillMaxWidth(), enabled = canSave, busy = saving)
                },
            )
        }
    }
}

/** A public key's way to its host: Copy, and Add to the provider where iCode knows its form. */
@Composable
internal fun PublicKeyActions(publicKey: String, addUrl: String?, providerName: String, onOpenHost: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    if (addUrl == null) {
        SecondaryButton("Copy Public Key", { clipboard.setText(AnnotatedString(publicKey)) }, Modifier.fillMaxWidth())
    } else {
        TwoColumnActions(
            left = {
                SecondaryButton("Copy Key", { clipboard.setText(AnnotatedString(publicKey)) }, Modifier.fillMaxWidth())
            },
            right = {
                PrimaryButton(
                    "Add to $providerName",
                    {
                        // Copied first, so the key is ready to paste
                        // when the browser reaches the form.
                        clipboard.setText(AnnotatedString(publicKey))
                        onOpenHost(addUrl)
                    },
                    Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

@Composable
internal fun SshSection(
    auth: GitAuthState,
    /** The SSH client's install, while Generate waits on it. */
    clientInstall: RuntimeToolWork?,
    onGenerate: () -> Unit,
    onOpenHost: (String) -> Unit,
) {
    val publicKey = auth.sshPublicKey
    Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
        if (auth.hasSshKey && publicKey != null) {
            NoticeCard(tone = IcodeTone.Success, modifier = Modifier.fillMaxWidth()) {
                StatusLabel("SSH key ready", IcodeColors.Green)
            }
            IcodeCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                    SectionLabel("Public Key")
                    MonoLine(publicKey, style = IcodeType.MonoSmall)
                }
            }
            PublicKeyActions(publicKey, auth.provider.sshKeyUrl, auth.provider.displayName, onOpenHost)
            MonoLine(
                "Add the key to your host, then use a git@ URL.",
                color = IcodeColors.TextMuted,
                style = IcodeType.MonoSmall,
            )
        } else {
            MonoLine(
                "Generating creates an ed25519 pair inside the runtime; the private half never leaves it.",
                color = IcodeColors.TextMuted,
                style = IcodeType.MonoSmall,
            )
            // The SSH client's install says its own progress; the key's generation, that it is working.
            SecondaryButton(
                clientInstall?.status ?: "Generate SSH Key",
                onGenerate,
                Modifier.fillMaxWidth(),
                enabled = auth.work == null,
                busy = clientInstall != null || auth.work == GitAuthWork.GeneratingKey,
            )
        }
    }
}
