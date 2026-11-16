package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.DiffBand
import dev.icode.mobile.designsystem.DiffKind
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeLoadingLine
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.PinchToScaleText
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.designsystem.ThreeColumnActions
import dev.icode.mobile.domain.BusyAction
import dev.icode.mobile.domain.GitConflictChoice
import dev.icode.mobile.domain.GitConflictFile
import dev.icode.mobile.domain.GitConflictSection
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.domain.isBusy
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.syntax.SyntaxColouring
import dev.icode.mobile.ui.syntax.SyntaxPlace
import dev.icode.mobile.ui.syntax.conflictSources
import dev.icode.mobile.ui.syntax.languageOf
import dev.icode.mobile.ui.syntax.rememberSyntaxLines

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
    // Until the file asked for is read, neither the last one nor "no longer conflicted" is shown for it.
    val opening = state.isBusy(BusyAction.OpenConflict)
    val conflict = state.activeConflict.takeUnless { opening }
    if (conflict == null) {
        ConflictUnavailable(actions, opening)
        return
    }
    // The file is rewritten by each choice: one at a time, the one tapped working.
    val resolving = state.isBusy(BusyAction.Conflict, conflict.path)
    var chosen by remember(conflict.path) { mutableStateOf<Pair<Int, GitConflictChoice>?>(null) }
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
                ConflictedFile(conflict, chosen.takeIf { resolving }) { section, choice ->
                    chosen = section.startLine to choice
                    gateway.dispatch(ICodeIntent.ResolveConflict(conflict.path, section.startLine, choice))
                }
            }
        }
        ConflictFooter(
            conflict = conflict,
            enabled = !resolving,
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
 * Code is coloured as the file's language, each side as the file it would make
 * (docs/editor-and-language-services.md).
 */
@Composable
private fun ConflictedFile(conflict: GitConflictFile, working: Pair<Int, GitConflictChoice>?, onChoice: (GitConflictSection, GitConflictChoice) -> Unit) {
    val sources = remember(conflict) { conflictSources(conflict) }
    val colouring = rememberSyntaxLines(languageOf(conflict.path.substringAfterLast('/'), conflict.lines.firstOrNull().orEmpty()), sources.texts)
    var line = 0
    while (line < conflict.lines.size) {
        val section = conflict.sections.firstOrNull { it.startLine == line }
        if (section == null) {
            MonoLine(
                colouring.shown(sources.context[line], conflict.lines[line]),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IcodeSpacing.Space3),
                color = IcodeColors.TextMuted,
                maxLines = 1,
            )
            line++
        } else {
            val choosing = working?.let { (start, choice) -> if (start == section.startLine) choice else null }
            ConflictSection(section, sources.sides[section.startLine], colouring, choosing, working != null) { choice -> onChoice(section, choice) }
            line = section.endLine + 1
        }
    }
}

/** [working] is the choice being written for this section; while [anyWorking], no other is taken. */
@Composable
private fun ConflictSection(
    section: GitConflictSection,
    places: Pair<List<SyntaxPlace>, List<SyntaxPlace>>?,
    colouring: SyntaxColouring,
    working: GitConflictChoice?,
    anyWorking: Boolean,
    onChoice: (GitConflictChoice) -> Unit,
) {
    DiffBand("<<<<<<< ${section.currentLabel} (Current Change)", DiffKind.ConflictCurrent)
    SideLines(section.current, places?.first, colouring)
    DiffBand("=======", DiffKind.Separator)
    SideLines(section.incoming, places?.second, colouring)
    DiffBand(">>>>>>> ${section.incomingLabel} (Incoming Change)", DiffKind.ConflictIncoming)
    ThreeColumnActions(
        modifier = Modifier.padding(IcodeSpacing.Space3),
        first = { ChoiceButton("Current", GitConflictChoice.Current, working, anyWorking, onChoice) },
        second = { ChoiceButton("Incoming", GitConflictChoice.Incoming, working, anyWorking, onChoice) },
        third = { ChoiceButton("Both", GitConflictChoice.Both, working, anyWorking, onChoice) },
    )
}

@Composable
private fun ChoiceButton(label: String, choice: GitConflictChoice, working: GitConflictChoice?, anyWorking: Boolean, onChoice: (GitConflictChoice) -> Unit) =
    SecondaryButton(label, { onChoice(choice) }, Modifier.fillMaxWidth(), enabled = !anyWorking, busy = working == choice)

/** An empty side is stated rather than drawn as nothing, which reads as a bug. */
@Composable
private fun SideLines(lines: List<String>, places: List<SyntaxPlace>?, colouring: SyntaxColouring) {
    if (lines.isEmpty()) {
        MonoLine(
            "(empty)",
            modifier = Modifier.padding(horizontal = IcodeSpacing.Space3, vertical = IcodeSpacing.Space1),
            color = IcodeColors.TextMuted,
            style = IcodeType.MonoSmall,
        )
        return
    }
    lines.forEachIndexed { at, text ->
        MonoLine(
            colouring.shown(places?.getOrNull(at), text),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = IcodeSpacing.Space3, vertical = IcodeSpacing.Micro),
            color = IcodeColors.TextPrimary,
            maxLines = 1,
        )
    }
}

/** A blank line is one space, so it keeps its height. */
private fun SyntaxColouring.shown(place: SyntaxPlace?, text: String) = if (text.isBlank()) AnnotatedString(" ") else line(place, text)

@Composable
private fun ConflictFooter(conflict: GitConflictFile, enabled: Boolean, onMarkResolved: () -> Unit) {
    Column {
        NoticeCard(
            tone = if (conflict.isResolved) IcodeTone.Success else IcodeTone.Warning,
            modifier = Modifier.fillMaxWidth().padding(horizontal = IcodeSpacing.Gutter),
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
            Modifier.fillMaxWidth().padding(IcodeSpacing.Gutter),
            enabled = conflict.isResolved && enabled,
        )
    }
}

/** While the file is read, [opening]; else reached only if the file stopped being conflicted while the screen was open. */
@Composable
private fun ConflictUnavailable(actions: NavigationActions, opening: Boolean) {
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Conflict", onBack = actions.back)
        if (opening) {
            IcodeLoadingLine("Reading the conflicts…", Modifier.padding(IcodeSpacing.Gutter))
        } else {
            NoticeCard(tone = IcodeTone.Info, modifier = Modifier.fillMaxWidth().padding(IcodeSpacing.Gutter)) {
                StatusLabel("This file is no longer conflicted.", IcodeColors.TextSecondary)
            }
        }
    }
}
