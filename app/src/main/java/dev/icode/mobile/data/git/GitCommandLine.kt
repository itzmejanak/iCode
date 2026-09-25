package dev.icode.mobile.data.git

/**
 * Builds the shell script that runs one Git command inside the guest.
 *
 * The runtime's process bridge merges stderr into stdout for non-PTY commands,
 * which would corrupt NUL-delimited porcelain output with any warning Git
 * prints. So stderr is redirected to a file the host reads afterwards, keeping
 * stdout byte-exact while still surfacing Git's own error text (PR-GIT-12).
 *
 * Every argument is quoted, because Git operates on user paths that routinely
 * contain spaces, quotes and `$`.
 */
internal object GitCommandLine {

    /** Where the guest writes stderr; read back from the host as a normal file. */
    const val GUEST_STDERR_PATH = "/tmp/icode-git-stderr"

    /** Path of [GUEST_STDERR_PATH] relative to the root filesystem directory. */
    const val STDERR_RELATIVE_PATH = "tmp/icode-git-stderr"

    /**
     * Wraps [arguments] as `git ... 2>stderrfile`.
     *
     * Git is invoked with `-c core.quotepath=false` so non-ASCII paths come
     * back as UTF-8 rather than octal escapes, and with `--no-optional-locks`
     * so a read-only status never takes the index lock away from a command the
     * user is running in their own terminal.
     */
    fun script(arguments: List<String>): String {
        val command = (BASE_ARGUMENTS + arguments).joinToString(" ") { quote(it) }
        return "$command 2>${quote(GUEST_STDERR_PATH)}"
    }

    /**
     * POSIX single-quoting.
     *
     * Single quotes protect everything except a single quote itself, which is
     * closed, escaped and reopened — the standard `'\''` dance.
     */
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private val BASE_ARGUMENTS = listOf(
        "git",
        "-c", "core.quotepath=false",
        // The workspace is an Android directory bound into the guest, owned by
        // a media uid while Git runs as fake-root. Ownership can never match,
        // so Git's dubious-ownership check would refuse every command. The
        // directory is one the user explicitly chose to open.
        "-c", "safe.directory=*",
        "--no-optional-locks",
    )
}
