package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.GitRemoteRules
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/** Clone form; authentication opens the Git credentials frame. */
@Composable
fun CloneRepositoryScreen(actions: NavigationActions) {
    val gateway = LocalWorkbenchGateway.current
    val state = LocalWorkbenchSnapshot.current
    // Typing drives local state so every keystroke lands immediately; routing
    // each one through the gateway made the field lag and drop characters.
    // The snapshot copy is what survives navigating away (to Git credentials),
    // and seeds this state when the screen is entered again.
    var url by remember { mutableStateOf(state.cloneUrl) }
    var name by remember { mutableStateOf(state.cloneName) }
    var branch by remember { mutableStateOf(state.cloneBranch) }
    fun keepForm() = gateway.dispatch(WorkbenchIntent.UpdateCloneForm(url, name, branch))
    val destination = state.projectDestination
    val nameProblem = projectProblem(name, destination)
    val branchError = branch.trim().takeIf { it.isNotEmpty() }?.let(GitRemoteRules::branchError)
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Clone Repository", onBack = actions.back)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(IcodeSpacing.Space5),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space5),
        ) {
            LabeledTextField(
                label = "Repository URL",
                value = url,
                onValueChange = {
                    // The name is Git's for the address until the user gives their own.
                    if (name.isEmpty() || name == directoryNameFor(url)) name = directoryNameFor(it)
                    url = it
                    keepForm()
                },
                placeholder = "https://host/owner/repo.git or git@host:owner/repo.git",
                literal = true,
            )
            DestinationField(destination) { actions.navigate(Route.ChooseDestination) }
            WorkspaceNameFields(name, { name = it; keepForm() }, nameProblem, destination)
            LabeledTextField(
                label = "Checkout Branch (Optional)",
                value = branch,
                onValueChange = {
                    branch = it
                    keepForm()
                },
                literal = true,
            )
            branchError?.let { Text(it, style = IcodeType.Caption, color = IcodeColors.Error) }
            (state.gitOperation as? GitOperationState.Failed)?.let { failure ->
                NoticeCard(tone = IcodeTone.Error, modifier = Modifier.fillMaxWidth()) {
                    StatusLabel(failure.reason, IcodeColors.Error)
                }
            }
            if (state.gitOperation is GitOperationState.Cancelled) {
                NoticeCard(tone = IcodeTone.Warning, modifier = Modifier.fillMaxWidth()) {
                    StatusLabel("Clone cancelled. Nothing was kept.", IcodeColors.Amber)
                }
            }
            IcodeListRow(
                card = true,
                title = "AUTHENTICATION",
                titleStyle = IcodeType.Label,
                titleColor = IcodeColors.TextPrimary,
                onClick = { actions.navigate(Route.GitAuth) },
                leading = {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = IcodeColors.TextSecondary,
                        modifier = Modifier.size(IcodeSizes.IconMd),
                    )
                },
                trailing = {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary)
                },
            )
        }
        PrimaryButton(
            if (state.gitOperation is GitOperationState.Running) "Cloning…" else "Clone Repository",
            {
                gateway.dispatch(
                    WorkbenchIntent.CloneRepository(url = url.trim(), directoryName = name.trim(), branch = branch.trim(), into = destination?.location),
                )
                actions.navigate(Route.CloneProgress)
            },
            Modifier.fillMaxWidth().padding(IcodeSpacing.Space5),
            enabled = url.isNotBlank() && nameProblem == null && branchError == null &&
                state.gitOperation !is GitOperationState.Running,
        )
    }
}

/**
 * The name Git would give the folder for [url], proposed as the workspace's name.
 *
 * Mirrors Git's own rule — the last path segment without a trailing `.git`.
 */
internal fun directoryNameFor(url: String): String {
    val trimmed = url.trim().trimEnd('/')
    if (trimmed.isEmpty()) return ""
    val lastSegment = trimmed.substringAfterLast('/').substringAfterLast(':')
    return lastSegment.removeSuffix(".git")
}
