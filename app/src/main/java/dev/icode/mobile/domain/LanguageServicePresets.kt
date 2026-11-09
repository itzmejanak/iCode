package dev.icode.mobile.domain

/**
 * Language servers known to install in iCode's Ubuntu 24.04 ARM64 runtime (docs/lsp-plan.md §2.4, D9):
 * data only, no code per language. Nothing installs without the user's tap or the assistant's command,
 * asked as any command is.
 */
data class LanguageServicePreset(
    val id: String,
    val configuration: LanguageServiceConfiguration,
    /** The command that installs it, typed in a terminal. */
    val install: String,
    /** What it needs first: Runtime Tools' Node.js for npm's servers. */
    val needs: String? = null,
)

object LanguageServicePresets {
    private const val NODE = "Node.js, from Settings › Runtime Tools"

    val all: List<LanguageServicePreset> = listOf(
        preset("pylsp", "Python (pylsp)", "pylsp", "python", "pyproject.toml setup.py setup.cfg requirements.txt", "sudo apt install -y python3-pylsp"),
        preset("pyright", "Python (Pyright)", "pyright-langserver --stdio", "python", "pyproject.toml pyrightconfig.json setup.py requirements.txt", "npm install -g pyright", NODE),
        preset("clangd", "C and C++ (clangd)", "clangd", "c cpp", "compile_commands.json compile_flags.txt CMakeLists.txt .clangd", "sudo apt install -y clangd"),
        preset("gopls", "Go (gopls)", "gopls", "go", "go.work go.mod", "sudo apt install -y gopls"),
        preset(
            "typescript", "TypeScript and JavaScript", "typescript-language-server --stdio",
            "typescript typescriptreact javascript javascriptreact", "tsconfig.json jsconfig.json package.json",
            "npm install -g typescript-language-server typescript", NODE,
        ),
        preset("bash", "Bash", "bash-language-server start", "shellscript", "", "npm install -g bash-language-server", NODE),
        preset("html", "HTML", "vscode-html-language-server --stdio", "html", "package.json", "npm install -g vscode-langservers-extracted", NODE),
        preset("css", "CSS, SCSS and Less", "vscode-css-language-server --stdio", "css scss less", "package.json", "npm install -g vscode-langservers-extracted", NODE),
        preset("json", "JSON", "vscode-json-language-server --stdio", "json", "", "npm install -g vscode-langservers-extracted", NODE),
        preset("yaml", "YAML", "yaml-language-server --stdio", "yaml", "", "npm install -g yaml-language-server", NODE),
    )

    fun of(id: String): LanguageServicePreset? = all.firstOrNull { it.id == id }

    private fun preset(id: String, name: String, command: String, languages: String, markers: String, install: String, needs: String? = null) =
        LanguageServicePreset(id, LanguageServiceConfiguration("", name, command, languages = languages, rootMarkers = markers), install, needs)
}
