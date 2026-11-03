package dev.icode.mobile.data.voice

import android.content.Context
import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.domain.voice.SpokenText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Spoken replies (docs/phone-agent-plan.md §5.8): when a turn ends without failing or being stopped,
 * its answer is read aloud as the Voice setting says; a new turn silences what is being read.
 */
internal class VoiceReplies(
    context: Context,
    private val snapshot: StateFlow<ICodeSnapshot>,
    private val scope: CoroutineScope,
) {
    private val app = context.applicationContext

    fun start() {
        Voice.load(app)
        scope.launch {
            var running = snapshot.value.assistant.running
            snapshot.collect { now ->
                val assistant = now.assistant
                if (assistant.running == running) return@collect
                running = assistant.running
                if (running) {
                    Voice.stopSpeaking()
                    Voice.stopped = false
                    return@collect
                }
                val fromBubble = Voice.bubbleTurn
                Voice.bubbleTurn = false
                if (Voice.stopped || assistant.failure != null) return@collect
                if (!Voice.replies.value.speaks(fromBubble)) return@collect
                SpokenText.lastAnswer(assistant.messages)?.let { Voice.speak(app, it) }
            }
        }
    }
}
