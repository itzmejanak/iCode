package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.DiffBand
import dev.icode.mobile.designsystem.DiffKind
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PinchToScaleText
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.designsystem.ThreeColumnActions
import dev.icode.mobile.domain.GitConflictChoice
import dev.icode.mobile.domain.GitConflictFile
import dev.icode.mobile.domain.GitConflictSection
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Merge conflict resolution on the real conflicted file.
 *
 * The file is shown as Git left it — its own lines and its own marker blocks —
 * and a choice rewrites the file on disk. Marking the file resolved stays a
 * separate, explicit step (`git add`), so a half-finished file is never staged
 * by accident.
 */
@Composable
fun ConflictResolutionScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val conflict = state.activeConflict
    if (conflict == null) {
        ConflictUnavailable(actions)
        return
    }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            conflict.path.substringAfterLast('/'),
            subtitle = conflict.path,
            onBack = {
                gateway.dispatch(ICodeIntent.CloseConflict)
                actions.back()
            },
            actions = {
                StatusLabel(
                    if (conflict.isResolved) "Resolved" else countOf(conflict.sections.size, "conflict"),
                    if (conflict.isResolved) IcodeColors.Green else IcodeColors.Error,
                )
            },
        )
        PinchToScaleText(Modifier.weight(1f)) {
            // Scrolls: a long file, or one zoomed in, runs past the screen.
            CodeSurface(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                padding = PaddingValues(vertical = IcodeSpacing.Space3),
            ) {
                ConflictedFile(conflict) { section, choice ->
                    gateway.dispatch(ICodeIntent.ResolveConflict(conflict.path, section.startLine, choice))
                }
            }
        }
        ConflictFooter(
            conflict = conflict,
            onMarkResolved = {
                gateway.dispatch(ICodeIntent.MarkConflictResolved(conflict.path))
                gateway.dispatch(ICodeIntent.CloseConflict)
                actions.back()
            },
        )
    }
}

/**
 * The file, top to bottom.
 *
 * Lines outside a conflict are context; each marker block becomes a pair of
 * bands with the choices attached, so the user acts where they are reading.
 */
@Composable
private fun ConflictedFile(conflict: GitConflictFile, onChoice: (GitConflictSection, GitConflictChoice) -> Unit) {
    var line = 0
    while (line < conflict.lines.size) {
        val section = conflict.sections.firstOrNull { it.startLine == line }
        if (section == null) {
            MonoLine(
                conflict.lines[line].ifBlank { " " },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IcodeSpacing.Space3),
                color = IcodeColors.TextMuted,
                maxLines = 1,
            )
            line++
        } else {
            ConflictSection(section) { choice -> onChoice(section, choice) }
            line = section.endLine + 1
        }
    }
}

@Composable
private fun ConflictSection(section: GitConflictSection, onChoice: (GitConflictChoice) -> Unit) {
    DiffBand("<<<<<<< ${section.currentLabel} (Current Change)", DiffKind.ConflictCurrent)
    SideLines(section.current)
    DiffBand("=======", DiffKind.Separator)
    SideLines(section.incoming)
    DiffBand(">>>>>>> ${section.incomingLabel} (Incoming Change)", DiffKind.ConflictIncoming)
    ThreeColumnActions(
        modifier = Modifier.padding(IcodeSpacing.Space3),
        first = {
            SecondaryButton("Current", { onChoice(GitConflictChoice.Current) }, Modifier.fillMaxWidth())
        },
        second = {
            SecondaryButton("Incoming", { onChoice(GitConflictChoice.Incoming) }, Modifier.fillMaxWidth())
        },
        third = {
            SecondaryButton("Both", { onChoice(GitConflictChoice.Both) }, Modifier.fillMaxWidth())
        },
    )
}

/** An empty side is stated rather than drawn as nothing, which reads as a bug. */
@Composable
private fun SideLines(lines: List<String>) {
    if (lines.isEmpty()) {
        MonoLine(
            "(empty)",
            modifier = Modifier.padding(horizontal = IcodeSpacing.Space3, vertical = IcodeSpacing.Space1),
            color = IcodeColors.TextMuted,
            style = IcodeType.MonoSmall,
        )
        return
    }
    lines.forEach { text ->
        MonoLine(
            text.ifBlank { " " },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = IcodeSpacing.Space3, vertical = IcodeSpacing.Micro),
            color = IcodeColors.TextPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun ConflictFooter(conflict: GitConflictFile, onMarkResolved: () -> Unit) {
    Column {
        NoticeCard(
            tone = if (conflict.isResolved) IcodeTone.Success else IcodeTone.Warning,
            modifier = Modifier.fillMaxWidth().padding(horizontal = IcodeSpacing.Space4),
        ) {
            StatusLabel(
                if (conflict.isResolved) {
                    "No markers left in this file. Mark it resolved to stage it."
                } else {
                    "Resolve every conflict before marking the file resolved."
                },
                if (conflict.isResolved) IcodeColors.Green else IcodeColors.Amber,
            )
        }
        PrimaryButton(
            "Mark as Resolved",
            onMarkResolved,
            Modifier.fillMaxWidth().padding(IcodeSpacing.Space4),
            enabled = conflict.isResolved,
        )
    }
}

/** Reached only if the file stopped being conflicted while the screen was open. */
@Composable
private fun ConflictUnavailable(actions: NavigationActions) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
    ) {
        IcodeTopBar("Conflict", onBack = actions.back)
        NoticeCard(tone = IcodeTone.Info, modifier = Modifier.fillMaxWidth().padding(IcodeSpacing.Space4)) {
            StatusLabel("This file is no longer conflicted.", IcodeColors.TextSecondary)
        }
    }
}
