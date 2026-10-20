package dev.icode.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.designsystem.edgeLine
import dev.icode.mobile.domain.AppNotice
import dev.icode.mobile.domain.NoticeIntent
import dev.icode.mobile.domain.NoticeRules
import kotlinx.coroutines.delay

/**
 * The bell and the sheet, over every screen (docs/notices-plan.md §2.2–2.3): what the bell last
 * told, whether its line still shows, whether × put it away, and the notice in the sheet.
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

    /** A notice was said: its line unfolds, and the bell comes back if × put it away. */
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

    /** The notice [id] in its sheet. */
    fun open(id: String) {
        unfolded = false
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
        LaunchedEffect(told) {
            told?.let { delay(NoticeRules.lineMillis(it.tone)) }
            state.fold()
        }
        AnimatedVisibility(
            visible = told != null && !pageOpen && state.shown == null && !state.away && (state.unfolded || unread.isNotEmpty()),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = IcodeSpacing.Gutter, bottom = IcodeSizes.FabClearance),
            enter = fadeIn(),
            exit = fadeOut(),
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
                            else -> onAll()
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
 * The bell: unfolded, its tone's colour and [told]'s line; folded, the most serious unread tone,
 * their count and × to put it away. The line folds away from left to right, toward the bell's edge.
 */
@Composable
private fun Bell(told: AppNotice.Message, unfolded: Boolean, unread: List<AppNotice>, lineWidth: Dp, onTap: () -> Unit, onAway: () -> Unit) {
    val tone = if (unfolded) told.tone else NoticeRules.bellTone(unread) ?: told.tone
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
            Modifier
                .fillMaxHeight()
                .clickable(onClickLabel = if (unfolded) "Show this notification" else "Show notifications", onClick = onTap)
                .padding(horizontal = IcodeSpacing.Space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            Box {
                Icon(
                    Icons.Outlined.Notifications,
                    contentDescription = if (unfolded) null else "Notifications, ${unread.size} unread",
                    tint = tone.tint,
                    modifier = Modifier.size(IcodeSizes.IconLg),
                )
                if (!unfolded && unread.isNotEmpty()) {
                    IconBadge(
                        if (unread.size > MAX_COUNTED) "$MAX_COUNTED+" else "${unread.size}",
                        tint = IcodeColors.Background,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = BADGE_OFFSET, y = -BADGE_OFFSET),
                        size = IcodeSizes.IconSm,
                        background = tone.tint,
                        style = IcodeType.Micro,
                    )
                }
            }
            AnimatedVisibility(
                visible = unfolded,
                enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
            ) {
                Text(
                    told.title,
                    style = IcodeType.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // TalkBack reads it as it read a toast.
                    modifier = Modifier.widthIn(max = lineWidth).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        AnimatedVisibility(visible = !unfolded, enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(), exit = fadeOut()) {
            IconButton(onClick = onAway) {
                Icon(Icons.Outlined.Close, contentDescription = "Put the bell away", tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconSm))
            }
        }
    }
}

/** The line takes at most this share of the screen's width. */
private const val LINE_SHARE = 0.75f

/** Counts past this read as "99+". */
private const val MAX_COUNTED = 99

private val BADGE_OFFSET = 6.dp
