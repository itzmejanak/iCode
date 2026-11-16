package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A palette as a small screen of itself (docs/design-and-navigation.md): its page; a card with two
 * lines of text and an accent button; a line of code in three of its colours on its editor; and its
 * green, amber, error and blue. Drawn from [palette] alone, whichever palette the app is in, with the
 * card's line while Border Lines is on, as the app draws it. A picture only: the card it is on says
 * what it is.
 */
@Composable
fun ThemePreview(palette: IcodePalette, modifier: Modifier = Modifier) {
    val outlined = LocalIcodeBorderLines.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(HEIGHT)
            .clip(IcodeShapes.Small)
            .background(palette.background)
            .padding(INSET)
            .clearAndSetSemantics {},
    ) {
        // The status colours where there is room for them beside the code: three cards to a small phone leave none.
        val roomForDots = maxWidth >= ROOM_FOR_DOTS
        Column(
            Modifier
                .fillMaxWidth(CARD_WIDTH)
                .clip(IcodeShapes.Small)
                .background(palette.surface)
                .then(if (outlined) Modifier.border(LINE, palette.outline, IcodeShapes.Small) else Modifier)
                .padding(INSET),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Bar(palette.textPrimary, TEXT_WIDTH, TEXT_HEIGHT)
            Bar(palette.textSecondary, SECONDARY_WIDTH, SECONDARY_HEIGHT)
            Box(Modifier.size(BUTTON_WIDTH, BUTTON_HEIGHT).background(palette.accent, IcodeShapes.Pill))
        }
        Row(
            Modifier.align(Alignment.BottomStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Row(
                Modifier.background(palette.backgroundDeep, IcodeShapes.Small).padding(horizontal = GAP, vertical = CODE_INSET),
                horizontalArrangement = Arrangement.spacedBy(CODE_GAP),
            ) {
                listOf(SyntaxRole.Keyword, SyntaxRole.String, SyntaxRole.Function).forEach { role ->
                    Box(Modifier.size(CODE_WIDTH, CODE_HEIGHT).background(palette.syntaxColor(role), IcodeShapes.Pill))
                }
            }
            if (roomForDots) {
                Row(horizontalArrangement = Arrangement.spacedBy(CODE_GAP)) {
                    listOf(palette.green, palette.amber, palette.error, palette.blue).forEach { status ->
                        Box(Modifier.size(DOT).background(status, CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun Bar(color: Color, width: Float, height: Dp) {
    Box(Modifier.fillMaxWidth(width).height(height).background(color, IcodeShapes.Pill))
}

private val HEIGHT = 68.dp
private val INSET = 6.dp
private val GAP = 4.dp
private val LINE = 1.dp
private const val CARD_WIDTH = 0.72f
private const val TEXT_WIDTH = 0.8f
private const val SECONDARY_WIDTH = 0.55f
private val TEXT_HEIGHT = 5.dp
private val SECONDARY_HEIGHT = 4.dp
private val BUTTON_WIDTH = 28.dp
private val BUTTON_HEIGHT = 8.dp
private val CODE_INSET = 3.dp
private val CODE_GAP = 2.dp
private val CODE_WIDTH = 9.dp
private val CODE_HEIGHT = 3.dp
private val DOT = 5.dp
/** The code strip (39 dp), a gap (4 dp) and the four dots (26 dp), inside the preview's insets. */
private val ROOM_FOR_DOTS = 69.dp
