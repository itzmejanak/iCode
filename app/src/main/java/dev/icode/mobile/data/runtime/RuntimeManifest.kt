package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.io.DigestAlgorithm
import org.json.JSONObject

/**
 * The root filesystem this build installs, read from the packaged manifest. [entries] is how many the
 * pinned archive holds (`tar -tzf … | wc -l`), what extraction counts against
 * (docs/design-and-navigation.md).
 */
internal data class RootfsDescriptor(
    val id: String,
    val base: String,
    val url: String,
    val sha256: String,
    val minimumFreeBytes: Long,
    val entries: Long,
)

/** A digest a pinned file must match: SHA-256 where iCode pins it, SHA-1 where Google's repository does. */
internal data class Checksum(val algorithm: DigestAlgorithm, val hex: String)

/** A file downloaded at one pinned version: where it is, its size when known, and the digest that proves it. */
internal data class PinnedFile(val url: String, val bytes: Long?, val checksum: Checksum) {
    /** Its name, as its URL ends: what a download is kept as, and how the page lists it. */
    val fileName: String get() = url.substringAfterLast('/')
}

/**
 * `assets/runtime/manifest.json` (docs/runtime-and-terminal.md): the root filesystem, the PRoot it
 * runs under, the files a runtime tool downloads itself ([archives]), and the size of what a tool's own
 * installer fetches ([packageBytes] — the Android SDK's packages, as Google's repository lists them).
 * One reader, so a pin is written once.
 */
internal data class RuntimeManifest(
    val architecture: String,
    val rootfs: RootfsDescriptor,
    val prootVersion: String,
    val archives: Map<String, PinnedFile>,
    val packageBytes: Map<String, Long>,
) {
    /** [id]'s pin; a missing one is this build's mistake, said as such. */
    fun archive(id: String): PinnedFile = archives[id] ?: throw IllegalStateException("The runtime manifest pins no $id")

    companion object {
        const val ASSET = "runtime/manifest.json"
        private const val SUPPORTED_SCHEMA = 1

        /** A pin's digest, by its key in the manifest. */
        private val DIGEST_KEYS = listOf("sha256" to DigestAlgorithm.Sha256, "sha1" to DigestAlgorithm.Sha1)

        fun parse(json: String): RuntimeManifest {
            val manifest = JSONObject(json)
            require(manifest.getInt("schemaVersion") == SUPPORTED_SCHEMA) { "Unsupported runtime manifest schema" }
            val rootfs = manifest.getJSONObject("rootfs")
            val archives = manifest.optJSONObject("archives")
            val packages = manifest.optJSONObject("packageBytes")
            return RuntimeManifest(
                architecture = manifest.getString("architecture"),
                rootfs = RootfsDescriptor(
                    id = rootfs.getString("id"),
                    base = rootfs.getString("base"),
                    url = rootfs.getString("url"),
                    sha256 = rootfs.getString("sha256"),
                    minimumFreeBytes = rootfs.getLong("minimumFreeBytes"),
                    entries = rootfs.getLong("entries"),
                ),
                prootVersion = manifest.getJSONObject("proot").getString("version"),
                archives = archives?.keys()?.asSequence()?.associateWith { id -> pinnedFile(archives.getJSONObject(id)) }.orEmpty(),
                packageBytes = packages?.keys()?.asSequence()?.associateWith { id -> packages.getLong(id) }.orEmpty(),
            )
        }

        private fun pinnedFile(entry: JSONObject): PinnedFile {
            val checksum = DIGEST_KEYS.firstNotNullOfOrNull { (key, algorithm) ->
                entry.optString(key).takeIf { it.isNotEmpty() }?.let { Checksum(algorithm, it.lowercase()) }
            } ?: throw IllegalArgumentException("A pinned file needs a sha256 or a sha1: ${entry.optString("url")}")
            return PinnedFile(entry.getString("url"), entry.optLong("bytes").takeIf { it > 0 }, checksum)
        }
    }
}
