package dev.icode.mobile.ui.phone

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.icode.mobile.data.phone.PhoneOverlayHost
import dev.icode.mobile.data.phone.PhoneService
import dev.icode.mobile.data.runtime.TerminalSessionManager
import dev.icode.mobile.data.settings.AndroidWorkspacePreferencesRepository
import dev.icode.mobile.data.tasks.WorkNotifications
import dev.icode.mobile.data.workspace.ICodeProcess
import dev.icode.mobile.designsystem.IcodeSizes
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
import kotlin.math.hypot
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
 * no touch of the screen blocked. One bubble, docked on an edge — and its states run here:
 *
 * - **Closed**: the bubble alone. A tap expands it into the bar; while it listens, a tap mutes; while
 *   it speaks, a tap silences.
 * - **Typing**: the bar the bubble became, holding the keyboard: the field, the microphone, Send — Stop
 *   while the agent works. A tap on its mark closes it, or, muted, resumes listening.
 * - **Voice**: the bar gives way to the voice bubble — the words heard so far under a waveform, with a
 *   microphone to mute and a Stop to throw the hearing away. Once the user stops talking, what was
 *   heard is sent; muted, it waits in the bar. Muting never stops what the agent does: Stop does.
 */
internal class PhoneOverlays(private val service: PhoneService) : PhoneOverlayHost {
    private enum class Mode { Closed, Typing, Voice }

    private val gateway = ICodeProcess.attach(service)
    private val windows = service.getSystemService(WindowManager::class.java)
    private val owner = OverlayOwner()
    private val scope = MainScope()
    private val glow = PhoneGlow(service, windows)
    private val bubble = ComposeView(service)
    private val target = ComposeView(service)
    private val density = service.resources.displayMetrics.density
    private val place = BubblePlacement(windows, bubble, density, service.getSharedPreferences(PLACE_PREFERENCES, Context.MODE_PRIVATE))

    private var snapshot by mutableStateOf<ICodeSnapshot?>(null)
    private var onScreen = false
    private var preferences by mutableStateOf(WorkspacePreferences(workspaceLocation = null, loaded = false))
    private var mode by mutableStateOf(Mode.Closed)
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
        listOf(bubble, target).forEach {
            it.setViewTreeLifecycleOwner(owner)
            it.setViewTreeSavedStateRegistryOwner(owner)
        }
        bubble.setContent { Themed { snapshot?.let { PhoneBubble(view(it), actions()) } } }
        target.setContent { Themed { PhoneBubbleTarget(overlay.working, overTarget) } }
        bubble.setOnTouchListener(::outside)
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
        scope.cancel()
        glow.hide()
        listOfNotNull(bubble.takeIf { bubbleOut }, target.takeIf { targetOut }).forEach { runCatching { windows.removeViewImmediate(it) } }
        owner.stop()
        ICodeProcess.detach()
    }

    override suspend fun hideForCapture() = withContext(Dispatchers.Main) {
        glow.capture(hidden = true)
        bubble.visibility = View.INVISIBLE
        // A frame for the windows to be drawn without them.
        delay(FRAME_MILLIS)
    }

    override suspend fun showAfterCapture() = withContext(Dispatchers.Main) {
        glow.capture(hidden = false)
        bubble.visibility = View.VISIBLE
    }

    private fun view(snapshot: ICodeSnapshot) = PhoneBubbleView(
        overlay, snapshot.assistant, snapshot.voice,
        bar = mode == Mode.Typing, voiceUp = mode == Mode.Voice, tucked = place.tucked, edge = place.edge,
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
            to(Mode.Closed)
            // Started straight from the service: a bound accessibility service may start activities from the background.
            service.startActivity(WorkNotifications.intentFor(service, BackgroundPlace.Assistant))
        },
        mute = ::mute,
        cancel = ::cancelVoice,
    )

    /** The bubble tapped (§5.7): mute while it listens, silence while it speaks; else expand the bar, close it, or resume. */
    private fun tap() {
        when {
            overlay.look == Look.Listening -> mute()
            overlay.look == Look.Speaking -> gateway.dispatch(VoiceIntent.Silence)
            mode == Mode.Typing && muted -> listen()
            mode == Mode.Typing -> to(Mode.Closed)
            else -> to(Mode.Typing)
        }
    }

    private fun listen() {
        muted = false
        to(Mode.Voice)
        gateway.dispatch(VoiceIntent.Listen(VoiceFrom.Bubble))
    }

    /** The voice bubble's microphone (§5.7): listening stops and the words wait in the bar — and what the agent does runs on. */
    private fun mute() {
        if (overlay.look != Look.Listening) return
        muted = true
        to(Mode.Typing)
        gateway.dispatch(VoiceIntent.FinishListening)
    }

    /** The voice bubble's Stop: the voice session ends and nothing heard is kept; what runs goes on running. */
    private fun cancelVoice() {
        if (mode != Mode.Voice) return
        to(Mode.Closed)
    }

    /** Sent to the chat iCode shows — "Open in iCode" opens the same one — and answered aloud. */
    private fun send(text: String) {
        if (text.isBlank()) return
        if (snapshot?.assistant?.workspace == null) gateway.dispatch(ICodeIntent.OpenAssistant)
        gateway.dispatch(ICodeIntent.SendAssistantMessage(text.trim(), aloud = true))
        draft = ""
        muted = false
        // Closed, the bar covers nothing the agent may tap.
        to(Mode.Closed)
    }

    /** A hearing for the bubble ended: its words sent in a voice session, else kept in the bar; nothing heard says why there. */
    private fun heard(next: ICodeSnapshot) {
        val heard = next.voice.heard?.takeIf { it.from == VoiceFrom.Bubble && it.final && it.id != heardId } ?: return
        heardId = heard.id
        val words = heard.sent
        when {
            words == null -> if (mode == Mode.Voice) to(Mode.Typing)
            mode == Mode.Voice && next.assistant.turn == null -> send(listOf(draft, words).filter { it.isNotBlank() }.joinToString(" "))
            else -> {
                draft = listOf(draft, words).filter { it.isNotBlank() }.joinToString(" ")
                if (mode == Mode.Voice) {
                    muted = true
                    to(Mode.Typing)
                }
            }
        }
    }

    private fun to(next: Mode) {
        enter(next)
        show()
    }

    /** [next] taken, without showing it yet. */
    private fun enter(next: Mode) {
        // Leaving a voice session without a word sent: nothing heard is sent behind the user's back.
        if (mode == Mode.Voice && next == Mode.Closed && overlay.look == Look.Listening) gateway.dispatch(VoiceIntent.CancelListening)
        if (next == Mode.Closed) muted = false
        mode = next
        if (bubbleOut) place.focusable(next == Mode.Typing)
    }

    private fun show() {
        val now = snapshot ?: return
        val next = PhoneOverlay.of(now.assistant, now.voice, now.phone, onScreen, muted = muted && mode == Mode.Typing, now.mcp.question)
        overlay = next
        glow.show(next.glow, appearancePalette(preferences.appearance.takeIf { preferences.loaded }).accent.toArgb())
        if (next.bubble && !bubbleOut) {
            windows.addView(bubble, place.params)
            bubbleOut = true
        } else if (!next.bubble && bubbleOut) {
            enter(Mode.Closed)
            windows.removeViewImmediate(bubble)
            bubbleOut = false
        }
        place.rest(next.look == Look.Idle && mode == Mode.Closed && !next.asking, scope)
    }

    private fun drag(dx: Float, dy: Float) {
        if (mode == Mode.Typing) to(Mode.Closed)
        place.drag(dx, dy)
        if (!targetOut) {
            windows.addView(target, WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (TARGET_MARGIN_DP * density).toInt()
                title = "iCode bubble target"
            })
            targetOut = true
        }
        val (width, _) = place.screen()
        val (x, y) = place.centre()
        val targetY = (TARGET_MARGIN_DP + IcodeSizes.Bubble.value / 2) * density
        overTarget = hypot(x - width / 2f, y - targetY) < IcodeSizes.Bubble.value * density * TARGET_REACH
    }

    /** Let go: on the target at the top, put away — and a turn on the phone stopped, the voice silenced — else docked on the nearest edge. */
    private fun dropped() {
        if (targetOut) {
            windows.removeViewImmediate(target)
            targetOut = false
        }
        place.drop(away = overTarget)
        if (overTarget) {
            overTarget = false
            to(Mode.Closed)
            if (overlay.working) gateway.dispatch(ICodeIntent.StopAssistant)
            gateway.dispatch(VoiceIntent.Silence)
            gateway.dispatch(PhoneIntent.ShowBubble(false))
        }
        show()
    }

    /** A touch outside the open bar closes it, and goes on to the app under it. */
    @SuppressLint("ClickableViewAccessibility")
    private fun outside(view: View, event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_OUTSIDE && mode == Mode.Typing) to(Mode.Closed)
        return false
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        val appearance = preferences.appearance.takeIf { preferences.loaded }
        IcodeTheme(palette = appearancePalette(appearance), uiTextScalePercent = appearance?.uiTextScalePercent ?: 100, content = content)
    }

    private companion object {
        const val TARGET_MARGIN_DP = 48f
        const val TARGET_REACH = 1.5f
        const val FRAME_MILLIS = 50L
        /** Where the bubble was last docked, kept for the next time it shows. */
        const val PLACE_PREFERENCES = "phone_bubble"
    }
}
