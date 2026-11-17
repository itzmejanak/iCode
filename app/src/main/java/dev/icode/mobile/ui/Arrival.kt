package dev.icode.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import dev.icode.mobile.domain.BackgroundPlace
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.domain.McpIntent
import dev.icode.mobile.domain.NoticeIntent
import dev.icode.mobile.domain.notices.NoticeRules
import dev.icode.mobile.navigation.Route

/** What Android asked iCode's screen to show as it opened or came back to the front. */
sealed interface Arrival {
    /** A terminal session's notification: that session. */
    data class TerminalSession(val id: String) : Arrival

    /** Background work's notification: where iCode shows that work (docs/runtime-and-terminal.md). */
    data class Work(val place: BackgroundPlace) : Arrival

    /**
     * A notice in Android's shade: its sheet; their summary, without [id]: the Notifications page; its
     * button ([act]): its main action, done while it still can be, else its sheet (docs/engineering-rules.md).
     */
    data class Notice(val id: String?, val act: Boolean = false) : Arrival

    /** A remote MCP server's sign-in came back (docs/assistant-and-mcp.md): finished, and its servers shown. */
    data class SignedIn(val answer: String) : Arrival {
        /** It holds a one-time code: never written out. */
        override fun toString(): String = "SignedIn(…)"
    }
}

/**
 * Shows [arrival] once onboarding is over — onboarding replaces the top screen as it advances, so
 * anything opened during it would be replaced — then reports it done. A runtime install's
 * notification needs nothing then: onboarding is already showing the install.
 */
@Composable
internal fun ArrivalEffect(
    arrival: Arrival?,
    navigation: NavigationState,
    pastOnboarding: Boolean,
    gateway: ICodeGateway,
    snapshot: ICodeSnapshot,
    notices: NoticeHostState,
    onArrived: () -> Unit,
) {
    val current by rememberUpdatedState(snapshot)
    LaunchedEffect(arrival, pastOnboarding) {
        val asked = arrival ?: return@LaunchedEffect
        if (!pastOnboarding) {
            if (asked == Arrival.Work(BackgroundPlace.Runtime)) onArrived()
            return@LaunchedEffect
        }
        // Where the work is, shown as asked for: a tool on its main screen, a page over what is open.
        fun show(route: Route) = navigation.show(route)
        when (asked) {
            is Arrival.TerminalSession -> gateway.dispatch(ICodeIntent.ShowTerminalSession(asked.id))
            is Arrival.Work -> when (val place = asked.place) {
                is BackgroundPlace.Task -> gateway.dispatch(ICodeIntent.ShowTask(place.id))
                BackgroundPlace.Clone -> show(Route.CloneProgress)
                BackgroundPlace.Sync -> show(Route.PullFetchProgress)
                BackgroundPlace.Runtime -> show(Route.RuntimeInstalling)
                BackgroundPlace.RuntimeTools -> show(Route.RuntimeTools)
                // That chat: one in no workspace is outside it (docs/design-and-navigation.md).
                is BackgroundPlace.AssistantChat -> {
                    gateway.dispatch(ICodeIntent.OpenAssistantSession(place.workspace, place.session))
                    if (place.workspace == null) navigation.showOutside(Route.Assistant) else show(Route.Assistant)
                }
                // The chat the bubble follows, else the one shown.
                BackgroundPlace.Assistant, BackgroundPlace.Processes -> {
                    val assistant = current.assistant
                    val outside = assistant.focus?.let { it.workspace == null } ?: (assistant.opened && assistant.workspace == null)
                    if (outside) navigation.showOutside(Route.Assistant) else show(Route.Assistant)
                }
                // The GnuPG sheet is drawn over whatever is open.
                BackgroundPlace.Gpg -> Unit
            }
            // A notice's sheet is drawn over whatever is open.
            is Arrival.Notice -> when (val id = asked.id) {
                null -> show(Route.Notifications)
                else -> {
                    val action = current.notices.firstOrNull { it.id == id }?.takeIf { asked.act }?.let { NoticeRules.available(it, current).firstOrNull() }
                    if (action == null) {
                        notices.open(id)
                    } else {
                        gateway.dispatch(NoticeIntent.Read(id))
                        act(action, gateway, navigation.actions)
                    }
                }
            }
            is Arrival.SignedIn -> {
                gateway.dispatch(McpIntent.SignedIn(asked.answer))
                show(Route.McpServers)
            }
        }
        onArrived()
    }
}
