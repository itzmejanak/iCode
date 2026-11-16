package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.FileType
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.countOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One line of Properties (docs/workspaces-and-files.md): what it says; a [path] has Copy beside it. */
data class PropertyRow(val label: String, val value: String, val path: Boolean = false)

object FileProperties {
    /**
     * Properties of [facts]. One item: its [name], kind — a workspace with its [branch], a link with
     * where it points, a file by its kind and extension — where it is, its size, when it changed and,
     * a file in Private, whether it runs. Many: how many, their size, and the folder they are in.
     * [path] is the one item's runtime path, or that folder's — none for many a view gathered from
     * more than one. No creation time: Android keeps none.
     */
    fun rows(facts: FileFacts, name: String, path: String?, branch: String?, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<PropertyRow> {
        if (facts.kind == null) return listOfNotNull(PropertyRow("Items", items(facts)), PropertyRow("Size", size(facts)), path?.let { PropertyRow("In", it, path = true) })
        return listOfNotNull(
            PropertyRow("Name", name),
            PropertyRow("Kind", kind(facts.kind, name, facts.linkTarget, branch, facts.type)),
            path?.let { PropertyRow("Location", it, path = true) },
            PropertyRow("Size", size(facts)),
            facts.modifiedAt?.let { PropertyRow("Modified", said(it, now, zone)) },
            facts.executable?.let { PropertyRow("Executable", if (it) "Yes" else "No") },
        )
    }

    /**
     * Properties of what Trash holds, as it was deleted: its name, kind, where it was — where
     * Restore puts it — its size, when it was deleted, and until when Trash keeps it.
     */
    fun trashed(item: TrashedItem, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<PropertyRow> {
        val kind = when {
            item.workspace -> FactKind.Workspace
            item.folder -> FactKind.Folder
            else -> FactKind.File
        }
        return listOf(
            PropertyRow("Name", item.name),
            PropertyRow("Kind", kind(kind, item.name, null, null)),
            PropertyRow("Deleted from", item.path, path = true),
            PropertyRow("Size", item.bytes?.let { bytes(it, single = !item.folder) } ?: UNREAD),
            PropertyRow("Deleted", said(item.deletedAt, now, zone)),
            PropertyRow("Kept until", DATE.withZone(zone).format(Instant.ofEpochMilli(TrashRules.removedAt(item)))),
        )
    }

    /** "3 items · 2 folders, 1 file". */
    fun items(facts: FileFacts): String =
        "${countOf(facts.locations.size, "item")} · ${FileSelection.counted(facts.folders, facts.locations.size - facts.folders)}"

    private fun kind(kind: FactKind, name: String, linkTarget: String?, branch: String?, type: FileType? = null): String = when (kind) {
        FactKind.Folder -> "Folder"
        FactKind.Workspace -> branch?.let { "Workspace · $it" } ?: "Workspace"
        FactKind.Link -> linkTarget?.let { "Link to $it" } ?: "Link"
        FactKind.File -> FileCategories.of(type ?: FileTypes.of(name), name).one + FilesRules.extension(name).let { if (it.isEmpty()) "" else " · .$it" }
    }

    /** A file's "1.2 MB (1,234,567 bytes)"; a folder's or many items' "340 MB · 1234 files, 56 folders"; until measured, so said. */
    private fun size(facts: FileFacts): String {
        val measured = facts.measured ?: return "Measuring…"
        val single = facts.kind == FactKind.File || facts.kind == FactKind.Link
        val bytes = measured.bytes?.let { bytes(it, single) } ?: UNREAD
        return if (single) bytes else "$bytes · ${countOf(measured.files, "file")}, ${countOf(measured.folders, "folder")}"
    }

    /** One file's "1.2 MB (1,234,567 bytes)"; else "340 MB". */
    private fun bytes(bytes: Long, single: Boolean) = if (single) "${ByteSize.format(bytes)} (${"%,d".format(bytes)} bytes)" else ByteSize.format(bytes)

    /** "28 Oct 2026, 14:05 · 2h ago". */
    private fun said(at: Long, now: Long, zone: ZoneId) = "${DATE.withZone(zone).format(Instant.ofEpochMilli(at))} · ${RelativeTime.since(at, now)}"

    private const val UNREAD = "Unknown, a folder could not be read"

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
}
