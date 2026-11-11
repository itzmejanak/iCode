package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.domain.files.ExplorerState
import dev.icode.mobile.domain.files.FileHistoryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeRulesTest {
    private fun notice(at: Long, tone: NoticeTone, read: Boolean = false, keep: NoticeKeep = NoticeKeep.of(tone), repeatKey: String? = null) =
        Notice(tone, NoticeSource.Files, "said at $at", keep = keep, repeatKey = repeatKey, id = "n$at", at = at, read = read)

    private fun snapshot(open: String? = null, undo: String? = null) = ICodeSnapshot(
        workspaces = emptyList(),
        files = emptyList(),
        gitChanges = emptyList(),
        diagnostics = emptyList(),
        languageServices = emptyList(),
        activeWorkspace = open?.substringAfterLast('/').orEmpty(),
        activePath = open.orEmpty(),
        activeWorkspaceLocation = open,
        activeBranch = "",
        runtimeHealthy = true,
        explorer = ExplorerState(history = FileHistoryState(undo = undo)),
    )

    @Test fun `a success is kept without ringing the bell, a moment rings without being kept`() {
        val done = notice(1, NoticeTone.Done)
        assertTrue(NoticeRules.kept(done))
        assertFalse(NoticeRules.rings(done))
        val moment = notice(2, NoticeTone.Done, keep = NoticeKeep.Moment)
        assertFalse(NoticeRules.kept(moment))
        assertTrue(NoticeRules.rings(moment))
        listOf(NoticeTone.Problem, NoticeTone.HeadsUp, NoticeTone.Info).forEach {
            assertTrue(NoticeRules.kept(notice(3, it)))
            assertTrue(NoticeRules.rings(notice(3, it)))
        }
    }

    @Test fun `problems and heads-ups need the user, the rest are kept as done`() {
        assertEquals(
            listOf(NoticeKeep.NeedsYou, NoticeKeep.NeedsYou, NoticeKeep.Done, NoticeKeep.Done),
            NoticeTone.entries.map(NoticeKeep::of),
        )
    }

    @Test fun `a problem keeps the bell longest`() {
        assertEquals(listOf(15_000L, 10_000L, 10_000L, 3_000L), NoticeTone.entries.map(NoticeRules::bellMillis))
    }

    @Test fun `the bell takes the most serious of what needs the user, unread`() {
        assertNull(NoticeRules.bellTone(listOf(notice(1, NoticeTone.Problem, read = true), notice(2, NoticeTone.Info))))
        assertEquals(NoticeTone.HeadsUp, NoticeRules.bellTone(listOf(notice(1, NoticeTone.HeadsUp), notice(2, NoticeTone.Problem, read = true))))
        assertEquals(NoticeTone.Problem, NoticeRules.bellTone(listOf(notice(1, NoticeTone.HeadsUp), notice(2, NoticeTone.Problem))))
    }

    @Test fun `the shade holds the newest unread that need the user, and what finished away`() {
        val many = (1..NoticeRules.MAX_POSTED + 3L).map { notice(it, NoticeTone.Problem) } +
            notice(100, NoticeTone.Problem, read = true) + notice(101, NoticeTone.Done) + notice(102, NoticeTone.Problem).copy(dismissed = true)
        val posted = NoticeRules.posted(many)
        assertEquals(NoticeRules.MAX_POSTED, posted.size)
        assertEquals("n${NoticeRules.MAX_POSTED + 3}", posted.first().id)
        assertEquals("n4", posted.last().id)
        assertEquals(listOf("n9"), NoticeRules.posted(listOf(notice(9, NoticeTone.Done).copy(away = true))).map { it.id })
    }

    @Test fun `the same event told again soon folds into the one before`() {
        val first = notice(1_000, NoticeTone.Problem, repeatKey = "save")
        val again = NoticeRules.added(listOf(first.copy(read = true)), notice(2_000, NoticeTone.Problem, repeatKey = "save"))
        assertEquals(listOf("n2000"), again.map { it.id })
        assertEquals(2, again.single().repeats)
        assertFalse(again.single().read)
        val later = NoticeRules.added(listOf(first), notice(1_000 + NoticeRules.FOLD_MILLIS + 1, NoticeTone.Problem, repeatKey = "save"))
        assertEquals(2, later.size)
    }

    @Test fun `what needs the user and the rest are each cut to their own most`() {
        var notices = (1..NoticeRules.MAX_NEEDS_YOU + 2L).fold(emptyList<Notice>()) { list, at -> NoticeRules.added(list, notice(at, NoticeTone.Problem)) }
        notices = (1..NoticeRules.MAX_DONE + 2L).fold(notices) { list, at -> NoticeRules.added(list, notice(1_000 + at, NoticeTone.Done)) }
        assertEquals(NoticeRules.MAX_NEEDS_YOU, notices.count { it.keep == NoticeKeep.NeedsYou })
        assertEquals(NoticeRules.MAX_DONE, notices.count { it.keep == NoticeKeep.Done })
        assertEquals("n3", notices.last().id)
    }

    @Test fun `undo is offered while its change is still the one undo reverses`() {
        val moved = notice(1, NoticeTone.Done).copy(actions = listOf(NoticeAction.Undo("Moved notes.md to Trash")))
        fun offered(undo: String?) = NoticeRules.available(moved, snapshot(undo = undo))
        assertEquals(moved.actions, offered("Moved notes.md to Trash"))
        assertTrue(offered("Renamed a.md to b.md").isEmpty())
        assertTrue(offered(null).isEmpty())
    }

    @Test fun `what acts in a workspace is offered only while it is the open one`() {
        val pulled = notice(1, NoticeTone.Problem).copy(
            place = NoticePlace(workspace = "phone", workspaceLocation = "/w/phone"),
            actions = listOf(NoticeAction.Run(NoticeCommand.Pull, "Try Again"), NoticeAction.Show(NoticeTarget.SourceControl)),
        )
        assertEquals(pulled.actions, NoticeRules.available(pulled, snapshot(open = "/w/phone")))
        assertTrue(NoticeRules.available(pulled, snapshot(open = "/w/other")).isEmpty())
        val runtime = pulled.copy(actions = listOf(NoticeAction.Show(NoticeTarget.RuntimeTools), NoticeAction.OpenUrl("https://x.dev")))
        assertEquals(runtime.actions, NoticeRules.available(runtime, snapshot(open = "/w/other")))
    }

    @Test fun `the page filters by kind and by place`() {
        val here = notice(1, NoticeTone.Problem).copy(place = NoticePlace(workspace = "phone"))
        val there = notice(2, NoticeTone.Done).copy(place = NoticePlace(workspace = "Phone storage"))
        val nowhere = notice(3, NoticeTone.Info)
        val all = listOf(nowhere, there, here)
        assertEquals(listOf(here), all.filter(NoticeFilter.NeedsYou::matches))
        assertEquals(listOf(nowhere, there), all.filter(NoticeFilter.Done::matches))
        assertEquals(listOf(here), all.filter(NoticeFilter.At("phone")::matches))
        assertEquals(listOf(nowhere), all.filter(NoticeFilter.Elsewhere::matches))
        assertEquals(listOf("Phone storage", "phone"), NoticeFilter.placesOf(all))
    }

    @Test fun `a place reads as one line, the branch going where it goes`() {
        assertEquals("phone · app · main → origin/main", NoticePlace("phone", repository = "app", branch = "main", remote = "origin/main").line)
        assertEquals("phone · src", NoticePlace("phone", subject = "src").line)
    }
}
