# Syntax Highlighting: What Stands, What Is Missing, and the Architecture to Get There

Owner's request (2026-11-11): every file and folder type is now covered and the architecture is far
better, but syntax highlighting still has architectural gaps and limits. Understand what we have,
what we need, and whether a migration is needed, to get scalability, no duplication, no
inconsistency, reliability, stability, performance and speed. Make a proper plan.

Nothing is built yet. §6 lists the questions to answer before building.

## 1. What stands (verified on `main`, c5b2e01)

### 1.1 The pieces

| Piece | Where | What it does |
|---|---|---|
| Engine | Sora Editor 0.24.6, `language-textmate` (tm4e and native Oniguruma) | TextMate grammars, the same family VS Code uses |
| Grammars | `assets/textmate/`: 33 folders, 42 registrations in `languages.json`, 3.8 MB | Each language's grammar and `language-configuration.json`. Copied unchanged from VS Code 307b60e7 or Sora; provenance in `editor-grammar-provenance.md` |
| File → grammar | `domain/FileTypes.kt`: `FileType.grammar` is a scope string | Extension, name, prefix and `#!` line pick the scope |
| Scope → colour role | `ui/editor/SyntaxScopes.kt`, one table | VS Code Dark+ selectors, each placed in an iCode `SyntaxRole` |
| Role → colour | `designsystem/SyntaxColors.kt` | The palette's colour for each role |
| Theme | `ui/editor/EditorTheme.kt`, `editorTheme(palette)` | A TextMate theme JSON built at run time per palette; no theme file is shipped |
| Loading | `TextMateAssets` (in `EditorTheme.kt`) | All 42 grammars load once, on a background thread at app start (about 1.4 s on the owner's phone) |
| Editor colouring | `CodeEditorSession.applyLanguage` → `TextMateLanguage.create(scope)` | Sora's `TextMateAnalyzer` colours the open document incrementally, line by line, off the main thread |
| Other code colouring | `ui/editor/TextMateCode.kt`, `textMateRuns(scope, code)` | Markdown code fences (chat, Markdown preview), tokenized whole, outside Sora |
| Guards | `GrammarAssetsTest`, `SyntaxScopesTest`, `EditorThemeTest`, `EditorColoursTest` | Every include is shipped or listed as not shipped; every grammar is reachable; every scope is placed; every theme parses |

### 1.2 What is already right, and stays

- **TextMate is the right engine for iCode.** It is VS Code's own, works offline, needs no native
  library per language, and covers 42 languages under test. §3.1 weighs it against tree-sitter.
- **One scope table, one role set, one palette.** Colours are never written per language.
- **The theme is built at run time** from the palette, so every theme and accent colours code.
- **The grammars are pinned and recorded**, and the tests catch drift.

## 2. The gaps

Each gap names the quality it costs: Scalability, Duplication, Inconsistency, Reliability,
Stability, Performance.

| # | Gap | Cause (file) | Costs |
|---|---|---|---|
| G1 | **Two tokenizing paths.** The editor uses Sora's analyzer. Markdown fences use `textMateRuns`, which reads tm4e's `org.eclipse.tm4e.core.internal.*` classes directly. | `TextMateCode.kt` | Duplication; Stability: an internal API breaks on any Sora/tm4e update |
| G2 | **Runs carry resolved colours, not roles.** `CodeRun.color` is the theme's colour at tokenizing time, so a palette change tokenizes every code block again, and a code block drawn under a different palette (a theme preview) is wrong. | `TextMateCode.kt`, `MarkdownCode.kt` | Performance; Inconsistency |
| G3 | **One global current theme.** tm4e resolves colours against `ThemeRegistry.currentThemeModel`, a process singleton that whichever screen set it last decides. | `TextMateAssets.setTheme` | Reliability |
| G4 | **Code outside the editor and Markdown is not coloured at all.** This covers the diff view (`DiffViewScreen`, `GitDiffLineRow`), the editor's change sheet, conflict resolution, and the assistant's file previews. The same line of Kotlin is coloured in the editor and plain in its diff. | those screens use `MonoLine` | Inconsistency |
| G5 | **All or nothing at start.** Every grammar is parsed before any can be used: about 1.4 s and all 3.8 MB in memory, even to open one JSON file. One bad grammar fails them all (`Grammars.Failed` is global), so colouring is off everywhere. | `TextMateAssets.whenGrammarsLoaded` | Performance; Reliability; Scalability: every grammar added makes start slower |
| G6 | **A language is declared in three places.** These are the scope string in `FileTypes`, the entry (name, scope, paths, embedded languages) in `languages.json`, and the asset folder, with the provenance doc as a fourth. Tests check that they agree, but each new language means editing all of them by hand. | `FileTypes.kt`, `languages.json` | Duplication; Scalability |
| G7 | **No limits for large or minified files.** The editor opens text up to 5 MiB (`WorkspaceFileSource.MAX_TEXT_BYTES`) and colours all of it. A minified line is tokenized for up to 2 s (`LINE_TIME`) before tm4e gives up, and the analyzer keeps a state per line. VS Code stops tokenizing a line past 20 000 characters and skips colouring very large files. | `CodeEditorSession`, Sora defaults | Performance; Stability |
| G8 | **Thread safety is not established.** `textMateRuns` tokenizes on `Dispatchers.Default` with the same `IGrammar` objects Sora's analyzer threads use. tm4e compiles rules lazily on first use. No test shows two threads tokenizing one grammar safely. | `TextMateCode.kt` | Reliability (to be confirmed by S1's test) |
| G9 | **Coverage gaps.** No grammar for TOML (`Cargo.toml`, `pyproject.toml`), Vue, Svelte, Protobuf, Terraform/HCL, GraphQL, Haskell, Scala, R, Perl, PowerShell, CMake, Nix, Zig, Elixir, JSON5 or CSV. `FileTypes` has no rule for most of these, so they open as plain text. Another 34 scopes that shipped grammars embed are listed as not shipped (`GrammarAssetsTest.NOT_SHIPPED`). | `FileTypes.kt`, assets | Scalability |
| G10 | **No semantic colouring.** The language servers iCode already runs (pyright, clangd, gopls, the TypeScript server) can send semantic tokens, telling a parameter from a field from a type. iCode never asks for them. | `data/language` | Accuracy (later; §3.3) |
| G11 | **Colouring failures are mostly silent.** A grammar that fails sets `coloringError` and logs. A file colouring had to skip says nothing. | `CodeEditorSession` | Reliability (the user cannot tell "no grammar" from "broken") |

## 3. Decisions

### 3.1 Engine: stay on TextMate (D1)

| | TextMate (today) | tree-sitter (Sora's `language-treesitter`) | LSP semantic tokens only |
|---|---|---|---|
| Accuracy | Good: regex-based, VS Code's level | Best: a real parse tree | Best, but only where a server runs |
| Coverage now | 42 languages, tested | A native parser and highlight queries per language | Only languages with a running server |
| Offline, no install | Yes | Yes | No: needs the runtime and a server |
| APK size and build | 3.8 MB of JSON | A native `.so` per language and per ABI, built with the NDK | None |
| Editing speed | Incremental per line (Sora) | Incremental parse | Server round trips; arrives late |
| Risk of migrating | — | High: every language redone, every test rewritten | — |

**Decision: keep TextMate as the one base engine.** Fix its architecture (§3.2). Add semantic
tokens as an optional layer on top later (§3.3). Do not migrate to tree-sitter. Tree-sitter's gain
(structure, accuracy) does not repay rebuilding 42 languages, native builds per ABI, and losing
VS Code parity. Revisit only if Sora's tree-sitter support matures and one language needs structure
TextMate cannot give, such as folding by syntax.

### 3.2 One syntax layer, used everywhere (D2)

A single owner of the engine, `ui/syntax/` (UI side, since tm4e and Sora are Android libraries).
Every screen that shows code goes through it.

```
domain/syntax/Languages.kt     what a language is: id, scope, grammar and config assets,
                               what it embeds, what it includes   (the one catalog, D3)
domain/FileTypes.kt            file → language id (no scope strings any more)

ui/syntax/SyntaxEngine         the only code that touches tm4e and Sora's registries:
   ├ grammar(scope)            loads that grammar and what it includes, lazily, once (D4);
   │                           a failure is that language's alone
   ├ tokens(lang, text)        read-only views: role-based spans (D5), cached, bounded (D6)
   └ language(lang)            the editor: Sora's TextMateLanguage on the same lazy grammar
ui/syntax/SyntaxTheme          one TextMate theme per palette, from SyntaxScopes (as today)
ui/syntax/SyntaxLimits         when colouring stops: long lines, large files (D7)
ui/syntax/rememberSyntax()     Compose: spans for (language, text), coloured by the palette at draw time
```

- **The editor keeps Sora's incremental analyzer.** It is the right tool for editing. It only takes
  its grammar from `SyntaxEngine`, not from an eagerly filled global registry.
- **Read-only views share one path.** Markdown fences, the diff view, the change sheet, conflict
  resolution and assistant file previews all call `rememberSyntax(language, text)`. The same line
  looks the same everywhere (G4).
- **`textMateRuns` and its `internal` imports go (G1).** The engine tokenizes through tm4e's public
  `IGrammar.tokenizeLine` and reads scopes, not encoded attributes.

### 3.3 Semantic tokens, later and optional (D8)

When a language server for the open file offers `semanticTokensProvider`, its tokens are laid over
the TextMate colours. Each token type (parameter, property, enumMember…) maps to an existing
`SyntaxRole` through one table beside `SyntaxScopes`, so no new colours are needed. With no server,
or a server that is slow, TextMate's colours stand alone. This is cycle S7, after the base is sound.
It is on a setting, default on.

### 3.4 One catalog for languages (D3)

`domain/syntax/Languages.kt` lists each language once: its `id` (VS Code's language id), `scope`,
its grammar and configuration assets, its embedded languages, and its upstream (commit and licence).

- `FileTypes` rules name a language **id**; the scope is looked up.
- `languages.json` is no longer hand-written. The engine registers grammars from the catalog
  (Sora's `GrammarDefinition` API), or a Gradle task generates the JSON from the catalog. S2 picks
  whichever Sora 0.24.6 supports cleanly.
- The provenance doc's table comes from the catalog's upstream fields, with a test that it matches.
- Adding a language means one catalog entry, one asset folder and one `FileTypes` rule. The tests
  say what else is missing.

### 3.5 Lazy, per-language loading (D4)

- Nothing is parsed at app start except the theme. A grammar is parsed the first time a file or
  block needs it, together with its `include` closure (HTML pulls in CSS and JavaScript). The
  closure is computed from the catalog, and the existing include test keeps it honest.
- While a grammar loads, its text shows plain (as today) and colours as soon as it is ready.
- A grammar that fails is that language's failure alone. It is said once (G11), and every other
  language keeps colouring.
- One loader thread, so two screens asking for the same grammar parse it once.

### 3.6 Role-based tokens, cached and confined (D5, D6)

- The engine returns `SyntaxSpan(start, end, role, bold, italic)` built from scopes through
  `SyntaxScopes`. The colour is applied when drawn, from the current palette. A palette change
  redraws without tokenizing again (G2), and nothing depends on a global current theme (G3). The
  editor still needs a TextMate theme for Sora, and `SyntaxTheme` keeps building it from the same
  table.
- **One tokenizer thread** for read-only views. It is confined, so read-only tokenizing never races
  itself. S1's test checks whether the editor's analyzer threads and this one can share a grammar.
  If they cannot, read-only views get their own grammar instances, loaded once.
- **An LRU cache** keyed by (language, text hash), bounded by total characters (for example 2 MB),
  so a chat that scrolls past the same block, or a diff opened twice, is not tokenized again.

### 3.7 Limits (D7)

`SyntaxLimits`, one place, tested:

| Limit | Value (proposed) | Over it |
|---|---|---|
| Line length coloured | 10 000 characters | The rest of that line is plain; the next line starts fresh |
| Time per line | 100 ms (from 2 s) | That line stays plain; colouring goes on with the next |
| File coloured | 2 MiB or 50 000 lines | Opens and edits as plain text, with a one-line note in the editor: "Colouring is off for files this large" |
| Read-only block | 200 000 characters | Plain |

The values are set from measurements on the owner's phone in S3, not guessed. The editor still
opens files up to 5 MiB, as today.

## 4. Cycles

| Cycle | What | Gaps | Exit gate |
|---|---|---|---|
| **S1 — measure and pin** | JVM benchmark: grammar parse time per language, tokenize time for a 10 000-line file and a 1 MB minified line. Concurrency test: two threads tokenizing one grammar. Record the numbers here. | G7, G8 | Numbers recorded; the concurrency question answered |
| **S2 — the catalog** | `domain/syntax/Languages`; `FileTypes` names language ids; registrations come from the catalog; provenance checked by test | G6 | All existing grammar tests pass on the catalog |
| **S3 — the engine** | `SyntaxEngine` with lazy per-language loading and per-language failure; `SyntaxLimits`; the editor's `applyLanguage` through it; `TextMateAssets` gone | G5, G7, G11 | Cold start parses nothing; opening JSON parses only JSON; a broken grammar affects only its language (tests) |
| **S4 — role spans and one read-only path** | `tokens()` and `rememberSyntax()`; LRU cache; Markdown fences moved over; `textMateRuns` and its internal imports gone | G1, G2, G3, G8 | No `org.eclipse.tm4e.core.internal` import (guard rule); a palette change re-tokenizes nothing (test) |
| **S5 — code everywhere** | The diff view, change sheet, conflict resolution and assistant file previews coloured through `rememberSyntax` | G4 | The same file's lines get the same roles in the editor and in its diff (test on spans) |
| **S6 — coverage** | TOML, Vue, Svelte, Protobuf, HCL, GraphQL, CMake, PowerShell, Scala, Haskell, R, Perl, Elixir and Nix grammars from VS Code or their maintained upstreams, each with its `FileTypes` rule and provenance. The list is to be confirmed (Q3). | G9 | Every new scope placed in `SyntaxScopes`; include closure test green |
| **S7 — semantic tokens** (optional) | LSP `textDocument/semanticTokens/full` and its delta form, laid over TextMate; token type → role table; setting | G10 | With pyright: parameters and properties coloured apart; without a server, unchanged |
| **S8 — docs** | This record, `editor-plan.md`, `editor-colors-plan.md`, provenance, `layout.md` where the editor's note appears, CHANGELOG | — | — |

Each cycle is its own commit, with the full gate and both guards run before it. A new backend guard
rule, `tm4e-owner`, keeps every tm4e and Sora-registry import inside `ui/syntax/` (from S4).

## 5. Acceptance criteria

- **A1** [T] Cold start parses no grammar. The first Kotlin file parses Kotlin only; an HTML file
  parses HTML and its closure.
- **A2** [T] A grammar that throws while loading leaves every other language coloured, and its own
  files open plain with a note.
- **A3** [T] A palette change produces no tokenizing call for read-only views.
- **A4** [T] The roles of one line are the same from the editor's analyzer and from `tokens()`.
- **A5** [T] A 1 MB single-line file opens, edits and scrolls with colouring stopped at the limit.
  It takes no more time per line than the limit allows.
- **A6** [T] No file outside `ui/syntax/` imports tm4e or Sora's TextMate registries (guard).
- **A7** [T] Adding a language is one catalog entry, one asset folder and one `FileTypes` rule. The
  tests list anything else missing.
- **A8** [P] On the owner's phone: opening the first file after a cold start colours sooner than
  today. Scrolling a 10 000-line file stays smooth. A diff of a Kotlin file is coloured as the editor
  colours it.

## 6. Questions for the owner

1. **Engine (D1).** Stay on TextMate and fix its architecture, with semantic tokens later as a
   layer. Do not migrate to tree-sitter. *Recommended: yes.*
2. **Semantic tokens (S7).** In this plan as the last, optional cycle, or left for later?
   *Recommended: in the plan, last.*
3. **Coverage (S6).** Which languages matter most to you? *Recommended: TOML, Vue, Svelte, GraphQL,
   Protobuf, HCL, CMake and PowerShell first.*
4. **Large files (D7).** Above the limits, colour nothing and say so, or colour only the part on
   screen? *Recommended: colour nothing and say so; it is simpler and cannot stall.*
5. **Colouring outside the editor (S5).** Diff view, change sheet, conflict resolution and assistant
   previews all at once? *Recommended: yes.*

## 7. Record

- Nothing built yet.
