plugins {
    id("com.android.application") version "9.5.1" apply false
    // Kotlin 2.4: the MCP SDK's runtime needs kotlin-stdlib 2.4.21 (docs/mcp-plan.md §2.2, M0).
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
