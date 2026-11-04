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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeFloatingAway
import dev.icode.mobile.designsystem.IcodeFloatingPill
import dev.icode.mobile.designsystem.IcodeSizes
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
    var away by mutableStateOf(true)
        private set

    /** [news] told: the pill comes; false when it was told before. */
    fun tell(news: FileNews): Boolean {
        if (news.id == seen) return false
        seen = news.id
        told = news
        away = false
        return true
    }

    fun putAway() {
        away = true
    }

    companion object {
        val Saver = Saver<UndoPillState, Long>(save = { it.seen }, restore = { UndoPillState(it) })
    }
}

/** The pill's state, telling each new change of [news]: the pill for its time, then gone. */
@Composable
internal fun rememberUndoPill(news: FileNews?): UndoPillState {
    val state = rememberSaveable(saver = UndoPillState.Saver) { UndoPillState() }
    LaunchedEffect(news?.id) {
        if (news == null || !state.tell(news)) return@LaunchedEffect
        delay(FileNews.HOLD_MILLIS)
        state.putAway()
    }
    return state
}

/**
 * The undo pill in the floating stack, above the bell and drawn as it is: icons only (owner's request,
 * 2026-11-04 — a long name stretched the pill), ↶ to undo and ↷ when there is something to redo, × apart
 * at its top end. Each says what it would do; TalkBack reads the change told — "Moved 3 items to Trash".
 */
@Composable
internal fun UndoPill(state: UndoPillState, history: FileHistoryState) {
    val gateway = LocalICodeGateway.current
    AnimatedVisibility(
        visible = state.told != null && !state.away,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        state.told?.let { news ->
            IcodeFloatingAway("Put the undo pill away", state::putAway) {
                IcodeFloatingPill(Modifier.semantics { contentDescription = news.line; liveRegion = LiveRegionMode.Polite }) {
                    Step(Icons.AutoMirrored.Outlined.Undo, history.undo?.let { "Undo: $it" }) { gateway.dispatch(FilesIntent.Undo) }
                    if (history.redo != null) Step(Icons.AutoMirrored.Outlined.Redo, "Redo: ${history.redo}") { gateway.dispatch(FilesIntent.Redo) }
                }
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
