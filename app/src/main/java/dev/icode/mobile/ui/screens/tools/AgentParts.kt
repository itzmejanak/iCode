package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeLoadingLine
import dev.icode.mobile.designsystem.IcodeSelect
import dev.icode.mobile.designsystem.IcodeSelectVariant
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.AgentChat
import dev.icode.mobile.domain.AgentIntent
import dev.icode.mobile.domain.AgentLink
import dev.icode.mobile.domain.AgentMenuItem
import dev.icode.mobile.domain.AgentOption
import dev.icode.mobile.domain.AgentPlanItem
import dev.icode.mobile.domain.AgentRules
import dev.icode.mobile.domain.AgentSignIn
import dev.icode.mobile.domain.AssistantApproval
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.ui.LocalICodeGateway

// What a coding agent offers the Assistant page (docs/assistant-and-mcp.md): its settings as the
// composer's chips, its commands under "/", its plan, its sign-in and the links it asks to open.

/** An agent's own answers to its approval, rejecting ones first, its first allowing one the main one. */
@Composable
internal fun AgentApprovalAnswers(approval: AssistantApproval, modifier: Modifier) {
    val gateway = LocalICodeGateway.current
    val (allows, rejects) = approval.choices.partition { it.kind.allows }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2, Alignment.End)) {
        (rejects + allows.drop(1)).forEach { choice ->
            SecondaryButton(choice.label, { gateway.dispatch(AgentIntent.AnswerApproval(approval.call.id, choice.id)) }, compact = true)
        }
        allows.firstOrNull()?.let { choice -> PrimaryButton(choice.label, { gateway.dispatch(AgentIntent.AnswerApproval(approval.call.id, choice.id)) }, compact = true) }
    }
}

/** The agent's mode, model and effort, each a chip of its choices, as the composer shows the built-in assistant's. */
@Composable
internal fun AgentChips(chat: AgentChat?) {
    val gateway = LocalICodeGateway.current
    AgentRules.chips(chat?.options.orEmpty()).forEach { option ->
        IcodeSelect(
            options = option.choices,
            selected = option.choices.firstOrNull { it.value == option.current } ?: AgentOption.Select.Choice(option.current, option.currentName),
            optionLabel = { it.name },
            onSelect = { gateway.dispatch(AgentIntent.SetOption(option.id, it.value)) },
            label = option.currentName,
            variant = IcodeSelectVariant.Chip,
            chipRole = option.name,
            optionDescription = { choice -> listOfNotNull(choice.group, choice.description).joinToString(" · ").ifEmpty { null } },
        )
    }
}

/**
 * The "/" menu, above the composer while the message is "/" and a word: the agent's commands, typed into
 * the message by a tap, then its other settings, each changed in place.
 */
@Composable
internal fun AgentSlashMenu(chat: AgentChat, query: String, modifier: Modifier, onCommand: (String) -> Unit) {
    val gateway = LocalICodeGateway.current
    val items = AgentRules.menu(query, chat.commands, chat.options)
    if (items.isEmpty()) return
    IcodeCard(modifier.fillMaxWidth(), padding = PaddingValues(IcodeSpacing.Space1)) {
        Column(Modifier.heightIn(max = IcodeSizes.MenuWidth).verticalScroll(rememberScrollState())) {
            items.forEach { item ->
                when (item) {
                    is AgentMenuItem.Command -> IcodeListRow(
                        title = "/${item.command.name}",
                        subtitle = listOfNotNull(item.command.description.ifBlank { null }, item.command.hint).joinToString(" · ").ifEmpty { null },
                        titleStyle = IcodeType.Mono,
                        onClick = { onCommand(AgentRules.typed(item.command)) },
                    )
                    is AgentMenuItem.Option -> when (val option = item.option) {
                        is AgentOption.Toggle -> IcodeListRow(
                            title = option.name,
                            subtitle = option.description,
                            onClick = { gateway.dispatch(AgentIntent.SetToggle(option.id, !option.on)) },
                            trailing = { IcodeToggle(option.on) { gateway.dispatch(AgentIntent.SetToggle(option.id, it)) } },
                        )
                        is AgentOption.Select -> Row(Modifier.fillMaxWidth().padding(horizontal = IcodeSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
                            Text(option.name, style = IcodeType.Body, modifier = Modifier.weight(1f))
                            IcodeSelect(
                                options = option.choices,
                                selected = option.choices.firstOrNull { it.value == option.current } ?: AgentOption.Select.Choice(option.current, option.currentName),
                                optionLabel = { it.name },
                                onSelect = { gateway.dispatch(AgentIntent.SetOption(option.id, it.value)) },
                                label = option.currentName,
                                variant = IcodeSelectVariant.Chip,
                                chipRole = option.name,
                                optionDescription = { it.description },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The agent's plan, one line above the composer: how far it is, and the step it is on. */
@Composable
internal fun AgentPlanRow(plan: List<AgentPlanItem>, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().minimumInteractiveComponentSize().clickable(onClickLabel = "Show the plan", onClick = onOpen).padding(horizontal = IcodeSpacing.Gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
    ) {
        Icon(Icons.Outlined.Checklist, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconSm))
        Text("Plan · ${AgentRules.planProgress(plan)}", style = IcodeType.Caption)
        AgentRules.planStep(plan)?.let {
            Text(it.text, style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        } ?: Spacer(Modifier.weight(1f))
        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
    }
}

/** Every step of the agent's plan, as it last sent it: done, in hand, or to do. */
@Composable
internal fun AgentPlanSheet(plan: List<AgentPlanItem>, modifier: Modifier, onClose: () -> Unit) {
    IcodeBottomSheet(title = "Plan", onClose = onClose, modifier = modifier, subtitle = AgentRules.planProgress(plan)) {
        plan.forEach { item ->
            val (icon, tint) = when (item.status) {
                AgentPlanItem.Status.Completed -> Icons.Outlined.CheckCircle to IcodeColors.Green
                AgentPlanItem.Status.InProgress -> Icons.Outlined.Schedule to IcodeColors.Accent
                AgentPlanItem.Status.Pending -> Icons.Outlined.RadioButtonUnchecked to IcodeColors.TextMuted
            }
            IcodeListRow(
                title = item.text,
                subtitle = item.priority.takeIf { it == AgentPlanItem.Priority.High }?.let { "High priority" },
                titleColor = if (item.status == AgentPlanItem.Status.Completed) IcodeColors.TextSecondary else IcodeColors.TextPrimary,
                leading = { Icon(icon, contentDescription = item.status.name, tint = tint, modifier = Modifier.size(IcodeSizes.IconSm)) },
            )
        }
    }
}

/**
 * The agent asks to sign in: each way it offers, as a button. A terminal login opens in the chat's tab
 * ([onTerminal] shows it), and Done says it is finished; keys are entered in its settings ([onKeys]).
 */
@Composable
internal fun AgentSignInCard(chat: AgentChat, agent: String, modifier: Modifier, onTerminal: () -> Unit, onKeys: () -> Unit) {
    val gateway = LocalICodeGateway.current
    NoticeCard(IcodeTone.Warning, modifier.fillMaxWidth(), title = "Sign in to $agent", icon = Icons.AutoMirrored.Outlined.Login) {
        Text("$agent asks to sign in before it goes on.", style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2, Alignment.End)) {
            if (chat.signingIn != null && chat.signIn.firstOrNull { it.id == chat.signingIn }?.kind == AgentSignIn.Kind.Terminal) {
                PrimaryButton("Done", { gateway.dispatch(AgentIntent.SignedIn()) }, compact = true)
            }
            chat.signIn.forEach { method ->
                SecondaryButton(method.name, {
                    when (method.kind) {
                        AgentSignIn.Kind.Keys -> onKeys()
                        AgentSignIn.Kind.Terminal -> gateway.dispatch(AgentIntent.SignIn(method.id)).also { onTerminal() }
                        AgentSignIn.Kind.Agent -> gateway.dispatch(AgentIntent.SignIn(method.id))
                    }
                }, compact = true, busy = chat.signingIn == method.id && method.kind == AgentSignIn.Kind.Agent)
            }
        }
    }
}

/** A link the agent asked to open: named by its host, opened only when the user taps Open. */
@Composable
internal fun AgentLinkCard(link: AgentLink, agent: String, modifier: Modifier) {
    val gateway = LocalICodeGateway.current
    IcodeCard(modifier.fillMaxWidth(), padding = PaddingValues(IcodeSpacing.Space2)) {
        Column(Modifier.padding(horizontal = IcodeSpacing.Space1), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                Icon(Icons.Outlined.OpenInBrowser, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconSm))
                Text("$agent asks to open ${link.host}", style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            }
            Text(link.url, style = IcodeType.MonoSmall.copy(color = IcodeColors.TextSecondary), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2, Alignment.End)) {
                SecondaryButton("Not Now", { gateway.dispatch(AgentIntent.LinkTaken) }, compact = true)
                PrimaryButton("Open", {
                    gateway.dispatch(ICodeIntent.OpenExternalUrl(link.url))
                    gateway.dispatch(AgentIntent.LinkTaken)
                }, compact = true)
            }
        }
    }
}

/** The chat being read back from its agent, or why it could not be. */
@Composable
internal fun AgentChatLine(chat: AgentChat, agent: String, modifier: Modifier) {
    when {
        chat.loading -> IcodeLoadingLine("Reading the chat back from $agent…", modifier.padding(horizontal = IcodeSpacing.Gutter))
        chat.failure != null && chat.signIn.isEmpty() -> NoticeCard(IcodeTone.Error, modifier.fillMaxWidth(), title = "$agent couldn't open this chat") {
            Text(chat.failure, style = IcodeType.MonoSmall.copy(color = IcodeColors.TextSecondary), maxLines = 8, overflow = TextOverflow.Ellipsis)
        }
    }
}
