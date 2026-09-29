# Editor colours for every file type — plan

Status: **planned** (2026-09-29); scope decided by the owner (§5). Owner's request:
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
