package dev.icode.mobile.data.runtime

import androidx.annotation.MainThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the user is looking at in the terminal: whether iCode's screen is in view, and which
 * session's view is attached. A finished command or a failure the user was watching needs no
 * pop-up ([RuntimeForegroundService]). Written on the main thread, read from anywhere.
 */
internal class TerminalAttention {
    private val mutableScreenVisible = MutableStateFlow(false)
    /** iCode's screen is in view: work the user was watching there needs no pop-up when it fails. */
    val screenVisible: StateFlow<Boolean> = mutableScreenVisible.asStateFlow()
    private var viewedId: String? = null
    private val mutableLookingAt = MutableStateFlow<String?>(null)
    /** The session the user is looking at: iCode on screen and that session's view attached. */
    val lookingAt: StateFlow<String?> = mutableLookingAt.asStateFlow()

    /** iCode's screen came into view or left it. */
    @MainThread
    fun setScreenVisible(visible: Boolean) {
        mutableScreenVisible.value = visible
        mutableLookingAt.value = viewedId.takeIf { visible }
    }

    /** [id]'s view was attached; null: none is. */
    @MainThread
    fun viewed(id: String?) {
        viewedId = id
        mutableLookingAt.value = id.takeIf { screenVisible.value }
    }

    /** [id]'s view was detached: no longer looked at, unless another took its place. */
    @MainThread
    fun left(id: String) {
        if (viewedId == id) viewed(null)
    }
}
