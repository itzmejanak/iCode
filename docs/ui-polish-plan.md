# UI polish: Git views, Explorer status, terminal menu, branch sheet, shared headers

Status: **plan** (2026-09-25). Owner's list of the same date. Follows
[backend-split-rules.md](./backend-split-rules.md) and [ui-split-plan.md](./ui-split-plan.md).

> **Note (2026-09-26).** Names in this record are as they were then. The demo layer (`DemoICodeGateway`, `DemoContent`, `staticDemo`, `demo-fork`) was replaced by debug-only capture fixtures (`CaptureGateway`, `CaptureFixtures`, `captureMode`, `capture-fork`) — [code-separation-plan.md](./code-separation-plan.md) K1.

## 1. Evidence (code and device, 2026-09-25)

| Owner's item | What the code does today |
|---|---|
| Diff: "Modified" in the header | `DiffViewScreen` top bar shows a status label, "MODIFIED" (unstaged) or "STAGED" |
| Diff and conflicts: pinch zoom | Lines are Compose rows (`DiffRow`, `DiffBand`) in `IcodeType.MonoSmall`; no gesture. The editor zooms natively (Sora), the terminal too (Termux) |
| Explorer: Git status like VS Code | `decorateFiles` marks a file only when its exact path changed; folders get nothing, so a change inside a collapsed folder is invisible. A changed file shows a coloured letter at the end of its row |
| Terminal: "More" does nothing | Termux's selection menu (Copy, Paste, More…) calls `TerminalView.showContextMenu()` for More (bytecode of terminal-view 0.118.3). Termux's app builds that menu; iCode registers none, so More does nothing |
| Branch sheet and the keyboard | `IcodeBottomSheet` is one scrolling column: search, every branch, then the new-branch field and its button — with many branches the field is at the far end of the scroll and is not kept above the keyboard |
| Headers and subheaders | Reference: the editor's bottom section, `BottomPanelBar` — a flat surface band, plain text entries, a count pill ("Problems 3"). Subheaders today are each different: `PanelTabStrip` (tools panel), `SegmentedControl` (Find, Quick Open, cursor style), a scope row (Settings), chips (Problems severities), a status line (Output), a stepper (Git credentials) |

## 2. Steps (each: build, tests, device check with a screenshot)

| Step | Change | Verify |
|---|---|---|
| **U1** | Diff header: no "MODIFIED"; "STAGED" stays, because the same file can have a staged and an unstaged diff and the header is what tells them apart | Device: unstaged diff shows the file name and path only |
| **U2** | One shared pinch-to-scale for code text in the design system (two fingers scale, one finger still scrolls; bounded; kept while the screen is open), used by the diff and the conflict views | Device: pinch in/out on both; scrolling and the hunk buttons still work |
| **U3** | Terminal "More…": iCode's own menu, drawn with the design system — Copy all output, Share output, Open a link in the output, Paste, Reset terminal | Device: long-press → select → More… → each item |
| **U4** | Bottom sheet with a fixed footer: title and search on top, the list scrolls in a bounded middle, the footer (new-branch field and Create) stays above the keyboard | Device: 20+ branches, focus the field — it stays visible while typing; short list unchanged |
| **U5** | Explorer: a folder takes the strongest status found beneath it (conflict > deleted > modified > added/untracked), shown as a coloured dot; a changed file's name takes its status colour plus the letter (M, A, U, D, C), as VS Code does. Rolled up by a pure, tested function | Device: a change deep in a collapsed folder marks every folder above it |
| **D1** | Proposal, for the owner's review before any restyling: an inventory of every header / subheader / third-level header (screenshots), and a shared set built on `BottomPanelBar`'s pattern — a subheader band for tabs and scopes, with counts and an active state, and a section-header row — mapped screen by screen: Settings, Find, Quick Open, tools panel (Terminal / Problems / Output), editor, and the others found | Owner approves or edits the mapping |
| **D2…** | Apply per screen, one at a time, each fitted to its content | Screenshot before/after per screen |

## 3. Record

**U1–U5 (2026-09-25) — done.** Checks: 241 unit tests pass (5 new for the tree
roll-up); lint 0 errors, 23 warnings (unchanged); UI consistency passes. On the
device, before the owner asked to stop testing: U1 header shows the file name
and path only; U4 footer stays above the keyboard with the list shrinking; U3
menu opens with Copy all output, Share output, Open a link, Reset terminal.

- U1 `DiffViewScreen`: "STAGED" only.
- U2 `PinchToScaleText` + `LocalCodeTextScale` (design system); `MonoLine`
  applies the scale, so only text inside the container changes; the diff list
  and the conflict file use it. The conflict file now also scrolls (a long file
  was cut off before). Pinch not checked on the device (adb has one pointer).
- U3 `TerminalMoreMenu`: the view's context-menu listener opens iCode's menu,
  anchored where the long press was (owner's report: it first opened at the top
  right). "Open a link" lists the output's links, newest first (at most 8);
  Share sends the last 100 000 characters (an intent carries a bounded amount).
- U4 `IcodeBottomSheet(footer = …)`: fixed title, a bounded scrolling middle, a
  fixed footer. Found on the way: every branch's delete button was labelled
  "Delete branch $branch" for screen readers (an escaped `$`); fixed.
- U5 `GitTreeStatus` (domain, pure): a folder takes the strongest status beneath
  it (conflict > deleted > modified > added > untracked); everything inside an
  untracked folder is untracked. Explorer rows: the name in its status colour, a
  letter for a file, a dot for a folder. Letters stay the codes Source Control
  shows, so the two screens agree.

**Capture regression pass (2026-09-25):** 39 routes captured, none broken.
Found: the diff, conflict and discard frames showed empty states — they read
state that production sets when a change is tapped, which a direct capture
never does. The demo snapshot now starts with those fixtures; the conflict
fixture's markers are now what Git writes (the screen adds "(Current Change)",
which showed twice). `capture-figma-screens.sh` and
`build-screenshot-contact-sheets.py` expected 40 routes; fixed to 39 with the
reason.

**D1 — inventory (2026-09-25).** Subheaders found, top to bottom of the app:

| Screen | Today |
|---|---|
| Editor (reference) | `BottomPanelBar`: flat surface band, plain text, "Problems" red with a count pill |
| Settings | band: accent label "Application Settings" + "Switch to Workspace" link |
| Find, Quick Open | two equal-width tabs, accent underline on the active one |
| Tools panel | tabs in the header row, the active one on a raised surface, "Problems 3" as plain text |
| Problems | two more rows: severity counts, then filter chips |
| Output | band: "Git operation output" + status |
| Git credentials | a step row with dots, then a segmented "Default / This repo" |
| Section labels (Appearance, Terminal Settings…) | small uppercase `SectionLabel` |

**D1 — proposal.** Three levels, one family:

1. **Header** — `IcodeTopBar`, unchanged.
2. **Subheader band** — new `IcodeSubheader`: the reference bar's surface,
   height and text; items left-aligned, each with an optional count pill in its
   tone (as "Problems 3"); an active item in the accent; an optional trailing
   slot (a status, a scope link, an action). One component for tabs, scopes,
   filters and a titled status line.
3. **Section header** — `SectionLabel`, unchanged in style, spacing aligned.

Mapped: Settings (Application | Workspace as items), Find and Quick Open
(Files | In Files), tools panel (Terminal | Problems 3 | Output), Problems (one
band — Errors 2 · Warnings 1 · Info 0 as filters — instead of two rows), Output
(title + status), Git credentials steps. Document tabs in the editor stay their
own pattern (a tab per open file, with close), restyled to the band's colours.
**Owner's decisions (2026-09-25):** an active item is accent text only —
exactly the reference bar's look, nothing added; the tools panel keeps its tabs
in the header row, restyled to the shared item (count pill on Problems); Problems
becomes one band, Errors · Warnings · Info as filters with count pills.
**And (same day): second- and third-level bars go to the bottom of the screen,
as the editor's bottom section does** — Find / Quick Open modes, the Settings
scope, the Problems filters, the Output status. The tools panel's tabs are the
header row and stay there; section labels inside scrolling content stay where
they are. With the keyboard open the band sits just above it. Also decided:
the editor's file tabs stay under its header; Git credentials' steps become its
bottom bar, the Continue button just above it; the diff's summary and Prev /
Next become one bottom bar instead of the summary line and two big buttons.

**D2 (2026-09-25) — done.** `IcodeBottomBar` + `BottomBarItem` + `BottomBarEntry`
(design system) are the editor's bottom section made shared: its band, height,
type and count pill; the active item in the accent only; items scroll sideways
when large text overflows; an item can be disabled (muted, not tappable).
`BottomPanelBar` is now built on it, unchanged in look. Moved to the bottom:

| Screen | Bottom bar |
|---|---|
| Find / Quick Open | Files · In Files, and in In Files mode the Aa · Ab* · .* toggles (the chip row is gone) |
| Settings and Workspace Settings | Application · Workspace (switching replaces the page, as a tab; Back returns to where settings were opened); the "WORKSPACE" header label is gone |
| Problems | Errors · Warnings · Info as filters with coloured count pills (the two rows are gone) |
| Output | "Git operation output" and its status |
| Git credentials | 1 Identity · 2 Authentication · 3 Verify, under the step's button; later steps muted until an identity is valid |
| Diff | "+2 −1 · 1 hunk" with ⌃ Prev · Next ⌄ (the summary line and the two big buttons are gone) |

The tools panel keeps its tabs in the header row, drawn as the bar's entries;
only the open tab is coloured (a red "Problems" next to an orange active tab
read as two selections — seen on the device, changed). `PanelTabStrip`,
`PanelTab` and `PanelTabPadding` had no other user and are removed. The editor's
file tabs stay on top (owner). Rendered on the device from the capture set:
every changed screen shows its bar; nothing overlaps. Checks: 241 unit tests,
lint 0 errors / 23 warnings (unchanged), UI consistency passes.

