package dev.icode.mobile.domain

/** A file going with a message, as the composer holds it: a workspace file's location, or a phone file's `content://` URI. */
data class AssistantDraftFile(val location: String, val name: String, val kind: AssistantAttachmentKind)

/**
 * A message waiting in a chat's queue while the chat works (docs/assistant-and-mcp.md), saved with the
 * chat. The first goes when the work in hand ends, however it ends; Send now steers that work with it.
 * [aloud]: to be answered aloud, as decided when it was sent.
 */
data class AssistantQueued(val id: String, val text: String, val files: List<AssistantDraftFile> = emptyList(), val aloud: Boolean = false)
