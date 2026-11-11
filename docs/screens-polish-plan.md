# Four Screens Made to Fit: the Empty Editor, Find, About and the Runtime Page

Owner's request (2026-11-11), with screenshots:

- The editor's empty state is not good, and its content does not scroll when the recent list is long.
- In Find, the result cards' heights are not consistent; they are a little big and do not fit.
- About's Build Specifications card is not good.
- The Runtime page needs a new, clean design: less text, no big "healthy" card for what is only a
  state, and consistent with the other pages.

## 1. What is wrong, and why (verified in the code)

| Screen | What the owner saw | Cause |
|---|---|---|
| Empty editor (`EditorEmptyScreen.kt`) | Recent files do not scroll | The page is a plain `Column`. The Recent section gets `weight(1f)` and draws every row into that fixed height, so rows past it are cut and nothing can scroll (up to 20 recent files reach it). |
| | Big, repetitive cards | One card per file. A generic document icon instead of the file's own icon. The path repeats the name ("CHANGELOG.md / CHANGELOG.md"). The 64 dp mark and "Select a file to begin coding" take a third of the screen. |
| Find (`WorkspaceFindResults.kt`) | Heights differ; cards too big | Each result is a card around a list row. The second line is the full host path (`/data/data/dev.icode.mobile.debug/files/workspaces/iCode/…`), which wraps to 1–3 lines. |
| | "Showing the best 50 matches" with nothing typed | An empty query ranks every file; the cap message is an amber warning. |
| | (found on the way) | Ranking matches the query against the host path, so "data" or "files" matches every file. |
| About (`AboutScreen.kt`) | "Runtime VersionUbuntu 24.04.5…": label and value touch; values huge | `DetailRow` puts label and value in a `SpaceBetween` row with no gap or weight: a long value takes all the width, so the gap becomes 0. Values are Mono 13 sp under an 11 sp label. The same row is used in 14 places. |
| | (found on the way) | "Export Diagnostics — Not available yet" is a row that does nothing. |
| Runtime (`RuntimeSettingsScreen.kt`) | Cluttered, too much text | A big green-bordered "Runtime Healthy" card for a state. Six detail rows, one of them a 3-line host path. The tools row lists every tool and is cut off. Repair and Reset are big buttons with a paragraph each, pinned to the foot. The page does not scroll. |
| | (found on the way) | Healthy is green here, but in your accent colour on the Launcher (your decision, 2026-09-30). |

## 2. The proposal

### D1 — Three shared pieces, so the four screens look like one app

- **`DetailRow`, fixed for all its 14 uses** (About, Runtime, Git history, MCP, the notice sheet…):
  - The label keeps its width, with at least a `Space3` gap.
  - The value is end-aligned in MonoSmall and `TextPrimary`, the same size as its label, on at most
    2 lines.
- **`IcodeFileRow`**, promoted from the file explorer's private row to the design system. One height
  for every file everywhere:
  - the file's own icon (the icon theme's);
  - the name on one line;
  - the folder on one line, relative to the workspace, cut from its start so the nearest folders show
    (`…/ISSUE_TEMPLATE`);
  - a trailing slot (an age, a match count).

  The explorer, the empty editor's Recent list, Find's files and In Files' groups all use it.
- **`IcodeFactGrid`**: a card holding a 2-column grid of facts, each a label above its value. About's
  build card and the Runtime page's numbers use it.

### D2 — The empty editor

```
←  iCode
   /workspace/iCode
┌──────────────────┬──────────────────┐
│ 🔍 Quick Open     │ ⌕  Search Files   │
├──────────────────┼──────────────────┤
│ ＋ New File       │ >_ Terminal       │
└──────────────────┴──────────────────┘
RECENT FILES
┌──────────────────────────────────────┐
│ [M↓] assistant-questions-plan.md  12h │
│      docs                             │
│ [M↓] CHANGELOG.md                 12h │
│      iCode                            │
│ …                    (all of them)    │
└──────────────────────────────────────┘
 Terminal   Problems
```

- **One scrolling list** (a `LazyColumn`) holds the actions and every recent file, above the
  Terminal/Problems bar.
- **Four actions in a 2×2 grid** of equal tiles. Search Workspace becomes the fourth tile ("Search
  Files") instead of a link row of its own.
- **Recent files in one card**, as `IcodeFileRow`s: the file's icon, its name, its folder (the
  workspace's name for a top-level file), and its age.
- **The mark and the title** appear only when there are no recent files yet: the mark, "No file open"
  and "Files you open appear here".

### D3 — Find (Files and In Files)

```
←  Find
   iCode
[🔍 Go to file by name or path        ]
RECENT                     (nothing typed)
 [⚙] .editorconfig
     iCode
 [🤖] dependabot.yml
     .github
 [!] bug_report.yml
     .github/ISSUE_TEMPLATE
 …
 Files   In Files
```

- **Rows, not cards**: `IcodeFileRow`s, one height each. The first result (what Enter opens) keeps its
  accent name on a soft highlight.
- **The folder is the workspace-relative one**, never the host path. Ranking matches the name and the
  relative path only, which fixes "data" matching everything.
- **Nothing typed: your recent files first** (layout.md §14 asks for this), with no warning. A typed
  query with more than 50 matches says "50 of 312 shown" quietly, in the caption style, not amber.
- **In Files**:
  - each file's group header is the same row, with its match count and a chevron;
  - its matches sit under it as mono lines with a line-number gutter (`12  val text = …`), tap to open
    at the line.

### D4 — About

```
      [logo]  iCode
              MOBILE IDE · built by itzmejanak
BUILD
┌───────────────────┬───────────────────┐
│ Version           │ Android           │
│ 0.1.0-debug       │ API 30 and later  │
├───────────────────┼───────────────────┤
│ Runtime           │ Editor            │
│ Ubuntu 24.04.5 LTS│ Sora Editor 0.24.6│
│ arm64-v8a         │                   │
└───────────────────┴───────────────────┘
DIAGNOSTICS · RESOURCES · PRIVACY   (as now, as Settings' rows)
```

- **Build becomes an `IcodeFactGrid`**: four short facts, never a label touching its value.
- **"Export Diagnostics — Not available yet" goes.** The other rows become Settings' rows (`SettingRow`:
  icon, title, one line), so About looks like the rest of Settings.

### D5 — The Runtime page

```
←  Runtime Environment
┌──────────────────────────────────────┐
│ Ubuntu 24.04.5 LTS          ● Healthy │
│ noble · arm64-v8a                     │
├───────────────────┬──────────────────┤
│ Packages          │ PRoot             │
│ 587               │ 5.1.107.92        │
├───────────────────┼──────────────────┤
│ Runtime size      │ Free on phone     │
│ 2.3 GB            │ 12.1 GB           │
└───────────────────┴──────────────────┘
TOOLS
 [🧩] Runtime Tools   12 of 13 installed  ›
MAINTENANCE
 [↻] Repair Runtime       Keeps your home and workspaces      ›
 [⟲] Reset Runtime        Keeps only your workspaces          ›   (red)
```

- **The big green card goes.** The state is a dot and a word ("● Healthy") beside the release, in your
  accent colour as on the Launcher; "● Unavailable" stays amber.
- **One summary card**: the release, then its codename and architecture, then the numbers as an
  `IcodeFactGrid`.
- **Removed from the page**: the Runtime Home path (iCode's private folder) and the Manifest row.
- **Runtime Tools says only its count.** The tool names are on its own page.
- **Repair and Reset become two rows** with one short line each. Their full explanation moves into the
  confirmation sheet they already open. Reset's row is red.
- **The page scrolls.**
- **When the runtime is not healthy**, the same card says why in one line ("● Unavailable" or
  "● Installing…"), with Install or Repair as its button.

## 3. Questions for the owner

1. **The empty editor's mark and title**: (a) only when there are no recent files, or (b) a small one
   always. *Recommended: (a).*
2. **Search Workspace as a fourth action tile** in a 2×2 grid, instead of its own row. *Recommended:
   yes.*
3. **The Runtime page's state**: is the big healthy card meant to become a "● Healthy" dot and word in
   the summary card, in your accent colour? *Recommended: yes.*
4. **Runtime Home and Manifest**: remove them from the Runtime page, or keep them under a folded "More
   details". *Recommended: remove.*
5. **About's other rows**: restyle them as Settings' rows and drop "Export Diagnostics", or change only
   the build card. *Recommended: restyle and drop.*

## 4. Cycles

| Cycle | What |
|---|---|
| **P1** | `DetailRow` fixed; `IcodeFileRow` and `IcodeFactGrid` in the design system; the explorer on `IcodeFileRow` |
| **P2** | The empty editor (D2) |
| **P3** | Find and In Files (D3), with the ranking fix |
| **P4** | About (D4) |
| **P5** | The Runtime page (D5) |
| **P6** | layout.md (§13.4, §14, §15.2, and new sections for About and the Runtime page), CHANGELOG |

Each cycle is its own commit, with the full gate and both guards run before it. This sandbox has no
phone or emulator to render screens on, so how each screen looks is for the owner to check on the
phone after each push.

**The owner's answers (2026-11-11): "Go"**, taking every recommendation: (1) the mark only with no
recent files; (2) Find in Files as the fourth tile; (3) the healthy card becomes a dot and a word in
the accent; (4) Runtime Home and Manifest removed; (5) About's rows restyled, Export Diagnostics
dropped.

## 5. Record

Built on `ui/screens-polish`, from `main`. The cycles were built together and committed as one,
gated once: every screen stands on P1's pieces.

- **P1 — shared pieces.**
  - `DetailRow`'s value takes the rest of the row, end-aligned, in MonoSmall, on two lines at most,
    a `Space3` gap from its label. This fixes all 14 uses.
  - `MonoLine` takes a `textAlign`, and `SettingRow` a `titleColor`.
  - `IcodeFileRow` is the explorer's row, now in the design system. Its folder line ellipsizes from
    the start (`TextOverflow.StartEllipsis`).
  - `IcodeFactGrid` and `IcodeFact` are in `designsystem/IcodeFacts.kt`.
  - `FileFolders` (`domain/files`) gives a file's folder in its workspace.
  - `RuntimeDetails.releaseName` and `codename` split "Ubuntu 24.04.5 LTS (noble) base" into its
    name and codename.
- **P2 — empty editor.**
  - One `LazyColumn` between the top bar and the Terminal/Problems bar, so the list scrolls.
  - The 2×2 tiles, then the Recent label and one `IcodeFileRow` per recent file of the workspace.
  - The mark, "No file open" and "Files you open appear here" show only while there are none.
- **P3 — Find.**
  - File results are `IcodeFileRow`s, no cards. The best match is accented and highlighted once
    something is typed.
  - With nothing typed, the workspace's recent files show under "Recent".
  - The caption says "N of M shown", or that a large workspace was indexed in part.
  - `rankFiles(files, query, root)` matches a file's path within the workspace (`RankedFiles.total`),
    and the attach sheet passes its root too.
  - In Files: each file is an `IcodeFileRow` with its match count and a chevron. Its matches follow
    as lazy items, with a padded line-number gutter.
- **P4 — About.**
  - Build is an `IcodeFactGrid`: Version, Android ("API 30 and later"), Runtime (name ·
    architecture) and Editor.
  - Every other row is a `SettingRow` with its icon. Links show an open-in-new mark, and links not
    yet set are drawn muted.
  - Export Diagnostics is gone.
- **P5 — Runtime.**
  - The page scrolls (`IcodePageColumn`), with Settings' subtitle.
  - One card: the release name, then its codename and architecture, then `StatusLabel`. The label is
    Healthy in the accent; Installing and Not installed in amber; Failed in the error colour;
    Checking muted.
  - Ready, the card shows the facts: Packages, PRoot, Runtime size and Free on phone. Otherwise it
    shows the reason and, for not installed or failed, Set Up the Runtime.
  - Tools: Runtime Tools as "N of M installed".
  - Maintenance: Repair (keeps your home and workspaces) and Reset (keeps only your workspaces, in
    red) are `SettingRow`s. Both are disabled while a rebuild runs, with a spinner on the one that
    runs.
  - The confirmation sheets are unchanged.
  - Runtime Home, Manifest and the big state card are gone.
- **P6 — docs.**
  - layout.md: §13.4 rewritten, a new §13.5 File rows, §14 and §15.2 brought up to date, and §19.3
    gains the label/value rule plus the Runtime and About pages.
  - The CHANGELOG.
- **Checks.** Full gate, both guards, and new tests:
  - `FileFoldersTest`;
  - `WorkspaceFindResultsTest`, which checks that the phone's folders never match.

  How the screens look is for the owner to check on the phone; this sandbox cannot render them.
