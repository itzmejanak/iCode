package dev.icode.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import dev.icode.mobile.domain.ICodeEffect
import java.util.UUID

/**
 * A dialog's one request to the gateway, answered by its id: a new file or folder, a rename. The
 * dialog waits while it is sent, closes when it is done, and says why when it failed.
 */
@Stable
class PendingRequest internal constructor() {
    var id by mutableStateOf<String?>(null)
        internal set
    /** Why the last one failed, in the gateway's words; cleared by an edit or a new request. */
    var failure by mutableStateOf<String?>(null)

    val waiting: Boolean get() = id != null

    /** Sends one request through [dispatch] under a fresh id. */
    fun send(dispatch: (requestId: String) -> Unit) {
        val requestId = UUID.randomUUID().toString()
        id = requestId
        failure = null
        dispatch(requestId)
    }
}

/** A [PendingRequest] for the dialog keyed by [key]; [onDone] runs once the gateway says it is done. */
@Composable
fun rememberPendingRequest(key: Any?, onDone: () -> Unit): PendingRequest {
    val gateway = LocalICodeGateway.current
    val request = remember(key) { PendingRequest() }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(gateway, request) {
        gateway.effects.collect { effect ->
            when {
                effect is ICodeEffect.WorkspaceItemChanged && effect.requestId == request.id -> done()
                effect is ICodeEffect.WorkspaceItemFailed && effect.requestId == request.id -> {
                    request.failure = effect.message
                    request.id = null
                }
            }
        }
    }
    return request
}
