package dev.icode.mobile.ui.syntax

import java.util.concurrent.ConcurrentHashMap

/**
 * Which TextMate scopes a language server's token stands for: VS Code's own defaults
 * (src/vs/platform/theme/common/tokenClassificationRegistry.ts, commit 307b60e7, MIT), by token type
 * and the modifiers it must have. A token takes the rule with the most modifiers it has, and of that
 * rule the first scope [syntaxScopes] colours, so a parameter is drawn as `variable.parameter` is.
 * No colours of its own: the roles are the one table's (docs/editor-and-language-services.md).
 */
internal val semanticScopes: List<Triple<String, Set<String>, List<String>>> = listOf(
    Triple("comment", emptySet(), listOf("comment")),
    Triple("string", emptySet(), listOf("string")),
    Triple("keyword", emptySet(), listOf("keyword.control")),
    Triple("number", emptySet(), listOf("constant.numeric")),
    Triple("regexp", emptySet(), listOf("constant.regexp")),
    Triple("operator", emptySet(), listOf("keyword.operator")),
    Triple("namespace", emptySet(), listOf("entity.name.namespace")),
    Triple("type", emptySet(), listOf("entity.name.type", "support.type")),
    Triple("struct", emptySet(), listOf("entity.name.type.struct")),
    Triple("class", emptySet(), listOf("entity.name.type.class", "support.class")),
    Triple("interface", emptySet(), listOf("entity.name.type.interface")),
    Triple("enum", emptySet(), listOf("entity.name.type.enum")),
    Triple("typeParameter", emptySet(), listOf("entity.name.type.parameter")),
    Triple("function", emptySet(), listOf("entity.name.function", "support.function")),
    Triple("method", emptySet(), listOf("entity.name.function.member", "support.function")),
    // `member` is VS Code's old name for `method`.
    Triple("member", emptySet(), listOf("entity.name.function.member", "support.function")),
    Triple("macro", emptySet(), listOf("entity.name.function.preprocessor")),
    Triple("variable", emptySet(), listOf("variable.other.readwrite", "entity.name.variable")),
    Triple("parameter", emptySet(), listOf("variable.parameter")),
    Triple("property", emptySet(), listOf("variable.other.property")),
    Triple("enumMember", emptySet(), listOf("variable.other.enummember")),
    Triple("event", emptySet(), listOf("variable.other.event")),
    Triple("decorator", emptySet(), listOf("entity.name.decorator", "entity.name.function")),
    Triple("variable", setOf("readonly"), listOf("variable.other.constant")),
    Triple("property", setOf("readonly"), listOf("variable.other.constant.property")),
    Triple("type", setOf("defaultLibrary"), listOf("support.type")),
    Triple("class", setOf("defaultLibrary"), listOf("support.class")),
    Triple("interface", setOf("defaultLibrary"), listOf("support.class")),
    Triple("variable", setOf("defaultLibrary"), listOf("support.variable", "support.other.variable")),
    Triple("variable", setOf("defaultLibrary", "readonly"), listOf("support.constant")),
    Triple("property", setOf("defaultLibrary"), listOf("support.variable.property")),
    Triple("property", setOf("defaultLibrary", "readonly"), listOf("support.constant.property")),
    Triple("function", setOf("defaultLibrary"), listOf("support.function")),
    Triple("method", setOf("defaultLibrary"), listOf("support.function")),
    Triple("member", setOf("defaultLibrary"), listOf("support.function")),
)

/**
 * A semantic token's [SyntaxStyle] by [semanticScopes] and [roles]; null where no rule names its type
 * (`label`, a server's own types), or no scope of its rules is coloured: the grammar's colour stays,
 * as in VS Code. Any thread.
 */
internal class SemanticRoles(private val roles: SyntaxRoles = SyntaxRoles.shipped) {
    private val byType = semanticScopes.groupBy { it.first }.mapValues { (_, rules) -> rules.sortedByDescending { it.second.size } }
    private val styles = ConcurrentHashMap<Pair<String, Set<String>>, Any>()

    fun style(type: String, modifiers: Set<String>): SyntaxStyle? = styles.getOrPut(type to modifiers) { resolve(type, modifiers) ?: NONE } as? SyntaxStyle

    private fun resolve(type: String, modifiers: Set<String>): SyntaxStyle? =
        byType[type].orEmpty().asSequence().filter { modifiers.containsAll(it.second) }.flatMap { it.third }
            .firstOrNull { roles.named(listOf(it)) != null }?.let { roles.style(listOf(it)) }

    companion object {
        val shipped: SemanticRoles by lazy { SemanticRoles() }
        private val NONE = Any()
    }
}
