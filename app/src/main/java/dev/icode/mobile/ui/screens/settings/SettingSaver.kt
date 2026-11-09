package dev.icode.mobile.ui.screens.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeLoadingLine
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.WorkspacePreferences
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * How every settings form saves: nothing while its settings are still loading
 * or could not be read — a value written then would replace one never shown —
 * and the last failure kept for the form to show until a save succeeds.
 */
@Stable
internal class SettingSaver(
    private val scope: CoroutineScope,
    private val preferences: State<WorkspacePreferences>,
    private val fallback: String,
) {
    var error: String? by mutableStateOf(null)
        private set

    val ready: Boolean get() = preferences.value.loaded && preferences.value.loadError == null

    fun save(change: suspend () -> Unit) {
        if (!ready) return
        scope.launch {
            try {
                change()
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: fallback
            }
        }
    }
}

@Composable
internal fun rememberSettingSaver(preferences: WorkspacePreferences, fallback: String): SettingSaver {
    val scope = rememberCoroutineScope()
    val current = rememberUpdatedState(preferences)
    return remember(scope) { SettingSaver(scope, current, fallback) }
}

/**
 * What a settings page shows above its controls: until its settings are read, the wait; when they
 * could not be, why; then the last save's failure. False until the controls can be shown — before,
 * they would show defaults as the user's and offer to change values never read.
 */
@Composable
internal fun settingsShown(preferences: WorkspacePreferences, saver: SettingSaver): Boolean {
    when {
        preferences.loadError != null -> Text("Settings unavailable: ${preferences.loadError}", style = IcodeType.Caption.copy(color = IcodeColors.Error))
        !preferences.loaded -> IcodeLoadingLine("Loading settings…")
        else -> {
            saver.error?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.Error)) }
            return true
        }
    }
    return false
}
