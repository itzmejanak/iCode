package dev.icode.mobile.data.workspace

import dev.icode.mobile.domain.Busy
import dev.icode.mobile.domain.BusyAction
import dev.icode.mobile.domain.BusyRules
import dev.icode.mobile.domain.ICodeIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The one owner of [dev.icode.mobile.domain.ICodeSnapshot.busy] (docs/loading-plan.md §4.1). An
 * action's busy is published on the dispatching thread, before its work is launched — before it waits
 * on the mutation lock, the disk or the runtime — so the screen answers the tap in the same frame; a
 * second tap of an action that drops repeats is refused there too, so nothing runs twice. It is taken
 * away when the work ends, however it ends.
 */
internal class ActionTracker(private val stateHolder: ICodeStateHolder, private val scope: CoroutineScope) {
    /** How many of each run now: an action that queues repeats (a save) stays busy until its last ends. */
    private val running = HashMap<Busy, Int>()

    /**
     * [intent] done at once by [instant] when it makes no busy, else launched as [work]: so instant
     * intents keep their order (a selection before the page that shows it), and changes show busy.
     */
    fun route(intent: ICodeIntent, instant: () -> Unit, work: suspend () -> Unit) {
        if (BusyRules.of(intent) == null) instant() else launch(intent, work)
    }

    /**
     * Launches [work] for [intent], busy while it runs; null when [intent] was a repeat refused. Every
     * intent under the gateway's mutation lock comes here, so one waiting for the lock — behind the last
     * session's restore at launch, a refresh, a runtime install — shows it is waiting from the tap.
     */
    fun launch(intent: ICodeIntent, work: suspend () -> Unit): Job? {
        val busy = BusyRules.of(intent) ?: return scope.launch { work() }
        if (!begin(busy)) return null
        return scope.launch {
            try {
                work()
            } finally {
                end(busy)
            }
        }
    }

    private fun begin(busy: Busy): Boolean = synchronized(running) {
        val count = running[busy] ?: 0
        if (count > 0 && busy.action.repeat == BusyAction.Repeat.Drop) return false
        running[busy] = count + 1
        publish()
        true
    }

    private fun end(busy: Busy) = synchronized(running) {
        val count = (running[busy] ?: 1) - 1
        if (count <= 0) running.remove(busy) else running[busy] = count
        publish()
    }

    /** Under [running]'s lock, so two changes are published in the order they were made. */
    private fun publish() {
        val now = running.keys.toSet()
        stateHolder.update { if (it.busy == now) it else it.copy(busy = now) }
    }
}
