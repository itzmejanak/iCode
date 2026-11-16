package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.RuntimeInstallReason

/** What the Linux runtime's install, repair and reset tell (docs/engineering-rules.md). */
object RuntimeNotices {
    private val runtime = NoticePlace(NoticePlaces.RUNTIME)

    fun busy() = Notice(NoticeTone.HeadsUp, NoticeSource.Runtime, "Wait for the runtime install to finish", runtime)

    fun closeFirst() = Notice(
        NoticeTone.HeadsUp, NoticeSource.Runtime, "Close what runs in the runtime first", runtime,
        detail = "Terminal sessions, language servers and processes run in it. Then rebuild it.",
    )

    /** Done; [away]: it ran long enough to be worth the shade, should iCode be out of view by its end. */
    fun installed(reason: RuntimeInstallReason, away: Boolean) =
        Notice(NoticeTone.Done, NoticeSource.Runtime, "${verbOf(reason, past = true)} the Linux runtime", runtime, tellAway = away)

    /** Its reason — the installer's words — is the output; a first install can be tried again from here. */
    fun notInstalled(reason: RuntimeInstallReason, words: String) = Notice(
        NoticeTone.Problem, NoticeSource.Runtime, "Couldn't ${verbOf(reason, past = false)} the Linux runtime", runtime,
        output = words,
        actions = listOfNotNull(NoticeAction.Run(NoticeCommand.InstallRuntime, "Try Again").takeIf { reason == RuntimeInstallReason.Initial }),
    )

    private fun verbOf(reason: RuntimeInstallReason, past: Boolean): String = when (reason) {
        RuntimeInstallReason.Initial -> if (past) "Installed" else "install"
        RuntimeInstallReason.Repair -> if (past) "Repaired" else "repair"
        RuntimeInstallReason.Reset -> if (past) "Reset" else "reset"
    }
}
