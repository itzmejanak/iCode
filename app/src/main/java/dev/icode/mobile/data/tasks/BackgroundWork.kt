package dev.icode.mobile.data.tasks

import android.content.Context
import dev.icode.mobile.data.runtime.RuntimeForegroundService
import dev.icode.mobile.domain.BackgroundItem
import dev.icode.mobile.domain.ICodeIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The iCode app's background work (docs/long-operations-plan.md §6), published for
 * [RuntimeForegroundService]: it keeps the process while any of it runs and carries it in
 * notifications. The process's one gateway [follow]s its own; a notification's Cancel comes back
 * through [cancel] to that gateway.
 */
internal object BackgroundWork {
    private val mutableItems = MutableStateFlow<List<BackgroundItem>>(emptyList())
    val items: StateFlow<List<BackgroundItem>> = mutableItems.asStateFlow()

    @Volatile
    private var dispatch: (ICodeIntent) -> Unit = {}

    /**
     * Publishes [items] as they change, and [dispatch] takes a notification's Cancel. The service
     * starts as work begins — always on the user's action, while iCode is in front, the only time
     * Android lets an app start one. A failure read in iCode, dismissed or run again leaves the
     * notification drawer too.
     */
    fun follow(context: Context, scope: CoroutineScope, items: Flow<List<BackgroundItem>>, dispatch: (ICodeIntent) -> Unit) {
        this.dispatch = dispatch
        scope.launch {
            items.distinctUntilChanged().collect { next ->
                val before = mutableItems.value
                mutableItems.value = next
                if (before.none { it.running } && next.any { it.running }) RuntimeForegroundService.ensureRunning(context)
                before.filterNot { it.running }
                    .filter { failed -> next.none { it.key == failed.key && it.failure == failed.failure } }
                    .forEach { WorkNotifications.dismissFailure(context, it) }
            }
        }
    }

    /** Cancel on [key]'s notification: sent only while that work still runs and may be cancelled. */
    fun cancel(key: String) {
        items.value.firstOrNull { it.key == key && it.running }?.cancel?.let(dispatch)
    }
}
