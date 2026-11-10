package dev.icode.mobile.domain

/**
 * The open workspace's six tools (docs/dock-navigation-plan.md D1): each keeps a history of its own,
 * and the dock switches between them. Which routes are each one's main screen is the route registry's
 * (`navigation.Route.tool`); what each shows on the dock, its badges and suggestions are
 * [ToolBadges] and [ToolSuggestions].
 */
enum class WorkspaceTool { Explorer, Editor, Search, SourceControl, Assistant, Terminal }
