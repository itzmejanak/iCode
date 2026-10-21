package dev.icode.mobile.ui

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IndeterminateProgressTrack
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A picture in a sheet, its name for the title (docs/file-viewers-plan.md): whole, in the height the
 * sheet leaves; two fingers pinch it up to [MAX_ZOOM] times larger and move it while it is; a double
 * tap goes to [DOUBLE_TAP_ZOOM] and back. Decoded as large as the screen, off the main thread; one
 * that cannot be read is [onUnreadable].
 */
@Composable
internal fun ImageSheet(file: ViewedFile, modifier: Modifier, onUnreadable: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val image by produceState<Result<ImageBitmap>?>(null, file.location) {
        val screen = with(context.resources.displayMetrics) { max(widthPixels, heightPixels) }
        value = withContext(Dispatchers.IO) { runCatching { decodeImage(context, file.location, screen) } }
    }
    LaunchedEffect(image) { if (image?.isFailure == true) onUnreadable() }
    IcodeBottomSheet(title = file.name, onClose = onClose, modifier = modifier, scroll = false) {
        val shown = image?.getOrNull()
        if (shown == null) IndeterminateProgressTrack(Modifier.fillMaxWidth()) else ZoomablePicture(shown, file.name)
    }
}

/** [shown] fitted to its frame, scaled and moved within it. */
@Composable
private fun ColumnScope.ZoomablePicture(shown: ImageBitmap, name: String) {
    var scale by remember(shown) { mutableFloatStateOf(1f) }
    var offset by remember(shown) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // Moved no further than the picture reaches past its frame at that scale.
    fun settle(to: Offset) = Offset(
        to.x.coerceIn(-(scale - 1) * size.width / 2, (scale - 1) * size.width / 2),
        to.y.coerceIn(-(scale - 1) * size.height / 2, (scale - 1) * size.height / 2),
    )
    val zoom = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
        offset = settle(offset + panChange)
    }
    Image(
        shown,
        contentDescription = name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .aspectRatio(shown.width.toFloat() / shown.height)
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(shown) {
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > 1f) 1f else DOUBLE_TAP_ZOOM
                    offset = settle(Offset.Zero)
                })
            }
            // One finger moves the picture only while it is larger than its frame.
            .transformable(zoom, canPan = { scale > 1f })
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

/**
 * The picture at [location] — a path, or a picked file's `content://` URI — as `ImageDecoder` reads
 * it, turned the way the camera held it, its longer side at most [maxSide] pixels. It reads the
 * file: off the main thread.
 */
internal fun decodeImage(context: Context, location: String, maxSide: Int): ImageBitmap = decodeImage(
    if (location.startsWith(CONTENT_URI)) ImageDecoder.createSource(context.contentResolver, Uri.parse(location)) else ImageDecoder.createSource(File(location)),
    maxSide,
)

/** The picture [source] holds, as [decodeImage] reads a file's: also a Markdown document's bytes. Off the main thread. */
internal fun decodeImage(source: ImageDecoder.Source, maxSide: Int): ImageBitmap =
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val scale = maxSide.toFloat() / max(info.size.width, info.size.height)
        if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
    }.asImageBitmap()

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
