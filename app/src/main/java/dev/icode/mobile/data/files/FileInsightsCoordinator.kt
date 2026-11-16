package dev.icode.mobile.data.files

import dev.icode.mobile.data.io.FileHeads
import dev.icode.mobile.domain.isAtOrUnder
import dev.icode.mobile.data.io.tallyTree
import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.files.BrowsedItem
import dev.icode.mobile.domain.files.ExplorerView
import dev.icode.mobile.domain.files.FactKind
import dev.icode.mobile.domain.files.FileFacts
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.files.ScanScope
import dev.icode.mobile.domain.files.ScannedView
import dev.icode.mobile.domain.files.StorageInsights
import dev.icode.mobile.domain.files.StoragePlace
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
 * What the explorers learn of their files (docs/workspaces-and-files.md): Properties of
 * one item or many — what is known at once shown at once, a folder's size measured while it stays
 * open — Manage storage's scan, and the scan's view shown, listed from it. Requests and iCode's own changes are taken one after another,
 * off the main thread and never behind the mutation lock; measuring and scanning run on jobs of
 * their own, a new one replacing the one before.
 */
internal class FileInsightsCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val places: FilePlaces,
    private val follower: FileFollower,
    private val scan: StorageScan,
    private val paths: WorkspacePaths,
) {
    /** What reaches it, handled one after another: a request, or iCode's own change. */
    private sealed interface Event {
        class Asked(val intent: FilesIntent.Insight) : Event
        class Changed(val steps: List<FileStep>) : Event
        data object Scanned : Event
    }

    private val events = Channel<Event>(Channel.UNLIMITED)
    private var measuring: Job? = null
    @Volatile private var scanning: Job? = null
    @Volatile private var seen: Long? = null
    private var viewing: ExplorerView? = null

    fun start() {
        scope.launch {
            for (event in events) {
                // One that fails — a file gone while it was read — is said in the log, and the next is taken as ever.
                runCatching {
                    when (event) {
                        is Event.Asked -> asked(event.intent)
                        is Event.Changed -> changed(event.steps)
                        Event.Scanned -> publishView()
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
            is FilesIntent.ListView -> {
                viewing = intent.view
                publishView()
            }
            FilesIntent.CloseView -> {
                viewing = null
                stateHolder.update { it.copy(explorer = it.explorer.copy(scanned = null)) }
            }
        }
    }

    /** The scan follows the change; Properties of what it moved or took away are read again. */
    private fun changed(steps: List<FileStep>) {
        scan.follow(steps)
        if (scan.found != null) {
            publishScan()
            publishView()
        }
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
            type = if (kind == FactKind.File) FileHeads.typeOf(path) else null,
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
                events.trySend(Event.Scanned)
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

    /** The scan's view shown, as it lists it: each file as the explorer lists one; Duplicates' sets beside. */
    private fun publishView() {
        val view = viewing ?: return
        val found = scan.found?.takeIf { it.scope == view.scope }
        val files = found?.let { StorageInsights.listed(view, it.files, it.duplicates) }.orEmpty()
        val rows = files.map { file ->
            val place = if (places.storage.contains(file.location)) StoragePlace.Shared else StoragePlace.Private
            BrowsedItem(
                File(file.location).name, file.location, paths.runtimePathOf(file.location), place, folder = false,
                bytes = file.bytes, changedAt = file.changedAt, type = file.type,
            )
        }
        val groups = found?.duplicates.orEmpty().takeIf { view is ExplorerView.Duplicates }.orEmpty()
        stateHolder.update { it.copy(explorer = it.explorer.copy(scanned = ScannedView(view, rows, groups))) }
    }
}
