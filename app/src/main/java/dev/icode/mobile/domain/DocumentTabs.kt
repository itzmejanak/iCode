package dev.icode.mobile.domain

/** Move one open document to the position of another without changing its identity. */
fun reorderDocuments(
    documents: List<OpenDocument>,
    movingLocation: String,
    targetLocation: String,
): List<OpenDocument> {
    val from = documents.indexOfFirst { it.location == movingLocation }
    val to = documents.indexOfFirst { it.location == targetLocation }
    if (from < 0 || to < 0 || from == to) return documents
    return documents.toMutableList().apply { add(to, removeAt(from)) }
}

/** Closing the active tab selects its nearest surviving neighbour. */
fun selectedAfterClose(
    documents: List<OpenDocument>,
    selectedLocation: String?,
    closedLocation: String,
): String? {
    if (selectedLocation != closedLocation) return selectedLocation
    val index = documents.indexOfFirst { it.location == closedLocation }
    if (index < 0) return selectedLocation
    return documents.getOrNull(index + 1)?.location ?: documents.getOrNull(index - 1)?.location
}

/**
 * The snapshot with the open document at [location] replaced by [change] of
 * it — in the tab list and, when it is the active one, as the active document
 * too, so the two can never disagree. Unchanged when no such tab is open.
 */
fun ICodeSnapshot.withDocument(location: String, change: (OpenDocument) -> OpenDocument): ICodeSnapshot {
    val current = openDocuments.firstOrNull { it.location == location } ?: return this
    val changed = change(current)
    return copy(
        openDocuments = openDocuments.map { if (it.location == location) changed else it },
        activeDocument = if (activeDocument?.location == location) changed else activeDocument,
    )
}

/** The snapshot without the unanswered external change of [location], if it had one. */
fun ICodeSnapshot.withoutExternalChange(location: String): ICodeSnapshot =
    if (externalChanges.none { it.location == location }) this
    else copy(externalChanges = externalChanges.filterNot { it.location == location })
