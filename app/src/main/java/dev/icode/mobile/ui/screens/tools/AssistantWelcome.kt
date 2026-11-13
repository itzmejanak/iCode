package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeBrandMark
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeWatermark
import dev.icode.mobile.domain.AssistantMode
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.AssistantState

/** An empty chat: the logo over its letterpress, as on the home screen, and a few things to ask. */
@Composable
internal fun AssistantWelcome(state: AssistantState, modifier: Modifier, onSuggestion: (String) -> Unit) {
    // The workspace's name; null for a chat in none, which works in `~` (docs/home-plan.md §2).
    val workspace = state.workspace?.let(AssistantPlace::name)
    val asking = state.settings.mode == AssistantMode.Ask
    val planning = state.settings.mode == AssistantMode.Plan
    val orchestrating = state.settings.mode == AssistantMode.Orchestrator
    Box(modifier.fillMaxWidth()) {
        IcodeWatermark(Modifier.align(Alignment.Center))
        Column(
            Modifier.align(Alignment.Center).widthIn(max = IcodeSizes.ContentMaxWidth).padding(IcodeSpacing.Gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.SectionGap),
        ) {
            EmptyState(
                title = when {
                    asking -> workspace?.let { "Ask about $it" } ?: "Ask anything"
                    planning -> workspace?.let { "Plan for $it" } ?: "Plan with the assistant"
                    orchestrating -> "Build with orchestration"
                    else -> "Build with the assistant"
                },
                description = when {
                    asking -> if (workspace != null) "It reads this workspace to answer, and changes nothing." else "It reads to answer, and changes nothing."
                    planning -> "It reads everything to understand and plan. It never changes files or runs commands."
                    orchestrating -> "It plans, implements and verifies step by step, reporting what it did."
                    else -> (if (workspace != null) "It reads and changes $workspace's files and runs commands in its terminal. " else "It works from ~ and runs commands in its terminal. ") +
                        "${permissionLine(state.permission, workspace)}."
                },
                mark = { IcodeBrandMark(Modifier.size(IcodeSizes.MarkLg)) },
            )
            IcodeSection {
                when {
                    asking -> ASK_SUGGESTIONS
                    planning -> PLAN_SUGGESTIONS
                    orchestrating -> ORCHESTRATOR_SUGGESTIONS
                    else -> CODE_SUGGESTIONS
                }.forEach { suggestion ->
                    IcodeListRow(title = suggestion, card = true, onClick = { onSuggestion(suggestion) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private val CODE_SUGGESTIONS = listOf(
    "Explain how this project is organized",
    "Review my uncommitted changes",
    "Add a README for this project",
)

private val PLAN_SUGGESTIONS = listOf(
    "Analyze the architecture and suggest improvements",
    "Plan how to add authentication",
    "What would it take to add dark mode?",
)

private val ASK_SUGGESTIONS = listOf(
    "Explain how this project is organized",
    "Where does the app start?",
    "What do my uncommitted changes do?",
)

private val ORCHESTRATOR_SUGGESTIONS = listOf(
    "Add a new settings screen with search",
    "Refactor the gateway into smaller coordinators",
    "Set up CI with GitHub Actions",
)
