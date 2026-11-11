package dev.icode.mobile.domain

import dev.icode.mobile.domain.files.ArchiveFormat
import dev.icode.mobile.domain.syntax.Languages
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTypesTest {
    @Test fun `react extensions select react grammars`() {
        assertEquals("typescriptreact", FileTypes.of("App.tsx").language)
        assertEquals("javascriptreact", FileTypes.of("View.jsx").language)
    }

    @Test fun `a fence's language by id or extension`() {
        val cases = mapOf(
            "kotlin" to "kotlin", "kt" to "kotlin", "Python" to "python", "py" to "python",
            "bash" to "shellscript", "sh" to "shellscript", "shell" to "shellscript", "ts" to "typescript", "tsx" to "typescriptreact",
            "json" to "json", "yml" to "yaml", "c++" to "cpp", "diff" to "diff", "dockerfile" to "dockerfile",
        )
        cases.forEach { (fence, language) -> assertEquals(fence, language, FileTypes.ofLanguage(fence).language) }
        assertEquals(FileTypes.PlainText, FileTypes.ofLanguage("text"))
        assertEquals(FileTypes.PlainText, FileTypes.ofLanguage("mermaid"))
    }

    @Test fun `unknown names are plain text`() {
        listOf("notes.txt", "LICENSE", "archive.", "Makefile.txt.bak").forEach { name ->
            assertEquals(name, FileTypes.PlainText, FileTypes.of(name))
        }
    }

    @Test fun `whole names, name patterns and the longest extension`() {
        val cases = mapOf(
            "Makefile" to "makefile", "GNUmakefile" to "makefile", "Dockerfile" to "dockerfile",
            "Dockerfile.dev" to "dockerfile", "Containerfile.ci" to "dockerfile", "Jenkinsfile" to "groovy",
            "build.gradle" to "groovy", "Gemfile" to "ruby", "Berksfile.lock" to "ruby", ".env" to "dotenv",
            ".env.local" to "dotenv", "prod.env" to "dotenv", ".gitignore" to "ignore", ".gitattributes" to "properties",
            ".editorconfig" to "properties", "gradle.properties" to "properties", "COMMIT_EDITMSG" to "git-commit",
            "git-rebase-todo" to "git-rebase", "run.cmd" to "bat", "fix.patch" to "diff", "query.sql" to "sql",
            "main.c" to "c", "api.h" to "cpp", "vec.hpp.in" to "cpp", "a.c++" to "cpp", "App.cs" to "csharp",
            "index.php" to "php", "View.swift" to "swift", "style.scss" to "scss", "theme.less" to "less",
            "init.lua" to "lua", "main.dart" to "dart", "config.ini" to "ini",
            ".bashrc" to "shell", "PKGBUILD" to "shell", "tsconfig.json" to "json", ".eslintrc" to "json",
            "app.js.map" to "json", "events.jsonl" to "jsonl", "page.xslt" to "xsl", "App.csproj" to "xml",
            "types.pyi" to "python", "index.es6" to "javascript", "notes.mdown" to "markdown", "page.jsp" to "html",
        )
        cases.forEach { (name, id) -> assertEquals(name, id, FileTypes.of(name).id) }
    }

    @Test fun `case does not matter`() {
        assertEquals(FileTypes.Markdown, FileTypes.of("README.MD"))
        assertEquals("kotlin", FileTypes.of("Build.KTS").language)
    }

    @Test fun `each type has a family for the lists`() {
        assertEquals(FileFamily.Code, FileTypes.of("Main.kt").family)
        assertEquals(FileFamily.Code, FileTypes.of("server.mjs").family)
        assertEquals(FileFamily.Data, FileTypes.of("package.json").family)
        assertEquals(FileFamily.Data, FileTypes.of("config.yml").family)
        assertEquals(FileFamily.Document, FileTypes.of("notes.md").family)
        assertEquals(FileFamily.Image, FileTypes.of("Logo.PNG").family)
        assertEquals(FileFamily.Code, FileTypes.of("logo.svg").family)
        assertEquals(FileFamily.Video, FileTypes.of("clip.mp4").family)
        assertEquals(FileFamily.Audio, FileTypes.of("song.flac").family)
        assertEquals(FileFamily.Archive, FileTypes.of("site.tar.gz").family)
        assertEquals(FileFamily.App, FileTypes.of("app.apk").family)
        assertEquals(FileFamily.Binary, FileTypes.of("Inter.ttf").family)
        assertEquals(FileTypes.Pdf, FileTypes.of("paper.PDF"))
    }

    @Test fun `text is what the editor opens, the other kinds are their formats' bytes`() {
        listOf("Main.kt", "package.json", "notes.md", "LICENSE", "table.csv", "logo.svg").forEach { assertTrue(it, FileTypes.of(it).text) }
        listOf("photo.jpg", "clip.mp4", "paper.pdf", "letter.docx", "site.zip", "app.apk", "font.otf", "lib.so").forEach { assertFalse(it, FileTypes.of(it).text) }
        assertTrue(FileTypes.rules.all { it.type.language == null || it.type.text })
    }

    @Test fun `archives are the endings Extract reads, an app package an app first`() {
        val endings = ArchiveFormat.entries.flatMap { it.suffixes }.map { "x$it" }
        endings.filterNot { it == "x.apk" }.forEach { assertEquals(it, FileTypes.Archive, FileTypes.of(it)) }
        assertEquals(FileTypes.AndroidPackage, FileTypes.of("x.apk"))
    }

    @Test fun `the bytes are read only where the name cannot say`() {
        listOf("clip.ts", "index.mts", "README", "download", "notes.log").forEach { assertTrue(it, FileTypes.needsContent(it)) }
        listOf("lib.dom.d.ts", "types.d.mts", "a.cts", "Main.kt", "clip.mp4", "logo.svg", "package.json").forEach { assertFalse(it, FileTypes.needsContent(it)) }
    }

    @Test fun `a shared or unknown name takes what its bytes say, and text stays what its name says`() {
        val stream = ByteArray(FileTypes.HEAD_BYTES).also { bytes -> (0 until 3).forEach { bytes[it * 188] = 0x47 } }
        assertEquals(FileTypes.Video, FileTypes.of("news.ts", stream))
        assertEquals("typescript", FileTypes.of("server.ts", "import { x } from './x';\n".toByteArray()).id)
        assertEquals(FileTypes.Image, FileTypes.of("download", byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)))
        assertEquals(FileTypes.PlainText, FileTypes.of("README", "Hello".toByteArray()))
        // A name that says what it is is never overruled: a PNG named `Main.kt` is still Kotlin to the editor.
        assertEquals("kotlin", FileTypes.of("Main.kt", stream).id)
    }

    @Test fun `extensions are read longest first, each kind stands for one of its own`() {
        assertEquals(listOf("dom.d.ts", "d.ts", "ts"), FileTypes.extensionsOf("lib.dom.d.ts").toList())
        assertEquals(listOf("env"), FileTypes.extensionsOf(".env").toList())
        assertEquals(emptyList<String>(), FileTypes.extensionsOf("Makefile").toList())
        assertEquals("mp4", FileTypes.extensionOf(FileTypes.Video))
        assertEquals(FileTypes.Video, FileTypes.byId("video"))
        assertEquals(FileTypes.PlainText, FileTypes.byId("plaintext"))
    }

    @Test fun `a name or an extension belongs to one type only`() {
        val extensions = FileTypes.rules.flatMap { it.extensions }
        val names = FileTypes.rules.flatMap { it.names }
        assertEquals(extensions.groupBy { it }.filterValues { it.size > 1 }.keys, emptySet<String>())
        assertEquals(names.groupBy { it }.filterValues { it.size > 1 }.keys, emptySet<String>())
        val prefixes = FileTypes.rules.flatMap { it.prefixes }
        assertEquals(prefixes.groupBy { it }.filterValues { it.size > 1 }.keys, emptySet<String>())
        assertEquals(FileTypes.rules.map { it.type.id }.size, FileTypes.rules.map { it.type.id }.toSet().size)
    }

    @Test fun `every language a file type names is in the catalog`() {
        val catalog = Languages.parse(File("src/main/assets", Languages.ASSET).readText())
        val named = FileTypes.rules.mapNotNull { it.type.language }.toSet()
        assertEquals(emptySet<String>(), named.filter { catalog.byId(it) == null }.toSet())
        assertTrue(named.isNotEmpty())
    }

    @Test fun `a script without an extension is known by its first line`() {
        val shell = FileTypes.of("sh.sh")
        listOf(
            "#!/usr/bin/env bash",
            "#!/bin/bash -e",
            "#!/bin/sh",
            "#! /usr/bin/env zsh",
            "#!/usr/bin/env bash\r",
        ).forEach { line -> assertEquals(line, shell, FileTypes.of("task-start", line)) }
        assertEquals(FileTypes.of("a.py"), FileTypes.of("serve", "#!/usr/bin/env -S python3.12 -u"))
        assertEquals(FileTypes.of("a.py"), FileTypes.of("serve", "#!/usr/bin/env PYTHONPATH=lib python3"))
        assertEquals(FileTypes.of("a.js"), FileTypes.of("cli", "#!/usr/bin/env node"))
        assertEquals("ruby", FileTypes.of("setup", "#!/usr/bin/env ruby").id)
        assertEquals("makefile", FileTypes.of("build", "#!/usr/bin/make -f").id)
        assertEquals("groovy", FileTypes.of("deploy", "#!/usr/bin/env groovy").id)
        assertEquals("typescript", FileTypes.of("serve", "#!/usr/bin/env ts-node").id)
        assertEquals("typescript", FileTypes.of("serve", "#!/usr/bin/env -S deno run").id)
        assertEquals("xml", FileTypes.of("feed", "<?xml version=\"1.0\"?>").id)
        assertEquals("xml", FileTypes.of("icon", "<svg xmlns=\"http://www.w3.org/2000/svg\">").id)
        assertEquals("yaml", FileTypes.of("user-data", "#cloud-config").id)
        assertEquals("shell", FileTypes.of("run", "# -*- mode: shell-script -*-").id)
    }

    @Test fun `the name wins over the first line, and a line that runs nothing known is plain`() {
        assertEquals(FileTypes.Markdown, FileTypes.of("notes.md", "#!/bin/bash"))
        listOf("", "# a comment", "#!", "#!/usr/bin/env", "#!/usr/bin/env -S", "#!/usr/bin/awk -f").forEach { line ->
            assertEquals(line, FileTypes.PlainText, FileTypes.of("run", line))
        }
    }
}
