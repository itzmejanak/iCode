package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.countOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One line of Properties (docs/explorer-gaps-plan.md §3.9): what it says; a [path] has Copy beside it. */
data class PropertyRow(val label: String, val value: String, val path: Boolean = false)

object FileProperties {
    /**
     * Properties of [facts]. One item: its [name], kind — a workspace with its [branch], a link with
     * where it points, a file by its kind and extension — where it is, its size, when it changed and,
     * a file in Private, whether it runs. Many: how many, their size, and the folder they are in.
     * [path] is the one item's runtime path, or that folder's. No creation time: Android keeps none.
     */
    fun rows(facts: FileFacts, name: String, path: String, branch: String?, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<PropertyRow> {
        if (facts.kind == null) return listOf(PropertyRow("Items", items(facts)), PropertyRow("Size", size(facts)), PropertyRow("In", path, path = true))
        return listOfNotNull(
            PropertyRow("Name", name),
            PropertyRow("Kind", kind(facts.kind, name, facts.linkTarget, branch)),
            PropertyRow("Location", path, path = true),
            PropertyRow("Size", size(facts)),
            facts.modifiedAt?.let { PropertyRow("Modified", "${DATE.withZone(zone).format(Instant.ofEpochMilli(it))} · ${RelativeTime.since(it, now)}") },
            facts.executable?.let { PropertyRow("Executable", if (it) "Yes" else "No") },
        )
    }

    /** "3 items · 2 folders, 1 file". */
    fun items(facts: FileFacts): String =
        "${countOf(facts.locations.size, "item")} · ${FileSelection.counted(facts.folders, facts.locations.size - facts.folders)}"

    private fun kind(kind: FactKind, name: String, linkTarget: String?, branch: String?): String = when (kind) {
        FactKind.Folder -> "Folder"
        FactKind.Workspace -> branch?.let { "Workspace · $it" } ?: "Workspace"
        FactKind.Link -> linkTarget?.let { "Link to $it" } ?: "Link"
        FactKind.File -> FileCategories.of(name).one + FilesRules.extension(name).let { if (it.isEmpty()) "" else " · .$it" }
    }

    /** A file's "1.2 MB (1,234,567 bytes)"; a folder's or many items' "340 MB · 1234 files, 56 folders"; until measured, so said. */
    private fun size(facts: FileFacts): String {
        val measured = facts.measured ?: return "Measuring…"
        val single = facts.kind == FactKind.File || facts.kind == FactKind.Link
        val bytes = measured.bytes?.let { if (single) "${ByteSize.format(it)} (${"%,d".format(it)} bytes)" else ByteSize.format(it) }
            ?: "Unknown, a folder could not be read"
        return if (single) bytes else "$bytes · ${countOf(measured.files, "file")}, ${countOf(measured.folders, "folder")}"
    }

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
}
