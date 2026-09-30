# Runtime components — choose the tools, install them in the open — plan

Status: **plan, for the owner's review** (2026-09-30). Owner's request, after Generate SSH Key sat
on "Generating…" for two minutes: fix the wait (A: "the button says what is happening, and it
no longer holds up the app"), and (B) "on onboarding, after the runtime installation, show an
Android Studio–like UI where the user can make custom installation selections — a better and
scalable design". Follows [backend-split-rules.md](./backend-split-rules.md); the install is
background work as [long-operations-plan.md](./long-operations-plan.md) §6 defines it.

## 1. Measured (phone and code, 2026-09-30)

What happened (the owner's phone, fresh build 7f17d68):

| Step | When | Evidence |
|---|---|---|
| Generate SSH Key tapped | ~14:21:19 | `apt-get install … openssh-client` had run 1:33 just after 14:22:50 |
| apt downloads 6 packages, 1,238,158 bytes | ~14:21:19 → 14:23:17 (**~2 min**) | the six `.deb` in `/var/cache/apt/archives`; `partial/` empty at 14:22 |
| dpkg installs them | 14:23:17 → 14:23:22 (**5 s**) | `/var/log/apt/history.log` Start/End-Date |
| key and SSH config written | 14:23 | `~/.ssh/id_ed25519`, `config` |

The mirror answered pings in 220–300 ms, one IPv4 ping of two lost. The two minutes are close to
apt's default 120 s timeout — a stalled connection retried, likely but not proven.

What the code does:

| Gap | Where |
|---|---|
| **The button says "Generating…" through the whole install** — nothing says tools are being downloaded, nor how far | `GitAuthCredentialForms.kt:138` |
| **The install holds the workbench's mutation lock** — open, save, create and Git wait behind a network download. Test Connection (a network `ls-remote`) and Install Git do the same | `AndroidWorkbenchGateway.kt:239` (`withLock`), `:308`, `:327`, `:328` |
| **One flag for two actions**: `GitAuthState.busy` is set by Generate and by Test, so each button reads the other's work; `sshAvailable` is never set nor read | `GitContract.kt:213` |
| **Tools arrive only on first use**, one by one, each paying for `apt-get update` (36.5 MB, measured 1 min 1 s, [integration-plan.md](./integration-plan.md)) when the lists are missing | `CliGitService.provision`, `GitAuthenticator.ensureSshAvailable`, `RipgrepService.ensureAvailable` |
| **"Installed" is decided three ways**, each by a binary path restated in its own file | `GIT_RELATIVE_PATH`, `SSH_KEYGEN_RELATIVE`, `RG_RELATIVE_PATH` |
| **An install shows no progress anywhere**: `RuntimePackageInstaller.installing` is only the package's name | `RuntimePackageInstaller.kt` |

apt's own lines, as recorded on the phone (`term.log`, 2026-09-30), are what progress can be read
from: `Unpacking libcbor0.10:arm64 (0.10.2-1.2ubuntu2) ...`, then `Setting up … ...` once per
package; before them apt prints its plan (`N upgraded, M newly installed, …`) and one `Get:k …`
line per file it fetches ([integration-plan.md](./integration-plan.md) records `Get:1 http://ports.ubuntu.com/ubuntu-ports noble InRelease`).

## 2. The reference — Android Studio's setup wizard and SDK Manager

- **Install Type**: *Standard* (the recommended set) or *Custom*.
- **SDK Components Setup**: a checklist — each component with its size; the selected one's
  description; the **total download size** and the free space under the list.
- **Verify Settings**: what will be downloaded, before anything starts.
- **Downloading Components**: one bar, the step in hand, and **Show Details** — the installer's
  own log.
- Later, the **SDK Manager** shows the same list with what is installed ticked: tick to install,
  untick to remove, Apply.

The phone keeps the substance on one page: the choice, the list with sizes and the total, then
progress on the same page.

## 3. Design

### 3.1 One catalog (`domain/RuntimeComponents.kt`, pure)

A component is what the user chooses; packages are what apt installs.

```kotlin
data class RuntimeComponent(
    val id: String,               // "git"
    val name: String,             // "Git"
    val summary: String,          // "Version control"
    val usedBy: String?,          // "Source Control, Clone, Pull and Push" — what in iCode needs it
    val packages: List<String>,   // ["git"]
    val recommended: Boolean,     // in Standard
)
```

Features name the component they need (`RuntimeComponents.Git`), never a package or a path. A new
component is one entry. The first catalog (§5 D1):

| Component | Packages | Used by in iCode | Standard |
|---|---|---|---|
| Git | `git` | Source Control, Clone, Pull, Push | ✓ |
| SSH client | `openssh-client` | SSH keys, `git@` remotes | ✓ |
| ripgrep | `ripgrep` | Workspace search | ✓ |
| curl | `curl`, `ca-certificates` | — (downloads, installers such as nvm) | |
| Python 3 | `python3`, `python3-pip`, `python3-venv` | — | |
| Build tools | `build-essential` | — (native npm and pip modules compile with it) | |

### 3.2 Installed is read, not guessed (`data/runtime/DpkgStatus`)

Installed means dpkg says so: `/var/lib/dpkg/status` read from the app's side (no process, no
PRoot), `Status: install ok installed` per package. A component is installed when all its packages
are. It replaces the three binary-path checks.

### 3.3 Sizes are apt's (`--print-uris`)

A component's size is its **download**, including dependencies not yet installed, as apt
computes it: `apt-get install --print-uris -qq <packages>` lists each archive to fetch with its
size. The selection's total is computed the same way on the whole selection, so a shared dependency
counts once. It needs the package lists, which any install needs anyway: the page starts reading
them (`apt-get update`) when it opens, shows the list at once, and fills in the sizes when they
are known. It shows "…" until then, and never a number written in the code.

### 3.4 One installer, with progress (`RuntimePackageInstaller`)

- **Several packages in one apt run** (`apt-get install -y --no-install-recommends a b c`): one
  plan, one download session. The dpkg mutex, "update the lists and try once more" and
  "a second request waits for the running install" stay.
- **Progress from apt's own lines** (`AptProgress`, pure, tested on the recorded lines): the plan
  gives the total (`upgraded + newly installed`). `Get:k` gives *Downloading (k)*.
  `Unpacking x` / `Setting up x` give *Installing x (k of N)*. The bar fills by packages set up, and
  sweeps while downloading, since apt reports no byte progress when it is not on a terminal.
- **Published as one state**, `ComponentInstall`: the components, the phase, the item, k of N,
  the last lines (for Details), and the failure in apt's words. The background-work item and its
  notification ("Installing Git, SSH client… 4 of 12"), the components page, and any button
  waiting on it all read that one state.
- **Cancel only while downloading.** Stopping apt then leaves partial downloads, which apt resumes.
  Stopping dpkg leaves an interrupted package database, so once unpacking starts there is no Cancel,
  as delete has none (§5 D4).
- Removal: `apt-get remove -y --autoremove <packages>`, the same state and rules.

### 3.5 The Components page (new route `RuntimeComponents`)

One page, two places:

- **Onboarding**: Installing Runtime → **Components** → permissions → launcher, offered once
  (recorded like a permission offered). It opens on **Standard** (the ✓ set) or **Custom**
  (the checklist). Under the list: "Download <total> · <free space> free". Actions: **Install** and
  **Skip** — skipping installs nothing now; a feature that needs a component installs it when
  used (§3.6).
- **Settings → Runtime Environment → Components**: the same list, installed ones ticked
  ("Installed"); tick to add, untick to remove, **Apply** — the SDK Manager.

Each row shows its name, summary, "Used by …" and its size or "Installed". Once started, the page
turns into its progress: the bar, the step ("Downloading · 3" → "Setting up git · 9 of 12"),
**Details** (apt's last lines, as Studio's Show Details), **Hide** (the notification carries it,
§6 of the long-operations plan), and Cancel while downloading. A failure shows apt's words, then
**Retry** and **Skip**.

No Figma frame exists for it. It is built from the design system's parts (page heading, option
cards, check rows, the progress track, the bottom action bar), as Import was.

### 3.6 A feature that needs a component (the owner's A)

- **Off the lock**: Generate SSH Key, Test Connection and Install Git run on their own jobs, one
  at a time each, never inside the mutation lock. They touch the runtime's home and the network,
  not the workspace. The lock is taken only if a result changes the workbench.
- **The button says what is happening**, read from `ComponentInstall`: "Installing SSH client… 3
  of 6", then "Generating…". Test has its own state ("Testing…"): `busy` becomes
  `GitAuthWork { GeneratingKey, Testing }`, and the dead `sshAvailable` goes.
- The feature installs what it needs when the user asks for it (the tap is the consent), through
  the same installer. No second question.

## 4. Steps (each: gate, commit; device where it touches a screen)

| Step | What |
|---|---|
| C1 | Domain: the catalog, `AptProgress` (the recorded lines as tests), selection rules (Standard, Custom, what to install and remove), the published install state |
| C2 | Data: `DpkgStatus`; the installer for several packages with published progress, cancel while downloading, removal; sizes by `--print-uris` (the output recorded on the device first); Git, SSH and search ask through the catalog; background item from `ComponentInstall`. JVM tests with a scripted apt |
| C3 | A: Generate SSH Key, Test Connection and Install Git off the lock; the buttons read the install; `GitAuthWork`; `sshAvailable` removed |
| C4 | UI: the Components page, the onboarding rule with its step (with the route it leads to), the Settings entry, capture fixture and route tests |
| C5 | Device: §6 |

## 5. Decisions — the owner may overrule

- **D1** The first catalog is §3.1's six. Node.js is not in it: Ubuntu 24.04's `nodejs` is v18,
  older than what projects expect (RevPlayer runs on v24.21.0 from nvm). Node needs a second kind
  of source (the official binaries, checksum-verified), a later step.
- **D2** Standard = Git, SSH client, ripgrep: what iCode's own features use.
- **D3** Removal is in (untick in Settings), as the SDK Manager does.
- **D4** Cancel while downloading only; no Cancel once dpkg starts.
- **D5** A feature needing a missing component installs it on the user's tap, with its progress
  on the button, and asks nothing more.
- **D6** Runtimes installed before this (the owner's) are not touched. The Settings page shows
  what they have, and features install on use.

## 6. Device checks

| # | Check | Expected |
|---|---|---|
| K1 | Fresh install: runtime, then Components | Standard ticked; sizes appear after the lists are read; total and free space |
| K2 | Install Standard; open Details; Hide | Progress by packages, apt's lines; notification "Installing … k of N"; Git, SSH, search ready without waiting |
| K3 | Custom: add Python 3 | Its size joins the total; installed; `python3` runs in the terminal |
| K4 | Skip, then Generate SSH Key | "Installing SSH client… k of N", then "Generating…"; opening a file meanwhile does not wait |
| K5 | Settings → Components on the owner's runtime | Git, SSH client ticked "Installed"; untick ripgrep → removed; search installs it again on use |
| K6 | Cancel while downloading | Stops; nothing half-installed; Retry resumes |
| K7 | Airplane mode during an install | apt's words, Retry, Skip; the app is usable meanwhile |

## 7. Record

- **C1** (2026-09-30): `RuntimeComponents` (domain): the catalog of §3.1, Standard = the three
  iCode uses. `ComponentRules`: installed when dpkg has every package; the page opens on Standard
  plus what is installed; a plan installs what is chosen and missing, and removes what is unticked
  and installed, keeping any package a component that stays lists; the download counts each archive
  once, and is unknown until each part is measured. `ComponentWork`: the one published install,
  with its title and whether Cancel is safe (before dpkg only). `AptProgress`: apt's plan gives the
  total, `Get:` lines the package being downloaded (a list's `Get:` is updating, not a package), and
  dpkg's `Setting up` / `Removing` lines, which always name a version, the count. A maintainer
  script's "Removing obsolete conffile …" is not counted. Tested on the dpkg lines recorded on the
  phone. The onboarding rule moved to C4, with the route it leads to. 16 tests. Gate: 539 tests;
  lint 0 errors / 12 warnings; both guards pass.
- **C2** (2026-09-30): recorded on the phone first (read-only apt in the runtime): `--print-uris`
  gives `'<uri>' <file> <size> MD5Sum:…` per archive; a simulation `Remv curl [8.5.0-…]`; a real
  download `Get:1 http://ports.ubuntu.com/ubuntu-ports noble/universe arm64 ripgrep arm64
  14.1.0-1 [1455 kB]` and `Need to get 1455 kB` (no thousands separator: C1's tests were corrected
  to these lines). `DpkgStatus` reads dpkg's status file on the app's side, again only when it
  changes. Installed means the state word is `installed` (`hold` and `deinstall ok installed` are
  still on disk). `RuntimePackageInstaller` is by component: an install is `apt-get install -d`
  (cancellable: nothing installed yet), then the install from the cache (never stopped). Missing or
  old lists are read once, then it tries again. Progress and apt's last lines are published as
  `ComponentWork`. A feature's failure is its to say; Apply's stays until read. Apply always runs
  on its own, queued: it never takes over a feature's install. Removal marks the packages as no
  longer wanted and removes what apt would now remove, less what it would have removed before
  (another cause's leftovers). What another package needs is not removed and says so, and its mark
  is put back. Measuring: each component alone, the lists read once when apt names nothing; one it
  cannot find is left out with apt's reason. `RuntimeComponentsCoordinator` follows dpkg and the
  run into the snapshot (sizes measured again once what is installed changes) and takes the
  page's intents off the lock. The background item is the snapshot's run: step, package,
  count, and Cancel while it is safe. Git, the SSH client and search ask by component; the three
  binary-path checks are gone. The gateway stays at its budget (373). 18 installer and coordinator
  tests (5 runs, no failure), 2 dpkg, 1 progress. Gate: 552 tests; lint 0 errors / 12 warnings;
  both guards pass.
- **C3** (2026-09-30): Generate SSH Key, Test Connection and Install Git leave the mutation lock
  (the gateway launches them; Install Git takes the lock only for the repository's refresh after
  it). Generate and Test run one at a time (`GitAuthWork`: GeneratingKey, Testing — a second
  request while one runs is dropped); `busy` and the never-read `sshAvailable` are gone. The
  buttons read the component's install: "Installing SSH client · Downloading 1238 kB", then
  "· Setting up 3 of 6" (dpkg's own word: the step says "Setting up k of N"), then "Generating…".
  Install Git (credentials page) and Source Control's gate say the same for Git. A client that
  could not come says apt's words, not "could not be installed". Found on the way: Install Git
  also set a Git operation, which the background list carried as a second notification for the
  same install; the Git item now carries transfers only (`GitTransferLabel.ALL`). Gate: 554 tests;
  lint 0 errors / 12 warnings (checked against lint's fresh analysis: an unchanged report is not
  rewritten); both guards pass.
- **C4** (2026-09-30): the Components page (`RuntimeComponentsScreen`, `RuntimeComponentsParts`;
  route `RuntimeComponents`, no Figma frame, built from the design system's parts plus a new
  `IcodeCheckbox`). A runtime build (install, repair, reset) leaves `components-offer` beside its
  marker; `RuntimeCoordinator` publishes Ready and the offer in one update, so the screen that
  routes on Ready sees both. The install screen then puts the page in its own place, and the
  splash routes to it when a process ended before it was answered. Install, Continue, Skip or Apply
  answers it once, and it is not offered again. A runtime built before this has no offer (the
  owner's is untouched). Onboarding: Standard (preselected, fixed ticks) or Custom; Install or
  Skip; the summary under the list: "Download … · … free", as apt measured it. Settings → Runtime
  Environment → Components: installed ticked, "Removes" in amber when unticked, Apply. While apt
  runs, the page is its progress: title, bar (sweeping, then filling by packages set up), step,
  package, Details (apt's last lines), Cancel while it only downloads, Hide. A failure shows apt's
  words, with Retry and Close. After an offered install ends well, onboarding moves on by itself.
  Its notification opens the page (`BackgroundPlace.Components`, replacing the unused `App`).
  `OnboardingRoute`: runtime → components (when offered) → permissions → launcher. Docs: layout
  §11, US-ENTRY-003 and architecture §7.2 record the step and that it forces nothing (the PRD's
  "no forced tool decisions" stands: Standard is preselected, Skip installs nothing). Found by the
  gate: lint's MutableCollectionMutableState on the Custom choice (now an immutable set with a
  saver), and a needless capture check (the UI guard). The capture script counts framed routes
  only, so it stays at 39. 5 onboarding and coordinator tests added. Gate: 557 tests; lint 0 errors
  / 12 warnings; both guards pass.
- **Found on the phone** (2026-09-30, the owner's first run after C4): a clone over SSH opened
  blank, then "Bad object HEAD". The cause is not left-over data: it recurs after a clean install.
  Git finishes a pack by hard-linking it into place. Android refuses link(), and PRoot's stand-in
  is a symbolic link naming an absolute path; iCode clones into a staging folder and renames it,
  and every such link breaks. Reproduced in the runtime with a second repository
  (Tom60chat/Extension-List): cloned as before and renamed as iCode renames, `git cat-file` said
  "bad object HEAD" and fsck exited 10. With `core.createObject = rename` the same clone and rename
  read clean (status clean, fsck 0). Even a `mv` inside the runtime breaks an emulated link,
  both names at once. Fixed at the source: the runtime's `/etc/gitconfig` sets
  `core.createObject = rename`, written when the runtime is built, beside dpkg's setting. Git then
  renames its finished objects and packs into place, as it does wherever link() fails, so a
  repository holds no emulated links to break. Second: opening a workspace ran `git inspect`, and
  its failure failed the whole open (the blank Explorer). Now a repository Git cannot read still
  opens with its files; Source Control shows "Git cannot read this repository" and Git's words,
  with Check again — neither "no changes" nor "Initialize Git" (`gitFailure`). Source Control's
  gate choice moved into its gates file (the screen stays under 300 lines). A runtime built before
  this has no `/etc/gitconfig` until it is rebuilt (Repair keeps the home, with its SSH key, and
  the workspaces). Other tools that hard-link inside a workspace (cargo's incremental cache, pnpm)
  still get PRoot's emulated links, which a rename breaks — PRoot's own limit, not addressed here.
  Gate: 558 tests; lint 0 errors / 12 warnings; both guards pass.

