# S5 Git Separation and Hardening Plan

Status: **plan for approval** (2026-09-28). This is the detailed S5 plan under
[code-separation-plan.md](./code-separation-plan.md), which remains authoritative. The dated
[backend-split-audit.md](./backend-split-audit.md) is evidence, not the governing order. Follow
[backend-split-rules.md](./backend-split-rules.md): one lifecycle owner, one writer per fact,
roughly 90–270 lines per file, and a 300-line review budget.

No S5 production code starts until this plan is approved. Every numbered step is a separate,
reviewable change. A code move and a behavior change never share a step.

## 1. Result this cycle must deliver

A user must be able to enter Source Control, understand the repository Git actually found, review
and change it, synchronize it, authenticate safely, interrupt long work, and recover without stale
state or false success. S5 therefore delivers all of the following together:

1. The gateway only routes Git intents and sequences cross-scope workspace entry; it no longer owns
   Git commands, progress, credentials, conflict writes, or identity configuration.
2. `GitWorkspaceCoordinator` owns repository inspection and local operations;
   `GitTransferCoordinator` owns clone/fetch/pull/push and their one live process;
   `GitAuthCoordinator` owns identity, HTTPS credentials, SSH, and connection tests.
3. `GitInspectParser` is a pure, directly tested parser over a filename-safe protocol. Git remains
   the authority; iCode does not infer repository state from UI text.
4. One Git client, one Git-config path, one package installer, one active transfer, one sanitized
   transcript path, and one exact safe-directory policy replace the current duplicates.
5. Every existing Git control remains reachable and becomes truthful. The known gaps in §3 are
   closed one at a time, with a test and the relevant lifecycle check.
6. Tokens remain Keystore-encrypted at rest, masked while typed, absent from snapshots/captures/logs,
   never shown again after save, and materialized only for the command that needs them.

## 2. Verified current baseline

Measured against the current branch on 2026-09-28, after S4:

| Area | Current owner / size | What is already real |
|---|---|---|
| Gateway Git cluster | `AndroidWorkbenchGateway.kt` 1,227 lines | all Git intents reach the real CLI, but local actions, transfers, auth, progress and config still live together |
| Repository adapter | `GitRepositoryClient.kt` 353 lines | init, status, stage, commit, branch, diff, discard, conflicts, abort, fetch/pull/push/clone |
| Process adapter | `CliGitService.kt` 282 lines | PRoot Git commands, status/diff parsing, provisioning and stderr polling |
| Authentication | `GitAuthenticator.kt` 166; `GitCredentialStore.kt` 102 | Keystore AES/GCM, HTTPS helper, ed25519 key, read-only `ls-remote` test |
| Parsers | porcelain 168; diff 169; conflict 104; progress 59 | 38 fixture-derived parser/command tests; no inspect or coordinator tests |
| Main Git screens | Source Control 299; Git Auth 275 | production state and real controls; both are at the review budget |

The attached-device baseline already proved an SSH key, a private-host connection test, and a
private clone. S5 preserves those paths while fixing the gaps below. The current Git documentation
was checked through Context7 (`/git/htmldocs`): porcelain formats are the machine-stable interface,
`-z` avoids pathname quoting, `safe.directory` is honored only in protected config, and the
`store` credential helper writes plaintext credentials.

## 3. Lifecycle and control map

This is the contract S5 must preserve and harden. “Owner after S5” is backend ownership; screens
continue to see only `WorkbenchGateway`, intents, effects and snapshot state.

| Lifecycle stage | User surface and controls | Current behavior | Owner after S5 |
|---|---|---|---|
| Enter / availability | Explorer Source Control rail; Source Control Install Git / Initialize Git | checks the runtime, distinguishes missing Git from no repository, installs or initializes | workspace coordinator + shared package installer |
| Load / react | Source Control open, Refresh, app resume, terminal command completion, editor save, external file event | one inspect script publishes status, branches, identity, remote and last commit; save refresh is debounced | workspace coordinator with workspace generation |
| Review | staged/unstaged rows, diff, previous/next hunk, binary/no-change states | Git patch parser and exact staged flag | workspace coordinator |
| Local action | stage/unstage one/all, commit, create/switch/delete branch | real CLI operations, then refresh | workspace coordinator |
| Destructive action | row menu Discard, delete-confirmation screen, Abort in-progress | confirmation exists, but target handoff and discard semantics have gaps | workspace coordinator with a pinned discard request |
| Conflict | Current / Incoming / Both for each marker block; Mark Resolved; Abort merge/rebase/etc. | parses markers and writes a resolved file; currently one section button changes every section | workspace coordinator through the document write seam |
| Sync | Fetch, Pull, Push, progress screen, Run in Background | real Git output and exit status; process is not independently cancellable | transfer coordinator |
| Clone | URL, destination, optional branch, Authentication, Clone, Cancel Clone | real clone; branch is ignored and Cancel only navigates back | transfer coordinator |
| Identity | global / this-repository name and email; reset to default | real Git config, but three gateway call paths bypass the repository client | auth coordinator through one config API |
| HTTPS auth | provider, host, account, masked PAT, save/remove | encrypted preference plus persistent plaintext guest credential file | auth coordinator; command-scoped credential materialization |
| SSH auth | generate, copy public key, open provider, test URL | ed25519 generation, `accept-new`, read-only test | auth coordinator + shared package installer |
| Interrupt / recover | back from progress, workspace switch, app background, cancellation, process death, offline/rejection | sync continues in background; clone cancellation is false; stale publication is not uniformly guarded | transfer job + operation id + workspace generation |

## 4. Confirmed gaps to close

These are current-code findings, not speculative features.

### 4.1 Repository inspection and state

1. `GitRepositoryClient.inspect` mixes command protocol, parsing and guest→host path translation.
   `parseLastCommit` and the inspect script have no direct tests.
2. The inspect script separates sections with ASCII RS while status itself is NUL-delimited, then
   decodes the whole stream as text. A legal filename can contain RS/newline, and malformed UTF-8
   cannot be handled byte-exactly.
3. Opening a subdirectory of a parent repository, a linked worktree or a submodule is not mounted
   with all metadata Git needs. The host fallback can show a branch while `gitRepository` is null.
4. Detached HEAD falls back to the first local branch, which presents a branch that is not checked
   out. Unborn repositories need to remain distinct from detached ones.
5. A delayed save refresh captures the old workspace root and can publish it after a workspace
   switch. The same guard is needed for every inspect and local action result.
6. `safe.directory=*` is passed to every command and written globally. The selected repository can
   be named exactly; an unrestricted wildcard is unnecessary.

### 4.2 Local actions and conflicts

7. A deleted Source Control row navigates to confirmation without dispatching
   `RequestDiscardChanges`, so the page can say “Nothing to discard”.
8. A discard request identifies only a path. A file that is staged and edited again has two rows
   with that path, so the pending row can be the wrong one.
9. Current tracked-file discard restores index **and** worktree from `HEAD`, destroying staged work
   when the user meant to discard only the unstaged row. An added file is removed with `git rm -f`;
   an untracked directory can be recursively deleted while the confirmation says “file”.
10. Conflict buttons are rendered per marker block, but `GitConflictParser.resolve` resolves every
    block with the clicked choice. The write also bypasses the document codec/revision owner, so an
    open tab, encoding, BOM, line endings or unsaved edits can be clobbered.
11. Remote-branch checkout relies on Git's ambiguous short-name DWIM behavior. Detached state and
    multiple remotes need explicit inputs.
12. “No remote configured. Add one…” has no UI control or intent to do so.

### 4.3 Processes, transfers and packages

13. `CliGitService` uses one fixed stderr file, unbounded `readText()`, and a blocking capture path
    that does not share S4's cancellation/reaping contract.
14. Clone/fetch/pull/push run under the gateway mutation lock. A network stall can queue unrelated
    actions; progress is matched only by a label, so two successive “Pulling” operations have no
    unique identity.
15. The optional clone branch is saved in `cloneBranch` but never enters `CloneRepository` or
    `git clone --branch`.
16. Clone's Cancel button calls only `actions.back`; Git continues and later opens the clone. There
    is no cancelled state or partial-destination recovery policy.
17. Git, OpenSSH and ripgrep have three apt paths. Two first-use requests can contend for dpkg, and
    Git provisioning constructs a second `CliGitService`.

### 4.4 Configuration, authentication and output

18. Repository identity uses `GitRepositoryClient`, while global identity, local reset and auth
    reads call `gitCli.run("config", …)` directly from the gateway.
19. Git progress/output enters `GitOperationState.output` without `BackendLog.credentialSafe`, so a
    remote error can put a credential-bearing URL on screen or in Output.
20. Saving a PAT decrypts it into `/root/.icode-git-credentials` and configures a persistent global
    plaintext `credential.helper=store`. App-private permissions help, but this contradicts the
    Keystore-at-rest promise and survives longer than the command.
21. `GitCredentialStore.describeHttps()` decrypts the token merely to show `account@host`; host and
    account are already non-secret metadata. The store also retains two `UseKtx` warnings.
22. `GitRepositoryClient` retains duplicate KDoc and two conflict methods the production flow does
    not use.

## 5. Target ownership

### 5.1 Owners

| Owner | Responsibility | Must not own |
|---|---|---|
| `GitInspectParser` | parse the framed byte protocol into `GitWorkspaceSnapshot`; ref/path and last-commit parsing | process launch, state publication, UI strings |
| `GitWorkspaceCoordinator` | availability, inspect/refresh generation, Explorer decoration, init, stage/unstage, commit, branches, diff, pinned discard, conflict, abort, debounced save refresh | network transfers, secrets, apt implementation |
| `GitTransferCoordinator` | one clone/fetch/pull/push job; operation id, bounded sanitized progress, background policy, cancel clone, completion, interruption ledger and stale-result rules | identity, token storage, local status parsing |
| `GitAuthCoordinator` | auth snapshot, global/local identity, PAT save/clear, SSH generation/config, connection test | rendering fields, retaining typed token, transfer progress |
| `GitRepositoryClient` | typed Git CLI operations and one config read/write API | workbench state, effects, screen navigation |
| `CliGitService` | construct Git argv, run through `GuestCommand`, return bounded stdout/stderr/exit, exact Git mount context | domain decisions, progress wording, stored credentials |
| `RuntimePackageInstaller` | the single serialized apt update/install path for `git`, `openssh-client`, `ripgrep`; shared in-flight jobs | feature state or screen messages |
| Gateway | composition root, intent routing, cross-scope open-workspace sequence | Git commands, parser logic, credentials, progress polling |

### 5.2 One-writer map

| Fact/resource | One writer after S5 |
|---|---|
| `gitRepository`, `gitChanges`, `activeBranch`, Git tree decoration | `GitWorkspaceCoordinator` |
| `activeDiff`, `activeConflict`, `pendingDiscard` | `GitWorkspaceCoordinator` |
| `gitOperation`, transfer transcript and non-secret interruption ledger | `GitTransferCoordinator` (package install status is translated through its caller, never written by the installer) |
| global/local Git identity and `gitIdentityLoaded` | `GitAuthCoordinator` |
| `gitAuth` | `GitAuthCoordinator` |
| encrypted credential preference | `GitCredentialStore`, only through `GitAuthCoordinator` |
| guest Git config | `GitRepositoryClient` config API; auth uses command-scoped overrides rather than a second writer |
| dpkg/apt database | `RuntimePackageInstaller` |
| conflict file bytes | existing document/file write seam, invoked by `GitWorkspaceCoordinator` with revision and encoding guards |

### 5.3 State/intent changes expected

- Replace path-only discard intents with `RequestDiscard(change identity)` and a payload-free
  `ConfirmDiscard`; the coordinator pins and revalidates the exact unstaged target.
- Add `CancelGitTransfer`; add `GitOperationState.Cancelled` and an operation id/target so old
  progress cannot update a newer operation.
- Add clone branch to `CloneRepository` and validate it before launch.
- Add an explicit remote-config intent and state/effect for its compact form.
- Add explicit detached/unborn representation to `GitRepositoryState`; do not encode either as a
  made-up branch name.
- Add a conflict-section identity to `ResolveConflict`; one click resolves one section.
- No secret, process, filesystem or Compose type enters the domain contract.

## 6. Invariants

1. Git CLI is authoritative. No Kotlin implementation of Git status, branch, merge or remote rules.
2. Every path argument follows `--`; every ref/branch/remote is validated and passed as one argv
   element, never interpolated into a shell command.
3. Machine output is NUL/framed and byte-bounded. Display output is UTF-8 decoded with an explicit
   replacement/error policy and independently bounded.
4. Every async result carries the operation id and starting workspace. It can update repository
   state only while that workspace is still active.
5. A network transfer never holds the workbench mutation lock. Starting it is quick; its job owns
   its process and state.
6. Back on sync means “run in background”; Cancel Clone means kill/reap the clone and never open its
   destination afterward.
7. Cancellation never silently deletes a partial clone. If Git leaves a new directory, iCode names
   it and asks before any later cleanup.
8. Discard never crosses the repository root, never accepts `.git` or a descendant, and never
   erases staged work from an unstaged-row action.
9. Conflict resolution never overwrites unsaved editor text or an unexpected disk revision and
   preserves encoding, BOM and line endings.
10. Credentials are absent from snapshot, effect, transcript, log, exception message, command-line
    URL and capture fixtures. A PAT is decrypted only inside the auth/transfer call that needs it.
11. Every transcript line passes one sanitizer before state publication. The same bounded list feeds
    the progress screen and Output; there is no unsanitized parallel path.
12. Package install is serialized independently of feature cancellation. Cancelling a search or
    leaving Git Auth does not kill dpkg halfway through an update.

## 7. Ordered implementation plan

Each step ends with debug/release compilation, all unit tests, lint and the UI consistency guard.
Only the step's affected lifecycle is device-checked. No routine 39-screen capture.

### S5.0 — Close and commit S4

- Record ripgrep search, its device evidence and the final 295-test/16-warning gate in the owner
  plan and Todo.
- Keep the Explorer row extraction that restored the 300-line UI guard (229/225 lines).
- Commit S4 alone. Do not mix any Git split into that commit.

Exit: clean S4 review boundary; gate green; no push.

### S5.1 — Extract and characterize inspect parsing (move only)

- Move section parsing, last-commit parsing, ref filtering, in-progress detection and guest→host
  translation from `GitRepositoryClient.inspect` into `GitInspectParser` without changing the
  current RS protocol yet.
- Add fixtures/tests for normal repository, no repository, unborn branch, detached HEAD as it is
  currently represented, multiple local/remotes, author absent/present, last commit, and each
  in-progress marker.
- Pin malformed/missing sections to today's safe fallback rather than throwing.

Exit: same output for every current fixture; `GitRepositoryClient` delegates; G3 from the audit is
closed. This is a move, not the protocol fix.

### S5.2 — Move local Git ownership (behavior unchanged)

- Create `GitWorkspaceCoordinator`; move availability, initial inspect, refresh, debounce, init,
  stage/unstage/all, commit, branch actions, diff, discard confirmation state, conflict load/write,
  mark-resolved and abort exactly as they work today.
- Keep open-workspace sequencing in the gateway, but call the coordinator for the initial Git
  picture. Replace item/document callbacks with coordinator methods.
- Route Git workspace intents as one compact gateway branch. The gateway retains no Git action
  helper and no Git refresh job.
- Add coordinator characterization tests with a fake typed Git client: success, one reported
  failure, refresh after action, and current behavior on workspace close.

Exit: behavior and UI unchanged; no workspace command remains in the gateway.

### S5.3 — Move transfer ownership (behavior unchanged)

- Create `GitTransferCoordinator`; move fetch, pull, push, clone, progress parsing/publication,
  completion wording and existing credential application as-is.
- Keep the current blocking dispatch semantics temporarily; do not add cancellation in this move.
- Add characterization tests for success/failure transcript and upstream push arguments.

Exit: gateway delegates the four transfer intents; progress output remains byte-for-byte the same.

### S5.4 — Move auth/config ownership and remove duplicate paths (behavior unchanged)

- Create `GitAuthCoordinator`; move token save/clear, provider/method selection, SSH generation,
  connection test, auth refresh and global/local identity.
- Add one `GitRepositoryClient` config API for get/set/unset at global/local scope. Remove direct
  gateway `gitCli.run("config", …)` calls and the second `CliGitService` in provisioning.
- Remove unused repository conflict methods, duplicate KDoc and the touched `UseKtx` warnings.
- Add tests for identity validation/inheritance/reset and auth snapshot metadata (never a token).

Exit: the gateway has no credential/config code; D4/D5 are closed; behavior unchanged.

### S5.5 — One cancellable guest-command and package path

#### S5.5a — Git execution on `GuestCommand`

- Extend the existing S4 `GuestCommand` with bounded capture and optional bounded stderr snapshots.
- Run ordinary Git argv directly through it; reserve `/bin/sh -c` for the inspected, constant
  multi-command protocol. Give every invocation a unique stderr file and `killOnExit=true`.
- On coroutine cancellation: kill, reap, close descriptors and delete temp files. Cap captured bytes
  and displayed lines; return explicit truncation.
- Delete `GitCommandLine`'s fixed stderr paths and shell-script wrapping; keep only typed base argv
  construction and shared POSIX quoting for the constant inspect script.

#### S5.5b — Shared `RuntimePackageInstaller`

- Introduce one gateway-scoped installer with a mutex/in-flight map and a bounded output result.
- Migrate Git, OpenSSH and ripgrep to it. Apt update/install survives the initiating screen/query,
  but process shutdown cancels it cleanly.
- Concurrent Git/Search/Auth requests share or serialize work; no dpkg lock race.

Exit: cancellation tests at the process seam, one package writer, no fixed stderr file, no second CLI.

### S5.6 — Make repository inspection filename-safe and repository-correct

#### S5.6a — Framed byte protocol

- Replace RS/US text splitting with a NUL-safe tagged protocol. Status remains porcelain v2 `-z`;
  refs, config, commit and markers have explicit tags/length or NUL boundaries that filenames and
  commit subjects cannot impersonate.
- Parse bytes before decoding individual text values. Test spaces, quotes, Unicode, embedded
  newlines, RS/US bytes, rename origins and malformed/truncated frames.

#### S5.6b — Git mount context

- Add a host-side `GitMountResolver` whose only job is to expose the selected worktree and exact
  Git metadata to the guest; Git still validates and reports the repository.
- Cover normal repo, opened parent-repo subdirectory, nearest nested repo, linked worktree and
  submodule `.git` file. Preserve the opened workspace path separately from repository top-level.
- Show all repository-relative paths consistently and guard actions to the repository Git found.

#### S5.6c — exact safe directory and head state

- Replace wildcard `safe.directory=*` with exact selected guest worktree entries in protected/global
  config for app commands and the user's terminal. Migrate only the wildcard previously marked as
  iCode-owned; do not rewrite unrelated user Git config.
- Represent attached, detached and unborn heads explicitly. Never substitute the first local branch
  for detached HEAD.
- Add a generation/current-root check before every inspect publication and cancel delayed refresh on
  workspace close/switch.

Exit: the B-04 discovery matrix passes; a stale old workspace cannot repaint the new one.

### S5.7 — Fix local-action gaps, one behavior per commit

#### S5.7a — Exact and safe discard

- Every row that can discard dispatches the exact path/status/staged identity before navigation;
  deleted rows use the same helper as the overflow action.
- Only unstaged rows offer Discard. Revalidate the pending change immediately before confirmation.
- Tracked discard restores worktree from the index, preserving staged content. Untracked file/folder
  deletion states the exact consequence and is guarded to one repository-relative path. `.git`,
  root and outside paths are refused.
- Add staged+modified-same-file, staged addition, unstaged deletion, untracked file/folder, stale
  request and hostile path tests.

#### S5.7b — Conflict section and document safety

- Give each marker section a stable identity; Current/Incoming/Both rewrites only that section.
- Read/write through the existing document codec/revision seam. Preserve encoding, BOM and line
  endings; block when an open tab has unsaved edits or disk changed since load; reconcile an open
  clean tab after a successful choice.
- Mark Resolved remains explicit and only enables when no valid markers remain.

#### S5.7c — Explicit remote branch operations

- Local branch switch uses the exact local ref. Remote selection carries the full remote-tracking
  ref and creates/tracks an explicit local branch; ambiguity or an existing conflicting local name
  is shown rather than guessed.
- Detached HEAD is labelled, branch delete cannot target the active branch, and Git's unmerged
  refusal remains the failure text.

#### S5.7d — Configure a remote where the UI asks for one

- Replace the dead-end “Add one” notice with a compact Add/Change Remote form using a typed intent.
- Validate remote name and URL, use `git remote add` or `set-url`, refresh, and never embed credentials
  in the URL. Existing remotes are not silently replaced.

Exit: every Source Control, Diff, Branch and Conflict control has a real, exact outcome.

### S5.8 — Harden authentication and output

#### S5.8a — Metadata and validation

- `describeHttps()` reads host/account metadata without decrypting the token.
- Normalize/validate host and repository URL; reject embedded credentials and control characters.
- Keep the token field secret, clear its local value after dispatch, and never put it in an effect or
  test fixture.

#### S5.8b — Command-scoped HTTPS credentials

- Replace the global plaintext store helper with a unique 0600 credential file or equivalent
  command-scoped helper created immediately before HTTPS clone/sync/test and deleted in `finally`.
  The token stays Keystore-encrypted at rest; its only plaintext materialization is owner-only and
  limited to the Git process lifetime.
- Pass the helper through command-scoped Git config. Clear the old global helper and old credential
  file during migration without touching unrelated user helpers.
- This intentionally stops app-saved PATs from being silently reused by an arbitrary terminal
  command. HTTPS in the app keeps working; a terminal prompts/uses its own helper, while the user's
  persistent SSH key remains ordinary Git/SSH behavior. State this on the auth screen.
- Verify after success, failure and cancellation that no plaintext credential file remains.

#### S5.8c — Sanitized bounded output

- Pass errors, progress and completion lines through one credential sanitizer before snapshot state.
  Extend redaction tests for `user:token@`, token-only userinfo, query/userinfo variants and quoted
  Git fatal messages.
- Bound transcript lines and line length; state when older/longer output was omitted.
- Use `countOf` for connection-test branch wording.

Exit: security invariants in §6 are test-pinned; Output and progress cannot expose a credential.

### S5.9 — Make transfers independent, cancellable and stale-safe

#### S5.9a — Operation identity and background execution

- Start transfers outside `mutationMutex`; the coordinator owns one `Job` and monotonic operation id.
- Reject a second transfer with a clear message. A late stderr poll/result can update only its own id.
- Fetch/pull/push remain running when the progress page closes. They refresh only the repository that
  started them and never publish that state into a different active workspace.

#### S5.9b — Clone branch and validation

- Carry trimmed optional branch through the intent and `git clone --branch <branch> --progress`.
- Validate URL, destination, derived directory name and non-existing destination immediately before
  launch. Keep form fields on failure.

#### S5.9c — Real clone cancellation and recovery

- `Cancel Clone` dispatches cancellation, waits for kill/reap and publishes `Cancelled`; it does not
  merely navigate. A cancelled clone can never auto-open later.
- Do not silently remove a partial destination. If one remains and did not exist before launch,
  report its exact path and offer only a separate confirmed cleanup through the existing safe file
  deletion owner.
- Persist a small app-private, non-secret transfer ledger at start (kind, operation id, workspace
  or clone destination, timestamp) and clear it only at a terminal state. Android cannot resume a
  dead Git process; on next start, a leftover ledger is reported as interrupted, never Running or
  Succeeded, and its repository is refreshed before another transfer.

Exit: branch clone, cancel, background sync, offline/rejection and workspace-switch tests pass.

### S5.10 — UI ownership, final guard and records

- Split UI only where touched behavior would cross the 300-line budget: Source Control change groups
  and Git Auth identity/step state are the likely seams. Do not split for line count alone.
- Add the backend consistency guard checks needed by this scope: gateway cannot call `gitCli`; one
  apt owner; no fixed Git stderr file; Git screens cannot import `data`; 300-line review budget.
- Update `code-separation-plan.md`, `Todo.md`, architecture ownership and this record with exact test,
  lint and device results. Commit each step separately; do not push.

Exit: no known S5 gap remains, the gateway's Git cluster is gone, and every changed file has one
clear reason to change.

## 8. Test plan

### 8.1 Pure/unit tests

- `GitInspectParserTest`: all repository/head/marker/config/commit cases plus malformed and byte-hostile
  fixtures.
- `GitWorkspaceCoordinatorTest`: current-root publication, stale suppression, refresh debounce,
  one user-visible failure, exact discard, branch, conflict and post-action refresh.
- `GitTransferCoordinatorTest`: one job, operation-id isolation, background behavior, cancellation,
  branch clone argv, offline/auth/rejection failure, bounded/sanitized transcript, no stale refresh.
- `GitAuthCoordinatorTest`: identity scope/inheritance/reset, host/URL validation, metadata without
  token decrypt, save/clear, SSH state, test generations.
- `CliGitService`/command tests: argv boundaries, `--`, exact safe directory, unique temp files,
  output caps, cancellation cleanup and exit status.
- `RuntimePackageInstallerTest`: concurrent same package, different packages serialize, failure
  cooldown/retry, initiating caller cancellation does not strand dpkg.
- Parser regressions remain: porcelain v2 rename/staged+unstaged/NUL; diff binary/hunks/no-newline;
  conflict one-section-at-a-time; progress carriage-return output.
- UI helper tests: deleted row always pins discard; staged row has no Discard; clone branch reaches
  intent; Cancel dispatches; remote form validation; detached label.

### 8.2 Routine gate per step

```text
:app:compileDebugKotlin
:app:compileReleaseKotlin
:app:testDebugUnitTest
:app:lintDebug
tools/check-ui-consistency.sh
```

No routine 39-screen capture. Build a fresh debug APK in the same command as each requested device
install; lint/tests never count as rebuilding the installed APK.

## 9. Physical-device acceptance matrix

Use the real app and real terminal on the attached phone, only after checking the owner is not using
it. Use an isolated scratch repository or the owner-provided private test repository; never print,
capture or inspect a token; never place scripts in a user workspace; never delete pre-existing
workspace or `.git` paths.

1. **Availability:** runtime with Git present; missing-Git UI by a fake/test runtime only unless the
   owner authorizes changing the installed runtime; first-use install shares apt with ripgrep/SSH.
2. **Discovery:** normal root; opened subdirectory of parent repo; nested repo; linked worktree;
   submodule; detached HEAD; unborn repo; no repo.
3. **Status/review:** untracked, added, modified, deleted, rename with spaces/Unicode/newline;
   staged+edited same file appears in both groups; staged and unstaged diffs are distinct.
4. **Local actions:** stage/unstage one/all; commit with missing then valid identity; create/switch/
   delete branch; explicit remote branch tracking; add/change remote.
5. **Discard:** exact deleted-row handoff; discard unstaged while staged work survives; untracked
   file and folder confirmation; stale target refusal; no `.git` deletion.
6. **Conflict:** at least two marker blocks resolved differently; Both; open clean tab refresh;
   dirty-tab refusal; mark resolved; abort merge/rebase/cherry-pick/revert where practical.
7. **HTTPS:** token masked during entry and absent after save; test/clone/fetch/pull/push success;
   auth rejection; no plaintext helper file after each terminal state; no token in logcat/Output.
8. **SSH:** existing/generated key, copy/add handoff, first-host accept-new, changed-host-key failure,
   connection test and private operation.
9. **Transfers:** clone default and named branch; cancel during object transfer; partial-directory
   message; fetch/pull/push success; offline, DNS, auth and non-fast-forward rejection; duplicate
   start refused.
10. **Interrupt/recover:** Run in Background then return; switch workspace during fetch; leave and
    return to clone progress; rotate; background/foreground; process kill reports interrupted rather
    than success/running.
11. **Regression:** Explorer decorations and branch badge refresh after editor save, terminal Git and
    external file change; Output shows the same bounded sanitized transcript.

Record commands/outcomes but no repository content, URL credentials, token, private key or secret
screen. Device cleanup is limited to fixtures created for the matrix and requires confirmation if a
path could contain user work.

## 10. Done criteria

S5 is complete only when all are true:

- The gateway has no Git implementation methods and no direct `gitCli`/credential/config calls.
- The four target owners and parser stay within the 300-line review budget or carry a written,
  single-lifecycle exception.
- One typed client/config path, one package installer and one transfer job exist.
- Normal/parent/nested/worktree/submodule/detached/unborn/no-repo inspection is tested and accepted.
- Every listed control has a real success, loading, empty/unavailable, failure and relevant
  cancel/recovery path.
- Clone branch and Cancel work; stale operations cannot publish; exact discard preserves unrelated
  staged work; one conflict choice changes one block.
- No plaintext PAT remains after an operation; transcripts/logs/snapshots/captures contain no token.
- Debug/release compile, all unit tests, lint and UI guard pass with no new warning; touched S5
  warnings are removed.
- Fresh APK is built and installed in one step and the §9 matrix is recorded.
- The S5 record is committed in small reviewable commits. Nothing is pushed without an explicit ask.

## 11. Explicitly outside S5

- A hosting-provider account system, OAuth browser flow, pull-request UI or issue UI.
- Reimplementing Git, a Java Git library, or parsing human-formatted `git status`.
- Resuming a network transfer after Android killed the process.
- Automatic deletion of partial clones or any user file.
- Broad Output-channel history (S7); S5 only supplies the correct bounded sanitized Git source.
- Routine whole-app screenshot capture or unrelated Git-screen restyling.
