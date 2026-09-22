package dev.icode.mobile.demo

/**
 * Deterministic Phase 1 content for screens whose state is not (yet) part of
 * [dev.icode.mobile.domain.WorkbenchSnapshot].
 *
 * Phase 3 replaces these fixtures with gateway state. Keeping them in one file
 * prevents the same demo string from being duplicated across screens.
 */
object DemoContent {
    const val packageJson = "package.json"
    const val appTsx = "App.tsx"

    /** Explorer row highlighted on first load (matches the Figma frame). */
    const val selectedExplorerPath = "src/utils/helpers.ts"

    /** Active branch for source control, editor and sync frames. */
    const val activeBranch = "feature/auth"

    val branches = listOf("main", "develop", activeBranch, "feature/dashboard", "bugfix/login-error")

    val authMethods = listOf(
        "SSH Public/Private Key",
        "Personal Access Token (PAT)",
        "Username & Password",
    )

    /** One recoverable document shown by the recovery center. */
    data class RecoveryDocument(
        val name: String,
        val path: String,
        val size: String,
        val time: String,
    )

    val recoveryDocuments = listOf(
        RecoveryDocument("auth.ts", "src/components/auth.ts", "4.2 KB", "14:28:10"),
        RecoveryDocument("token-utils.ts", "src/utils/token-utils.ts", "1.8 KB", "14:25:32"),
        RecoveryDocument(packageJson, "package.json", "12 KB", "14:15:00"),
    )
}
