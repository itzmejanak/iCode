package dev.icode.mobile.domain.files

/**
 * Where a file is, as a list of files says it under its name (docs/screens-polish-plan.md D1): its
 * folder in the workspace, never the phone's folders iCode keeps the workspace in.
 */
object FileFolders {
    /** [location]'s folder in [root] — "docs", ".github/ISSUE_TEMPLATE" — or [rootName] at its top; outside it, its folder as it is. */
    fun of(location: String, root: String?, rootName: String): String {
        val relative = relative(location, root) ?: return location.substringBeforeLast('/', location)
        return if ('/' in relative) relative.substringBeforeLast('/') else rootName
    }

    /** [location] in [root] ("docs/plan.md"), or null when it is not in it. */
    fun relative(location: String, root: String?): String? =
        root?.let { "${it.trimEnd('/')}/" }?.takeIf { location.startsWith(it) }?.let { location.removePrefix(it) }
}
