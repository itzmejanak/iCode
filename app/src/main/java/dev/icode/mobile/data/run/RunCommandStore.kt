package dev.icode.mobile.data.run

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.runtime.RuntimeProcesses
import dev.icode.mobile.domain.RunConfiguration
import java.io.File
import java.io.IOException
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * The commands the user saved on the Run tab (docs/run-tab-plan.md §2.2), per workspace, in iCode's
 * own storage rather than the project: `{"<workspace>": [{"id", "name", "command"}]}`. A file that
 * cannot be read holds nothing; the next save writes it whole again.
 */
internal class RunCommandStore(private val file: File) {
    private val lock = Any()

    fun of(workspace: String): List<RunConfiguration.Command> = synchronized(lock) { read()[workspace].orEmpty() }

    /** Saves a new command when [id] is null, else changes that one; a blank name is the command's first word. */
    fun save(workspace: String, id: String?, name: String, command: String): RunConfiguration.Command = synchronized(lock) {
        val all = read()
        val saved = RunConfiguration.Command(
            id = id ?: UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { RuntimeProcesses.nameOf(command) },
            command = command.trim(),
        )
        val commands = all[workspace].orEmpty()
        val updated = if (commands.any { it.id == saved.id }) commands.map { if (it.id == saved.id) saved else it } else commands + saved
        write(all + (workspace to updated))
        saved
    }

    fun delete(workspace: String, id: String) = synchronized(lock) {
        val all = read()
        write(all + (workspace to all[workspace].orEmpty().filterNot { it.id == id }))
    }

    /** A workspace renamed: its commands follow it. */
    fun move(from: String, to: String) = synchronized(lock) {
        val all = read()
        val commands = all[from] ?: return@synchronized
        write(all - from + (to to commands))
    }

    /** A workspace deleted: its commands go. */
    fun forget(workspace: String) = synchronized(lock) {
        val all = read()
        if (workspace in all) write(all - workspace)
    }

    private fun read(): Map<String, List<RunConfiguration.Command>> {
        if (!file.isFile) return emptyMap()
        return runCatching {
            val json = JSONObject(file.readText())
            json.keys().asSequence().associateWith { workspace ->
                val list = json.getJSONArray(workspace)
                (0 until list.length()).map(list::getJSONObject).map {
                    RunConfiguration.Command(it.getString("id"), it.getString("name"), it.getString("command"))
                }
            }
        }.onFailure { BackendLog.w(TAG, "The saved run commands could not be read (${it.javaClass.simpleName})") }.getOrDefault(emptyMap())
    }

    private fun write(all: Map<String, List<RunConfiguration.Command>>) {
        val json = JSONObject()
        all.filterValues { it.isNotEmpty() }.forEach { (workspace, commands) ->
            json.put(workspace, JSONArray(commands.map { JSONObject().put("id", it.id).put("name", it.name).put("command", it.command) }))
        }
        file.parentFile?.mkdirs()
        val written = File(file.parentFile, "${file.name}.tmp").apply { writeText(json.toString()) }
        if (!written.renameTo(file)) throw IOException("Could not save the run commands")
    }

    private companion object {
        const val TAG = "RunCommandStore"
    }
}
