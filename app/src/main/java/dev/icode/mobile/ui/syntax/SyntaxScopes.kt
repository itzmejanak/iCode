package dev.icode.mobile.ui.syntax

import dev.icode.mobile.designsystem.SyntaxRole

/**
 * Which TextMate scopes take which [SyntaxRole]: the one table every editor
 * theme is built from ([editorTheme]), and the one read-only views resolve
 * their tokens' roles by ([SyntaxRoles]), so both colour a token alike.
 *
 * First VS Code's default Dark+ selectors (extensions/theme-defaults,
 * `dark_vs.json` + `dark_plus.json`, commit 307b60e7, MIT), written for the
 * same grammars the editor ships; only the colours are iCode's. Then iCode's
 * own additions, for scopes those rules leave uncoloured. A scope that matches
 * nothing takes its enclosing scope's colour, or plain text, on purpose only
 * for [inheritedScopes]; SyntaxScopesTest fails on any grammar scope that is
 * neither placed nor inherited.
 */
internal val syntaxScopes: List<Pair<SyntaxRole, List<String>>> = listOf(
    SyntaxRole.Plain to listOf(
        "meta.embedded", "source.groovy.embedded", "string meta.image.inline.markdown",
        "variable.legacy.builtin.python", "meta.template.expression", "keyword.operator",
        "storage.modifier.import.java", "variable.language.wildcard.java", "storage.modifier.package.java",
        "entity.name.label",
    ),
    SyntaxRole.Emphasis to listOf(
        "emphasis", "markup.italic",
    ),
    SyntaxRole.Strong to listOf(
        "strong", "markup.bold",
    ),
    SyntaxRole.Heading to listOf(
        "header", "markup.heading",
    ),
    SyntaxRole.Comment to listOf(
        "comment",
    ),
    SyntaxRole.Keyword to listOf(
        "constant.language", "meta.preprocessor", "entity.name.function.preprocessor", "meta.diff.header",
        "storage", "storage.type", "storage.modifier", "keyword.operator.noexcept",
        "punctuation.definition.template-expression.begin", "punctuation.definition.template-expression.end",
        "punctuation.section.embedded", "keyword", "keyword.operator.new", "keyword.operator.expression",
        "keyword.operator.cast", "keyword.operator.sizeof", "keyword.operator.alignof",
        "keyword.operator.typeid", "keyword.operator.alignas", "keyword.operator.instanceof",
        "keyword.operator.logical.python", "keyword.operator.wordlike",
        "punctuation.section.embedded.begin.php", "punctuation.section.embedded.end.php", "variable.language",
        "constant.character", "constant.other.option",
    ),
    SyntaxRole.Number to listOf(
        "constant.numeric", "keyword.operator.plus.exponent", "keyword.operator.minus.exponent",
        "meta.preprocessor.numeric", "keyword.other.unit", "constant.sha.git-rebase",
    ),
    SyntaxRole.Regex to listOf(
        "constant.regexp", "string.regexp", "punctuation.definition.group.regexp",
        "punctuation.definition.group.assertion.regexp", "punctuation.definition.character-class.regexp",
        "punctuation.character.set.begin.regexp", "punctuation.character.set.end.regexp",
        "keyword.operator.negation.regexp", "support.other.parenthesis.regexp",
        "constant.character.character-class.regexp", "constant.other.character-class.set.regexp",
        "constant.other.character-class.regexp", "constant.character.set.regexp",
    ),
    SyntaxRole.Tag to listOf(
        "entity.name.tag", "entity.name.tag.css", "entity.name.tag.less",
        "entity.other.attribute-name.class.css", "source.css entity.other.attribute-name.class",
        "entity.other.attribute-name.id.css", "entity.other.attribute-name.parent-selector.css",
        "entity.other.attribute-name.parent.less", "source.css entity.other.attribute-name.pseudo-class",
        "entity.other.attribute-name.pseudo-element.css", "source.css.less entity.other.attribute-name.id",
        "entity.other.attribute-name.scss",
    ),
    SyntaxRole.Attribute to listOf(
        "entity.other.attribute-name", "meta.structure.dictionary.key.python",
        "support.type.vendored.property-name", "support.type.property-name", "source.css variable",
        "source.coffee.embedded", "support.function.git-rebase", "meta.object-literal.key",
    ),
    SyntaxRole.Invalid to listOf(
        "invalid",
    ),
    SyntaxRole.Link to listOf(
        "markup.underline",
    ),
    SyntaxRole.Strikethrough to listOf(
        "markup.strikethrough",
    ),
    SyntaxRole.Inserted to listOf(
        "markup.inserted",
    ),
    SyntaxRole.Deleted to listOf(
        "markup.deleted",
    ),
    SyntaxRole.Changed to listOf(
        "markup.changed",
    ),
    SyntaxRole.Quote to listOf(
        "punctuation.definition.quote.begin.markdown",
    ),
    SyntaxRole.ListMarker to listOf(
        "punctuation.definition.list.begin.markdown",
    ),
    SyntaxRole.String to listOf(
        "markup.inline.raw", "meta.preprocessor.string", "string", "meta.embedded.assembly", "string.tag",
        "string.value",
    ),
    SyntaxRole.TagPunctuation to listOf(
        "punctuation.definition.tag",
    ),
    SyntaxRole.Function to listOf(
        "entity.name.function", "support.function", "support.constant.handlebars",
        "source.powershell variable.other.member", "entity.name.operator.custom-literal",
        "keyword.operator.or.regexp", "keyword.control.anchor.regexp",
    ),
    SyntaxRole.Type to listOf(
        "support.class", "support.type", "entity.name.type", "entity.name.namespace",
        "entity.other.attribute", "entity.name.scope-resolution", "entity.name.class",
        "storage.type.numeric.go", "storage.type.byte.go", "storage.type.boolean.go",
        "storage.type.string.go", "storage.type.uintptr.go", "storage.type.error.go", "storage.type.rune.go",
        "storage.type.cs", "storage.type.generic.cs", "storage.type.modifier.cs", "storage.type.variable.cs",
        "storage.type.annotation.java", "storage.type.generic.java", "storage.type.java",
        "storage.type.object.array.java", "storage.type.primitive.array.java", "storage.type.primitive.java",
        "storage.type.token.java", "storage.type.groovy", "storage.type.annotation.groovy",
        "storage.type.parameters.groovy", "storage.type.generic.groovy", "storage.type.object.array.groovy",
        "storage.type.primitive.array.groovy", "storage.type.primitive.groovy", "meta.type.cast.expr",
        "meta.type.new.expr", "support.constant.math", "support.constant.dom", "support.constant.json",
        "entity.other.inherited-class", "punctuation.separator.namespace.ruby",
    ),
    SyntaxRole.Control to listOf(
        "keyword.control", "source.cpp keyword.operator.new", "keyword.operator.delete",
        "keyword.other.using", "keyword.other.directive.using", "keyword.other.operator",
        "entity.name.operator",
    ),
    SyntaxRole.Variable to listOf(
        "variable", "meta.definition.variable.name", "support.variable", "entity.name.variable",
        "constant.other.placeholder",
    ),
    SyntaxRole.Constant to listOf(
        "variable.other.constant", "variable.other.enummember", "support.constant.property-value",
        "support.constant.font-name", "support.constant.media-type", "support.constant.media",
        "constant.other.color.rgb-value", "constant.other.rgb-value", "support.constant.color",
    ),
    SyntaxRole.Escape to listOf(
        "keyword.operator.quantifier.regexp", "constant.character.escape",
    ),
    // iCode's additions, from the shipped grammars' own scope lists (2026-09-29).
    SyntaxRole.Constant to listOf(
        "constant.other.caps", "constant.other.enum", "constant.other.ellipsis", "constant.other.unicode-range",
        "support.constant", "constant.codepoint-range", "support.unicode-range", "constant.enum", "constant.variable",
        "constant.other.symbol", "constant.other.variable.mac-classic", "entity.name.constant",
        "entity.name.other.preprocessor.macro.predefined", "entity.other.counter-name", "entity.other.counter-style-name",
        // YAML's dates and times.
        "constant.other.timestamp",
        // Groovy's UPPER_CASE names, PHP's constant names and `Foo::NAME`, SCSS's `n`, `even`, `odd`.
        "constant.other.groovy", "constant.other.php", "constant.other.class.php", "constant.other.scss",
    ),
    SyntaxRole.String to listOf(
        "constant.other.rune", "markup.raw.block", "markup.raw.inline",
        // A C/C++ header name in `#include <…>`; the value in a `.env` line.
        "entity.name.other.preprocessor.macro.include", "property.value",
    ),
    SyntaxRole.Regex to listOf("constant.other.character-class.range.regexp", "constant.other.set.regexp", "support.other.match"),
    SyntaxRole.Escape to listOf("support.other.escape.special.regexp"),
    SyntaxRole.Function to listOf("entity.name.command"),
    SyntaxRole.Type to listOf(
        "entity.name.import", "entity.name.module", "entity.name.package", "entity.other.namespace-prefix",
        "entity.name.namespace-prefix", "entity.other.alias", "support.other.namespace",
        // PHP attributes `#[Route]`; SQL's database and table names.
        "support.attribute", "constant.other.database-name", "constant.other.table-name",
    ),
    SyntaxRole.Number to listOf("entity.other.keyframe-offset"),
    SyntaxRole.Attribute to listOf(
        "constant.other.key", "fenced_code.block.language",
        // A key in a data file is one thing, as JSON's are: YAML's, INI's and .properties', .env's.
        "entity.name.tag.yaml", "keyword.other.definition.ini", "variable.key",
        // A property read and a key written are one thing: one colour.
        "variable.other.property", "variable.other.object.property", "support.variable.property",
    ),
    SyntaxRole.Link to listOf("constant.other.reference.link"),
    // Kotlin's `$name` in a string, as VS Code colours `${` `}`; YAML's `---` and `...` between documents;
    // C++'s `[[nodiscard]]`; Less's repetition keywords.
    SyntaxRole.Keyword to listOf(
        "entity.string.template.element", "entity.other.document", "support.other.attribute", "support.keyword",
        // A YAML `%` directive the specification reserves.
        "support.other.directive.reserved",
    ),
    SyntaxRole.Variable to listOf("support.other.variable"),
    // A `goto` label, as VS Code's `entity.name.label`.
    SyntaxRole.Plain to listOf("entity.name.goto-label", "support.other.php"),
    SyntaxRole.Quote to listOf("markup.quote"),
    // A section's name: a Markdown heading's text, an INI `[section]`.
    SyntaxRole.Heading to listOf("entity.name.section", "entity.section"),
    // iCode's additions for the grammars of every language (S6, 2026-11-11). First TextMate's own names
    // for what has no more specific rule: a constant, a name, an entity.
    SyntaxRole.Constant to listOf("constant", "support.language", "support.property-value"),
    SyntaxRole.Type to listOf(
        "entity", "support.module", "support.tabletype", "support.namespace", "support.package", "support.apex",
        "support.other.module", "support.other.protocol", "support.other.proto",
    ),
    SyntaxRole.Attribute to listOf(
        "entity.other", "entity.name.record.field", "entity.property", "markup.meta", "support.other.vcl",
    ),
    SyntaxRole.Function to listOf("entity.function", "entity.helper", "markup.macro"),
    SyntaxRole.Variable to listOf(
        "entity.function.arguments", "entity.function.operands", "entity.function.local-variables",
        "entity.function.return-value", "entity.global", "support.system", "markup.substitution",
    ),
    // Numbers written another way (JSON5's hex and decimal, Turtle's, Nushell's binary).
    SyntaxRole.Number to listOf("constant.dec", "constant.hex", "constant.binary", "constant.complex", "entity.status", "constant.unit"),
    SyntaxRole.Keyword to listOf(
        "constant.boolean", "entity.directive", "entity.other.jinja.delimiter", "entity.other.prompt-prefix",
        "support.block", "markup.admonition",
    ),
    SyntaxRole.Escape to listOf("constant.escape", "markup.other.escape", "markup.character-reference"),
    SyntaxRole.Regex to listOf("constant.other.character-class"),
    SyntaxRole.String to listOf(
        "entity.other.attribute-value", "entity.value", "entity.mime-type", "entity.service", "markup.raw", "markup.monospace",
        "markup.math",
    ),
    SyntaxRole.Tag to listOf("entity.other.attribute-selector", "entity.other.tag", "entity.other.tag-mixin", "entity.other.tag-ref", "entity.tag"),
    SyntaxRole.Emphasis to listOf("markup.emphasis", "markup.mark", "markup.highlight"),
    SyntaxRole.Strong to listOf("markup.strong", "support.strong"),
    SyntaxRole.Strikethrough to listOf("markup.strike"),
    SyntaxRole.Quote to listOf("support.quote"),
    SyntaxRole.Link to listOf("markup.link", "markup.other", "markup.reference", "markup.biblioref", "markup.blockid", "entity.name.footnote"),
    SyntaxRole.Plain to listOf("entity.label"),
    // A doc comment's description and address, which are Comment where they are written.
    SyntaxRole.Comment to listOf("constant.other.description", "constant.other.email.link.underline"),
)

/** Scope prefixes left, on purpose, to the colour of what encloses them. */
internal val inheritedScopes: List<String> = listOf(
    // Structure: a function, a block, an argument list, a template.
    "meta",
    // Brackets and separators; a string's quotes and a comment's markers take its colour.
    "punctuation",
    // The root of a language, or of one embedded in another.
    "source", "text",
    // A list item's text (its marker is a ListMarker).
    "markup.list",
    // A fenced block: the grammar of its language colours it.
    "markup.fenced_code",
    // A fenced or delimited block, a table and its cells, text set higher or lower: their text colours them.
    "markup.code", "markup.block", "markup.table", "markup.content", "markup.punctuation",
    "markup.sub", "markup.subscript", "markup.super", "markup.superscript",
    // Embedded GLSL in Elm, a Handlebars expression: the embedded language colours them.
    "entity.glsl", "entity.expression", "entity.subexpression", "entity.unescaped",
    // API Blueprint's fence lines, as Markdown's fence markers.
    "support.gfm",
)

/**
 * TextMate's own first names for scopes (its manual, "Naming Conventions"), and the roots a grammar's
 * text starts in. A scope under any other first name (C's `everything.else`, TypeScript's `case-clause`,
 * Hack's `XHPEndTagStart`) is a grammar's own, which no theme styles: it takes what encloses it, as in
 * VS Code, on purpose (SyntaxScopesTest).
 */
internal val namingRoots: Set<String> = setOf(
    "comment", "constant", "entity", "invalid", "keyword", "markup", "meta", "storage", "string", "support", "variable",
    "punctuation", "source", "text",
)
