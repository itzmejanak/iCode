package dev.icode.mobile.ui.editor

/** TextMate scopes shipped with the APK. Unrecognized text stays editable as plain text. */
internal fun scopeForFile(name: String): String? {
    val lower = name.lowercase()
    val extension = lower.substringAfterLast('.', missingDelimiterValue = "")
    return when (extension) {
        "kt", "kts" -> "source.kotlin"
        "java" -> "source.java"
        "py", "pyw" -> "source.python"
        "js", "mjs", "cjs" -> "source.js"
        "jsx" -> "source.js.jsx"
        "ts", "mts", "cts" -> "source.ts"
        "tsx" -> "source.tsx"
        "json", "jsonc" -> "source.json"
        "html", "htm" -> "text.html.basic"
        "xml", "svg" -> "text.xml"
        "css" -> "source.css"
        "md", "markdown" -> "text.html.markdown"
        "sh", "bash", "zsh" -> "source.shell"
        "rs" -> "source.rust"
        "go" -> "source.go"
        "yaml", "yml" -> "source.yaml"
        else -> null
    }
}

internal fun isMarkdownFile(name: String): Boolean =
    name.endsWith(".md", ignoreCase = true) || name.endsWith(".markdown", ignoreCase = true)
