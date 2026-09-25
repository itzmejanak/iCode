package dev.icode.mobile.navigation

/** Views of one tools panel, not independent back-stack destinations. */
enum class ToolPanelTab { Terminal, Problems, Output }

internal fun Route.panelTabOrNull(): ToolPanelTab? = when (this) {
    Route.TerminalExpanded -> ToolPanelTab.Terminal
    Route.ProblemsPanel -> ToolPanelTab.Problems
    Route.OutputPanel -> ToolPanelTab.Output
    else -> null
}
