package dev.icode.mobile.designsystem

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A floating pill as one see-through button (docs/dock-navigation-plan.md D12–D14): [icon] in [tint]
 * on a faint round of the raised surface, so it reads over code while the screen shows through it,
 * with [badge], a count, and a [dot] on it; a touch target wide and tall, the round inside it, so what
 * it carries is whole. A new [icon] crossfades in: the undo pill's ↶ turning ↷.
 *
 * Left alone while not [awake], it rests as the assistant's bubble over other apps does
 * ([IcodeResting]): slowly, it sinks half behind its edge ([LocalPillEdge]) and fades, out of the
 * way of the work; a touch or anything new brings it out at once. Its time to rest counts only while
 * it is shown ([shownFor]), so it never sinks unseen under a sheet. A tap on it at rest is still a tap.
 * While it is shown it is kept from Android's back gesture, which would otherwise take a touch at the
 * screen's edge ([keptFromBackGesture]).
 *
 * [onLongClick], held, does the pill's other action, named [longClickLabel]. TalkBack has that, then
 * [actions], then the stack's moves for [name].
 */
@Composable
fun IcodePillButton(
    name: String,
    icon: ImageVector,
    description: String?,
    onClick: () -> Unit,
    awake: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = IcodeColors.TextSecondary,
    badge: Int? = null,
    dot: Color? = null,
    iconModifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    actions: List<CustomAccessibilityAction> = emptyList(),
) {
    var touches by remember { mutableIntStateOf(0) }
    var resting by remember { mutableStateOf(false) }
    val hidden = rememberPillsHidden()
    LaunchedEffect(awake, touches) {
        resting = false
        if (awake) return@LaunchedEffect
        // Hidden, its time waits: it sinks only where it is seen to.
        hidden.shownFor(IcodeResting.AFTER_MILLIS)
        resting = true
    }
    // 0 out, 1 at rest: sunk half its width behind its edge, faded.
    val rest by animateFloatAsState(
        if (resting) 1f else 0f,
        tween(if (resting) IcodeResting.SETTLE_MILLIS else IcodeResting.WAKE_MILLIS, easing = FastOutSlowInEasing),
        label = "resting",
    )
    val edge = LocalPillEdge.current
    val way = outward(edge)
    val sinkPx = with(LocalDensity.current) { (IcodeSizes.DockedPill / 2 + EDGE_INSET).toPx() }
    val moves = LocalPillMoves.current(name)
    val pressed = remember { MutableInteractionSource() }
    val line = edgeLine
    Box(
        modifier
            .size(IcodeSizes.DockedPill)
            .graphicsLayer {
                val sunk = way * sinkPx * rest
                if (edge.across) translationY = sunk else translationX = sunk
                alpha = 1f - (1f - IcodeResting.ALPHA) * rest
            }
            // Inside the layer, so the place kept from the back gesture is where the pill is drawn.
            .keptFromBackGesture()
            .wakeOnTouch { touches++ }
            .combinedClickable(
                interactionSource = pressed,
                indication = null,
                role = Role.Button,
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .semantics { customActions = actions + moves },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(IcodeSizes.PillRound)
                .clip(CircleShape)
                .background(IcodeColors.SurfaceElevated.copy(alpha = SEE_THROUGH))
                .then(if (line != null) Modifier.border(1.dp, line.copy(alpha = SEE_THROUGH), CircleShape) else Modifier)
                .indication(pressed, LocalIndication.current),
        )
        Crossfade(icon, label = "icon") { shown ->
            Icon(shown, contentDescription = description, tint = tint, modifier = iconModifier.size(IcodeSizes.IconXl))
        }
        IconMarks(badge ?: 0, dot)
    }
}

/**
 * A floating pill that opens (the dock, D6, D13). Closed, it is its own icon ([LocalPillIcon]) as one
 * [IcodePillButton] in [tone], what needs the user there, resting in its edge as that button does,
 * kept out while it has a [tone]. Tapped, it opens at once into one row of [content] on the raised surface, grown from
 * its icon, which stays under the finger at the row's docked end ([LocalPillIconAtEnd]). That icon
 * tapped closes it again — or, tapped again within a double tap's time of opening it, does
 * [onDoubleTap] — and so does [closeAfterMillis] without a touch while it is shown: hidden
 * ([LocalPillsHidden]) it stays open, and its time starts again when it is back. TalkBack reads it as [description],
 * with Show or Hide, then [actions], then the stack's moves.
 */
@Composable
fun IcodeDockedPill(
    open: Boolean,
    onOpen: (Boolean) -> Unit,
    name: String,
    description: String,
    modifier: Modifier = Modifier,
    tone: Color? = null,
    actions: List<CustomAccessibilityAction> = emptyList(),
    onDoubleTap: (() -> Unit)? = null,
    closeAfterMillis: Long? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val opened by rememberUpdatedState(onOpen)
    val icon = LocalPillIcon.current
    val atEnd = LocalPillIconAtEnd.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val doubleTap = LocalViewConfiguration.current.doubleTapTimeoutMillis
    var openedAt by remember { mutableLongStateOf(0L) }
    var touches by remember { mutableIntStateOf(0) }
    // Hidden under a sheet, it stays open and its time waits; shown again, its time starts again.
    val hidden = rememberPillsHidden()
    if (open && closeAfterMillis != null) {
        LaunchedEffect(touches) {
            hidden.shownFor(closeAfterMillis)
            opened(false)
        }
    }
    val show = CustomAccessibilityAction("Show $name") { opened(true); true }
    val hide = CustomAccessibilityAction("Hide $name") { opened(false); true }
    // Grown from its icon: from the docked end of the row, on the screen.
    val origin = TransformOrigin(if (atEnd != rtl) 1f else 0f, 0.5f)
    AnimatedContent(
        targetState = open,
        modifier = modifier.wakeOnTouch { touches++ },
        transitionSpec = {
            (fadeIn(tween(OPEN_MILLIS)) + scaleIn(tween(OPEN_MILLIS), initialScale = OPEN_FROM, transformOrigin = origin)) togetherWith
                fadeOut(tween(OPEN_MILLIS / 2)) using SizeTransform(clip = false)
        },
        contentAlignment = if (atEnd) Alignment.CenterEnd else Alignment.CenterStart,
        label = "pill",
    ) { isOpen ->
        if (!isOpen) {
            IcodePillButton(
                name, icon, description,
                onClick = {
                    openedAt = SystemClock.uptimeMillis()
                    opened(true)
                },
                // A suggestion keeps it out: it is what needs the user.
                awake = tone != null,
                tint = tone ?: IcodeColors.TextSecondary,
                actions = listOf(show) + actions,
            )
        } else {
            val line = edgeLine
            val moves = LocalPillMoves.current(name)
            // Its own icon, where the closed pill was: tapped, it closes the row, or answers the double tap.
            val close: @Composable () -> Unit = {
                Box(
                    Modifier
                        .size(IcodeSizes.DockedPill)
                        .clickable(role = Role.Button, onClickLabel = "Hide $name") {
                            val again = onDoubleTap != null && SystemClock.uptimeMillis() - openedAt <= doubleTap
                            if (again) onDoubleTap?.invoke() else opened(false)
                        }
                        .semantics { customActions = listOf(hide) + actions + moves },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = description, tint = tone ?: IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconXl))
                }
            }
            Row(
                Modifier
                    .keptFromBackGesture()
                    .height(IcodeSizes.DockedPill)
                    .shadow(OPEN_ELEVATION, IcodeShapes.Pill)
                    .clip(IcodeShapes.Pill)
                    .background(IcodeColors.SurfaceElevated)
                    .then(if (line != null) Modifier.border(1.dp, line, IcodeShapes.Pill) else Modifier)
                    .padding(start = if (atEnd) IcodeSpacing.Space1 else 0.dp, end = if (atEnd) 0.dp else IcodeSpacing.Space1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!atEnd) close()
                content()
                if (atEnd) close()
            }
        }
    }
}

/**
 * An action in an opened pill's row: [icon] in [tint] (muted when not [enabled]), a
 * [IcodeSizes.DockedAction] wide and the pill tall, with [badge], a count, on it where there is one; a
 * [dot] while something runs there; and a [line] under it, as the rail marks its active entry. Its
 * press shows in a round of its width; what it carries is drawn over that, whole.
 */
@Composable
fun IcodeDockedAction(
    icon: ImageVector,
    description: String?,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badge: Int? = null,
    dot: Color? = null,
    line: Color? = null,
) {
    val pressed = remember { MutableInteractionSource() }
    Box(
        modifier
            .width(IcodeSizes.DockedAction)
            .height(IcodeSizes.DockedPill)
            .clickable(pressed, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(IcodeSizes.DockedAction).clip(IcodeShapes.Pill).indication(pressed, LocalIndication.current))
        line?.let { UnderLine(it, lift = LINE_LIFT) }
        Icon(icon, contentDescription = description, tint = if (enabled) tint else IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconXl))
        IconMarks(badge ?: 0, dot)
    }
}

/** Any touch here counts, before what it does: a pill wakes, and its time to close starts again. */
private fun Modifier.wakeOnTouch(touched: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        touched()
    }
}

/** How much of the raised surface a pill's round keeps: the screen shows through the rest. */
private const val SEE_THROUGH = 0.6f

/** An opened pill grows from its icon this long, from this much of its size, and stands this high. */
private const val OPEN_MILLIS = 180
private const val OPEN_FROM = 0.9f
private val OPEN_ELEVATION = 8.dp

/** A docked action's line stands clear of the pill's edge, under its icon. */
private val LINE_LIFT = 5.dp
