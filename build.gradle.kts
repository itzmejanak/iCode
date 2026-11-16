plugins {
    id("com.android.application") version "8.13.2" apply false
    // Kotlin 2.4: the MCP SDK's runtime needs kotlin-stdlib 2.4.21 (docs/assistant-and-mcp.md).
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
