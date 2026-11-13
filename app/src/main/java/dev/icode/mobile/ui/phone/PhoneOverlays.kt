package dev.icode.mobile.ui.phone

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.icode.mobile.data.notify.NotificationLink
import dev.icode.mobile.data.phone.PhoneOverlayHost
import dev.icode.mobile.data.phone.PhoneService
import dev.icode.mobile.data.runtime.TerminalSessionManager
import dev.icode.mobile.data.settings.AndroidWorkspacePreferencesRepository
import dev.icode.mobile.data.workspace.ICodeProcess
import dev.icode.mobile.designsystem.IcodeTheme
import dev.icode.mobile.domain.BackgroundPlace
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.domain.PhoneIntent
import dev.icode.mobile.domain.VoiceIntent
import dev.icode.mobile.domain.WorkspacePreferences
import dev.icode.mobile.domain.phone.PhoneOverlay
import dev.icode.mobile.domain.phone.PhoneOverlay.Look
import dev.icode.mobile.domain.voice.VoiceFrom
import dev.icode.mobile.ui.appearancePalette
import dev.icode.mobile.ui.systemDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The glow and the bubble over every app (docs/phone-agent-plan.md §5.6, §5.7; redesigned 2026-11-04,
 * after Gemini), in the phone service's own trusted windows: no "draw over other apps" permission, and
 * no touch of the screen blocked. One bubble, docked on an edge, in a window of its own; the bar and the
 * voice bubble in another at the bottom, so neither moves the other — and their states run here:
 *
 * - **Closed**: the bubble alone. A tap expands it into the bar; while it listens, a tap mutes; while
 *   it speaks, a tap silences. Only it drags: it follows the finger, and docks on the nearest edge.
 * - **Typing**: the bar the bubble became, holding the keyboard: the field, the microphone, Send — Stop
 *   while the agent works. A tap on its mark closes it back into the bubble, or, muted, resumes listening.
 * - **Voice**: the bar gives way to the voice bubble — the words heard so far under a waveform, with a
 *   microphone to mute and a Stop to throw the hearing away. Once the user stops talking, what was
 *   heard is sent; muted, it waits in the bar. Muting never stops what the agent does: Stop does.
 */
internal class PhoneOverlays(private val service: PhoneService) : PhoneOverlayHost {
    private val gateway = ICodeProcess.attach(service)
    private val windows = OverlayWindows(service.getSystemService(WindowManager::class.java)) { putAway() }
    private val owner = OverlayOwner()
    private val scope = MainScope()
    private val glow = PhoneGlow(service, windows)
    private val bubble = ComposeView(service)

    /** Where the finger is on the screen, as the bubble's window last heard it: a drag follows it there. */
    private var finger = 0f to 0f
    private val bubbleWindow = OverlayFrame(service) { finger = it.rawX to it.rawY }.apply { addView(bubble) }
    private val bar = ComposeView(service)
    private val target = ComposeView(service)
    private val place = BubblePlacement(
        windows, bubbleWindow, bar, service.resources.displayMetrics.density, service.getSharedPreferences(PLACE_PREFERENCES, Context.MODE_PRIVATE),
    )

    private var snapshot by mutableStateOf<ICodeSnapshot?>(null)
    private var onScreen = false
    private var preferences by mutableStateOf(WorkspacePreferences(workspaceLocation = null, loaded = false))
    private var mode by mutableStateOf(BubbleMode.Closed)
    private var muted by mutableStateOf(false)
    private var draft by mutableStateOf("")
    private var overlay by mutableStateOf(PhoneOverlay(bubble = false, glow = PhoneOverlay.Glow.Off, look = Look.Idle, working = false, asking = false, step = null))
    private var overTarget by mutableStateOf(false)
    private var bubbleOut = false
    private var targetOut = false

    /** The last hearing for the bubble already acted on, so one is sent once. */
    private var heardId = 0L

    init {
        owner.start()
        // On each window's root: Compose looks for them there, not on a view inside it.
        listOf(bubbleWindow, bar, target).forEach {
            it.setViewTreeLifecycleOwner(owner)
            it.setViewTreeSavedStateRegistryOwner(owner)
        }
        bubble.setContent { Themed { snapshot?.let { PhoneBubble(view(it), actions()) } } }
        bar.setContent { Themed { snapshot?.let { PhoneBubblePanel(view(it), actions()) } } }
        target.setContent { Themed { PhoneBubbleTarget(overlay.working, overTarget) } }
        bar.setOnTouchListener(::outside)
        val visible = TerminalSessionManager.get(service).screenVisible
        val appearance = AndroidWorkspacePreferencesRepository(service).values(null)
        scope.launch {
            combine(gateway.snapshot, visible, appearance) { snapshot, front, prefs -> Triple(snapshot, front, prefs) }.collect { (next, front, prefs) ->
                if (snapshot == null) heardId = next.voice.heard?.id ?: 0L
                snapshot = next
                onScreen = front
                preferences = prefs
                heard(next)
                show()
            }
        }
    }

    override fun detach() {
        putAway()
        owner.stop()
        ICodeProcess.detach()
    }

    /** Every window gone and nothing shown again; safe twice, and for a window never added. Also what a revoked token ([OverlayWindows]) does, before [detach]. */
    private fun putAway() {
        scope.cancel()
        glow.hide()
        place.drop(away = true)
        listOf(bubbleWindow, bar, target).forEach(windows::remove)
        bubbleOut = false
        targetOut = false
    }

    override suspend fun hideForCapture() = withContext(Dispatchers.Main) {
        glow.capture(hidden = true)
        listOf(bubbleWindow, bar).forEach { it.visibility = View.INVISIBLE }
        // A frame for the windows to be drawn without them.
        delay(FRAME_MILLIS)
    }

    override suspend fun showAfterCapture() = withContext(Dispatchers.Main) {
        glow.capture(hidden = false)
        listOf(bubbleWindow, bar).forEach { it.visibility = View.VISIBLE }
    }

    private fun view(snapshot: ICodeSnapshot) = PhoneBubbleView(
        overlay, snapshot.assistant, snapshot.voice,
        mode = mode, tucked = place.tucked, edge = place.edge,
        draft = draft, onDraft = { draft = it },
    )

    private fun actions() = PhoneBubbleActions(
        tap = ::tap,
        drag = ::drag,
        dragEnd = ::dropped,
        dispatch = gateway::dispatch,
        listen = ::listen,
        send = { send(draft) },
        open = {
            to(BubbleMode.Closed)
            // Started straight from the service: a bound accessibility service may start activities from the background.
            service.startActivity(NotificationLink.intent(service, NotificationLink.Work(BackgroundPlace.Assistant)))
        },
        mute = ::mute,
        cancel = ::cancelVoice,
    )

    /** The bubble tapped (§5.7): mute while it listens, silence while it speaks; else expand the bar, close it, or resume. */
    private fun tap() {
        when {
            overlay.look == Look.Listening -> mute()
            overlay.look == Look.Speaking -> gateway.dispatch(VoiceIntent.Silence)
            mode == BubbleMode.Typing && muted -> listen()
            mode == BubbleMode.Typing -> to(BubbleMode.Closed)
            else -> to(BubbleMode.Typing)
        }
    }

    private fun listen() {
        muted = false
        to(BubbleMode.Voice)
        gateway.dispatch(VoiceIntent.Listen(VoiceFrom.Bubble))
    }

    /** The voice bubble's microphone (§5.7): listening stops and the words wait in the bar — and what the agent does runs on. */
    private fun mute() {
        if (overlay.look != Look.Listening) return
        muted = true
        to(BubbleMode.Typing)
        gateway.dispatch(VoiceIntent.FinishListening)
    }

    /** The voice bubble's Stop: the voice session ends and nothing heard is kept; what runs goes on running. */
    private fun cancelVoice() {
        if (mode != BubbleMode.Voice) return
        to(BubbleMode.Closed)
    }

    /** Sent to the chat iCode shows — "Open in iCode" opens the same one — and answered aloud. */
    private fun send(text: String) {
        if (text.isBlank()) return
        // Not opened yet: the open workspace's chats, else those in no workspace (docs/home-plan.md §2).
        if (snapshot?.assistant?.opened != true) gateway.dispatch(ICodeIntent.OpenAssistant(inWorkspace = true))
        gateway.dispatch(ICodeIntent.SendAssistantMessage(text.trim(), aloud = true))
        draft = ""
        muted = false
        // Closed, the bar covers nothing the agent may tap.
        to(BubbleMode.Closed)
    }

    /** A hearing for the bubble ended: its words sent in a voice session, else kept in the bar; nothing heard says why there. */
    private fun heard(next: ICodeSnapshot) {
        val heard = next.voice.heard?.takeIf { it.from == VoiceFrom.Bubble && it.final && it.id != heardId } ?: return
        heardId = heard.id
        val words = heard.sent
        when {
            words == null -> if (mode == BubbleMode.Voice) to(BubbleMode.Typing)
            mode == BubbleMode.Voice && next.assistant.turn == null -> send(listOf(draft, words).filter { it.isNotBlank() }.joinToString(" "))
            else -> {
                draft = listOf(draft, words).filter { it.isNotBlank() }.joinToString(" ")
                if (mode == BubbleMode.Voice) {
                    muted = true
                    to(BubbleMode.Typing)
                }
            }
        }
    }

    private fun to(next: BubbleMode) {
        enter(next)
        show()
    }

    /** [next] taken, without showing it yet. */
    private fun enter(next: BubbleMode) {
        // Leaving a voice session without a word sent: nothing heard is sent behind the user's back.
        if (mode == BubbleMode.Voice && next == BubbleMode.Closed && overlay.look == Look.Listening) gateway.dispatch(VoiceIntent.CancelListening)
        if (next == BubbleMode.Closed) muted = false
        mode = next
        place.open(next)
    }

    private fun show() {
        val now = snapshot ?: return
        val next = PhoneOverlay.of(now.assistant, now.voice, now.phone, onScreen, muted = muted && mode == BubbleMode.Typing, now.mcp.question)
        overlay = next
        glow.show(next.glow, appearancePalette(preferences.appearance.takeIf { preferences.loaded }, service.systemDark).accent.toArgb())
        if (!windows.usable) return
        if (next.bubble && !bubbleOut) {
            // Both or neither: half a window is none.
            bubbleOut = windows.add(bubbleWindow, place.params) && windows.add(bar, place.barParams)
            if (!bubbleOut) return listOf(bubbleWindow, bar).forEach(windows::remove)
        } else if (!next.bubble && bubbleOut) {
            enter(BubbleMode.Closed)
            listOf(bubbleWindow, bar).forEach(windows::remove)
            bubbleOut = false
        }
        place.rest(next.look == Look.Idle && mode == BubbleMode.Closed && !next.asking, scope)
    }

    /** The bubble dragged: it follows the finger, and the target to put it away shows at the top. */
    private fun drag() {
        place.dragTo(finger.first, finger.second)
        overTarget = place.overTarget()
        if (!targetOut && windows.add(target, place.targetParams)) targetOut = true
    }

    /** Let go: on the target at the top, put away — and a turn on the phone stopped, the voice silenced — else docked on the nearest edge. */
    private fun dropped() {
        if (!place.dragging) return
        if (targetOut) {
            windows.remove(target)
            targetOut = false
        }
        place.drop(away = overTarget)
        if (overTarget) {
            overTarget = false
            to(BubbleMode.Closed)
            if (overlay.working) gateway.dispatch(ICodeIntent.StopAssistant)
            gateway.dispatch(VoiceIntent.Silence)
            gateway.dispatch(PhoneIntent.ShowBubble(false))
        }
        show()
    }

    /** A touch outside the open bar closes it, and goes on to the app under it. */
    @SuppressLint("ClickableViewAccessibility")
    private fun outside(view: View, event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_OUTSIDE && mode == BubbleMode.Typing) to(BubbleMode.Closed)
        return false
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        val appearance = preferences.appearance.takeIf { preferences.loaded }
        IcodeTheme(palette = appearancePalette(appearance, isSystemInDarkTheme()), uiTextScalePercent = appearance?.uiTextScalePercent ?: 100, content = content)
    }

    private companion object {
        const val FRAME_MILLIS = 50L
        /** Where the bubble was last docked, kept for the next time it shows. */
        const val PLACE_PREFERENCES = "phone_bubble"
    }
}
