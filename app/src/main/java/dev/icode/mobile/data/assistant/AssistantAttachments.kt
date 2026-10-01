package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.assistant.tools.bounded
import dev.icode.mobile.domain.AssistantAttachment
import java.io.File

/** Files sent with a message (§12 R6): the workspace's own text files, each bounded, [dev.icode.mobile.domain.AssistantRules.MAX_ATTACHMENTS] at most. */
internal object AssistantAttachments {
    /** Characters of a file the model reads; a longer one is sent as its start and its end. */
    const val MAX_CHARACTERS = 64_000

    /**
     * [location] in [workspace], its text as [read] gives it — the open tab's, or the file's. Refused
     * outside the workspace, for a folder, and for a binary file, in the words the page shows.
     */
    fun of(workspace: AssistantWorkspace, location: String, read: (File) -> String): AssistantAttachment {
        // Through the tools' own check: no `..`, link or other location leads out of the workspace.
        val file = runCatching { workspace.resolve(workspace.relative(File(location))) }
            .getOrElse { throw AssistantTurnException("${File(location).name} is not in this workspace") }
        val path = workspace.relative(file)
        if (!file.isFile) throw AssistantTurnException("$path is not a file")
        val text = runCatching { read(file) }.getOrElse { throw AssistantTurnException("Unable to read $path") }
        if ('\u0000' in text) throw AssistantTurnException("$path is a binary file")
        return AssistantAttachment(path, bounded(text, MAX_CHARACTERS))
    }
}
