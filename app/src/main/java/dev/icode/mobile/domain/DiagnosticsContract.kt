package dev.icode.mobile.domain

// Tools panel (S7): problems reported for the workspace.

data class Diagnostic(
    val severity: Severity,
    val file: String,
    val message: String,
    val location: String,
    /** The tool that reported it, as it names itself. */
    val source: String,
)

enum class Severity { Error, Warning, Info }
