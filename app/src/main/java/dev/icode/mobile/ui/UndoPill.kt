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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodePillButton
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
 * 2026-11-04 and 2026-11-10): one see-through icon, whose state is what a tap does — ↶ while a change
 * can be undone, ↷ once only a redo is left, so undoing the last change turns it into its redo. While
 * both can be done, it shows ↶, and held, it redoes. It is there while anything can be undone or
 * redone, awake after a change and resting in its edge on its time. TalkBack reads the change told and
 * offers the redo. The explorer's ⋮ has no Undo or Redo: this is at hand on every screen.
 */
@Composable
internal fun UndoPill(state: UndoPillState, history: FileHistoryState) {
    val gateway = LocalICodeGateway.current
    AnimatedVisibility(visible = history.undo != null || history.redo != null, enter = pillEnter(), exit = pillExit()) {
        val undo = history.undo
        val redo = history.redo?.let { "Redo: $it" }
        val redoes = undo == null
        IcodePillButton(
            "the undo pill",
            if (redoes) Icons.AutoMirrored.Outlined.Redo else Icons.AutoMirrored.Outlined.Undo,
            description = if (redoes) redo else "Undo: $undo",
            onClick = { gateway.dispatch(if (redoes) FilesIntent.Redo else FilesIntent.Undo) },
            awake = state.awake,
            modifier = Modifier.semantics {
                state.told?.let { contentDescription = it.line }
                liveRegion = LiveRegionMode.Polite
            },
            tint = IcodeColors.Accent,
            onLongClick = if (!redoes && redo != null) ({ gateway.dispatch(FilesIntent.Redo) }) else null,
            longClickLabel = redo.takeUnless { redoes },
        )
    }
}
