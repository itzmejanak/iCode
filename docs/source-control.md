# Source control

This document describes the Source Control implementation shipped in iCode. Current code, configuration, and tests define the behavior documented here. Open implementation scope is tracked in [Source Control requirement completion](./roadmap.md#source-control-requirement-completion), and acceptance work is tracked in [Git remotes, conflicts, branches, and GPG signing](./roadmap.md#git-remotes-conflicts-branches-and-gpg-signing).

## Status

| Area | Status | Current scope |
|---|---|---|
| Git CLI command and parsing integration | Done | iCode invokes the Git CLI in the Linux guest, parses its results, and does not emulate Git or use a Java Git library. Broader requirement and device acceptance remain separate. |
| Repository discovery and selection | Partially Done | Parent, nested, submodule, and linked-worktree layouts are understood, but the selected repository is not persisted across Android process death. |
| Source Control requirement coverage | Partially Done | Core status, diff, stage, commit, branch, conflict, history, and remote flows exist. Ignored-path presentation, a wide side-by-side diff, selected-repository persistence, and first push through a selected non-`origin` remote are not implemented. |
| Current physical-device acceptance | Needs Verification | The canonical credential, signing, history, and repository-layout checks are in the [Git acceptance roadmap](./roadmap.md#git-remotes-conflicts-branches-and-gpg-signing). |

## Execution model

Source Control is a front end to the real `git` executable installed in iCode's Ubuntu guest. Every command goes through `GuestCommand` in the `git` context:

- arguments remain separate until they are POSIX-quoted for `/bin/sh -c`;
- every command starts with `git -c core.quotepath=false --no-optional-locks`;
- command-specific `-c key=value` entries are scoped to that invocation;
- the user's login environment is read, so Git hooks, filters, credential helpers, SSH configuration, GPG configuration, and programs on the user's `PATH` behave as guest programs;
- there is no PTY, `GIT_TERMINAL_PROMPT=0`, and pagers are disabled, so a Source Control command cannot wait invisibly for terminal input;
- Git exit code zero is the success condition. Failures preserve and show Git's actionable error text.

Git is a Runtime Tool and may be absent from a fresh runtime. Repository metadata can still be detected without launching Git, which lets Source Control offer **Install Git** instead of incorrectly offering to initialize an existing repository. Clone also installs Git before first use when necessary.

Each command sees the full guest and all workspaces under `/workspace`. Source Control does not create a second Git environment separate from the terminal.

## Repository discovery and multiple repositories

`GitLocator` first looks for the nearest `.git` directory or `.git` file at the selected path or one of its parents. It never walks above iCode's workspaces root. A `.git` file may point to:

- a relative Git directory, as used by a submodule; or
- an absolute guest path, as used by a linked worktree.

For a workspace containing several repositories, iCode includes the repository containing the workspace and scans ordinary child directories up to three levels deep. The scan skips hidden directories, `node_modules`, and symbolic-link directories. The repository picker selects the one used by status, commits, branches, history, and remote operations.

The selected repository is currently held in process memory. After Android ends the process, iCode falls back to the repository containing the workspace or the first repository found by the scan. Persisting that selection is tracked in [Source Control requirement completion](./roadmap.md#source-control-requirement-completion).

Git remains the authority on whether a discovered location is a usable repository. If Git cannot read an existing repository, Source Control shows the failure instead of reporting a clean tree.

## Repository state and refresh

A refresh runs one guest script and reads:

- the worktree top;
- local and remote-tracking refs;
- remotes;
- the effective `user.name` and `user.email`;
- the latest commit and, when GnuPG is installed, its signature verdict;
- merge, rebase, cherry-pick, revert, and bisect marker files;
- `git status --porcelain=v2 --branch --untracked-files=all -z`.

NUL-delimited records preserve valid Git paths containing spaces, quotes, non-ASCII text, or newlines. Source Control represents staged and unstaged additions, modifications, deletions, renames, untracked paths, and conflicts. It also reports the branch, upstream, ahead/behind counts, unborn branches, detached HEAD, and an in-progress operation.

The status command does not use `--ignored`; ignored paths are omitted rather than shown in a separate group. Adding a distinct ignored-path presentation is tracked in [Source Control requirement completion](./roadmap.md#source-control-requirement-completion).

Source Control refreshes when it opens, when the user requests a refresh, after its own mutations, and after relevant worktree changes. Save-driven refreshes are coalesced for 350 ms. A result is discarded if another workspace became active before it completed.

## Local change workflow

### Stage and unstage

| Action | Git operation |
|---|---|
| Stage one path | `git add -A -- <path>` |
| Stage all | `git add -A` |
| Unstage one path with a `HEAD` | `git reset --quiet HEAD -- <path>` |
| Unstage all with a `HEAD` | `git reset --quiet HEAD` |
| Unstage before the first commit | `git rm --cached -r [--quiet] -- <path>` or the whole index |

Using `add -A` means a deleted path can be staged. Before the first commit, iCode avoids a reset against a nonexistent `HEAD`.

### Per-line changes

Open text documents can show line-change markers against the version in Git's index. iCode rereads that index version when the repository or index changes, waits 200 ms after editor changes, and computes the current hunks.

**Stage Change** applies one displayed hunk to the previously read index content, writes the resulting blob with `git hash-object -w --path`, and updates only that index entry with `git update-index --cacheinfo`. It preserves the index version's encoding, byte-order mark, and file mode. If either the file or index changed after the hunk was displayed, the operation is refused and the user must review the new change.

This action stages editor text into the index. It does not save the editor buffer to the worktree. Untracked files, files with no index version, binary data, and undecodable index content do not receive per-line markers.

### Diff

A path diff uses:

```text
git diff [--cached] --find-renames --no-color -- <path>
```

The normal view compares worktree to index; the staged view compares index to `HEAD`. Text is parsed into inline hunks with old and new line numbers. Binary output is identified without fabricating a text diff. The current implementation proves the phone-friendly inline view only; the required wider side-by-side view is tracked in [Source Control requirement completion](./roadmap.md#source-control-requirement-completion) and the [adaptive-shell roadmap](./roadmap.md#adaptive-tablet-landscape-foldable-and-multi-window-shell).

### Discard

Discard applies only to unstaged modified, deleted, or untracked entries:

- modified or deleted paths use `git restore --worktree -- <path>`, returning the worktree to the index so staged work survives;
- untracked paths use `git clean -f -d -- <path>`. Git-ignored content is not removed because iCode does not pass `-x`.

The confirmation pins the exact set of changes. iCode refreshes and compares that set immediately before acting; if it changed, discard is refused and must be reviewed again. Absolute paths, traversal components, empty components, `.` and `.git` are rejected. Staged and conflicted rows are not directly discardable. The confirmation identifies that untracked files or folders will be deleted because Git has no saved copy.

## Commit and identity

The credentials page can set a global author identity. Source Control can set or clear repository-local `user.name` and `user.email`; the local values override the global values in that repository.

A commit requires a nonblank message. The Source Control UI also requires staged changes and an effective author identity before offering the action. It runs:

```text
git commit -m <message>
```

It does not stage files automatically. Configured hooks and signing run because this is an ordinary Git commit in the guest. **Commit & Push** commits first and starts the push only if Git accepted the commit.

### AI commit messages

The sparkle action is available when changes are staged. It uses the model chosen under **Settings › Source Control › Commit Message Model**, falling back to the configured assistant model. iCode reads from the currently selected repository:

- `git diff --cached --numstat --find-renames`, bounded to 8,000 characters;
- `git diff --cached --find-renames --no-color --no-ext-diff`, bounded to 24,000 characters using its head and tail;
- up to ten non-merge commit subjects for repository style.

That material is sent to the selected model provider in one request without tools. A second tap cancels the previous request, and only the latest result may update the screen. The generated message is placed in the multiline field for review and editing; it is never committed automatically. A missing model, empty staged diff, Git failure, provider failure, or empty model answer leaves the user's existing field unchanged and reports why.

Diffs, generated messages, and model answers are not written to iCode logs. The selected provider still receives the staged material, so the user must choose a provider appropriate for the repository's confidentiality requirements.

## Remotes and transfers

Network operations are real Git commands:

| Action | Git operation | Behavior |
|---|---|---|
| Fetch | `git fetch --prune --progress` | Updates remote-tracking refs and prunes deleted ones. |
| Pull | `git pull --ff-only --progress` | Refuses divergence; it does not create a merge commit or rebase automatically. |
| Push with an upstream | `git push --progress` | Uses the configured upstream. |
| First push | `git push --progress --set-upstream origin <branch>` | Establishes `origin/<branch>` as upstream. |
| Clone | `git clone --progress [--branch <branch>] -- <url> <directory>` | The `--` prevents a dash-led address from becoming a Git option. |

Only one clone, fetch, pull, push, or Commit & Push transfer runs at a time. Git's stderr supplies progress and the transcript. Transcript lines are credential-sanitized and capped at 400 characters before entering UI state. Cancelling the transfer cancels its coroutine, kills and reaps Git and its children, and records a cancelled state rather than success.

Clone writes first to an app-private staging directory. A failed or cancelled clone discards that staging directory; iCode opens and lists the requested workspace only after the complete clone has been moved into place.

Source Control can add a remote after validating its name and URL. It does not edit or remove an existing remote; use the terminal for those operations. HTTPS URLs containing `account@` are refused so a token is not stored in `.git/config`.

The first-push path is currently hard-coded to remote `origin`. A repository whose only remote has another name must set an upstream in the terminal before the Source Control push path can use it. Selecting a non-`origin` first-push remote is tracked in [Source Control requirement completion](./roadmap.md#source-control-requirement-completion).

## Branches and detached HEAD

Source Control supports:

- switching with `git checkout <branch>`;
- creating and switching with `git checkout -b <branch>`;
- safe deletion with `git branch -d <branch>`;
- creating a tracking branch from the explicit full ref with `git checkout --track refs/remotes/<remote>/<branch>`.

The active branch cannot be deleted through the UI, and Git refuses deletion of an unmerged branch. Force deletion, branch rename, tags, arbitrary ref checkout, stash, and reflog are terminal workflows.

Detached HEAD is shown explicitly. Commit and push affordances are blocked until the user checks out or creates a branch rather than guessing a destination.

## Conflicts and interrupted operations

Source Control detects an in-progress merge, rebase, cherry-pick, revert, or bisect from Git's own marker files. **Abort** uses the matching command:

- `git merge --abort`;
- `git rebase --abort`;
- `git cherry-pick --abort`;
- `git revert --abort`;
- `git bisect reset`.

It never substitutes a generic reset.

A conflicted text file is read and written through the same document codec as the editor. Each conflict marker block can be resolved independently as **Current**, **Incoming**, or **Both**. The write preserves encoding, byte-order mark, line endings, and text outside the selected marker block. An unsaved editor buffer blocks the write, and a changed-on-disk revision is reread rather than overwritten. Resolving text and **Mark Resolved** are separate actions; the latter stages the path with `git add -- <path>`.

## History

History is the selected branch's `HEAD` history, read 100 commits at a time with topological ordering and full ref decorations. Rows include commit ID, parents, author, author time, local branches, remote-tracking branches, tags, subject, and body. Opening the sheet refreshes its first page; a failed next page retains earlier rows and can be retried.

History does not currently provide per-file history, checkout, cherry-pick, revert, or signature verification. Signature status is shown only for **Last Commit** in the sync view, and only when GnuPG is installed to verify it.

## HTTPS personal access tokens

The app stores one HTTPS host, account name, and personal access token tuple:

1. Host and account are private preference metadata used for display.
2. The token is encrypted with the Android Keystore alias `icode.git.credentials`; the key material is not exportable and plaintext is not stored in preferences, workspace records, snapshots, or capture state.
3. For one app-managed clone, fetch, pull, push, or connection test, iCode decrypts the token and percent-encodes the account and token into one Git credential-store line.
4. It creates an owner-readable and owner-writable temporary file in the guest's app-private `/tmp`.
5. It passes command-scoped configuration equivalent to an empty `credential.helper` followed by `credential.helper=store --file=/tmp/<temporary-file>`, preventing an inherited helper from replacing this command's credential source.
6. A `finally` block deletes the file when the command returns, fails, or is cancelled normally.

If the saved value can no longer be decrypted with the device's Keystore key, iCode attempts to remove the unusable entry and runs without it. The saved token is injected only into app-managed operations. Git commands typed in the terminal use the user's own Git credential configuration.

A hard Android process kill can prevent normal `finally` cleanup. The file is still app-private and owner-only. Scanning for and deleting stale credential files at startup is **Not Started** and tracked in [Git temporary credential cleanup](./roadmap.md#git-temporary-credential-cleanup).

## SSH

The SSH flow installs `openssh-client` through Runtime Tools when necessary. **Generate Key** creates one unencrypted Ed25519 key pair at `/root/.ssh/id_ed25519`; only `/root/.ssh/id_ed25519.pub` is displayed or copied. The private key remains in the guest home and is not stored in Android Keystore.

iCode writes a mode-0600 `~/.ssh/config` using:

```sshconfig
Host *
    StrictHostKeyChecking accept-new
    UserKnownHostsFile ~/.ssh/known_hosts
    IdentityFile /root/.ssh/id_ed25519
```

An unknown host key is recorded on first contact, while a changed known key is refused. Connection testing runs the read-only command `git ls-remote --heads -- <url>` and reports success only when Git exits zero.

The generated key has no passphrase. The configuration step replaces the complete `~/.ssh/config` rather than merging custom entries. Users who need several identities, a passphrase, an agent, or host-specific policy should manage SSH in the terminal.

## GPG signing and prompts

GPG signing uses the guest's ordinary Git and GnuPG configuration. The credentials page can:

- install `gpg` and `gpg-agent`;
- list usable secret keys and export a public key;
- create a key for the global Git identity using `gpg --batch --yes --quick-generate-key`;
- select global `user.signingkey`;
- set `gpg.format=openpgp` and `commit.gpgsign=true`, or unset automatic signing;
- change the selected key's passphrase.

The key-generation command does not specify an algorithm, subkey layout, or expiry. Those properties come from the shipped GnuPG defaults and remain in the [Git acceptance roadmap](./roadmap.md#git-remotes-conflicts-branches-and-gpg-signing); documentation must not promise a particular expiry.

iCode installs guest-side `icode-pinentry` and `icode-gpg-agent` bridges. A signing or key-management prompt is handed from the guest through an owner-only FIFO in `/tmp/icode-gpg` and shown in an iCode sheet. The answer is written directly to the FIFO rather than to a regular file, app state, or logs. Cancelling the sheet answers GnuPG with cancellation.

The GPG agent runs in a separate PRoot session without `--kill-on-exit`, so it may outlive the commit that started it. The managed configuration sets a 1,800-second default cache TTL; GnuPG's own maximum cache TTL still applies. Runtime teardown cancels pending questions and terminates agents.

Any program running in the user's guest can invoke the shared GPG agent and cause a prompt, just as in a desktop account. The prompt establishes that the request came through this guest, not which guest program initiated it. Secret GPG keys live under the guest home, not in Android Keystore. A repository-local author email can also differ from the key identity, which may cause a hosting provider to label a locally valid signature unverified.

## Security boundaries and deliberate limitations

- **The guest is user-controlled, not a repository sandbox.** A repository's hooks, filters, configuration, and executables can run with the Android app's filesystem permissions. Review untrusted repositories before invoking Git operations.
- **PRoot is not a security boundary.** Guest processes run as iCode's Android UID and can reach the paths bound into the guest.
- **Transport depends on the caller.** Android HTTP clients reject cleartext except on loopback. Git runs inside the guest and currently accepts configured `http://` remotes, while user-installed guest tools follow their own transport settings. Prefer HTTPS or SSH for remotes and treat plain HTTP as exposing repository traffic and credentials to the network.
- **Path handling is literal.** Git values are passed as separately quoted arguments, and path-taking commands use `--` where applicable. Discard adds repository-relative traversal checks.
- **Secrets have different storage models.** HTTPS tokens are Keystore-encrypted and command-scoped. SSH and GPG private keys intentionally live in `/root` so the real guest tools can use them.
- **Transfers do not resume after process death.** Clone staging prevents an incomplete clone becoming a workspace, but an interrupted fetch, pull, or push is left for Git to report on the next refresh.
- **Source Control operates on one selected repository at a time.** Discovery is depth-limited, excludes hidden and symlinked directory trees, and does not persist the selection.
- **The UI intentionally exposes a safe subset.** It has no force push, force branch deletion, arbitrary reset, remote edit/removal, tags, stash, reflog, submodule command, or pull-strategy control. The terminal remains the complete Git interface.
- **Pull is fast-forward-only.** Resolve divergence deliberately in the terminal, then refresh Source Control.
- **Ignored paths and wide side-by-side diffs are known requirement gaps.** Current source proves neither feature; both are tracked in [Source Control requirement completion](./roadmap.md#source-control-requirement-completion).
- **The first push assumes `origin`.** A custom-only remote needs a terminal-configured upstream until [Source Control requirement completion](./roadmap.md#source-control-requirement-completion) adds remote selection.

## Implementation map

| Responsibility | Current path |
|---|---|
| Git process construction and bounded guest execution | `app/src/main/java/dev/icode/mobile/data/git/CliGitService.kt`, `GitCommandLine.kt`; `data/runtime/GuestCommand.kt` |
| Repository discovery and selection | `app/src/main/java/dev/icode/mobile/data/git/GitLocator.kt` |
| Status protocol and parsing | `app/src/main/java/dev/icode/mobile/data/git/GitInspectParser.kt`, `GitPorcelainParser.kt` |
| Local commands, branch commands, history command, remote commands | `app/src/main/java/dev/icode/mobile/data/git/GitRepositoryClient.kt` |
| Local lifecycle and discard revalidation | `app/src/main/java/dev/icode/mobile/data/git/GitWorkspaceCoordinator.kt` |
| Per-line index staging | `app/src/main/java/dev/icode/mobile/data/git/GitLineChanges.kt`, `GitIndexVersions.kt` |
| Conflicts | `app/src/main/java/dev/icode/mobile/data/git/GitConflictCoordinator.kt`, `GitConflictParser.kt` |
| Transfers and cancellation | `app/src/main/java/dev/icode/mobile/data/git/GitTransferCoordinator.kt` |
| HTTPS, SSH, and GPG | `app/src/main/java/dev/icode/mobile/data/git/GitAuthenticator.kt`, `GitAuthCoordinator.kt`, `GitCredentialStore.kt`, `GitCredentialFile.kt` |
| GPG prompt bridge | `app/src/main/java/dev/icode/mobile/data/runtime/GpgBridge.kt`, `GuestGpg.kt` |
| History paging and parsing | `app/src/main/java/dev/icode/mobile/data/git/GitHistoryCoordinator.kt`, `GitHistoryParser.kt` |
| AI commit-message generation | `app/src/main/java/dev/icode/mobile/data/assistant/AssistantCommitMessages.kt` |
| Domain rules and public state | `app/src/main/java/dev/icode/mobile/domain/GitContract.kt`, `GitActionRules.kt`, `GitRemoteRules.kt` |
| Regression coverage | `app/src/test/java/dev/icode/mobile/data/git/`, `app/src/test/java/dev/icode/mobile/domain/` |

## Roadmap

This guide records current behavior and limitations. The canonical requirements and completion evidence for selected-repository persistence, ignored paths, wide diffs, non-`origin` first push, real remotes, GPG, hard-kill credential cleanup, and AI commit messages live only in [Source Control requirement completion](./roadmap.md#source-control-requirement-completion), [Git remotes, conflicts, branches, and GPG signing](./roadmap.md#git-remotes-conflicts-branches-and-gpg-signing), and [Built-in assistant providers and tool lifecycle](./roadmap.md#built-in-assistant-providers-and-tool-lifecycle).
