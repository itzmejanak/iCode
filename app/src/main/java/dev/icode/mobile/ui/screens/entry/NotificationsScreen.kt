package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.domain.AppNotice
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/** What iCode has to tell the user, newest first; opening the page marks it all seen. */
@Composable
fun NotificationsScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    // New when the page opened: they keep their dot for this visit.
    val newIds = remember { state.notices.filterNot { it.read }.map { it.id }.toSet() }
    LaunchedEffect(Unit) { gateway.dispatch(ICodeIntent.MarkNoticesRead) }

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "Notifications",
            onBack = actions.back,
            actions = {
                if (state.notices.isNotEmpty()) {
                    IconButton(onClick = { gateway.dispatch(ICodeIntent.ClearNotices) }) {
                        Icon(Icons.Outlined.ClearAll, contentDescription = "Clear all notifications", tint = IcodeColors.TextPrimary)
                    }
                }
            },
        )
        if (state.notices.isEmpty() && state.recoverableDrafts.isEmpty()) {
            // Centered in the space under the header, clear of the screen's edges.
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = IcodeSpacing.Space6),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = "No notifications",
                    description = "iCode tells you here what happened while it was away, such as terminal sessions that ended.",
                    icon = Icons.Outlined.NotificationsNone,
                )
            }
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            if (state.recoverableDrafts.isNotEmpty()) {
                item(key = "unsaved-work") {
                    UnsavedWorkNotice(
                        state.recoverableDrafts,
                        onReview = { actions.navigate(Route.DocumentRecovery) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(state.notices, key = { it.id }) { notice ->
                when (notice) {
                    is AppNotice.TerminalSessionsEnded -> LostSessionsNotice(
                        notice,
                        new = notice.id in newIds,
                        onDelete = { gateway.dispatch(ICodeIntent.DeleteNotice(notice.id)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    is AppNotice.GitTransferInterrupted -> InterruptedTransferNotice(
                        notice,
                        new = notice.id in newIds,
                        onDelete = { gateway.dispatch(ICodeIntent.DeleteNotice(notice.id)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
