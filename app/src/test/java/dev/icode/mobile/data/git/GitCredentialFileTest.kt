package dev.icode.mobile.data.git

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import org.junit.Assert.assertEquals
import org.junit.Test

class GitCredentialFileTest {
    @Test fun `the store line percent-encodes whatever would break the address`() {
        assertEquals(
            "https://ada%40work:p%3Ass%2Fw%25rd@git.example.com:8443",
            GitCredentialFile.line("git.example.com:8443", "ada@work", "p:ss/w%rd"),
        )
    }

    @Test fun `a non-ascii account is encoded byte by byte`() {
        // "é" is two UTF-8 bytes; encoding the character code would give the wrong %E9.
        assertEquals("Ren%C3%A9", GitCredentialFile.encode("René"))
        assertEquals("ghp_AbC-1.2~x", GitCredentialFile.encode("ghp_AbC-1.2~x"))
    }

    @Test fun `the file is readable by its owner alone and holds the one line`() {
        val directory = Files.createTempDirectory("guest-tmp").toFile()
        try {
            val file = GitCredentialFile.create(directory, "https://ada:token@github.com")
            assertEquals(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(file.toPath()),
            )
            assertEquals("https://ada:token@github.com\n", file.readText())
        } finally {
            directory.deleteRecursively()
        }
    }
}
