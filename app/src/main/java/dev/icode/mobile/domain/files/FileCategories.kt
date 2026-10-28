package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.FileFamily
import dev.icode.mobile.domain.FileTypes

/** The kinds Manage storage counts space by, each a view of its own (docs/explorer-gaps-plan.md §3.10). */
enum class FileCategory(val label: String) {
    Images("Images"),
    Videos("Videos"),
    Audio("Audio"),
    Documents("Documents"),
    Archives("Archives"),
    Apps("Apps"),
    Code("Code"),
    Other("Other"),
}

object FileCategories {
    /**
     * [name]'s category, by its extension. An app package is an app before it is a zip; an archive is
     * one by [ArchiveFormats]; pictures, source and data by [FileTypes], the editor's own answer — an
     * SVG, which the editor reads as XML, is a picture here.
     */
    fun of(name: String): FileCategory {
        val extension = FilesRules.extension(name)
        return when {
            extension in APPS -> FileCategory.Apps
            ArchiveFormats.of(name) != null -> FileCategory.Archives
            extension == "svg" -> FileCategory.Images
            extension in VIDEOS -> FileCategory.Videos
            extension in AUDIO -> FileCategory.Audio
            extension in DOCUMENTS -> FileCategory.Documents
            else -> when (val type = FileTypes.of(name)) {
                FileTypes.PlainText -> FileCategory.Other
                else -> when (type.family) {
                    FileFamily.Image -> FileCategory.Images
                    FileFamily.Code, FileFamily.Data -> FileCategory.Code
                    FileFamily.Document -> FileCategory.Documents
                }
            }
        }
    }

    private val APPS = setOf("apk", "apks", "xapk", "apkm", "aab")
    private val VIDEOS = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "3g2", "ts", "flv", "wmv", "mpg", "mpeg")
    private val AUDIO = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "oga", "opus", "amr", "mid", "midi", "wma", "aiff")
    private val DOCUMENTS = setOf(
        "pdf", "txt", "rtf", "doc", "docx", "odt", "xls", "xlsx", "ods", "csv", "tsv", "ppt", "pptx", "odp", "epub",
    )
}
