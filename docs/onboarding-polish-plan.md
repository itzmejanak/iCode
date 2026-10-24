# Onboarding — the install ring, one setup frame, and tool sizes in seconds

Status: **built** (2026-10-24). §6 says where the build differs from §3, the owner's request on
the bars among it.

## 1. The owner's request (2026-10-24, with four screenshots of a fresh install)

> "the runtime loader need to be into ⭕ circle progress that best fit on overall layout and ui
> ensuring that home page like ICode icon at background and make onboarding screens consistent and
> best feel... and the measuring of size is taking a lots of time or there is any bug figure it out
> and fix it. If any bugs issue and gaps you found while digging on this make sure to fill and be
> 100% accurate without any stale leftovers"

The screenshots: Installing Runtime at 5:37 with a bar and "PREPARING"; Ready the same minute;
Verify Settings at 5:38 with every row "Measuring…"; Runtime Tools at 5:39, still "Measuring…".

## 2. What is there now

### 2.1 The install screen (`RuntimeInstallingScreen.kt`)

- A straight bar (`ProgressTrack`) for the download, and a sweeping one
  (`IndeterminateProgressTrack`) for everything after it, extraction included. Its comment says
  extraction "holds at a fixed point", which it stopped doing when the sweep came: stale.
- Extraction reports how many files it has written, but not out of how many. The base system is
  pinned by its SHA-256 (`assets/runtime/manifest.json`), so its file count is fixed: **3,413**
  entries (`tar -tzf ubuntu-base-24.04.5-base-arm64.tar.gz | wc -l` on the verified archive). The
  extractor counts every entry the same way (`ArchiveExtractor.extractTar`, `place` keeps all).
- No logo. The launcher shows the logo as a letterpress in its free space (`IcodeWatermark`).
- On failure, Retry and Continue sit in the page, under the error.

### 2.2 The onboarding pages do not match

| Page | Top line | Heading starts | Actions |
|---|---|---|---|
| Installing Runtime | none | `Gutter` from the top | inline, on failure only |
| Set Up Tools | "SET UP TOOLS · STEP 2 OF 2", `Space6` from the top | under it, after `IcodePageColumn`'s `Gutter` | foot bar (`IcodeFootBar`) |
| Permissions | "Step 1 of 2", only with two steps | `Gutter` from the top | inline at the bottom |

### 2.3 Measuring the tools' downloads takes about a minute (measured 2026-10-24)

I measured on this Linux host (4 cores) with apt reading the phone's own lists: `ubuntu-ports`
for arm64, from the pinned base's `ubuntu.sources`, with the base's apt configuration and dpkg
status. Each run was timed natively and under the runtime lab's PRoot (`tools/runtime-lab.sh`).

| What | Natively | Under PRoot |
|---|---|---|
| `apt-get update` on a fresh runtime | 10.6 s: 37.5 MB fetched in 8 s, 205 MB of lists, a 60 MB `pkgcache.bin` | 9.0 s |
| One tool's `apt-get install --print-uris` | 0.72–0.97 s | 0.64–2.21 s |
| The same naming all ten tools' packages at once | 0.80 s | — |
| Nine tools, one after another | 8.0 s | 10.9 s |
| Nine tools, 2 / 3 / 4 / 8 at a time | — | 6.5 / 4.4 / 2.4 / 5.1 s |

On the phone, the lists alone were measured before: 36.5 MB in 1 min 1 s
([integration-plan.md](./integration-plan.md)). The screenshots' 1.65 MB/s gives about 23 s.

**Why it takes so long:**
1. **The lists come first and say nothing.** A fresh runtime has no package lists, so the first
   size fails and apt reads them. The page says "Measuring…" for the whole download.
2. **One apt run per tool, one after another.** Each run loads apt's 60 MB cache, so it costs the
   same whatever it measures (0.8 s naming one package or all of them, natively). Ten runs in
   a row cost ten loads.
3. **Two runs are the same run.** Each NDK revision's tool names only `xz-utils`.
4. **Nothing shows until the last run ends.** `RuntimeToolsCoordinator.measure` publishes every
   size at once.
5. **Install throws the lists away.** Apply cancels a measure still reading the lists, killing apt
   (`GuestCommand.run`'s watchdog). The install then reads them again from the start.
6. **A feature waits behind the page.** The measure holds the installer's dpkg lock from start to
   end. Clone asking for Git while the page measures waits for every size.

**Two facts the fix stands on, both checked:**
- **apt takes no lock to measure.** With dpkg's `lock` and `lock-frontend` held, `--print-uris`
  printed its archives and exited 0. A download (`-d`) said "Could not get lock".
- **The pinned base keeps apt's cache.** It ships only `01-vendor-ubuntu`, `01autoremove` and
  `70debconf` in `apt.conf.d`, with no `docker-clean`, so `pkgcache.bin` is kept between runs.

## 3. Decisions

### 3.1 The ring (`ProgressRing`, design system)

- It is Material 3's `CircularProgressIndicator`, already used in three places, in iCode's
  colours: the accent arc on `SurfaceTrack`, round caps, no gap. It is
  `IcodeSizes.ProgressRing` (176 dp) wide, with a 6 dp stroke (`IcodeSizes.RingStroke`).
- With a share known, the arc fills to it, animated. Without one, it sweeps.
- What it holds:

| State | Ring | Centre | Card under it |
|---|---|---|---|
| Starting, Preparing | sweeps | PREPARING | the installer's words |
| Downloading | fills by bytes; sweeps while the size is unknown | 45%, then DOWNLOADING | "Downloading … base", then "54.1 MB / 120 MB" |
| Extracting | fills by files of 3,413 | 60%, then EXTRACTING | "Extracting … base", then "2048 / 3413 files" |
| Configuring, Verifying | sweeps | CONFIGURING, VERIFYING | the installer's words |
| Ready | full | ✓, then READY | "Ready: Ubuntu 24.04.5 LTS" |
| Failed | none: the failure card takes its place | — | — |

- Each step's ring starts afresh, so the arc never runs back from a full download to an empty
  extraction.
- **The logo sits behind the ring.** It is the launcher's letterpress (`IcodeWatermark`), centred
  on the ring. The ring and the card under it sit together in the middle of the page's free space,
  which is clipped, so on a short screen the logo never shades the heading.
- **Only the install page has the logo.** Set Up Tools and Permissions are forms, rows of text a
  watermark would sit behind, and the launcher keeps it where its rows are cards.

### 3.2 Extraction counts against the pinned archive

- The manifest's `rootfs` gains `"entries": 3413`, read into `RootfsDescriptor.entries`.
- `RuntimeState.Installing` gains `step` (Preparing, Extracting, Configuring, Verifying) and
  `done` / `total` with a `fraction`. The installer fills them in as it goes.
- The background item, and through it the notification, shows extraction's counts and fraction
  as it does the download's.

### 3.3 One setup frame (`IcodeSetupPage`, design system)

- Every setup page has the same three parts, in the same places:
  - its stage line in small capitals, `Space6` from the top;
  - the page;
  - its actions in the foot bar.
- **Stage lines:**
  - Installing: "Linux Runtime", for repair and reset as well.
  - Set Up Tools: "Set Up Tools · Step 2 of 2".
  - Permissions: "Permissions · Step 1 of 2", or "Permissions" alone for one step.
- **The install page's foot** holds the free space, the same line Set Up Tools shows its cost on.
  On failure it also holds Retry and Skip (§6).
- **The permission page's foot** holds Not Now and Allow.
- **`PermissionExplanation` splits**: its reasons (`PermissionReasons`) are onboarding's page, and
  onboarding's bar holds the answers (`PermissionBar`). The explorer and the Run tab still draw both
  in their page. Its `header` slot goes.
- **Runtime Tools from Settings** keeps its top bar. Only Set Up Tools uses the frame.
- **The splash stays the brand moment:** the colour mark, the wordmark and "Code anywhere."

### 3.4 Sizes in seconds, as they land

In `RuntimeToolSizes` (taken out of `RuntimePackageInstaller`, which keeps reading the lists under
dpkg's lock) and `RuntimeToolsCoordinator.measure`:
1. **Three at a time.** That is about a 2.5× gain on this host. It also keeps well inside
   Android's limit of about 32 child processes in all: each size is PRoot and apt.
2. **One run per package list.** Tools naming the same packages share it; each adds its own
   setup's files.
3. **Each size shows as it lands**, the Standard tools first. Verify Settings, Standard's page, fills
   after its three tools.
4. **The lists are read once, under dpkg's lock, and said.** A size that failed before the lists
   were read is measured again after. The page's foot says "Reading the package lists…", and the
   starting step after Install says the same.
5. **Sizes take no lock.** As apt itself takes none, a feature's install never waits for them; it
   waits only for lists being read, which it needs anyway.
6. **Install keeps the lists.** Apply no longer cancels a measure. The install waits for the lists
   being read, and the sizes stop when an install or a removal begins, since it changes what they
   measure.
7. **Sizes for what is installed now.** A size measured against what was installed before a run
   is dropped. A request that comes while sizes are being measured runs once they end, so the
   page's "measure again" after an install is never lost.

The phone's own timing is not claimed until it is measured there.

## 4. Stale leftovers this removes

- The install screen's comments saying extraction has no measurable share.
- `PermissionExplanation`'s `header` slot, which only carried the step count.
- The tools coordinator's comment on cancelling a measure for Apply.
- [runtime-tools-plan.md](./runtime-tools-plan.md) §3.3 and [layout.md](./layout.md) §11, which
  describe the measuring and the install screen as they were.

## 5. Cycles

| Cycle | What | Done when |
|---|---|---|
| **U2** Sizes | §3.4 in the installer and the coordinator; `RuntimeToolsState.measuring` becomes Lists or Sizes | Scripted-apt tests: three at a time and no more; one run for two NDKs; the lists read once, with a failed size measured again; sizes while dpkg's lock is held; sizes stop at a run; a stale size dropped; a request while measuring runs after |
| **U3** Install and frame | §3.1–§3.3: manifest entries, `Installing` steps and counts, `ProgressRing`, `IcodeSetupPage`, the three pages | UI type-check; renders of the install page (downloading, extracting, failed) and of Set Up Tools and Permissions in the frame |
| **U4** Gate | Docs as built; the full gate | Push the branch and `main` |

## 6. As built

- **One bar at the foot (owner, 2026-10-24).** The owner asked, once the frame was drawn:
  > "For consistent bottom bar design please take reference from other screen for the onboarding
  > eg: workspace and git pages, explorer"

  The app's bars are one row: the Git pages' bar (`IcodeBottomBar`), and the Workspaces page's and
  the explorer's paste and choose bars. The frame's first foot was a tall block of full-width
  buttons instead. It is now the explorer's bar, made the design system's `IcodeActionBar`:
  - a raised band under its edge line, one touch target tall;
  - what it is about on the left, a line and a caption under it;
  - the actions on the right, at their full height.

  The paste bar (`ClipboardBar`) and the choose bar (`ChooseBar`) are drawn by it, unchanged but
  for a long name now taking a second line instead of being cut. `IcodeSetupPage` takes the bar.
  Runtime Tools from Settings has it too: its body is Set Up Tools'.
- **What each bar says:**

  | Page | Left | Right |
  |---|---|---|
  | Installing | the free space, "of 106.0 GB on this phone" | on failure: Skip (Back for a rebuild), Retry |
  | Set Up Tools, Runtime Tools | the cost (`RuntimeToolRules.cost`), the free space or why a size could not be measured | Skip or Back, then Next, Install, Apply, Remove or Finish |
  | The same, while apt runs | why a step can't be stopped, while it can't | Cancel while it can, Run in Background |
  | Permissions | — | Not Now, Allow … |

- **The failure's words.**
  - "Retry Installation" and "Continue without the runtime" became Retry and Skip, the words Set
    Up Tools uses.
  - "This cannot be retried on this device" moved into the failure card.
- **The foot's summary split.** `RuntimeToolRules.summary` became `cost`. The free space is said under
  it.
- **Document Recovery** keeps `IcodeFootBar`: its two long actions are not onboarding's.
