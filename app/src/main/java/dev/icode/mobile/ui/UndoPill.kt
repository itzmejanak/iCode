package dev.icode.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodePillButton
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
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

    /** Its sheet is open ([UndoSheet]). */
    var opened by mutableStateOf(false)
        private set

    /** The pill tapped: its sheet opens, and the pill, having led there, rests. */
    fun open() {
        rest()
        opened = true
    }

    fun close() {
        opened = false
    }

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
 * 2026-11-04 and 2026-11-10): one see-through icon — ↶ while a change can be undone, ↷ once only a
 * redo is left. It is there while anything can be undone or redone, awake after a change and resting
 * in its edge on its time. A tap opens its sheet ([UndoSheet]) and changes nothing (owner's choice,
 * 2026-11-11: docs/notices-redesign-plan.md D5). TalkBack reads the change told. The explorer's ⋮ has
 * no Undo or Redo: this is at hand on every screen.
 */
@Composable
internal fun UndoPill(state: UndoPillState, history: FileHistoryState) {
    AnimatedVisibility(visible = history.undo != null || history.redo != null, enter = pillEnter(), exit = pillExit()) {
        IcodePillButton(
            "the undo pill",
            if (history.undo == null) Icons.AutoMirrored.Outlined.Redo else Icons.AutoMirrored.Outlined.Undo,
            description = listOfNotNull(state.told?.line, "Undo or Redo").joinToString(". "),
            onClick = state::open,
            awake = state.awake,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            tint = IcodeColors.Accent,
        )
    }
}

/**
 * What the undo pill opens (docs/notices-redesign-plan.md D5): the change last made or undone, and
 * where — "Moved notes.md to Trash", "phone · src" — then what Undo and what Redo would do, each with
 * its button. A look never changes a file; a button closes the sheet, and the pill tells what it did.
 * It closes by itself once there is nothing left to undo or redo.
 */
@Composable
internal fun UndoSheet(state: UndoPillState, history: FileHistoryState) {
    val gateway = LocalICodeGateway.current
    val nothing = history.undo == null && history.redo == null
    LaunchedEffect(nothing) { if (nothing) state.close() }
    if (!state.opened || nothing) return
    val choices = listOfNotNull(history.undo?.let { "Undo" to FilesIntent.Undo }, history.redo?.let { "Redo" to FilesIntent.Redo })
    fun choose(intent: FilesIntent) {
        state.close()
        gateway.dispatch(intent)
    }
    IcodeSheetOverlay(onClose = state::close) {
        IcodeBottomSheet(
            title = history.news?.line ?: history.undo ?: history.redo.orEmpty(),
            onClose = state::close,
            modifier = Modifier.align(Alignment.BottomCenter),
            subtitle = history.news?.place?.line?.takeIf { it.isNotEmpty() },
            titleLines = Int.MAX_VALUE,
            footer = {
                choices.first().let { (label, intent) -> PrimaryButton(label, { choose(intent) }, Modifier.fillMaxWidth()) }
                choices.getOrNull(1)?.let { (label, intent) -> SecondaryButton(label, { choose(intent) }, Modifier.fillMaxWidth()) }
            },
        ) {
            history.undo?.let { DetailRow("Undo", it) }
            history.redo?.let { DetailRow("Redo", it) }
        }
    }
}
