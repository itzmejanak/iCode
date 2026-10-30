package dev.icode.mobile.data.runtime

import android.content.Context
import dev.icode.mobile.data.web.ICodeWebClient
import dev.icode.mobile.domain.ManifestOrigin
import org.json.JSONObject
import java.io.File

/**
 * Where the runtime manifest comes from (contracts/api-v1.md § Runtime): the website's published one
 * overrides the one built into iCode, when it passes the same checks ([RuntimeManifest.parse]) and is
 * for the same architecture. Unreachable, unpublished or failing, the last good website copy stays
 * until the website says there is none; then the built-in one applies. Nothing is reinstalled
 * because the manifest changed: it applies to the next install or tool download.
 */
internal object ManifestSource {
    private const val DIRECTORY = "runtime"
    private const val CACHE = "manifest.website.json"
    private const val ETAG = "manifest.website.etag"

    /** What a parse found: the manifest to use, and whether the website's was the one. */
    data class Choice(val manifest: RuntimeManifest, val origin: ManifestOrigin)

    /**
     * The one rule, kept free of Android so it is tested on its own: [website] wins when it parses
     * and its architecture is [bundled]'s; [bundled] otherwise.
     */
    fun choose(bundled: String, website: String?): Choice {
        val builtIn = RuntimeManifest.parse(bundled)
        val override = website?.let { text ->
            runCatching { RuntimeManifest.parse(text) }.getOrNull()
                ?.takeIf { it.architecture == builtIn.architecture }
                ?.let { Choice(it, ManifestOrigin(fromWebsite = true, revision = revisionOf(text))) }
        }
        return override ?: Choice(builtIn, ManifestOrigin())
    }

    fun read(context: Context): RuntimeManifest = current(context).manifest

    fun origin(context: Context): ManifestOrigin = current(context).origin

    private fun current(context: Context): Choice = choose(bundled(context), cache(context).takeIf { it.isFile }?.readText())

    /**
     * Asks the website; returns the origin in use afterwards. A failure to reach it is not an
     * answer, so the kept copy stays (blocking: call from Dispatchers.IO).
     */
    fun refresh(context: Context, client: ICodeWebClient): ManifestOrigin {
        if (!client.configured) return origin(context)
        val bundled = bundled(context)
        val architecture = RuntimeManifest.parse(bundled).architecture
        val etagFile = File(directory(context), ETAG)
        val etag = etagFile.takeIf { it.isFile && cache(context).isFile }?.readText()
        when (val fetch = runCatching { client.runtimeManifest(architecture, etag) }.getOrNull()) {
            is ICodeWebClient.ManifestFetch.Fetched -> {
                // Kept only when the app itself can use it; a bad publish leaves the previous copy.
                if (choose(bundled, fetch.json).origin.fromWebsite) {
                    writeAtomically(cache(context), fetch.json)
                    fetch.etag?.let { writeAtomically(etagFile, it) } ?: etagFile.delete()
                }
            }
            ICodeWebClient.ManifestFetch.NotPublished -> {
                cache(context).delete()
                etagFile.delete()
            }
            ICodeWebClient.ManifestFetch.NotModified, null -> Unit
        }
        return origin(context)
    }

    private fun bundled(context: Context): String =
        context.assets.open(RuntimeManifest.ASSET).bufferedReader().use { it.readText() }

    private fun directory(context: Context): File = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    private fun cache(context: Context): File = File(directory(context), CACHE)

    private fun revisionOf(json: String): Int? = runCatching { JSONObject(json).optInt("revision").takeIf { it > 0 } }.getOrNull()

    /** Readers never see half a file: written beside it, then moved over it. */
    private fun writeAtomically(target: File, text: String) {
        val temporary = File(target.parentFile, target.name + ".tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(target)) {
            target.delete()
            temporary.renameTo(target)
        }
    }
}
