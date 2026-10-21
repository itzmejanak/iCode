package dev.icode.mobile.data.notices

import android.app.NotificationManager
import android.content.Context
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AppNotice
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.NoticeIntent
import dev.icode.mobile.domain.NoticeRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * iCode's notices (docs/notices-plan.md): what is said anywhere is kept on the Notifications page
 * unless it is Done, and the unread ones are in Android's shade.
 */
internal class NoticeBoard(context: Context, private val stateHolder: ICodeStateHolder, private val screenVisible: StateFlow<Boolean>) {
    private val store = NoticeStore.get(context)
    private val shade = NoticeNotifications(context, context.getSystemService(NotificationManager::class.java))

    /**
     * Follows what is said — from before anything else starts, so nothing is said before it can be
     * kept — then adds what the previous process's end took with it ([previous]), and publishes the
     * page's list and the shade from then on.
     */
    fun start(scope: CoroutineScope, previous: () -> List<AppNotice>) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            stateHolder.effects.collect { effect -> if (effect is ICodeEffect.Notice && NoticeRules.kept(effect.notice)) store.add(effect.notice) }
        }
        scope.launch {
            previous().forEach(store::add)
            store.notices.collect { list -> stateHolder.update { it.copy(notices = list) } }
        }
        // A problem pops up only while iCode is out of view.
        scope.launch { combine(store.notices, screenVisible) { list, visible -> list to visible }.collect { (list, visible) -> shade.show(list, visible) } }
    }

    suspend fun handle(intent: NoticeIntent) = when (intent) {
        NoticeIntent.MarkAllRead -> store.markAllRead()
        is NoticeIntent.Read -> store.markRead(intent.id)
        is NoticeIntent.Delete -> store.delete(intent.id)
        NoticeIntent.Clear -> store.clear()
        is NoticeIntent.Notify -> stateHolder.notify(intent.tone, intent.title, intent.detail)
    }
}
