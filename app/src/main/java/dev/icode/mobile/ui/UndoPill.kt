package dev.icode.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeDockedAction
import dev.icode.mobile.designsystem.IcodeDockedPill
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.files.FileHistoryState
import dev.icode.mobile.domain.files.FileNews
import kotlinx.coroutines.delay

/**
 * The undo pill's state, kept over every screen as the bell's is (docs/explorer-gaps-plan.md §3.8):
 * the change it tells and whether it has gone. Each change is told once: the last one told is kept
 * as the screen turns, and not told again.
 */
@Stable
internal class UndoPillState(private var seen: Long? = null) {
    var told: FileNews? by mutableStateOf(null)
        private set
    var tucked by mutableStateOf(true)
        private set

    /** One more each time the pill comes out: its time starts again. */
    var outs by mutableIntStateOf(0)
        private set

    /** [news] told: the pill comes out; false when it was told before. */
    fun tell(news: FileNews): Boolean {
        if (news.id == seen) return false
        seen = news.id
        told = news
        show()
        return true
    }

    fun show() {
        tucked = false
        outs++
    }

    fun tuck() {
        tucked = true
    }

    companion object {
        val Saver = Saver<UndoPillState, Long>(save = { it.seen }, restore = { UndoPillState(it) })
    }
}

/** The pill's state, telling each new change of [news]: the pill out for its time, then tucked. */
@Composable
internal fun rememberUndoPill(news: FileNews?): UndoPillState {
    val state = rememberSaveable(saver = UndoPillState.Saver) { UndoPillState() }
    LaunchedEffect(news?.id) { if (news != null) state.tell(news) }
    LaunchedEffect(state.outs) {
        if (state.tucked) return@LaunchedEffect
        delay(FileNews.HOLD_MILLIS)
        state.tuck()
    }
    return state
}

/**
 * The undo pill in the floating stack, above the bell and drawn as it is (owner's requests,
 * 2026-11-04): icons only, ↶ to undo and ↷ when there is something to redo, each saying what it would
 * do; docked on the edge while anything can be undone or redone, out after a change and tucked to
 * its grip on its time or swiped in, pulled or tapped out again. TalkBack reads the change told.
 * The explorer's ⋮ has no Undo or Redo: this is at hand on every screen.
 */
@Composable
internal fun UndoPill(state: UndoPillState, history: FileHistoryState) {
    val gateway = LocalICodeGateway.current
    AnimatedVisibility(
        visible = history.undo != null || history.redo != null,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        IcodeDockedPill(
            tucked = state.tucked,
            onTucked = { if (it) state.tuck() else state.show() },
            name = "the undo pill",
            modifier = Modifier.semantics {
                state.told?.let { contentDescription = it.line }
                liveRegion = LiveRegionMode.Polite
            },
        ) {
            Step(Icons.AutoMirrored.Outlined.Undo, history.undo?.let { "Undo: $it" }) { gateway.dispatch(FilesIntent.Undo) }
            if (history.redo != null) Step(Icons.AutoMirrored.Outlined.Redo, "Redo: ${history.redo}") { gateway.dispatch(FilesIntent.Redo) }
        }
    }
}

/** ↶ or ↷, saying what it would do; greyed with nothing to do. */
@Composable
private fun Step(icon: ImageVector, what: String?, onClick: () -> Unit) {
    IcodeDockedAction(icon, what ?: "Nothing to undo", IcodeColors.Accent, onClick, enabled = what != null)
}
