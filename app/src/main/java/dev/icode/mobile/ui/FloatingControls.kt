package dev.icode.mobile.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import dev.icode.mobile.designsystem.IcodeFloatingStack
import dev.icode.mobile.designsystem.IcodeFloors
import dev.icode.mobile.designsystem.IcodeStackPill
import dev.icode.mobile.domain.FloatingPill
import dev.icode.mobile.domain.NoticeIntent
import dev.icode.mobile.domain.PillPlace
import dev.icode.mobile.ui.dock.ToolDock
import dev.icode.mobile.ui.dock.ToolDockState
import dev.icode.mobile.ui.screens.settings.rememberSettingSaver
import dev.icode.mobile.domain.notices.SettingsNotices

/**
 * The floating stack over every screen but the Notifications page (docs/explorer-gaps-plan.md §3.8,
 * docs/dock-navigation-plan.md D6, D11): the undo pill above the bell, the tool dock below them, each
 * where the user moved it to on any edge, kept app-wide. A pill let go is drawn at its new place at
 * once, while the place is written; one that could not be written is said.
 */
@Composable
internal fun FloatingControls(
    floors: IcodeFloors,
    visible: Boolean,
    undo: UndoPillState,
    notices: NoticeHostState,
    onAll: () -> Unit,
    dock: ToolDockState,
    navigation: NavigationState,
) {
    val snapshot = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val repository = LocalWorkspacePreferencesRepository.current
    val preferences = LocalWorkspacePreferences.current
    val saver = rememberSettingSaver(preferences, "The pill's place could not be kept")
    val stored = preferences.appearance.pillPlaces
    // Let go, ahead of the store; each gone once the store has it.
    val moved = remember { mutableStateMapOf<FloatingPill, PillPlace>() }
    LaunchedEffect(stored) { moved.keys.toList().forEach { pill -> if (stored[pill] == moved[pill]) moved.remove(pill) } }
    LaunchedEffect(saver.error) { saver.error?.let { gateway.dispatch(NoticeIntent.Tell(SettingsNotices.pillNotKept(it))) } }
    fun pill(which: FloatingPill, content: @Composable () -> Unit) = IcodeStackPill(
        key = which,
        // What floats under the finger while it is moved.
        icon = when (which) {
            FloatingPill.Undo -> Icons.AutoMirrored.Outlined.Undo
            FloatingPill.Bell -> Icons.Outlined.Notifications
            FloatingPill.Dock -> Icons.Outlined.Apps
        },
        place = moved[which] ?: stored[which],
        onPlaced = { place ->
            moved[which] = place
            saver.save { repository.setPillPlace(which, place) }
        },
        content = content,
    )
    IcodeFloatingStack(
        floors,
        // Not before the settings are read: the pills would show where they start, then move.
        visible = visible && preferences.loaded,
        pills = listOf(
            pill(FloatingPill.Undo) { UndoPill(undo, snapshot.explorer.history) },
            pill(FloatingPill.Bell) { NoticeBell(notices, snapshot.notices, onAll = onAll) },
            pill(FloatingPill.Dock) { ToolDock(dock, navigation) },
        ),
        onOutside = dock.onOutside,
    )
}
