package dev.icode.mobile.data.notices

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import dev.icode.mobile.domain.AppNotice
import dev.icode.mobile.domain.EarlierWorkspace
import dev.icode.mobile.domain.LostTerminalSession
import dev.icode.mobile.domain.LostTerminalSessions
import dev.icode.mobile.domain.ProcessEndCause
import dev.icode.mobile.domain.markedRead
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Notifications page's notices: kept until the user deletes them, newest
 * first, at most [MAX_NOTICES]. One per process, so every screen and service
 * sees the same list.
 */
internal class NoticeStore private constructor(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableNotices = MutableStateFlow(read())
    val notices: StateFlow<List<AppNotice>> = mutableNotices.asStateFlow()

    /** Stores [notice] unless one with its id is already kept. */
    @Synchronized
    fun add(notice: AppNotice) {
        val current = mutableNotices.value
        if (current.any { it.id == notice.id }) return
        save((listOf(notice) + current).sortedByDescending { it.at }.take(MAX_NOTICES))
    }

    @Synchronized
    fun markAllRead() {
        val current = mutableNotices.value
        if (current.all { it.read }) return
        save(current.map { it.markedRead() })
    }

    @Synchronized
    fun delete(id: String) {
        val current = mutableNotices.value
        if (current.none { it.id == id }) return
        save(current.filterNot { it.id == id })
    }

    @Synchronized
    fun clear() {
        if (mutableNotices.value.isNotEmpty()) save(emptyList())
    }

    private fun save(notices: List<AppNotice>) {
        mutableNotices.value = notices
        preferences.edit { putString(KEY_NOTICES, encode(notices)) }
    }

    /** A notice that cannot be read back — damaged, or of a kind this build does not know — is left out. */
    private fun read(): List<AppNotice> {
        val array = runCatching { JSONArray(preferences.getString(KEY_NOTICES, null) ?: return emptyList()) }
            .getOrElse { return emptyList() }
        return (0 until array.length()).mapNotNull { index -> runCatching { decode(array.getJSONObject(index)) }.getOrNull() }
    }

    private fun encode(notices: List<AppNotice>): String = JSONArray().apply {
        notices.forEach { notice ->
            when (notice) {
                is AppNotice.TerminalSessionsEnded -> put(
                    JSONObject()
                        .put(KEY_KIND, KIND_SESSIONS_ENDED)
                        .put(KEY_ID, notice.id)
                        .put(KEY_AT, notice.at)
                        .put(KEY_READ, notice.read)
                        .put(KEY_CAUSE, notice.lost.cause.name)
                        .put(KEY_ENDED_AT, notice.lost.endedAt ?: JSONObject.NULL)
                        .put(
                            KEY_SESSIONS,
                            JSONArray().apply {
                                notice.lost.sessions.forEach { session ->
                                    put(JSONObject().put(KEY_TITLE, session.title).put(KEY_WORKSPACE, session.workspace ?: JSONObject.NULL))
                                }
                            },
                        ),
                )
                is AppNotice.GitTransferInterrupted -> put(
                    JSONObject()
                        .put(KEY_KIND, KIND_GIT_TRANSFER_INTERRUPTED)
                        .put(KEY_ID, notice.id)
                        .put(KEY_AT, notice.at)
                        .put(KEY_READ, notice.read)
                        .put(KEY_LABEL, notice.label)
                        .put(KEY_TARGET, notice.target),
                )
                is AppNotice.WorkspacesMovedIn -> put(
                    JSONObject()
                        .put(KEY_KIND, KIND_WORKSPACES_MOVED_IN)
                        .put(KEY_ID, notice.id)
                        .put(KEY_AT, notice.at)
                        .put(KEY_READ, notice.read)
                        .put(
                            KEY_EARLIER,
                            JSONArray().apply {
                                notice.earlier.forEach { put(JSONObject().put(KEY_NAME, it.name).put(KEY_PATH, it.path)) }
                            },
                        ),
                )
            }
        }
    }.toString()

    private fun decode(item: JSONObject): AppNotice? = when (item.getString(KEY_KIND)) {
        KIND_SESSIONS_ENDED -> {
            val sessions = item.getJSONArray(KEY_SESSIONS)
            AppNotice.TerminalSessionsEnded(
                id = item.getString(KEY_ID),
                at = item.getLong(KEY_AT),
                read = item.getBoolean(KEY_READ),
                lost = LostTerminalSessions(
                    sessions = (0 until sessions.length()).map { index ->
                        val session = sessions.getJSONObject(index)
                        LostTerminalSession(
                            session.getString(KEY_TITLE),
                            session.optString(KEY_WORKSPACE).takeIf { !session.isNull(KEY_WORKSPACE) },
                        )
                    },
                    cause = ProcessEndCause.entries.firstOrNull { it.name == item.getString(KEY_CAUSE) } ?: ProcessEndCause.Other,
                    endedAt = if (item.isNull(KEY_ENDED_AT)) null else item.getLong(KEY_ENDED_AT),
                ),
            )
        }
        KIND_GIT_TRANSFER_INTERRUPTED -> AppNotice.GitTransferInterrupted(
            id = item.getString(KEY_ID),
            at = item.getLong(KEY_AT),
            read = item.getBoolean(KEY_READ),
            label = item.getString(KEY_LABEL),
            target = item.getString(KEY_TARGET),
        )
        KIND_WORKSPACES_MOVED_IN -> {
            val earlier = item.getJSONArray(KEY_EARLIER)
            AppNotice.WorkspacesMovedIn(
                id = item.getString(KEY_ID),
                at = item.getLong(KEY_AT),
                read = item.getBoolean(KEY_READ),
                earlier = (0 until earlier.length()).map { index ->
                    earlier.getJSONObject(index).let { EarlierWorkspace(it.getString(KEY_NAME), it.getString(KEY_PATH)) }
                },
            )
        }
        else -> null
    }

    companion object {
        private const val MAX_NOTICES = 50

        // Stored keys and kinds; do not rename.
        private const val PREFERENCES_NAME = "notices"
        private const val KEY_NOTICES = "notices"
        private const val KEY_KIND = "kind"
        private const val KIND_SESSIONS_ENDED = "terminal-sessions-ended"
        private const val KEY_ID = "id"
        private const val KEY_AT = "at"
        private const val KEY_READ = "read"
        private const val KEY_CAUSE = "cause"
        private const val KEY_ENDED_AT = "endedAt"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_TITLE = "title"
        private const val KEY_WORKSPACE = "workspace"
        private const val KIND_GIT_TRANSFER_INTERRUPTED = "git-transfer-interrupted"
        private const val KEY_LABEL = "label"
        private const val KEY_TARGET = "target"
        private const val KIND_WORKSPACES_MOVED_IN = "workspaces-moved-in"
        private const val KEY_EARLIER = "earlier"
        private const val KEY_NAME = "name"
        private const val KEY_PATH = "path"

        // Holds the application context only, which lives as long as the process.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: NoticeStore? = null

        fun get(context: Context): NoticeStore = instance ?: synchronized(this) {
            instance ?: NoticeStore(context.applicationContext).also { instance = it }
        }
    }
}
