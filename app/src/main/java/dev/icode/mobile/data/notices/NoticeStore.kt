package dev.icode.mobile.data.notices

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.icode.mobile.domain.GitTransferLabel
import dev.icode.mobile.domain.LostTerminalSession
import dev.icode.mobile.domain.LostTerminalSessions
import dev.icode.mobile.domain.ProcessEndCause
import dev.icode.mobile.domain.notices.GitNotices
import dev.icode.mobile.domain.notices.Notice
import dev.icode.mobile.domain.notices.NoticeAction
import dev.icode.mobile.domain.notices.NoticeCommand
import dev.icode.mobile.domain.notices.NoticeFilter
import dev.icode.mobile.domain.notices.NoticeKeep
import dev.icode.mobile.domain.notices.NoticePlace
import dev.icode.mobile.domain.notices.NoticeRules
import dev.icode.mobile.domain.notices.NoticeSource
import dev.icode.mobile.domain.notices.NoticeTarget
import dev.icode.mobile.domain.notices.NoticeTone
import dev.icode.mobile.domain.notices.TerminalNotices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Notifications page's notices (docs/notices-redesign-plan.md D3): kept until the user deletes
 * them, newest first, what needs the user and the rest each up to their own most
 * ([NoticeRules.added]). One per process, so every screen and service sees the same list. What an
 * earlier build kept is read and kept on.
 */
internal class NoticeStore(private val preferences: SharedPreferences) {
    private val mutableNotices = MutableStateFlow(read())
    val notices: StateFlow<List<Notice>> = mutableNotices.asStateFlow()

    /** Keeps [notice] unless one with its id is already kept, or is a website notice the user deleted. */
    @Synchronized
    fun add(notice: Notice) {
        val current = mutableNotices.value
        if (current.any { it.id == notice.id } || notice.id in forgotten()) return
        save(NoticeRules.added(current, notice))
    }

    @Synchronized
    fun markRead(id: String) = change(id) { it.copy(read = true) }

    @Synchronized
    fun markAllRead() {
        val current = mutableNotices.value
        if (current.all { it.read }) return
        save(current.map { it.copy(read = true) })
    }

    /** Swiped from Android's shade: not posted again. */
    @Synchronized
    fun dismiss(id: String) = change(id) { it.copy(dismissed = true) }

    /** Whether the website notice [id] is kept or was deleted: either way, it is not new. */
    fun knows(id: String): Boolean = mutableNotices.value.any { it.id == id } || id in forgotten()

    @Synchronized
    fun delete(id: String) {
        val current = mutableNotices.value
        if (current.none { it.id == id }) return
        forget(listOf(id))
        save(current.filterNot { it.id == id })
    }

    /** Clear all: what [filter] shows. */
    @Synchronized
    fun clear(filter: NoticeFilter) {
        val (gone, kept) = mutableNotices.value.partition(filter::matches)
        if (gone.isEmpty()) return
        forget(gone.map { it.id })
        save(kept)
    }

    private fun change(id: String, how: (Notice) -> Notice) {
        val current = mutableNotices.value
        val at = current.indexOfFirst { it.id == id }
        if (at < 0) return
        val changed = how(current[at])
        if (changed == current[at]) return
        save(current.toMutableList().also { it[at] = changed })
    }

    private fun forgotten(): Set<String> = preferences.getStringSet(KEY_FORGOTTEN, emptySet()).orEmpty()

    /** A deleted website notice never comes back: its id is kept (the newest [NoticeRules.MAX_NEEDS_YOU]). */
    private fun forget(ids: List<String>) {
        val website = ids.filter { it.startsWith(Notice.WEBSITE_PREFIX) }
        if (website.isEmpty()) return
        val kept = (forgotten() + website).toList().takeLast(NoticeRules.MAX_NEEDS_YOU).toSet()
        preferences.edit { putStringSet(KEY_FORGOTTEN, kept) }
    }

    private fun save(notices: List<Notice>) {
        mutableNotices.value = notices
        preferences.edit { putString(KEY_NOTICES, NoticeJson.encode(notices)) }
    }

    /** A notice that cannot be read back — damaged, or of a kind this build does not know — is left out. */
    private fun read(): List<Notice> {
        val array = runCatching { JSONArray(preferences.getString(KEY_NOTICES, null) ?: return emptyList()) }
            .getOrElse { return emptyList() }
        return (0 until array.length()).mapNotNull { index -> runCatching { NoticeJson.decode(array.getJSONObject(index)) }.getOrNull() }
    }

    companion object {
        // Stored keys; do not rename.
        private const val PREFERENCES_NAME = "notices"
        private const val KEY_NOTICES = "notices"
        private const val KEY_FORGOTTEN = "forgotten_website_notices"

        @Volatile
        private var instance: NoticeStore? = null

        fun get(context: Context): NoticeStore = instance ?: synchronized(this) {
            instance ?: NoticeStore(context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)).also { instance = it }
        }
    }
}

/**
 * A notice as it is stored: one JSON object of kind `notice`. The kinds an earlier build wrote —
 * a message, terminal sessions that ended, a transfer cut short — are read into notices.
 */
internal object NoticeJson {
    fun encode(notices: List<Notice>): String = JSONArray().apply { notices.forEach { put(encode(it)) } }.toString()

    fun encode(notice: Notice): JSONObject = JSONObject()
        .put(KEY_KIND, KIND_NOTICE)
        .put(KEY_ID, notice.id)
        .put(KEY_AT, notice.at)
        .put(KEY_READ, notice.read)
        .put(KEY_TONE, notice.tone.name)
        .put(KEY_SOURCE, notice.source.name)
        .put(KEY_TITLE, notice.title)
        .putOpt(KEY_DETAIL, notice.detail)
        .putOpt(KEY_OUTPUT, notice.output)
        .put(KEY_KEEP, notice.keep.name)
        .put(KEY_TELL_AWAY, notice.tellAway)
        .putOpt(KEY_REPEAT_KEY, notice.repeatKey)
        .put(KEY_REPEATS, notice.repeats)
        .put(KEY_AWAY, notice.away)
        .put(KEY_DISMISSED, notice.dismissed)
        .putOpt(KEY_PLACE, notice.place?.let(::encode))
        .put(KEY_ACTIONS, JSONArray().apply { notice.actions.forEach { put(encode(it)) } })

    fun decode(item: JSONObject): Notice? = when (item.getString(KEY_KIND)) {
        KIND_NOTICE -> Notice(
            tone = NoticeTone.valueOf(item.getString(KEY_TONE)),
            source = NoticeSource.entries.firstOrNull { it.name == item.optString(KEY_SOURCE) } ?: NoticeSource.ICode,
            title = item.getString(KEY_TITLE),
            place = item.optJSONObject(KEY_PLACE)?.let(::place),
            detail = item.text(KEY_DETAIL),
            output = item.text(KEY_OUTPUT),
            actions = item.optJSONArray(KEY_ACTIONS)?.let { array -> (0 until array.length()).mapNotNull { action(array.getJSONObject(it)) } }.orEmpty(),
            keep = NoticeKeep.valueOf(item.getString(KEY_KEEP)),
            tellAway = item.optBoolean(KEY_TELL_AWAY),
            repeatKey = item.text(KEY_REPEAT_KEY),
            id = item.getString(KEY_ID),
            at = item.getLong(KEY_AT),
            read = item.getBoolean(KEY_READ),
            repeats = item.optInt(KEY_REPEATS, 1),
            away = item.optBoolean(KEY_AWAY),
            dismissed = item.optBoolean(KEY_DISMISSED),
        )
        // What earlier builds kept.
        KIND_MESSAGE -> {
            val id = item.getString(KEY_ID)
            val website = id.startsWith(Notice.WEBSITE_PREFIX)
            val tone = NoticeTone.valueOf(item.getString(KEY_TONE))
            Notice(
                tone = tone,
                source = if (website) NoticeSource.Website else NoticeSource.ICode,
                title = item.getString(KEY_TITLE),
                detail = item.text(KEY_DETAIL),
                actions = listOfNotNull(item.text(KEY_LINK_URL)?.let { NoticeAction.OpenUrl(it, item.optString(KEY_LINK_LABEL, "Open Link")) }),
                keep = if (website) NoticeKeep.NeedsYou else NoticeKeep.of(tone),
                id = id,
                at = item.getLong(KEY_AT),
                read = item.getBoolean(KEY_READ),
            )
        }
        KIND_SESSIONS_ENDED -> {
            val sessions = item.getJSONArray(KEY_SESSIONS)
            val lost = LostTerminalSessions(
                sessions = (0 until sessions.length()).map { index ->
                    val session = sessions.getJSONObject(index)
                    LostTerminalSession(session.getString(KEY_TITLE), session.text(KEY_WORKSPACE))
                },
                cause = ProcessEndCause.entries.firstOrNull { it.name == item.getString(KEY_CAUSE) } ?: ProcessEndCause.Other,
                endedAt = if (item.isNull(KEY_ENDED_AT)) null else item.getLong(KEY_ENDED_AT),
            )
            TerminalNotices.sessionsEnded(lost).copy(id = item.getString(KEY_ID), at = item.getLong(KEY_AT), read = item.getBoolean(KEY_READ))
        }
        KIND_GIT_TRANSFER_INTERRUPTED -> GitNotices.cutShort(item.optString(KEY_LABEL, GitTransferLabel.FETCHING), item.getString(KEY_TARGET))
            .copy(id = item.getString(KEY_ID), at = item.getLong(KEY_AT), read = item.getBoolean(KEY_READ))
        else -> null
    }

    private fun encode(place: NoticePlace): JSONObject = JSONObject()
        .putOpt(KEY_WORKSPACE, place.workspace)
        .putOpt(KEY_WORKSPACE_LOCATION, place.workspaceLocation)
        .putOpt(KEY_REPOSITORY, place.repository)
        .putOpt(KEY_BRANCH, place.branch)
        .putOpt(KEY_REMOTE, place.remote)
        .putOpt(KEY_SUBJECT, place.subject)

    private fun place(item: JSONObject) = NoticePlace(
        workspace = item.text(KEY_WORKSPACE),
        workspaceLocation = item.text(KEY_WORKSPACE_LOCATION),
        repository = item.text(KEY_REPOSITORY),
        branch = item.text(KEY_BRANCH),
        remote = item.text(KEY_REMOTE),
        subject = item.text(KEY_SUBJECT),
    )

    private fun encode(action: NoticeAction): JSONObject = when (action) {
        is NoticeAction.OpenFile -> JSONObject().put(KEY_TYPE, "open-file").put(KEY_VALUE, action.location).put(KEY_LABEL, action.label)
        is NoticeAction.Show -> JSONObject().put(KEY_TYPE, "show").put(KEY_VALUE, action.target.name)
        is NoticeAction.OpenSession -> JSONObject().put(KEY_TYPE, "open-session").put(KEY_VALUE, action.id).put(KEY_LABEL, action.label)
        is NoticeAction.OpenUrl -> JSONObject().put(KEY_TYPE, "open-url").put(KEY_VALUE, action.url).put(KEY_LABEL, action.label)
        is NoticeAction.Undo -> JSONObject().put(KEY_TYPE, "undo").put(KEY_VALUE, action.change)
        is NoticeAction.Run -> JSONObject().put(KEY_TYPE, "run").put(KEY_VALUE, action.command.name).put(KEY_LABEL, action.label)
    }

    /** An action this build does not know is left out. */
    private fun action(item: JSONObject): NoticeAction? {
        val value = item.getString(KEY_VALUE)
        val label = item.text(KEY_LABEL)
        return when (item.getString(KEY_TYPE)) {
            "open-file" -> NoticeAction.OpenFile(value, label ?: "Open")
            "show" -> NoticeTarget.entries.firstOrNull { it.name == value }?.let(NoticeAction::Show)
            "open-session" -> NoticeAction.OpenSession(value, label ?: "Open")
            "open-url" -> NoticeAction.OpenUrl(value, label ?: "Open Link")
            "undo" -> NoticeAction.Undo(value)
            "run" -> NoticeCommand.entries.firstOrNull { it.name == value }?.let { NoticeAction.Run(it, label ?: "Try Again") }
            else -> null
        }
    }

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    // Stored keys and kinds; do not rename.
    private const val KEY_KIND = "kind"
    private const val KIND_NOTICE = "notice"
    private const val KIND_MESSAGE = "message"
    private const val KIND_SESSIONS_ENDED = "terminal-sessions-ended"
    private const val KIND_GIT_TRANSFER_INTERRUPTED = "git-transfer-interrupted"
    private const val KEY_ID = "id"
    private const val KEY_AT = "at"
    private const val KEY_READ = "read"
    private const val KEY_TONE = "tone"
    private const val KEY_SOURCE = "source"
    private const val KEY_TITLE = "title"
    private const val KEY_DETAIL = "detail"
    private const val KEY_OUTPUT = "output"
    private const val KEY_KEEP = "keep"
    private const val KEY_TELL_AWAY = "tellAway"
    private const val KEY_REPEAT_KEY = "repeatKey"
    private const val KEY_REPEATS = "repeats"
    private const val KEY_AWAY = "away"
    private const val KEY_DISMISSED = "dismissed"
    private const val KEY_PLACE = "place"
    private const val KEY_ACTIONS = "actions"
    private const val KEY_WORKSPACE = "workspace"
    private const val KEY_WORKSPACE_LOCATION = "workspaceLocation"
    private const val KEY_REPOSITORY = "repository"
    private const val KEY_BRANCH = "branch"
    private const val KEY_REMOTE = "remote"
    private const val KEY_SUBJECT = "subject"
    private const val KEY_TYPE = "type"
    private const val KEY_VALUE = "value"
    private const val KEY_LABEL = "label"
    private const val KEY_LINK_URL = "linkUrl"
    private const val KEY_LINK_LABEL = "linkLabel"
    private const val KEY_CAUSE = "cause"
    private const val KEY_ENDED_AT = "endedAt"
    private const val KEY_SESSIONS = "sessions"
    private const val KEY_TARGET = "target"
}
