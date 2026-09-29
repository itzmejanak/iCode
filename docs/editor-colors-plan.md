# Editor colours for every file type — plan

Status: **done** (2026-09-29): E1–E6 done, checked on the phone (§6, §7). Owner's request:
"make sure to [colour] all the file types for editors, nothing left over, and make sure colours
work on all theme types, 100% accurate, no guess and no assumptions; if the design is wrong and
hard to scale, change it — in a clean way, not just jumping into new code". Follows
[backend-split-rules.md](./backend-split-rules.md) and [ui-split-plan.md](./ui-split-plan.md).

## 1. Measured (2026-09-29)

| # | Finding | Evidence |
|---|---|---|
| C1 | The three editor themes are three hand-kept files with nothing in common. Dark is `darcula.json`, JetBrains colours copied from Sora's sample app (66 rules; orange `#CC8242` keywords, green `#6A8759` strings) — not iCode's palette. Light and AMOLED have **8 rules each** | `assets/textmate/*.json` |
| C2 | So on Light and AMOLED nothing colours Markdown headings, bold, italics, links or code, HTML/XML tags and attributes, JSON keys (they fall into "type" by accident), CSS selectors and properties, escapes, regular expressions, annotations — all coloured on Dark | same |
| C3 | `darcula.json` carries rules for languages the app does not ship (PHP, Perl, Dart) and selectors no grammar produces (`ts.cast.expr`, `class.instance.constructor`) | same; `languages.json` |
| C4 | Nothing checks that a token colour is readable on its theme's background or on the current-line highlight | no test |
| C5 | A file's type is decided in four places that disagree: `scopeForFile` (editor), `fileKindFor` (Explorer), `isMarkdownFile`, and two copies of icon + tint per kind (Explorer, Find results). `.jsonc` is JSON to the editor and "Other" to the Explorer, `.json5` the reverse; the Explorer calls C, C++, C#, PHP, Ruby and Swift "Code" but the editor has no grammar for them; `FileKind.TypeScript` is a leftover of the Figma sample | `EditorLanguages.kt`, `FileTreeProjector.kt:39-49`, `ExplorerItemActions.kt:164-176`, `WorkspaceFindResults.kt:252-262` |
| C6 | Only the extension is read: six bash scripts in `superpowers` (`hooks/session-start`, `…/task-start`, `task-done`, `task-brief`, `review-package`, `sdd-workspace`, each `#!/usr/bin/env bash`) open uncoloured | device, `head -1` |
| C7 | The engine allows a theme built in code (`IThemeSource.fromString(JSON, …)`, tm4e in Sora 0.24.6) and draws bold, italic and strikethrough (`TextStyle.makeStyle(fg, bg, bold, italic, strike)`) | `javap` of `language-textmate-0.24.6.aar` |
| C8 | File types in the owner's workspaces: `md` 124, `sh` 45, `ts`/`tsx`/`mts` 51, `json` 19, `js`/`mjs`/`cjs` 18, `txt` 9, `py` 6, `svg` 6, `yml`/`yaml` 3, `css`, `html`, `.gitignore` ×4, `.gitattributes`, `.cmd`, `.dot`, the 6 scripts above | `find` on the phone, `.git` and `node_modules` excluded |

## 2. Design (one owner per fact)

1. **One file-type registry** (`domain/FileTypes`): name → `FileType(id, label, grammar scope or none,
   family)`, by exact file name (`Dockerfile`, `.gitignore`), then extension, then — where the text is
   at hand — the `#!` line. The editor, the Explorer and Find results all ask it; `scopeForFile`,
   `isMarkdownFile`, `fileKindFor`'s language cases and the two icon/tint copies go.
2. **One syntax palette** (`designsystem`): semantic roles — comment, keyword, storage, string, escape,
   number, constant, type, function, parameter, property, tag, attribute, heading, emphasis, strong,
   link, inline code, quote, list marker, inserted, deleted, regex, annotation, invalid — with one
   colour per role for each of Dark, Light and AMOLED, taken from the app's own hues where they read.
3. **One scope table** (`ui/editor`): each role's TextMate selectors, by TextMate's naming convention,
   not by one grammar's quirks. **One builder** turns palette + syntax colours into the editor's theme
   at run time; the three JSON files are deleted. The accent keeps caret, selection and current line
   number, as today.
4. **Proof, not review** (JVM tests):
   - every role on every theme is at least 4.5 : 1 (WCAG AA text) on the editor background and on
     the current-line highlight;
   - every scope name in every shipped grammar is either coloured by a role or on one explicit
     "plain by design" list (structure such as `meta.*`, `punctuation.*`); a new grammar with an
     unplaced scope fails the test;
   - every grammar in `languages.json` is reached from the registry, and every registry grammar exists;
   - the registry's name, extension and `#!` cases.

## 3. Steps (each: gate, commit; device where it touches a screen)

| Step | What | Kind |
|---|---|---|
| E1 | `FileTypes` registry; editor, Explorer and Find results use it — the file-type answers unchanged except where C5 shows two answers (each difference named) | move + fix |
| E2 | `#!` detection for the editor (C6) | fix |
| E3 | Syntax palette + scope table + builder; the three theme files deleted; contrast and coverage tests | redesign |
| E4 | Grammars for the file types chosen in §5, from the pinned VS Code commit (MIT), provenance updated | add |
| E5 | Device: one sample file per language, on Dark, Light and AMOLED, in the scratch workspace | verify |

## 4. Not changed

The grammars themselves (copied unchanged, per their provenance); Markdown *preview* colours (a
separate renderer); the terminal's colours (already one palette, S6 / accent §7).

## 5. Owner decisions (2026-09-29)

- **Both:** full syntax colours inside the editor for every file type on all three themes, **and**
  a colour per file type for its icon in the Explorer and Find results — both from the one registry.
- **File types gaining a grammar — all four groups**, "make sure the design is scalable and no gaps":
  - found in the owner's work: `#!` scripts, `.gitignore` (and `.gitattributes`, read by the same
    ignore-style rules only if the grammar covers it — checked, not assumed), `.cmd`/`.bat`, Git
    commit messages;
  - Android project files: Gradle Groovy, `.properties`/`.ini`, SQL, diff/patch, Dockerfile,
    Makefile, `.env`;
  - what the Explorer already claims: C, C++ (the full C++ grammar and its macro grammar — the C
    grammar on C++ would leave `class`, `template`, `namespace` uncoloured, a gap), C#, PHP, Ruby,
    Swift;
  - web extras: SCSS, Less, Lua, Dart.
  Each comes unchanged from the pinned VS Code commit (MIT) with its language configuration where
  VS Code has one; the coverage test of §2.4 runs over all of them.

## 6. Record

- **E1** `06727a0` one file-type registry; **E2** `f1c567a` scripts known by their `#!` line;
  **E3** `4e39b05` themes built from one scope table and one set of syntax colours;
  **E4a** YAML; **E4b** `d190d10` 22 more file types; **E4c** `33e464c` the first sixteen aligned with
  VS Code; **E5a** `467a73f` one hue rule; **E5b** `1219d05` Seti's icon colours.
- **On the phone** (2026-09-29, a scratch workspace with one sample per type), found and fixed:
  - Makefile configuration unreadable by the editor's parser; YAML and Markdown uncoloured (a
    capture note); both fixed with tests.
  - The first file opened froze the screen ~1.4 s: `GrammarRegistry.loadGrammars` parsed all
    42 grammars on the main thread (measured). Fixed by **E6** (§7).
  - The engine test (EditorColours, run on the JVM and on the phone) found: plain-text rules never
    winning (tm4e's colour 0), CSS selectors and three other constructs lost to joni (native
    Oniguruma now), VS Code's new YAML grammar mis-tokenizing (long-standing grammar now),
    Markdown `*` emphasis broken by a Sora bug (grammar split), keys drawn three ways across data
    formats (all Attribute now), INI sections plain (Heading now).
  - The editor lost every palette colour when Sora attached its scheme, and the current line was
    the tab bar's colour (owner: "editor text goes into the tab bg colour"): `d3cecc8`.
  - **Incident:** two instrumented runs uninstalled the debug app on the owner's phone, deleting
    its runtime and data (AGP uninstalls after `connectedAndroidTest`). The app is reinstalled;
    `gradle.properties` now keeps it installed after such a run, and such runs need the owner's OK.

## 7. E6 — no freeze on the first file (done 2026-09-29)

Measured on the phone: the first file opened after start blocks the screen ~1.4 s —
`GrammarRegistry.loadGrammars` parses all 42 grammars on the main thread when the first editor
is made. Sora cannot load one grammar lazily: `findGrammar` returns only loaded grammars, and the
definitions of all must be registered for embedded languages to resolve. So:

- the grammars load once, **off the main thread**, starting as soon as the app knows its palette
  (the theme must be current before grammars load, which stays on the main thread: ~56 ms);
- a file opened before they are ready shows its text at once, uncoloured, and takes its grammar
  when they are — no screen freeze; a failure is said as today ("Syntax coloring is unavailable");
- the first use of a large grammar still colours a moment later (C++ ~2–3 s, measured): tm4e
  compiles its rules on Sora's analysis thread, which does not block the screen.

Check on the phone: time from tap to text and to colours, cold start, by screenshot timeline.

**Done.** `TextMateAssets` loads the grammars once on a background thread, started when the app
applies its palette; `CodeEditorSession` shows a file as plain text until they are ready, then gives
it its grammar (unless another file has taken the view meanwhile); a failure falls back to plain
text and says so, as before. Two faults found on the way, both fixed:

- **Tokens drawn in the default colour when the grammars arrived after the file.** Sora gives tm4e
  its theme only when the theme *changes* after the grammar registry exists; the old code happened
  to re-set the same theme on every editor. The registry is now made on the main thread and handed
  the current theme before loading starts; later changes reach it through Sora's listener.
- **The theme was built twice at start** — for the stand-in Dark palette used until the settings
  load, then for the owner's. The editor now takes the palette only once the settings have loaded.

On the phone (clean build, cold start):

| | Before | After |
|---|---|---|
| Main thread on the first file | ~1.4 s frozen (grammars) | no skipped frame (Choreographer) |
| `box.cpp`: tap → text | after the freeze | ≤ 0.5 s |
| `box.cpp`: tap → colours | 2–3 s | ~1.6 s |
| Theme build at start | on the first file, per palette | once, ~55–110 ms, after settings load |
| Grammars (background) | — | 1.2–2.2 s after start |

A file opened before the grammars are ready was checked with a build that delayed them by 5 s
(not committed): its text showed at once, uncoloured, and took its colours as soon as they
loaded.
