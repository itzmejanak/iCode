# Assistant chat — what a turn did, in sheets

Status: **built** (2026-10-24). §4 says where the build differs from §2.

## 1. The owner's request (2026-10-24, with the Claude app as the reference)

> "I think we need to redesign the assistant ui to reflect the run commands details to show on
> sheet rather then expand collapse logic as currently and I have attached the Claude ui reference
> also address other areas like that if any others aspects you found for ui enhancement and to make
> consistency and best user/feel/experience. Also make sure to long press user message copy and
> select text options and select text open as sheet.."

**The reference, the Claude app:**
- **Steps in a row.** A run of tool calls between two pieces of text is one muted line ending in ›,
  for example "Ran 3 commands, created a file +98 −0" or "Read 2 files". A single call is named by
  what it did.
- **A tap opens a sheet.**
  - For a run: the sheet is titled "Ran 3 commands" and lists each call on a rail. A tap on one
    opens it in the sheet, with ← back.
  - For one call: the sheet is titled by the tool ("Bash"), with its status ("Completed"). Under
    that come Description, Command and Output, each in a box with its own Copy.

**iCode today:**
- **Steps open inline.** Each call is its own row: "✓ Read /workspace/ForgeKit/README.md ⌄". A tap
  opens its output under it, in a box a few lines high (`Disclosure`, `AssistantSteps.kt`).
- **A file step's actions hide behind a long press:** Open in Editor, Show Changes, Copy Path.
- **Other things fold inline the same way:**
  - the model's reasoning ("Reasoning · 1.2k words ⌄");
  - a compaction's summary;
  - the approval card's "all it will do".
- **The user's message** does nothing on a long press, and its text cannot be selected. Answers
  can be selected where they are (`SelectionContainer` in `MarkdownAnswer`).

## 2. Decisions

### 2.1 A run of steps is one row

- **Grouping.** Steps that follow one another in the chat, with no answer or reasoning between
  them, are one row: `AssistantEntry.Steps`, made by `AssistantStepRules.rows` from the entries.
  `AssistantRules.entries` stays as it is, so each step keeps its key and its status.
- **What the row says:**
  - **One step:** its summary, as now ("Read README.md", "Run `npm test`").
  - **More than one:** what they did, in the order they first did it, as the reference says it:
    "Read 2 files, listed a folder, ran a command".
  - Each tool's phrase comes from one table in the domain, `AssistantToolName`. Its ids are the
    names the data layer's tools are called by, so a name is written once.
- **Its status icon.** In order of what matters:
  - a question waiting (amber);
  - running (accent);
  - next (muted);
  - any failed (red);
  - else done (green).
- **Its changes.** The lines added and removed by its edits, summed ("+98 −3").
- **Its affordance.** A › instead of ⌄. A tap opens its sheet.
- **A long press** on a single step about a file keeps its menu (Open in Editor, Show Changes,
  Copy Path), as the owner asked on 2026-10-14. The sheet offers the same three as buttons.

### 2.2 The step sheet

`AssistantStepsSheet`, over the chat (`IcodeSheetOverlay` + `IcodeBottomSheet`):
- **A run's page:**
  - titled by the row's words;
  - each step on a rail: status icon, summary, changes, ›;
  - a tap opens that step's page in the sheet, with ← back.
- **A step's page:**
  - titled by its tool ("Command", "Read file", "Edit file"…, from `AssistantToolName`), with its
    status ("Completed", "Running", "Next", "Failed", "Waiting for your answer");
  - its summary;
  - **Command** (a command's line, whole) or **File** (its path, with Open in Editor, Show Changes
    and Copy Path);
  - **Output**, all of it, selectable, with Copy. A step not run yet says so.
- **Live.** The sheet reads the chat as it is, so a step that finishes while its sheet is open
  shows its output there.
- **The question waiting.** A step whose call waits for an answer shows, in place of Output, what
  the approval card asks about:
  - **What it will do** (the change's text, or the command);
  - what it reaches outside the workspace;
  - what "Allow in" opens.

### 2.3 Everything else that folded inline opens a sheet too

- **Reasoning:**
  - Once recorded: its row ("Reasoning · 1.2k words ›") opens a sheet with the thinking,
    selectable, with Copy.
  - While still written: it shows under its row as now, so it can be watched.
- **A compaction:** its row opens a sheet with the summary.
- **The approval card:** a tap on its question opens the waiting step's sheet (§2.2) instead of
  folding open. The card keeps its first line and its three answers. `Disclosure` goes.

### 2.4 The user's message

- **A long press** offers **Copy** and **Select text**.
- **Select text** opens a sheet holding the message's text, selectable: Android's own handles and
  toolbar copy any part of it. **Copy All** under it takes it whole.
- **Answers** keep what they have: selectable where they are, and Copy under the turn's last one.

### 2.5 One sheet, more ways in

- `IcodeBottomSheet` gains:
  - an optional **subtitle** (a step's status);
  - an optional **back** arrow, for a sheet's page within the sheet, as the reference's ←.
- The sheet's text pages share one part (`AssistantTextSheet`): reasoning, a compaction's summary,
  a message's Select text.

## 3. Cycles

| Cycle | What | Done when |
|---|---|---|
| **S1** Domain | `AssistantToolName` (the tools' ids, phrases, titles); `AssistantEntry.Steps`; `AssistantStepRules.rows`, `summary`, `status`, `changes` | Tests: runs grouped and broken by an answer or reasoning; one step's summary; the phrases, counted, in first-seen order; the status order; changes summed |
| **S2** Screens | Rows (§2.1), the step sheet (§2.2), text sheets (§2.3), the approval card's tap, the message's long press (§2.4), the sheet's back and subtitle (§2.5) | UI type-check; renders of the chat, a run's sheet, a step's sheet, the menu and Select text |
| **S3** Gate | Docs as built (PRD PR-ASSIST-07, user stories, layout, assistant-permissions-plan §4.3); the full gate | Push the branch and `main` |

## 4. As built

- **Where it lives.**
  - The domain: `domain/AssistantSteps.kt` (`AssistantToolName`, `AssistantStepRules`, the status
    words), tested by `AssistantStepRulesTest`. The data layer's tools take their names from
    `AssistantToolName`.
  - The rows: `AssistantSteps.kt` (`AssistantStepsRow`, `AssistantOpenRow`, `AssistantRail`, the
    file's actions).
  - The sheets: `AssistantDetailSheets.kt` (`AssistantEntrySheet`, the run's and the step's pages,
    `AssistantTextSheet`), opened by `AssistantScreen` as its `Entry` sheet.
- **One row for all of it.** A run of steps, the reasoning and a compaction are all drawn by
  `AssistantOpenRow`: its icon, what it is, what it changed, and ›. Each is a touch target high
  (48 dp), as the reasoning's line was. Reasoning still being written is the same row without ›.
- **The rail.** On a run's page, the rows touch, and a line runs from each icon to the next.
- **The step's page:**
  - **Command**, **Output** and **What it will do** are each a selectable code box with **Copy** at
    its corner.
  - **File** shows its path, with **Copy** at its corner for the path the runtime names it by (the
    explorer's Copy path), its lines changed, and **Open in Editor** and **Show Changes** as buttons.
  - A command's question shows its line once, as **Command**: its "What it will do" is the same
    line.
  - A step with nothing to show says "Running…", "Not run yet" or "No output".
- **The question waiting is answered in its sheet too.** Its step's page holds the card's three
  answers at its foot (`ApprovalAnswers`, one part for both). After an answer the page stays open
  and shows the step running, then its output. The card's question opens the run it is in, at its
  step.
- **Back.** On a step's page in a run, Back returns to the run before it closes the sheet.
  `IcodeSheetOverlay` now composes its own Back handler before its sheet, so a sheet's own handler
  comes first. No other sheet has one, so nothing else changed.
- **A sheet whose row is gone closes.** A sheet is kept by its row's key, per chat. When the row
  is no longer in the chat (another chat opened, a compaction), the sheet closes.
- **The chat follows a run as it grows.** A run's new step is followed to the end as a new row is.
- **The message's menu** sits under the message, anchored to it. A photo-only message has no long
  press.
- **Removed:**
  - `Disclosure` and `AssistantStepRow`;
  - the approval card's fold, and `IcodeSizes.ApprovalDetail`, which only it used.
- **Checked:**
  - the UI type-check;
  - the consistency guards;
  - `AssistantStepRulesTest` with `AssistantRulesTest` (30 tests);
  - a desktop scene of the real chat view, rows and sheets, driven by touches in both palettes. It
    tapped a run, a step in it, ←, a file's step and its Show Changes, long-pressed the message,
    then tapped Select text, the reasoning, the question and its Allow in, and the compaction.
