# Source Control — Commit History

Status: **built (2026-10-22)**. §6 records how it was checked; the phone check is §6.3.

Owner ask (2026-10-22): "On the last commit card put the two sided expand icon at right side top so user can see commit history from sheet make sure to show commit tree into best way in clean oneline manner clicking line user can see expanded full commit message over there … best behavior ensuring scalability and cleanness and no duplicacy". The icon is the owner's picture: two corners pulled apart, ┌ above ┘.

## 1. What the owner sees

- **Last Commit's corner.** Source Control's Last Commit card has the expand icon at its top right, in the accent, beside the card's label. It shows only once there is a commit. The icon takes no more of the header than its own 18 dp; its 40 dp ripple spreads into the card's padding, and a touch 15 dp off its centre still opens the sheet (§6.2).
- **The History sheet.** Titled *Commit History*, it lists HEAD's commits newest first. Each is one line beside the graph of where it branched and merged, as `git log --graph --oneline --decorate` draws it.
- **A line.** The graph, then:
  - the short id (7 characters, as `--oneline` writes it);
  - up to two names (HEAD's branch filled in the accent, other branches green, remotes' branches blue, tags amber), then "+N" for any more;
  - the subject, cut short on one line;
  - how long ago.
- **An open line.** A tap opens a line, and any number can be open. It shows the commit as Last Commit's card tells it: the id (12 characters), the subject, the rest of the message, the author and when. Every name it carries is listed when its line had no room for them all. The text can be selected and copied. The graph's lanes run on beside it, and a tap on the line closes it.
- **Its end.** The next page is read before the list reaches it. The foot shows a progress track while a page is read, Git's words and *Try Again* when one could not be, and "N commits" once all are read.

## 2. Reading the history (`GitHistoryParser`)

One `git log` reads one page, through the runtime's Git like every other command (`CliGitService.run`, in the chosen repository):

```
git log --topo-order --decorate=full --no-show-signature -z --skip=<read> -n <page> \
  --format=%H%x00%P%x00%an <%ae>%x00%at%x00%D%x00%s%x00%b HEAD
```

| Choice | Why |
|---|---|
| `--topo-order` | Every commit comes before its parents, as the graph needs; it is the order `git log --graph` itself uses. The order is the same on each run for the same refs, so `--skip` pages through it. |
| `--decorate=full` with `%D` | Names come in full (`HEAD -> refs/heads/main, refs/remotes/origin/main, tag: refs/tags/v1.0`), so a branch is told from a remote's branch and a tag by Git's own word, not by guessing from a slash. |
| `--no-show-signature` | With `log.showSignature` set, gpg's verdict would be printed between the records. |
| `-z` and `%x00` | A NUL ends each field, and `-z` ends each record with one. Git refuses a NUL in a commit message (`commit.c`: "a NUL byte in commit log message not allowed"), and names and idents cannot hold one. So 7 fields a commit always read back exactly; output cut short is a failure, never fewer commits. |
| `%an <%ae>` | The author as Last Commit names one (`GitInspectParser`): one way to name an author. |
| `%s` and `%b` | Git's own split of a message: its first paragraph on one line, then the rest. |

Names read as: `HEAD -> refs/heads/x` is HEAD's branch; a bare `HEAD` is a detached HEAD; `refs/heads/` is a branch; `refs/remotes/` is a remote's branch, except `*/HEAD`, which only says which branch is the remote's own; `tag: refs/tags/` is a tag. Anything else (the stash, notes) is not a name a commit is known by.

All of this was checked against Git 2.43's real output (§6.1). HEAD with no commit fails with exit 128, but the sheet never asks: it opens only from a commit, and `Open` with no HEAD records an empty, complete history.

## 3. Paging and freshness (`GitHistoryCoordinator`)

- **Off the lock.** Reading the history changes nothing, so `GitHistoryIntent` is routed beside the other reads, never behind the mutation lock. One read runs at a time: a new `Open` cancels the read before it.
- **Pages of 100.** That is a screen's worth many times over, for one PRoot start each. `withPage` lays each page out after the last (§4.1) and marks the history complete at the first page shorter than 100.
- **Read afresh on each open.** A branch, a tag or a remote's branch may have come to a commit since the last open, without HEAD moving. While HEAD is where it was, the rows already read stay on screen until the first page lands in their place.
- **HEAD moved.** The sheet re-opens the history when HEAD moves while it is open (`LaunchedEffect` keyed on the workspace and `lastCommit.id`). `GitHistory.isFor(workspace, head)` is the one rule for whose history is shown: never another HEAD's, the sheet waits instead.
- **Failure.** A failed page keeps the rows before it, with Git's words. `More`, which is what *Try Again* sends, reads that same page again; nothing is fetched by itself until then.
- **Stale reads.** A read that lands after the sheet opened again is dropped: the state it started from is no longer the one shown.

## 4. The sheet (`GitHistoryScreen`, `GitHistoryLine`)

### 4.1 The graph's layout (`GitGraph.layout`, domain)

Each lane holds the id of the commit it waits for, or null for a lane left open. Going down the commits in topological order:

1. A commit takes the lane waiting for it, or else the first open lane (a new lane at the right if none is open).
2. Every lane waiting for it meets it there and is freed.
3. Its first parent goes on in its lane. A merge's other parents join a lane already waiting for them, or open one.
4. Open lanes at the right end are dropped.

Each row keeps the lanes as the row above left them (`above`), as it leaves them (`below`), its lane, and its parents' lanes. Starting a page from the last row's `below` lays the history out exactly as if it were read whole (tested).

### 4.2 Drawing it

The graph is drawn behind the line's column, down its whole height, open part too, so no intrinsic measuring is needed:

- **Lanes passing by** run straight from top to foot.
- **Lanes waiting for this commit** come in at the top and curve into its dot, half way down the first 48 dp.
- **Parent lanes.** A line leaves the dot to each parent's lane by the foot of the first 48 dp, and that lane runs on to the line's foot.
- **The dot.** A commit's dot is filled; a merge's is a ring.
- **Colours.** Lanes take their colour by place: accent, green, blue, amber.

Every line reserves room for the widest graph read so far, so the ids line up down the list.

### 4.3 One commit, told once

`CommitFacts` is how Source Control tells a commit: Last Commit's card and an open line both use it. It shows `commit <id>` cut to `GitCommitSummary.ID_LENGTH` (12, one constant, which `GitInspectParser` uses too), the subject, the body when there is one, the author and the time. The card adds its signature rows after these.

### 4.4 Paging from the list

The next page is asked for once the last line in view is within 20 lines of the end. It is not asked for while a page is read, or after a failure.

## 5. Design system

| Piece | What it is |
|---|---|
| `IcodeIcons.Expand` | The owner's icon: ┌ at the top left, ┘ at the bottom right, on Material's 24-unit grid with 2-unit strokes. |
| `SectionHeader(title, icon, actionLabel, onAction)` | A card's label with an icon action at its end. The icon takes no more of the row than itself; its ripple, `IcodeSizes.CornerAction` (40 dp), fits inside the narrowest card padding. |
| `IcodeTag` | A name on one line, tinted in its colour and at most 96 dp wide, with the rest ellipsized. `filled` is the accent's own tag. |
| `IcodeSizes.GraphLane` / `GraphDot` / `GraphLine` | 14 dp, 8 dp, 2 dp. |

## 6. Checks

### 6.1 Tests

- **`GitGraphTest`**:
  - a line of commits;
  - a merge and the lanes meeting again, row by row, against `git log --graph`'s picture;
  - a merge joining a lane that already waits;
  - a page laid out after a page being the same as the history laid out whole;
  - completeness at the first short page.
- **`GitHistoryParserTest`**, on Git 2.43's real output for a merged repository with a tag, a branch and a remote's branch:
  - ids, parents, author, time, subject and body;
  - names of each kind, with a remote's HEAD left out;
  - a detached HEAD;
  - output cut short is a failure;
  - the arguments.
- **`GitHistoryCoordinatorTest`**:
  - pages read in turn until the last;
  - a failed page, with Git's words logged, and read again by `More`;
  - opened again, the held rows shown while the first page is read afresh;
  - HEAD moved, the old rows not shown.

### 6.2 Driven in a desktop scene

The real `CommitPushScreen` and `GitHistoryScreen` were compiled with the real parser and driven with touches (Compose Desktop 1.7.3). Each page was the parser's reading of real `git log` output from a 38-commit repository with three merged branches, a merge of main into develop, two tags and remote branches. The scene paged 10 commits at a time.

- **The card.** The icon's target measures 40 × 40 dp and the label stays 15.6 dp tall. A tap 15 dp right of and 15 dp above the icon's centre opens the sheet.
- **Paging.** The sheet sends `Open`, then `More` as the list nears its end, until all 38 are read. The foot then reads "38 commits".
- **The graph** matches `git log --graph` lane for lane: develop's lane from "Add the editor shell", main merged into develop at "Theme the status bar", feature/login's lane, the hotfix lane.
- **Open lines.** A tap opens a line (state "Open"). An open merge shows `commit 198f6ed123ae` on one line; a commit with a body shows it under its subject. The lanes run on beside both.

### 6.3 On a phone

Still to do:

- open the sheet on a large repository and scroll past several pages;
- open a line and select its id;
- push or fetch, then reopen the sheet, and see the names move;
- check TalkBack reads each line as one, with "Open"/"Closed".
