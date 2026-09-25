package dev.icode.mobile.domain

object WorkspaceNameRules {
    fun validationError(rawName: String): String? = nameError(rawName, "folder")

    fun fileValidationError(rawName: String): String? = nameError(rawName, "file")

    fun workspaceFolderValidationError(rawName: String): String? =
        validationError(rawName) ?: if (rawName.trim() == ".git") "The .git path is reserved for Git metadata" else null

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
