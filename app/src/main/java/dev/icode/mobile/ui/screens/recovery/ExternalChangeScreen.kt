package dev.icode.mobile.ui.screens.recovery

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.ExternalChange
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.rememberHeldAnswer

/**
 * A file changed on disk while it had unsaved edits here (US-EDIT-009): which
 * version wins. The same sheet as every other question the editor asks.
 * Nothing is written until the user says; Cancel keeps both, and autosave waits.
 * Answered, it stays until the answer is carried out, the button tapped [working].
 */
@Composable
fun ExternalChangeSheet(
    change: ExternalChange,
    onReload: () -> Unit,
    onCompare: () -> Unit,
    onOverwrite: () -> Unit,
    onDismiss: () -> Unit,
    working: ICodeIntent? = null,
) {
    IcodeConfirmSheet(
        title = "${change.name} changed on disk",
        confirmLabel = "Reload from disk",
        onConfirm = onReload,
        onDismiss = onDismiss,
        confirmEnabled = working == null,
        confirmBusy = working is ICodeIntent.ReloadFromDisk,
        dismissEnabled = working == null,
    ) {
        MonoLine(change.path, style = IcodeType.MonoSmall, color = IcodeColors.TextSecondary)
        Text(
            "It was changed outside iCode ${RelativeTime.since(change.diskChangedAt)}, while you have unsaved edits here. " +
                "Reload takes the disk version; Cancel keeps your edits, and saving asks again.",
            style = IcodeType.Body,
        )
        SecondaryButton("Compare versions", onCompare, Modifier.fillMaxWidth(), enabled = working == null)
        SecondaryButton(
            "Overwrite disk with my edits",
            onOverwrite,
            Modifier.fillMaxWidth(),
            enabled = working == null,
            color = IcodeColors.Error,
            busy = working is ICodeIntent.OverwriteDisk,
        )
    }
}

/**
 * Asks about the external change [asked] names over whatever screen is open,
 * and asks again on coming back from comparing the two versions.
 */
@Composable
fun ExternalChangePrompt(
    asked: String?,
    onAskedChange: (String?) -> Unit,
    snapshot: ICodeSnapshot,
    gateway: ICodeGateway,
    current: Route,
    navigate: (Route) -> Unit,
) {
    var comparing by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(current) {
        val location = comparing ?: return@LaunchedEffect
        if (current != Route.DiffView) {
            comparing = null
            onAskedChange(location)
        }
    }
    // Before the change is looked up: once carried out, the change is gone, and the answer closes the question after.
    val held = rememberHeldAnswer(asked)
    val change = snapshot.externalChanges.firstOrNull { it.location == asked } ?: return
    ExternalChangeSheet(
        change = change,
        onReload = { held.answer(ICodeIntent.ReloadFromDisk(change.location)) { onAskedChange(null) } },
        onCompare = {
            gateway.dispatch(ICodeIntent.CompareWithDisk(change.location))
            comparing = change.location
            onAskedChange(null)
            navigate(Route.DiffView)
        },
        onOverwrite = { held.answer(ICodeIntent.OverwriteDisk(change.location)) { onAskedChange(null) } },
        onDismiss = { onAskedChange(null) },
        working = held.answering,
    )
}

/** The captured frame: the sheet as it appears over the editor. */
@Composable
fun ExternalChangeScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val change = state.externalChanges.firstOrNull()
    val held = rememberHeldAnswer(change?.location)
    if (change == null) {
        EmptyState(
            title = "Nothing to resolve",
            description = "Every open file matches the version on disk.",
            modifier = Modifier.fillMaxSize().padding(IcodeSpacing.Space6),
        )
        return
    }
    ExternalChangeSheet(
        change = change,
        onReload = { held.answer(ICodeIntent.ReloadFromDisk(change.location), actions.back) },
        onCompare = {
            gateway.dispatch(ICodeIntent.CompareWithDisk(change.location))
            actions.navigate(Route.DiffView)
        },
        onOverwrite = { held.answer(ICodeIntent.OverwriteDisk(change.location), actions.back) },
        onDismiss = actions.back,
        working = held.answering,
    )
}
