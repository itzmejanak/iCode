package dev.icode.mobile.ui.syntax

import dev.icode.mobile.designsystem.SyntaxRole
import dev.icode.mobile.domain.SemanticTokenNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A server's token types and modifiers through VS Code's scopes to the one table's roles (S7). */
class SemanticRolesTest {
    private val roles = SemanticRoles.shipped

    private fun role(type: String, vararg modifiers: String) = roles.style(type, modifiers.toSet())?.role

    @Test
    fun `every standard token type takes a role, but the two VS Code leaves to the grammar`() {
        // `label` and `modifier` have no default scope in VS Code: the grammar's colour stays.
        val left = setOf("label", "modifier")
        val unmapped = SemanticTokenNames.types.filter { it !in left && role(it) == null }
        assertTrue("token types without a role: $unmapped", unmapped.isEmpty())
        left.forEach { assertNull(it, role(it)) }
        assertTrue(semanticScopes.map { it.first }.toSet().containsAll(SemanticTokenNames.types - left))
    }

    @Test
    fun `parameters and properties are coloured apart, as their scopes are`() {
        assertEquals(SyntaxRole.Variable, role("parameter"))
        assertEquals(SyntaxRole.Attribute, role("property"))
        assertNotEquals(role("parameter"), role("property"))
        assertEquals(SyntaxRoles.shipped.named(listOf("variable.parameter")), role("parameter"))
        assertEquals(SyntaxRoles.shipped.named(listOf("variable.other.property")), role("property"))
    }

    @Test
    fun `each type takes the role of its scope in the one table`() {
        val expected = mapOf(
            "namespace" to SyntaxRole.Type, "type" to SyntaxRole.Type, "class" to SyntaxRole.Type, "enum" to SyntaxRole.Type,
            "interface" to SyntaxRole.Type, "struct" to SyntaxRole.Type, "typeParameter" to SyntaxRole.Type,
            "variable" to SyntaxRole.Variable, "enumMember" to SyntaxRole.Constant, "function" to SyntaxRole.Function,
            "method" to SyntaxRole.Function, "comment" to SyntaxRole.Comment, "string" to SyntaxRole.String,
            "number" to SyntaxRole.Number, "regexp" to SyntaxRole.Regex, "keyword" to SyntaxRole.Control,
        )
        expected.forEach { (type, role) -> assertEquals(type, role, role(type)) }
    }

    @Test
    fun `modifiers choose the rule with the most of them`() {
        assertEquals(SyntaxRole.Constant, role("variable", "readonly"))
        assertEquals(SyntaxRoles.shipped.named(listOf("support.constant")), role("variable", "readonly", "defaultLibrary"))
        assertEquals(SyntaxRole.Function, role("function", "defaultLibrary"))
        // A modifier no rule names changes nothing.
        assertEquals(role("parameter"), role("parameter", "declaration", "deprecated"))
    }

    @Test
    fun `a server's own token type leaves the grammar's colour`() {
        assertNull(role("selfParameter"))
        assertNull(role("unknown", "readonly"))
    }
}
