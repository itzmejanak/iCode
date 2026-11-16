package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.FileFamily
import dev.icode.mobile.domain.FileType
import dev.icode.mobile.domain.FileTypes

/**
 * The kinds Manage storage counts space by, each a view of its own (docs/workspaces-and-files.md);
 * [one] names one file of it, as Properties does.
 */
enum class FileCategory(val label: String, val one: String) {
    Images("Images", "Image"),
    Videos("Videos", "Video"),
    Audio("Audio", "Audio"),
    Documents("Documents", "Document"),
    Archives("Archives", "Archive"),
    Apps("Apps", "App"),
    Code("Code", "Code"),
    Other("Other", "File"),
}

object FileCategories {
    /** [name]'s category by its name alone: where its bytes are not at hand. */
    fun of(name: String): FileCategory = of(FileTypes.of(name), name)

    /**
     * The category of a file named [name] that is of [type] — [FileTypes]' answer, by its bytes too
     * where the name could not say (docs/workspaces-and-files.md). Its family decides, with two
     * exceptions: an SVG, which the editor reads as XML, is a picture here; a `.txt`, plain text to the
     * editor as any unknown name is, is a document.
     */
    fun of(type: FileType, name: String): FileCategory = when {
        type == FileTypes.PlainText -> if (FilesRules.extension(name) == TEXT) FileCategory.Documents else FileCategory.Other
        type.id == XML && FilesRules.extension(name) == SVG -> FileCategory.Images
        else -> when (type.family) {
            FileFamily.Image -> FileCategory.Images
            FileFamily.Video -> FileCategory.Videos
            FileFamily.Audio -> FileCategory.Audio
            FileFamily.Document -> FileCategory.Documents
            FileFamily.Archive -> FileCategory.Archives
            FileFamily.App -> FileCategory.Apps
            FileFamily.Code, FileFamily.Data -> FileCategory.Code
            FileFamily.Binary -> FileCategory.Other
        }
    }

    private const val TEXT = "txt"
    private const val XML = "xml"
    private const val SVG = "svg"
}
