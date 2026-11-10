package dev.icode.mobile.ui.dock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeDockedAction
import dev.icode.mobile.designsystem.IcodeDockedPill
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.LocalPillEdge
import dev.icode.mobile.designsystem.pillEnter
import dev.icode.mobile.designsystem.pillExit
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.ScreenEdge
import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.domain.SuggestionFacts
import dev.icode.mobile.domain.ToolBadge
import dev.icode.mobile.domain.ToolBadges
import dev.icode.mobile.domain.ToolSuggestion
import dev.icode.mobile.domain.ToolSuggestions
import dev.icode.mobile.domain.WorkspaceTool
import dev.icode.mobile.navigation.ToolPanelTab
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.NavigationState
import dev.icode.mobile.ui.dot
import dev.icode.mobile.ui.icon
import dev.icode.mobile.ui.tint

/**
 * The tool dock's state over every screen (docs/dock-navigation-plan.md D6, D8): whether its row is
 * out, and its suggestion, followed snapshot by snapshot even while the dock is hidden.
 */
@Stable
internal class ToolDockState {
    var tucked by mutableStateOf(true)
        private set
    var suggestions by mutableStateOf(ToolSuggestions())
        private set

    /** Out, what a touch outside it does: tuck it. Tucked, nothing. */
    val onOutside: (() -> Unit)? get() = if (tucked) null else tuck

    val tuck = { tucked = true }

    fun show() {
        tucked = false
    }

    fun follow(facts: SuggestionFacts, shown: WorkspaceTool?) {
        suggestions = suggestions.next(facts, shown)
    }
}

/** The dock's state, its suggestion following [snapshot] with [shown] the tool on screen. */
@Composable
internal fun rememberToolDock(snapshot: ICodeSnapshot, shown: WorkspaceTool?): ToolDockState {
    val dock = remember { ToolDockState() }
    val facts = remember(snapshot) { SuggestionFacts.of(snapshot) }
    LaunchedEffect(facts, shown) { dock.follow(facts, shown) }
    return dock
}

/**
 * The dock in the floating stack, at its foot nearest the thumb unless the user moved it (D6, D11):
 * in the workspace its place is kept, so the pills beside it never move, and it shows on the tools'
 * main screens only. Tucked, only its grip shows, in the suggestion's tone; tapped or pulled out, the
 * six tools in one row, [DOCK_ORDER] from whichever edge it is on. A tap switches to a tool as it was
 * left — the one suggested, to where the suggestion is — and tucks the dock; Back or a touch outside
 * tucks it too. A swipe along the grip switches to the tool used before (D9).
 */
@Composable
internal fun ToolDock(dock: ToolDockState, navigation: NavigationState) {
    val model = navigation.model
    if (!model.inWorkspace) return
    val shown = model.onMainScreen
    LaunchedEffect(shown) { if (!shown) dock.tuck() }
    // On a side, its height is kept while it is hidden; across the top or the bottom it lies along the edge, and is not.
    Box(if (LocalPillEdge.current.across) Modifier else Modifier.height(IcodeSizes.DockedPill)) {
        AnimatedVisibility(visible = shown, enter = pillEnter(), exit = pillExit()) {
            Dock(dock, navigation)
        }
    }
}

@Composable
private fun Dock(dock: ToolDockState, navigation: NavigationState) {
    val snapshot = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val model = navigation.model
    val suggestion = dock.suggestions.suggestion
    val previous = model.previousTool
    BackHandler(enabled = !dock.tucked, onBack = dock.tuck)
    // Gone with the stack, under a sheet: back again, it is tucked.
    DisposableEffect(dock) { onDispose { dock.tuck() } }
    val go = { tool: WorkspaceTool ->
        dock.tuck()
        val why = suggestion?.takeIf { it.tool == tool }?.why
        when (why) {
            ToolSuggestion.Why.BuildErrors -> navigation.actions.openPanel(ToolPanelTab.Problems)
            ToolSuggestion.Why.CommandEnded -> {
                suggestion?.session?.let { gateway.dispatch(ICodeIntent.SelectTerminalSession(it)) }
                navigation.actions.openPanel(ToolPanelTab.Terminal)
            }
            else -> navigation.switchTo(tool)
        }
    }
    val badges = DOCK_ORDER.associateWith { ToolBadges.of(it, snapshot) }
    val said = { tool: WorkspaceTool -> said(tool, badges.getValue(tool), suggestion) }
    IcodeDockedPill(
        tucked = dock.tucked,
        onTucked = { if (it) dock.tuck() else dock.show() },
        name = "the tools",
        modifier = Modifier.semantics {
            contentDescription = listOfNotNull("Tools", suggestion?.let { "Suggested: ${it.tool.label}, ${it.why.said}" }).joinToString(". ")
            liveRegion = LiveRegionMode.Polite
        },
        tone = suggestion?.why?.tone?.tint,
        actions = listOfNotNull(previous?.let { CustomAccessibilityAction("Switch to ${it.label}") { go(it); true } }) +
            DOCK_ORDER.map { tool -> CustomAccessibilityAction(said(tool)) { go(tool); true } },
        onSwipeAlong = previous?.let { { go(it) } },
    ) {
        // The first of the order at the edge on a side; across the top or the bottom, at the end, nearest the thumb.
        (if (LocalPillEdge.current == ScreenEdge.Start) DOCK_ORDER else DOCK_ORDER.asReversed()).forEach { tool ->
            val badge = badges.getValue(tool)
            IcodeDockedAction(
                tool.icon,
                description = said(tool),
                // The tool on screen in the accent, as on the rail; the one suggested in its tone; else its badge's.
                tint = if (tool == model.tool) IcodeColors.Accent
                else suggestion?.takeIf { it.tool == tool }?.why?.tone?.tint ?: badge.tint ?: IcodeColors.TextSecondary,
                onClick = { go(tool) },
                badge = badge.count.takeIf { it > 0 },
                dot = badge.dot,
                // The tool on screen marked as the rail marks it; the one before it, where the swipe goes, quieter.
                line = when (tool) {
                    model.tool -> IcodeColors.Accent
                    previous -> IcodeColors.TextMuted
                    else -> null
                },
            )
        }
    }
}

/** "Source Control, 3 changes, suggested: the assistant changed files": a tool as TalkBack says it. */
private fun said(tool: WorkspaceTool, badge: ToolBadge, suggestion: ToolSuggestion?): String = listOfNotNull(
    tool.label,
    badge.said,
    suggestion?.takeIf { it.tool == tool }?.let { "suggested: ${it.why.said}" },
).joinToString(", ")

/** The tools from the edge outward, the most used nearest the thumb (owner's decision, 2026-11-10). */
internal val DOCK_ORDER = listOf(
    WorkspaceTool.Editor,
    WorkspaceTool.Terminal,
    WorkspaceTool.Assistant,
    WorkspaceTool.SourceControl,
    WorkspaceTool.Search,
    WorkspaceTool.Explorer,
)
