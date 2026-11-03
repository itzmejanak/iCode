package dev.icode.mobile.domain

/**
 * Where a chat works (docs/phone-agent-plan.md L1): a workspace, by its location, or the phone
 * itself. A chat's `workspace` everywhere is its place: a location, or [PHONE].
 */
object AssistantPlace {
    /** The phone's chats: they work from the runtime's home, `/root`, in no workspace. Never a location, which starts with `/`. */
    const val PHONE = "phone"

    /** "app", the workspace's own name; "Phone" for the phone's chats. */
    fun name(place: String): String = if (place == PHONE) "Phone" else place.trimEnd('/').substringAfterLast('/')

    /** What a chat's permissions are bounded by, as the page says it: its workspace, or "Home" (`/root`) for the phone's. */
    fun bound(place: String): String = if (place == PHONE) "Home" else name(place)
}
