package dev.icode.mobile.data.git

import dev.icode.mobile.data.runtime.shellQuote

/**
 * The argument vector of one Git command inside the guest.
 *
 * Each argument stays one argument — the guest command quotes every one —
 * because Git operates on user paths that routinely contain spaces, quotes
 * and `$`. Stderr goes to a file of its own, so NUL-delimited porcelain output
 * on stdout stays byte-exact while Git's own error text is still read (PR-GIT-12).
 */
internal object GitCommandLine {

    /**
     * `git` with the options every command needs, then [arguments].
     *
     * `-c core.quotepath=false` returns non-ASCII paths as UTF-8 rather than
     * octal escapes, and `--no-optional-locks` keeps a read-only status from
     * taking the index lock from a command the user runs in their own terminal.
     * [config] (`key=value`) holds for this command alone.
     */
    fun argv(arguments: List<String>, config: List<String> = emptyList()): List<String> =
        BASE_ARGUMENTS + config.flatMap { listOf("-c", it) } + arguments

    /**
     * POSIX single-quoting, for the few constant scripts that are not one Git command.
     *
     * Single quotes protect everything except a single quote itself, which is
     * closed, escaped and reopened — the standard `'\''` dance.
     */
    fun quote(value: String): String = shellQuote(value)

    private val BASE_ARGUMENTS = listOf(
        "git",
        "-c", "core.quotepath=false",
        "--no-optional-locks",
    )
}
