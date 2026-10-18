package dev.icode.mobile.data.runtime

import dev.icode.mobile.domain.RuntimeTool
import dev.icode.mobile.domain.ToolSetupProgress
import java.io.File
import java.io.IOException

/**
 * What a runtime tool installs after its apt packages (docs/android-toolchain-plan.md §4.1): pinned
 * downloads, and steps of its own. [RuntimePackageInstaller] runs it once apt is done, removes it
 * before apt removes the packages, and keeps its receipt in the guest home — a setup never writes
 * one itself.
 */
internal interface RuntimeToolSetup {
    /** [dev.icode.mobile.domain.RuntimeTool.setup]. */
    val id: String

    /**
     * What it installs, by version and checksum: its receipt. A build that pins another version
     * reads as not installed, and installing brings it to this one.
     */
    val receipt: String

    /** Each file it downloads, by name, with its size as pinned. */
    fun measure(): Map<String, Long>

    /**
     * Installs, saying each step as it goes and the output of what it runs. A step that fails throws,
     * in words the page can show; a cancel stops it only where [ToolSetupProgress.cancellable] says so.
     */
    suspend fun install(onProgress: (ToolSetupProgress) -> Unit, onLine: (String) -> Unit)

    /**
     * Writes what it keeps outside the guest home: the system's, which Repair rebuilds while the
     * home — and the receipt — stay. Run on every install of its tool, after [install] or in its
     * place when the receipt already matches.
     */
    fun configure()

    /** Removes what [install] and [configure] added. */
    suspend fun remove()
}

/**
 * The setups runtime tools have, by id, and their receipts (`~/.icode/runtime-tools/<id>`): a setup
 * is installed while its receipt says what this build pins.
 */
internal class ToolSetups(setups: List<RuntimeToolSetup>, private val receipts: File) {
    private val byId = setups.associateBy { it.id }

    /** [id]'s setup; a catalog naming one that is not registered is this build's mistake. */
    fun of(id: String): RuntimeToolSetup = byId[id] ?: throw IllegalStateException("No runtime tool setup is registered as $id")

    /** Each setup whose receipt matches, by id. */
    fun installed(): Set<String> = byId.values.filter(::matches).mapTo(hashSetOf()) { it.id }

    fun matches(setup: RuntimeToolSetup): Boolean = runCatching { File(receipts, setup.id).readText() == setup.receipt }.getOrDefault(false)

    /** What [tool]'s setup downloads, by file, unless its receipt matches. */
    fun download(tool: RuntimeTool): Map<String, Long> = tool.setup?.let(::of)?.takeUnless(::matches)?.measure().orEmpty()

    /** [setup]'s receipt, once it is installed: written whole, then put in place. */
    fun record(setup: RuntimeToolSetup) {
        receipts.mkdirs()
        val written = File(receipts, "${setup.id}.tmp").apply { writeText(setup.receipt) }
        if (!written.renameTo(File(receipts, setup.id))) throw IOException("Could not record the ${setup.id} setup")
    }

    fun forget(setup: RuntimeToolSetup) {
        File(receipts, setup.id).delete()
    }
}
