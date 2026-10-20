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
        /** The viewer for a file named [name]; null for one the editor opens. */
        fun of(name: String): FileViewer? = when (FileTypes.of(name).family) {
            FileFamily.Image -> Image
            FileFamily.Code, FileFamily.Data, FileFamily.Document -> null
        }
    }
}
