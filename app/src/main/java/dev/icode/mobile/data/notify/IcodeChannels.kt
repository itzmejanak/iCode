package dev.icode.mobile.data.notify

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager

/** How Android's settings list iCode's channels (docs/notices-redesign-plan.md D7). */
internal enum class IcodeChannelGroup(val id: String, val title: String) {
    Running("icode.group.running", "Running"),
    NeedsYou("icode.group.needs-you", "Needs you"),
    Updates("icode.group.updates", "Updates"),
}

/**
 * Every channel iCode posts to (D7). An id that keeps its meaning stays, so the user's choices for it
 * stay: Android lets a channel's name, description and group change, never its behaviour once made.
 */
internal enum class IcodeChannel(
    val id: String,
    val title: String,
    val description: String,
    val importance: Int,
    val group: IcodeChannelGroup,
    /** Whether it may put a dot on iCode's icon. */
    val badge: Boolean = true,
) {
    Running(
        "icode.runtime", "Running in the background", "While terminal sessions or long operations run",
        NotificationManager.IMPORTANCE_LOW, IcodeChannelGroup.Running, badge = false,
    ),

    // Minimum importance, as the reference's: no sound, no pop-up, no status-bar icon of its own.
    Progress(
        "icode.work-progress", "Progress", "Each long operation's progress: deleting, copying, importing, cloning",
        NotificationManager.IMPORTANCE_MIN, IcodeChannelGroup.Running, badge = false,
    ),
    PhoneControl(
        "icode.phone-control", "Phone control", "While the assistant can use this phone",
        NotificationManager.IMPORTANCE_LOW, IcodeChannelGroup.Running, badge = false,
    ),
    Questions(
        "icode.work-questions", "Needs your answer", "Work waiting for you: the assistant asking before a change or a command, GnuPG asking for a passphrase",
        NotificationManager.IMPORTANCE_HIGH, IcodeChannelGroup.NeedsYou,
    ),
    Problems(
        "icode.problems", "Problems", "A problem iCode meets, or work that stopped short, while you are elsewhere",
        NotificationManager.IMPORTANCE_HIGH, IcodeChannelGroup.NeedsYou,
    ),
    ProgramAsks(
        "icode.terminal-alerts", "A program asks for you", "A terminal program rang the bell while you were elsewhere",
        NotificationManager.IMPORTANCE_DEFAULT, IcodeChannelGroup.NeedsYou,
    ),

    // A sound and no pop-up (owner's choice, 2026-11-11).
    Finished(
        "icode.finished", "Finished while you were away", "Work that ran 30 seconds or more and ended while iCode was out of view: a long command, a pull, a clone, an install",
        NotificationManager.IMPORTANCE_DEFAULT, IcodeChannelGroup.Updates,
    ),
    Messages(
        "icode.messages", "Messages", "What needs you, as iCode's Notifications page keeps it: no sound, no pop-up",
        NotificationManager.IMPORTANCE_LOW, IcodeChannelGroup.Updates,
    ),
}

internal object IcodeChannels {
    /**
     * Channels an earlier build made that iCode no longer posts to; deleting one takes what it holds
     * with it. Task failures are Problems now (owner's choice, 2026-11-11).
     */
    val RETIRED = listOf("icode.work-failures")

    /** Makes every group and channel, as they are now, and deletes the retired: once, as iCode's process starts. */
    fun register(manager: NotificationManager) {
        manager.createNotificationChannelGroups(IcodeChannelGroup.entries.map { NotificationChannelGroup(it.id, it.title) })
        manager.createNotificationChannels(
            IcodeChannel.entries.map { channel ->
                NotificationChannel(channel.id, channel.title, channel.importance).apply {
                    description = channel.description
                    group = channel.group.id
                    setShowBadge(channel.badge)
                }
            },
        )
        RETIRED.forEach(manager::deleteNotificationChannel)
    }
}
