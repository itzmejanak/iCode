package dev.icode.mobile.ui.terminal

import android.content.ClipData
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import com.termux.terminal.TerminalSession
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.domain.UrlDetector
import kotlinx.coroutines.launch

/** What the terminal's "More…" shows: its actions, or the links found in the output. */
internal sealed interface TerminalMore {
    data object Actions : TerminalMore
    data class Links(val urls: List<String>) : TerminalMore
}

/**
 * The menu behind "More…" in the terminal's text-selection bar. Termux's
 * view asks for a context menu there; iCode answers with its own menu, in the
 * app's style: copy or share everything the session printed, open a link from
 * it, or reset a terminal a program left in a strange state.
 */
@Composable
internal fun TerminalMoreMenu(
    session: TerminalSession?,
    shown: TerminalMore?,
    onShow: (TerminalMore?) -> Unit,
    onUrl: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val output = { session?.emulator?.screen?.transcriptTextWithFullLinesJoined?.trimEnd().orEmpty() }
    val actions = when (shown) {
        TerminalMore.Actions -> {
            val urls = UrlDetector.find(output()).asReversed().take(MAX_LINKS)
            listOf(
                IcodeMenuAction("Copy all output", Icons.Outlined.ContentCopy, {
                    val text = output()
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Terminal output", text))) }
                }),
                IcodeMenuAction("Share output", Icons.Outlined.Share, {
                    // An intent carries a bounded amount; the latest output is what matters.
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, output().takeLast(MAX_SHARED_CHARS))
                    context.startActivity(Intent.createChooser(send, "Share terminal output"))
                }),
                IcodeMenuAction(
                    if (urls.isEmpty()) "No links in the output" else "Open a link",
                    Icons.Outlined.Link,
                    { if (urls.size == 1) onUrl(urls.single()) else onShow(TerminalMore.Links(urls)) },
                    enabled = urls.isNotEmpty(),
                ),
                IcodeMenuAction("Reset terminal", Icons.Outlined.RestartAlt, { session?.reset() }),
            )
        }
        is TerminalMore.Links -> shown.urls.map { url ->
            IcodeMenuAction(url.removePrefix("https://").removePrefix("http://"), Icons.AutoMirrored.Outlined.OpenInNew, { onUrl(url) })
        }
        null -> emptyList()
    }
    IcodeActionMenu(expanded = shown != null, onDismiss = { onShow(null) }, actions = actions)
}

private const val MAX_LINKS = 8
private const val MAX_SHARED_CHARS = 100_000
