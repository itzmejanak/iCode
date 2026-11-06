package dev.icode.mobile.data.runtime

/**
 * Who a process in the runtime runs for (docs/runtime-contract-plan.md §3.2). Every guest process is
 * told its own in [GuestVariables.CONTEXT], and starts with [GuestVariables.of] it: the base a Linux
 * login sets, the user's profile over it where the context reads one, the context's own over both.
 */
internal enum class GuestContext(val id: String, val readsProfile: Boolean) {
    /** A terminal tab: its login shell reads the profile itself. */
    Terminal("terminal", readsProfile = false),
    /** A chat's terminal tab (docs/agent-control-plan.md §4.2): a terminal whose bash reads the profile itself, where nothing pages and nothing waits on a password the assistant cannot see. */
    AssistantTerminal("assistant-terminal", readsProfile = false),
    /** What runs on the user's behalf without a terminal: the assistant's commands and processes, the Run tab, Build & Run, the Runtime Tools' installs. */
    Command("command", readsProfile = true),
    /** Source Control's Git, the assistant's Git views and the gpg agent signing needs: hooks, credential helpers and gpg are the user's programs. */
    Git("git", readsProfile = true),
    LanguageServer("language-server", readsProfile = true),
    Search("search", readsProfile = true),
    /** The runtime's own install, Repair and checks, and the reading of the profile itself: a broken profile never stops them. */
    Install("install", readsProfile = false),
}

internal object GuestVariables {
    /** The variable naming a process's [GuestContext]. */
    const val CONTEXT = "ICODE_CONTEXT"

    /** What a Linux login sets before the user's profile: root, the runtime's only user, and its login [shell]. */
    fun base(shell: String): Map<String, String> = mapOf(
        "HOME" to RuntimeLayout.GUEST_HOME,
        "USER" to USER,
        "LOGNAME" to USER,
        "SHELL" to shell,
        "PATH" to RuntimeLayout.GUEST_PATH,
        "LANG" to "C.UTF-8",
        // PRoot's limit: guest glibc's restartable sequences misbehave under its tracing.
        "GLIBC_TUNABLES" to "glibc.pthread.rseq=0",
    )

    /** True of the terminal emulator, which also draws 24-bit colour. */
    private val TERMINAL = mapOf("TERM" to "xterm-256color", "COLORTERM" to "truecolor")

    /** No one is there to answer: nothing pages, nothing waits on a prompt. */
    private val NO_WAITING = mapOf(
        "PAGER" to "cat",
        "GIT_PAGER" to "cat",
        "GIT_TERMINAL_PROMPT" to "0",
        "DEBIAN_FRONTEND" to "noninteractive",
    )

    /** No terminal is attached either. */
    private val NO_TERMINAL = mapOf("TERM" to "dumb") + NO_WAITING

    /** [context]'s own variables, over the base and the profile. */
    fun own(context: GuestContext): Map<String, String> = when (context) {
        GuestContext.Terminal -> TERMINAL
        GuestContext.AssistantTerminal -> TERMINAL + NO_WAITING
        GuestContext.Install -> emptyMap()
        else -> NO_TERMINAL
    }

    /** [context]'s variables: the base for [shell], then [profile] over it, then the context's own over both. */
    fun of(context: GuestContext, shell: String, profile: Map<String, String> = emptyMap()): Map<String, String> =
        base(shell) + profile + own(context) + (CONTEXT to context.id)

    private const val USER = "root"
}
