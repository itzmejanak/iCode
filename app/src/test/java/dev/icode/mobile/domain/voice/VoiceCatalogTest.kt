package dev.icode.mobile.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The models iCode offers, as pinned (docs/phone-agent-plan.md §3.4). */
class VoiceCatalogTest {
    @Test fun `every model is pinned by size and SHA-256, under its own id and folder`() {
        val models = VoiceCatalog.models
        assertEquals(36, models.size)
        assertEquals(models.size, models.map { it.id }.distinct().size)
        models.forEach { model ->
            assertTrue(model.id, Regex("[0-9a-f]{64}").matches(model.sha256))
            assertTrue(model.id, model.bytes > 0)
            assertTrue(model.id, model.keep.isNotEmpty())
            assertEquals(model.id, model.archive, model.path.endsWith(".tar.bz2"))
        }
        assertEquals(models.size, models.map { it.sha256 }.distinct().size)
    }

    @Test fun `Kokoro's speakers keep their numbers in voices bin, Japanese left out`() {
        val heart = VoiceCatalog.voice("kokoro/af_heart")
        assertEquals(3, heart.speaker)
        assertEquals("English (US)", heart.language)
        assertEquals(VoiceCatalog.KOKORO, heart.model)
        assertEquals(53, VoiceCatalog.voice("kokoro/em_santa").speaker)
        assertEquals(49, VoiceCatalog.voices.count { it.model == VoiceCatalog.KOKORO })
        assertTrue(VoiceCatalog.voices.none { it.id.startsWith("kokoro/j") })
    }

    @Test fun `English and Mandarin read with Kokoro's lexicons, the others with espeak-ng`() {
        VoiceCatalog.voices.filter { it.model == VoiceCatalog.KOKORO }.forEach { voice ->
            assertTrue(voice.id, voice.lexicons.isEmpty() != voice.lang.isEmpty())
            voice.lexicons.forEach { assertTrue(voice.id, it in voice.model.keep) }
        }
        assertEquals("pt-br", VoiceCatalog.voice("kokoro/pf_dora").lang)
    }

    @Test fun `a voice or a listener not in the catalogue falls back to the default`() {
        assertEquals(VoiceCatalog.DEFAULT_VOICE, VoiceCatalog.voice("gone").id)
        assertEquals(VoiceCatalog.WHISPER_BASE, VoiceCatalog.listener("gone"))
        assertEquals(VoiceCatalog.SENSE_VOICE, VoiceCatalog.listener("sense-voice"))
    }

    @Test fun `languages are listed once, sorted, each with its voices`() {
        val languages = VoiceCatalog.languages
        assertEquals(languages.sorted(), languages)
        assertEquals(40, languages.size)
        assertEquals(listOf("piper/de_DE-thorsten-medium"), VoiceCatalog.voicesIn("German").map { it.id })
        assertFalse(VoiceCatalog.voicesIn("Spanish").isEmpty())
    }

    @Test fun `settings need the detector and their models only for the engines on this phone`() {
        assertEquals(emptyList<VoiceModel>(), VoiceSettings().listenModels + VoiceSettings().speakModels)
        val here = VoiceSettings(listening = VoiceEngine.OnPhone, listenModel = "sense-voice", speaking = VoiceEngine.OnPhone, voice = "piper/de_DE-thorsten-medium")
        assertEquals(listOf(VoiceCatalog.VAD, VoiceCatalog.SENSE_VOICE), here.listenModels)
        assertEquals(listOf("piper-de_DE-thorsten-medium"), here.speakModels.map { it.id })
    }
}
