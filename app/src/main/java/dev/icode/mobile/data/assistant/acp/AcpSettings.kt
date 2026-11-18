package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.model.AvailableCommand
import com.agentclientprotocol.model.AvailableCommandInput
import com.agentclientprotocol.model.PlanEntry
import com.agentclientprotocol.model.PlanEntryStatus
import com.agentclientprotocol.model.SessionConfigId
import com.agentclientprotocol.model.SessionConfigOption
import com.agentclientprotocol.model.SessionConfigOptionValue
import com.agentclientprotocol.model.SessionConfigSelectOptions
import com.agentclientprotocol.model.SessionModeId
import dev.icode.mobile.domain.AgentCommand
import dev.icode.mobile.domain.AgentPlanStatus
import dev.icode.mobile.domain.AgentPlanStep
import dev.icode.mobile.domain.AgentSetting
import dev.icode.mobile.domain.AgentSettings
import dev.icode.mobile.domain.AgentValue

/**
 * An agent session's settings as the chat shows them (docs/assistant-and-mcp.md): its config options,
 * and its modes as one more setting where it offers them as modes only; its commands and to-do list.
 */
@OptIn(UnstableApi::class)
internal object AcpSettings {
    /** [session]'s settings now: its options, then its modes where no option is its mode. */
    fun of(session: ClientSession): List<AgentSetting> {
        val options = if (session.configOptionsSupported) session.configOptions.value.mapNotNull(::settingOf) else emptyList()
        // The SDK refuses to read what the agent did not provide: each is read only once it says it did.
        val modes = if (session.modesSupported) session.availableModes.takeIf { it.isNotEmpty() && options.none { option -> option.category == AgentSettings.MODE } } else null
        val mode = modes?.let { available ->
            AgentSetting(
                AgentSettings.MODE, "Mode", AgentSettings.MODE, (session.operations as? ChatOperations)?.mode ?: session.currentMode.value.value,
                available.map { AgentValue(it.id.value, it.name, it.description) },
            )
        }
        return listOfNotNull(mode) + options
    }

    /** [session]'s [id] set to [value]: an option through the options, the modes through the modes. */
    suspend fun set(session: ClientSession, id: String, value: String) {
        val option = if (session.configOptionsSupported) session.configOptions.value.firstOrNull { it.id.value == id } else null
        when {
            option is SessionConfigOption.BooleanOption -> session.setConfigOption(SessionConfigId(id), SessionConfigOptionValue.of(value.toBoolean()))
            option != null -> session.setConfigOption(SessionConfigId(id), SessionConfigOptionValue.of(value))
            id == AgentSettings.MODE && session.modesSupported -> {
                session.setMode(SessionModeId(value))
                (session.operations as? ChatOperations)?.mode = value
            }
        }
    }

    /** [session] of [agent] in its asking values (docs/roadmap.md), each it offers and is not in already. */
    suspend fun startAsking(agent: String, session: ClientSession) {
        val now = of(session).associateBy { it.id }
        AgentSettings.ASKING[agent].orEmpty().forEach { (id, value) ->
            val setting = now[id] ?: return@forEach
            if (setting.current != value && (setting.switch || setting.values.any { it.id == value })) set(session, id, value)
        }
    }

    fun commandsOf(commands: List<AvailableCommand>): List<AgentCommand> =
        commands.map { AgentCommand(it.name, it.description, (it.input as? AvailableCommandInput.Unstructured)?.hint) }

    fun planOf(entries: List<PlanEntry>): List<AgentPlanStep> = entries.map { entry ->
        val status = when (entry.status) {
            PlanEntryStatus.COMPLETED -> AgentPlanStatus.Done
            PlanEntryStatus.IN_PROGRESS -> AgentPlanStatus.Working
            else -> AgentPlanStatus.Pending
        }
        AgentPlanStep(entry.content, status)
    }

    private fun settingOf(option: SessionConfigOption): AgentSetting? = when (option) {
        is SessionConfigOption.Select -> AgentSetting(
            option.id.value, option.name, option.category?.value, option.currentValue.value,
            when (val values = option.options) {
                is SessionConfigSelectOptions.Flat -> values.options
                is SessionConfigSelectOptions.Grouped -> values.groups.flatMap { it.options }
            }.map { AgentValue(it.value.value, it.name, it.description) },
        )
        is SessionConfigOption.BooleanOption -> AgentSetting(option.id.value, option.name, option.category?.value, option.currentValue.toString())
        else -> null
    }
}
