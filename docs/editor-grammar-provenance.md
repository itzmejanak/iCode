# Editor grammar provenance

The TextMate grammars are bundled in the APK so syntax coloring works without
network access or a language server. Every shipped grammar is one entry in the
language catalog, `app/src/main/assets/textmate/catalog.json`
(`domain/syntax/Languages.kt`, docs/syntax-highlighting-plan.md D3): its id,
scope, grammar and configuration files, what it embeds and includes, the source
it was copied from, the upstream commit the grammar file names as its own origin
(its `version` field), and its licence. The editor registers its grammars from the
catalog.

Unknown text extensions fall back to a readable uncolored editor rather than
guessing a grammar. Binary/malformed files remain rejected by `DocumentCodec`.

## Shipped grammars

The two tables below are the catalog's, row for row (`LanguageCatalogTest` fails on
any difference and prints the tables as they must read). Files are copied
unchanged except for the edits recorded below. Sora Editor is
LGPL-2.1-or-later; a grammar Sora ships keeps its upstream's licence where the
file names an upstream. The Kotlin grammar names none, so it is recorded under
Sora's licence. VS Code's sources and licences per extension are also in each
extension's `cgmanifest.json`.

| Source | Where | Licence |
|---|---|---|
| Sora Editor 0.24.6 | https://github.com/Rosemoe/sora-editor/tree/0.24.6/app/src/main/assets/textmate | LGPL-2.1-or-later |
| VS Code 307b60e7 | https://github.com/microsoft/vscode/tree/307b60e7aba9fe6c2dec8cae5970561b126f807b | MIT |
| VS Code 337cacaa | https://github.com/microsoft/vscode/tree/337cacaafa0248ef6db264f52971d283b40aa94d | MIT |

| Language | Scope | Copied from | Upstream | Licence |
|---|---|---|---|---|
| `java` | `source.java` | Sora Editor 0.24.6 `java` | [atom/language-java@29f977dc](https://github.com/atom/language-java/commit/29f977dc42a7e2568b39bb6fb34c4ef108eb59b3) | MIT |
| `kotlin` | `source.kotlin` | Sora Editor 0.24.6 `kotlin` | None: Sora Editor 0.24.6 is the origin | LGPL-2.1-or-later |
| `python` | `source.python` | Sora Editor 0.24.6 `python` | [MagicStack/MagicPython@b2b4f4ae](https://github.com/MagicStack/MagicPython/commit/b2b4f4ae7b4e6284e80bda8080106b93bd588f9e) | MIT |
| `xml` | `text.xml` | Sora Editor 0.24.6 `xml` | [atom/language-xml@7bc75dfe](https://github.com/atom/language-xml/commit/7bc75dfe779ad5b35d9bf4013d9181864358cb49) | MIT |
| `html` | `text.html.basic` | Sora Editor 0.24.6 `html` | [textmate/html.tmbundle@0c3d5ee5](https://github.com/textmate/html.tmbundle/commit/0c3d5ee54de3a993f747f54186b73a4d2d3c44a2) | TextMate Bundle License |
| `javascript` | `source.js` | Sora Editor 0.24.6 `javascript` | [microsoft/TypeScript-TmLanguage@4d30ff83](https://github.com/microsoft/TypeScript-TmLanguage/commit/4d30ff834ec324f56291addd197aa1e423cedfdd) | MIT |
| `javascriptreact` | `source.js.jsx` | VS Code 307b60e7 `extensions/javascript` | [microsoft/TypeScript-TmLanguage@48f60869](https://github.com/microsoft/TypeScript-TmLanguage/commit/48f608692aa6d6ad7bd65b478187906c798234a8) | MIT |
| `markdown` | `text.html.markdown` | Sora Editor 0.24.6 `markdown` | [microsoft/vscode-markdown-tm-grammar@09c3e715](https://github.com/microsoft/vscode-markdown-tm-grammar/commit/09c3e715102d08bba4c4ea828634474fc58b6f57) | MIT |
| `json` | `source.json` | VS Code 307b60e7 `extensions/json` | [microsoft/vscode-JSON.tmLanguage@9bd83f1c](https://github.com/microsoft/vscode-JSON.tmLanguage/commit/9bd83f1c252b375e957203f21793316203f61f70) | MIT |
| `typescript` | `source.ts` | VS Code 307b60e7 `extensions/typescript-basics` | [microsoft/TypeScript-TmLanguage@48f60869](https://github.com/microsoft/TypeScript-TmLanguage/commit/48f608692aa6d6ad7bd65b478187906c798234a8) | MIT |
| `typescriptreact` | `source.tsx` | VS Code 307b60e7 `extensions/typescript-basics` | [microsoft/TypeScript-TmLanguage@48f60869](https://github.com/microsoft/TypeScript-TmLanguage/commit/48f608692aa6d6ad7bd65b478187906c798234a8) | MIT |
| `css` | `source.css` | VS Code 307b60e7 `extensions/css` | [microsoft/vscode-css@de9e6bee](https://github.com/microsoft/vscode-css/commit/de9e6beee756760f31b15efbd782735fc25de3db) | MIT |
| `shellscript` | `source.shell` | VS Code 307b60e7 `extensions/shellscript` | [jeff-hykin/better-shell-syntax@35020b0b](https://github.com/jeff-hykin/better-shell-syntax/commit/35020b0bd79a90d3b262b4c13a8bb0b33adc1f45) | MIT |
| `rust` | `source.rust` | VS Code 307b60e7 `extensions/rust` | [dustypomerleau/rust-syntax@ca34cf38](https://github.com/dustypomerleau/rust-syntax/commit/ca34cf382a7b250144f2ccb99ff9af96912c79f2) | MIT |
| `go` | `source.go` | VS Code 307b60e7 `extensions/go` | [worlpaker/go-syntax@c74e22eb](https://github.com/worlpaker/go-syntax/commit/c74e22eb9ef32958e3edd130ea750ce78d8b8241) | MIT |
| `yaml` | `source.yaml` | VS Code 337cacaa `extensions/yaml` | [textmate/yaml.tmbundle@e54ceae3](https://github.com/textmate/yaml.tmbundle/commit/e54ceae3b719506dba7e481a77cea4a8b576ae46) | TextMate Bundle License |
| `bat` | `source.batchfile` | VS Code 307b60e7 `extensions/bat` | [mmims/language-batchfile@6154ae25](https://github.com/mmims/language-batchfile/commit/6154ae25a24e01ac9329e7bcf958e093cd8733a9) | MIT |
| `c` | `source.c` | VS Code 307b60e7 `extensions/cpp` | [jeff-hykin/better-c-syntax@34712a61](https://github.com/jeff-hykin/better-c-syntax/commit/34712a6106a4ffb0a04d2fa836fd28ff6c5849a4) | MIT |
| `cpp-embedded-macro` | `source.cpp.embedded.macro` | VS Code 307b60e7 `extensions/cpp` | [jeff-hykin/better-cpp-syntax@071dd6ec](https://github.com/jeff-hykin/better-cpp-syntax/commit/071dd6ecc9eda347bd84c8aa0e0b557396cb6a40) | MIT |
| `cpp` | `source.cpp` | VS Code 307b60e7 `extensions/cpp` | [jeff-hykin/better-cpp-syntax@071dd6ec](https://github.com/jeff-hykin/better-cpp-syntax/commit/071dd6ecc9eda347bd84c8aa0e0b557396cb6a40) | MIT |
| `csharp` | `source.cs` | VS Code 307b60e7 `extensions/csharp` | [dotnet/csharp-tmLanguage@c2719309](https://github.com/dotnet/csharp-tmLanguage/commit/c2719309e91d2e26be21fce0ecaaca86c7b48ce4) | MIT |
| `dart` | `source.dart` | VS Code 307b60e7 `extensions/dart` | [dart-lang/dart-syntax-highlight@b308b4b3](https://github.com/dart-lang/dart-syntax-highlight/commit/b308b4b3502c8639149e63d74844da51031b5f47) | BSD |
| `diff` | `source.diff` | VS Code 307b60e7 `extensions/diff` | [textmate/diff.tmbundle@0593bb77](https://github.com/textmate/diff.tmbundle/commit/0593bb775eab1824af97ef2172fd38822abd97d7) | TextMate Bundle License |
| `dockerfile` | `source.dockerfile` | VS Code 307b60e7 `extensions/docker` | [moby/moby@c2029cb2](https://github.com/moby/moby/commit/c2029cb2574647e4bc28ed58486b8e85883eedb9) | Apache-2.0 |
| `dotenv` | `source.dotenv` | VS Code 307b60e7 `extensions/dotenv` | [dotenv-org/dotenv-vscode@e7e41baa](https://github.com/dotenv-org/dotenv-vscode/commit/e7e41baa5b23e01c1ff0567a4e596c24860e7def) | MIT |
| `git-commit` | `text.git-commit` | VS Code 307b60e7 `extensions/git-base` | [walles/git-commit-message-plus@35a079de](https://github.com/walles/git-commit-message-plus/commit/35a079dea5a91b087021b40c01a6bb4eb0337a87) | MIT |
| `git-rebase` | `text.git-rebase` | VS Code 307b60e7 `extensions/git-base` | [textmate/git.tmbundle@5870cf3f](https://github.com/textmate/git.tmbundle/commit/5870cf3f8abad3a6637bdf69250b5d2ded427dc4) | MIT |
| `ignore` | `source.ignore` | VS Code 307b60e7 `extensions/git-base` | None: VS Code 307b60e7 is the origin | MIT |
| `groovy` | `source.groovy` | VS Code 307b60e7 `extensions/groovy` | [textmate/groovy.tmbundle@85d8f7c9](https://github.com/textmate/groovy.tmbundle/commit/85d8f7c97ae473ccb9473f6c8d27e4ec957f4be1) | TextMate Bundle License |
| `ini` | `source.ini` | VS Code 307b60e7 `extensions/ini` | [textmate/ini.tmbundle@2af0cbb0](https://github.com/textmate/ini.tmbundle/commit/2af0cbb0704940f967152616f2f1ff0aae6287a6) | TextMate Bundle License |
| `less` | `source.css.less` | VS Code 307b60e7 `extensions/less` | [radium-v/Better-Less@63c0cba9](https://github.com/radium-v/Better-Less/commit/63c0cba9792e49e255cce0f6dd03250fb30591e6) | MIT |
| `lua` | `source.lua` | VS Code 307b60e7 `extensions/lua` | [sumneko/lua.tmbundle@b295d83b](https://github.com/sumneko/lua.tmbundle/commit/b295d83bf0e91b5d3a69eb097f9ed351623b95be) | TextMate Bundle License |
| `makefile` | `source.makefile` | VS Code 307b60e7 `extensions/make` | [fadeevab/make.tmbundle@1d4c0b54](https://github.com/fadeevab/make.tmbundle/commit/1d4c0b541959995db098df751ffc129da39a294b) | TextMate Bundle License |
| `php-source` | `source.php` | VS Code 307b60e7 `extensions/php` | [KapitanOczywisty/language-php@d94fd6b7](https://github.com/KapitanOczywisty/language-php/commit/d94fd6b7dff1613d34bc0d36328f9982e65bd545) | MIT |
| `php` | `text.html.php` | VS Code 307b60e7 `extensions/php` | [KapitanOczywisty/language-php@ff64523c](https://github.com/KapitanOczywisty/language-php/commit/ff64523c94c014d68f5dec189b05557649c5872a) | MIT |
| `ruby` | `source.ruby` | VS Code 307b60e7 `extensions/ruby` | [Shopify/ruby-lsp@e044408a](https://github.com/Shopify/ruby-lsp/commit/e044408a069f43c892143f19c389628eebffbd01) | MIT |
| `scss` | `source.css.scss` | VS Code 307b60e7 `extensions/scss` | [atom/language-sass@f52ab12f](https://github.com/atom/language-sass/commit/f52ab12f7f9346cc2568129d8c4419bd3d506b47) | MIT |
| `sql` | `source.sql` | VS Code 307b60e7 `extensions/sql` | [microsoft/vscode-mssql@684648ee](https://github.com/microsoft/vscode-mssql/commit/684648ee5414eb2d9df22b0392ef05079f91e44e) | MIT |
| `swift` | `source.swift` | VS Code 307b60e7 `extensions/swift` | [jtbandes/swift-tmlanguage@2be007b6](https://github.com/jtbandes/swift-tmlanguage/commit/2be007b6bc88e54a0edfd3c313ff31276edc923d) | MIT |
| `sassdoc` | `source.sassdoc` | VS Code 307b60e7 `extensions/scss` | [atom/language-sass@303bbf0c](https://github.com/atom/language-sass/commit/303bbf0c250fe380b9e57375598cfd916110758b) | MIT |
| `xsl` | `text.xml.xsl` | VS Code 307b60e7 `extensions/xml` | [atom/language-xml@507de2ee](https://github.com/atom/language-xml/commit/507de2ee7daca60cf02e9e21fbeb92bbae73e280) | MIT |
| `jsonl` | `source.json.lines` | VS Code 307b60e7 `extensions/json` | [microsoft/vscode-JSON.tmLanguage@9bd83f1c](https://github.com/microsoft/vscode-JSON.tmLanguage/commit/9bd83f1c252b375e957203f21793316203f61f70) | MIT |

The JSX and TSX grammars are registered separately from JavaScript and
TypeScript so the capture fixture `App.tsx` and real React files receive their
proper scopes. A language's configuration file is from the same source as its
grammar, with two exceptions: JSX uses JavaScript's configuration (Sora), and
YAML's configuration is from VS Code 307b60e7 while its grammar is from 337cacaa
(see below).

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
programs in `domain/FileTypes.kt` are the extension's own `contributes.languages`. Their
sources, upstreams and licences are in the tables under "Shipped grammars".

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

