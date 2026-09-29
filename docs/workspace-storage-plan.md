# One logical workspace on several storage backends, and a runtime that keeps working — plan

Status: **plan** (2026-09-30), following the owner's direction in
[storage-logic.md](./storage-logic.md), which governs where the two differ. Replaces the earlier
version of this plan (iCode Projects in private storage; generated folders "kept fast") — that was
narrower than the direction: it was shaped around dependencies rather than a workspace
abstraction.

The rule, in the owner's words: **shared storage is the user's project space; private Linux storage
is iCode's invisible execution infrastructure; physical storage may differ, but iCode presents one
logical workspace.** One canonical copy of the user's source, no mirror, no synchronisation, no
stack-specific logic in the foundation. The runtime must not depend on the screen.

## 1. Measured (owner's phone, Infinix-X6881, 2026-09-29/30)

**Storage.**

| | Shared storage (`/storage/emulated/0`) | iCode's private storage (`files/`, ext4) |
|---|---|---|
| 1,000 small files created / deleted | 13.0 s / 6.1 s | 0.07 s / 0.04 s |
| symbolic link | refused ("Permission denied") | works |
| `chmod 755` | ignored (`-rw-rw----`) | kept (`-rwxr-xr-x`) |
| `A.txt` beside `a.txt` | one file: the second write replaced the first, silently | two files |
| a real `npm install` (1,116 files, cold) through iCode's PRoot | not measured: `run-as` cannot reach shared storage; to measure from iCode itself | 17 s, `.bin` link made |

Network inside the runtime: DNS 160 ms; a registry request 356 ms, then 46–56 ms.

**The owner's `npm install` in RevPlayer never ended** — npm's log: 30 requests in hours, median
67 s, worst 2 h 47 min; it never reached writing `node_modules`. Watched every 10 s (01:44–01:59):
npm ran while iCode was in front; at 01:51:19, iCode still in front and the screen still awake,
npm, its shell, PRoot **and iCode's own process** were all `D` in `__refrigerator`, and stayed so
through doze. Android's own freezer is off (`use_freezer=false`); the maker's is on — Transsion's
**Fast Freezer** (`mFastFreezerEnabled=true`, `FEATURE_FAST_FREEZER_SUPPORTED=true`), with a
package whitelist iCode is not on and an empty `noFreezeModeAppList`. It froze iCode although its
foreground service ran (`procState 4`), iCode was on the battery whitelist, and background running
was allowed.

**Who owns the work today** (code, 2026-09-30):

| Work | Owner | Lifetime |
|---|---|---|
| Terminal sessions (and what runs in them) | `TerminalSessionManager`, one per process; `RuntimeForegroundService` keeps the process in the foreground while any runs | the process; a killed process's sessions are reported, never resumed (`TerminalSessionLedger`) |
| Git transfers, file tasks, package installs, language servers, the workspace refresh and watcher | `AndroidWorkbenchGateway`'s scope — **owned by the Activity's ViewModel**: `onCleared()` → `gateway.close()` cancels it | **the Activity**: backing out of iCode, or the system discarding the Activity, stops them |
| Runtime install | `RuntimeCoordinator` + the service (`RuntimeWork.installing`) | the gateway, kept alive by the service while it installs |

## 2. Order (storage-logic.md "Order of work")

| Step | What | Done when |
|---|---|---|
| **R1** | The runtime owns the work, not the screen: one process-scoped runtime host (sessions, commands, servers, tasks, transfers, language services, future agents) started by and kept with the foreground service; the UI connects and reconnects to it. The Fast Freezer measured and answered. Every session's state is true: running, exited, interrupted (paused by the system, and for how long), killed | §3's matrix measured on the phone, each row either continuing or reported truthfully |
| **R2** | Verify long-running commands (an install, a build, a localhost server) through the matrix | recorded |
| S1 | This plan, rewritten around the logical workspace (§4) — before any storage code | this document |
| S2 | Design the generic `WorkspaceManager`, its backends and storage classes | reviewed with the owner |
| S3 | Proof of concept on the phone: a source file on shared storage and one private Linux-backed directory, seen as one tree at one runtime path | §4.4's assumptions measured |
| S4 | The Explorer and editor read the same logical workspace | one view, not two |
| S5 | A `DocumentsProvider` over the same logical tree | read, write, rename, delete measured from Files and a SAF app |
| S6 | Compatibility detection, and the Linux-native workspace as the exceptional fallback | detector tested on real collisions, links, exec bits |
| S7 | Stack/toolchain profiles, declarative, on top | only after S3–S6 hold |

## 3. R1 — the runtime keeps working

**Architecture.** A `RuntimeHost` owns everything that runs: terminal sessions, the commands in
them, development servers, file tasks, Git transfers, package installs, language services — and is
where future toolchains and agents attach. It lives for the process, is started when the user starts
work, and is held by `RuntimeForegroundService`, which already keeps the process in the foreground
while sessions run. The gateway becomes the UI's connection to it: the Activity and its ViewModel
can come and go without stopping anything. No second process until a measurement shows it is needed
(one uid is frozen whole, so a second process would not escape the freezer).

**Truthful state.** A session reports one of: running; exited (with status); **interrupted** — the
system stopped the process for a time (a heartbeat inside the host notices a wall-clock gap and
says "paused by the system for 46 min"); killed (the ledger, as today). A frozen command never
shows as quietly running.

**The matrix** (storage-logic.md "Test scenarios"), each measured with a heartbeat command started
from an iCode terminal (a process started from `adb` is not in iCode's cgroup and proves nothing):

| Scenario | Levers to measure |
|---|---|
| iCode in front; screen on, then dimming | none (baseline) |
| launcher in front | none; a held partial wake lock (the notification already offers one per session) |
| another app in front | same |
| screen off / locked, 3 min and 30 min | none; wake lock; the service's foreground type; the maker's own exemption, if the phone offers one to users |
| Activity recreated (rotation, theme); Activity finished (Back out) | before and after the host moves out of the ViewModel |
| UI process killed by the system | the ledger's report on the next start |
| a long install, a long build, a localhost server | the levers that held above |

Nothing is claimed about the Fast Freezer until the matrix says it. If some scenario cannot be kept
running on this phone, that is said to the user, as the session's state and in Settings.

**Measured (2026-09-30, a heartbeat started in an iCode terminal, one line a second):**

| Scenario | Ran | Freezer's own log (`Hiber/sceneManager`) |
|---|---|---|
| iCode in front | 90–95% | — |
| launcher in front | 5–14% | `freeze uid 10889` ~4 s after Home; brief thaws for `bumpService`, `wakelock`, network `packet` |
| another app (Settings) in front | 25% | frozen after ~25 s |
| screen off | frozen until the user returned | `unfreeze … reason:activity` |
| launcher, then screen off, **with a partial wake lock held** by the service | 5% | `freeze` 02:34:33 → `unfreeze reason:activity` 02:36:22 — the held lock did not prevent it |

iCode's app-info Battery page reads Unrestricted. Phone Master's "Freezer" page is a manual list
for unused apps, not this. [Mobile-Harness](https://github.com/techjarves/Mobile-Harness)'s
`RuntimeExecutionService` does the same as iCode — a `specialUse` foreground service, a partial wake
lock acquired automatically while a task runs, battery unrestricted — so it would be frozen alike
on this phone. **Parked by the owner (2026-09-30)**: the core storage plan goes first; R1 resumes
with the automatic wake lock (as Mobile-Harness), the runtime owned by the process rather than the
Activity, and truthful session state, since on a Transsion phone continuation cannot be
guaranteed.

## 4. The logical workspace (storage-logic.md)

1. **One logical tree per workspace**, `/workspace/<name>` in the runtime and in iCode, whose
   entries are physically on a **backend**: `SharedBackend` (the user's folder on shared storage —
   the canonical source) or `LinuxBackend` (iCode's private storage). One copy of each file.
2. **Storage classes, not stacks**: USER (the user's files, shared), LINUX (needs Linux semantics or
   heavy I/O), CACHE (regenerable), TEMP (per session). The foundation maps paths to classes; later
   profiles only *declare* such mappings (`node_modules` → LINUX, …). No `if Node` in the core.
3. **Every consumer reads the same projection**: the PRoot runtime (first try: its existing bind
   options), iCode's Explorer and editor, and a `DocumentsProvider`. Direct-path apps (MT Manager)
   see the USER part at its shared path — which is the part that must stay visible.
4. **Assumptions to prove on the phone before building on them**: PRoot projecting a private
   directory into a project on shared storage (create, rename, delete across the boundary; cwd and
   path resolution; `..`); file watchers inside the runtime on both backends; Git across the
   boundary; a build tool; process lifetime; `DocumentsProvider` read/write/rename/delete; the
   Explorer seeing external changes to shared files. Only if PRoot's binds cannot do it: document
   the exact limitation, then consider an iCode path-routing extension.
5. **Compatibility**: a detector for what shared storage cannot hold — case collisions, committed
   symbolic links, executable bits a project needs — surfaced only when present; the exceptional
   fallback is a Linux-native workspace wholly in private storage, still exposed through the
   provider. No storage choices in ordinary project creation.

## 5. S3 first measurement — PRoot projects a private folder into a project (2026-09-30)

Through iCode's own PRoot (same binary, binds and environment as its terminal), from `run-as`: a
project folder standing for the user's source, and a separate private folder bound at
`/workspace/proj/node_modules`.

| Check, inside the runtime | Result |
|---|---|
| `ls /workspace/proj` | `node_modules` beside `src`, `package.json`: one tree |
| create in `node_modules` | lands in the private folder |
| `mv src/move-me.txt node_modules/` | done |
| `cd node_modules`, `cd ..`, `readlink -f node_modules/../package.json` | `/workspace/proj/…`, as one tree |
| symbolic link in `node_modules` | made |
| `git init`, `git status` | the project's files; `.git` in the project folder; `node_modules` ignored per `.gitignore` |
| `npm install semver` | exit 0; the package in the private folder, `.bin/semver` linked; `package-lock.json` in the project folder |
| the host's view of the project folder | no `node_modules` made on the user's side — PRoot needs no placeholder |

Both folders were on ext4: `run-as` is refused on all shared storage (measured). Still to measure
from iCode's own process, on real shared storage: the bind onto a shared path that does not exist
there; a move across the boundary (two filesystems: the kernel answers `EXDEV`, and each tool must
copy instead); file watchers on both backends. Also measured: shared storage is mounted
**`noexec`** — nothing on it can be executed directly, whatever its bits (a compatibility fact for
S6: `./gradlew` needs `sh`).

## 6. S2 — the design (for the owner's review)

**One model.** A `LogicalWorkspace`: its user root (the shared folder — canonical, never copied),
its private root (`files/workspaces/<sha256 of the user root>/`, the key the settings already use),
and its **mounts**: relative paths whose storage class is not USER.

```text
StorageClass  USER   the user's files — shared storage, the default for every path
              LINUX  needs Linux semantics or heavy I/O — private, kept
              CACHE  regenerable — private, may be cleared
              TEMP   per session — private, cleared when the session ends
Mount(path relative to the workspace, class)       e.g. ("node_modules", LINUX)
```

**One resolver.** `resolve(relativePath) → (backend, physical file)`: the longest mount that
contains the path wins; otherwise USER. Pure and tested; everything else asks it.

**One projection, three consumers.**

| Consumer | What it gets from the same projection |
|---|---|
| The runtime — every PRoot launch (terminal, Git, ripgrep, apt, language services) | the user root at `/workspace/<name>`, then one bind per mount: `<private root>/<class>/<path>` → `/workspace/<name>/<path>` (measured above). `RuntimeLayout.prootCommand` takes the projection instead of a bare folder, so no launch sees a different tree |
| iCode's Explorer, editor, search results, file operations | a merged listing — the user root's entries, with each mount appearing where it belongs; reads and writes go through `resolve`; a move across backends is a copy then a delete, as the kernel requires |
| A `DocumentsProvider` (S5) | the same listing and `resolve` |

**Where mounts come from.** The foundation has no stack knowledge. For the proof of concept a mount
is a per-workspace setting (stored with the workspace's other settings); S7's profiles later only
*declare* mounts ("a `package.json` beside → `node_modules` LINUX"). A mount whose path already
exists on the user's side with content is not shadowed silently: it is reported (S6).

**Lifetime.** Private data lives with the workspace: listed per workspace in Storage settings,
cleared there (CACHE and TEMP freely; LINUX with confirmation), and removed with the workspace's
last trace. Uninstalling iCode deletes it — acceptable, because it is regenerable by design.

**Steps.** S3a the model and resolver, with tests. S3b `prootCommand` from the projection; one
workspace with one mount; measure the three open questions above on the phone. S4 the Explorer on
the merged listing. S5 the provider. S6 the compatibility detector (collisions, committed links,
`noexec`) and the Linux-native fallback. S7 profiles.

## 7. S3b — every launch binds the logical workspace; measured on real shared storage (2026-09-30)

`WorkspaceStorage` (`data/storage`) owns each workspace's private root and its mount list;
`RuntimeLayout.prootCommand` binds `LogicalWorkspace.binds()` for every launch with a workspace —
the terminal, Git, ripgrep, apt and language services go through it, so none sees another tree.
Measured with the scratch workspace (`s3b/node_modules`, `s3c/node_modules` LINUX), run in an iCode
terminal in front of the owner:

| Check | Result |
|---|---|
| the runtime's view | one tree: `node_modules` inside `s3b`, `s3c` |
| create in the mount | lands in private storage |
| `mv` shared → mount, and back | done both ways (`mv` copies across filesystems) |
| raw `rename()` shared → mount | `EXDEV` — as between any two filesystems; a tool that only renames must copy (`mv` does) |
| file watchers (`fs.watch`) | events on both sides: `change:w.txt` in `src`; `rename`/`change` in `node_modules` |
| `npm install semver lodash`, mounted | exit 0 in 8 s; `.bin/semver` linked |
| the same with no mount, on shared storage | **fails**: `EACCES … symlink '../semver/bin/semver.js' -> '…/.bin/semver'`; 0 files — the second cause of RevPlayer's failure, after the freezer |
| a script on shared storage | `./run.sh` → "Permission denied" (`noexec`); `sh run.sh` runs |
| the user's side of a mount | PRoot makes an **empty** folder at the mount path on shared storage when a session starts (even with the parent present); the contents stay private |
| `rm -rf` through a mount | removes the private folder too; the next tool that makes the folder again (`npm`'s `mkdir`) gets a private one; `WorkspaceStorage.of` also remakes it at the next launch |

Consequences for S4–S6: the Explorer must list a mount from its private folder (the shared side is
an empty placeholder); iCode's own file operations across the boundary copy then delete; the
compatibility detector reports `noexec` scripts (`sh gradlew`), committed links and case collisions.
