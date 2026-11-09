package dev.icode.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.dispatchAndWait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A question held open over the work its answer started (docs/loading-plan.md §4.2): restoring a
 * draft, reloading a file changed on disk, clearing the cache. [answer] sends the answer and closes
 * the question once its work has ended; meanwhile [answering] is that answer, so the sheet shows which
 * button is working and takes no other. A [PendingRequest] does the same for a request answered by id.
 */
@Stable
internal class HeldAnswer(private val scope: CoroutineScope, private val gateway: ICodeGateway) {
    var answering by mutableStateOf<ICodeIntent?>(null)
        private set

    val busy: Boolean get() = answering != null

    fun answer(intent: ICodeIntent, onDone: () -> Unit) {
        if (answering != null) return
        answering = intent
        scope.launch {
            try {
                gateway.dispatchAndWait(intent)
                onDone()
            } finally {
                answering = null
            }
        }
    }
}

/** A [HeldAnswer] for the question keyed by [key]. */
@Composable
internal fun rememberHeldAnswer(key: Any?): HeldAnswer {
    val scope = rememberCoroutineScope()
    val gateway = LocalICodeGateway.current
    return remember(key, gateway) { HeldAnswer(scope, gateway) }
}
