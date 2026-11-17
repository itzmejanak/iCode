package dev.icode.mobile.domain

/**
 * A commit message the model writes from [workspace]'s staged changes
 * (docs/assistant-and-mcp.md): being written, written, or why it could not be.
 */
data class AssistantCommitMessage(
    val workspace: String,
    val message: String? = null,
    val failure: String? = null,
) {
    val writing: Boolean get() = message == null && failure == null
}
