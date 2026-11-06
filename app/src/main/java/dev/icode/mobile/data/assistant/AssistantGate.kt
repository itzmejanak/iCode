package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.domain.AssistantAnswer
import dev.icode.mobile.domain.AssistantAsk
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantPermission
import dev.icode.mobile.domain.AssistantPermissions
import dev.icode.mobile.domain.AssistantReach
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.LineChanges
import org.json.JSONObject

/**
 * The gate every call passes (docs/assistant-permissions-plan.md §5): what it reaches outside the
 * chat's place, less what the chat opened, and the chat's mode decide whether it asks, once. An act in
 * an app on the phone asks about the app alone, as a folder outside does: allowed there for the chat,
 * it runs (docs/phone-agent-plan.md L6).
 */
internal class AssistantGate(
    private val tool: AssistantTool,
    private val place: AssistantWorkspace,
    private val chats: AssistantChats,
    private val page: AssistantPage,
    private val questions: AssistantQuestions,
    /** The chat the turn is in: its place and its session. */
    private val workspace: String,
    private val session: String,
    /** The chat's mode now: the setting, raised by its own answers. */
    private val permission: () -> AssistantPermission,
    /** The call running now: what an approval is about. */
    private val calling: () -> AssistantCall?,
    /** A file change's lines, counted by the call that made them. */
    private val counted: (AssistantCall, LineChanges) -> Unit,
) : AssistantTool by tool {
    override suspend fun run(arguments: JSONObject): String {
        val opened = chats.roots(workspace, session)
        val outside = place.outside(tool.reach(arguments)).filterNot { path -> opened.any { AssistantPermissions.within(path, it) } }
        val actor = tool as? AssistantPlaceActor
        val elsewhere = actor?.place(arguments)?.takeIf { it.root !in opened }
        val asks = AssistantPermissions.ask(permission(), if (actor != null) AssistantToolKind.Read else tool.kind, outside.isNotEmpty() || elsewhere != null)
        if (asks != null) {
            val call = calling() ?: throw AssistantToolException("Nothing to ask about")
            val reach = when {
                asks != AssistantAsk.Outside -> null
                elsewhere != null -> AssistantReach(listOf(elsewhere.name), listOf(elsewhere.root), place = elsewhere)
                else -> AssistantReach(outside, outside.mapNotNull(place::rootOf).distinct())
            }
            when (questions.approve(call, tool.detail(arguments), reach)) {
                AssistantAnswer.Allow -> Unit
                AssistantAnswer.AllowInChat -> {
                    if (reach != null) chats.open(workspace, session, reach.roots) else chats.allowInWorkspace(workspace, session)
                    page.refresh(workspace, session)
                }
                AssistantAnswer.Deny -> throw AssistantToolException(AssistantPermissions.denied(asks, outside, elsewhere))
            }
        }
        return tool.run(arguments).also {
            (tool as? AssistantFileChanger)?.lastChanges?.let { changes -> calling()?.let { counted(it, changes) } }
        }
    }
}
