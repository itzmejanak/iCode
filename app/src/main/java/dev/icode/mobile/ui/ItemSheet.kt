package dev.icode.mobile.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeActionSheet
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.designsystem.rememberSheetPages
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.WorkspaceNameRules
import dev.icode.mobile.domain.files.ArchiveFormats
import dev.icode.mobile.domain.files.CompressFormat
import dev.icode.mobile.domain.files.FileProperties
import dev.icode.mobile.domain.files.PropertyRow

/** The pages an item's or a selection's sheet opens within itself (docs/explorer-gaps-plan.md §3.1). */
internal enum class ItemPage { Rename, Compress, Properties }

/**
 * The folder a sheet acts in (§3.3, §3.4): its name — null at a place's top — and runtime path, the
 * names in it, and whether it ignores case, as phone storage does.
 */
internal class SheetFolder(val name: String?, val runtimePath: String, val names: Set<String>, val ignoreCase: Boolean)

/**
 * One item's sheet, or a selection's (§3.1, §3.2): [leading], [title] and [detail] over the actions
 * [actions] lists ([FileItemActions]) with the sheet's own Rename… and pages. Rename… — one name,
 * or many by a rule — Compress… and Properties open within it, ← back to the actions. [branch] is
 * a workspace's, for Properties.
 */
@Composable
internal fun ItemSheet(
    targets: List<ActionTarget>,
    folder: SheetFolder,
    title: String,
    detail: String,
    leading: @Composable () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    branch: String? = null,
    actions: (rename: () -> Unit, pages: SheetPageActions) -> List<IcodeMenuAction>,
) {
    val pages = rememberSheetPages<ItemPage>(targets.map { it.location })
    val one = targets.singleOrNull()
    when (pages.current) {
        null -> IcodeActionSheet(
            title = title,
            actions = actions({ pages.open(ItemPage.Rename) }, SheetPageActions({ pages.open(ItemPage.Compress) }, { pages.open(ItemPage.Properties) })),
            onClose = onClose,
            modifier = modifier,
            detail = detail,
            leading = leading,
        )
        ItemPage.Rename -> if (one != null) RenamePage(one, pages.back, onClose, modifier) else BatchRenamePage(targets, folder, pages.back, onClose, modifier)
        ItemPage.Compress -> CompressPage(targets, folder, title, pages.back, onClose, modifier)
        ItemPage.Properties -> PropertiesPage(targets, one?.runtimePath ?: folder.runtimePath, title, branch, pages.back, onClose, modifier)
    }
}

/** Rename… (§3.1): the new name as it is typed, checked by the same rules as everywhere; done, the sheet closes. */
@Composable
private fun RenamePage(target: ActionTarget, onBack: (() -> Unit)?, onClose: () -> Unit, modifier: Modifier) {
    val gateway = LocalICodeGateway.current
    val form = rememberRenameForm(target.location, target.name, target.folder, target.workspace, onClose)
    IcodeBottomSheet(
        title = "Rename",
        onClose = onClose,
        modifier = modifier,
        subtitle = target.name,
        onBack = onBack,
        footer = { PrimaryButton("Rename", { form.send(gateway::dispatch) }, Modifier.fillMaxWidth(), enabled = form.ready) },
    ) {
        RenameFields(form)
    }
}

/**
 * Compress… (§3.11): the format, what it keeps, and the archive's name — one item's, or for many the
 * folder's; it goes into that folder, under a free name.
 */
@Composable
private fun CompressPage(targets: List<ActionTarget>, folder: SheetFolder, subject: String, onBack: (() -> Unit)?, onClose: () -> Unit, modifier: Modifier) {
    val gateway = LocalICodeGateway.current
    val locations = targets.map { it.location }
    val one = targets.singleOrNull()
    var format by rememberSaveable(locations) { mutableStateOf(CompressFormat.Zip) }
    var name by rememberSaveable(locations) { mutableStateOf(ArchiveFormats.archiveName(one?.let { it.name to it.folder }, folder.name)) }
    val archive = name.trim() + format.extension
    val problem = WorkspaceNameRules.fileValidationError(archive)
    IcodeBottomSheet(
        title = "Compress",
        onClose = onClose,
        modifier = modifier,
        subtitle = subject,
        onBack = onBack,
        footer = {
            PrimaryButton(
                "Compress",
                {
                    gateway.dispatch(FilesIntent.Compress(locations, format, name.trim()))
                    onClose()
                },
                Modifier.fillMaxWidth(),
                enabled = problem == null,
            )
        },
    ) {
        SegmentedControl(CompressFormat.entries.map { it.label }, format.label) { label -> format = CompressFormat.entries.first { it.label == label } }
        Text(format.note, style = IcodeType.Caption)
        NameFields("Archive name", name, { name = it }, workspace = false, problem = problem)
        MonoLine(archive, style = IcodeType.MonoSmall)
    }
}

/**
 * Properties (§3.9): what is known shown at once, a folder's size — or many items' together —
 * measured while the page is open. [path] is the one item's, or the folder the many are in.
 */
@Composable
private fun PropertiesPage(targets: List<ActionTarget>, path: String, subject: String, branch: String?, onBack: (() -> Unit)?, onClose: () -> Unit, modifier: Modifier) {
    val gateway = LocalICodeGateway.current
    val locations = targets.map { it.location }
    val facts = LocalICodeSnapshot.current.explorer.facts?.takeIf { it.locations == locations }
    DisposableEffect(locations) {
        gateway.dispatch(FilesIntent.Properties(locations))
        onDispose { gateway.dispatch(FilesIntent.CloseProperties) }
    }
    IcodeBottomSheet(title = "Properties", onClose = onClose, modifier = modifier, subtitle = subject, onBack = onBack) {
        if (facts == null) {
            Text("Reading…", style = IcodeType.Caption)
        } else {
            FileProperties.rows(facts, subject, path, branch, System.currentTimeMillis()).forEach { PropertyLine(it) }
        }
    }
}

/** One of Properties' rows; a path with Copy beside it. */
@Composable
private fun PropertyLine(row: PropertyRow) {
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        DetailRow(row.label, row.value, Modifier.weight(1f))
        if (row.path) {
            IconButton(onClick = { copyText(context, row.label, row.value, "${row.label} copied", gateway::dispatch) }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy ${row.label.lowercase()}", tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconMd))
            }
        }
    }
}
