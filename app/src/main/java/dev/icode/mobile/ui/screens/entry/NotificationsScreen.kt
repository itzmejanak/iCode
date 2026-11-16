package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSelect
import dev.icode.mobile.designsystem.IcodeSelectVariant
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextButton
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.NoticeIntent
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.notices.NoticeFilter
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.LocalNotices
import dev.icode.mobile.ui.NoticeRow

/**
 * What iCode has told the user, newest first (docs/engineering-rules.md): All, what needs
 * them, or what was done, and of one place; opening the page marks it all seen, a tap opens one's
 * sheet, Clear all clears what the filters show.
 */
@Composable
fun NotificationsScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val notices = LocalNotices.current
    // New when the page opened: they keep their dot for this visit.
    val newIds = remember { state.notices.filterNot { it.read }.map { it.id }.toSet() }
    LaunchedEffect(Unit) { gateway.dispatch(NoticeIntent.MarkAllRead) }
    var kind by rememberSaveable { mutableStateOf(ALL) }
    var place by rememberSaveable { mutableStateOf<String?>(null) }
    val places = NoticeFilter.placesOf(state.notices)
    val byKind = when (kind) {
        NEEDS_YOU -> NoticeFilter.NeedsYou
        DONE -> NoticeFilter.Done
        else -> NoticeFilter.All
    }
    val byPlace = place?.let { if (it == ELSEWHERE) NoticeFilter.Elsewhere else NoticeFilter.At(it) } ?: NoticeFilter.All
    val shown = state.notices.filter { byKind.matches(it) && byPlace.matches(it) }

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "Notifications",
            onBack = actions.back,
            actions = {
                if (shown.isNotEmpty()) {
                    IconButton(onClick = { gateway.dispatch(NoticeIntent.Clear(if (place == null) byKind else byPlace)) }) {
                        Icon(Icons.Outlined.ClearAll, contentDescription = "Clear these notifications", tint = IcodeColors.TextPrimary)
                    }
                }
            },
        )
        if (state.systemAccess?.notifications == false) NotificationsOff { gateway.dispatch(ICodeIntent.RequestSystemPermission(SystemPermission.Notifications)) }
        if (state.notices.isEmpty() && state.recoverableDrafts.isEmpty()) {
            // Centered in the space under the header, clear of the screen's edges.
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = IcodeSpacing.Space6),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = "No notifications",
                    description = "What iCode tells you stays here: problems, what to do first, and what was done, with where.",
                    icon = Icons.Outlined.NotificationsNone,
                )
            }
            return@Column
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = IcodeSpacing.Gutter, vertical = IcodeSpacing.Space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            SegmentedControl(listOf(ALL, NEEDS_YOU, DONE), kind) { kind = it }
            if (places.size > 1 || places.isNotEmpty() && state.notices.any { it.place?.workspace == null }) {
                IcodeSelect(
                    options = listOf<String?>(null) + places + ELSEWHERE,
                    selected = place,
                    optionLabel = { it ?: ALL_PLACES },
                    onSelect = { place = it },
                    variant = IcodeSelectVariant.Chip,
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = IcodeSpacing.Gutter, end = IcodeSpacing.Gutter, bottom = IcodeSpacing.Gutter),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.ItemGap),
        ) {
            if (state.recoverableDrafts.isNotEmpty() && kind != DONE) {
                item(key = "unsaved-work") {
                    UnsavedWorkNotice(
                        state.recoverableDrafts,
                        onReview = { actions.navigate(Route.DocumentRecovery) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(shown, key = { it.id }) { notice ->
                NoticeRow(notice, new = notice.id in newIds, onOpen = { notices.open(notice.id) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Android's notifications are off for iCode: what is said here is not said in the shade. */
@Composable
private fun NotificationsOff(onTurnOn: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = IcodeSpacing.Gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
    ) {
        Icon(Icons.Outlined.NotificationsOff, contentDescription = null, tint = IcodeColors.Amber, modifier = Modifier.padding(end = IcodeSpacing.Micro).then(Modifier))
        Text("Android's notifications are off for iCode", style = IcodeType.Caption, modifier = Modifier.weight(1f))
        IcodeTextButton("Turn On", onTurnOn)
    }
}

private const val ALL = "All"
private const val NEEDS_YOU = "Needs you"
private const val DONE = "Done"
private const val ALL_PLACES = "All places"
private const val ELSEWHERE = "Elsewhere"
