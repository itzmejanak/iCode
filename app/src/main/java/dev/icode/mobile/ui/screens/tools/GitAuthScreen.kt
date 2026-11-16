package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.domain.BusyAction
import dev.icode.mobile.domain.GitAuthState
import dev.icode.mobile.domain.GitAuthWork
import dev.icode.mobile.domain.GitIdentityRules
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.isBusy
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/** What setting up Git requires, in order; signing is the user's choice (docs/source-control.md). */
internal enum class GitAuthStep(val title: String, val caption: String, val short: String = title) {
    Identity("Commit Identity", "Who your commits are attributed to", short = "Identity"),
    Authentication("Authentication", "How this device proves it may push"),
    Signing("Commit Signing", "How your commits are signed", short = "Signing"),
    Verify("Verify", "Check it works against a real repository"),
}

private enum class IdentityScope { Default, Repository }

/**
 * Git credentials as a short guided flow.
 *
 * One step is on screen at a time: identity, then authentication, then a real
 * connection test. Showing all three at once buried the one thing the user
 * actually needed next, and the steps genuinely depend on each other — a token
 * is useless before Git knows who is committing.
 */
@Composable
fun GitAuthScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val state = LocalICodeSnapshot.current
    val repository = state.gitRepository
    val auth = state.gitAuth

    LaunchedEffect(Unit) { gateway.dispatch(ICodeIntent.RefreshGitAuth) }

    var identityScope by rememberSaveable { mutableStateOf(IdentityScope.Default) }
    var identityEdited by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var pendingIdentitySave by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(state.gitIdentityLoaded, identityScope, state.globalGitAuthorName, state.globalGitAuthorEmail,
        state.localGitAuthorName, state.localGitAuthorEmail) {
        if (state.gitIdentityLoaded && !identityEdited) {
            name = if (identityScope == IdentityScope.Default) state.globalGitAuthorName.orEmpty()
                else state.localGitAuthorName ?: state.globalGitAuthorName.orEmpty()
            email = if (identityScope == IdentityScope.Default) state.globalGitAuthorEmail.orEmpty()
                else state.localGitAuthorEmail ?: state.globalGitAuthorEmail.orEmpty()
        }
    }
    // Typed only for a self-hosted server; a known provider's host is its own.
    var customHost by rememberSaveable { mutableStateOf("") }
    val host = auth.provider.host.ifEmpty { customHost }
    var username by rememberSaveable { mutableStateOf("") }
    // Never saveable: saved state leaves the process in a Bundle, and a token must not.
    var token by remember { mutableStateOf("") }
    var testUrl by rememberSaveable { mutableStateOf("") }

    // Start on the first thing that is not done yet, so a returning user is not
    // walked through settings they already have.
    val initialStep = remember(repository?.workTreePath) {
        when {
            repository?.authorName.isNullOrBlank() -> GitAuthStep.Identity
            !auth.hasHttpsCredential && !auth.hasSshKey -> GitAuthStep.Authentication
            else -> GitAuthStep.Verify
        }
    }
    var step by rememberSaveable { mutableStateOf(initialStep) }
    LaunchedEffect(pendingIdentitySave, state.globalGitAuthorName, state.globalGitAuthorEmail,
        state.localGitAuthorName, state.localGitAuthorEmail) {
        val pending = pendingIdentitySave ?: return@LaunchedEffect
        val savedName = if (identityScope == IdentityScope.Default) state.globalGitAuthorName else state.localGitAuthorName
        val savedEmail = if (identityScope == IdentityScope.Default) state.globalGitAuthorEmail else state.localGitAuthorEmail
        if (savedName == pending.first && savedEmail == pending.second) {
            pendingIdentitySave = null
            identityEdited = false
            step = GitAuthStep.Authentication
        }
    }

    val identityValid = state.gitAvailable == true && name.isNotBlank() &&
        GitIdentityRules.isValidEmail(email)

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Git Credentials", subtitle = step.caption, onBack = actions.back)
        IcodePageColumn(Modifier.weight(1f)) {
            when (step) {
                GitAuthStep.Identity -> {
                    if (state.gitIdentityLoaded && state.gitAvailable == false) IcodeSection {
                        Text(
                            if (state.runtimeHealthy) "Git is not installed in the runtime yet."
                            else "Install the Linux runtime before setting Git identity.",
                            style = IcodeType.Caption.copy(color = IcodeColors.Amber),
                        )
                        val gitInstall = state.runtimeTools.installing(RuntimeTools.Git)
                        SecondaryButton(
                            when {
                                gitInstall != null -> gitInstall.status
                                state.runtimeHealthy -> "Install Git"
                                else -> "Runtime settings"
                            },
                            {
                                if (state.runtimeHealthy) gateway.dispatch(ICodeIntent.ProvisionGit)
                                else actions.navigate(Route.SettingsRuntime)
                            },
                            busy = gitInstall != null,
                        )
                    }
                    IcodeSection {
                        SegmentedControl(
                            values = if (repository == null) listOf("Default") else listOf("Default", "This repo"),
                            active = if (identityScope == IdentityScope.Default) "Default" else "This repo",
                        ) { selected ->
                            identityScope = if (selected == "Default") IdentityScope.Default else IdentityScope.Repository
                            identityEdited = false
                            pendingIdentitySave = null
                        }
                        Text(
                            if (identityScope == IdentityScope.Default) "Default for every repository in this app's Linux runtime"
                            else "Overrides the default only for ${state.activeWorkspace}",
                            style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary),
                        )
                    }
                    IdentitySection(
                        name = name,
                        email = email,
                        description = if (identityScope == IdentityScope.Default)
                            "Saved in Git's global config. Existing repository overrides still take priority."
                        else "Saved only in this repository's Git config.",
                        onName = { name = it; identityEdited = true },
                        onEmail = { email = it; identityEdited = true },
                    )
                    if (identityScope == IdentityScope.Repository &&
                        (state.localGitAuthorName != null || state.localGitAuthorEmail != null)
                    ) {
                        SecondaryButton("Use default identity", {
                            gateway.dispatch(ICodeIntent.ClearRepositoryGitIdentity)
                            identityEdited = false
                        }, busy = state.isBusy(BusyAction.GitIdentity))
                    }
                }
                GitAuthStep.Authentication -> AuthenticationSection(
                    auth = auth,
                    host = host,
                    username = username,
                    token = token,
                    onProvider = { provider -> gateway.dispatch(ICodeIntent.SelectGitProvider(provider)) },
                    onMethod = { gateway.dispatch(ICodeIntent.SelectGitAuthMethod(it)) },
                    onHost = { customHost = it },
                    onUsername = { username = it },
                    onToken = { token = it },
                    onSaveToken = {
                        gateway.dispatch(ICodeIntent.SaveGitToken(host, username, token))
                        token = ""
                    },
                    onClearToken = { gateway.dispatch(ICodeIntent.ClearGitToken) },
                    onGenerateSsh = { gateway.dispatch(ICodeIntent.GenerateSshKey) },
                    onOpenHost = { gateway.dispatch(ICodeIntent.OpenExternalUrl(it)) },
                    sshClientInstall = state.runtimeTools.installing(RuntimeTools.SshClient),
                )
                GitAuthStep.Signing -> SigningSection(
                    auth = auth,
                    identity = state.globalGitAuthorName?.takeIf { it.isNotBlank() }?.let { name ->
                        state.globalGitAuthorEmail?.takeIf { it.isNotBlank() }?.let { "$name <$it>" }
                    },
                    commitEmail = repository?.authorEmail ?: state.globalGitAuthorEmail,
                    gnupgInstall = state.runtimeTools.installing(RuntimeTools.GnuPg),
                    onIntent = gateway::dispatch,
                    onOpenHost = { gateway.dispatch(ICodeIntent.OpenExternalUrl(it)) },
                )
                GitAuthStep.Verify -> VerifySection(
                    auth = auth,
                    url = testUrl,
                    onUrl = { testUrl = it },
                )
            }
        }
        StepActions(
            step = step,
            auth = auth,
            identityValid = identityValid,
            testUrl = testUrl,
            onBack = { step = previousStep(step) },
            onNext = {
                if (step == GitAuthStep.Identity) {
                    pendingIdentitySave = name.trim() to email.trim()
                    gateway.dispatch(
                        if (identityScope == IdentityScope.Default) ICodeIntent.ConfigureGlobalGitIdentity(name, email)
                        else ICodeIntent.ConfigureGitIdentity(name, email),
                    )
                } else step = nextStep(step)
            },
            onTest = { gateway.dispatch(ICodeIntent.TestGitConnection(testUrl)) },
            onDone = actions.back,
        )
        // The steps: the bottom bar, as the editor's (owner's design), under the step's button.
        GitAuthStepBar(current = step, onSelect = { step = it }, identityValid = identityValid)
    }
}

@Composable
private fun StepActions(
    step: GitAuthStep,
    auth: GitAuthState,
    identityValid: Boolean,
    testUrl: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onTest: () -> Unit,
    onDone: () -> Unit,
) {
    val canAdvance = when (step) {
        GitAuthStep.Identity -> identityValid
        GitAuthStep.Authentication -> auth.hasHttpsCredential || auth.hasSshKey
        // Signing is the user's choice: the step never holds the flow up.
        GitAuthStep.Signing, GitAuthStep.Verify -> true
    }
    Column(Modifier.padding(IcodeSpacing.Gutter)) {
        when (step) {
            GitAuthStep.Verify -> TwoColumnActions(
                left = { SecondaryButton("Back", onBack, Modifier.fillMaxWidth()) },
                right = {
                    PrimaryButton(
                        "Test Connection",
                        onTest,
                        Modifier.fillMaxWidth(),
                        enabled = auth.work == null && testUrl.isNotBlank(),
                        busy = auth.work == GitAuthWork.Testing,
                    )
                },
            )
            GitAuthStep.Identity -> PrimaryButton(
                "Save and Continue",
                onNext,
                Modifier.fillMaxWidth(),
                enabled = canAdvance,
                busy = LocalICodeSnapshot.current.isBusy(BusyAction.GitIdentity),
            )
            GitAuthStep.Authentication, GitAuthStep.Signing -> TwoColumnActions(
                left = { SecondaryButton("Back", onBack, Modifier.fillMaxWidth()) },
                right = {
                    PrimaryButton(
                        "Continue",
                        onNext,
                        Modifier.fillMaxWidth(),
                        // Advancing without a credential would only fail the
                        // test step, so the flow says so here instead.
                        enabled = canAdvance,
                    )
                },
            )
        }
        if (step == GitAuthStep.Verify && auth.lastTest?.succeeded == true) {
            SecondaryButton("Done", onDone, Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space2))
        }
    }
}

private fun nextStep(step: GitAuthStep): GitAuthStep = when (step) {
    GitAuthStep.Identity -> GitAuthStep.Authentication
    GitAuthStep.Authentication -> GitAuthStep.Signing
    GitAuthStep.Signing -> GitAuthStep.Verify
    GitAuthStep.Verify -> GitAuthStep.Verify
}

private fun previousStep(step: GitAuthStep): GitAuthStep = when (step) {
    GitAuthStep.Identity -> GitAuthStep.Identity
    GitAuthStep.Authentication -> GitAuthStep.Identity
    GitAuthStep.Signing -> GitAuthStep.Authentication
    GitAuthStep.Verify -> GitAuthStep.Signing
}
