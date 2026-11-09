package dev.icode.mobile.data.language

import dev.icode.mobile.domain.LanguageServiceConfiguration
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageServiceStoreTest {
    @Test
    fun `a version 1 list, as v0_1_0 saved it, reads with the new fields' defaults`() {
        val v1 = """[{"id":"a","name":"TS","command":"typescript-language-server --stdio","patterns":"*.ts, *.tsx","rootMarkers":"tsconfig.json","initializationOptions":"{\"x\":1}"}]"""
        assertEquals(
            listOf(LanguageServiceConfiguration("a", "TS", "typescript-language-server --stdio", "", "*.ts, *.tsx", "tsconfig.json", "", "{\"x\":1}", "{}")),
            LanguageServiceStore.parse(v1),
        )
    }

    @Test
    fun `version 2 keeps every field`() {
        val config = LanguageServiceConfiguration("b", "Py", "pylsp", "python", "*.pyi", "pyproject.toml", "A=1\nB=2", "{}", """{"pylsp":{"plugins":{}}}""")
        assertEquals(listOf(config), LanguageServiceStore.parse(LanguageServiceStore.format(listOf(config))))
    }
}

class LanguageRootsTest {
    private val top = Files.createTempDirectory("roots").toFile()

    @Test
    fun `the top is the project when it holds a marker or none is named`() {
        File(top, "package.json").writeText("{}")
        File(top, "web").mkdirs().also { File(top, "web/package.json").writeText("{}") }
        assertEquals(listOf(top.path), LanguageRoots.folders(top.path, listOf("package.json")))
        assertEquals(listOf(top.path), LanguageRoots.folders(top.path, emptyList()))
    }

    @Test
    fun `else each folder that holds one, not its own folders, and never inside node_modules`() {
        File(top, "api/inner").mkdirs()
        File(top, "api/go.mod").writeText("")
        File(top, "api/inner/go.mod").writeText("")
        File(top, "tools/cli").mkdirs()
        File(top, "tools/cli/go.mod").writeText("")
        File(top, "node_modules/x").mkdirs()
        File(top, "node_modules/x/go.mod").writeText("")
        assertEquals(listOf(File(top, "api").path, File(top, "tools/cli").path), LanguageRoots.folders(top.path, listOf("go.mod")))
        assertEquals(listOf(top.path), LanguageRoots.folders(top.path, listOf("Cargo.toml")))
    }
}
