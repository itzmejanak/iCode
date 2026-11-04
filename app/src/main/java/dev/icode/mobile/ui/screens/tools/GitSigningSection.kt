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
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.designsystem.IcodeSelect
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.GitAuthState
import dev.icode.mobile.domain.GitAuthWork
import dev.icode.mobile.domain.GitSigningKey
import dev.icode.mobile.domain.GitSigningRules
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RuntimeToolWork
import dev.icode.mobile.domain.SigningIntent
import java.time.Instant
import java.time.ZoneId

/**
 * The Signing step of Git Credentials (docs/gpg-signing-plan.md §7): the gpg key Git signs every
 * commit with — made here from the default identity, or one gpg already holds — its public half for
 * the provider's form, and its passphrase, which GnuPG asks in its own sheet.
 */
@Composable
internal fun SigningSection(
    auth: GitAuthState,
    /** The default commit identity, "Name <email>", a new key is made for; null until it is saved. */
    identity: String?,
    /** The email the open repository commits as. */
    commitEmail: String?,
    /** GnuPG's install, while Install waits on it. */
    gnupgInstall: RuntimeToolWork?,
    onIntent: (ICodeIntent) -> Unit,
    onOpenHost: (String) -> Unit,
) {
    val signing = auth.signing
    // The key Git signs with, or the one turning signing on would choose.
    val shown = signing.key ?: signing.keys.firstOrNull()
    var creating by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
        when {
            signing.gnupgInstalled == false -> {
                MonoLine(
                    "Signing commits needs GnuPG in the runtime: gpg makes the key and signs with it.",
                    color = IcodeColors.TextMuted,
                    style = IcodeType.MonoSmall,
                )
                SecondaryButton(
                    gnupgInstall?.status ?: "Install GnuPG",
                    { onIntent(SigningIntent.InstallGnuPg) },
                    Modifier.fillMaxWidth(),
                    enabled = auth.work == null && gnupgInstall == null,
                )
            }
            shown == null -> {
                MonoLine(
                    "A key of gpg's defaults — ed25519, expires in 3 years — for your default identity. GnuPG asks its passphrase.",
                    color = IcodeColors.TextMuted,
                    style = IcodeType.MonoSmall,
                )
                SecondaryButton(
                    if (auth.work == GitAuthWork.CreatingSigningKey) "Creating…" else "Create Key",
                    { creating = true },
                    Modifier.fillMaxWidth(),
                    enabled = auth.work == null && identity != null && signing.gnupgInstalled == true,
                )
                if (identity == null) {
                    MonoLine("Save a default commit identity first, in step 1.", color = IcodeColors.Amber, style = IcodeType.MonoSmall)
                }
            }
            else -> {
                if (signing.key != null) {
                    NoticeCard(tone = IcodeTone.Success, modifier = Modifier.fillMaxWidth()) {
                        StatusLabel("Signing key ready", IcodeColors.Green)
                    }
                }
                if (signing.keys.size > 1) {
                    IcodeSelect(
                        options = signing.keys,
                        selected = shown,
                        optionLabel = { it.userId },
                        optionDescription = { "${it.algorithm} · ${it.keyId}" },
                        onSelect = { onIntent(SigningIntent.UseKey(it.fingerprint)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Key",
                    )
                }
                KeyCard(shown)
                SettingRow("Sign every commit", Modifier.fillMaxWidth()) {
                    IcodeToggle(signing.signsCommits && signing.key != null) { onIntent(SigningIntent.SignCommits(it)) }
                }
                signing.publicKey?.let { PublicKeyActions(it, auth.provider.gpgKeyUrl, auth.provider.displayName, onOpenHost) }
                SecondaryButton(
                    if (auth.work == GitAuthWork.ChangingPassphrase) "Changing…" else "Change Passphrase",
                    { onIntent(SigningIntent.ChangePassphrase(shown.fingerprint)) },
                    Modifier.fillMaxWidth(),
                    enabled = auth.work == null,
                )
                GitSigningRules.unmatchedEmail(shown, commitEmail)?.let { email ->
                    NoticeCard(tone = IcodeTone.Warning, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "This repository commits as $email, so GitHub shows its commits as Unverified: the key's email is " +
                                "${shown.email ?: "none"}. Use that email in step 1, or make a key for this one.",
                            style = IcodeType.Caption,
                        )
                    }
                }
            }
        }
    }
    if (creating && identity != null) {
        IcodeConfirmSheet(
            title = "Create Key",
            confirmLabel = "Create",
            onConfirm = {
                creating = false
                onIntent(SigningIntent.CreateKey)
            },
            onDismiss = { creating = false },
        ) {
            DetailRow("Identity", identity, valueStyle = IcodeType.MonoSmall)
            Text("ed25519 · expires in 3 years", style = IcodeType.Body)
            Text("The passphrase is asked in the GnuPG sheet.", style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary))
        }
    }
}

/** The key: who it is for, what it is, and until when. */
@Composable
private fun KeyCard(key: GitSigningKey) {
    IcodeCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
            SectionLabel("Key")
            Text(key.userId, style = IcodeType.Body)
            MonoLine("${key.algorithm} · ${key.keyId}", style = IcodeType.MonoSmall)
            MonoLine(
                key.expiresAtEpochMillis?.let { "expires ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()}" }
                    ?: "never expires",
                color = IcodeColors.TextSecondary,
                style = IcodeType.MonoSmall,
            )
        }
    }
}
