package dev.icode.mobile.ui.syntax

/**
 * What SyntaxSamplesTest allows a sample to leave without a role, and why (docs/syntax-highlighting-plan.md
 * A10, S6 part 2). Each is how the language's grammar draws it, as VS Code draws it too: the grammar has no
 * rule there, not iCode's table. A grammar iCode could fix is fixed instead (tools/import-grammars.py EDITS).
 */
internal object SyntaxSamples {
    /** Languages whose text outside their markup is the document's own content (prose or data), plain on purpose. */
    val PROSE: Map<String, String> = mapOf(
        "markdown" to "a paragraph's text",
        "asciidoc" to "a paragraph's text and a table's cells",
        "org" to "a heading's and a paragraph's text, a property's value and a timestamp's date",
        "wikitext" to "a paragraph's text, a template's arguments and a link's address",
        "xml" to "an element's text: a book's title, price and date in the sample",
        "gherkin" to "a step's text after Given, When and Then",
        "eml" to "a header's value (the date) and the message's body",
        "search-result" to "the summary line VS Code writes above the results",
    )

    private const val VALUE = "an unquoted value or argument, which the grammar draws as text"

    /** Words a language's sample leaves without a role, and why. */
    val EXCEPTIONS: Map<String, Pair<String, Set<String>>> = mapOf(
        "agc" to ("an instruction's operand, which the grammar draws as text" to setOf("0", "10", "4000")),
        "abap-cds" to ("a cardinality's bounds, `[0..*]`" to setOf("0")),
        "angular-html" to ("an `@if` block's condition, which the grammar leaves to the template's text" to setOf("0")),
        "antlr" to ("a character set in a lexer rule, `[0-9]`" to setOf("0", "9")),
        "apiblueprint" to ("the FORMAT line's version" to setOf("1A")),
        "bat" to ("a label's name after goto, and the words `if` compares" to setOf("1", "2", "else", "endif")),
        "bibtex" to ("a field's value written without braces or quotes" to setOf("01", "06", "12", "1997", "2006", "2010", "8")),
        "blade" to ("an address in the text after the template" to setOf("2024")),
        "c" to ("a macro used as a value: C's grammar names a macro only where `#define` defines it" to setOf("ARR_LEN")),
        "cabal" to ("the cabal-version field's value" to setOf("0", "3")),
        "clarity" to ("part of a hyphenated name (`err-owner-only`, `to-print`) that starts like a built-in" to setOf("err", "print")),
        "codeowners" to ("a section's number of required reviewers, `[Vue] [2]`" to setOf("2")),
        "denizenscript" to ("a command's argument (`wait 5s`)" to setOf("5s")),
        "desktop" to ("$VALUE (`InitialPreference=5`)" to setOf("5")),
        "dot" to ("an attribute's value inside `[ ]`" to setOf("2")),
        "ejs" to ("JavaScript inside an output tag within an element's text, which the grammar reads as JSX text" to setOf("1")),
        "graphql" to ("a number in the sample's `variables` block, which is JSON after the query, not GraphQL" to setOf("3")),
        "groovy" to ("a method defined with `def` at the top of a script, and a name `import` coloured on another line" to setOf("def", "jetty")),
        "haml" to ("the doctype's version, `!!! 5`" to setOf("5")),
        "html" to ("a textarea's text, which is the page's content" to setOf("448")),
        "http" to ("the port in a request line's address" to setOf("8080")),
        "hurl" to ("a number in a JSON request body" to setOf("1")),
        "hxml" to ("a library's version in a path and a define" to setOf("0", "1", "12")),
        "icalendar" to ("a property's value" to setOf("0", "1", "2", "3", "42")),
        "ini" to ("$VALUE" to setOf("0", "143", "192", "2", "62")),
        "jflex" to ("a character class's range, `[0-9]`" to setOf("0", "9", "9_")),
        "jinja" to ("an attribute's value in the HTML the template writes" to setOf("8")),
        "just" to ("an index in a PowerShell recipe's line, which the grammar draws as text" to setOf("0")),
        "lean4-markdown" to ("a `lean` code block: Lean's own grammar is not shipped" to setOf("0", "1")),
        "macaulay2" to ("an exponent after `^`" to setOf("2")),
        "nginx" to ("an address and port in a directive's arguments, and parts of a path" to
            setOf("0", "1", "1025", "127", "168", "192", "3", "8000", "8001", "8080", "http", "index", "log", "pid", "server")),
        "nunjucks" to ("a number in a `{{ }}` or `{% %}` expression" to setOf("1", "42")),
        "ocaml" to ("the sample is a utop session: its prompts and replies are not OCaml source" to setOf("32", "type")),
        "openscad" to ("a module's parameter defaults, `module Logo(size=50)`" to setOf("100", "50")),
        "peggy" to ("a character class, `[0-9]`, which the grammar scopes as `declaration.keyword.peggy`, a name no theme styles" to setOf("0", "9")),
        "python-console" to ("a call's argument on a `>>>` line, which the console grammar reads through a capture, where Python's rule for the brackets stops; and the session's output" to setOf("12", "144")),
        "plantuml" to ("a message's text" to setOf("401")),
        "polar" to ("a rule's parameter name (`actor: Actor`), a keyword only where it starts a declaration" to setOf("actor", "resource")),
        "prolog" to ("a predicate's arity, `player/2`" to setOf("1", "2")),
        "raku" to ("a number after `}` that closes a hash subscript" to setOf("1")),
        "rebol" to ("a date, `1-Jan-2026`, which the grammar scopes as `date.rebol`, a name no theme styles" to setOf("1", "2026")),
        "robotframework" to ("a variable's value in the Variables table" to setOf("10")),
        "ruby" to ("on the JVM only: joni rejects the lookbehind of the rule for a block's `|params|`, so the class's body reads as one (`meta.block.parameters.ruby`)" to setOf("0x3", "1", "25", "3", "5", "raise")),
        "sas" to ("a number in a DATA step's assignment" to setOf("0", "25")),
        "scheme" to ("the value in `(define NAME value)`, and a number after a string" to setOf("150", "180", "8")),
        "splunk" to ("a time span's value" to setOf("1m", "30s")),
        "svelte" to ("an element's text" to setOf("of")),
        "systemd" to ("$VALUE (a manual page's section in Documentation=)" to setOf("1")),
        "tcl" to ("a command inside braces, which Tcl's grammar draws as text (its commands are words in a list)" to setOf("set")),
        "vhdl" to ("a port's name in a port declaration, `CLOCK_50: in std_logic`" to setOf("CLOCK_50", "LEDR", "SW")),
        "xsl" to ("CSS in a `<style>` element, which the XSL grammar does not embed" to setOf("0", "10px", "155px", "1px", "50px", "5px", "639ACE")),
        "yang" to ("part of a module's name, `example-system`" to setOf("system")),
        "zenscript" to ("an import's path, which the grammar scopes as `plain.text`" to setOf("BracketHandlers")),
    )
}
