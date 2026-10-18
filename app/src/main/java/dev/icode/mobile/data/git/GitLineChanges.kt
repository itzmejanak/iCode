package dev.icode.mobile.data.git

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.DecodedText
import dev.icode.mobile.data.workspace.DocumentCodec
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.DocumentLineChanges
import dev.icode.mobile.domain.GitDiffHunk
import dev.icode.mobile.domain.LineDiff
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.StageLineChange
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The editor's Git change markers (docs/editor-plan.md §3): each open file's version in its
 * repository's index, read when the file opens and again when the index file changes, and the
 * file's changes against it, computed again 200 ms after its text changes, as VS Code waits.
 */
internal class GitLineChanges(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val git: GitIndexOperations,
) {
    /**
     * What was read for an open file: its repository's [top] and the index's [stamp] then, its
     * [path] there, and its index version — null when the index holds none, or it cannot be
     * decoded as text.
     */
    private class Base(val top: String?, val stamp: String?, val path: String, val mode: String?, val decoded: DecodedText?)

    private val bases = MutableStateFlow<Map<String, Base>>(emptyMap())
    private val enabled = MutableStateFlow(true)
    private val reading = Mutex()

    /** The last changes computed for each file, with the text and base they were computed from. */
    private val computed = HashMap<String, Triple<String, String, List<GitDiffHunk>>>()

    init {
        val documents = stateHolder.snapshot.map { it.openDocuments }.distinctUntilChanged()
        scope.launch {
            combine(documents.map { list -> list.map { it.location } }.distinctUntilChanged(), enabled) { locations, on -> locations to on }
                .collect { (locations, on) ->
                    if (!on) return@collect bases.update { emptyMap() }
                    bases.update { current -> current.filterKeys { it in locations } }
                    read(locations.filterNot { it in bases.value })
                }
        }
        scope.launch {
            combine(documents, bases, enabled) { open, read, on -> Triple(open, read, on) }.collectLatest { (open, read, on) ->
                if (!on) return@collectLatest publish(emptyMap())
                delay(COMPUTE_DELAY_MILLIS)
                publish(compute(open, read))
            }
        }
    }

    /** Git Change Markers, as Editor Settings says; while off nothing is read or computed. */
    fun follow(markers: Flow<Boolean>) {
        scope.launch { markers.collect { enabled.value = it } }
    }

    /**
     * Source Control read a repository again: an index that changed since its files' versions were
     * read, or a file now in a repository it was not in, is read again — and any not read yet.
     */
    fun repositoryRead() {
        if (!enabled.value) return
        scope.launch {
            val open = stateHolder.current.openDocuments.map { it.location }
            val stale = open.filter { location ->
                val base = bases.value[location] ?: return@filter true
                val top = git.repositoryTopOf(location)
                top != base.top || top?.let(git::indexStampOf) != base.stamp
            }
            read(stale)
        }
    }

    /**
     * Stages only [intent]'s change: the index version with that change applied, encoded as the
     * index version was. Throws, in words for the user, when it cannot.
     */
    suspend fun stage(intent: StageLineChange) {
        val base = bases.value[intent.location]
        val decoded = base?.decoded ?: throw IOException("Git's index holds no version of this file to stage into")
        val text = stateHolder.current.openDocuments.firstOrNull { it.location == intent.location }?.text
            ?: throw IOException("The file is no longer open")
        val staged = LineDiff.stage(decoded.text, text, intent.change)
            ?: throw IOException("The file changed since this change was shown; look at it again")
        git.stageVersion(checkNotNull(base.top), base.path, checkNotNull(base.mode), DocumentCodec.encode(staged, decoded.encoding, decoded.hasByteOrderMark))
    }

    /** Reads the index versions of [locations], one script per repository. */
    private suspend fun read(locations: List<String>) {
        if (locations.isEmpty() || !git.isAvailable()) return
        reading.withLock {
            val read = locations.groupBy { git.repositoryTopOf(it) }.flatMap { (top, files) ->
                val paths = files.associateWith { location -> top?.let { File(it).toPath().relativize(File(location).toPath()).toString() } ?: location }
                // The stamp before the versions: an index changing meanwhile reads as changed next time.
                val stamp = top?.let(git::indexStampOf)
                val versions = if (top == null || stamp == null) emptyMap() else versionsOf(top, paths.values.toList())
                files.map { location ->
                    val path = paths.getValue(location)
                    val version = versions[path]
                    location to Base(top, stamp, path, version?.mode, version?.let { decode(it.bytes) })
                }
            }
            val open = stateHolder.current.openDocuments.map { it.location }.toSet()
            bases.update { current -> (current + read).filterKeys { it in open } }
        }
    }

    private suspend fun versionsOf(top: String, paths: List<String>): Map<String, IndexVersion> = try {
        git.indexVersions(top, paths)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        // No markers, rather than wrong ones; the next change of the index tries again.
        BackendLog.w("git", "Unable to read the index", error)
        emptyMap()
    }

    /** As the editor decodes the file; a version that is not text has no markers. */
    private fun decode(bytes: ByteArray): DecodedText? = runCatching { DocumentCodec.decode(bytes) }.getOrNull()

    private suspend fun compute(open: List<OpenDocument>, read: Map<String, Base>): Map<String, DocumentLineChanges> =
        withContext(Dispatchers.Default) {
            val job = coroutineContext.job
            val changes = open.mapNotNull { document ->
                val base = read[document.location]?.decoded?.text ?: return@mapNotNull null
                val last = computed[document.location]
                val hunks = if (last != null && last.first === document.text && last.second === base) {
                    last.third
                } else {
                    LineDiff.hunks(base, document.text) { job.ensureActive() }.also { computed[document.location] = Triple(document.text, base, it) }
                }
                document.location to DocumentLineChanges(base, hunks)
            }.toMap()
            computed.keys.retainAll(changes.keys)
            changes
        }

    private fun publish(changes: Map<String, DocumentLineChanges>) = stateHolder.update {
        if (it.lineChanges == changes) it else it.copy(lineChanges = changes)
    }

    private companion object {
        /** VS Code's quick diff waits this long after a change before it compares again. */
        const val COMPUTE_DELAY_MILLIS = 200L
    }
}
