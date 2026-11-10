# The editor's completion list — plan

Status: C1–C4 done in code (2026-11-10); the phone checks in §6 are the owner's. Owner's request, with a phone screenshot of the list in `route.js` while
typing `aw`: "make the behaviour and the suggestions drop-down UI/UX better, clean and perfect, the
best fit for the UI". The list in the screenshot shows `await`, `AudioWorklet`, `AudioWorkletNode`,
`DataView` and `HTMLTableRowElement`. It has bare names, a box twice as tall as its rows, a box
covering the lines under the caret, and two names that only loosely contain "aw".

Follows [lsp-plan.md](./lsp-plan.md) §7 (completion) and [layout.md](./layout.md) (popups, the 1 dp
edge), on Sora Editor 0.24.6.

## 1. Measured (code and Sora's source, 2026-11-10)

**The popup is Sora's default, coloured only (`EditorTheme.kt`).**
- Rows are reserved 45 dp each (`DefaultCompletionItemAdapter.getItemHeight`), but the row layout
  wraps to about 28 dp. Five items take a 200 dp box with about 60 dp of empty space.
- An indeterminate progress bar sits at the top of the box.
- Each row leaves a 20 dp space for an icon that is never set.
- The box is placed half a row into the next line (`panelY = rowBottom + rowHeight/2`) and never
  above the caret. On a phone it is 7/8 of the editor's width, centred.

**Only the label is drawn.**
- The kind is set but has no icon.
- A server's detail goes to Sora's right-hand `desc` column. TypeScript sends none in the list.
  `labelDetails.detail` (a signature such as `(x: number)`) is dropped in parsing.
- The matched letters are never coloured: Sora colours them only for its own
  `SortedCompletionItem`, which iCode does not use.

**Ranking.**
- `EditorCompletionRules.matching` keeps any item whose name holds the typed letters in order,
  with any gaps (`DataView` holds a…w), then sorts by two keys: starts with the prefix, then the
  server's `sortText`.
- TypeScript gives every global one `sortText`, so after the prefix matches the server's own order
  stands.
- `LspResults` keeps only the first 500 items before any filtering. TypeScript sends its globals
  unfiltered, about 1500 of them, so a match past the 500th is lost.
- Results of two servers are joined with no de-duplication.

**When it opens.**
- Every inserted character asks the servers, whatever it is: a space, `;`, `(`. With no word typed,
  everything passes the filter, so after a space the list opens with up to 200 globals.

**Keys.**
- Soft-keyboard Enter accepts the first row (`selectCompletionItemOnEnterForSoftKbd`, Sora's
  default) even though no row is shown as chosen.
- The key row's Tab inserts a tab instead of the suggestion. Its ↑ and ↓ move the caret, which
  closes the list.

## 2. The reference

**VS Code's suggest widget.**
- Each row is a kind icon, the label with its matched letters bold in the accent, the label's
  detail (signature) muted after it, and the type at the right.
- The first row is focused, and Enter or Tab accepts it.
- The widget sits under the line, or above it when there is no room below.
- Matching is `fuzzyScore`: the first typed letter must match at the start of the word or of a word
  inside it (a camel hump, after `_` or `-`). Contiguous runs, word starts and the same case score
  higher, and the best score sorts first, then the server's `sortText`.
- Suggestions open as a word is typed or on a server's trigger character (`.`), not on spaces or
  punctuation.

**JetBrains and Xcode** mark each kind with a letter in its colour (`m` method, `c` class,
`f` field…), as Sora's own `SimpleCompletionIconDrawer` does.

## 3. Decisions

**D1 — VS Code's matching** (`EditorCompletionRules.match`).
- The first letter must match at a word start. The rest match in order.
- Scoring: a word start scores more, a run of contiguous letters more again, and the same case a
  little. The best match is chosen by dynamic programming in O(pattern × word), and its letters'
  positions are kept for drawing.
- Sorting: by score, then `sortText`, then the shorter label.
- For `aw`: `await`, `AudioWorklet`, `AudioWorkletNode`, and no `DataView`.
- *Rejected:* keeping the subsequence filter with more sort keys. The loose matches would still be
  listed.

**D2 — Asked only where a word is typed or a server's trigger character is.** In
`LanguageRequests`, a character before the caret that is neither part of a word nor one of that
server's trigger characters asks nothing. The list closes on a space, as VS Code's does.

**D3 — Nothing dropped.**
- All of a server's items are parsed, up to 5000, before the editor filters them.
- `labelDetails.detail` is kept as `LanguageCompletionItem.labelDetail`.
- Two servers' identical items show once.

**D4 — A row: badge, label, signature, type** (`EditorCompletionWindow.kt`).
- The badge is a 20 dp rounded square holding the kind's letter (Sora's and JetBrains' first
  letter, lower case) in its syntax colour on that colour's tint, from the editor's own
  `syntaxColor`:
  - function colour: method, function, constructor;
  - type colour: class, interface, struct, enum, type parameter, module;
  - attribute colour: field, property, event;
  - constant colour: constant, enum member, value;
  - keyword colour: keyword, operator;
  - accent: snippet;
  - muted: the rest.
- Then the label in the editor's font, its matched letters in the accent, and the signature
  (`labelDetail`) muted after it.
- At the right, the type (`detail`), muted, cut short at its start.
- A deprecated item is struck through.
- The row is 36 dp high, and its height is the height reserved for it.

**D5 — The box fits its rows and the caret.**
- Its height is its rows, at most 6.
- It sits a hair under the caret's line, or above it when there is more room above and too little
  below.
- It is as wide as the editor allows, up to 420 dp, starting where the word being completed starts,
  kept 8 dp inside the editor.
- It is the raised surface with the 1 dp outline and an 8 dp corner, as iCode's menus are, with no
  progress bar.

**D6 — Keys say what they do.**
- The first row is drawn as chosen, because soft-keyboard Enter accepts it.
- The key row's Tab accepts the chosen row, and its ↑ and ↓ move the choice, as a keyboard's arrows
  already do in Sora. ← and → still move the caret.

**D7 — Installed once, through Sora's own seam.** `IcodeCodeEditor` replaces Sora's
`EditorAutoCompletion` with `EditorCompletionWindow`, a subclass with its own adapter and layout.
Sora's request, threading, publisher, key handling and `select` are kept unchanged.

## 4. Cycles

- **C1 — what is listed.** The matcher with positions, de-duplication, the trigger rule
  (`LanguageServiceRules.asksCompletion`), `labelDetail`, and the larger parse bound. Tests: the
  screenshot's case, camel humps, case, runs, `sortText` ties, positions, duplicates, the trigger
  rule, and `labelDetails` parsing.
- **C2 — how it looks and where.** The window, the row adapter and the layout; position and
  height; badges.
- **C3 — keys.** The key row's Tab, ↑ and ↓, and the first row shown as chosen.
- **C4 — docs as built.** This plan, the CHANGELOG, and lsp-plan §7.

## 5. As built

- **C1 — what is listed.**
  - `ui/editor/EditorCompletion.kt`:
    - `CompletionMatcher` is VS Code's strong-first matching. Scoring: a letter 1; the same case 1
      more; the word's first letter 8 more, any other word start 6; a run 5; a gap 1 less; the whole
      word 4 more. The best placement is found by dynamic programming over tables reused between
      words, and its positions are kept.
    - `EditorCompletionRules.matching` returns `RankedCompletion`s, sorted by score, `sortText`,
      length, then label, with two servers' identical items shown once.
    - `LanguageCompletion` carries the item and its matched letters.
  - `LanguageServiceRules.asksCompletion` is used in `LanguageRequests.complete`.
  - `LanguageCompletionItem.labelDetail` is parsed from `labelDetails.detail`.
  - `LspResults.MAX_COMPLETIONS` is 5000.
- **C2 — how it looks and where.** `ui/editor/EditorCompletionWindow.kt`:
  - `EditorCompletionWindow` extends Sora's `EditorAutoCompletion` and is installed in
    `IcodeCodeEditor` through `replaceComponent`.
  - Position: under the line with 2 dp to spare, or above it; at most 6 rows of 36 dp, cut to whole
    rows; up to 420 dp wide, starting under the word; kept 8 dp inside the editor.
  - `CompletionList`: the raised surface, a 1 dp edge, an 8 dp corner, no progress bar.
  - `CompletionRows` and `CompletionRow`: the badge (20 dp, the kind's letter in its syntax colour
    on a fifth of it), the label in the editor's font with its matched letters in the accent and its
    signature muted after it, and the type at the right, cut at its start. A deprecated item is
    struck through. The chosen row is inset 4 dp with a 6 dp corner.
  - With no rows the window does not show.
  - `PaletteColorScheme.palette` is open to the window for the badge colours.
- **C3 — keys.** The first row is drawn as chosen. In `CodeEditorSession.press`, Tab is
  `acceptChosen()` and ↑ ↓ are `moveChoice(up)`, each falling back to the caret when no list shows.
- **Tests:**
  - `EditorCompletionRulesTest`: the screenshot's case, camel humps, acronyms, snake case, runs
    and case, `sortText` ties, the empty prefix, positions on the label, duplicates.
  - `LanguageServiceRulesTest`: the trigger rule.
  - `LspResultsTest`: `labelDetails`.
- **Gate:** 1518 unit tests run, and the only failure is `TreeSizeTest`'s root-only case, as on
  main. assembleDebug and assembleRelease build. Both consistency guards pass. Lint reports only
  main's existing error and nothing new in the touched files.

## 6. Phone checks (owner)

1. In `route.js`, type `aw`: `await` first, `AudioWorklet…` after it, no `DataView`. "aw" is
   coloured in each.
2. Each row shows its kind's badge. A function shows its signature and type.
3. Near the bottom of the screen, above the keyboard, the list opens above the line.
4. A space or `;` closes the list. `.` after an object opens its members.
5. The key row's Tab accepts the highlighted row, and ↑ and ↓ move it.
