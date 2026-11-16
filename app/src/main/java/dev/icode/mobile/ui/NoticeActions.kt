package dev.icode.mobile.ui

import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.notices.NoticeAction
import dev.icode.mobile.domain.notices.NoticeCommand
import dev.icode.mobile.domain.notices.NoticeTarget
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.navigation.ToolPanelTab

/** Does what a notice offers (docs/engineering-rules.md), once [NoticeRules.available] has said it can. */
internal fun act(action: NoticeAction, gateway: ICodeGateway, navigation: NavigationActions) {
    when (action) {
        is NoticeAction.OpenFile -> gateway.dispatch(ICodeIntent.OpenFile(action.location))
        is NoticeAction.OpenSession -> gateway.dispatch(ICodeIntent.ShowTerminalSession(action.id))
        is NoticeAction.OpenUrl -> gateway.dispatch(ICodeIntent.OpenExternalUrl(action.url))
        is NoticeAction.Undo -> gateway.dispatch(FilesIntent.Undo)
        is NoticeAction.Run -> gateway.dispatch(
            when (action.command) {
                NoticeCommand.Pull -> ICodeIntent.PullRemote
                NoticeCommand.Push -> ICodeIntent.PushRemote
                NoticeCommand.Fetch -> ICodeIntent.FetchRemote()
                NoticeCommand.InstallRuntime -> ICodeIntent.InstallRuntime
            },
        )
        is NoticeAction.Show -> when (action.target) {
            NoticeTarget.SourceControl -> navigation.navigate(Route.SourceControl)
            NoticeTarget.Terminal -> navigation.openPanel(ToolPanelTab.Terminal)
            NoticeTarget.Output -> navigation.openPanel(ToolPanelTab.Output)
            NoticeTarget.RuntimeTools -> navigation.navigate(Route.RuntimeTools)
            NoticeTarget.Languages -> navigation.navigate(Route.SettingsLanguages)
            NoticeTarget.McpServers -> navigation.navigate(Route.McpServers)
            // A chat in a workspace inside it, as its notice is offered only while that one is open; one in none outside.
            NoticeTarget.Assistant -> navigation.show(Route.Assistant)
            NoticeTarget.AssistantOutside -> navigation.outside(Route.Assistant)
        }
    }
}
