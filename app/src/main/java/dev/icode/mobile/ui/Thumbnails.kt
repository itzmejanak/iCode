package dev.icode.mobile.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.ThumbnailUtils
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.domain.files.FileCategories
import dev.icode.mobile.domain.files.FileCategory
import dev.icode.mobile.domain.files.FilesRules
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** What a thumbnail is made from (docs/explorer-gaps-plan.md §3.12): a picture, a video's frame, or an app package's own icon. */
internal enum class ThumbnailKind {
    Picture,
    Video,
    App,
    ;

    companion object {
        /** [name]'s kind, by its category; none for an SVG, which Android's decoder cannot read. */
        fun of(name: String): ThumbnailKind? = when (FileCategories.of(name)) {
            FileCategory.Images -> Picture.takeUnless { FilesRules.extension(name) == "svg" }
            FileCategory.Videos -> Video
            FileCategory.Apps -> App.takeIf { FilesRules.extension(name) == "apk" }
            else -> null
        }
    }
}

/**
 * Small pictures of files (§3.12), by Android's own means: `ImageDecoder` for a picture, turned the
 * way the camera held it ([decodeImage]); `ThumbnailUtils` for a video's frame; `PackageManager` for
 * an app package's icon. Made off the main thread, a few at a time however many are on screen, and
 * kept in memory by where the file is and when it changed.
 */
internal object Thumbnails {
    private val made = LruCache<String, ImageBitmap>(KEPT)
    private val making = Semaphore(AT_ONCE)

    fun keyOf(location: String, changedAt: Long) = "$location@$changedAt"

    fun cached(key: String): ImageBitmap? = made.get(key)

    /** [location]'s thumbnail, kept under [key]; null when it cannot be made. Reads the file: off the main thread. */
    suspend fun make(context: Context, location: String, kind: ThumbnailKind, key: String): ImageBitmap? = making.withPermit {
        made.get(key) ?: runCatching {
            when (kind) {
                ThumbnailKind.Picture -> decodeImage(context, location, SIDE_PX)
                ThumbnailKind.Video -> ThumbnailUtils.createVideoThumbnail(File(location), Size(SIDE_PX, SIDE_PX), null).asImageBitmap()
                ThumbnailKind.App -> appIcon(context, location)
            }
        }.getOrNull()?.also { made.put(key, it) }
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
    private const val AT_ONCE = 4
}

/** [location]'s thumbnail of [kind] — none for no kind — as it is when [changedAt]; null until it is made, or when it cannot be. */
@Composable
internal fun rememberThumbnail(location: String, kind: ThumbnailKind?, changedAt: Long = 0): ImageBitmap? {
    val context = LocalContext.current
    val key = Thumbnails.keyOf(location, changedAt)
    val image by produceState(kind?.let { Thumbnails.cached(key) }, key, kind) {
        if (kind != null && value == null) value = withContext(Dispatchers.IO) { Thumbnails.make(context, location, kind, key) }
    }
    return image
}
