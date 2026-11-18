package dev.icode.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.R
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.AssistantAnswerer
import dev.icode.mobile.domain.AssistantChoice
import dev.icode.mobile.domain.AssistantProviderKind

/**
 * [this] kind's mark, from Lobe Icons (res/drawable/mark_*.xml; THIRD_PARTY_NOTICES.md), drawn in the
 * colour given, as every icon in iCode is. An OpenAI-compatible server, which may be anyone's, has a
 * plain one. The names and marks are their owners' trademarks.
 */
@Composable
internal fun AssistantProviderKind.mark(): ImageVector = when (this) {
    AssistantProviderKind.OpenAI -> ImageVector.vectorResource(R.drawable.mark_openai)
    AssistantProviderKind.Anthropic -> ImageVector.vectorResource(R.drawable.mark_anthropic)
    AssistantProviderKind.Google -> ImageVector.vectorResource(R.drawable.mark_gemini)
    AssistantProviderKind.OpenRouter -> ImageVector.vectorResource(R.drawable.mark_openrouter)
    AssistantProviderKind.Ollama -> ImageVector.vectorResource(R.drawable.mark_ollama)
    AssistantProviderKind.OpenAICompatible -> Icons.Outlined.Hub
}

/**
 * The mark of the coding agent [id] (docs/assistant-and-mcp.md): the ACP registry's icon
 * (res/drawable/mark_agent_*.xml), or its maker's mark from Lobe Icons where the registry's is that same
 * one — Codex's OpenAI, Gemini CLI's Gemini. The marks are their owners' trademarks.
 */
@Composable
internal fun agentMark(id: String): ImageVector = when (id) {
    "claude-acp" -> ImageVector.vectorResource(R.drawable.mark_agent_claude)
    "codex-acp" -> ImageVector.vectorResource(R.drawable.mark_openai)
    "gemini" -> ImageVector.vectorResource(R.drawable.mark_gemini)
    "opencode" -> ImageVector.vectorResource(R.drawable.mark_agent_opencode)
    "cline" -> ImageVector.vectorResource(R.drawable.mark_agent_cline)
    "factory-droid" -> ImageVector.vectorResource(R.drawable.mark_agent_droid)
    else -> Icons.Outlined.SmartToy
}

/**
 * Who answers, as the chat names them above their replies (docs/assistant-and-mcp.md): their mark and
 * name — a model by its ID, its provider's mark beside it; a model whose provider is gone, the plain one.
 */
@Composable
internal fun AnswererName(by: AssistantAnswerer, modifier: Modifier = Modifier) {
    val providers = LocalICodeSnapshot.current.assistant.settings.providers
    val (mark, name) = when (by) {
        is AssistantChoice -> (providers.firstOrNull { it.id == by.provider }?.kind?.mark() ?: Icons.Outlined.Hub) to by.model
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
        Icon(mark, contentDescription = null, tint = IcodeColors.TextPrimary, modifier = Modifier.size(IcodeSizes.IconSm))
        Text(name, style = IcodeType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
