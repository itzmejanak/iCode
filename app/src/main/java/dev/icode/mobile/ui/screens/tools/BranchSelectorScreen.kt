package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeChoiceRow
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Branch picker: a sheet over the screen it was opened from (Source Control, or its sync screen).
 *
 * Local branches switch directly. A remote-tracking branch is offered by its
 * short name, which is what `git checkout` needs to create the local branch
 * that tracks it. Deleting is a long press, never a tap next to switching.
 */
@Composable
fun BranchSelectorScreen(actions: NavigationActions) {
    var query by rememberSaveable { mutableStateOf("") }
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val repository = state.gitRepository
    val branches = repository?.branches.orEmpty()
    val activeBranch = repository?.branch.orEmpty()
    var newBranch by rememberSaveable(repository?.workTreePath) { mutableStateOf("") }
    var branchPendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val matching = branches.filter { it.contains(query, ignoreCase = true) }
    // A remote branch already checked out locally is the same branch, so it is
    // not offered twice.
    val remotes = repository?.remoteBranches.orEmpty()
        .filter { it.contains(query, ignoreCase = true) }
        .filterNot { remote -> branches.any { it == remote.substringAfter('/') } }
    Box(Modifier.fillMaxSize()) {
        IcodeBottomSheet(
            title = "Switch Branch",
            onClose = actions.back,
            modifier = Modifier.align(Alignment.BottomCenter),
            // However long the branch list, the new branch's field and its
            // button stay in view, above the keyboard while typing.
            footer = {
                IcodeTextField(
                    value = newBranch,
                    onValueChange = { newBranch = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "New branch name",
                    literal = true,
                )
                PrimaryButton(
                    "Create New Branch",
                    {
                        gateway.dispatch(ICodeIntent.CreateBranch(newBranch))
                        newBranch = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = newBranch.isNotBlank(),
                    icon = Icons.Outlined.Add,
                )
            },
        ) {
            IcodeTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Search branches",
                leadingIcon = Icons.Outlined.Search,
            )
            matching.forEach { branch ->
                LocalBranchRow(
                    branch = branch,
                    active = branch == activeBranch,
                    onSwitch = {
                        gateway.dispatch(ICodeIntent.SwitchBranch(branch))
                        actions.back()
                    },
                    onDelete = { branchPendingDelete = branch },
                )
            }
            if (remotes.isNotEmpty()) {
                SectionLabel("Remote branches", Modifier.padding(top = IcodeSpacing.SectionBreak))
                remotes.forEach { remote ->
                    IcodeListRow(
                        title = remote,
                        titleStyle = IcodeType.Mono,
                        titleColor = IcodeColors.TextSecondary,
                        onClick = {
                            // Git creates the local branch that tracks exactly this one.
                            gateway.dispatch(ICodeIntent.CheckoutRemoteBranch(remote))
                            actions.back()
                        },
                        trailing = {
                            Icon(
                                Icons.Outlined.CloudDownload,
                                contentDescription = null,
                                tint = IcodeColors.TextMuted,
                                modifier = Modifier.size(IcodeSizes.IconMd),
                            )
                        },
                    )
                }
            }
        }
    }
    branchPendingDelete?.let { branch ->
        IcodeConfirmDialog(
            title = "Delete branch",
            message = "Git refuses to delete a branch whose commits are not merged, " +
                "so anything already merged is safe. This cannot be undone.",
            detail = branch,
            confirmLabel = "DELETE",
            onConfirm = {
                gateway.dispatch(ICodeIntent.DeleteBranch(branch))
                branchPendingDelete = null
            },
            onDismiss = { branchPendingDelete = null },
        )
    }
}

@Composable
private fun LocalBranchRow(
    branch: String,
    active: Boolean,
    onSwitch: () -> Unit,
    onDelete: () -> Unit,
) {
    IcodeChoiceRow(branch, chosen = active, onChoose = onSwitch) {
        // Git refuses to delete the checked-out branch, so only the others carry the control at all.
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "Delete branch $branch",
                tint = IcodeColors.TextMuted,
                modifier = Modifier.size(IcodeSizes.IconMd),
            )
        }
    }
}
