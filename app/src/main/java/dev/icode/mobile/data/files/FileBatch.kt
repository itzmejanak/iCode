package dev.icode.mobile.data.files

import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.domain.files.ConflictAnswer
import dev.icode.mobile.domain.files.ConflictSide
import dev.icode.mobile.domain.files.FileConflict
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.files.Reversal
import dev.icode.mobile.domain.files.TrashedItem
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Collections
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * One operation on many items, inside its task (docs/workspaces-and-files.md): each
 * carried, duplicated, renamed, put in Trash, restored, reversed, or — an archive's — settled in turn
 * through [operator]. A name already there is asked about through [questions] — Replace, Merge, Keep
 * both, Skip, or stop — and in Private a merge never touches Git's `.git`. Every step done is kept in
 * [steps], in order: however the operation ends, what it did stays, one change to undo.
 */
internal class FileBatch(
    private val operator: FileOperator,
    private val questions: FileQuestions,
    val progress: TaskRunner.Progress,
    /** Why what is at or under a location cannot be replaced now — unsaved edits — or null. */
    private val edited: (location: String) -> String?,
) {
    val steps: MutableList<FileStep> = Collections.synchronizedList(mutableListOf())

    /** What the result says besides: what a merge left where it was, and why. */
    val notes: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** "Do this for the other N": the answer given for every question after. */
    private var always: ConflictAnswer? = null

    /** How an item goes where it goes: moved, copied, or — what an archive operation staged — settled, kept as made. */
    private enum class How { Move, Copy, Settle }

    /** [locations] into the folder [into], moved or copied; a copy into its own folder goes beside itself. */
    suspend fun carry(locations: List<String>, into: String, move: Boolean) = carry(locations, into, if (move) How.Move else How.Copy)

    /**
     * What an archive operation [staged] into the folder [into], under the name it has: a name there
     * asked about when [asking] — Extract's — else a free one taken — Compress's. Kept as made.
     */
    suspend fun settle(staged: String, into: String, asking: Boolean) =
        if (asking) carry(listOf(staged), into, How.Settle) else place(staged, freeTarget(staged, into), How.Settle)

    private suspend fun carry(locations: List<String>, into: String, how: How) {
        locations.forEachIndexed { index, location ->
            val target = File(into, File(location).name).path
            when {
                target == location -> if (how == How.Copy) place(location, freeTarget(location, into), how)
                !fileExists(target) -> place(location, target, how)
                else -> when (answer(location, target, others = locations.drop(index + 1).count { fileExists(File(into, File(it).name).path) })) {
                    ConflictAnswer.Skip -> Unit
                    ConflictAnswer.KeepBoth -> place(location, freeTarget(location, into), how)
                    ConflictAnswer.Replace -> {
                        replace(target)
                        place(location, target, how)
                    }
                    ConflictAnswer.Merge -> merge(location, target, how)
                }
            }
        }
    }

    /** Each of [locations] beside itself, under a free name; it never asks. */
    suspend fun duplicate(locations: List<String>) = locations.forEach { location ->
        place(location, freeTarget(location, File(location).parent ?: return@forEach), How.Copy)
    }

    /**
     * Each of [renames], one folder's items, to its new name. A name another of them has yet to give
     * up waits for it; a ring of them — two swapping names — goes through a passing name, one that
     * would still be seen, and renamed by hand, if iCode stopped midway.
     */
    suspend fun rename(renames: List<Pair<String, String>>) {
        val pending = renames.filter { (from, to) -> from != to }.toMutableList()
        while (pending.isNotEmpty()) {
            val ready = pending.firstOrNull { (_, to) -> pending.none { (from, _) -> from == to } }
            if (ready != null) {
                steps += operator.move(ready.first, ready.second, progress)
                pending.remove(ready)
            } else {
                val (from, to) = pending.removeAt(0)
                val passing = File(File(from).parentFile, "${File(from).name} (renaming ${UUID.randomUUID().toString().take(8)})").path
                steps += operator.move(from, passing, progress)
                pending += passing to to
            }
        }
    }

    /** Each of [locations] into its place's Trash. */
    suspend fun trash(locations: List<String>) = locations.forEach { steps += operator.trash(it, progress) }

    /** Each of [items] back where it was; a name taken there asks, as a paste does. */
    suspend fun restore(items: List<TrashedItem>) {
        items.forEachIndexed { index, item ->
            val target = item.from
            val folder = File(target).parent.orEmpty()
            val answer = if (!fileExists(target)) null else answer(item.heldAt, target, others = items.drop(index + 1).count { fileExists(it.from) })
            when (answer) {
                null -> steps += operator.restore(item, target, progress)
                ConflictAnswer.Skip -> Unit
                ConflictAnswer.KeepBoth -> steps += operator.restore(item, freeTarget(item.name, folder, item.folder), progress)
                ConflictAnswer.Replace -> {
                    replace(target)
                    steps += operator.restore(item, target, progress)
                }
                ConflictAnswer.Merge -> {
                    // Back beside the folder there, then moved into it as a merge moves.
                    val beside = freeTarget(item.name, folder, item.folder)
                    steps += operator.restore(item, beside, progress)
                    merge(beside, target, How.Move)
                }
            }
        }
    }

    /** [reversals] done in order: an undo, or a redo. */
    suspend fun reverse(reversals: List<Reversal>) = reversals.forEach { reversal ->
        steps += when (reversal) {
            is Reversal.MoveBack -> operator.move(reversal.from, reversal.to, progress)
            is Reversal.TrashMade ->
                if (operator.isEmptyFolder(reversal.location)) operator.removeEmpty(reversal.location, progress)
                else operator.trash(reversal.location, progress)
            is Reversal.Restore -> operator.restore(
                operator.trash.item(reversal.id) ?: throw IOException("${File(reversal.to).name} is no longer in Trash"), reversal.to, progress,
            )
            is Reversal.MakeFolder -> operator.makeFolder(reversal.location, progress)
        }
    }

    private suspend fun place(location: String, target: String, how: How) {
        steps += when (how) {
            How.Move -> operator.move(location, target, progress)
            How.Copy -> operator.copy(location, target, progress)
            How.Settle -> operator.settle(location, target, progress)
        }
    }

    /**
     * What the folder [folder] holds into the folder [into], each name asked about as it meets one; a
     * moved folder left empty goes. A staged one is its operation's to clear.
     */
    private suspend fun merge(folder: String, into: String, how: How) {
        val gitOwned = operator.places.of(into).gitOwnsDotGit
        val (git, rest) = File(folder).list().orEmpty().sorted().partition { gitOwned && it == ".git" }
        if (git.isNotEmpty()) notes += "${File(folder).name}'s .git was left where it was: Git's own folder is never merged"
        carry(rest.map { File(folder, it).path }, into, how)
        if (how == How.Move && operator.isEmptyFolder(folder)) steps += operator.removeEmpty(folder, progress)
    }

    /** The one at [target] to Trash, so the one coming takes its place — refused while it holds unsaved edits. */
    private suspend fun replace(target: String) {
        edited(target)?.let { throw IOException(it) }
        steps += operator.trash(target, progress)
    }

    /** How a name met is answered: asked, or as "Do this for the other N" said. Null from the question stops the operation. */
    private suspend fun answer(coming: String, there: String, others: Int): ConflictAnswer {
        val comingSide = side(coming)
        val thereSide = side(there)
        val first = if (comingSide.folder && thereSide.folder) ConflictAnswer.Merge else ConflictAnswer.Replace
        val answer = always ?: run {
            val conflict = { id: Long -> FileConflict(id, File(there).name, File(there).parent.orEmpty(), comingSide, thereSide, others) }
            val answered = questions.ask(conflict, progress) ?: throw CancellationException("Stopped")
            if (answered.forAll) always = answered.answer
            answered.answer
        }
        // Merge and Replace are one choice, the first: the one that fits these two.
        return if (answer == ConflictAnswer.Merge || answer == ConflictAnswer.Replace) first else answer
    }

    /** One side as the question shows it; a link as the link it is. */
    private fun side(location: String): ConflictSide {
        val attributes = Files.readAttributes(File(location).toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        return ConflictSide(attributes.isDirectory, attributes.size().takeUnless { attributes.isDirectory }, attributes.lastModifiedTime().toMillis())
    }

    /** [location]'s name in [folder], or “name copy”… when taken. */
    private fun freeTarget(location: String, folder: String): String =
        freeTarget(File(location).name, folder, Files.isDirectory(File(location).toPath(), LinkOption.NOFOLLOW_LINKS))

    private fun freeTarget(name: String, folder: String, isFolder: Boolean): String = freeName(File(folder).toPath(), name, isFolder).toString()
}
