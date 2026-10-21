package dev.icode.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.icode.mobile.designsystem.CountBadge
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.edgeLine
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
 * [content] with [state] to open notices in; over it the bell, unless the Notifications page is
 * open ([pageOpen]) or a sheet is, and the notice [state] shows. [onAll] goes to the page.
 */
@Composable
internal fun NoticeHost(state: NoticeHostState, notices: List<AppNotice>, pageOpen: Boolean, onAll: () -> Unit, content: @Composable () -> Unit) {
    val gateway = LocalICodeGateway.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalNotices provides state) { content() }
        val told = state.told
        val unread = notices.filterNot { it.read }
        // The line, then the bell alone for its time once the line has slid away.
        LaunchedEffect(told) {
            val notice = told ?: return@LaunchedEffect
            delay(NoticeRules.lineMillis(notice.tone))
            state.fold()
            delay(SLIDE_MILLIS + NoticeRules.BELL_MILLIS)
            state.putAway()
        }
        AnimatedVisibility(
            visible = told != null && !pageOpen && state.shown == null && !state.away,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = IcodeSpacing.Gutter, bottom = IcodeSizes.FabClearance),
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            told?.let { notice ->
                Bell(
                    told = notice,
                    unfolded = state.unfolded,
                    unread = unread,
                    lineWidth = maxWidth * LINE_SHARE,
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
 * The bell (owner's design, 2026-10-21). Unfolded: the bell in its tone's colour and [told]'s line.
 * Folded: the line slides away from left to right, then the bell rings once, in the most serious
 * unread tone, with the unread count as the Explorer's Source Control count is drawn, and × beside it.
 */
@Composable
private fun Bell(told: AppNotice.Message, unfolded: Boolean, unread: List<AppNotice>, lineWidth: Dp, onTap: () -> Unit, onAway: () -> Unit) {
    val tone = if (unfolded) told.tone else NoticeRules.bellTone(unread) ?: told.tone
    // 0 to 1 over one ring; at rest at both ends.
    val ring = remember { Animatable(0f) }
    LaunchedEffect(unfolded) {
        // A new line cuts a ring short: the bell hangs straight again.
        if (unfolded) return@LaunchedEffect ring.snapTo(0f)
        delay(SLIDE_MILLIS)
        ring.snapTo(0f)
        ring.animateTo(1f, tween(RING_MILLIS, easing = LinearEasing))
        ring.snapTo(0f)
    }
    val line = edgeLine
    Row(
        Modifier
            .height(IcodeSizes.TouchTarget)
            .clip(IcodeShapes.Pill)
            .background(IcodeColors.SurfaceElevated)
            .then(if (line != null) Modifier.border(1.dp, line, IcodeShapes.Pill) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            AnimatedVisibility(
                visible = unfolded,
                enter = expandHorizontally(tween(SLIDE_MILLIS.toInt()), expandFrom = Alignment.End) + fadeIn(tween(SLIDE_MILLIS.toInt())),
                exit = shrinkHorizontally(tween(SLIDE_MILLIS.toInt()), shrinkTowards = Alignment.End) + fadeOut(tween(SLIDE_MILLIS.toInt())),
            ) {
                Text(
                    told.title,
                    style = IcodeType.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // TalkBack reads it as it read a toast.
                    modifier = Modifier.widthIn(max = lineWidth).padding(end = IcodeSpacing.Space4).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        // × comes once the line has gone, so the bell does not jostle while it slides.
        AnimatedVisibility(
            visible = !unfolded,
            enter = expandHorizontally(tween(SLIDE_MILLIS.toInt(), delayMillis = SLIDE_MILLIS.toInt()), expandFrom = Alignment.Start) +
                fadeIn(tween(SLIDE_MILLIS.toInt(), delayMillis = SLIDE_MILLIS.toInt())),
            exit = fadeOut(tween(SLIDE_MILLIS.toInt())),
        ) {
            IconButton(onClick = onAway) {
                Icon(Icons.Outlined.Close, contentDescription = "Put the bell away", tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconSm))
            }
        }
    }
}

/** The line takes at most this share of the screen's width. */
private const val LINE_SHARE = 0.75f

/** The line's slide, in and out. */
private const val SLIDE_MILLIS = 350L

/** One ring of the bell, and how far and how often it swings. */
private const val RING_MILLIS = 900
private const val MAX_SWING_DEGREES = 18f
private const val SWINGS = 3
