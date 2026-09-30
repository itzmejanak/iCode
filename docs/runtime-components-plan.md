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
