package dev.icode.mobile.data.workspace

import dev.icode.mobile.domain.FileEntry
import dev.icode.mobile.domain.FileKind

internal data class DocumentNode(
    val name: String,
    val location: String,
    val kind: FileKind,
)

internal fun projectVisibleFiles(
    rootLocation: String,
    childrenByParent: Map<String, List<DocumentNode>>,
    expandedLocations: Set<String>,
): List<FileEntry> {
    val visible = mutableListOf<FileEntry>()
    val visited = mutableSetOf<String>()

    fun appendChildren(parentLocation: String, depth: Int) {
        if (!visited.add(parentLocation)) return
        childrenByParent[parentLocation].orEmpty().forEach { node ->
            val expanded = node.kind == FileKind.Folder && node.location in expandedLocations
            visible += FileEntry(
                name = node.name,
                path = node.location,
                kind = node.kind,
                depth = depth,
                expanded = expanded,
            )
            if (expanded) appendChildren(node.location, depth + 1)
        }
    }

    appendChildren(rootLocation, depth = 0)
    return visible
}
