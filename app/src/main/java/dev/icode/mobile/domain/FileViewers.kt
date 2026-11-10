package dev.icode.mobile.domain

/**
 * A viewer of iCode's own for a kind of file (docs/file-viewers-plan.md). A file with one opens in
 * it; any other opens as text in the editor, or in another app when it is not text. A new viewer is
 * an entry here, the kind it shows in [of], and its screen in the UI's viewer host, which must draw
 * every entry.
 */
enum class FileViewer {
    /** A picture, whole in its sheet, pinched larger. */
    Image;

    companion object {
        /** The viewer for a file named [name]; null for one the editor opens, or another app. */
        fun of(name: String): FileViewer? = of(FileTypes.of(name))

        /** The viewer for a file of [type]. */
        fun of(type: FileType): FileViewer? = Image.takeIf { type.family == FileFamily.Image }
    }
}
