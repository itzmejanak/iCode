package dev.icode.mobile.data.voice

import dev.icode.mobile.domain.voice.ModelState
import dev.icode.mobile.domain.voice.VoiceCatalog
import dev.icode.mobile.domain.voice.VoiceModel
import java.io.File
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A voice model's files taken from its checked download (docs/phone-agent-plan.md §5.8). */
class VoiceModelsTest {
    @get:Rule val temp = TemporaryFolder()

    private val models by lazy { VoiceModels(temp.newFolder("voice")) }
    private val model = VoiceModel("piper-x", "X voice", "Piper: x", "tts-models/vits-piper-x.tar.bz2", 1, "ab".repeat(32), "vits-piper-x", listOf("x.onnx", "tokens.txt", "espeak-ng-data/"), "MIT")

    /** A tar.bz2 of [entries], by name; a name ending in `/` is a folder. */
    private fun archive(vararg entries: Pair<String, String>): File = temp.newFile().also { file ->
        TarArchiveOutputStream(BZip2CompressorOutputStream(file.outputStream())).use { tar ->
            entries.forEach { (name, text) ->
                val bytes = text.toByteArray()
                tar.putArchiveEntry(TarArchiveEntry(name).apply { size = if (name.endsWith("/")) 0 else bytes.size.toLong() })
                if (!name.endsWith("/")) tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
    }

    @Test fun `only the kept files and folders are taken, inside the model's folder, and it is ready once marked`() {
        val part = archive(
            "vits-piper-x/" to "",
            "vits-piper-x/x.onnx" to "model",
            "vits-piper-x/tokens.txt" to "tokens",
            "vits-piper-x/MODEL_CARD" to "card",
            "vits-piper-x/espeak-ng-data/" to "",
            "vits-piper-x/espeak-ng-data/en_dict" to "dict",
            "vits-piper-x/../../evil" to "no",
            "other/x.onnx" to "no",
        )
        assertFalse(models.ready(model))
        models.install(model, part)
        val folder = models.folder(model)
        assertTrue(models.ready(model))
        assertEquals("model", File(folder, "x.onnx").readText())
        assertEquals("dict", File(folder, "espeak-ng-data/en_dict").readText())
        assertFalse(File(folder, "MODEL_CARD").exists())
        assertFalse(File(temp.root, "evil").exists())
        assertEquals(File(folder, "tokens.txt").path, models.file(model, "tokens.txt"))
    }

    @Test fun `an archive without a kept file is refused and leaves no model, and Remove takes all`() {
        val part = archive("vits-piper-x/x.onnx" to "model")
        val refused = runCatching { models.install(model, part) }.exceptionOrNull()
        assertTrue(refused!!.message!!.contains("tokens.txt"))
        assertFalse(models.ready(model))
        models.install(model, archive("vits-piper-x/x.onnx" to "m", "vits-piper-x/tokens.txt" to "t", "vits-piper-x/espeak-ng-data/a" to "a"))
        assertTrue(models.ready(model))
        models.remove(model)
        assertFalse(models.folder(model).exists())
    }

    @Test fun `a single file is kept as itself, and the catalogue's models on the phone are listed with their size`() {
        val vad = VoiceCatalog.VAD
        val part = temp.newFile("vad.part").apply { writeText("silero") }
        models.install(vad, part)
        assertTrue(models.ready(vad))
        val state = models.states()[vad.id]
        assertTrue(state is ModelState.Ready && state.bytes > 0)
        // A mark of another pin is not this model.
        File(models.folder(vad), ".ready").writeText("0".repeat(64))
        assertFalse(models.ready(vad))
    }
}
