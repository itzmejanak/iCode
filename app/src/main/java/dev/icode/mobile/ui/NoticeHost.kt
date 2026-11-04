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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.icode.mobile.designsystem.CountBadge
import dev.icode.mobile.designsystem.IcodeFloatingAway
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
 * The bell and the sheet, over every screen (docs/notices-plan.md §2.2–2.3): what last rang the bell,
 * whether it has gone (its time up, × or its notice opened), and the notice in the sheet.
 */
@Stable
internal class NoticeHostState {
    var told: AppNotice.Message? by mutableStateOf(null)
        private set
    var away by mutableStateOf(false)
        private set
    var shown: String? by mutableStateOf(null)
        private set

    /** A notice was said: the bell rings, and comes back if it had gone. */
    fun arrived(notice: AppNotice.Message) {
        told = notice
        away = false
    }

    fun putAway() {
        away = true
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
 * [content] with [state] to open notices in, and the notice [state] shows in its sheet. The bell goes
 * on its time, wherever it is drawn ([NoticeBell]): the time of the most serious unread tone, or of
 * the tone that rang it.
 */
@Composable
internal fun NoticeHost(state: NoticeHostState, notices: List<AppNotice>, pageOpen: Boolean, onAll: () -> Unit, content: @Composable () -> Unit) {
    val gateway = LocalICodeGateway.current
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalNotices provides state) { content() }
        LaunchedEffect(state.told) {
            val notice = state.told ?: return@LaunchedEffect
            delay(NoticeRules.bellMillis(NoticeRules.bellTone(notices) ?: notice.tone))
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
 * The bell in the floating stack (owner's design, 2026-10-21; no line since 2026-11-04, the owner's
 * request), while it has something to tell and has not gone: a notice rings it once, in the most
 * serious unread tone — or a success's green, which is not kept — with the unread count as the
 * Explorer's Source Control count is drawn. TalkBack reads what rang it. × stands apart, on nothing
 * at the bell's top end. A tap opens the one unread notice; with more, [onAll].
 */
@Composable
internal fun NoticeBell(state: NoticeHostState, notices: List<AppNotice>, onAll: () -> Unit) {
    val unread = notices.filterNot { it.read }
    AnimatedVisibility(
        visible = state.told != null && !state.away,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        state.told?.let { notice ->
            Bell(
                told = notice,
                unread = unread,
                onTap = {
                    when (unread.size) {
                        0 -> state.putAway()
                        1 -> state.open(unread.single().id)
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
private fun Bell(told: AppNotice.Message, unread: List<AppNotice>, onTap: () -> Unit, onAway: () -> Unit) {
    val tone = NoticeRules.bellTone(unread) ?: told.tone
    // 0 to 1 over one ring; at rest at both ends.
    val ring = remember { Animatable(0f) }
    LaunchedEffect(told) {
        ring.snapTo(0f)
        ring.animateTo(1f, tween(RING_MILLIS, easing = LinearEasing))
        ring.snapTo(0f)
    }
    IcodeFloatingAway("Put the bell away", onAway) {
        IcodeFloatingPill {
            Box(
                Modifier
                    .size(IcodeSizes.TouchTarget)
                    .clickable(onClickLabel = "Show notifications", onClick = onTap)
                    .semantics {
                        contentDescription = "${told.title}. Notifications, ${unread.size} unread"
                        liveRegion = LiveRegionMode.Polite
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = tone.tint,
                    // A bell swings from where it hangs: a swing that dies away, three times over.
                    modifier = Modifier.size(IcodeSizes.IconLg).graphicsLayer {
                        val p = ring.value
                        rotationZ = MAX_SWING_DEGREES * (1 - p) * sin(p * SWINGS * 2 * PI.toFloat())
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    },
                )
                if (unread.isNotEmpty()) CountBadge(unread.size, Modifier.align(Alignment.Center))
            }
        }
    }
}

/** One ring of the bell, and how far and how often it swings. */
private const val RING_MILLIS = 900
private const val MAX_SWING_DEGREES = 18f
private const val SWINGS = 3
