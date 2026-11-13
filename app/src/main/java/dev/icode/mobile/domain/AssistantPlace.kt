package dev.icode.mobile.domain

/**
 * Where a chat works, as it is named: "app", its workspace's own name; "~" for a chat in no workspace,
 * which works in `$HOME`, as a shell names it (docs/home-plan.md §2).
 */
object AssistantPlace {
    fun name(workspace: String?): String = workspace?.trimEnd('/')?.substringAfterLast('/') ?: "~"
}
