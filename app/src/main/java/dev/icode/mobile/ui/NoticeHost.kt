package dev.icode.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import dev.icode.mobile.designsystem.CountBadge
import dev.icode.mobile.designsystem.FLOATING_SLIDE_MILLIS
import dev.icode.mobile.designsystem.IcodeFloatingAway
import dev.icode.mobile.designsystem.IcodeFloatingLine
import dev.icode.mobile.designsystem.IcodeFloatingPill
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.domain.AppNotice
import dev.icode.mobile.domain.NoticeIntent
import dev.icode.mobile.domain.NoticeRules
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * The bell and the sheet, over every screen (docs/notices-plan.md §2.2–2.3): what the bell last
 * told, whether its line still shows, whether it has gone (its time up, × or its notice opened), and
 * the notice in the sheet.
 */
@Stable
internal class NoticeHostState {
    var told: AppNotice.Message? by mutableStateOf(null)
        private set
    var unfolded by mutableStateOf(false)
        private set
    var away by mutableStateOf(false)
        private set
    var shown: String? by mutableStateOf(null)
        private set

    /** A notice was said: its line unfolds, and the bell comes back if it had gone. */
    fun arrived(notice: AppNotice.Message) {
        told = notice
        unfolded = true
        away = false
    }

    fun fold() {
        unfolded = false
    }

    fun putAway() {
        away = true
        unfolded = false
    }

    /** The notice [id] in its sheet; the bell, having led there, goes. */
    fun open(id: String) {
        putAway()
        shown = id
    }

    fun close() {
        shown = null
    }
}

internal val LocalNotices = staticCompositionLocalOf<NoticeHostState> { error("NoticeHostState was not provided") }

/**
 * [content] with [state] to open notices in, and the notice [state] shows in its sheet. The bell's
 * line folds, then the bell goes, on their times, wherever the bell is drawn ([NoticeBell]).
 */
@Composable
internal fun NoticeHost(state: NoticeHostState, notices: List<AppNotice>, pageOpen: Boolean, onAll: () -> Unit, content: @Composable () -> Unit) {
    val gateway = LocalICodeGateway.current
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalNotices provides state) { content() }
        // The line, then the bell alone for its time once the line has slid away.
        LaunchedEffect(state.told) {
            val notice = state.told ?: return@LaunchedEffect
            delay(NoticeRules.lineMillis(notice.tone))
            state.fold()
            delay(FLOATING_SLIDE_MILLIS + NoticeRules.BELL_MILLIS)
            state.putAway()
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
 * The bell in the floating stack (owner's design, 2026-10-21), while it has something to tell and
 * has not gone. Unfolded: the bell in its tone's colour and the told line, at most [lineWidth].
 * Folded: the line slides away from left to right, then the bell rings once, in the most serious
 * unread tone, with the unread count as the Explorer's Source Control count is drawn, and × beside
 * it. A tap opens the notice told, or the one unread; with more, [onAll].
 */
@Composable
internal fun NoticeBell(state: NoticeHostState, notices: List<AppNotice>, lineWidth: Dp, onAll: () -> Unit) {
    val unread = notices.filterNot { it.read }
    AnimatedVisibility(
        visible = state.told != null && !state.away,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        state.told?.let { notice ->
            Bell(
                told = notice,
                unfolded = state.unfolded,
                unread = unread,
                lineWidth = lineWidth,
                onTap = {
                    when {
                        state.unfolded && NoticeRules.kept(notice) -> state.open(notice.id)
                        state.unfolded -> state.fold()
                        unread.size == 1 -> state.open(unread.single().id)
                        else -> {
                            state.putAway()
                            onAll()
                        }
                    }
                },
                onAway = state::putAway,
            )
        }
    }
}

@Composable
private fun Bell(told: AppNotice.Message, unfolded: Boolean, unread: List<AppNotice>, lineWidth: Dp, onTap: () -> Unit, onAway: () -> Unit) {
    val tone = if (unfolded) told.tone else NoticeRules.bellTone(unread) ?: told.tone
    // 0 to 1 over one ring; at rest at both ends.
    val ring = remember { Animatable(0f) }
    LaunchedEffect(unfolded) {
        // A new line cuts a ring short: the bell hangs straight again.
        if (unfolded) return@LaunchedEffect ring.snapTo(0f)
        delay(FLOATING_SLIDE_MILLIS.toLong())
        ring.snapTo(0f)
        ring.animateTo(1f, tween(RING_MILLIS, easing = LinearEasing))
        ring.snapTo(0f)
    }
    IcodeFloatingPill {
        Row(
            Modifier.fillMaxHeight().clickable(onClickLabel = if (unfolded) "Show this notification" else "Show notifications", onClick = onTap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(IcodeSizes.TouchTarget), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Notifications,
                    contentDescription = if (unfolded) null else "Notifications, ${unread.size} unread",
                    tint = tone.tint,
                    // A bell swings from where it hangs: a swing that dies away, three times over.
                    modifier = Modifier.size(IcodeSizes.IconLg).graphicsLayer {
                        val p = ring.value
                        rotationZ = MAX_SWING_DEGREES * (1 - p) * sin(p * SWINGS * 2 * PI.toFloat())
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    },
                )
                if (!unfolded && unread.isNotEmpty()) CountBadge(unread.size, Modifier.align(Alignment.Center))
            }
            IcodeFloatingLine(unfolded, told.title, lineWidth)
        }
        IcodeFloatingAway(!unfolded, "Put the bell away", onAway)
    }
}

/** One ring of the bell, and how far and how often it swings. */
private const val RING_MILLIS = 900
private const val MAX_SWING_DEGREES = 18f
private const val SWINGS = 3
