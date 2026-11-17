package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.AuthMethod
import com.agentclientprotocol.model.AvailableCommand
import com.agentclientprotocol.model.AvailableCommandInput
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.EmbeddedResourceResource
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionKind
import com.agentclientprotocol.model.PlanEntry
import com.agentclientprotocol.model.PlanEntryPriority
import com.agentclientprotocol.model.PlanEntryStatus
import com.agentclientprotocol.model.SessionConfigOption
import com.agentclientprotocol.model.SessionConfigSelectOptions
import com.agentclientprotocol.model.SessionModeState
import com.agentclientprotocol.model.SessionModelState
import com.agentclientprotocol.model.ToolCallContent
import com.agentclientprotocol.model.ToolCallStatus
import com.agentclientprotocol.model.ToolKind
import dev.icode.mobile.data.assistant.tools.bounded
import dev.icode.mobile.domain.AgentCommand
import dev.icode.mobile.domain.AgentOption
import dev.icode.mobile.domain.AgentPlanItem
import dev.icode.mobile.domain.AgentRules
import dev.icode.mobile.domain.AgentSignIn
import dev.icode.mobile.domain.AssistantApprovalChoice
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.LineChanges
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A tool call as it stands: what the agent said of it so far, merged update by update (ACP `tool_call`,
 * `tool_call_update`). Its content replaces what was before when an update carries some, as ACP says.
 */
internal class AcpToolCall(val id: String) {
    var title: String = ""
    var kind: ToolKind? = null
    var status: ToolCallStatus? = null
    var content: List<ToolCallContent> = emptyList()
    var paths: List<String> = emptyList()
    var input: JsonElement? = null
    var output: JsonElement? = null

    /** Its row in the chat, once recorded: what it is, fixed from then on. */
    var recorded: AssistantCall? = null
    var answered = false

    val done: Boolean get() = status == ToolCallStatus.COMPLETED || status == ToolCallStatus.FAILED

    fun merge(title: String?, kind: ToolKind?, status: ToolCallStatus?, content: List<ToolCallContent>?, paths: List<String>?, input: JsonElement?, output: JsonElement?) {
        title?.takeIf { it.isNotBlank() }?.let { this.title = it }
        kind?.let { this.kind = it }
        status?.let { this.status = it }
        content?.let { this.content = it }
        paths?.let { this.paths = it }
        input?.let { this.input = it }
        output?.let { this.output = it }
    }
}

/**
 * The one mapping from what an agent says over ACP to iCode's chat (docs/assistant-and-mcp.md): calls,
 * results, settings, commands, plans, sign-ins and approval options. [cwd] is where the chat works in
 * the runtime: a path under it is shown relative to it.
 */
@OptIn(UnstableApi::class)
internal object AcpMapping {
    /** [call]'s row: its tool by its kind, its title, and its file or command where it has one. */
    fun call(call: AcpToolCall, cwd: String): AssistantCall {
        val (tool, kind) = AgentRules.toolOf(kindName(call.kind))
        val file = call.paths.firstOrNull()?.let { relative(it, cwd) }
            ?: call.content.firstNotNullOfOrNull { (it as? ToolCallContent.Diff)?.path }?.let { relative(it, cwd) }
        return AssistantCall(
            id = call.id,
            tool = tool.id,
            arguments = call.input?.toString() ?: "{}",
            summary = call.title.ifBlank { tool.title },
            kind = kind,
            file = file.takeIf { kind != AssistantToolKind.Command },
            command = if (kind == AssistantToolKind.Command) commandOf(call.input) ?: call.title else null,
        )
    }

    /** [call]'s result: its text and its diffs, each diff's lines counted; failed when the agent says so. */
    fun result(call: AcpToolCall, recorded: AssistantCall): AssistantResult {
        val texts = call.content.mapNotNull { (it as? ToolCallContent.Content)?.content?.let(::text) }.filter(String::isNotBlank)
        val diffs = call.content.filterIsInstance<ToolCallContent.Diff>()
        val changes = diffs.map { LineChanges.between(it.oldText, it.newText) }
            .takeIf { it.isNotEmpty() }
            ?.let { all -> LineChanges(all.sumOf { it.added }, all.sumOf { it.removed }) }
        val said = texts.ifEmpty { listOfNotNull(call.output?.let(::printable)) }
        val output = (said + diffs.map { "${it.path}: ${LineChanges.between(it.oldText, it.newText).label}" }).joinToString("\n\n")
        return AssistantResult(
            recorded.id, recorded.tool, bounded(output.ifBlank { if (call.status == ToolCallStatus.FAILED) "Failed" else "Done" }, MAX_OUTPUT),
            failed = call.status == ToolCallStatus.FAILED, changes = changes,
        )
    }

    /** What a content block says as text: its text, a resource's text, or a link's name. */
    fun text(block: ContentBlock): String = when (block) {
        is ContentBlock.Text -> block.text
        is ContentBlock.ResourceLink -> block.title ?: block.name
        is ContentBlock.Resource -> (block.resource as? EmbeddedResourceResource.TextResourceContents)?.text ?: block.resource.let { "" }
        is ContentBlock.Image -> ""
        is ContentBlock.Audio -> ""
    }

    /** The agent's settings: its config options, else its modes and models as ACP had them before. */
    fun options(config: List<SessionConfigOption>?, modes: SessionModeState?, models: SessionModelState?): List<AgentOption> {
        config?.let { return it.map(::option) }
        return listOfNotNull(
            modes?.let {
                AgentOption.Select(MODE, "Mode", null, "mode", it.currentModeId.value, it.availableModes.map { mode -> AgentOption.Select.Choice(mode.id.value, mode.name, mode.description) })
            },
            models?.let {
                AgentOption.Select(MODEL, "Model", null, "model", it.currentModelId.value, it.availableModels.map { model -> AgentOption.Select.Choice(model.modelId.value, model.name, model.description) })
            },
        )
    }

    fun option(option: SessionConfigOption): AgentOption = when (option) {
        is SessionConfigOption.Select -> AgentOption.Select(
            option.id.value, option.name, option.description, option.category?.value, option.currentValue.value,
            when (val choices = option.options) {
                is SessionConfigSelectOptions.Flat -> choices.options.map { AgentOption.Select.Choice(it.value.value, it.name, it.description) }
                is SessionConfigSelectOptions.Grouped -> choices.groups.flatMap { group ->
                    group.options.map { AgentOption.Select.Choice(it.value.value, it.name, it.description, group.name ?: group.group.value) }
                }
            },
        )
        is SessionConfigOption.BooleanOption -> AgentOption.Toggle(option.id.value, option.name, option.description, option.category?.value, option.currentValue)
    }

    fun commands(commands: List<AvailableCommand>): List<AgentCommand> =
        commands.map { AgentCommand(it.name, it.description, (it.input as? AvailableCommandInput.Unstructured)?.hint) }

    fun plan(entries: List<PlanEntry>): List<AgentPlanItem> = entries.map { entry ->
        AgentPlanItem(
            entry.content,
            when (entry.status) {
                PlanEntryStatus.PENDING -> AgentPlanItem.Status.Pending
                PlanEntryStatus.IN_PROGRESS -> AgentPlanItem.Status.InProgress
                PlanEntryStatus.COMPLETED -> AgentPlanItem.Status.Completed
            },
            when (entry.priority) {
                PlanEntryPriority.HIGH -> AgentPlanItem.Priority.High
                PlanEntryPriority.MEDIUM -> AgentPlanItem.Priority.Medium
                PlanEntryPriority.LOW -> AgentPlanItem.Priority.Low
            },
        )
    }

    /** How the agent signs in, each as iCode can offer it: in the agent, in a terminal, or with keys. */
    fun signIn(methods: List<AuthMethod>): List<AgentSignIn> = methods.mapNotNull { method ->
        when (method) {
            is AuthMethod.AgentAuth -> AgentSignIn(method.id.value, method.name, method.description, AgentSignIn.Kind.Agent)
            is AuthMethod.TerminalAuth -> AgentSignIn(method.id.value, method.name, method.description, AgentSignIn.Kind.Terminal)
            is AuthMethod.EnvVarAuth -> AgentSignIn(method.id.value, method.name, method.description, AgentSignIn.Kind.Keys, method.vars.map { it.name }, method.link)
            is AuthMethod.UnknownAuthMethod -> null
        }
    }

    fun choices(options: List<PermissionOption>): List<AssistantApprovalChoice> = options.map { option ->
        AssistantApprovalChoice(
            option.optionId.value, option.name,
            when (option.kind) {
                PermissionOptionKind.ALLOW_ONCE -> AssistantApprovalChoice.Kind.AllowOnce
                PermissionOptionKind.ALLOW_ALWAYS -> AssistantApprovalChoice.Kind.AllowAlways
                PermissionOptionKind.REJECT_ONCE -> AssistantApprovalChoice.Kind.RejectOnce
                PermissionOptionKind.REJECT_ALWAYS -> AssistantApprovalChoice.Kind.RejectAlways
            },
        )
    }

    /** The name ACP gives [kind]: "read", "edit", "execute"… */
    fun kindName(kind: ToolKind?): String? = when (kind) {
        null -> null
        ToolKind.READ -> "read"
        ToolKind.EDIT -> "edit"
        ToolKind.DELETE -> "delete"
        ToolKind.MOVE -> "move"
        ToolKind.SEARCH -> "search"
        ToolKind.EXECUTE -> "execute"
        ToolKind.THINK -> "think"
        ToolKind.FETCH -> "fetch"
        ToolKind.SWITCH_MODE -> "switch_mode"
        ToolKind.OTHER -> "other"
    }

    /** [path] relative to [cwd] when it is inside it. */
    fun relative(path: String, cwd: String): String = when {
        path == cwd -> "."
        path.startsWith("$cwd/") -> path.removePrefix("$cwd/")
        else -> path
    }

    /** A command's text in its raw input: `command` as a string, or its words joined. */
    private fun commandOf(input: JsonElement?): String? = when (val command = (input as? JsonObject)?.get("command")) {
        is JsonPrimitive -> command.content.takeIf { command.isString }
        is JsonArray -> command.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString(" ").ifBlank { null }
        else -> null
    }

    /** A raw output as text: a string as written, anything else as JSON. */
    private fun printable(output: JsonElement): String? = when (output) {
        is JsonNull -> null
        is JsonPrimitive -> output.content
        else -> output.toString()
    }

    /** The option ID iCode gives an agent's modes when it offers them as modes, not as a config option. */
    const val MODE = "icode.mode"

    /** The same for its models. */
    const val MODEL = "icode.model"

    private const val MAX_OUTPUT = 48_000
}
