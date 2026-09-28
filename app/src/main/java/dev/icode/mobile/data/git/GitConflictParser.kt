package dev.icode.mobile.data.git

import dev.icode.mobile.domain.GitConflictChoice
import dev.icode.mobile.domain.GitConflictFile
import dev.icode.mobile.domain.GitConflictSection

/**
 * Reads Git's merge markers out of a conflicted file.
 *
 * The file on disk is the authority: Git wrote these markers, and resolving
 * means rewriting the same file. Everything outside a marker block is carried
 * through untouched, so resolving one conflict never disturbs the rest.
 */
internal object GitConflictParser {

    private const val CURRENT_MARKER = "<<<<<<<"
    private const val BASE_MARKER = "|||||||"
    private const val SEPARATOR_MARKER = "======="
    private const val INCOMING_MARKER = ">>>>>>>"

    fun parse(path: String, text: String): GitConflictFile {
        val lines = text.split("\n")
        val sections = mutableListOf<GitConflictSection>()

        var index = 0
        while (index < lines.size) {
            if (!lines[index].startsWith(CURRENT_MARKER)) {
                index += 1
                continue
            }
            val start = index
            val currentLabel = lines[index].removePrefix(CURRENT_MARKER).trim()
            index += 1

            val current = mutableListOf<String>()
            // diff3 style adds a ||||||| base block between the two sides; it is
            // context for a human, not a side that can be chosen.
            var inBase = false
            while (index < lines.size &&
                !lines[index].startsWith(SEPARATOR_MARKER) &&
                !lines[index].startsWith(INCOMING_MARKER)
            ) {
                when {
                    lines[index].startsWith(BASE_MARKER) -> inBase = true
                    !inBase -> current += lines[index]
                }
                index += 1
            }
            if (index >= lines.size || !lines[index].startsWith(SEPARATOR_MARKER)) {
                // Unterminated block: not a conflict this parser can resolve.
                break
            }
            index += 1

            val incoming = mutableListOf<String>()
            while (index < lines.size && !lines[index].startsWith(INCOMING_MARKER)) {
                incoming += lines[index]
                index += 1
            }
            if (index >= lines.size) break
            val incomingLabel = lines[index].removePrefix(INCOMING_MARKER).trim()

            sections += GitConflictSection(
                currentLabel = currentLabel.ifEmpty { "Current" },
                current = current.toList(),
                incomingLabel = incomingLabel.ifEmpty { "Incoming" },
                incoming = incoming.toList(),
                startLine = start,
                endLine = index,
            )
            index += 1
        }
        return GitConflictFile(path = path, lines = lines, sections = sections)
    }

    /**
     * Rebuilds the file with [section] resolved by [choice]; every other
     * conflict keeps its markers for its own decision.
     *
     * [GitConflictChoice.Both] keeps the current side followed by the incoming
     * side, which is what "accept both" means in every editor that offers it.
     */
    fun resolve(file: GitConflictFile, section: GitConflictSection, choice: GitConflictChoice): String {
        val resolved = mutableListOf<String>()
        var index = 0
        while (index < file.lines.size) {
            if (index != section.startLine) {
                resolved += file.lines[index]
                index += 1
            } else {
                resolved += when (choice) {
                    GitConflictChoice.Current -> section.current
                    GitConflictChoice.Incoming -> section.incoming
                    GitConflictChoice.Both -> section.current + section.incoming
                }
                // Skip past the closing marker.
                index = section.endLine + 1
            }
        }
        return resolved.joinToString("\n")
    }
}
