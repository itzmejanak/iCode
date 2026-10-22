package dev.icode.mobile.ui.screens.entry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeTickBranch
import dev.icode.mobile.designsystem.IcodeTickRow
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.RuntimeToolNode
import dev.icode.mobile.domain.RuntimeToolRules
import dev.icode.mobile.domain.RuntimeToolTick
import dev.icode.mobile.domain.RuntimeToolTree
import dev.icode.mobile.domain.RuntimeToolsState

/**
 * Select Tools, as Android Studio's SDK Manager lists its tools (docs/runtime-tools-plan.md §8.7–8.9):
 * iCode's own first, then the rest, each tool's add-ons beneath it and a family's versions beneath the
 * family. A row with rows beneath it opens and closes by its arrow; [open] holds the open ones, by id.
 * A tick changes the whole [choice], kept whole: what a tool needs is ticked with it.
 */
@Composable
internal fun ToolList(
    tools: RuntimeToolsState,
    choice: Set<String>,
    open: Set<String>,
    onChoice: (Set<String>) -> Unit,
    onOpen: (Set<String>) -> Unit,
) {
    val sections = remember { RuntimeToolTree.sections() }
    sections.forEach { section ->
        IcodeSection(section.title) {
            Column {
                section.nodes.forEachIndexed { index, node ->
                    if (index > 0) Divider()
                    ToolNode(node, tools, choice, open, onChoice, onOpen)
                }
            }
        }
    }
}

/** A row, then, while it is open, the rows beneath it. */
@Composable
private fun ToolNode(
    node: RuntimeToolNode,
    tools: RuntimeToolsState,
    choice: Set<String>,
    open: Set<String>,
    onChoice: (Set<String>) -> Unit,
    onOpen: (Set<String>) -> Unit,
) {
    val tick = RuntimeToolTree.tick(node, choice)
    val status = (node as? RuntimeToolNode.Tool)?.let { RuntimeToolRules.status(it.tool, tick == RuntimeToolTick.On, tools) }
    val opened = node.id in open
    IcodeTickRow(
        state = when (tick) {
            RuntimeToolTick.Off -> ToggleableState.Off
            RuntimeToolTick.Partly -> ToggleableState.Indeterminate
            RuntimeToolTick.On -> ToggleableState.On
        },
        onTick = { onChoice(RuntimeToolTree.toggle(choice, node)) },
        name = node.name,
        open = opened.takeIf { node.children.isNotEmpty() },
        onOpen = { onOpen(if (opened) open - node.id else open + node.id) },
        trailing = status?.let { { Text(label(it), style = IcodeType.Caption.copy(color = color(it))) } },
    ) {
        // Said by its whole name: a revision is shown as its number only beneath its family.
        Text(node.title, Modifier.clearAndSetSemantics { text = AnnotatedString(node.name) }, style = IcodeType.Body)
        Text(node.summary, style = IcodeType.Caption)
        node.usedBy?.let { Text("Used by $it", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted)) }
        if (node.needs.isNotEmpty()) {
            Text("Needs ${spoken(node.needs.map { it.name })}", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
        }
        // Closed, it says what it hides: "CMake 3.22.1 and NDK (Side by side) · 2 ticked".
        if (node.children.isNotEmpty() && !opened) {
            val ticked = RuntimeToolTree.tickedBeneath(node, choice)
            Text(
                "${spoken(node.children.map { it.title })} · ${if (ticked == 0) "none" else ticked} ticked",
                style = IcodeType.Caption.copy(color = IcodeColors.TextMuted),
            )
        }
    }
    if (node.children.isNotEmpty()) {
        AnimatedVisibility(opened) {
            IcodeTickBranch { node.children.forEach { ToolNode(it, tools, choice, open, onChoice, onOpen) } }
        }
    }
}
