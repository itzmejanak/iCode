package dev.icode.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.icode.mobile.domain.BackgroundPlace
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.navigation.Route

/** What Android asked iCode's screen to show as it opened or came back to the front. */
sealed interface Arrival {
    /** A terminal session's notification: that session. */
    data class TerminalSession(val id: String) : Arrival

    /** Background work's notification: where iCode shows that work (docs/long-operations-plan.md §6). */
    data class Work(val place: BackgroundPlace) : Arrival
}

/**
 * Shows [arrival] once onboarding is over — onboarding replaces the top screen as it advances, so
 * anything opened during it would be replaced — then reports it done. A runtime install's
 * notification needs nothing then: onboarding is already showing the install.
 */
@Composable
internal fun ArrivalEffect(arrival: Arrival?, navigation: NavigationState, pastOnboarding: Boolean, gateway: ICodeGateway, onArrived: () -> Unit) {
    LaunchedEffect(arrival, pastOnboarding) {
        val asked = arrival ?: return@LaunchedEffect
        if (!pastOnboarding) {
            if (asked == Arrival.Work(BackgroundPlace.Runtime)) onArrived()
            return@LaunchedEffect
        }
        fun show(route: Route) {
            if (navigation.current != route) navigation.actions.navigate(route)
        }
        when (asked) {
            is Arrival.TerminalSession -> gateway.dispatch(ICodeIntent.ShowTerminalSession(asked.id))
            is Arrival.Work -> when (val place = asked.place) {
                is BackgroundPlace.Task -> gateway.dispatch(ICodeIntent.ShowTask(place.id))
                BackgroundPlace.Clone -> show(Route.CloneProgress)
                BackgroundPlace.Sync -> show(Route.PullFetchProgress)
                BackgroundPlace.Runtime -> show(Route.RuntimeInstalling)
                BackgroundPlace.RuntimeTools -> show(Route.RuntimeTools)
                BackgroundPlace.Assistant, BackgroundPlace.Processes -> show(Route.Assistant)
                // The GnuPG dialog is drawn over whatever is open.
                BackgroundPlace.Gpg -> Unit
            }
        }
        onArrived()
    }
}
