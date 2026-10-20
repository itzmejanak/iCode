package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.domain.AssistantAttachment
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantModel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AssistantAttachmentsTest {
    @get:Rule val temp = TemporaryFolder()

    private val phone = mutableMapOf<String, PhoneFile>()
    private val keptFolder by lazy { temp.newFolder("kept") }
    private val workspace by lazy { AssistantWorkspace(temp.newFolder("app"), "/workspace/app") }
    private val reader = AssistantAttachments({ phone[it] }, prepareImage = { bytes -> if (bytes.first() == NOT_AN_IMAGE) null else bytes + 0 }, openText = { null }, readText = { File(it).readText() })
    private val seeing = AssistantModel("vision", images = true, documents = true)
    private val keep: (ByteArray, String) -> String = { bytes, extension -> File(keptFolder, "${bytes.size}.$extension").apply { writeBytes(bytes) }.path }

    private fun phoneFile(uri: String, name: String, type: String?, bytes: ByteArray) {
        phone[uri] = PhoneFile(name, type) { bytes.inputStream() }
    }

    private fun refused(locations: List<String>, model: AssistantModel = seeing): String? = try {
        reader.read(workspace, locations, model, keep)
        null
    } catch (failure: AssistantTurnException) {
        failure.message
    }

    @Test fun `text goes as text, an image as prepared and kept, a PDF as itself, a workspace file said to be one`() {
        File(workspace.root, "a.kt").writeText("val a = 1")
        phoneFile("content://1", "notes.md", "text/markdown", "# Notes".toByteArray())
        phoneFile("content://2", "IMG.HEIC", "image/heic", byteArrayOf(1, 2))
        phoneFile("content://3", "spec.pdf", "application/pdf", "%PDF-1.7 …".toByteArray())
        val read = reader.read(workspace, listOf(File(workspace.root, "a.kt").path, "content://1", "content://2", "content://3"), seeing, keep)
        assertEquals(AssistantAttachment("a.kt", text = "val a = 1", inWorkspace = true), read[0])
        assertEquals(AssistantAttachment("notes.md", text = "# Notes"), read[1])
        assertEquals(AssistantAttachmentKind.Image, read[2].kind)
        assertEquals("image/jpeg", read[2].mimeType)
        assertEquals(listOf<Byte>(1, 2, 0), File(read[2].file!!).readBytes().toList())
        assertEquals(AssistantAttachmentKind.Document, read[3].kind)
        assertTrue(File(read[3].file!!).readText().startsWith("%PDF"))
    }

    @Test fun `what the model cannot read is refused before anything is read or kept`() {
        phoneFile("content://2", "photo.jpg", "image/jpeg", byteArrayOf(1, 2))
        assertEquals("text-only does not read images — choose a model that does", refused(listOf("content://2"), AssistantModel("text-only")))
        phoneFile("content://3", "spec.pdf", "application/pdf", "%PDF".toByteArray())
        assertEquals("pictures does not read PDFs — choose a model that does", refused(listOf("content://3"), AssistantModel("pictures", images = true)))
        assertTrue(keptFolder.listFiles()!!.isEmpty())
    }

    @Test fun `binary files, files too large, false PDFs and images this phone cannot read are each said`() {
        phoneFile("content://zip", "code.zip", "application/zip", byteArrayOf(80, 75, 0, 3))
        assertEquals("code.zip is not text — only text, images and PDFs can be sent", refused(listOf("content://zip")))
        phoneFile("content://big", "huge.pdf", "application/pdf", ByteArray((AssistantAttachments.MAX_DOCUMENT_BYTES + 1).toInt()))
        assertEquals("huge.pdf is larger than 10 MB", refused(listOf("content://big")))
        phoneFile("content://fake", "fake.pdf", "application/pdf", "hello".toByteArray())
        assertEquals("fake.pdf is not a PDF", refused(listOf("content://fake")))
        phoneFile("content://broken", "broken.png", "image/png", byteArrayOf(NOT_AN_IMAGE))
        assertEquals("broken.png is not an image this phone can read", refused(listOf("content://broken")))
        assertEquals("A file from the phone can no longer be read — attach it again", refused(listOf("content://gone")))
        phone["content://denied"] = PhoneFile("denied.txt", "text/plain") { throw SecurityException("no grant") }
        assertEquals("Unable to read denied.txt — attach it again", refused(listOf("content://denied")))
    }

    @Test fun `what was kept for a message is removed when a later file of it fails`() {
        phoneFile("content://ok", "photo.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
        phoneFile("content://fake", "fake.pdf", "application/pdf", "hello".toByteArray())
        try {
            reader.read(workspace, listOf("content://ok", "content://fake"), seeing, keep)
            fail("expected a refusal")
        } catch (refused: AssistantTurnException) {
            assertEquals("fake.pdf is not a PDF", refused.message)
        }
        assertTrue(keptFolder.listFiles()!!.isEmpty())
    }

    @Test fun `at most five files go, each once, and none from outside the workspace`() {
        (1..7).forEach { phoneFile("content://$it", "n$it.txt", "text/plain", "$it".toByteArray()) }
        val read = reader.read(workspace, listOf("content://1", "content://1") + (2..7).map { "content://$it" }, seeing, keep)
        assertEquals(listOf("n1.txt", "n2.txt", "n3.txt", "n4.txt", "n5.txt"), read.map { it.name })
        val outside = temp.newFile("secret.txt")
        assertEquals("secret.txt is not in this workspace", refused(listOf(outside.path)))
    }

    private companion object {
        const val NOT_AN_IMAGE: Byte = 42
    }
}
