package dev.icode.mobile.domain

// Tools panel (S7): problems reported for the workspace.

data class Diagnostic(
    val severity: Severity,
    val file: String,
    val message: String,
    val location: String,
    val source: String = "TS",
)

enum class Severity { Error, Warning, Info }
