package dev.icode.mobile.data.assistant.acp

import android.content.Context
import dev.icode.mobile.BuildConfig
import dev.icode.mobile.data.agents.AgentReceipts
import dev.icode.mobile.data.assistant.AgentChatsOf
import dev.icode.mobile.data.assistant.tools.AssistantToolbox
import dev.icode.mobile.data.runtime.GuestContext
import dev.icode.mobile.data.runtime.GuestStdio
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.workspace.ICodeStateHolder
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock

/** The guest folder the agents' logs go in: what each wrote on stderr since it last started. */
private const val LOGS = "/tmp/icode-agents"

/**
 * The agents in the chats as iCode runs them (docs/assistant-and-mcp.md): in the runtime, with the login's
 * variables, their receipts' commands and their kept keys, each session given iCode's tools; [save] saves an
 * open file, under the workspace's lock.
 */
internal fun agentChatsOf(
    context: Context,
    icode: ICodeStateHolder,
    scope: CoroutineScope,
    toolbox: AssistantToolbox,
    save: suspend (String) -> Boolean,
): AgentChatsOf {
    val layout = toolbox.execution.layout
    val receipts = AgentReceipts(layout.home)
    val preferences = AgentPreferences(context)
    val saveOpen: suspend () -> Unit = {
        icode.mutations.withLock { icode.current.openDocuments.filter { it.dirty }.forEach { save(it.location) } }
    }
    val releases = icode.snapshot.map { snapshot -> snapshot.agents.installed.mapValues { it.value.version } }.distinctUntilChanged()
    return { page, chats, placeOf, ask, summarise, offered ->
        val bridge = File(layout.home, AgentToolServer.BRIDGE_PATH) to "${RuntimeLayout.GUEST_HOME}/${AgentToolServer.BRIDGE_PATH}"
        val tools = AgentToolServer(offered, bridge, BuildConfig.VERSION_NAME)
        AgentChats(
            scope, page, chats, preferences, { icode.current.agents.catalogue }, receipts::installed, placeOf, toolbox::runInTab, saveOpen,
            icode.snapshot.map { it.assistant }, ask, releases, summarise, tools,
        ) { changed, stopped, records, asked ->
            AcpAgents(
                scope, GuestStdio(layout, GuestContext.Command, LOGS, tag = "agent"),
                login = { toolbox.execution.environment.of(GuestContext.Command).variables },
                installed = receipts::installed, keys = preferences::keys, version = BuildConfig.VERSION_NAME,
                changed = changed, stopped = stopped, records = records, servers = { agent, operations -> tools.serversFor(agent.capabilities, operations) }, asked = asked,
            )
        }
    }
}
