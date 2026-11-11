package dev.icode.mobile.domain.notices

/** What the iCode website says, and what iCode says of links (docs/notices-redesign-plan.md D8). */
object WebNotices {
    /**
     * A notice the website's admin wrote, by its [level]: kept as needing the user whatever its level,
     * so an announcement is counted and posted as before.
     */
    fun fromWebsite(id: String, at: Long, level: String, title: String, body: String?, link: String?, linkLabel: String?) = Notice(
        toneOf(level),
        NoticeSource.Website,
        title,
        detail = body,
        actions = listOfNotNull(link?.let { NoticeAction.OpenUrl(it, linkLabel ?: "Open Link") }),
        keep = NoticeKeep.NeedsYou,
        id = Notice.WEBSITE_PREFIX + id,
        at = at,
    )

    fun toneOf(level: String): NoticeTone = when (level) {
        "critical" -> NoticeTone.Problem
        "warning" -> NoticeTone.HeadsUp
        else -> NoticeTone.Info
    }

    fun noBrowser() = Notice(NoticeTone.HeadsUp, NoticeSource.ICode, "Install a browser to open links", detail = "No app on this phone opens web pages.")
}
