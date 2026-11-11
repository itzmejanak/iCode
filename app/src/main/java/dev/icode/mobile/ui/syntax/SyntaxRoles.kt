package dev.icode.mobile.ui.syntax

import dev.icode.mobile.designsystem.SyntaxRole
import java.util.concurrent.ConcurrentHashMap

/** How a token is drawn: its colour's [role], and its font style, which TextMate resolves apart from the colour. */
internal data class SyntaxStyle(
    val role: SyntaxRole,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
)

/**
 * A token's [SyntaxStyle] from its scopes (docs/syntax-highlighting-plan.md D5), by the same [rules] the
 * editor's TextMate theme is made of ([editorTheme]) and with tm4e's own precedence, so a read-only view
 * and the editor draw a token alike (A4). As tm4e's theme does: each scope, from the root to the token's
 * own, takes the rule whose last selector matches the most of its dotted segments, among those the
 * rule's parent selectors (`source.css variable`) match in its enclosing scopes; a scope no rule names
 * keeps what encloses it, and colour and font style are kept apart, so a link in bold text stays bold.
 * Any thread.
 */
internal class SyntaxRoles(rules: List<Pair<SyntaxRole, List<String>>>) {
    private class Rule(var depth: Int, val parents: List<String>?, var role: SyntaxRole?, var style: Int) {
        fun copy() = Rule(depth, parents, role, style)
    }

    private class Node(val main: Rule, val withParents: MutableList<Rule>) {
        val children = HashMap<String, Node>()
    }

    private class Selector(val leaf: String, val parents: List<String>?, val index: Int, val role: SyntaxRole)

    private val root = Node(Rule(0, null, null, NOT_SET), mutableListOf())

    /** Each scope's rules, most specific first, as tm4e caches them per scope name. */
    private val matches = ConcurrentHashMap<String, List<Rule>>()

    init {
        // As tm4e's Theme.parseTheme and resolveParsedThemeRules: rules sorted by selector, then inserted.
        rules.groupBy({ it.first }, { it.second }).entries.flatMapIndexed { index, (role, lists) ->
            lists.flatten().map { selector ->
                val segments = selector.trim().split(' ')
                Selector(segments.last(), segments.dropLast(1).reversed().ifEmpty { null }, index, role)
            }
        }.sortedWith(compareBy<Selector> { it.leaf }.then { a, b -> compareParents(a.parents, b.parents) }.thenBy { it.index })
            .forEach { insert(it) }
    }

    /** [scopes], root first as tm4e gives a token's, drawn: Plain where no rule names any of them. */
    fun style(scopes: List<String>): SyntaxStyle {
        val (role, style) = resolve(scopes)
        val set = style != NOT_SET
        return SyntaxStyle(
            role ?: SyntaxRole.Plain,
            bold = set && style and BOLD != 0,
            italic = set && style and ITALIC != 0,
            underline = set && style and UNDERLINE != 0,
            strikethrough = set && style and STRIKETHROUGH != 0,
        )
    }

    /** The role a rule gives [scopes], or null where no rule names any of them (drawn Plain). */
    fun named(scopes: List<String>): SyntaxRole? = resolve(scopes).first

    private fun resolve(scopes: List<String>): Pair<SyntaxRole?, Int> {
        var role: SyntaxRole? = null
        var style = NOT_SET
        scopes.indices.forEach { level ->
            val rule = match(scopes, level)
            if (rule.role != null) role = rule.role
            if (rule.style != NOT_SET) style = rule.style
        }
        return role to style
    }

    /** The rule for `scopes[level]` within the scopes enclosing it (tm4e's Theme.match). */
    private fun match(scopes: List<String>, level: Int): Rule =
        matches.getOrPut(scopes[level]) { candidates(scopes[level]) }.first { parentsMatch(scopes, level, it.parents) }

    private fun candidates(scope: String): List<Rule> {
        var node = root
        for (segment in scope.split('.')) node = node.children[segment] ?: break
        return (listOf(node.main) + node.withParents).sortedWith(::bySpecificity)
    }

    /** Whether [parents], nearest first, each name an enclosing scope of `scopes[level]`, in that order. */
    private fun parentsMatch(scopes: List<String>, level: Int, parents: List<String>?): Boolean {
        if (parents == null) return true
        var next = 0
        for (at in level - 1 downTo 0) {
            if (named(scopes[at], parents[next]) && ++next == parents.size) return true
        }
        return false
    }

    private fun insert(selector: Selector) {
        var node = root
        var depth = 0
        for (segment in selector.leaf.split('.')) {
            node = node.children.getOrPut(segment) { Node(node.main.copy(), node.withParents.mapTo(mutableListOf()) { it.copy() }) }
            depth++
        }
        val style = fontStyle(selector.role)
        if (selector.parents == null) {
            node.main.depth = maxOf(node.main.depth, depth)
            node.main.role = selector.role
            if (style != NOT_SET) node.main.style = style
            return
        }
        val same = node.withParents.firstOrNull { compareParents(it.parents, selector.parents) == 0 }
        if (same != null) {
            same.depth = maxOf(same.depth, depth)
            same.role = selector.role
            if (style != NOT_SET) same.style = style
        } else {
            node.withParents += Rule(depth, selector.parents, selector.role, if (style != NOT_SET) style else node.main.style)
        }
    }

    companion object {
        /** The roles of the one table, [syntaxScopes]. */
        val shipped: SyntaxRoles by lazy { SyntaxRoles(syntaxScopes) }

        private const val NOT_SET = -1
        private const val ITALIC = 1
        private const val BOLD = 2
        private const val UNDERLINE = 4
        private const val STRIKETHROUGH = 8

        /** The font style [editorTheme] gives [role]'s rule, or none set. */
        private fun fontStyle(role: SyntaxRole): Int {
            val style = (if (role.italic) ITALIC else 0) or (if (role.bold) BOLD else 0) or
                (if (role.underline) UNDERLINE else 0) or (if (role.strikethrough) STRIKETHROUGH else 0)
            return if (style == 0) NOT_SET else style
        }

        private fun named(scope: String, selector: String) =
            scope == selector || (scope.startsWith(selector) && scope[selector.length] == '.')

        /** tm4e's StringUtils.strArrCmp: none first, then fewer, then by each selector. */
        private fun compareParents(a: List<String>?, b: List<String>?): Int = when {
            a == null && b == null -> 0
            a == null -> -1
            b == null -> 1
            a.size != b.size -> a.size - b.size
            else -> a.indices.asSequence().map { a[it].compareTo(b[it]) }.firstOrNull { it != 0 } ?: 0
        }

        /** tm4e's ThemeTrieElement._cmpBySpecificity: deeper selector first, then longer parent selectors. */
        private fun bySpecificity(a: Rule, b: Rule): Int {
            if (a.depth != b.depth) return b.depth - a.depth
            val aParents = a.parents.orEmpty()
            val bParents = b.parents.orEmpty()
            if (aParents.size == bParents.size) {
                aParents.indices.firstOrNull { aParents[it].length != bParents[it].length }
                    ?.let { return bParents[it].length - aParents[it].length }
            }
            return bParents.size - aParents.size
        }
    }
}
