package dev.icode.mobile.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.icode.mobile.designsystem.IcodeColors

// What a settings page shares with others: a row's way on, and Android's own pages it opens and comes back from.

/** A row that opens another page. */
@Composable
internal fun Chevron() {
    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary)
}

/** iCode's own App info: restricted settings, and permissions asked for too often to ask again. */
internal fun Context.openAppInfo() =
    openFirst(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)), Intent(Settings.ACTION_SETTINGS))

/** Starts the first of [pages] this phone has. */
internal fun Context.openFirst(vararg pages: Intent) {
    for (page in pages) {
        try {
            startActivity(page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // This phone lacks the page; try the next.
        }
    }
}

/** [refresh] each time the page comes back to the front: from Android's settings, or Shizuku. */
@Composable
internal fun RefreshOnResume(refresh: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
