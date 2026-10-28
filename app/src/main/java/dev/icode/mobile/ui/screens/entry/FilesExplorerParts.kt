package dev.icode.mobile.ui.screens.entry

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DriveFolderUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.icode.mobile.designsystem.IcodeCheckbox
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.FileViewer
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.files.BrowsedItem
import dev.icode.mobile.domain.files.StoragePlace
import dev.icode.mobile.ui.FileViewerState
import dev.icode.mobile.ui.LocalFileViewer
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.SharedFiles
import dev.icode.mobile.ui.ThumbnailKind
import dev.icode.mobile.ui.rememberThumbnail
import dev.icode.mobile.ui.screens.workspace.itemIcon

/** `..`: up a folder, as Back. */
@Composable
internal fun UpRow(compact: Boolean, onClick: () -> Unit) {
    FileRow(
        leading = { UpIcon(if (compact) IcodeSizes.IconMd else IcodeSizes.IconXl) },
        name = "..",
        detail = null,
        end = null,
        compact = compact,
        onClick = onClick,
    )
}

/** `..` as a grid's tile. */
@Composable
internal fun UpTile(onClick: () -> Unit) {
    FileTile(picture = { UpIcon(IcodeSizes.GridTilePicture) }, name = "..", selected = false, onClick = onClick)
}

@Composable
private fun UpIcon(size: Dp) = Icon(Icons.Outlined.DriveFolderUpload, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(size))

/**
 * One folder or file, in a workspace or on phone storage — at Private's top, a workspace; in a view,
 * gathered from anywhere — as a row with its [detail], or [compact]: one line, a smaller icon, its
 * size or count at the end. Selecting, a tap or a long press ticks it. Otherwise a tap opens it
 * ([ListingTaps.onOpen]), a tap on its icon starts selecting with it, and a long press asks for its
 * sheet.
 */
@Composable
internal fun ItemRow(item: BrowsedItem, taps: ListingTaps, compact: Boolean, detail: String) {
    val acts = itemActs(item, taps)
    val size = if (compact) IcodeSizes.IconMd else IcodeSizes.IconXl
    FileRow(
        leading = {
            if (acts.ticked != null) IcodeCheckbox(acts.ticked, onCheckedChange = null, modifier = Modifier.size(size))
            else FileIcon(item, size, Modifier.then(acts.onIcon?.let { Modifier.clickable(onClickLabel = "Select ${item.name}", onClick = it) } ?: Modifier))
        },
        name = item.name,
        detail = detail,
        end = item.changedAt,
        compact = compact,
        selected = acts.ticked == true,
        onClick = acts.onClick,
        onLongClick = acts.onLongClick,
    )
}

/** One item as a grid's tile (§3.12): its icon or thumbnail, and its name on two lines; ticked, in selection. */
@Composable
internal fun ItemTile(item: BrowsedItem, taps: ListingTaps) {
    val acts = itemActs(item, taps)
    FileTile(
        picture = {
            Box(contentAlignment = Alignment.TopEnd) {
                FileIcon(item, IcodeSizes.GridTilePicture, Modifier.then(acts.onIcon?.let { Modifier.clickable(onClickLabel = "Select ${item.name}", onClick = it) } ?: Modifier))
                if (acts.ticked != null) IcodeCheckbox(acts.ticked, onCheckedChange = null)
            }
        },
        name = item.name,
        selected = acts.ticked == true,
        onClick = acts.onClick,
        onLongClick = acts.onLongClick,
    )
}

/** What [item]'s row or tile does with a tap, a long press, and a tap on its icon; whether it is ticked. */
private class ItemActs(val ticked: Boolean?, val onClick: () -> Unit, val onLongClick: (() -> Unit)?, val onIcon: (() -> Unit)?)

private fun itemActs(item: BrowsedItem, taps: ListingTaps): ItemActs {
    val ticked = taps.selection?.let { item.location in it }
    val tick = taps.onTick?.let { { it(item) } }
    return ItemActs(
        ticked = ticked,
        onClick = if (ticked != null && tick != null) tick else ({ taps.onOpen(item) }),
        onLongClick = if (ticked != null) tick else taps.onActions?.let { { it(item) } },
        onIcon = tick.takeIf { ticked == null },
    )
}

/** [item]'s picture: a thumbnail of a picture, a video or an app package once made (§3.12); else its type's icon. */
@Composable
internal fun FileIcon(item: BrowsedItem, size: Dp, modifier: Modifier = Modifier) {
    val thumbnail = rememberThumbnail(item.location, ThumbnailKind.of(item.name).takeUnless { item.folder }, item.changedAt)
    if (thumbnail != null) {
        Image(thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(IcodeShapes.Small))
    } else {
        val (icon, tint) = iconOf(item)
        Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
    }
}

/**
 * What opening an item does, wherever it is listed: a folder goes into the list; a file of a
 * workspace into the editor, which hands one it cannot show to another app (docs/open-with-plan.md);
 * one on phone storage, having no editor, into iCode's viewer when it has one for it
 * (docs/file-viewers-plan.md), and into Recent. [inIcode] is null for a file with nowhere in iCode
 * to open, which a tap ([tap]) hands to another app.
 */
internal class Opening(
    private val context: Context,
    private val gateway: ICodeGateway,
    private val viewer: FileViewerState,
    private val browse: (String) -> Unit,
) {
    fun inIcode(item: BrowsedItem): (() -> Unit)? = when {
        item.folder -> { { browse(item.location) } }
        item.place == StoragePlace.Private -> { { gateway.dispatch(ICodeIntent.OpenFile(item.location)) } }
        else -> FileViewer.of(item.name)?.let { kind ->
            {
                viewer.show(item.location, item.name, kind)
                gateway.dispatch(FilesIntent.Opened(item.location))
            }
        }
    }

    fun tap(item: BrowsedItem) = (inIcode(item) ?: { SharedFiles.open(context, item.location, gateway::dispatch) }).invoke()
}

/** [Opening] on this screen's own: a folder opened goes to [browse]. */
@Composable
internal fun opening(browse: (String) -> Unit) = Opening(LocalContext.current, LocalICodeGateway.current, LocalFileViewer.current, browse)

/** A folder's icon in the accent; a file's by its type. */
@Composable
internal fun iconOf(item: BrowsedItem): Pair<ImageVector, Color> = itemIcon(item.name, item.folder)

/**
 * A flat row, as ZArchiver's: [leading] — the type's icon or thumbnail, or a tick — then the name
 * over its detail, and when it changed ([end]) at the end; [compact], one line, the detail at the end.
 * Ticked, it is drawn square and edge to edge, so rows ticked together are one band, as the IDE tree's.
 */
@Composable
private fun FileRow(
    leading: @Composable () -> Unit,
    name: String,
    detail: String?,
    end: Long?,
    compact: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    IcodeListRow(
        selected = selected,
        rowShape = RectangleShape,
        inset = IcodeSpacing.Gutter,
        onClick = onClick,
        onLongClick = onLongClick,
        leading = leading,
        compact = compact,
        content = {
            Column(Modifier.weight(1f).then(if (compact) Modifier.padding(vertical = IcodeSpacing.Space2) else Modifier)) {
                Text(name, style = IcodeType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact) detail?.let { Text(it, style = IcodeType.Caption, maxLines = 1) }
            }
        },
        trailing = {
            val said = if (compact) detail else end?.let(RelativeTime::since)
            said?.let { Text(it, style = IcodeType.Caption) }
        },
    )
}

/** A grid's tile: [picture] over the name on two lines, ticked when [selected]. */
@Composable
private fun FileTile(picture: @Composable () -> Unit, name: String, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .padding(IcodeSpacing.Space1)
            .clip(IcodeShapes.Small)
            .background(if (selected) IcodeColors.SurfaceElevated else Color.Transparent)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .padding(IcodeSpacing.Space2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        picture()
        Text(name, style = IcodeType.Caption.copy(color = IcodeColors.TextPrimary), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
