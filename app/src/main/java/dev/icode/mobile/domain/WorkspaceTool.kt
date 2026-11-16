package dev.icode.mobile.domain

/**
 * The open workspace's six tools (docs/design-and-navigation.md): each keeps a history of its own,
 * and the dock switches between them. Which routes are each one's main screen is the route registry's
 * (`navigation.Route.tool`); what each shows on the dock and the rail, its badges and suggestions, are
 * [ToolBadges] and [ToolSuggestions]. [label] is its name wherever it is named.
 */
enum class WorkspaceTool(val label: String) {
    Explorer("Explorer"),
    Editor("Editor"),
    Search("Search"),
    SourceControl("Source Control"),
    Assistant("Assistant"),
    Terminal("Terminal"),
}
