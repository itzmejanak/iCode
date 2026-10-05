package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitActionRulesTest {
    private val ready = paletteReadySnapshot
    private val repository = ready.gitRepository!!

    private fun reasons(snapshot: ICodeSnapshot) = GitSyncAction.entries.map { GitActionRules.syncBlocked(it, snapshot) }

    @Test fun `a branch with a remote and an upstream can fetch, pull and push`() {
        assertEquals(listOf(null, null, null), reasons(ready))
    }

    @Test fun `nothing starts without a workspace, Git, a repository, or while Git is busy`() {
        val cases = mapOf(
            ready.copy(activeWorkspaceLocation = null) to "Open a workspace first",
            ready.copy(gitAvailable = null) to "Checking Git availability",
            ready.copy(gitAvailable = false) to "Git is not installed",
            ready.copy(gitRepository = null) to "Initialize Git first",
            ready.copy(gitOperation = GitOperationState.Running("Fetching")) to "A Git operation is running",
        )
        cases.forEach { (snapshot, reason) -> assertEquals(List(3) { reason }, reasons(snapshot)) }
    }

    @Test fun `each sync needs what Git needs for it`() {
        fun with(change: GitRepositoryState) = reasons(ready.copy(gitRepository = change))
        assertEquals(List(3) { "Add a Git remote first" }, with(repository.copy(remoteName = null)))
        assertEquals(listOf(null, "Check out a branch first", "Check out a branch first"), with(repository.copy(detached = true)))
        assertEquals(listOf(null, null, "Commit changes first"), with(repository.copy(hasCommits = false)))
        assertEquals(listOf(null, "Push this branch first to set its upstream", null), with(repository.copy(upstream = null)))
    }

    @Test fun `the repository alone decides for the coordinator`() {
        assertNull(GitActionRules.syncBlocked(GitSyncAction.Push, repository))
        assertEquals("Check out a branch first", GitActionRules.syncBlocked(GitSyncAction.Push, repository.copy(detached = true)))
    }
}
