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
