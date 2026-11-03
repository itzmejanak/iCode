package dev.icode.mobile.data.voice

import dev.icode.mobile.domain.voice.VoiceCatalog
import dev.icode.mobile.domain.voice.VoiceModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** sherpa-onnx is given only files each model keeps (docs/phone-agent-plan.md §3.4). */
class SherpaConfigsTest {
    private val asked = mutableListOf<Pair<VoiceModel, String>>()
    private val file = { model: VoiceModel, name: String -> asked += model to name; "/m/${model.id}/$name" }

    private fun assertKept() = asked.forEach { (model, name) ->
        assertTrue("${model.id} keeps no $name", model.keep.any { it == name || it == "$name/" })
    }

    @Test fun `every voice reads only its model's kept files, Chinese rules with Kokoro's lexicons`() {
        VoiceCatalog.voices.forEach { SherpaConfigs.speaker(it, file) }
        assertKept()
        val heart = SherpaConfigs.speaker(VoiceCatalog.voice("kokoro/af_heart"), file)
        assertEquals("/m/kokoro/lexicon-us-en.txt,/m/kokoro/lexicon-zh.txt", heart.model.kokoro.lexicon)
        assertEquals("", heart.model.kokoro.lang)
        assertTrue(heart.ruleFsts.contains("number-zh.fst"))
        val spanish = SherpaConfigs.speaker(VoiceCatalog.voice("kokoro/ef_dora"), file)
        assertEquals("es", spanish.model.kokoro.lang)
        assertEquals("", spanish.ruleFsts)
        val german = SherpaConfigs.speaker(VoiceCatalog.voice("piper/de_DE-thorsten-medium"), file)
        assertEquals("/m/piper-de_DE-thorsten-medium/de_DE-thorsten-medium.onnx", german.model.vits.model)
    }

    @Test fun `listeners and the detector read only their kept files, Whisper finding the language itself`() {
        VoiceCatalog.LISTENERS.forEach { SherpaConfigs.recognizer(it, file) }
        SherpaConfigs.vad(file)
        assertKept()
        val whisper = SherpaConfigs.recognizer(VoiceCatalog.WHISPER_BASE, file).modelConfig
        assertEquals("", whisper.whisper.language)
        assertEquals("whisper", whisper.modelType)
        assertEquals(16_000, SherpaConfigs.vad(file).sampleRate)
    }
}
