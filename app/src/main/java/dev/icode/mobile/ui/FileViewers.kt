package dev.icode.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.behindSheet
import dev.icode.mobile.domain.FileViewer
import dev.icode.mobile.domain.NoticeIntent
import dev.icode.mobile.domain.notices.FileNotices

/** A file in a viewer of iCode's own: [location] a path, or a picked file's `content://` URI. */
internal data class ViewedFile(val location: String, val name: String, val viewer: FileViewer)

/**
 * The file shown in its viewer, over every screen (docs/file-viewers-plan.md). Asked for by the
 * backend ([dev.icode.mobile.domain.ICodeEffect.ViewFile]) for a workspace file opened from anywhere,
 * and by a screen for what it holds itself: a chat's pictures, a phone file.
 */
@Stable
internal class FileViewerState {
    var shown: ViewedFile? by mutableStateOf(null)
        private set

    fun show(location: String, name: String, viewer: FileViewer) {
        shown = ViewedFile(location, name, viewer)
    }

    fun close() {
        shown = null
    }
}

internal val LocalFileViewer = staticCompositionLocalOf<FileViewerState> { error("FileViewerState was not provided") }

/** [content] with [state] to show files in, and the file shown over it, the screen under it blurred as under any sheet. */
@Composable
internal fun FileViewerHost(state: FileViewerState, content: @Composable () -> Unit) {
    val shown = state.shown
    CompositionLocalProvider(LocalFileViewer provides state) {
        Box(Modifier.fillMaxSize().behindSheet(shown != null)) { content() }
    }
    if (shown == null) return
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    // One its viewer cannot read goes to another app, as a file the editor cannot read does. A picked
    // file was lent to iCode alone, so it is only said.
    val unreadable = {
        state.close()
        if (shown.location.startsWith(CONTENT_URI)) {
            gateway.dispatch(NoticeIntent.Tell(FileNotices.cannotShow(shown.name)))
        } else {
            SharedFiles.open(context, shown.location, gateway::dispatch)
        }
    }
    IcodeSheetOverlay(onClose = state::close) {
        val modifier = Modifier.align(Alignment.BottomCenter)
        when (shown.viewer) {
            FileViewer.Image -> ImageSheet(shown, modifier, onUnreadable = unreadable, onClose = state::close)
        }
    }
}

internal const val CONTENT_URI = "content://"
