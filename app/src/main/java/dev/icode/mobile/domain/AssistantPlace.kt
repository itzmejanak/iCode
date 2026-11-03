package dev.icode.mobile.domain

/** A chat belongs to a workspace, by its location: "app" is the workspace's own name. */
object AssistantPlace {
    fun name(place: String): String = place.trimEnd('/').substringAfterLast('/')
}
