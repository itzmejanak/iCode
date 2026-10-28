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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

/** The pages an item's sheet opens within itself (docs/explorer-gaps-plan.md §3.1). */
internal enum class ItemPage { Rename, Compress, Properties }

/**
 * One item's sheet (§3.1): its [icon], name and [detail] over its actions, which [actions] lists
 * ([FileItemActions]) with the sheet's own Rename… and pages. Rename…, Compress… and Properties open
 * within it, ← back to the actions. [branch] is a workspace's, for Properties.
 */
@Composable
internal fun ItemSheet(
    target: ActionTarget,
    detail: String,
    icon: ImageVector,
    tint: Color,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    branch: String? = null,
    actions: (rename: () -> Unit, pages: SheetPageActions) -> List<IcodeMenuAction>,
) {
    val pages = rememberSheetPages<ItemPage>(target.location)
    when (pages.current) {
        null -> IcodeActionSheet(
            title = target.name,
            actions = actions({ pages.open(ItemPage.Rename) }, SheetPageActions({ pages.open(ItemPage.Compress) }, { pages.open(ItemPage.Properties) })),
            onClose = onClose,
            modifier = modifier,
            detail = detail,
            leading = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(IcodeSizes.IconXl)) },
        )
        ItemPage.Rename -> RenamePage(target, pages.back, onClose, modifier)
        ItemPage.Compress -> CompressPage(target, pages.back, onClose, modifier)
        ItemPage.Properties -> PropertiesPage(target, branch, pages.back, onClose, modifier)
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

/** Compress… (§3.11): the format, what it keeps, and the archive's name; it goes beside the item, under a free name. */
@Composable
private fun CompressPage(target: ActionTarget, onBack: (() -> Unit)?, onClose: () -> Unit, modifier: Modifier) {
    val gateway = LocalICodeGateway.current
    var format by rememberSaveable(target.location) { mutableStateOf(CompressFormat.Zip) }
    var name by rememberSaveable(target.location) { mutableStateOf(ArchiveFormats.archiveName(target.name to target.folder, null)) }
    val archive = name.trim() + format.extension
    val problem = WorkspaceNameRules.fileValidationError(archive)
    IcodeBottomSheet(
        title = "Compress",
        onClose = onClose,
        modifier = modifier,
        subtitle = target.name,
        onBack = onBack,
        footer = {
            PrimaryButton(
                "Compress",
                {
                    gateway.dispatch(FilesIntent.Compress(listOf(target.location), format, name.trim()))
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

/** Properties (§3.9): what is known shown at once, a folder's size measured while the page is open. */
@Composable
private fun PropertiesPage(target: ActionTarget, branch: String?, onBack: (() -> Unit)?, onClose: () -> Unit, modifier: Modifier) {
    val gateway = LocalICodeGateway.current
    val facts = LocalICodeSnapshot.current.explorer.facts?.takeIf { it.locations == listOf(target.location) }
    DisposableEffect(target.location) {
        gateway.dispatch(FilesIntent.Properties(listOf(target.location)))
        onDispose { gateway.dispatch(FilesIntent.CloseProperties) }
    }
    IcodeBottomSheet(title = "Properties", onClose = onClose, modifier = modifier, subtitle = target.name, onBack = onBack) {
        if (facts == null) {
            Text("Reading…", style = IcodeType.Caption)
        } else {
            FileProperties.rows(facts, target.name, target.runtimePath, branch, System.currentTimeMillis()).forEach { PropertyLine(it) }
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
