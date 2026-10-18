package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpgRequestsTest {
    // What gpg-agent 2.4.4 sent iCode's pinentry when a commit was signed, as the script hands it over.
    private val unlock = """
        pin

        SETDESC Please enter the passphrase to unlock the OpenPGP secret key:%0A%22Test <t@example.com>%22%0A255-bit EDDSA key, ID 56F1875955D51D85,%0Acreated 2026-10-18.%0A
        SETPROMPT Passphrase:
        SETERROR Bad Passphrase (try 2 of 3)



    """.trimIndent()

    @Test fun `gpg asking for an agent names its home`() {
        assertEquals(GpgRequest.StartAgent("/root/.gnupg"), GpgRequests.parse("812", "agent\nHOMEDIR /root/.gnupg\n"))
    }

    @Test fun `a passphrase is asked in gpg-agent's words, decoded, with why it asks again`() {
        assertEquals(
            GpgRequest.Ask(
                GpgPrompt(
                    id = "954",
                    asksPassphrase = true,
                    title = null,
                    description = "Please enter the passphrase to unlock the OpenPGP secret key:\n\"Test <t@example.com>\"\n" +
                        "255-bit EDDSA key, ID 56F1875955D51D85,\ncreated 2026-10-18.",
                    prompt = "Passphrase:",
                    error = "Bad Passphrase (try 2 of 3)",
                    ok = null,
                    cancel = null,
                ),
            ),
            GpgRequests.parse("954", unlock),
        )
    }

    @Test fun `a confirmation keeps gpg-agent's buttons`() {
        val request = "confirm\n\nSETDESC You have not entered a passphrase - this is in general a bad idea!%0APlease confirm.\n\n\n" +
            "SETOK Yes, protection is not needed\nSETCANCEL Enter new passphrase\n"
        val prompt = (GpgRequests.parse("7", request) as GpgRequest.Ask).prompt
        assertEquals(false, prompt.asksPassphrase)
        assertEquals("You have not entered a passphrase - this is in general a bad idea!\nPlease confirm.", prompt.description)
        assertEquals("Yes, protection is not needed", prompt.ok)
        assertEquals("Enter new passphrase", prompt.cancel)
    }

    @Test fun `escapes decode as UTF-8, a mnemonic's underscore goes, and what is not an escape stays`() {
        val prompt = (GpgRequests.parse("1", "confirm\nSETDESC Ren%C3%A9e%25 %-1 %G1 100%\nSETOK _OK\nSETCANCEL Save__as\n") as GpgRequest.Ask).prompt
        assertEquals("Renée% %-1 %G1 100%", prompt.description)
        assertEquals("OK", prompt.ok)
        assertEquals("Save_as", prompt.cancel)
    }

    @Test fun `a request this build does not know is none`() {
        assertNull(GpgRequests.parse("1", "fingerprint\nSETDESC x\n"))
        assertNull(GpgRequests.parse("1", "agent\n"))
        assertNull(GpgRequests.parse("1", ""))
    }

    @Test fun `the answer is the line the pinentry passes on`() {
        assertEquals("D s3cret%25pass%0D%0A", GpgRequests.answerLine(GpgAnswer.Passphrase("s3cret%pass\r\n")))
        // pinentry sends no data line for an empty passphrase.
        assertEquals("OK", GpgRequests.answerLine(GpgAnswer.Passphrase("")))
        assertEquals("OK", GpgRequests.answerLine(GpgAnswer.Ok))
        assertEquals("CANCEL", GpgRequests.answerLine(GpgAnswer.Cancel))
    }

    @Test fun `a passphrase is never written out`() {
        assertEquals("Answer(id=9, answer=Passphrase(***))", GpgIntent.Answer("9", GpgAnswer.Passphrase("hunter22")).toString())
    }
}
