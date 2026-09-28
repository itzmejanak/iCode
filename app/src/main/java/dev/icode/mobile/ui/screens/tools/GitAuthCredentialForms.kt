package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.icode.mobile.domain.GitAuthState

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
            SecondaryButton("Remove Saved Token", onClear, Modifier.fillMaxWidth())
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
            "Encrypted by the Android Keystore; never shown again after saving.",
            color = IcodeColors.TextMuted,
            style = IcodeType.MonoSmall,
        )
        val tokenUrl = auth.provider.tokenUrl
        if (tokenUrl == null) {
            SecondaryButton(
                "Save Token",
                onSave,
                Modifier.fillMaxWidth(),
                enabled = host.isNotBlank() && username.isNotBlank() && token.isNotBlank(),
            )
        } else {
            TwoColumnActions(
                left = {
                    SecondaryButton("Create on ${auth.provider.displayName}", { onOpenHost(tokenUrl) }, Modifier.fillMaxWidth())
                },
                right = {
                    PrimaryButton(
                        "Save Token",
                        onSave,
                        Modifier.fillMaxWidth(),
                        enabled = host.isNotBlank() && username.isNotBlank() && token.isNotBlank(),
                    )
                },
            )
        }
    }
}

@Composable
internal fun SshSection(auth: GitAuthState, onGenerate: () -> Unit, onOpenHost: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
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
            val keyUrl = auth.provider.sshKeyUrl
            if (keyUrl == null) {
                SecondaryButton(
                    "Copy Public Key",
                    { clipboard.setText(AnnotatedString(publicKey)) },
                    Modifier.fillMaxWidth(),
                )
            } else {
                TwoColumnActions(
                    left = {
                        SecondaryButton("Copy Key", { clipboard.setText(AnnotatedString(publicKey)) }, Modifier.fillMaxWidth())
                    },
                    right = {
                        PrimaryButton(
                            "Add to ${auth.provider.displayName}",
                            {
                                // Copied first, so the key is ready to paste
                                // when the browser reaches the form.
                                clipboard.setText(AnnotatedString(publicKey))
                                onOpenHost(keyUrl)
                            },
                            Modifier.fillMaxWidth(),
                        )
                    },
                )
            }
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
            SecondaryButton(
                if (auth.busy) "Generating…" else "Generate SSH Key",
                onGenerate,
                Modifier.fillMaxWidth(),
                enabled = !auth.busy,
            )
        }
    }
}
