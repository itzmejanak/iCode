package dev.icode.mobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodePillButton
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.rememberPillsHidden
import dev.icode.mobile.designsystem.shownFor
import dev.icode.mobile.domain.notices.Notice
import dev.icode.mobile.domain.notices.NoticeAction
import dev.icode.mobile.domain.NoticeIntent
import dev.icode.mobile.domain.notices.NoticeRules
import dev.icode.mobile.domain.notices.NoticeTone
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.flow.first

/**
 * The bell and the sheet, over every screen (docs/notices-plan.md §2.2–2.3): what last rang the bell,
 * whether it is awake — rung, for its time — or at rest, faded until touched (its time up, or its
 * notice opened); it never goes. And the notice in the sheet.
 */
@Stable
internal class NoticeHostState {
    var told: Notice? by mutableStateOf(null)
        private set
    var awake by mutableStateOf(false)
        private set

    /** One more each time the bell wakes: its time starts again. */
    var wakes by mutableIntStateOf(0)
        private set
    var shown: String? by mutableStateOf(null)
        private set

    /** A notice was told: the bell rings, awake, unless it is kept as done (docs/notices-redesign-plan.md D3). */
    fun arrived(notice: Notice) {
        if (!NoticeRules.rings(notice)) return
        told = notice
        wake()
    }

    fun wake() {
        awake = true
        wakes++
    }

    fun rest() {
        awake = false
    }

    /** The notice [id] in its sheet; the bell, having led there, rests. */
    fun open(id: String) {
        rest()
        shown = id
    }

    fun close() {
        shown = null
    }
}

internal val LocalNotices = staticCompositionLocalOf<NoticeHostState> { error("NoticeHostState was not provided") }

/**
 * [content] with [state] to open notices in, and the notice [state] shows in its sheet, its actions
 * done by [onAct]. The bell itself, and its time awake, are [NoticeBell]'s.
 */
@Composable
internal fun NoticeHost(
    state: NoticeHostState,
    notices: List<Notice>,
    pageOpen: Boolean,
    onAll: () -> Unit,
    onAct: (NoticeAction) -> Unit,
    content: @Composable () -> Unit,
) {
    val gateway = LocalICodeGateway.current
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalNotices provides state) { content() }
        state.shown?.let { id ->
            val notice = notices.firstOrNull { it.id == id }
            // Read once it is opened; gone (deleted, cleared), its sheet goes with it.
            LaunchedEffect(id, notice == null) { if (notice == null) state.close() else gateway.dispatch(NoticeIntent.Read(id)) }
            notice?.let {
                IcodeSheetOverlay(onClose = state::close) {
                    NoticeSheet(
                        it,
                        Modifier.align(Alignment.BottomCenter),
                        onAct = { action ->
                            state.close()
                            onAct(action)
                        },
                        onDelete = {
                            gateway.dispatch(NoticeIntent.Delete(id))
                            state.close()
                        },
                        onAll = if (pageOpen) null else ({ state.close(); onAll() }),
                        onClose = state::close,
                    )
                }
            }
        }
    }
}

/**
 * The bell in the floating stack (owner's design, 2026-10-21; since 2026-11-10, a see-through button
 * the user can move, docs/dock-navigation-plan.md D13): a notice wakes it and rings it once, in the
 * most serious tone counted — or in the tone that rang it — with the count of what needs the user, as
 * the Explorer's Source Control count is drawn; what is kept as done does not ring it (docs/notices-redesign-plan.md
 * D3, D5). TalkBack reads what rang it. On its time, the time of the most serious tone counted or of
 * the tone that rang it, it rests, half in its edge and faded, keeping its tone and count, until
 * touched. Its ring and its time are spent where it is seen: told while the stack is hidden (under a
 * sheet, on the Notifications page), it rings once it is back, and its time counts from then
 * ([shownFor]). A tap, while it is awake, opens what rang it; at rest, the one notice that needs the
 * user; otherwise the Notifications page ([onAll]).
 */
@Composable
internal fun NoticeBell(state: NoticeHostState, notices: List<Notice>, onAll: () -> Unit) {
    val unread = notices.filter(NoticeRules::counted)
    val told = state.told
    val unreadTone = NoticeRules.bellTone(notices)
    val hidden = rememberPillsHidden()
    LaunchedEffect(state.wakes) {
        if (!state.awake) return@LaunchedEffect
        hidden.shownFor(NoticeRules.bellMillis(NoticeRules.bellTone(notices) ?: state.told?.tone ?: NoticeTone.Info))
        state.rest()
    }
    // 0 to 1 over one ring; at rest at both ends.
    val ring = remember { Animatable(0f) }
    LaunchedEffect(told) {
        if (told == null) return@LaunchedEffect
        hidden.first { !it }
        ring.snapTo(0f)
        ring.animateTo(1f, tween(RING_MILLIS, easing = LinearEasing))
        ring.snapTo(0f)
    }
    IcodePillButton(
        "the bell",
        Icons.Outlined.Notifications,
        description = listOfNotNull(told?.title, "Notifications, ${unread.size} unread").joinToString(". "),
        onClick = {
            val rang = told?.takeIf { state.awake && NoticeRules.kept(it) && notices.any { kept -> kept.id == it.id } }
            val one = rang ?: unread.singleOrNull()
            if (one != null) {
                state.open(one.id)
            } else {
                state.rest()
                onAll()
            }
        },
        awake = state.awake,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        tint = (unreadTone ?: told?.tone?.takeIf { state.awake })?.tint ?: IcodeColors.TextSecondary,
        badge = unread.size.takeIf { it > 0 },
        // A bell swings from where it hangs: a swing that dies away, three times over.
        iconModifier = Modifier.graphicsLayer {
            val p = ring.value
            rotationZ = MAX_SWING_DEGREES * (1 - p) * sin(p * SWINGS * 2 * PI.toFloat())
            transformOrigin = TransformOrigin(0.5f, 0f)
        },
    )
}

/** One ring of the bell, and how far and how often it swings. */
private const val RING_MILLIS = 900
private const val MAX_SWING_DEGREES = 18f
private const val SWINGS = 3
