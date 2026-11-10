package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.FileTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Archives by their names, and the categories Manage storage counts (docs/explorer-gaps-plan.md §3.10, §3.11). */
class FileKindsTest {
    @Test fun `an archive is known by its longest ending`() {
        assertEquals(ArchiveFormat.TarGz, ArchiveFormats.of("site.tar.gz"))
        assertEquals(ArchiveFormat.TarGz, ArchiveFormats.of("SITE.TGZ"))
        assertEquals(ArchiveFormat.Gz, ArchiveFormats.of("notes.txt.gz"))
        assertEquals(ArchiveFormat.Zst, ArchiveFormats.of("pkg.tar.zst"))
        assertEquals(ArchiveFormat.Zip, ArchiveFormats.of("app-release.apk"))
        assertEquals(ArchiveFormat.SevenZ, ArchiveFormats.of("backup.7z"))
        assertNull(ArchiveFormats.of(".gz"))
        assertNull(ArchiveFormats.of("README.md"))
    }

    @Test fun `extract here names what it makes after the archive, without its ending`() {
        assertEquals("site", ArchiveFormats.stem("site.tar.gz"))
        assertEquals("notes.txt", ArchiveFormats.stem("notes.txt.gz"))
        assertEquals("README.md", ArchiveFormats.stem("README.md"))
    }

    @Test fun `what cannot be opened says why, and one compressed file is no archive of many`() {
        assertEquals(listOf(ArchiveFormat.Rar, ArchiveFormat.Zst), ArchiveFormat.entries.filter { it.unreadable != null })
        assertEquals(listOf(ArchiveFormat.Gz, ArchiveFormat.Bz2, ArchiveFormat.Xz), ArchiveFormat.entries.filter { it.single })
    }

    @Test fun `each file has one category`() {
        mapOf(
            "app.apk" to FileCategory.Apps,
            "site.tar.gz" to FileCategory.Archives,
            "lib.jar" to FileCategory.Archives,
            "logo.svg" to FileCategory.Images,
            "photo.JPG" to FileCategory.Images,
            "clip.mp4" to FileCategory.Videos,
            "song.flac" to FileCategory.Audio,
            "paper.pdf" to FileCategory.Documents,
            "README.md" to FileCategory.Documents,
            "notes.txt" to FileCategory.Documents,
            "Main.kt" to FileCategory.Code,
            "package.json" to FileCategory.Code,
            "Makefile" to FileCategory.Code,
            "data.bin" to FileCategory.Other,
            "LICENSE" to FileCategory.Other,
            // TypeScript by its name, whatever Android calls `.ts`; a transport stream only by its bytes.
            "thrir-client.ts" to FileCategory.Code,
            "lib.dom.d.ts" to FileCategory.Code,
            "index.mts" to FileCategory.Code,
            "config.cts" to FileCategory.Code,
            "movie.m2ts" to FileCategory.Videos,
            "intro.ogv" to FileCategory.Videos,
            "clip.MKV" to FileCategory.Videos,
            "voice.opus" to FileCategory.Audio,
            "loop.aif" to FileCategory.Audio,
            "table.csv" to FileCategory.Documents,
            "letter.docx" to FileCategory.Documents,
            "sheet.xlsx" to FileCategory.Documents,
            "book.epub" to FileCategory.Documents,
            "bundle.aab" to FileCategory.Apps,
            "pkg.tar.zst" to FileCategory.Archives,
            "notes.txt.gz" to FileCategory.Archives,
            "Inter.woff2" to FileCategory.Other,
            "libc.so" to FileCategory.Other,
            "app.db" to FileCategory.Other,
            "scan.jfif" to FileCategory.Images,
        ).forEach { (name, category) -> assertEquals(name, category, FileCategories.of(name)) }
    }

    @Test fun `what the bytes say decides only where the name could not`() {
        assertEquals(FileCategory.Videos, FileCategories.of(FileTypes.Video, "news.ts"))
        assertEquals(FileCategory.Images, FileCategories.of(FileTypes.Image, "download"))
        // An SVG is a picture here, though the editor reads it as XML; a `.txt` is a document, though plain text.
        assertEquals(FileCategory.Images, FileCategories.of(FileTypes.of("logo.svg"), "logo.svg"))
        assertEquals(FileCategory.Code, FileCategories.of(FileTypes.of("feed.xml"), "feed.xml"))
        assertEquals(FileCategory.Other, FileCategories.of(FileTypes.PlainText, "notes.log"))
    }
}
