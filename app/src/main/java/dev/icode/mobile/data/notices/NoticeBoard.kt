package dev.icode.mobile.data.notices

import android.content.Context
import dev.icode.mobile.data.notify.IcodeNotifications
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.NoticeIntent
import dev.icode.mobile.domain.notices.Notice
import dev.icode.mobile.domain.notices.NoticeRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * iCode's notices (docs/engineering-rules.md): what is told anywhere is kept on the
 * Notifications page unless it is a moment's, and what needs the user — or work that finished while
 * iCode was out of view — is in Android's shade until read.
 */
internal class NoticeBoard(context: Context, private val stateHolder: ICodeStateHolder, private val screenVisible: StateFlow<Boolean>) {
    private val store = NoticeStore.get(context)
    private val shade = NoticeNotifications(context, IcodeNotifications(context))

    /**
     * Follows what is told — from before anything else starts, so nothing is told before it can be
     * kept — then adds what the previous process's end took with it ([previous]), and publishes the
     * page's list and the shade from then on.
     */
    fun start(scope: CoroutineScope, previous: () -> List<Notice>) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            stateHolder.effects.collect { effect -> if (effect is ICodeEffect.Told && NoticeRules.kept(effect.notice)) keep(effect.notice) }
        }
        scope.launch {
            previous().forEach(store::add)
            store.notices.collect { list -> stateHolder.update { it.copy(notices = list) } }
        }
        // A problem pops up only while iCode is out of view; what finished away is told then too.
        scope.launch { combine(store.notices, screenVisible) { list, visible -> list to visible }.collect { (list, visible) -> shade.show(list, visible) } }
    }

    /** Work worth the shade that ends while iCode is out of view is marked so, as it comes. */
    private fun keep(notice: Notice) = store.add(notice.copy(away = notice.tellAway && !screenVisible.value))

    suspend fun handle(intent: NoticeIntent) = when (intent) {
        NoticeIntent.MarkAllRead -> store.markAllRead()
        is NoticeIntent.Read -> store.markRead(intent.id)
        is NoticeIntent.Delete -> store.delete(intent.id)
        is NoticeIntent.Clear -> store.clear(intent.filter)
        is NoticeIntent.Tell -> stateHolder.tell(intent.notice)
    }
}
