# Syntax Highlighting: What Stands, What Is Missing, and the Architecture to Get There

Owner's request (2026-11-11): every file and folder type is now covered and the architecture is far
better, but syntax highlighting still has architectural gaps and limits. Understand what we have,
what we need, and whether a migration is needed, to get scalability, no duplication, no
inconsistency, reliability, stability, performance and speed. Make a proper plan.

§6 records the owner's answers; building follows the cycles in §4. S1 to S3 are done (§3.4, §3.5,
§3.7 and §7 record what each built).

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
| G8 | **Thread safety is not established.** `textMateRuns` tokenizes on `Dispatchers.Default` with the same `IGrammar` objects Sora's analyzer threads use. tm4e compiles rules lazily on first use. No test shows two threads tokenizing one grammar safely. | `TextMateCode.kt` | Reliability. S1 confirmed it: a grammar shared by two threads gives wrong scopes and throws (§3.7, S1 measurements) |
| G9 | **Coverage gaps, missing and partial.** *Missing:* no grammar for TOML (`Cargo.toml`, `pyproject.toml`), Vue, Svelte, Protobuf, Terraform/HCL, GraphQL, Haskell, Scala, R, Perl, PowerShell, CMake, Nix, Zig, Elixir, JSON5 or CSV, and many more of the text types the icon theme names (`file-icons/theme.json`: 200 language ids, 1391 extensions, 2156 file names). `FileTypes` has no rule for most of these, so they open as plain text. Another 34 scopes that shipped grammars embed are listed as not shipped (`GrammarAssetsTest.NOT_SHIPPED`). *Partial:* some shipped grammars leave large parts of a file plain. Dockerfile (`source.dockerfile`) colours only instruction keywords and quoted strings: `FROM` image names, `ARG`/`ENV` `KEY=value`, the shell commands after `RUN`, flags such as `--from=`, and `EXPOSE` port numbers stay plain. dotenv colours key, value, `export` and interpolation, but numbers and booleans in values stay plain string. No test notices text left without a role. | `FileTypes.kt`, assets, tests | Scalability; Inconsistency |
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
| Risk of migrating | None | High: every language redone, every test rewritten | None |

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

The catalog lists each language once: its `id` (VS Code's language id), `scope`, its grammar and
configuration assets, its embedded languages, the grammars it includes, and its origin (the source
it was copied from, the upstream commit and the licence).

- **Its form (decided in S2): one data file, `assets/textmate/catalog.json`, read by
  `domain/syntax/Languages.kt`.** The file has a `sources` map (Sora Editor 0.24.6, VS Code at
  307b60e7 and at 337cacaa, each with its URL and licence) and a `languages` array with one line
  per language. `Languages.parse` reads it and offers `all`, `byId` and `byScope`. A data file has
  no length limit, so 250 languages fit, while `Languages.kt` stays small and under the 300-line
  guard. A tool writes it as easily as a person does: S6's import script appends entries, as
  `tools/import-file-icons.py` writes `file-icons/theme.json`, which `domain/files/FileIconTheme.kt`
  parses the same way. Kotlin files split by family were the other choice: they would be checked
  by the compiler, but a 250-entry catalog would need a generator that splits its output to stay
  under the guard, and every entry would be a code change. The tests below give the data file the
  checks the compiler would have given.
- `FileTypes` rules name a language **id** (`FileType.language`); the scope is looked up in the
  catalog.
- `languages.json` is gone. The engine builds Sora's `DefaultGrammarDefinition` from each catalog
  entry (grammar, configuration, id as Sora's name, scope, embedded languages), the same definition
  Sora's own reader made. S2 passed them all to `GrammarRegistry.loadGrammars` at start; since S3,
  `SyntaxEngine` passes only the ones a language needs to `GrammarRegistry.loadGrammar` (§3.5).
  Sora 0.24.6 supports both through public API.
- The provenance doc's tables are the catalog's, row for row, checked by `LanguageCatalogTest`. The
  same test checks that each grammar's own `scopeName` is the catalog's scope, that `includes` is
  what the grammar file pulls in, that `upstream` is the commit in the grammar's `version` field,
  that embedded languages name catalog ids, and that every asset folder is used. `FileTypesTest`
  checks that every id a rule names is in the catalog.
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
  itself. S1 showed that threads cannot share a grammar, so read-only views get their own grammar
  instances, loaded once, and each editor session gets its own as well (§3.7, S1 measurements).
- **An LRU cache** keyed by (language, text hash), bounded by total characters (for example 2 MB),
  so a chat that scrolls past the same block, or a diff opened twice, is not tokenized again.

### 3.7 Limits (D7)

`SyntaxLimits`, one place, tested:

| Limit | Value | Over it | Why this value (S1, below) |
|---|---|---|---|
| Line length coloured | 10 000 characters | The rest of that line is plain; the next line starts fresh | Kept. One minified JS line of 10 000 characters takes 117 ms, about the time limit; at VS Code's 20 000 it takes 243 ms and would stop early anyway. JSON of that length takes 2 ms. The cut must come before tokenizing, since the time limit alone does not protect (see the 1 MiB line) |
| Time per line | 100 ms (from 2 s) | That line stays plain; colouring goes on with the next | Kept. The slowest ordinary line in 10 000 lines of Kotlin or JSON took 2.2 ms, so 100 ms leaves a wide margin. At 2 s a 1 MiB JS line blocks for 2.4 s and still colours nothing |
| File coloured | 2 MiB or 50 000 lines | Opens and edits as plain text, with a one-line note in the editor: "Colouring is off for files this large" | Kept. 50 000 lines of Kotlin (1.7 MB) take 1.6 s on the JVM, about 32 µs a line, so a file at either bound takes about 2 s on a desktop CPU and several seconds on a phone, in the background |
| Read-only block | 200 000 characters | Plain | Kept. About 200 ms of Kotlin or 40 ms of JSON on the one read-only thread |

The editor still opens files up to 5 MiB, as today. S1's numbers are from the JVM; S3 checks the
limits on the owner's phone (A8) and changes a value only if the phone disagrees.

#### S1 measurements

Measured by `SyntaxBenchmark` (`app/src/test/.../ui/editor/SyntaxBenchmark.kt`), which is skipped
in the normal test run. To run it:

```
./gradlew :app:testDebugUnitTest --tests 'dev.icode.mobile.ui.editor.SyntaxBenchmark' -Picode.benchmark=true
```

It prints its report and writes it to `app/build/reports/syntax-benchmark.txt`.

**These are JVM numbers with the joni regex engine,** on OpenJDK 17 in the build sandbox. The phone
runs the grammars on native Oniguruma (`oniguruma-native`), which exists only for Android, and no
device or emulator was available. They give the shape and the ratios; absolute times on the phone
will differ.

*Grammar load,* cold registry each time, median of 5. "Parse" is `loadGrammar` with its include
closure; "first line" is the first `tokenizeLine`, which compiles the root rules.

| Grammar | Parse | First line |
|---|---|---|
| `text.html.markdown` (embeds many) | 35 ms | 51 ms |
| `source.ruby` | 19 ms | 26 ms |
| `source.cpp` | 12 ms | 25 ms |
| `source.php`, `text.html.php` | 7 ms | 7 to 12 ms |
| `source.ts`, `source.js`, `text.html.basic` | 3 to 5 ms | 8 to 14 ms |
| `source.kotlin`, `source.java`, `source.python`, `source.go` | 1 to 3 ms | 1 to 2 ms |
| `source.json`, `source.dockerfile`, `source.dotenv`, `source.ini` | under 0.3 ms | under 0.3 ms |

All 42 loaded into one registry, as `TextMateAssets` did at start before S3, take 77 ms (the phone takes
about 1.4 s for that and more). Lazy loading (D4) is supported: the costliest single language is
Markdown at about 86 ms, and JSON costs under 1 ms.

*Large files,* line by line with the state carried, warm grammar, median of 3:

| File | Characters | Total | Per line | Slowest line |
|---|---|---|---|---|
| Kotlin, 10 000 lines | 338 060 | 324 ms (547 ms with a cold grammar) | 32 µs | 2.2 ms |
| JSON, 10 000 lines | 147 744 | 30 ms | 3 µs | 1.6 ms |
| Kotlin, 50 000 lines | 1 708 060 | 1 626 ms | 33 µs | 1.4 ms |

*One minified line of 1 MiB,* through `tokenizeLine2` with a time limit:

| Line | First 10 000 chars | First 100 000 | Whole line, 100 ms limit | Whole line, 2 s limit |
|---|---|---|---|---|
| JS | 117 ms | 1 392 ms | stopped early after 121 ms, nothing coloured | stopped early after 2 409 ms, nothing coloured |
| JSON | 2 ms | 20 ms | stopped early after 106 ms, coloured to char 461 005 | finished in 240 ms |

For JS, one scan of the whole 1 MiB line costs 118 ms, and the time limit is checked only between
scans, so a line overruns its limit by at least one scan, and a scan grows with the line. Cutting the line
at 10 000 characters first bounds a scan at about 1 ms. When JS stops early, `isStoppedEarly()` is
true and the line comes back as one plain token.

*Two threads, one grammar (G8).* `GrammarThreadsTest.race` releases two threads together, each
tokenizing its own document with the same grammar, and compares every line's scopes with a
one-thread run. Over 20 cold rounds of 2 threads by 10 documents (400 documents per grammar), in
two runs: Markdown had 6 and 31 documents wrong, with 8 and 13 threads throwing
`ConcurrentModificationException`; shell (heredocs) had 11 and 17 wrong; Kotlin had 2 and 0 wrong,
with one `ArrayIndexOutOfBoundsException`. A probe that warmed the grammar on one thread first still
got wrong scopes and exceptions in all three.

**Answer to G8: sharing one tm4e grammar across threads is not safe** on tm4e 0.24.6. A grammar
keeps unsynchronized state: its rule table is a `HashMap` filled lazily, each rule caches its
compiled patterns, an end pattern with back-references (heredocs, Markdown fences) is rewritten in
that cache each time it opens, and on the JVM each joni regex caches its last search. Only the last
of these is specific to the JVM, so the phone is affected too. One grammar instance per thread is
safe: `GrammarThreadsTest` checks that two threads, each with its own cold registry, always match
the one-thread run, in the normal test suite.

What this means for the build:

- **Read-only views get their own grammar instances** (D6, §3.6), loaded once and used only on the
  one read-only tokenizer thread.
- **Each editor session gets its own grammar instance too.** Sora runs one analyzer thread per
  editor, and today every open tab and every Markdown fence (tokenized on `Dispatchers.Default`,
  several at once) shares the one `IGrammar` per scope in `GrammarRegistry`. S3's engine hands each
  tokenizing thread its own instance; `TextMateLanguage` has a protected constructor that takes an
  `IGrammar`, and its `create` overloads take a `GrammarRegistry`. At the load times above, a second
  instance of a grammar costs a few milliseconds for most languages.
- **The editor's per-line time needs S3 to replace Sora's default.** Sora's `TextMateAnalyzer`
  passes a fixed `Duration.ofSeconds(2)` to `tokenizeLine2`, so the 100 ms limit and the 10 000
  character cut belong in iCode's own analyzer or a subclass.

#### What Sora's API allows (S3)

Sora 0.24.6 lets iCode set every limit without its own analyzer:

- **Time and length per line: yes, through the grammar.** `TextMateAnalyzer` cannot be given a time
  (its `tokenizeLine` calls `tokenizeLine2(line, state, Duration.ofSeconds(2))` itself), and
  `TextMateLanguage` builds its analyzer in a private method, so neither can be swapped. But the
  analyzer only calls `IGrammar.tokenizeLine2`, and `TextMateLanguage`'s protected constructor takes
  any `IGrammar`. `ui/syntax/LimitedGrammar` wraps the real grammar: it lowers any time asked for to
  100 ms, and tokenizes a line longer than 10 000 characters only up to there, the rest one plain
  token of the grammar's root scope. The next line goes on from the state the long line started in,
  as VS Code does, so a minified line cannot leave the lines after it inside a string. Read-only
  views use the same wrapper, so the limits are in one place for every caller.
- **File size and line count: decided before Sora is involved.** `EditorSyntax` asks
  `SyntaxLimits.colours(text)` before it asks for a grammar; over the limit the editor keeps Sora's
  plain language and says "Colouring is off for files this large".
- **A grammar per editor: yes.** `GrammarRegistry(null)` is a registry of its own over a new tm4e
  `Registry`, and `loadGrammar(definition)` fills it. Unlike `GrammarRegistry.getInstance()` it does
  not follow the current theme by itself, so each editor's language adds a `ThemeChangeListener`
  that calls its registry's `setTheme`, and removes it in `destroy()`, which Sora calls when the
  editor changes language or is released. tm4e's `Theme` caches its matches in a
  `ConcurrentHashMap`, so editors can share one theme model.

## 4. Cycles

| Cycle | What | Gaps | Exit gate |
|---|---|---|---|
| **S1: measure and pin** | JVM benchmark: grammar parse time per language, tokenize time for a 10 000-line file and a 1 MB minified line. Concurrency test: two threads tokenizing one grammar. Record the numbers here. | G7, G8 | Numbers recorded; the concurrency question answered |
| **S2: the catalog** | `domain/syntax/Languages`; `FileTypes` names language ids; registrations come from the catalog; provenance checked by test | G6 | All existing grammar tests pass on the catalog |
| **S3: the engine** | `SyntaxEngine` with lazy per-language loading and per-language failure; `SyntaxLimits`; the editor's `applyLanguage` through it; `TextMateAssets` gone | G5, G7, G11 | Cold start parses nothing; opening JSON parses only JSON; a broken grammar affects only its language (tests) |
| **S4: role spans and one read-only path** | `tokens()` and `rememberSyntax()`; LRU cache; Markdown fences moved over; `textMateRuns` and its internal imports gone | G1, G2, G3, G8 | No `org.eclipse.tm4e.core.internal` import (guard rule); a palette change re-tokenizes nothing (test) |
| **S5: code everywhere** | The diff view, change sheet, conflict resolution and assistant file previews coloured through `rememberSyntax` | G4 | The same file's lines get the same roles in the editor and in its diff (test on spans) |
| **S6: full coverage** | Every text file type colours, and colours fully (Q3, §4.1). | G9 | A9 and A10 green; every new scope placed in `SyntaxScopes`; include closure test green |
| **S7: semantic tokens** (optional) | LSP `textDocument/semanticTokens/full` and its delta form, laid over TextMate; token type → role table; setting | G10 | With pyright: parameters and properties coloured apart; without a server, unchanged |
| **S8: docs** | This record, `editor-plan.md`, `editor-colors-plan.md`, provenance, `layout.md` where the editor's note appears, CHANGELOG | None | None |

Each cycle is its own commit, with the full gate and both guards run before it. A new backend guard
rule, `tm4e-owner`, keeps every tm4e and Sora-registry import inside `ui/syntax/` (from S4).

**The catalog's size (S2).** Full coverage (S6) means about 250 languages. The guards limit every
Kotlin file in `domain/` and `ui/` to 300 lines, so the catalog cannot be one hand-written Kotlin
file of that size. S2 chose one data file, `assets/textmate/catalog.json`, read by
`domain/syntax/Languages.kt` (§3.4).

### 4.1 S6 in full

1. **Every text type is covered.** Each text type the icon theme names (`theme.json`:
   `languageIds`, `fileExtensions`, `fileNames`) maps to a `FileType` with a grammar, or to an
   exemption recorded with its reason. The only reasons are: the format is binary (the icon groups
   image, audio, video, zip, font, database, 3d and the like); no grammar exists anywhere; or the
   licence is still unknown after checking the upstream repository.
2. **Partial grammars are fixed at the root.** Dockerfile gets a richer grammar, or an
   iCode-maintained one, that colours `FROM` image names and stage names, `ARG`/`ENV` keys and
   values, flags such as `--from=` and `--platform=`, `EXPOSE` ports, and embeds `source.shell`
   for the shell form of `RUN`, `CMD` and `ENTRYPOINT`. dotenv gets numbers and booleans in values.
   Every other shipped grammar is audited the same way, with a sample file per language.
3. **The 34 `NOT_SHIPPED` scopes are shipped** wherever a grammar exists, so embedded languages
   (a code fence, a `<script>` block) colour too.
4. **Sources, in order:** VS Code at the pinned commit 307b60e7, then Shiki's
   `textmate-grammars-themes` at 8f1828ebccc57930cf0adde9b35ae929584fe191 (242 grammars, about
   7.3 MB raw). A language missing from both is looked for at its own maintained upstream.
5. **Licences.** iCode is GPL-3.0 (`LICENSE`), so grammars under MIT, Apache-2.0, BSD, ISC,
   MPL-2.0, the TextMate Bundle License and GPL-3.0 can all ship. A grammar whose licence the Shiki
   index gives as `?` or `NOASSERTION` ships only after its upstream repository's licence is
   checked and recorded; otherwise it is exempted with that reason.
6. **Provenance.** `editor-grammar-provenance.md` is generated from the catalog, or tested against
   it, so every shipped grammar has its source, commit and licence recorded once.
7. **Tests.** A coverage test walks `theme.json` and fails on any text type with neither a grammar
   nor a recorded exemption (A9). A sample test tokenizes one sample file per shipped language and
   reports any word-like text left without a role, except tokens a list names with a reason (A10).
8. **Size.** Lazy loading (S3) means the extra grammars cost APK size, not start time or memory.
   S6 records the APK size before and after.

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
- **A9** [T] Every text type in the icon theme (`theme.json` language ids, extensions and file
  names) has a grammar or a recorded exemption with its reason.
- **A10** [T] Each shipped language's sample leaves no word-like token without a role, except
  tokens a test lists with a reason. In a Dockerfile, the `FROM` image, `ARG`/`ENV` keys and
  values, the shell commands after `RUN`, flags and `EXPOSE` ports are all coloured.

## 6. Owner's answers

1. **Engine (D1).** Stay on TextMate and fix its architecture, with semantic tokens later as a
   layer. No tree-sitter. *As recommended.*
2. **Semantic tokens (S7).** In this plan, as the last, optional cycle. *As recommended.*
3. **Coverage (S6).** Not a short list: every text file type. Languages that colour only partly
   today, such as Dockerfile and `.env`, must colour fully, and no file type may be left out or left
   with gaps. *The owner's own answer, in place of the recommended short list.* §4.1 sets out what
   this means.
4. **Large files (D7).** Above the limits, colour nothing and show a short note. *As recommended.*
5. **Colouring outside the editor (S5).** Diff view, change sheet, conflict resolution and assistant
   previews, all at once. *As recommended.*

## 7. Record

- Plan written; nothing built yet.
- 2026-11-11: owner answered §6. Q1, Q2, Q4 and Q5 as recommended. Q3 widened to full coverage of
  every text file type, with partial grammars (Dockerfile, dotenv and any others the audit finds)
  fixed to colour fully. S6, G9 and §5 (A9, A10) updated to match.
- S1: measured on the JVM with joni and recorded in §3.7. All four limits kept, with reasons. G8
  answered: threads cannot share a tm4e grammar, so read-only views and each editor session get
  their own grammar instance (S3, S4). `GrammarThreadsTest` checks per-thread instances in the
  normal suite; `SyntaxBenchmark` runs only with `-Picode.benchmark=true`. The test helper
  `ShippedGrammars` now also gives tests a registry over the shipped grammars, so no test reads
  `languages.json` on its own.
- S2: the language catalog is `assets/textmate/catalog.json`, read by `domain/syntax/Languages.kt`
  (§3.4 gives the form and why). `FileTypes` rules name language ids, `languages.json` is gone and
  the editor registers its grammars from the catalog through Sora's `GrammarDefinition` API. The
  provenance doc's tables now list every grammar with its source, upstream commit and licence, and
  a test keeps them equal to the catalog. Behaviour is unchanged: the same files colour with the
  same grammars. The shell language's id is now VS Code's `shellscript`. The YAML row now names its
  real upstream, textmate/yaml.tmbundle, where the old table named RedCMD's grammar, which is not
  shipped.
- S3: `ui/syntax/SyntaxEngine` is now the one owner of Sora's TextMate registries. Nothing is parsed
  at app start; `TextMateAssets` and its `loadGrammars` of all 42 grammars are gone. A language's
  grammar is parsed with its include closure (from the catalog's `includes`, followed through each
  included language) on one loader thread, `syntax-grammars`, the first time a file or block needs
  it. Each editor gets a registry and grammar of its own, and the read-only thread (`syntax-tokens`,
  for Markdown fences until S4) has its own per language, so no two threads share a grammar (G8).
  The loading logic is `GrammarLoader`; a grammar that throws is its language's failure alone,
  logged once and not tried again, and a language that includes it still loads with those rules
  plain. Its files open plain with a note naming the language by its catalog id, such as "Colouring
  is off for kotlin: its grammar could not be loaded". `SyntaxLimits` holds the four limits of §3.7, unchanged; `LimitedGrammar` applies the
  line limits (above, "What Sora's API allows"), and files over 2 MiB or 50 000 lines open plain
  with "Colouring is off for files this large". `SyntaxTheme` builds and registers each palette's
  theme (moved from `EditorTheme.kt`); Markdown fences now resolve colours against their own
  palette's theme instead of the current one. Opening a file now shows it plain for the moment its
  grammar takes to parse (a few milliseconds for most languages on the JVM, Markdown about 86 ms),
  each time an editor opens, since editors no longer share a parsed grammar. Tests:
  `GrammarLoaderTest` (A1, A2), `SyntaxLimitsTest` and `LimitedGrammarTest` (A5). The limits are not
  yet checked on the owner's phone (A8).
