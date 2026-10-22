package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTickBranch
import dev.icode.mobile.designsystem.IcodeTriStateCheckbox
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.RuntimeToolNode
import dev.icode.mobile.domain.RuntimeToolRules
import dev.icode.mobile.domain.RuntimeToolStatus
import dev.icode.mobile.domain.RuntimeToolTick
import dev.icode.mobile.domain.RuntimeToolTree
import dev.icode.mobile.domain.RuntimeToolsState

/**
 * Select Tools, as Android Studio's SDK Manager lists its tools (docs/runtime-tools-plan.md §8.7):
 * iCode's own first, then the rest, each tool's add-ons beneath it and a family's versions beneath
 * the family. A tap changes the whole [choice], kept whole: what a tool needs is ticked with it.
 */
@Composable
internal fun ToolList(tools: RuntimeToolsState, choice: Set<String>, onChoice: (Set<String>) -> Unit) {
    val sections = remember { RuntimeToolTree.sections() }
    sections.forEach { section ->
        IcodeSection(section.title) {
            Column {
                section.nodes.forEachIndexed { index, node ->
                    if (index > 0) Divider()
                    ToolNode(node, tools, choice, onChoice)
                }
            }
        }
    }
}

/** A row, then the rows beneath it on a rail from its tick. */
@Composable
private fun ToolNode(node: RuntimeToolNode, tools: RuntimeToolsState, choice: Set<String>, onChoice: (Set<String>) -> Unit) {
    val tick = RuntimeToolTree.tick(node, choice)
    val status = (node as? RuntimeToolNode.Tool)?.let { RuntimeToolRules.status(it.tool, tick == RuntimeToolTick.On, tools) }
    ToolRow(node, tick, status) { onChoice(RuntimeToolTree.toggle(choice, node)) }
    if (node.children.isNotEmpty()) {
        IcodeTickBranch { node.children.forEach { ToolNode(it, tools, choice, onChoice) } }
    }
}

/**
 * One row: its tick (the whole row takes the tap), what it is, what uses it and what it needs, and
 * — a tool's, not a family's — its status at the end. A screen reader hears it as one checkbox.
 */
@Composable
private fun ToolRow(node: RuntimeToolNode, tick: RuntimeToolTick, status: RuntimeToolStatus?, onTap: () -> Unit) {
    val state = when (tick) {
        RuntimeToolTick.Off -> ToggleableState.Off
        RuntimeToolTick.Partly -> ToggleableState.Indeterminate
        RuntimeToolTick.On -> ToggleableState.On
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IcodeShapes.Small)
            .triStateToggleable(state = state, role = Role.Checkbox, onClick = onTap)
            .padding(vertical = IcodeSpacing.Space2),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
    ) {
        IcodeTriStateCheckbox(state, onClick = null, modifier = Modifier.size(IcodeSizes.Tick))
        Column(Modifier.weight(1f).padding(top = IcodeSpacing.Micro)) {
            // Said by its whole name: a revision is shown as its number only beneath its family.
            Text(node.title, Modifier.clearAndSetSemantics { text = AnnotatedString(node.name) }, style = IcodeType.Body)
            Text(node.summary, style = IcodeType.Caption)
            node.usedBy?.let { Text("Used by $it", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted)) }
            if (node.needs.isNotEmpty()) {
                Text("Needs ${spoken(node.needs.map { it.name })}", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
            }
        }
        status?.let { Text(label(it), modifier = Modifier.padding(top = IcodeSpacing.Micro), style = IcodeType.Caption.copy(color = color(it))) }
    }
}
