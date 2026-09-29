# Editor grammar provenance

The TextMate grammars are bundled in the APK so syntax coloring works without
network access or a language server. The registry is
`app/src/main/assets/textmate/languages.json`.

- The Java, Kotlin, Python, XML, HTML, JavaScript and Markdown grammar/config
  files are copied unchanged from
  [Sora Editor 0.24.6](https://github.com/Rosemoe/sora-editor/tree/0.24.6/app/src/main/assets/textmate).
  Sora Editor is LGPL-2.1-or-later; the individual grammar files may retain
  upstream notices and need licence review before release.
- JSON, TypeScript, CSS, shell, Rust, Go and YAML grammars are copied unchanged
  from [VS Code commit 307b60e7](https://github.com/microsoft/vscode/tree/307b60e7aba9fe6c2dec8cae5970561b126f807b/extensions)
  (MIT); some grammar files identify their own upstream source in comments.
- The JSX and TSX grammars are from the same pinned VS Code commit and retain
  their upstream comments. They are registered separately so the capture fixture `App.tsx`
  and real React files receive their proper scopes.

Unknown text extensions fall back to a readable uncolored editor rather than
guessing a grammar. Binary/malformed files remain rejected by `DocumentCodec`.

## Colours (2026-09-29)

No theme file is shipped. The editor builds its TextMate theme for each palette at run time
(`ui/editor/EditorTheme.kt`) from one scope table (`ui/editor/SyntaxScopes.kt`) and the
palette's syntax colours (`designsystem/SyntaxColors.kt`, values in `IcodeColors.kt`). The
table's selectors are VS Code's default Dark+ rules — `extensions/theme-defaults/themes/`
`dark_vs.json` and `dark_plus.json` at the same pinned commit 307b60e7 (MIT) — each assigned to
an iCode role, followed by iCode's own additions for scopes those rules leave uncoloured. The
Sora sample theme `darcula.json` and the two preview themes are removed.

## Grammars added 2026-09-29 (editor-colors-plan E4)

Copied unchanged from VS Code at the same pinned commit 307b60e7 (the extension folder named),
each with its `language-configuration.json`. Their file names, extensions, name patterns and `#!`
programs in `domain/FileTypes.kt` are the extension's own `contributes.languages`. Upstream
sources and licences are the extension's `cgmanifest.json`:

| Assets | VS Code | Upstream (licence) |
|---|---|---|
| `bat/` | `extensions/bat` | https://github.com/mmims/language-batchfile (MIT) |
| `cpp/` | `extensions/cpp` | https://github.com/jeff-hykin/better-cpp-syntax (MIT); https://github.com/jeff-hykin/better-c-syntax (MIT); https://github.com/textmate/c.tmbundle (TextMate Bundle License) |
| `csharp/` | `extensions/csharp` | https://github.com/dotnet/csharp-tmLanguage (MIT) |
| `dart/` | `extensions/dart` | https://github.com/dart-lang/dart-syntax-highlight (BSD) |
| `diff/` | `extensions/diff` | https://github.com/textmate/diff.tmbundle (TextMate Bundle License) |
| `docker/` | `extensions/docker` | https://github.com/moby/moby (Apache-2.0) |
| `dotenv/` | `extensions/dotenv` | https://github.com/dotenv-org/dotenv-vscode (MIT License) |
| `git-base/` | `extensions/git-base` | https://github.com/textmate/git.tmbundle (MIT); https://github.com/walles/git-commit-message-plus (MIT) |
| `groovy/` | `extensions/groovy` | https://github.com/textmate/groovy.tmbundle (TextMate Bundle License) |
| `ini/` | `extensions/ini` | https://github.com/textmate/ini.tmbundle (TextMate Bundle License) |
| `less/` | `extensions/less` | https://github.com/radium-v/Better-Less (MIT) |
| `lua/` | `extensions/lua` | https://github.com/sumneko/lua.tmbundle (TextMate Bundle License) |
| `make/` | `extensions/make` | https://github.com/fadeevab/make.tmbundle (TextMate Bundle License) |
| `php/` | `extensions/php` | https://github.com/KapitanOczywisty/language-php (MIT) |
| `ruby/` | `extensions/ruby` | https://github.com/Shopify/ruby-lsp (MIT License) |
| `scss/` | `extensions/scss` | https://github.com/atom/language-sass (MIT) |
| `sql/` | `extensions/sql` | https://github.com/microsoft/vscode-mssql (MIT) |
| `swift/` | `extensions/swift` | https://github.com/jtbandes/swift-tmlanguage (MIT); https://github.com/owensd/vscode-swift (MIT) |
| `yaml/` | `extensions/yaml` | https://github.com/RedCMD/YAML-Syntax-Highlighter (MIT) |

Not shipped: `cpp/syntaxes/platform.tmLanguage.json` (568 KB, included by no grammar) and CUDA.
Languages the shipped grammars can embed but iCode does not ship (assembly and GLSL in C++, HAML,
Slim and GraphQL in Ruby, Javadoc in Groovy, Blade in PHP, and a Markdown fence's many languages)
are listed, exactly, in `GrammarAssetsTest`; their text keeps its enclosing colour. Path-only
patterns (`**/.git/config`, `**/rebase-merge/done`) are not matched: they are inside `.git` or a
home configuration folder, which the Explorer never lists.

YAML: VS Code's current grammar (RedCMD's YAML 1.2, adopted by VS Code in 2024) is not shipped. On the
editor's engine it scopes `key: value` as one plain scalar and every `:` as
`invalid.illegal.multiline-key` (EditorColoursTest). `yaml/syntaxes/yaml.tmLanguage.json` is instead
VS Code's long-standing grammar from textmate/yaml.tmbundle (TextMate Bundle License), as VS Code
itself shipped it until 2024, taken from VS Code commit 337cacaa (its October 2024 revert to it).

One edit to a copied file: `make/language-configuration.json` writes `lineComment` as the string
`"#"`. VS Code's form, an object with `noIndent`, is newer than the parser in Sora's tm4e, which
rejects it (the configuration would be lost and an error logged). `GrammarAssetsTest` loads every
configuration through that parser.

## The first sixteen languages, aligned (2026-09-29, E4c)

Their names, extensions, name patterns and first-line expressions in `domain/FileTypes.kt` are
now also their VS Code extensions' own (`java`, `python`, `javascript`, `typescript-basics`,
`json`, `html`, `xml`, `css`, `markdown-basics`, `shellscript`, `rust`, `go`, `yaml` at
307b60e7); Kotlin keeps `kt`, `kts` (Sora's grammar; VS Code has none). JSON with comments
(`.jsonc`, `.eslintrc`, `tsconfig.json`) uses the shipped `source.json`, whose grammar already
colours comments. Added from the same commit: the language configurations JSON, TypeScript, CSS,
shell, Rust, Go and YAML lacked; XSL (`xml/syntaxes/xsl.tmLanguage.json`) and JSON Lines
(`json/syntaxes/JSONL.tmLanguage.json`), each with its configuration.

Second edit, withdrawn with those files: three notes inside YAML 1.x `beginCaptures`; the grammar
now shipped has none. `GrammarAssetsTest` still fails on any capture that is not a rule.

## The regex engine and one engine bug (2026-09-29)

- **Native Oniguruma** (`io.github.rosemoe:oniguruma-native`, Sora 0.24.6; Oniguruma is BSD-2): the
  engine the grammars are written for. Without it tm4e uses joni, which rejects 9 of the 9,649
  regular expressions in the shipped grammars (variable-length lookbehinds) and drops their rules:
  CSS selectors, TypeScript's `export default`, C#'s `await`, Ruby block parameters. The JVM tests
  still run joni; `EditorColoursDeviceTest` runs the same check on the phone.
- **Sora bug, back-references:** tm4e quotes a captured text for `Matcher.appendReplacement`, but
  Sora's `MatcherUtils.replaceAll` appends it literally, so an `end` that refers to a capture
  containing a regular-expression character becomes a different pattern: `(\1)` for `**` ends at
  once. Third edit to a copied file: `markdown/syntaxes/markdown.tmLanguage.json` splits `bold` and
  `italic` into an asterisk rule and an underscore rule (`bold_underscore`, `italic_underscore`)
  with literal ends, same behaviour as VS Code. Still affected until Sora is fixed: Rust `r#"…"#`
  and Swift `#"…"#` raw strings, Ruby `%w[…]`-style literals with a bracket or `|` delimiter,
  heredoc names containing `-` or `.`.

