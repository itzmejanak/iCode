package dev.icode.mobile.data.files

import dev.icode.mobile.data.io.isAtOrUnder
import dev.icode.mobile.data.io.tallyTree
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.files.FactKind
import dev.icode.mobile.domain.files.FileFacts
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.files.ScanScope
import dev.icode.mobile.domain.files.StorageInsights
import dev.icode.mobile.domain.files.TreeFacts
import dev.icode.mobile.domain.files.paths
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * What the explorers learn of their files (docs/explorer-gaps-plan.md §3.9, §3.10): Properties of
 * one item or many — what is known at once shown at once, a folder's size measured while it stays
 * open — and Manage storage's scan. Requests and iCode's own changes are taken one after another,
 * off the main thread and never behind the mutation lock; measuring and scanning run on jobs of
 * their own, a new one replacing the one before.
 */
internal class FileInsightsCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val places: FilePlaces,
    private val follower: FileFollower,
    private val scan: StorageScan,
) {
    /** What reaches it, handled one after another: a request, or iCode's own change. */
    private sealed interface Event {
        class Asked(val intent: FilesIntent.Insight) : Event
        class Changed(val steps: List<FileStep>) : Event
    }

    private val events = Channel<Event>(Channel.UNLIMITED)
    private var measuring: Job? = null
    @Volatile private var scanning: Job? = null
    @Volatile private var seen: Long? = null

    fun start() {
        scope.launch {
            for (event in events) {
                // One that fails — a file gone while it was read — is said in the log, and the next is taken as ever.
                runCatching {
                    when (event) {
                        is Event.Asked -> asked(event.intent)
                        is Event.Changed -> changed(event.steps)
                    }
                }.onFailure { error -> stateHolder.failureText("files", "Unable to read what the files hold", error) }
            }
        }
    }

    fun handle(intent: FilesIntent.Insight) {
        events.trySend(Event.Asked(intent))
    }

    /** iCode's own change, done, undone or redone. */
    fun follow(steps: List<FileStep>) {
        events.trySend(Event.Changed(steps))
    }

    private fun asked(intent: FilesIntent.Insight) {
        when (intent) {
            is FilesIntent.Properties -> properties(intent.locations)
            FilesIntent.CloseProperties -> {
                measuring?.cancel()
                stateHolder.update { it.copy(explorer = it.explorer.copy(facts = null)) }
            }
            is FilesIntent.Scan -> scan(intent.scope)
            FilesIntent.StopScan -> scanning?.cancel()
        }
    }

    /** The scan follows the change; Properties of what it moved or took away are read again. */
    private fun changed(steps: List<FileStep>) {
        scan.follow(steps)
        if (scan.found != null) publishScan()
        val facts = stateHolder.current.explorer.facts ?: return
        if (facts.locations.none { location -> steps.any { step -> step.paths.any { location.isAtOrUnder(it) || it.isAtOrUnder(location) } } }) return
        properties(facts.locations.mapNotNull { StorageInsights.after(it, steps) })
    }

    private fun properties(locations: List<String>) {
        measuring?.cancel()
        val facts = facts(locations)
        stateHolder.update { it.copy(explorer = it.explorer.copy(facts = facts)) }
        if (facts == null || facts.measured != null) return
        measuring = scope.launch {
            val measured = measure(facts.locations) { isActive }
            if (!isActive) return@launch
            stateHolder.update { state ->
                state.copy(explorer = state.explorer.copy(facts = state.explorer.facts?.takeIf { it.locations == facts.locations }?.copy(measured = measured)))
            }
        }
    }

    /** What is known of [locations] at once: one item's kind, where a link points, when it changed; a file's size. */
    private fun facts(locations: List<String>): FileFacts? {
        val present = locations.filter(::fileExists).ifEmpty { return null }
        val folders = present.count { Files.isDirectory(File(it).toPath(), LinkOption.NOFOLLOW_LINKS) }
        val one = present.singleOrNull() ?: return FileFacts(present, kind = null, folders = folders)
        val path = File(one).toPath()
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        val kind = when {
            attributes.isSymbolicLink -> FactKind.Link
            attributes.isDirectory -> if (follower.isWorkspace(one)) FactKind.Workspace else FactKind.Folder
            else -> FactKind.File
        }
        return FileFacts(
            locations = present,
            kind = kind,
            folders = folders,
            linkTarget = if (kind == FactKind.Link) Files.readSymbolicLink(path).toString() else null,
            modifiedAt = attributes.lastModifiedTime().toMillis(),
            executable = if (kind == FactKind.File && !places.storage.contains(one)) File(one).canExecute() else null,
            measured = if (attributes.isDirectory) null else TreeFacts(attributes.size(), files = 0, folders = 0),
        )
    }

    /** What [locations] hold together: their files' bytes, unknown when a folder could not be read, and what lies in them. */
    private fun measure(locations: List<String>, keepGoing: () -> Boolean): TreeFacts =
        locations.map { tallyTree(File(it), keepGoing = keepGoing) }.let { tallies ->
            TreeFacts(
                bytes = tallies.takeIf { all -> all.all { it.bytes != null } }?.sumOf { it.bytes!! },
                files = tallies.sumOf { it.files },
                folders = tallies.sumOf { it.folders },
            )
        }

    /** A scan of [where], replacing the one running; what it has seen published as it goes, by it alone while it is the one. */
    private fun scan(where: ScanScope) {
        scanning?.cancel()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val mine = coroutineContext.job
            runCatching {
                scan.scan(where, keepGoing = { isActive }) { count ->
                    if (scanning === mine) {
                        seen = count
                        publishScan(where)
                    }
                }
            }
            if (scanning === mine) {
                seen = null
                publishScan(where)
            }
        }
        scanning = job
        seen = 0
        publishScan(where)
        job.start()
    }

    /** Manage storage as it stands: the last scan, else nothing found yet where it looks; and how far the one running has got. */
    private fun publishScan(where: ScanScope? = null) {
        val scope = where ?: scan.found?.scope ?: return
        val shown = scan.overview()?.takeIf { it.scope == scope }
            ?: StorageInsights.overview(scope, emptyList(), 0, emptyList(), scannedAt = null, sharedUnread = scope != ScanScope.Private && !places.storage.hasAccess())
        stateHolder.update { it.copy(explorer = it.explorer.copy(storage = shown.copy(scanning = seen))) }
    }
}
