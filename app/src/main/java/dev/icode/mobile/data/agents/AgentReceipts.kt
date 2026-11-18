package dev.icode.mobile.data.agents

import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.domain.InstalledAgent
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

/**
 * What says an agent is installed (docs/assistant-and-mcp.md): its receipt in the runtime's home,
 * `~/.icode/agents/<id>.json` — its version, how it starts, the space it takes — written whole, then
 * put in place. Read by the page and by the daily update check, which runs with no screen open.
 */
internal class AgentReceipts(private val home: File) {
    private val folder = File(home, ".icode/agents")

    /** Each installed agent whose program is in place, by ID. */
    fun installed(): Map<String, InstalledAgent> = folder.listFiles { file -> file.name.endsWith(JSON) }.orEmpty()
        .mapNotNull { file -> runCatching { file.name.removeSuffix(JSON) to decode(JSONObject(file.readText())) }.getOrNull() }
        .filter { (_, agent) -> agent.command.firstOrNull()?.let(::host)?.exists() == true }
        .toMap()

    fun record(id: String, agent: InstalledAgent) {
        folder.mkdirs()
        val json = JSONObject()
            .put("version", agent.version)
            .put("command", JSONArray(agent.command))
            .put("env", JSONObject(agent.env))
            .put("bytes", agent.bytes)
        val written = File(folder, "$id$JSON.tmp").apply { writeText(json.toString()) }
        Files.move(written.toPath(), File(folder, id + JSON).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun forget(id: String) {
        File(folder, id + JSON).delete()
    }

    /** [file], in the home, as the runtime sees it. */
    fun guest(file: File): String = RuntimeLayout.GUEST_HOME + "/" + file.relativeTo(home).invariantSeparatorsPath

    private fun host(guest: String): File = File(home, guest.removePrefix(RuntimeLayout.GUEST_HOME + "/"))

    private fun decode(json: JSONObject): InstalledAgent {
        val command = json.getJSONArray("command")
        val env = json.getJSONObject("env")
        return InstalledAgent(
            json.getString("version"),
            List(command.length(), command::getString),
            env.keys().asSequence().associateWith(env::getString),
            json.getLong("bytes"),
        )
    }

    private companion object {
        const val JSON = ".json"
    }
}
