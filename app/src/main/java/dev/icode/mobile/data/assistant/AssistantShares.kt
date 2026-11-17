package dev.icode.mobile.data.assistant

import dev.icode.mobile.domain.AssistantExports
import java.io.File

/**
 * Shared chats as the share sheet takes them (docs/assistant-and-mcp.md): each transcript written
 * whole to a file in [folder], in the cache — a share's text has a size limit, a file has none. Only
 * the latest stays.
 */
internal class AssistantShares(private val folder: File) {
    /** [text] as the one file in [folder], named by [title]. */
    fun write(title: String, text: String): File {
        folder.deleteRecursively()
        folder.mkdirs()
        return File(folder, AssistantExports.fileName(title)).apply { writeText(text) }
    }
}
