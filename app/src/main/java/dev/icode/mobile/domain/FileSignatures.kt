package dev.icode.mobile.domain

/**
 * What a file's first bytes say it is, for the names that cannot say (docs/file-kinds-icons-plan.md
 * D2): each format's own signature — its "magic number" — as the format's specification gives it and
 * `file(1)` reads it. Only formats a person keeps are here, each strong enough that text never starts
 * so: no text file begins with a NUL, a byte of 0x80 or more that is not UTF-8, or a run of these.
 */
object FileSignatures {
    /** The type [head] — a file's first bytes, up to [FileTypes.HEAD_BYTES] — is a format of; null for none known (text, or bytes no rule here knows). */
    fun of(head: ByteArray): FileType? = when {
        transportStream(head) -> FileTypes.Video
        head.startsWith(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A) -> FileTypes.Image
        head.startsWith(0xFF, 0xD8, 0xFF) -> FileTypes.Image
        head.startsWith("GIF87a") || head.startsWith("GIF89a") -> FileTypes.Image
        // A BMP's reserved bytes are zero; "BM" alone would be any text beginning so.
        head.startsWith("BM") && head.size >= BMP_HEADER && (6 until 10).all { head[it].toInt() == 0 } -> FileTypes.Image
        head.startsWith(0x00, 0x00, 0x01, 0x00) && head.size >= ICO_HEADER -> FileTypes.Image
        head.startsWith("RIFF") -> riff(head)
        head.at(4, "ftyp") -> isoMedia(head)
        head.startsWith(0x1A, 0x45, 0xDF, 0xA3) -> FileTypes.Video  // Matroska and WebM
        head.startsWith(0x00, 0x00, 0x01, 0xBA) || head.startsWith(0x00, 0x00, 0x01, 0xB3) -> FileTypes.Video  // MPEG-1 and -2
        head.startsWith("FLV") && head.size > 3 && head[3].toInt() == 1 -> FileTypes.Video
        head.startsWith(0x30, 0x26, 0xB2, 0x75, 0x8E, 0x66, 0xCF, 0x11) -> FileTypes.Video  // ASF: WMV, WMA
        // Signatures of letters alone are taken only with the binary bytes their headers go on with.
        (head.startsWith("OggS") || head.startsWith("fLaC") || head.startsWith("MThd") || head.startsWith("ID3")) && head.binary() -> FileTypes.Audio
        mpegAudioFrame(head) -> FileTypes.Audio
        head.startsWith("%PDF-") -> FileTypes.Pdf
        head.startsWith('P', 'K', 0x03, 0x04) || head.startsWith(0x1F, 0x8B) -> FileTypes.Archive
        head.startsWith('7', 'z', 0xBC, 0xAF, 0x27, 0x1C) || head.startsWith("Rar!", 0x1A, 0x07) -> FileTypes.Archive
        head.startsWith(0xFD, '7', 'z', 'X', 'Z', 0x00) || head.startsWith(0x28, 0xB5, 0x2F, 0xFD) -> FileTypes.Archive
        head.startsWith("BZh") && head.at(4, "1AY&SY") -> FileTypes.Archive
        head.at(TAR_MAGIC, "ustar") -> FileTypes.Archive
        head.startsWith(0x7F, 'E', 'L', 'F') || head.startsWith(0x00, 'a', 's', 'm') -> FileTypes.Executable
        head.startsWith("dex\n") && head.binary() -> FileTypes.Executable
        head.startsWith("SQLite format 3", 0x00) -> FileTypes.Database
        (head.startsWith("wOFF") || head.startsWith("wOF2") || head.startsWith("OTTO")) && head.binary() -> FileTypes.Font
        else -> null
    }

    /**
     * An MPEG transport stream: 188-byte packets, each beginning with the sync byte 0x47 — or, as
     * Blu-ray and AVCHD write them (`.m2ts`, `.mts`), 192-byte ones with a 4-byte timestamp first.
     * Three packets in a row are needed: one "G" could begin any text.
     */
    private fun transportStream(head: ByteArray): Boolean =
        listOf(0 to PACKET, PREFIX to PACKET + PREFIX).any { (start, step) ->
            (0 until PACKETS).all { packet -> head.getOrNull(start + packet * step)?.toInt() == SYNC }
        }

    /** RIFF holds a sound (WAVE), a video (AVI) or a picture (WEBP), said at byte 8. */
    private fun riff(head: ByteArray): FileType? = when {
        head.at(8, "WAVE") -> FileTypes.Audio
        head.at(8, "AVI ") -> FileTypes.Video
        head.at(8, "WEBP") -> FileTypes.Image
        else -> null
    }

    /** ISO base media (MP4, MOV, 3GP, HEIF, AVIF): its major brand, at byte 8, says pictures, sound or video. */
    private fun isoMedia(head: ByteArray): FileType {
        if (head.size < BRAND_END) return FileTypes.Video
        val brand = String(head, BRAND_START, BRAND_END - BRAND_START, Charsets.ISO_8859_1)
        return when (brand) {
            in PICTURE_BRANDS -> FileTypes.Image
            in SOUND_BRANDS -> FileTypes.Audio
            else -> FileTypes.Video
        }
    }

    /**
     * An MP3 or AAC frame's sync: eleven set bits. UTF-8 text never has the byte 0xFF; UTF-16 text
     * begins `FF FE`, which is not taken.
     */
    private fun mpegAudioFrame(head: ByteArray): Boolean {
        if (head.size < 2 || head[0].toInt() and 0xFF != 0xFF) return false
        val second = head[1].toInt() and 0xFF
        return second and 0xE0 == 0xE0 && second != 0xFE && second != 0xFF
    }

    /** A NUL among the first bytes: what no text holds, and every header here does after its letters. */
    private fun ByteArray.binary(): Boolean = take(HEADER_BYTES).any { it.toInt() == 0 }

    private fun ByteArray.startsWith(vararg expected: Any): Boolean {
        var at = 0
        expected.forEach { part ->
            val bytes = when (part) {
                is String -> part.toByteArray(Charsets.ISO_8859_1)
                is Char -> byteArrayOf(part.code.toByte())
                is Int -> byteArrayOf(part.toByte())
                else -> error("A signature is made of strings, characters and bytes")
            }
            if (!at(at, bytes)) return false
            at += bytes.size
        }
        return true
    }

    private fun ByteArray.at(offset: Int, text: String): Boolean = at(offset, text.toByteArray(Charsets.ISO_8859_1))

    private fun ByteArray.at(offset: Int, bytes: ByteArray): Boolean =
        size >= offset + bytes.size && bytes.indices.all { this[offset + it] == bytes[it] }

    private const val SYNC = 0x47
    private const val PACKET = 188
    private const val PREFIX = 4
    private const val PACKETS = 3
    private const val BMP_HEADER = 14
    private const val ICO_HEADER = 6
    private const val TAR_MAGIC = 257
    private const val BRAND_START = 8
    private const val BRAND_END = 12
    private const val HEADER_BYTES = 16

    /** HEIF and AVIF pictures, and their sequences. */
    private val PICTURE_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1", "avif", "avis")

    /** MPEG-4 sound and audiobooks. */
    private val SOUND_BRANDS = setOf("M4A ", "M4B ", "M4P ", "F4A ", "F4B ")
}
