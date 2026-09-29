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

