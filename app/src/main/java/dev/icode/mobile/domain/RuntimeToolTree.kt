package dev.icode.mobile.domain

// The runtime tools as their page lists them (docs/runtime-tools-plan.md §8.7): a tree, as Android
// Studio's SDK Manager shows its tools — a tool's add-ons beneath it, a family's versions beneath it.

/** A tool with versions installed side by side, each its own row beneath the family's: the NDK. */
data class RuntimeToolFamily(val id: String, val name: String, val summary: String)

/** One of a [family]'s versions: NDK (Side by side) 28.2.13676358. */
data class RuntimeToolVersion(val family: RuntimeToolFamily, val version: String)

/** A group of the page: iCode's own tools, or the rest. */
data class RuntimeToolSection(val title: String, val nodes: List<RuntimeToolNode>)

/** A row of the tree, saying only what no row above it says, and the rows beneath it. */
sealed interface RuntimeToolNode {
    /** Stable: what the page keeps the row open by. */
    val id: String
    /** What the row shows: a tool's name, or within its family its version. */
    val title: String
    /** What it is called alone, as a screen reader says the row: "NDK (Side by side) 28.2.13676358". */
    val name: String
    val summary: String
    /** What in iCode needs it. */
    val usedBy: String?
    /** What it requires that no row above it names (docs/android-ndk-plan.md §3.1): ticked with it. */
    val needs: List<RuntimeTool>
    val children: List<RuntimeToolNode>

    data class Tool(
        val tool: RuntimeTool,
        override val usedBy: String?,
        override val needs: List<RuntimeTool>,
        override val children: List<RuntimeToolNode>,
    ) : RuntimeToolNode {
        override val id: String get() = tool.id
        override val title: String get() = tool.versionOf?.version ?: tool.name
        override val name: String get() = tool.name
        override val summary: String get() = tool.summary
    }

    /** A family's row: its versions are its children. */
    data class Family(
        val family: RuntimeToolFamily,
        override val usedBy: String?,
        override val needs: List<RuntimeTool>,
        override val children: List<Tool>,
    ) : RuntimeToolNode {
        override val id: String get() = family.id
        override val title: String get() = family.name
        override val name: String get() = family.name
        override val summary: String get() = family.summary
    }
}

/** A row's tick: a family's is [Partly] while some of its versions are ticked. */
enum class RuntimeToolTick { Off, Partly, On }

object RuntimeToolTree {
    /** [catalog] as the page lists it: by section, each tool beneath the one it is [RuntimeTool.within], in the catalog's order. */
    fun sections(catalog: List<RuntimeTool> = RuntimeTools.all): List<RuntimeToolSection> {
        val roots = Builder(catalog).nodes(within = null, above = Above())
        return listOf("Used by iCode" to true, "More tools" to false)
            .map { (title, own) -> RuntimeToolSection(title, roots.filter { (it.usedBy != null) == own }) }
            .filter { it.nodes.isNotEmpty() }
    }

    fun tick(node: RuntimeToolNode, choice: Set<String>): RuntimeToolTick = when (node) {
        is RuntimeToolNode.Tool -> if (node.tool.id in choice) RuntimeToolTick.On else RuntimeToolTick.Off
        is RuntimeToolNode.Family -> when (node.children.count { it.tool.id in choice }) {
            0 -> RuntimeToolTick.Off
            node.children.size -> RuntimeToolTick.On
            else -> RuntimeToolTick.Partly
        }
    }

    /**
     * [choice] with [node]'s row tapped, kept whole by [RuntimeToolRules.toggle]. A family's row
     * unticks every version while any is ticked, and otherwise ticks its first, the newest, as
     * ticking Android Studio's NDK (Side by side) gets the latest.
     */
    fun toggle(choice: Set<String>, node: RuntimeToolNode, catalog: List<RuntimeTool> = RuntimeTools.all): Set<String> = when (node) {
        is RuntimeToolNode.Tool -> RuntimeToolRules.toggle(choice, node.tool.id, catalog)
        is RuntimeToolNode.Family -> if (tick(node, choice) == RuntimeToolTick.Off) {
            RuntimeToolRules.toggle(choice, node.children.first().tool.id, catalog)
        } else {
            node.children.map { it.tool.id }.filter { it in choice }.fold(choice) { left, id -> RuntimeToolRules.toggle(left, id, catalog) }
        }
    }

    /**
     * The rows the page opens with, by id: each with a ticked tool beneath it, so nothing ticked starts
     * hidden. The rest open and close at the user's tap.
     */
    fun opened(choice: Set<String>, catalog: List<RuntimeTool> = RuntimeTools.all): Set<String> {
        fun open(node: RuntimeToolNode): List<String> =
            listOfNotNull(node.id.takeIf { tickedBeneath(node, choice) > 0 }) + node.children.flatMap(::open)
        return sections(catalog).flatMap { it.nodes }.flatMapTo(linkedSetOf(), ::open)
    }

    /** How many tools beneath [node], however deep, are ticked: what its row says while it is closed. */
    fun tickedBeneath(node: RuntimeToolNode, choice: Set<String>): Int = beneath(node).count { it.id in choice }

    private fun beneath(node: RuntimeToolNode): List<RuntimeTool> =
        node.children.flatMap { child -> listOfNotNull((child as? RuntimeToolNode.Tool)?.tool) + beneath(child) }

    /** What the rows above a row say: the tools they are and need, by id, and what in iCode uses them. */
    private data class Above(val named: Set<String> = emptySet(), val usedBy: String? = null) {
        fun with(node: RuntimeToolNode, ids: List<String>) = Above(named + ids + node.needs.map { it.id }, node.usedBy ?: usedBy)
    }

    private class Builder(private val catalog: List<RuntimeTool>) {
        private val byId = catalog.associateBy { it.id }

        fun nodes(within: String?, above: Above): List<RuntimeToolNode> {
            val beneath = catalog.filter { it.within == within }
            return beneath.mapNotNull { tool ->
                val family = tool.versionOf?.family ?: return@mapNotNull node(tool, above)
                if (beneath.first { it.versionOf?.family == family } != tool) return@mapNotNull null
                val versions = beneath.filter { it.versionOf?.family == family }
                val ids = versions.map { it.id }
                val row = RuntimeToolNode.Family(
                    family,
                    usedBy(versions.map { it.usedBy }.distinct().single(), above),
                    needs(versions.flatMap { it.requires }, above.named + ids),
                    emptyList(),
                )
                row.copy(children = versions.map { node(it, above.with(row, ids)) })
            }
        }

        private fun node(tool: RuntimeTool, above: Above): RuntimeToolNode.Tool {
            val row = RuntimeToolNode.Tool(tool, usedBy(tool.usedBy, above), needs(tool.requires, above.named), emptyList())
            return row.copy(children = nodes(tool.id, above.with(row, listOf(tool.id))))
        }

        private fun usedBy(usedBy: String?, above: Above) = usedBy?.takeIf { it != above.usedBy }

        private fun needs(requires: List<String>, named: Set<String>): List<RuntimeTool> =
            requires.distinct().filterNot { it in named }.map(byId::getValue)
    }
}
