package dev.icode.mobile.ui.markdown

import android.graphics.ImageDecoder
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.domain.markdown.MarkdownDestination
import dev.icode.mobile.domain.markdown.MarkdownImageSource
import dev.icode.mobile.domain.markdown.MarkdownInline
import dev.icode.mobile.domain.markdown.MarkdownLinks
import dev.icode.mobile.domain.markdown.MarkdownTarget
import dev.icode.mobile.ui.decodeImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A paragraph of pictures alone (docs/editor-and-language-services.md): each as wide as its own pixels,
 * an HTML `width` or the page allows, decoded off the main thread and kept for when it is scrolled back
 * to. Until it is decoded, and when it cannot be — `http`, SVG, an answer's `https` — its alt text, with
 * an `https` picture's host. A tap opens it: the image viewer for a workspace file, the browser for `https`.
 */
@Composable
internal fun MarkdownPictures(pictures: List<MarkdownPicture>, context: MarkdownContext, type: MarkdownType, modifier: Modifier = Modifier) {
    val centred = LocalMarkdownAlign.current == TextAlign.Center
    BoxWithConstraints(modifier) {
        val most = maxWidth
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2, if (centred) Alignment.CenterHorizontally else Alignment.Start),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            pictures.forEach { MarkdownPicture(it, context, type, most) }
        }
    }
}

/** A picture; [link] where a tap on it goes when it is a link's, `[![shot](a.png)](b.md)`. */
internal data class MarkdownPicture(val image: MarkdownInline.Image, val link: MarkdownDestination?)

@Composable
private fun MarkdownPicture(picture: MarkdownPicture, context: MarkdownContext, type: MarkdownType, most: Dp) {
    val image = picture.image
    val href = (image.destination as? MarkdownDestination.Url)?.href
    val source = href?.let { MarkdownLinks.image(it, context.place) }
    val loads = source.takeUnless { it is MarkdownImageSource.Remote && !context.remoteImages }
    val maxSide = with(LocalDensity.current) { most.roundToPx() }
    val key = href?.let { "$maxSide:$it" }
    val bitmap by produceState(key?.let(pictures::get), key) {
        if (value == null && loads != null && key != null) {
            value = withContext(Dispatchers.IO) { runCatching { decode(loads, maxSide) }.getOrNull() }?.also { pictures.put(key, it) }
        }
    }
    val target = when (val link = picture.link) {
        is MarkdownDestination.Url -> MarkdownLinks.target(link.href, context.place)
        is MarkdownDestination.Reference -> null
        null -> when (source) {
            is MarkdownImageSource.Local -> MarkdownTarget.File(source.path, null)
            is MarkdownImageSource.Remote -> MarkdownTarget.Web(source.url)
            is MarkdownImageSource.Inline, null -> null
        }
    }
    val opens = if (target == null) Modifier else Modifier.clickable(onClickLabel = "Open the picture") { context.open(target) }
    val drawn = bitmap
    if (drawn != null) {
        val width = min(min(drawn.width.dp, image.width?.dp ?: most), most)
        Image(
            drawn,
            contentDescription = image.alt.ifBlank { null },
            modifier = Modifier.width(width).aspectRatio(drawn.width.toFloat() / drawn.height).clip(IcodeShapes.Small).then(opens),
            contentScale = ContentScale.Fit,
        )
    } else {
        Row(opens, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = type.colors.muted, modifier = Modifier.size(IcodeSizes.IconSm))
            val label = image.alt.ifBlank { href?.substringAfterLast('/') ?: (image.destination as MarkdownDestination.Reference).source }
            val host = (source as? MarkdownImageSource.Remote)?.let { runCatching { URI(it.url).host }.getOrNull() }
            Text(if (host == null) label else "$label · $host", style = type.small)
        }
    }
}

private fun decode(source: MarkdownImageSource, maxSide: Int): ImageBitmap = decodeImage(
    when (source) {
        is MarkdownImageSource.Local -> ImageDecoder.createSource(File(source.path))
        is MarkdownImageSource.Inline -> ImageDecoder.createSource(ByteBuffer.wrap(source.bytes))
        is MarkdownImageSource.Remote -> ImageDecoder.createSource(ByteBuffer.wrap(download(source.url)))
    },
    maxSide,
)

/** An `https` picture's bytes: at most [MAX_DOWNLOAD] of them, given up after the timeouts. */
private fun download(url: String): ByteArray {
    val connection = URI(url).toURL().openConnection() as HttpsURLConnection
    connection.connectTimeout = TIMEOUT_MILLIS
    connection.readTimeout = TIMEOUT_MILLIS
    connection.setRequestProperty("Accept", "image/*")
    try {
        if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
        if (connection.contentLengthLong > MAX_DOWNLOAD) throw IOException(TOO_LARGE)
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        connection.inputStream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                bytes.write(buffer, 0, read)
                if (bytes.size() > MAX_DOWNLOAD) throw IOException(TOO_LARGE)
            }
        }
        return bytes.toByteArray()
    } finally {
        connection.disconnect()
    }
}

/** Pictures decoded, by size and source, up to [PICTURES_BYTES] of them. */
private val pictures = object : LruCache<String, ImageBitmap>(PICTURES_BYTES) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * ARGB_BYTES
}

private const val PICTURES_BYTES = 32 * 1024 * 1024
private const val ARGB_BYTES = 4
private const val MAX_DOWNLOAD = 10 * 1024 * 1024
private const val TIMEOUT_MILLIS = 15_000
private const val BUFFER = 64 * 1024
private const val TOO_LARGE = "The picture is too large"
