package dev.icode.mobile.ui.screens.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTag
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.domain.GitCommitSummary
import dev.icode.mobile.domain.GitHistoryRow
import dev.icode.mobile.domain.GitRef
import dev.icode.mobile.domain.GitRefKind
import dev.icode.mobile.domain.RelativeTime

/**
 * A commit as Source Control tells it, on Last Commit's card and an open line of the History sheet
 * alike: its id as far as [GitCommitSummary.ID_LENGTH], its message (the subject, then the [body] when
 * it has one), its author and when; [more] rows follow. Each is a row of the caller's column.
 */
@Composable
internal fun CommitFacts(
    id: String,
    subject: String,
    author: String,
    authoredAtEpochMillis: Long,
    body: String = "",
    more: @Composable () -> Unit = {},
) {
    MonoLine("commit ${id.take(GitCommitSummary.ID_LENGTH)}", color = IcodeColors.Accent)
    Text(subject, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
    if (body.isNotEmpty()) Text(body, style = IcodeType.Body, color = IcodeColors.TextSecondary)
    DetailRow("Author", author, valueStyle = IcodeType.MonoSmall)
    DetailRow(
        "Time",
        RelativeTime.since(authoredAtEpochMillis),
        valueStyle = IcodeType.MonoSmall,
        valueColor = IcodeColors.TextSecondary,
    )
    more()
}

/**
 * A line of the History sheet (docs/source-control.md): [lanes] of graph at its start, then the
 * commit's short id, its names, its subject and how long ago. A tap opens it to the whole commit, its
 * text there to select and copy, the graph's lanes running on beside it; a tap on the line closes it.
 */
@Composable
internal fun GitHistoryLine(row: GitHistoryRow, lanes: Int, open: Boolean, onToggle: () -> Unit) {
    val commit = row.commit
    val paint = graphPaint()
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawGraph(row, paint) }
            .padding(start = IcodeSizes.GraphLane * lanes),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IcodeSizes.TouchTarget)
                .clip(IcodeShapes.Small)
                .clickable(role = Role.Button, onClickLabel = if (open) "Close" else "Open", onClick = onToggle)
                .semantics { stateDescription = if (open) "Open" else "Closed" }
                .padding(horizontal = IcodeSpacing.Space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro),
        ) {
            MonoLine(commit.id.take(SHORT_ID), color = IcodeColors.TextMuted, style = IcodeType.MonoSmall, maxLines = 1)
            commit.refs.take(SHOWN_REFS).forEach { RefTag(it) }
            if (commit.refs.size > SHOWN_REFS) Text("+${commit.refs.size - SHOWN_REFS}", style = IcodeType.Caption)
            Text(commit.subject, Modifier.weight(1f), style = IcodeType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(RelativeTime.since(commit.authoredAtEpochMillis), style = IcodeType.Caption, color = IcodeColors.TextMuted, maxLines = 1)
        }
        AnimatedVisibility(open) {
            SelectionContainer {
                Column(
                    Modifier.padding(start = IcodeSpacing.Space1, end = IcodeSpacing.Space1, bottom = IcodeSpacing.Space3),
                    verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro),
                ) {
                    CommitFacts(commit.id, commit.subject, commit.author, commit.authoredAtEpochMillis, commit.body) {
                        // The names its line had no room for, with the rest.
                        if (commit.refs.size > SHOWN_REFS) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
                                verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
                            ) { commit.refs.forEach { RefTag(it) } }
                        }
                    }
                }
            }
        }
    }
}

/** HEAD's branch filled in the accent; other branches, remotes' branches and tags each in their colour. */
@Composable
private fun RefTag(ref: GitRef) = when (ref.kind) {
    GitRefKind.Head -> IcodeTag(ref.name, IcodeColors.Accent, filled = true)
    GitRefKind.Branch -> IcodeTag(ref.name, IcodeColors.Green)
    GitRefKind.Remote -> IcodeTag(ref.name, IcodeColors.Blue)
    GitRefKind.Tag -> IcodeTag(ref.name, IcodeColors.Amber)
}

/** What the graph is drawn with, read in composition: each lane's colour by its place, and sizes in pixels. */
private class GraphPaint(val lanes: List<Color>, val hole: Color, val lane: Float, val line: Float, val dot: Float, val stroke: Float)

@Composable
private fun graphPaint(): GraphPaint {
    val density = LocalDensity.current
    val lanes = listOf(IcodeColors.Accent, IcodeColors.Green, IcodeColors.Blue, IcodeColors.Amber)
    // A merge's dot is a ring: hollow, the sheet showing through.
    val hole = IcodeColors.Surface
    return remember(density, lanes, hole) {
        with(density) {
            GraphPaint(lanes, hole, IcodeSizes.GraphLane.toPx(), IcodeSizes.TouchTarget.toPx(), IcodeSizes.GraphDot.toPx(), IcodeSizes.GraphLine.toPx())
        }
    }
}

/**
 * [row]'s part of the graph, down the whole line, open or not. The lanes the line above leaves come in
 * at the top, those waiting for this commit meeting at its dot, halfway down the line's first
 * [GraphPaint.line]; from the dot a line goes to each parent's lane, and that lane runs on to the foot,
 * as every lane passing by does: the next line's lanes start where these end.
 */
private fun DrawScope.drawGraph(row: GitHistoryRow, paint: GraphPaint) {
    val graph = row.graph
    val dotY = paint.line / 2
    fun x(lane: Int) = (lane + 0.5f) * paint.lane
    fun color(lane: Int) = paint.lanes[lane % paint.lanes.size]
    graph.above.forEachIndexed { lane, waiting ->
        when (waiting) {
            null -> Unit
            row.commit.id -> edge(x(lane), 0f, x(graph.lane), dotY, color(lane), paint.stroke)
            else -> drawLine(color(lane), Offset(x(lane), 0f), Offset(x(lane), size.height), paint.stroke)
        }
    }
    graph.parentLanes.forEach { lane ->
        edge(x(graph.lane), dotY, x(lane), paint.line, color(lane), paint.stroke)
        drawLine(color(lane), Offset(x(lane), paint.line), Offset(x(lane), size.height), paint.stroke)
    }
    val dot = Offset(x(graph.lane), dotY)
    if (row.commit.parents.size > 1) {
        drawCircle(paint.hole, paint.dot / 2, dot)
        drawCircle(color(graph.lane), (paint.dot - paint.stroke) / 2, dot, style = Stroke(paint.stroke))
    } else {
        drawCircle(color(graph.lane), paint.dot / 2, dot)
    }
}

/** A line between two points: straight down a lane, or between lanes a curve leaving one and arriving in the other upright. */
private fun DrawScope.edge(fromX: Float, fromY: Float, toX: Float, toY: Float, color: Color, stroke: Float) {
    if (fromX == toX) {
        drawLine(color, Offset(fromX, fromY), Offset(toX, toY), stroke)
        return
    }
    val middle = (fromY + toY) / 2
    val curve = Path().apply {
        moveTo(fromX, fromY)
        cubicTo(fromX, middle, toX, middle, toX, toY)
    }
    drawPath(curve, color, style = Stroke(stroke))
}

/** As `git log --oneline` abbreviates an id at its shortest. */
private const val SHORT_ID = 7
/** Names on a line before the rest are counted ("+2"); the open line lists them all. */
private const val SHOWN_REFS = 2
