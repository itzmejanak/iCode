package dev.icode.mobile.ui.terminal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import dev.icode.mobile.R
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.JumpToLatestButton
import dev.icode.mobile.designsystem.LocalIcodePalette
import dev.icode.mobile.domain.EditorFontFamily
import dev.icode.mobile.domain.TerminalSettings
import dev.icode.mobile.ui.LocalTerminalSessions
import kotlin.math.roundToInt

/**
 * Draws one terminal session and takes its input: Termux's [TerminalView], the
 * emulator's own renderer, selection and keyboard connection.
 *
 * One view serves every tab; switching tabs attaches another session to it.
 * Sessions outlive the view — they belong to the session manager — so leaving
 * the screen or rotating the device loses nothing.
 */
@Composable
fun TerminalHost(
    sessionId: String,
    settings: TerminalSettings,
    fontScale: Float,
    keys: TerminalKeys,
    onScale: (Float) -> Unit,
    onUrl: (String) -> Unit,
    onEndedEnter: () -> Unit,
    modifier: Modifier = Modifier,
    /** The view took the focus, or let it go: the keyboard types into it. */
    onFocus: (Boolean) -> Unit = {},
) {
    val source = LocalTerminalSessions.current
    val observe = remember(source, sessionId) { { changed: () -> Unit -> source.observe(sessionId, changed) } }
    TerminalPane(source.session(sessionId), settings, fontScale, keys, onScale, onUrl, onEndedEnter, modifier, observe = observe, onFocus = onFocus)
}

/**
 * What a command printed, drawn as [TerminalHost] draws a session (docs/agent-control-plan.md §6):
 * the same view, font, palette, selection, links and pinch, from a session that runs nothing; a tap
 * brings no keyboard. [onFits] hears how many rows the output fills at the view's width, and a row's
 * height in pixels: a view one row taller than that shows them all.
 */
@Composable
fun TerminalReplay(
    printed: String,
    settings: TerminalSettings,
    fontScale: Float,
    onScale: (Float) -> Unit,
    onUrl: (String) -> Unit,
    onFits: (rows: Int, rowPx: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = LocalTerminalSessions.current
    val session = remember(source, printed) { source.replay(printed, settings) }
    val keys = remember { TerminalKeys() }
    TerminalPane(session, settings, fontScale, keys, onScale, onUrl, onEndedEnter = {}, modifier, onEmulator = { view ->
        view.currentSession?.emulator?.let { onFits(it.contentRows(), view.mRenderer.fontLineSpacing) }
    })
}

/**
 * The rows its output fills: the scrollback, and the screen down to the cursor's row — that row too
 * when something stands on it, as a prompt or a line without its end does.
 */
internal fun TerminalEmulator.contentRows(): Int =
    (screen.activeTranscriptRows + cursorRow + if (cursorCol > 0) 1 else 0).coerceAtLeast(1)

/**
 * [session] in Termux's view. [observe] reports its screen's changes for a live one; a replay
 * ([observe] null) changes once, when it is sized, and takes nothing typed. [onEmulator] hears each
 * size its emulator is given.
 */
// The touch listener only claims the gesture; the view's own handling, clicks included, still runs.
@SuppressLint("ClickableViewAccessibility")
@Composable
private fun TerminalPane(
    session: TerminalSession?,
    settings: TerminalSettings,
    fontScale: Float,
    keys: TerminalKeys,
    onScale: (Float) -> Unit,
    onUrl: (String) -> Unit,
    onEndedEnter: () -> Unit,
    modifier: Modifier,
    observe: ((changed: () -> Unit) -> AutoCloseable)? = null,
    onEmulator: (TerminalView) -> Unit = {},
    onFocus: (Boolean) -> Unit = {},
) {
    val focusChanged by rememberUpdatedState(onFocus)
    val palette = LocalIcodePalette.current
    val context = LocalContext.current
    val textSizePx = with(LocalDensity.current) { (settings.fontSizeSp * fontScale).sp.roundToPx() }
    val typeface = remember(context, settings.fontFamily) { terminalTypeface(context, settings.fontFamily) }
    val callbacks = remember(keys) { TerminalViewCallbacks(keys) }
    callbacks.onScale = onScale
    callbacks.onUrl = onUrl
    callbacks.onEndedEnter = onEndedEnter
    callbacks.typing = observe != null
    val look = remember { Look() }
    callbacks.onEmulatorSet = {
        callbacks.view?.let { view ->
            look.restoring?.takeIf { it === view.currentSession }?.let { session ->
                look.restoring = null
                // The view sets its top row to the latest output once this returns: back where it was, after that.
                view.post { TerminalScroll.restore(view, session) }
            }
            onEmulator(view)
        }
    }
    var view by remember { mutableStateOf<TerminalView?>(null) }
    var scrolledBack by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf<TerminalMore?>(null) }
    var pressedAt by remember { mutableStateOf(IntOffset.Zero) }
    callbacks.onLongPressAt = { x, y -> pressedAt = IntOffset(x.roundToInt(), y.roundToInt()) }

    Box(modifier) {
        AndroidView(
            factory = { viewContext ->
                TerminalView(viewContext, null).also { created ->
                    created.setTerminalViewClient(callbacks)
                    created.isFocusable = true
                    created.isFocusableInTouchMode = true
                    // A gesture on the terminal is the terminal's: a sheet it stands in neither
                    // scrolls nor moves under it (docs/agent-control-plan.md §6).
                    created.setOnTouchListener { touched, event ->
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) touched.parent?.requestDisallowInterceptTouchEvent(true)
                        false
                    }
                    created.setOnFocusChangeListener { _, focused -> focusChanged(focused) }
                    // The view has no scroll callback, but every scroll redraws
                    // it. The listener belongs to the window, so it is removed
                    // when the view leaves it.
                    val drawListener = ViewTreeObserver.OnDrawListener {
                        val back = created.topRow < 0
                        if (back != scrolledBack) created.post { scrolledBack = back }
                    }
                    created.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(attached: View) {
                            attached.viewTreeObserver.addOnDrawListener(drawListener)
                        }

                        override fun onViewDetachedFromWindow(detached: View) {
                            detached.viewTreeObserver.removeOnDrawListener(drawListener)
                        }
                    })
                    // "More…" in the selection bar asks the view for a context
                    // menu; iCode's own menu answers, and the platform's stays empty.
                    created.setOnCreateContextMenuListener { _, _, _ -> more = TerminalMore.Actions }
                    callbacks.view = created
                    keys.view = created
                    view = created
                }
            },
            update = { target ->
                // The renderer must exist before a session is attached: size first.
                if (look.textSizePx != textSizePx) {
                    target.setTextSize(textSizePx)
                    look.textSizePx = textSizePx
                }
                if (look.typeface !== typeface) {
                    target.setTypeface(typeface)
                    look.typeface = typeface
                }
                if (session != null) {
                    TerminalPalette.colorFor(session, palette)
                    if (target.currentSession !== session) {
                        TerminalScroll.leave(target)
                        look.restoring = session
                        target.attachSession(session)
                    }
                    target.invalidate()
                }
            },
            onRelease = { released ->
                TerminalScroll.leave(released)
                if (callbacks.view === released) callbacks.view = null
                if (keys.view === released) keys.view = null
            },
            modifier = Modifier.fillMaxSize().padding(horizontal = IcodeSpacing.Space3),
        )
        // Opens where the user long-pressed — where the selection and its
        // More… were — the view sitting inside the same horizontal padding.
        Box(Modifier.padding(start = IcodeSpacing.Space3).offset { pressedAt }) {
            TerminalMoreMenu(session, more, onShow = { more = it }, onUrl = onUrl)
        }
        JumpToLatestButton(
            visible = scrolledBack,
            contentDescription = "Jump to latest output",
            onClick = { view?.let(::jumpToLatest) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(IcodeSpacing.Space3),
        )
    }

    DisposableEffect(observe, view) {
        val target = view
        val handle = target?.let { observe?.invoke { showOutput(it) } }
        onDispose { handle?.close() }
    }

    // The keyboard belongs to the terminal. Left open when the terminal goes —
    // its last shell exited, or the user went back — it would cover a screen
    // with nothing to type into.
    val window = LocalView.current
    DisposableEffect(window) {
        onDispose {
            window.context.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(window.windowToken, 0)
        }
    }
}

/**
 * Draws new output. At the end of the output the view follows it, as Termux
 * does; scrolled back, the same text stays in view while new lines arrive
 * below, so a build's earlier output can be read while it runs.
 */
private fun showOutput(view: TerminalView) {
    val emulator = view.currentSession?.emulator
    if (emulator == null || view.topRow == 0 || view.isSelectingText) {
        view.onScreenUpdated()
        return
    }
    val oldest = -emulator.screen.activeTranscriptRows
    view.topRow = maxOf(view.topRow - emulator.scrollCounter, oldest)
    emulator.clearScrollCounter()
    view.invalidate()
}

private fun jumpToLatest(view: TerminalView) {
    view.topRow = 0
    view.onScreenUpdated()
}

private class Look {
    var textSizePx: Int = -1
    var typeface: Typeface? = null
    /** The session just attached, whose place is set again once the view has sized it ([TerminalScroll]). */
    var restoring: TerminalSession? = null
}

private fun terminalTypeface(context: Context, family: EditorFontFamily): Typeface = when (family) {
    EditorFontFamily.PlexMono -> runCatching { ResourcesCompat.getFont(context, R.font.ibm_plex_mono_regular) }
        .getOrNull() ?: Typeface.MONOSPACE
    EditorFontFamily.SystemMono -> Typeface.MONOSPACE
}
