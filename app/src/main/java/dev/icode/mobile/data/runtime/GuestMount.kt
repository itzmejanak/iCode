package dev.icode.mobile.data.runtime

/**
 * What one guest command sees of the phone's storage: [root] bound at
 * [RuntimeLayout.guestWorkspace], the command running in [workingDirectory]
 * inside it, and any [samePath] folders bound at their own absolute path.
 */
internal data class GuestMount(
    val root: String,
    val workingDirectory: String = root,
    /** Folders a program finds by absolute host path, such as a linked worktree's main `.git`. */
    val samePath: List<String> = emptyList(),
) {
    init {
        require(workingDirectory == root || workingDirectory.startsWith("$root/")) {
            "The working directory must be inside the bound folder"
        }
    }

    /** Where [hostPath], at or under [root], is inside the guest. */
    fun guestPathOf(hostPath: String): String =
        RuntimeLayout.guestWorkspace(root) + hostPath.removePrefix(root)
}
