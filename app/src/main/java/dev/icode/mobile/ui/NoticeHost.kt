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
import dev.icode.mobile.domain.AppNotice
import dev.icode.mobile.domain.NoticeIntent
import dev.icode.mobile.domain.NoticeRules
import dev.icode.mobile.domain.NoticeTone
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * The bell and the sheet, over every screen (docs/notices-plan.md §2.2–2.3): what last rang the bell,
 * whether it is awake — rung, for its time — or at rest, faded until touched (its time up, or its
 * notice opened); it never goes. And the notice in the sheet.
 */
@Stable
internal class NoticeHostState {
    var told: AppNotice.Message? by mutableStateOf(null)
        private set
    var awake by mutableStateOf(false)
        private set

    /** One more each time the bell wakes: its time starts again. */
    var wakes by mutableIntStateOf(0)
        private set
    var shown: String? by mutableStateOf(null)
        private set

    /** A notice was said: the bell rings, awake. */
    fun arrived(notice: AppNotice.Message) {
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
 * [content] with [state] to open notices in, and the notice [state] shows in its sheet. The bell, out,
 * tucks on its time, wherever it is drawn ([NoticeBell]): the time of the most serious unread tone,
 * or of the tone that rang it.
 */
@Composable
internal fun NoticeHost(state: NoticeHostState, notices: List<AppNotice>, pageOpen: Boolean, onAll: () -> Unit, content: @Composable () -> Unit) {
    val gateway = LocalICodeGateway.current
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalNotices provides state) { content() }
        LaunchedEffect(state.wakes) {
            if (!state.awake) return@LaunchedEffect
            delay(NoticeRules.bellMillis(NoticeRules.bellTone(notices) ?: state.told?.tone ?: NoticeTone.Info))
            state.rest()
        }
        state.shown?.let { id ->
            val notice = notices.firstOrNull { it.id == id }
            // Read once it is opened; gone (deleted, cleared), its sheet goes with it.
            LaunchedEffect(id, notice == null) { if (notice == null) state.close() else gateway.dispatch(NoticeIntent.Read(id)) }
            notice?.let {
                IcodeSheetOverlay(onClose = state::close) {
                    NoticeSheet(
                        it,
                        Modifier.align(Alignment.BottomCenter),
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
 * most serious unread tone — or a success's green, which is not kept — with the unread count as the
 * Explorer's Source Control count is drawn; TalkBack reads what rang it. On its time it rests, half in
 * its edge and faded, keeping the unread tone and count, until touched. A tap opens the one unread
 * notice; otherwise the Notifications page ([onAll]).
 */
@Composable
internal fun NoticeBell(state: NoticeHostState, notices: List<AppNotice>, onAll: () -> Unit) {
    val unread = notices.filterNot { it.read }
    val told = state.told
    val unreadTone = NoticeRules.bellTone(unread)
    // 0 to 1 over one ring; at rest at both ends.
    val ring = remember { Animatable(0f) }
    LaunchedEffect(told) {
        if (told == null) return@LaunchedEffect
        ring.snapTo(0f)
        ring.animateTo(1f, tween(RING_MILLIS, easing = LinearEasing))
        ring.snapTo(0f)
    }
    IcodePillButton(
        "the bell",
        Icons.Outlined.Notifications,
        description = listOfNotNull(told?.title, "Notifications, ${unread.size} unread").joinToString(". "),
        onClick = {
            val one = unread.singleOrNull()
            if (one != null) {
                state.open(one.id)
            } else {
                state.rest()
                onAll()
            }
        },
        awake = state.awake,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        tint = (unreadTone ?: told?.tone)?.tint ?: IcodeColors.TextSecondary,
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
