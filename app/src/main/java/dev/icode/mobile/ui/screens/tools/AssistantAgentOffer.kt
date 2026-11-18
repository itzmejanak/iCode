package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Pending
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodePopupItem
import dev.icode.mobile.designsystem.IcodePopupList
import dev.icode.mobile.designsystem.IcodeSelect
import dev.icode.mobile.designsystem.IcodeSelectVariant
import dev.icode.mobile.designsystem.JumpToLatestButton
import dev.icode.mobile.domain.AgentChat
import dev.icode.mobile.domain.AgentChatIntent
import dev.icode.mobile.domain.AgentPlanStatus
import dev.icode.mobile.domain.AgentPlanStep
import dev.icode.mobile.domain.AgentSetting
import dev.icode.mobile.domain.AgentSettings
import dev.icode.mobile.domain.AgentValue
import dev.icode.mobile.ui.LocalICodeGateway
import kotlinx.coroutines.delay

/**
 * An agent chat's chips (docs/assistant-and-mcp.md): its mode, model and thinking, as the agent offers
 * them for the chat shown, in its words; a value that lets it act without asking says so in amber.
 */
@Composable
internal fun AgentChips(agent: String, offer: AgentChat?, modifier: Modifier = Modifier) {
    val gateway = LocalICodeGateway.current
    val chips = offer?.settings.orEmpty().filter { it.chip }.sortedBy { AgentSettings.CHIPS.indexOf(it.category) }
    Row(modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        chips.forEach { setting ->
            IcodeSelect(
                options = setting.values,
                selected = setting.values.firstOrNull { it.id == setting.current } ?: AgentValue(setting.current, setting.current),
                optionLabel = { it.name },
                onSelect = { gateway.dispatch(AgentChatIntent.Set(agent, setting.id, it.id)) },
                variant = IcodeSelectVariant.Chip,
                chipRole = setting.name,
                optionDescription = { it.description },
                optionWarning = { AgentSettings.warning(agent, setting.id, it.id) },
            )
        }
    }
}

/**
 * Over the message box while it starts with "/": the agent's commands whose names begin with what is
 * typed, a tap writing one in; and with "/" alone, its other settings too — a switch flips, a choice
 * opens its values in the same list.
 */
@Composable
internal fun AgentCommandList(offer: AgentChat?, draft: String, onDraft: (String) -> Unit) {
    val gateway = LocalICodeGateway.current
    var opened by remember(offer?.agent) { mutableStateOf<String?>(null) }
    if (offer == null || !draft.startsWith("/")) return
    val agent = offer.agent
    val others = offer.settings.filterNot { it.chip }.takeIf { draft == "/" }.orEmpty()
    val choosing = others.firstOrNull { it.id == opened }
    val items = if (choosing != null) {
        listOf(IcodePopupItem(choosing.name, icon = Icons.AutoMirrored.Outlined.ArrowBack, onClick = { opened = null })) + choosing.values.map { value ->
            IcodePopupItem(
                value.name, value.description, AgentSettings.warning(agent, choosing.id, value.id),
                icon = Icons.Outlined.Check.takeIf { value.id == choosing.current },
                tint = IcodeColors.Accent,
                onClick = {
                    gateway.dispatch(AgentChatIntent.Set(agent, choosing.id, value.id))
                    opened = null
                },
            )
        }
    } else {
        AgentSettings.commandsFor(draft, offer.commands).map { command ->
            IcodePopupItem("/${command.name}", command.description, icon = Icons.Outlined.Terminal, trailing = command.hint, onClick = { onDraft("/${command.name} ") })
        } + others.map { setting -> settingItem(agent, setting, gateway::dispatch) { opened = setting.id } }
    }
    IcodePopupList(expanded = true, onDismiss = {}, items = items, focusable = false)
}

/** One of an agent's other settings in the / list: a switch flips with a tap, a choice opens its values. */
private fun settingItem(agent: String, setting: AgentSetting, dispatch: (AgentChatIntent) -> Unit, open: () -> Unit): IcodePopupItem {
    if (!setting.switch) {
        val now = setting.values.firstOrNull { it.id == setting.current }?.name ?: setting.current
        return IcodePopupItem(setting.name, icon = Icons.Outlined.Tune, trailing = now, onClick = open)
    }
    val on = setting.current.toBoolean()
    return IcodePopupItem(
        setting.name,
        warning = AgentSettings.warning(agent, setting.id, true.toString()),
        icon = Icons.Outlined.Tune,
        trailing = if (on) ON else OFF,
        onClick = { dispatch(AgentChatIntent.Set(agent, setting.id, (!on).toString())) },
    )
}

/**
 * The chat's jump-to-latest button. While an agent keeps a to-do list it shows how far it has come,
 * "2/5", and a tap opens the list over it; scrolling brings back ↓ for a few seconds (owner's choice,
 * 2026-11-18). Without a list it is ↓ as ever, shown once the user has scrolled up.
 */
@Composable
internal fun JumpOrPlan(plan: List<AgentPlanStep>, following: Boolean, scrolling: Boolean, onJump: () -> Unit, modifier: Modifier) {
    val progress = AgentSettings.progress(plan)
    var arrow by remember { mutableStateOf(false) }
    var listed by remember { mutableStateOf(false) }
    LaunchedEffect(scrolling) { if (scrolling) arrow = true }
    LaunchedEffect(arrow, scrolling) {
        if (arrow && !scrolling) {
            delay(ARROW_MILLIS)
            arrow = false
        }
    }
    val jumps = progress == null || (arrow && !following)
    Box(modifier) {
        JumpToLatestButton(
            visible = progress != null || !following,
            contentDescription = if (jumps) "Jump to the latest" else "To-do list: $progress done",
            onClick = { if (jumps) onJump() else listed = true },
            label = progress.takeUnless { jumps },
        )
        IcodePopupList(
            expanded = listed,
            onDismiss = { listed = false },
            title = "To-do",
            items = plan.map { step ->
                val (icon, tint) = when (step.status) {
                    AgentPlanStatus.Done -> Icons.Outlined.CheckCircle to IcodeColors.Green
                    AgentPlanStatus.Working -> Icons.Outlined.Pending to IcodeColors.Accent
                    AgentPlanStatus.Pending -> Icons.Outlined.RadioButtonUnchecked to IcodeColors.TextMuted
                }
                IcodePopupItem(step.text, icon = icon, tint = tint)
            },
        )
    }
}

private const val ON = "On"
private const val OFF = "Off"

/** How long ↓ stays after scrolling stops, before the progress shows again. */
private const val ARROW_MILLIS = 3_000L
