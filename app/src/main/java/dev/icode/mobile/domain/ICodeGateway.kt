package dev.icode.mobile.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Stable UI boundary for the later runtime/storage/Git implementation.
 *
 * Production adapters will implement this interface. Compose never shells out or reads files directly.
 */
interface ICodeGateway {
    val snapshot: StateFlow<ICodeSnapshot>
    val effects: Flow<ICodeEffect>
        get() = emptyFlow()

    fun dispatch(intent: ICodeIntent)
}
