package dev.icode.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import dev.icode.mobile.designsystem.FLOATING_SLIDE_MILLIS
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeFloatingAway
import dev.icode.mobile.designsystem.IcodeFloatingLine
import dev.icode.mobile.designsystem.IcodeFloatingPill
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeTextButton
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.files.FileHistoryState
import dev.icode.mobile.domain.files.FileNews
import kotlinx.coroutines.delay

/**
 * The undo pill's state, kept over every screen as the bell's is (docs/explorer-gaps-plan.md §3.8):
 * the change it tells, whether its line still shows, and whether it has gone. Each change is told
 * once: the last one told is kept as the screen turns, and not told again.
 */
@Stable
internal class UndoPillState(private var seen: Long? = null) {
    var told: FileNews? by mutableStateOf(null)
        private set
    var unfolded by mutableStateOf(false)
        private set
    var away by mutableStateOf(true)
        private set

    /** [news] told, its line unfolded; false when it was told before. */
    fun tell(news: FileNews): Boolean {
        if (news.id == seen) return false
        seen = news.id
        told = news
        unfolded = true
        away = false
        return true
    }

    fun fold() {
        unfolded = false
    }

    fun putAway() {
        away = true
        unfolded = false
    }

    companion object {
        val Saver = Saver<UndoPillState, Long>(save = { it.seen }, restore = { UndoPillState(it) })
    }
}

/** The pill's state, telling each new change of [news]: its line, then the pill alone for its time, then gone. */
@Composable
internal fun rememberUndoPill(news: FileNews?): UndoPillState {
    val state = rememberSaveable(saver = UndoPillState.Saver) { UndoPillState() }
    LaunchedEffect(news?.id) {
        if (news == null || !state.tell(news)) return@LaunchedEffect
        delay(FileNews.LINE_MILLIS)
        state.fold()
        delay(FLOATING_SLIDE_MILLIS + FileNews.HOLD_MILLIS)
        state.putAway()
    }
    return state
}

/**
 * The undo pill in the floating stack, above the bell and drawn as it is: ↶ to undo; while its line
 * shows, the change told — "Moved 3 items to Trash" — and Undo, or, undone, what came back and Redo.
 * Folded, ↷ joins ↶ when there is something to redo, with × beside them. Each says what it would do.
 */
@Composable
internal fun UndoPill(state: UndoPillState, history: FileHistoryState, lineWidth: Dp) {
    val gateway = LocalICodeGateway.current
    AnimatedVisibility(
        visible = state.told != null && !state.away,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        state.told?.let { news ->
            IcodeFloatingPill {
                Step(Icons.AutoMirrored.Outlined.Undo, history.undo?.let { "Undo: $it" }) { gateway.dispatch(FilesIntent.Undo) }
                IcodeFloatingLine(state.unfolded, news.line, lineWidth) {
                    IcodeTextButton(if (news.undone) "Redo" else "Undo", { gateway.dispatch(if (news.undone) FilesIntent.Redo else FilesIntent.Undo) })
                }
                if (!state.unfolded && history.redo != null) Step(Icons.AutoMirrored.Outlined.Redo, "Redo: ${history.redo}") { gateway.dispatch(FilesIntent.Redo) }
                IcodeFloatingAway(!state.unfolded, "Put the undo pill away", state::putAway)
            }
        }
    }
}

/** ↶ or ↷, saying what it would do; greyed with nothing to do. */
@Composable
private fun Step(icon: ImageVector, what: String?, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = what != null) {
        Icon(icon, contentDescription = what ?: "Nothing to undo", tint = if (what != null) IcodeColors.Accent else IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconMd))
    }
}
