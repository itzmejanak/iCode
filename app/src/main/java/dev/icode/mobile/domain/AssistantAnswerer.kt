package dev.icode.mobile.domain

/**
 * Who answers a chat (docs/assistant-and-mcp.md): a chat may switch between them at any time, the
 * next one taking the conversation on, and each reply keeps who wrote it ([AssistantMessage.Reply.by]).
 */
sealed interface AssistantAnswerer

/** A provider's model, answered by the built-in assistant. */
data class AssistantChoice(val provider: String, val model: String) : AssistantAnswerer
