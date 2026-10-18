package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.git.ConflictFiles

/** A conflicted file as the conflict owner reads and writes it: through the workspace's files, minding open tabs. */
internal class WorkspaceConflictFiles(
    private val stateHolder: ICodeStateHolder,
    private val source: WorkspaceFileSource,
    /** The file changed: open tabs catch up. */
    private val written: suspend () -> Unit,
) : ConflictFiles {
    override fun read(location: String) = source.readDocument(location)

    override fun write(document: WorkspaceDocument, text: String) = source.writeDocument(document, text)

    override fun hasUnsavedEdits(location: String) = stateHolder.current.openDocuments.any { it.location == location && it.dirty }

    override suspend fun written(location: String) = written()
}
