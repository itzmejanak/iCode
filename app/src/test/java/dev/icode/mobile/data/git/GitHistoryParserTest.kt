package dev.icode.mobile.data.git

import dev.icode.mobile.domain.GitRef
import dev.icode.mobile.domain.GitRefKind
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHistoryParserTest {
    // Git 2.43's output for GitHistoryParser.arguments(0, 100) in a repository whose `git log --graph` is
    //   *   edb77cb (HEAD -> refs/heads/main, tag: refs/tags/v1.0) Merge feature
    //   |\
    //   | * 44d812c (refs/heads/feature) Feature: parse the log
    //   * | 3527ae8 (refs/remotes/origin/main, refs/remotes/origin/HEAD) main 3
    //   |/
    //   * 59d77fb main 2
    //   * 6794839 main 1
    private val output =
        "edb77cba37651fb3147fc3aa9ec0b8b522fb2cb7\u00003527ae83e71579cf70eff091e95ae1c6b404112f 44d812c87a666e5041a3ee327cd9bc06912561b3\u0000Ada Lovelace <ada@example.com>\u00001767952800\u0000HEAD -> refs/heads/main, tag: refs/tags/v1.0\u0000Merge feature\u0000\u0000" +
            "44d812c87a666e5041a3ee327cd9bc06912561b3\u000059d77fbd4fa268839e1e7b6a0e4357e0e99f4194\u0000Ada Lovelace <ada@example.com>\u00001767434400\u0000refs/heads/feature\u0000Feature: parse the log\u0000Each record ends with a NUL.\nLines stay as written.\n\u0000" +
            "3527ae83e71579cf70eff091e95ae1c6b404112f\u000059d77fbd4fa268839e1e7b6a0e4357e0e99f4194\u0000Ada Lovelace <ada@example.com>\u00001767520800\u0000refs/remotes/origin/main, refs/remotes/origin/HEAD\u0000main 3\u0000\u0000" +
            "59d77fbd4fa268839e1e7b6a0e4357e0e99f4194\u00006794839311c9f8bdcc5f75e664cdb2b82bd92756\u0000Ada Lovelace <ada@example.com>\u00001767348000\u0000\u0000main 2\u0000\u0000" +
            "6794839311c9f8bdcc5f75e664cdb2b82bd92756\u0000\u0000Ada Lovelace <ada@example.com>\u00001767261600\u0000\u0000main 1\u0000\u0000"

    @Test fun `each record is a commit, with its parents, author, time, subject and body`() {
        val commits = GitHistoryParser.parse(output)
        assertEquals(listOf("edb77cb", "44d812c", "3527ae8", "59d77fb", "6794839"), commits.map { it.id.take(7) })
        val merge = commits[0]
        assertEquals(listOf("3527ae83e71579cf70eff091e95ae1c6b404112f", "44d812c87a666e5041a3ee327cd9bc06912561b3"), merge.parents)
        assertEquals("Ada Lovelace <ada@example.com>", merge.author)
        assertEquals(1_767_952_800_000L, merge.authoredAtEpochMillis)
        assertEquals("Merge feature", merge.subject)
        assertEquals("", merge.body)
        val feature = commits[1]
        assertEquals("Feature: parse the log", feature.subject)
        assertEquals("Each record ends with a NUL.\nLines stay as written.", feature.body)
        assertTrue(commits.last().parents.isEmpty())
    }

    @Test fun `names read as HEAD's branch, branches, remotes' branches and tags, and a remote's HEAD is not one`() {
        val refs = GitHistoryParser.parse(output).map { it.refs }
        assertEquals(listOf(GitRef("main", GitRefKind.Head), GitRef("v1.0", GitRefKind.Tag)), refs[0])
        assertEquals(listOf(GitRef("feature", GitRefKind.Branch)), refs[1])
        assertEquals(listOf(GitRef("origin/main", GitRefKind.Remote)), refs[2])
        assertEquals(emptyList<GitRef>(), refs[3])
    }

    @Test fun `a detached HEAD is named HEAD`() {
        // Git 2.43, HEAD detached at main 3.
        val detached = output.replace("refs/remotes/origin/main, refs/remotes/origin/HEAD", "HEAD, refs/remotes/origin/main, refs/remotes/origin/HEAD")
        assertEquals(listOf(GitRef("HEAD", GitRefKind.Head), GitRef("origin/main", GitRefKind.Remote)), GitHistoryParser.parse(detached)[2].refs)
    }

    @Test fun `nothing printed is no commits, and output cut short is a failure, not fewer commits`() {
        assertEquals(emptyList<Any>(), GitHistoryParser.parse(""))
        assertThrows(IOException::class.java) { GitHistoryParser.parse(output.dropLast(40)) }
    }

    @Test fun `a page asks for its commits after the ones read, in the order the graph needs, without gpg's words`() {
        val arguments = GitHistoryParser.arguments(skip = 200, count = 100)
        assertTrue(arguments.containsAll(listOf("--topo-order", "--decorate=full", "--no-show-signature", "-z", "--skip=200")))
        assertEquals("100", arguments[arguments.indexOf("-n") + 1])
        assertEquals("HEAD", arguments.last())
    }
}
