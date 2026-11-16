package dev.icode.mobile.domain

object WorkspaceNameRules {
    fun validationError(rawName: String): String? = nameError(rawName, "folder")

    fun fileValidationError(rawName: String): String? = nameError(rawName, "file")

    fun workspaceFolderValidationError(rawName: String): String? =
        validationError(rawName) ?: if (rawName.trim() == ".git") "The .git path is reserved for Git metadata" else null

    /**
     * A whole workspace's name: a folder name that does not start with a dot — such names at the
     * top of the workspaces are iCode's own staging (docs/workspaces-and-files.md).
     */
    fun workspaceNameError(rawName: String): String? =
        validationError(rawName) ?: if (rawName.trim().startsWith('.')) "A workspace name cannot start with a dot" else null

    /** A new workspace's name: [workspaceNameError], or that [taken] says a workspace has it. */
    fun newWorkspaceError(rawName: String, taken: (name: String) -> Boolean): String? =
        workspaceNameError(rawName) ?: rawName.trim().takeIf(taken)?.let { "A workspace named $it already exists" }

    /**
     * A new project's name where it will land: a new workspace's when [destination] is none, else a
     * folder's that [destination] does not hold yet.
     */
    fun projectNameError(rawName: String, destination: ProjectDestination?, workspaceTaken: (name: String) -> Boolean): String? =
        if (destination == null) newWorkspaceError(rawName, workspaceTaken)
        else workspaceFolderValidationError(rawName)
            ?: rawName.trim().takeIf { it in destination.taken }?.let { "“$it” is already in ${destination.displayPath}" }

    private fun nameError(rawName: String, kind: String): String? {
        val name = rawName.trim()
        val label = if (kind == "file") "File" else "Folder"
        return when {
            name.isEmpty() -> "Enter a $kind name"
            name == "." || name == ".." -> "Use a specific $kind name"
            kind == "file" && name == ".git" -> "The .git path is reserved for Git metadata"
            name.any { it == '/' || it == '\\' || it == '\u0000' } -> "$label names cannot contain / or \\"
            name.toByteArray(Charsets.UTF_8).size > MAX_UTF8_BYTES -> "$label name is too long"
            else -> null
        }
    }

    private const val MAX_UTF8_BYTES = 255
}
