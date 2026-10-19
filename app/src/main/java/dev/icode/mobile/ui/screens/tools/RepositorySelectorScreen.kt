package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeChoiceRow
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Which repository Source Control works in, when the open workspace holds more than one
 * (docs/workspace-destinations-plan.md §2.6, D7): a sheet over the screen it was opened from, as
 * the branch selector is. Each is named by its folder in the workspace; the one Git works in is
 * ticked. Everything on Source Control and the sync page — changes, commit, branch, pull and
 * push — is the chosen one's.
 */
@Composable
fun RepositorySelectorScreen(actions: NavigationActions) {
    var query by rememberSaveable { mutableStateOf("") }
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val workspace = state.activeWorkspaceLocation
    val chosen = state.gitRepository?.workTreePath
    val matching = state.gitRepositories.filter { repositoryLabel(it, workspace).contains(query, ignoreCase = true) }
    Box(Modifier.fillMaxSize()) {
        IcodeBottomSheet(
            title = "Switch Repository",
            onClose = actions.back,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            IcodeTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Search repositories",
                leadingIcon = Icons.Outlined.Search,
            )
            matching.forEach { location ->
                IcodeChoiceRow(repositoryLabel(location, workspace), chosen = location == chosen, onChoose = {
                    gateway.dispatch(ICodeIntent.ChooseGitRepository(location))
                    actions.back()
                })
            }
        }
    }
}
