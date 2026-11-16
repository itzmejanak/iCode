package dev.icode.mobile.domain.files

/**
 * The archives the explorer knows (docs/workspaces-and-files.md), by the end of their name. Those
 * it cannot open say why, on the file, instead of failing halfway: [unreadable].
 */
enum class ArchiveFormat(val suffixes: List<String>, val unreadable: String? = null) {
    Zip(listOf(".zip", ".jar", ".apk")),
    Tar(listOf(".tar")),
    TarGz(listOf(".tar.gz", ".tgz")),
    TarBz2(listOf(".tar.bz2", ".tbz2", ".tbz")),
    TarXz(listOf(".tar.xz", ".txz")),
    SevenZ(listOf(".7z")),
    /** One file, compressed: `notes.txt.gz`. */
    Gz(listOf(".gz")),
    Bz2(listOf(".bz2")),
    Xz(listOf(".xz")),
    Rar(listOf(".rar"), unreadable = "iCode cannot open .rar archives: their format is UnRAR's own"),
    Zst(listOf(".tar.zst", ".zst"), unreadable = "iCode cannot open .zst archives"),
    ;

    /** One compressed file rather than an archive of many: it extracts to the name without its suffix. */
    val single: Boolean get() = this == Gz || this == Bz2 || this == Xz
}

/**
 * What Compress makes: a zip by default, a 7z, or a tar.gz. A zip and a tar.gz keep links and
 * executable bits; a 7z entry can hold neither ([note], as Compress… says it).
 */
enum class CompressFormat(val format: ArchiveFormat, val extension: String, val label: String, val note: String) {
    Zip(ArchiveFormat.Zip, ".zip", "Zip", "Opens anywhere, and keeps links and executable bits"),
    SevenZ(ArchiveFormat.SevenZ, ".7z", "7z", "Smallest, but holds no links and no executable bits"),
    TarGz(ArchiveFormat.TarGz, ".tar.gz", "Tar.gz", "As Linux keeps files, with links and executable bits"),
}

object ArchiveFormats {
    private val bySuffix: List<Pair<String, ArchiveFormat>> =
        ArchiveFormat.entries.flatMap { format -> format.suffixes.map { it to format } }.sortedByDescending { it.first.length }

    /** [name]'s archive format, by its longest known ending — `.tar.gz` before `.gz`; null for none. */
    fun of(name: String): ArchiveFormat? {
        val lower = name.lowercase()
        return bySuffix.firstOrNull { (suffix, _) -> lower.endsWith(suffix) && lower.length > suffix.length }?.second
    }

    /**
     * What Compress names its archive, before its ending: the [only] item's name — a file's
     * without its extension — or, for many, the [folder] they are in, or "Archive" at a place's top.
     */
    fun archiveName(only: Pair<String, Boolean>?, folder: String?): String = when {
        only == null -> folder ?: "Archive"
        only.second -> only.first
        else -> only.first.substringBeforeLast('.').ifEmpty { only.first }
    }

    /** [name] without its archive ending — "site.tar.gz" is "site" — what Extract here names a folder or a file after. */
    fun stem(name: String): String {
        val lower = name.lowercase()
        val suffix = bySuffix.firstOrNull { (suffix, _) -> lower.endsWith(suffix) && lower.length > suffix.length }?.first ?: return name
        return name.substring(0, name.length - suffix.length)
    }
}
