package dev.icode.mobile.data.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Runtime work, besides terminal sessions, that must keep iCode's process alive
 * while it lasts. [RuntimeForegroundService] observes it; whoever does the work
 * reports its start and end here.
 */
internal object RuntimeWork {
    private val mutableInstalling = MutableStateFlow(false)
    val installing: StateFlow<Boolean> = mutableInstalling.asStateFlow()

    fun installStarted() {
        mutableInstalling.value = true
    }

    fun installEnded() {
        mutableInstalling.value = false
    }
}
