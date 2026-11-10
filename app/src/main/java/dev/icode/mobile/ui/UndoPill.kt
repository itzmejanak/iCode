package dev.icode.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodePillButton
import dev.icode.mobile.designsystem.IcodePillRow
import dev.icode.mobile.designsystem.LocalPillIconAtEnd
import dev.icode.mobile.designsystem.pillEnter
import dev.icode.mobile.designsystem.pillExit
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.files.FileHistoryState
import dev.icode.mobile.domain.files.FileNews
import kotlinx.coroutines.delay

/**
 * The undo pill's state, kept over every screen as the bell's is (docs/explorer-gaps-plan.md §3.8):
 * the change it tells, and whether it is awake — after a change, for its time — or at rest, faded
 * until touched. Each change is told once: the last one told is kept as the screen turns, and not
 * told again.
 */
@Stable
internal class UndoPillState(private var seen: Long? = null) {
    var told: FileNews? by mutableStateOf(null)
        private set
    var awake by mutableStateOf(false)
        private set

    /** One more each time the pill wakes: its time starts again. */
    var wakes by mutableIntStateOf(0)
        private set

    /** [news] told: the pill wakes; false when it was told before. */
    fun tell(news: FileNews): Boolean {
        if (news.id == seen) return false
        seen = news.id
        told = news
        awake = true
        wakes++
        return true
    }

    fun rest() {
        awake = false
    }

    companion object {
        val Saver = Saver<UndoPillState, Long>(save = { it.seen }, restore = { UndoPillState(it) })
    }
}

/** The pill's state, telling each new change of [news]: the pill awake for its time, then at rest. */
@Composable
internal fun rememberUndoPill(news: FileNews?): UndoPillState {
    val state = rememberSaveable(saver = UndoPillState.Saver) { UndoPillState() }
    LaunchedEffect(news?.id) { if (news != null) state.tell(news) }
    LaunchedEffect(state.wakes) {
        if (!state.awake) return@LaunchedEffect
        delay(FileNews.HOLD_MILLIS)
        state.rest()
    }
    return state
}

/**
 * The undo pill in the floating stack, above the bell and drawn as it is (owner's requests,
 * 2026-11-04 and 2026-11-10): see-through buttons only, ↶ to undo where the pill is docked and ↷
 * beside it when there is something to redo, each saying what it would do; there while anything can
 * be undone or redone, awake after a change and at rest on its time. TalkBack reads the change told.
 * The explorer's ⋮ has no Undo or Redo: this is at hand on every screen.
 */
@Composable
internal fun UndoPill(state: UndoPillState, history: FileHistoryState) {
    val gateway = LocalICodeGateway.current
    AnimatedVisibility(visible = history.undo != null || history.redo != null, enter = pillEnter(), exit = pillExit()) {
        IcodePillRow(
            "the undo pill",
            awake = state.awake,
            modifier = Modifier.semantics {
                state.told?.let { contentDescription = it.line }
                liveRegion = LiveRegionMode.Polite
            },
        ) { moves ->
            val undo = @Composable { Step(Icons.AutoMirrored.Outlined.Undo, history.undo?.let { "Undo: $it" }, moves) { gateway.dispatch(FilesIntent.Undo) } }
            val redo = @Composable { if (history.redo != null) Step(Icons.AutoMirrored.Outlined.Redo, "Redo: ${history.redo}") { gateway.dispatch(FilesIntent.Redo) } }
            // ↶ stands where the pill is docked, so ↷ coming and going never moves it from under the thumb.
            if (LocalPillIconAtEnd.current) {
                redo()
                undo()
            } else {
                undo()
                redo()
            }
        }
    }
}

/** ↶ or ↷, saying what it would do; greyed with nothing to do. */
@Composable
private fun Step(icon: ImageVector, what: String?, actions: List<CustomAccessibilityAction> = emptyList(), onClick: () -> Unit) {
    IcodePillButton(icon, what ?: "Nothing to undo", onClick, tint = IcodeColors.Accent, enabled = what != null, actions = actions)
}
