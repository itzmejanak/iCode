package dev.icode.mobile.data.language

import dev.icode.mobile.data.runtime.GuestPaths
import java.io.File
import java.net.URI

/**
 * Files as servers name them: `file://` URIs of the path inside the runtime, where every server runs
 * (`/workspace/<name>/…`), and back to the canonical host location iCode keys documents by. The one
 * place either way is made (docs/editor-and-language-services.md, "Paths").
 */
internal class LspUris(private val paths: GuestPaths) {
    /** [location]'s URI; null for a file the runtime cannot see. */
    fun uriOf(location: String): String? = guestPathOf(location)?.let(::uriOfGuest)

    /** [location]'s path inside the runtime. */
    fun guestPathOf(location: String): String? = paths.guestOf(location)

    /** The guest path's URI, its segments percent-encoded as RFC 3986 asks. */
    fun uriOfGuest(guestPath: String): String = URI("file", "", guestPath, null, null).toASCIIString()

    /** The guest path [uri] names; null for anything but a `file:` URI. */
    fun guestOf(uri: String): String? = runCatching {
        val parsed = URI(uri)
        if (!parsed.scheme.equals("file", ignoreCase = true)) null else parsed.path
    }.getOrNull()

    /** The host location [uri] names, as documents are keyed; null for one outside what the runtime binds. */
    fun locationOf(uri: String): String? {
        val guest = guestOf(uri) ?: return null
        paths.workspaces.locationOf(guest)?.let { return it }
        return runCatching { paths.hostOf(guest).let { if (it.exists()) it.canonicalPath else null } }.getOrNull()
    }

    /** [location] relative to [workspace]'s top, as Problems heads it; null outside it. */
    fun relative(workspace: String, location: String): String? {
        val top = File(workspace).path.trimEnd('/') + "/"
        return location.takeIf { it.startsWith(top) }?.removePrefix(top)
    }
}
