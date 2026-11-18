package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextButton
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.AgentChatIntent
import dev.icode.mobile.domain.AgentSignIn
import dev.icode.mobile.domain.AgentSignInWay
import dev.icode.mobile.domain.CodingAgent
import dev.icode.mobile.ui.LocalICodeGateway

/**
 * An agent asking to be signed in before the chat can send (docs/assistant-and-mcp.md): each way it
 * offers — it signs in by itself, its own sign-in runs in the chat's terminal tab, or keys typed here,
 * kept encrypted and given only to it. Keys stay in memory, never in saved state, and are not shown again.
 */
@Composable
internal fun AgentSignInCard(agent: CodingAgent, ways: List<AgentSignIn>, modifier: Modifier) {
    val gateway = LocalICodeGateway.current
    val uri = LocalUriHandler.current
    var inTerminal by remember(agent.id) { mutableStateOf(false) }
    IcodeCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
            Text("Sign in to ${agent.name}", style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
            ways.forEach { way ->
                way.description?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary)) }
                when (val how = way.way) {
                    AgentSignInWay.ByAgent -> PrimaryButton(way.name, { gateway.dispatch(AgentChatIntent.SignIn(agent.id, way.id)) }, Modifier.fillMaxWidth())
                    is AgentSignInWay.InTerminal -> SecondaryButton(
                        way.name,
                        {
                            inTerminal = true
                            gateway.dispatch(AgentChatIntent.SignIn(agent.id, way.id))
                        },
                        Modifier.fillMaxWidth(),
                    )
                    is AgentSignInWay.Keys -> {
                        val typed = remember(agent.id, way.id) { mutableStateMapOf<String, String>() }
                        how.keys.forEach { key ->
                            IcodeTextField(
                                typed[key.name].orEmpty(),
                                { typed[key.name] = it },
                                Modifier.fillMaxWidth(),
                                placeholder = key.label ?: key.name,
                                secret = key.secret,
                                literal = true,
                            )
                        }
                        val ready = how.keys.all { it.optional || !typed[it.name].isNullOrBlank() }
                        PrimaryButton(way.name, { gateway.dispatch(AgentChatIntent.SaveKeys(agent.id, typed.toMap())) }, Modifier.fillMaxWidth(), enabled = ready)
                        how.link?.let { link -> IcodeTextButton("Get a key", { uri.openUri(link) }) }
                    }
                }
            }
            if (inTerminal) IcodeTextButton("Signed in? Check again", { gateway.dispatch(AgentChatIntent.SignedIn(agent.id)) })
        }
    }
}

/** An untested agent's note, the first time a chat is answered by it (owner's choice, 2026-11-18). */
@Composable
internal fun UntestedNote(agent: CodingAgent, modifier: Modifier) {
    Text("${agent.name} isn't tested on phones yet; it may not start.", modifier, style = IcodeType.Caption.copy(color = IcodeColors.Amber))
}
