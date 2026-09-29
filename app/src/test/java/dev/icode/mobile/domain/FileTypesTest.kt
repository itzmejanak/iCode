package dev.icode.mobile.domain

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTypesTest {
    @Test fun `react extensions select react grammars`() {
        assertEquals("source.tsx", FileTypes.of("App.tsx").grammar)
        assertEquals("source.js.jsx", FileTypes.of("View.jsx").grammar)
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
        )
        cases.forEach { (name, id) -> assertEquals(name, id, FileTypes.of(name).id) }
    }

    @Test fun `case does not matter`() {
        assertEquals(FileTypes.Markdown, FileTypes.of("README.MD"))
        assertEquals("source.kotlin", FileTypes.of("Build.KTS").grammar)
    }

    @Test fun `each type has a family for the lists`() {
        assertEquals(FileFamily.Code, FileTypes.of("Main.kt").family)
        assertEquals(FileFamily.Code, FileTypes.of("server.mjs").family)
        assertEquals(FileFamily.Data, FileTypes.of("package.json").family)
        assertEquals(FileFamily.Data, FileTypes.of("config.yml").family)
        assertEquals(FileFamily.Document, FileTypes.of("notes.md").family)
    }

    @Test fun `a name or an extension belongs to one type only`() {
        val extensions = FileTypes.rules.flatMap { it.extensions }
        val names = FileTypes.rules.flatMap { it.names }
        assertEquals(extensions.groupBy { it }.filterValues { it.size > 1 }.keys, emptySet<String>())
        assertEquals(names.groupBy { it }.filterValues { it.size > 1 }.keys, emptySet<String>())
        val prefixes = FileTypes.rules.flatMap { it.prefixes }
        assertEquals(prefixes.groupBy { it }.filterValues { it.size > 1 }.keys, emptySet<String>())
        val interpreters = FileTypes.rules.flatMap { it.interpreters }
        assertEquals(interpreters.groupBy { it }.filterValues { it.size > 1 }.keys, emptySet<String>())
        assertEquals(FileTypes.rules.map { it.type.id }.size, FileTypes.rules.map { it.type.id }.toSet().size)
    }

    @Test fun `every grammar a file type names is shipped`() {
        val registry = JSONObject(File("src/main/assets/textmate/languages.json").readText()).getJSONArray("languages")
        val shipped = (0 until registry.length()).map { registry.getJSONObject(it).getString("scopeName") }.toSet()
        val named = FileTypes.rules.mapNotNull { it.type.grammar }.toSet()
        assertEquals(emptySet<String>(), named - shipped)
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
    }

    @Test fun `the name wins over the first line, and a line that runs nothing known is plain`() {
        assertEquals(FileTypes.Markdown, FileTypes.of("notes.md", "#!/bin/bash"))
        listOf("", "# a comment", "#!", "#!/usr/bin/env", "#!/usr/bin/env -S", "#!/usr/bin/awk -f").forEach { line ->
            assertEquals(line, FileTypes.PlainText, FileTypes.of("run", line))
        }
    }
}
