package dev.icode.mobile.domain

object WorkspaceNameRules {
    fun validationError(rawName: String): String? = nameError(rawName, "folder")

    fun fileValidationError(rawName: String): String? = nameError(rawName, "file")

    fun workspaceFolderValidationError(rawName: String): String? =
        validationError(rawName) ?: if (rawName.trim() == ".git") "The .git path is reserved for Git metadata" else null

    /**
     * A whole workspace's name: a folder name that does not start with a dot — such names at the
     * top of the workspaces are iCode's own staging (docs/workspace-storage-plan.md §4.2).
     */
    fun workspaceNameError(rawName: String): String? =
        validationError(rawName) ?: if (rawName.trim().startsWith('.')) "A workspace name cannot start with a dot" else null

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
