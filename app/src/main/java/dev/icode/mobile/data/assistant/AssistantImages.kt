package dev.icode.mobile.data.assistant

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * An image as it is sent (§13 P7): turned the way the camera held it, its long edge at most
 * [MAX_EDGE] px — the size every provider reads without scaling it again — and JPEG, its
 * transparency on white. Any image Android decodes, a camera's HEIC too.
 */
internal object AssistantImages {
    const val MAX_EDGE = 1_568

    /** Null when [bytes] is not an image Android decodes. */
    fun prepare(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // Decoded at the smallest power of two that still leaves the long edge above the limit.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val longEdge = max(decoded.width, decoded.height)
        val scale = if (longEdge > MAX_EDGE) MAX_EDGE.toFloat() / longEdge else 1f
        val matrix = orientation(bytes).apply { postScale(scale, scale) }
        val shaped = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val opaque = Bitmap.createBitmap(shaped.width, shaped.height, Bitmap.Config.ARGB_8888).also { canvas ->
            Canvas(canvas).apply {
                drawColor(Color.WHITE)
                drawBitmap(shaped, 0f, 0f, null)
            }
        }
        return ByteArrayOutputStream().use { out ->
            opaque.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }.also {
            listOf(decoded, shaped, opaque).distinct().forEach(Bitmap::recycle)
        }
    }

    /** The turn and flip the camera recorded, as a matrix; none when the image says nothing. */
    private fun orientation(bytes: ByteArray): Matrix = Matrix().apply {
        val exif = runCatching { ExifInterface(ByteArrayInputStream(bytes)) }.getOrNull() ?: return@apply
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(270f)
                postScale(-1f, 1f)
            }
        }
    }

    private const val QUALITY = 85
}
