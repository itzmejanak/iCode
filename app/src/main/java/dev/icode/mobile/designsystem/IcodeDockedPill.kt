package dev.icode.mobile.designsystem

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
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
import androidx.compose.foundation.systemGestureExclusion
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A floating pill at rest (docs/dock-navigation-plan.md D12, D13): [content], its buttons
 * ([IcodePillButton]), on no ground of their own. Left alone while not [awake], it fades as the
 * assistant's bubble over other apps does ([IcodeResting]); a touch wakes it at once. It is kept
 * from Android's back gesture, which would otherwise take a touch at the screen's edge. [content] is
 * given TalkBack's moves for [name], for its first button to carry.
 */
@Composable
fun IcodePillRow(
    name: String,
    awake: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(moves: List<CustomAccessibilityAction>) -> Unit,
) {
    var touches by remember { mutableIntStateOf(0) }
    var resting by remember { mutableStateOf(false) }
    LaunchedEffect(awake, touches) {
        resting = false
        if (awake) return@LaunchedEffect
        delay(IcodeResting.AFTER_MILLIS)
        resting = true
    }
    val fade by animateFloatAsState(if (resting) IcodeResting.ALPHA else 1f, label = "resting")
    val moves = LocalPillMoves.current(name)
    Row(
        modifier
            .systemGestureExclusion()
            .graphicsLayer { alpha = fade }
            .wakeOnTouch { touches++ },
        verticalAlignment = Alignment.CenterVertically,
    ) { content(moves) }
}

/**
 * A floating pill's button: [icon] in [tint] — muted when not [enabled] — on a faint round of the
 * raised surface, so it reads over code and leaves the screen seen through it; with [badge], a count,
 * and a [dot] on it. A touch target wide and tall, the round inside it, so what it carries is whole.
 */
@Composable
fun IcodePillButton(
    icon: ImageVector,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = IcodeColors.TextSecondary,
    enabled: Boolean = true,
    badge: Int? = null,
    dot: Color? = null,
    iconModifier: Modifier = Modifier,
    actions: List<CustomAccessibilityAction> = emptyList(),
) {
    val pressed = remember { MutableInteractionSource() }
    val line = edgeLine
    Box(
        modifier
            .size(IcodeSizes.DockedPill)
            .clickable(pressed, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (actions.isNotEmpty()) customActions = actions },
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
        Icon(icon, contentDescription = description, tint = if (enabled) tint else IcodeColors.TextMuted, modifier = iconModifier.size(IcodeSizes.IconXl))
        IconMarks(badge ?: 0, dot)
    }
}

/**
 * A floating pill that opens (the dock, D6, D13). Closed, it is its own icon ([LocalPillIcon]) as one
 * [IcodePillButton] in [tone], what needs the user there, at rest as [IcodePillRow] says, awake while
 * it has a [tone]. Tapped, it opens at once into one row of [content] on the raised surface, grown from
 * its icon, which stays under the finger at the row's docked end ([LocalPillIconAtEnd]). That icon
 * tapped closes it again — or, tapped again within a double tap's time of opening it, does
 * [onDoubleTap] — and so does [closeAfterMillis] without a touch. TalkBack reads it as [description],
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
    if (open && closeAfterMillis != null) {
        LaunchedEffect(touches) {
            delay(closeAfterMillis)
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
            IcodePillRow(name, awake = tone != null) { moves ->
                IcodePillButton(
                    icon, description,
                    onClick = {
                        openedAt = SystemClock.uptimeMillis()
                        opened(true)
                    },
                    tint = tone ?: IcodeColors.TextSecondary,
                    actions = listOf(show) + actions + moves,
                )
            }
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
                    .systemGestureExclusion()
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
