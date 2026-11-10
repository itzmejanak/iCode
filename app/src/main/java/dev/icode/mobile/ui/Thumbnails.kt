package dev.icode.mobile.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.media.ThumbnailUtils
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import dev.icode.mobile.domain.FileFamily
import dev.icode.mobile.domain.FileType
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.files.FilesRules
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** What a thumbnail is made from (docs/explorer-gaps-plan.md §3.12): a picture, an SVG drawing, a video's frame, or an app package's own icon. */
internal enum class ThumbnailKind {
    Picture,
    Drawing,
    Video,
    App,
    ;

    companion object {
        /** A file named [name] of [type]'s kind — [FileTypes]' answer, by its bytes too where a scan read them. */
        fun of(name: String, type: FileType = FileTypes.of(name)): ThumbnailKind? = when (type.family) {
            FileFamily.Image -> Picture
            FileFamily.Video -> Video
            FileFamily.App -> App.takeIf { FilesRules.extension(name) == APK }
            else -> Drawing.takeIf { type.id == XML && FilesRules.extension(name) == SVG }
        }

        private const val APK = "apk"
        private const val XML = "xml"
        private const val SVG = "svg"
    }
}

/**
 * Small pictures of files (§3.12), by Android's own means: `ImageDecoder` for a picture, turned the
 * way the camera held it ([decodeImage]); AndroidSVG for an SVG, as the file icons are drawn;
 * `ThumbnailUtils` for a video's frame; `PackageManager` for an app package's icon. Made off the main
 * thread, a few at a time however many are on screen, and kept in memory by where the file is and when
 * it changed — and so is a file that gave none, so it is not decoded again each time it comes into view.
 */
internal object Thumbnails {
    private val made = LruCache<String, ImageBitmap>(KEPT)
    private val failed = LruCache<String, Boolean>(KEPT_FAILED)
    private val making = Semaphore(AT_ONCE)

    fun keyOf(location: String, changedAt: Long) = "$location@$changedAt"

    fun cached(key: String): ImageBitmap? = made.get(key)

    /** Whether [key]'s file gave no thumbnail when last tried: as it was then, it gives none. */
    fun failed(key: String): Boolean = failed.get(key) == true

    /** [location]'s thumbnail, kept under [key]; null when it cannot be made. Reads the file: off the main thread. */
    suspend fun make(context: Context, location: String, kind: ThumbnailKind, key: String): ImageBitmap? = making.withPermit {
        made.get(key)?.let { return@withPermit it }
        if (failed(key)) return@withPermit null
        val image = runCatching {
            when (kind) {
                ThumbnailKind.Picture -> decodeImage(context, location, SIDE_PX)
                ThumbnailKind.Drawing -> drawing(location)
                ThumbnailKind.Video -> ThumbnailUtils.createVideoThumbnail(File(location), Size(SIDE_PX, SIDE_PX), null).asImageBitmap()
                ThumbnailKind.App -> appIcon(context, location)
            }
        }.getOrNull()
        if (image != null) made.put(key, image) else failed.put(key, true)
        image
    }

    /** An SVG drawn at the thumbnail's size, its shape kept; none for one larger than [MAX_DRAWING_BYTES], which is no icon. */
    private fun drawing(location: String): ImageBitmap? {
        val file = File(location)
        if (file.length() > MAX_DRAWING_BYTES) return null
        val svg = file.inputStream().use { SVG.getFromInputStream(it) }
        val box = svg.documentViewBox ?: RectF(0f, 0f, svg.documentWidth, svg.documentHeight)
        if (box.width() <= 0f || box.height() <= 0f) return null
        val scale = SIDE_PX / maxOf(box.width(), box.height())
        val bitmap = Bitmap.createBitmap((box.width() * scale).toInt().coerceAtLeast(1), (box.height() * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        svg.renderToCanvas(Canvas(bitmap), RenderOptions().viewPort(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()))
        return bitmap.asImageBitmap()
    }

    private fun appIcon(context: Context, location: String): ImageBitmap? {
        val packages = context.packageManager
        val app = packages.getPackageArchiveInfo(location, 0)?.applicationInfo ?: return null
        // An archive's resources are read from where it lies.
        app.sourceDir = location
        app.publicSourceDir = location
        val icon = app.loadIcon(packages)
        val side = icon.intrinsicWidth.takeIf { it > 0 } ?: SIDE_PX / 2
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, side, side)
        icon.draw(Canvas(bitmap))
        return bitmap.asImageBitmap()
    }

    /** Sides at most this many pixels: sharp at a grid's tile and a chat's picture alike. */
    private const val SIDE_PX = 384
    private const val KEPT = 200
    private const val KEPT_FAILED = 1000
    private const val AT_ONCE = 4
    private const val MAX_DRAWING_BYTES = 1L shl 20
}

/** [location]'s thumbnail of [kind] — none for no kind — as it is when [changedAt]; null until it is made, or when it cannot be. */
@Composable
internal fun rememberThumbnail(location: String, kind: ThumbnailKind?, changedAt: Long = 0): ImageBitmap? {
    val context = LocalContext.current
    val key = Thumbnails.keyOf(location, changedAt)
    val image by produceState(kind?.let { Thumbnails.cached(key) }, key, kind) {
        if (kind != null && value == null && !Thumbnails.failed(key)) value = withContext(Dispatchers.IO) { Thumbnails.make(context, location, kind, key) }
    }
    return image
}
