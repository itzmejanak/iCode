package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitSigningRulesTest {
    // What gpg 2.4.4 (Ubuntu 24.04's) listed for two keys `gpg --quick-generate-key` made with its defaults.
    private val listing = """
        sec:u:255:22:BFDDD2AF39D6382D:1792326850:1886934850::u:::scESC:::+::ed25519:::0:
        fpr:::::::::928FF410DCC9C10D4A4D0940BFDDD2AF39D6382D:
        grp:::::::::C7BC326B59872A34067DCDE6C73239ED74D3C128:
        uid:u::::1792326850::2EBD48EFBB417D7955BBA1B137F4D1B4DA422F9B::Ada Lovelace <ada@example.com>::::::::::0:
        ssb:u:255:18:62226C62C6BE1E13:1792326850::::::e:::+::cv25519::
        fpr:::::::::4CE288A295435FDB4A51E46562226C62C6BE1E13:
        grp:::::::::9169B842D71EC77B7FD77A384D55FC5CDF33408F:
        sec:u:255:22:700CD9BE089AC697:1792326854:1886934854::u:::scESC:::+::ed25519:::0:
        fpr:::::::::D400C76C5143E0D6419B2D88700CD9BE089AC697:
        grp:::::::::C81DD4665E987216B4C366CE72A10D2BA384D570:
        uid:u::::1792326854::2EBD48EFBB417D7955BBA1B137F4D1B4DA422F9B::Ada Lovelace <ada@example.com>::::::::::0:
        ssb:u:255:18:1BCF44F3E641A3CF:1792326854::::::e:::+::cv25519::
        fpr:::::::::217CBFCF113BE1DD4178785B1BCF44F3E641A3CF:
        grp:::::::::ABE99BD3C146F848EEC3FB1CEA184BC623661C1C:
    """.trimIndent()

    private val first = GitSigningKey(
        fingerprint = "928FF410DCC9C10D4A4D0940BFDDD2AF39D6382D",
        keyId = "BFDDD2AF39D6382D",
        userId = "Ada Lovelace <ada@example.com>",
        algorithm = "ed25519",
        createdAtEpochMillis = 1_792_326_850_000L,
        expiresAtEpochMillis = 1_886_934_850_000L,
    )

    @Test fun `gpg's keys are read with their fingerprint, ID, user, algorithm and dates — the subkeys' are not keys`() {
        val keys = GitSigningRules.keys(listing)
        assertEquals(listOf("928FF410DCC9C10D4A4D0940BFDDD2AF39D6382D", "D400C76C5143E0D6419B2D88700CD9BE089AC697"), keys.map { it.fingerprint })
        assertEquals(first, keys.first())
        assertEquals("ada@example.com", keys.first().email)
    }

    @Test fun `a revoked, expired or encrypt-only key cannot sign, while an RSA key and an escaped colon read as gpg means them`() {
        val listing = """
            sec:r:255:22:1111111111111111:1:::u:::scESC:::+::ed25519:::0:
            fpr:::::::::AAAA1111111111111111:
            uid:r::::1::X::Revoked <r@x>::::::::::0:
            sec:e:255:22:2222222222222222:1:2::u:::scESC:::+::ed25519:::0:
            fpr:::::::::BBBB2222222222222222:
            uid:e::::1::X::Expired <e@x>::::::::::0:
            sec:u:255:18:3333333333333333:1:::u:::eE:::+::cv25519:::0:
            fpr:::::::::CCCC3333333333333333:
            uid:u::::1::X::Encrypt <c@x>::::::::::0:
            sec:u:4096:1:4444444444444444:1:::u:::scESC:::+:::::0:
            fpr:::::::::DDDD4444444444444444:
            uid:u::::1::X::Team\x3a Ops <ops@x>::::::::::0:
        """.trimIndent()
        val key = GitSigningRules.keys(listing).single()
        assertEquals("rsa4096", key.algorithm)
        assertEquals("Team: Ops <ops@x>", key.userId)
        assertNull(key.expiresAtEpochMillis)
    }

    @Test fun `user signingkey finds its key by fingerprint, long or short ID, 0x and bang, or email`() {
        val keys = GitSigningRules.keys(listing)
        listOf(
            "928FF410DCC9C10D4A4D0940BFDDD2AF39D6382D", "BFDDD2AF39D6382D", "39d6382d", "0xBFDDD2AF39D6382D", "BFDDD2AF39D6382D!",
            "ADA@example.com", "<ada@example.com>",
        ).forEach { assertEquals(it, first, GitSigningRules.keyFor(it, keys)) }
        assertNull(GitSigningRules.keyFor("DEADBEEF", keys))
        assertNull(GitSigningRules.keyFor(" ", keys))
        assertNull(GitSigningRules.keyFor(null, keys))
    }

    @Test fun `a commit's signature is Git's verdict, and none where GnuPG could not check it`() {
        assertEquals(
            GitCommitSignature(GitSignatureStatus.Verified, "Ada Lovelace <ada@example.com>", "BFDDD2AF39D6382D"),
            GitSigningRules.signature("G", "BFDDD2AF39D6382D", "Ada Lovelace <ada@example.com>"),
        )
        assertEquals(GitSignatureStatus.Verified, GitSigningRules.signature("U", "K", "")?.status)
        assertEquals(GitSignatureStatus.Expired, GitSigningRules.signature("X", "K", "")?.status)
        assertEquals(GitSignatureStatus.Expired, GitSigningRules.signature("Y", "K", "")?.status)
        assertEquals(GitSignatureStatus.Revoked, GitSigningRules.signature("R", "K", "")?.status)
        assertEquals(GitSignatureStatus.Bad, GitSigningRules.signature("B", "K", "")?.status)
        assertEquals(GitCommitSignature(GitSignatureStatus.UnknownKey, null, "K"), GitSigningRules.signature("E", "K", ""))
        assertEquals(GitCommitSignature(GitSignatureStatus.Unsigned, null, null), GitSigningRules.signature("N", "", ""))
        assertNull(GitSigningRules.signature("-", "", ""))
    }

    @Test fun `GitHub matches a commit to the key by email, whatever its case`() {
        assertNull(GitSigningRules.unmatchedEmail(first, "Ada@Example.com"))
        assertNull(GitSigningRules.unmatchedEmail(first, null))
        assertEquals("ada@work.example", GitSigningRules.unmatchedEmail(first, "ada@work.example"))
    }

    @Test fun `gpg's failure is its last line that says so, not what it set up on the way`() {
        val stderr = """
            gpg: directory '/root/.gnupg' created
            gpg: keybox '/root/.gnupg/pubring.kbx' created
            gpg: key generation failed: Operation cancelled
        """.trimIndent()
        assertEquals("key generation failed: Operation cancelled", GitSigningRules.failure(stderr))
        assertEquals("something else", GitSigningRules.failure("gpg: something else\n"))
        assertNull(GitSigningRules.failure(""))
    }
}
