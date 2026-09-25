# Editor grammar provenance

The TextMate grammars are bundled in the APK so syntax coloring works without
network access or a language server. The registry is
`app/src/main/assets/textmate/languages.json`.

- The Java, Kotlin, Python, XML, HTML, JavaScript and Markdown grammar/config
  files and `darcula.json` theme are copied unchanged from
  [Sora Editor 0.24.6](https://github.com/Rosemoe/sora-editor/tree/0.24.6/app/src/main/assets/textmate).
  Sora Editor is LGPL-2.1-or-later; the individual grammar files may retain
  upstream notices and need licence review before release.
- JSON, TypeScript, CSS, shell, Rust, Go and YAML grammars are copied unchanged
  from [VS Code commit 307b60e7](https://github.com/microsoft/vscode/tree/307b60e7aba9fe6c2dec8cae5970561b126f807b/extensions)
  (MIT); some grammar files identify their own upstream source in comments.
- The JSX and TSX grammars are from the same pinned VS Code commit and retain
  their upstream comments. They are registered separately so the demo `App.tsx`
  and real React files receive their proper scopes.

Unknown text extensions fall back to a readable uncolored editor rather than
guessing a grammar. Binary/malformed files remain rejected by `DocumentCodec`.
