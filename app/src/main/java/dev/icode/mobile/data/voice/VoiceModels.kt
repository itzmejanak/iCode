package dev.icode.mobile.data.voice

import dev.icode.mobile.data.io.ArchiveLinks
import dev.icode.mobile.data.io.Archives
import dev.icode.mobile.data.io.DigestAlgorithm
import dev.icode.mobile.data.io.Unpacking
import dev.icode.mobile.data.io.tallyTree
import dev.icode.mobile.data.runtime.Checksum
import dev.icode.mobile.data.runtime.PinnedDownload
import dev.icode.mobile.data.runtime.PinnedFile
import dev.icode.mobile.domain.files.ArchiveFormat
import dev.icode.mobile.domain.voice.ModelState
import dev.icode.mobile.domain.voice.VoiceCatalog
import dev.icode.mobile.domain.voice.VoiceModel
import java.io.File
import java.io.IOException

/**
 * The voice models on this phone (docs/phone-agent-plan.md §5.8), each in its own folder under [root]:
 * downloaded from its pin, checked by size and SHA-256, and only its kept files taken out of the
 * archive — every entry inside the folder, links never made. A folder is the model only once it holds
 * its pin's mark, written last; anything else there is a download cut short, and is removed.
 */
internal class VoiceModels(private val root: File) {
    fun folder(model: VoiceModel): File = File(root, model.id)

    /** [name], one of [VoiceModel.keep], in [model]'s folder. */
    fun file(model: VoiceModel, name: String): String = File(folder(model), name).path

    fun ready(model: VoiceModel): Boolean = runCatching { File(folder(model), READY).readText() == model.sha256 }.getOrDefault(false)

    /** Every model of the catalogue that is on the phone, with what it takes. */
    fun states(): Map<String, ModelState> = VoiceCatalog.models.filter(::ready).associate { model ->
        model.id to ModelState.Ready(tallyTree(folder(model)).bytes ?: 0L)
    }

    /** Downloads [model], reporting the fraction received, then [unpacking]; what fails or is stopped leaves nothing. */
    suspend fun download(model: VoiceModel, progress: suspend (Float) -> Unit, unpacking: () -> Unit) {
        remove(model)
        root.mkdirs()
        val part = File(root, "${model.id}$PART")
        try {
            val pin = PinnedFile(VoiceCatalog.RELEASES + model.path, model.bytes, Checksum(DigestAlgorithm.Sha256, model.sha256))
            PinnedDownload.download(pin, part) { received, total -> progress(if (total > 0) received.toFloat() / total else 0f) }
            unpacking()
            install(model, part)
        } catch (failed: Throwable) {
            remove(model)
            throw failed
        } finally {
            part.delete()
        }
    }

    /** [model]'s checked download [part], its kept files taken into its folder, and the folder marked as the model. */
    fun install(model: VoiceModel, part: File) {
        val folder = folder(model)
        if (model.archive) {
            Archives.unpack(part, ArchiveFormat.TarBz2, folder, Unpacking(ArchiveLinks.None, place = { kept(model, it) }))
        } else {
            folder.mkdirs()
            if (!part.renameTo(File(folder, model.keep.single()))) throw IOException("${model.name} could not be kept")
        }
        model.keep.firstOrNull { !File(folder, it).exists() }?.let { throw IOException("${model.name}'s download has no $it") }
        File(folder, READY).writeText(model.sha256)
    }

    fun remove(model: VoiceModel) {
        folder(model).deleteRecursively()
        File(root, "${model.id}$PART").delete()
    }

    private companion object {
        const val READY = ".ready"
        const val PART = ".part"

        /** An archive's [entry] as a path in the model's folder when the model keeps it; null leaves it out. */
        fun kept(model: VoiceModel, entry: String): String? {
            val path = entry.removePrefix("./").removePrefix("${model.folder}/").takeIf { it != entry.removePrefix("./") } ?: return null
            return path.takeIf { name -> model.keep.any { if (it.endsWith("/")) name.startsWith(it) || "$name/" == it else name == it } }
        }
    }
}
