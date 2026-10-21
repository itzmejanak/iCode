# Markdown Rendering: a Native Renderer from the Parser Up

Owner's request (2026-10-21), with screenshots of a `.md` file in the editor's preview and of an
assistant answer: math shows as raw `$$ \frac{1}{20} … $$`. Find the limits of how Markdown is
rendered, research the best native renderer, and plan a fix from the root for every kind of content,
ready to implement.

## 1. What stands (verified)

One renderer serves two places: the editor's **preview** (`MarkdownPreview`) and the assistant's
**answers** (`MarkdownText`), both in `ui/editor/MarkdownPreview.kt`.

| Part | What it is |
|---|---|
| Library | Markwon 4.6.2 (`core`, `ext-tables`, `ext-strikethrough`, `ext-tasklist`), last released **2021-02-08** (Maven Central). It parses with its bundled commonmark-java |
| View | One Android `TextView` per document (`ScalableMarkdownTextView`), in Compose through `AndroidView`, holding one `Spanned` |
| Tables | Markwon's `TableRowSpan`, laid out by reflection on its private fields before the text is set (the streaming fix of T1), kept from R8 by a rule in `proguard-rules.pro` |
| Streaming | Every chunk re-renders the whole answer: parse, spans, `setText` |

### 1.1 Its limits

| Limit | Seen | Root cause |
|---|---|---|
| **Math is raw text** | Both screenshots: `$$ \text{A's rate} = \frac{1}{20} … $$`, `$1$`, `$\dfrac{78}{35}$` in a heading | No math plugin. Markwon's own (`ext-latex`) reads `$$ … $$` only, not `$ … $` |
| **LaTeX is damaged before any math could see it** | `\;` shows as `;` | Markdown's backslash escapes run first: `\;` becomes `;`, and `_ … _` inside math can turn into italics. Math has to be recognised by the parser itself, not added afterwards |
| Not the app's type | The chat's answers are in Android's default font, beside Chakra Petch everywhere else | A `TextView` with no typeface set; colours set by hand from the palette |
| Code blocks uncoloured | Fenced code is plain monospace | No highlighting. The editor's TextMate grammars are not used |
| Tables squeeze | A wide table wraps every cell; it never scrolls sideways | `TableRowSpan` is a span inside one text layout |
| Fragile tables | A reflection workaround and an R8 keep rule | Markwon lays a row out at width 0 first, then fixes it one frame late |
| Bare URLs are not links | `https://…` in text is plain | Markwon's `linkify` module is not included |
| Relative links and anchors do nothing | `[guide](docs/guide.md)`, `[top](#setup)` | Markwon hands every link to Android `ACTION_VIEW`; a path or `#anchor` has no app |
| No images | `![](diagram.png)` is dropped | No image plugin |
| No HTML | `<br>`, `<details>`, `<p align="center">` are not shown as HTML (Markwon core has no HTML plugin) | No HTML plugin |
| No GitHub alerts, footnotes, front matter, ```` ```math ```` | `> [!NOTE]`, `[^1]`, a file's leading `---` block | Not in Markwon's plugins used |
| Streaming cost grows with length | Each chunk re-renders everything | One `Spanned` for the whole answer |

## 2. Research

### 2.1 Parsers (checked on Maven Central and in their jars)

| Parser | Latest | Math | Verdict |
|---|---|---|---|
| **JetBrains markdown** (`org.jetbrains:markdown`, the IntelliJ Markdown parser, pure Kotlin, depends only on `kotlin-stdlib` 2.0.0) | 0.7.17 (2026-10-13), 0.7.18 (2026-10-15) | **Yes**, in its GFM flavour: `INLINE_MATH`, `BLOCK_MATH` (read from the jar) | **Chosen** |
| commonmark-java (`org.commonmark`) | 0.30.1 (2026-10-10) | No official extension | Active, but math would be ours to write |
| Markwon's commonmark (inside Markwon 4.6.2) | 2021 | Only through `ext-latex`, `$$` only | Replaced |

The JetBrains parser was run here on a reconstruction of the screenshot's file (0.7.9, then 0.7.17):

| Input | Parsed as |
|---|---|
| `$1$`, `$W_A + W_B$`, `$\dfrac{78}{35}$` in a heading, `$x^2$` in a table cell | Inline math. The underscores stay math, not italics |
| `$$ … $$` on one line, and `$$` on lines of their own | Block math, the LaTeX untouched (`\;`, `\ `, `\left(` kept) |
| `a price of $5 and $6` | Text (0.7.17; 0.7.9 got it wrong) |
| `` `$x$` ``, a fenced block with `$$` in it, `\$5` | Not math |
| `> [!NOTE]`, `- [x]`, a bare `https://…`, `<https://…>` | Alert, task item, autolinks |
| `\( … \)`, `\[ … \]` | **Not math** (plain text, the backslashes eaten) |
| A file's leading `---` front matter | **A rule and a paragraph** |
| `[^1]` and `[^1]: …` | **Reference links**, not footnotes |
| ```` ```math ```` | A code fence (its language `math`) |
| An unclosed `$$ \frac{1}{2` (mid-stream) | Text, until it closes |

Speed: a 128 KB document parses in about 118 ms on this machine (20 runs, warmed). A phone is
slower, so a long answer must not be parsed whole for every streamed chunk (§3.6).

### 2.2 Renderers

| Option | Verdict |
|---|---|
| Keep Markwon, add plugins | No: unmaintained since 2021, math is `$$`-only, one `TextView` keeps every layout limit above |
| [multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer) (Compose, on the JetBrains parser, 0.46.0, 2026-10-13) | Not adopted. Its 0.46.0 needs Kotlin stdlib 2.4.21 and Compose foundation 1.12.1 (iCode: Kotlin 2.3.10, Compose BOM 2026.04.01); its README says nothing of math; it brings its own typography and code highlighter, beside iCode's design system and TextMate |
| **iCode's own Compose renderer on the JetBrains parser** | **Chosen**: math from the parser, the design system's type and colours, the editor's TextMate colours for code, iCode's own image decoder and link rules, no new framework |

### 2.3 Math engines (native only: the architecture rules out WebView rendering, §4 and §25)

| Engine | Licence | Coverage (checked) | Cost |
|---|---|---|---|
| [JLatexMath-android](https://github.com/noties/jlatexmath-android) 0.2.0 (2020-04-30), a port of [JLaTeXMath](https://github.com/opencollab/jlatexmath) | **GPL-2.0** as published (upstream JLaTeXMath has a Classpath Exception; the port's licence file does not carry it) | **Every command in the screenshot's file** is in its tables: `\text`, `\frac`, `\dfrac`, `\quad`, `\qquad`, `\times`, `\left`, `\right`, `\boxed`, `\approx`, `\checkmark`, and also `aligned`, `cases`, `pmatrix`, `\mathbb`, `\operatorname`, `\binom`, `\color` | Pure Java, 650 KB with its fonts; draws on an Android `Canvas` |
| [RaTeX](https://github.com/erweixin/RaTeX) through [RaTeX-CMP](https://github.com/darriousliu/RaTeX-CMP) | MIT | KaTeX's syntax, the syntax models write; coverage not checked here | `ratex-android` 0.1.15 (2026-10-16; first release 2026-04-19): native Rust `libratex_ffi.so`, 3.2 MB for arm64. Needs **Kotlin 2.4.20, Compose 1.12.2** and pulls Material 3 **1.10.0-alpha05** (it uses only `LocalContentColor` from it) |
| [AndroidMath](https://github.com/gregcockroft/AndroidMath) v1.1.0 | MIT | Its own source has **no `\dfrac`, `\tfrac`, `\boxed`, `\checkmark`, `\operatorname`**: the screenshot's file would fail | JitPack only (refused by this environment's proxy) |

The licence decides between the first two. The project records that iCode has no LICENSE (all
rights reserved) and that GPL code cannot be embedded in it (`storage-explorer-plan.md` §2). See
**Q1** (§6).

## 3. Decisions

### 3.1 One model, two hosts

- **Parse**: JetBrains markdown 0.7.17, GFM flavour.
- **Model**: pure Kotlin in `domain/markdown`, so it is tested on the JVM with the rest of the domain.
  - Blocks: heading (level, slug), paragraph, bullet/ordered/task list, quote, alert (note, tip,
    important, warning, caution), code (language), display math, table (alignments), rule, image,
    HTML block, details (summary, blocks), front matter, footnotes.
  - Inlines: text, emphasis, strong, strike, code, link, image, math, line break, footnote reference,
    HTML inline.
- **Render**: Compose in `ui/markdown`, one renderer for both hosts:
  - the **preview**: a lazy list of blocks, so a long file composes only what shows;
  - the **answers**: a column per answer, inside the chat's list.
- Markwon goes, with `ScalableMarkdownTextView`, the reflection on `TableRowSpan` and its R8 rule.

### 3.2 Math

- **Delimiters**:
  - files read as GitHub reads them: `$…$`, `$$…$$`, and ```` ```math ````;
  - the assistant's answers also accept `\(…\)` and `\[…\]`, which models write. They are turned
    into `$`/`$$` before parsing, outside code spans and fences only.
  - The assistant's instructions gain one line: write math as `$…$` inline and `$$…$$` on lines of
    its own.
- **Layout**:
  - Display math is centred, in a row that scrolls sideways when wider than the screen.
  - Inline math is laid out in the text through Compose's inline content, sized from the formula,
    centred on the line (`PlaceholderVerticalAlign.TextCenter`).
  - Both take the text's colour and size, pinch zoom included.
- **Copy**: a selection that includes a formula copies its LaTeX source (`$…$`), the formula's
  alternate text.
- **Errors**: LaTeX the engine refuses shows as its source in the mono face, amber. It never crashes.
- **Cache**: formulas are laid out once per (source, size, colour, display); unchanged blocks reuse them.
- **Engine**: behind one interface (`MathEngine`: lay out to width, height and baseline, then draw),
  so changing engines is one file. Which engine: **Q1**.

### 3.3 Code

- Fenced code is coloured exactly as the editor colours it, by its TextMate grammar and theme:
  - `IGrammar.tokenizeLine2`, whose foreground colour indices come from the theme `EditorTheme`
    already set on Sora's `GrammarRegistry`;
  - the fence's language is resolved through `FileTypes`, the one answer to what kind of file this is.
- An unknown language is plain mono.
- A block scrolls sideways (no wrapping), with its language and a **Copy** button on its header.
- Inline code: the mono face on the elevated surface.
- ```` ```mermaid ```` and other diagram languages: a code block titled with the language. No native
  diagram renderer exists to use.

### 3.4 Links and images

| Target | A tap |
|---|---|
| `http(s)://`, `mailto:` | Android's `ACTION_VIEW`, the user's browser or mail app (architecture §11.5) |
| A relative path, `./src/App.kt`, `docs/guide.md#L10` | Resolved against the file's folder (preview) or the chat's workspace (answers), then `ICodeIntent.OpenFile` (editor or image viewer), with a line number from `#L10` |
| `#anchor` | Scrolls to the heading with that slug (GitHub's rules) |
| Anything else (`javascript:`, `intent:`, `file:`) | Not followed: drawn as text |

| Image source | Preview (files) | Answers |
|---|---|---|
| A workspace path (relative or absolute) | Shown, decoded by the image viewer's `decodeImage`; a tap opens the viewer | The same |
| `data:image/…;base64` | Shown | Shown |
| `https://` | Loaded; a file is the user's own. (VS Code's default preview level, Strict, blocks `http` images and scripts) | **Not loaded**: alt text and host, tap to open in the browser. An answer can be steered to leak data through an image's URL; loading it unasked would send it |
| `http://` | Not loaded (alt text) | Not loaded |
| SVG | Not decodable by Android: alt text | Same |

### 3.5 Everything else

| Kind | Rendering |
|---|---|
| Headings | Chakra Petch bold on the type scale (H1 22, H2 20, H3 16, H4–H6 14 scaled), a rule under H1 and H2 as GitHub draws it, in the edge line's colour |
| Paragraph, lists | Body type; nested lists indented by the gutter; task items with read-only checkboxes |
| Quote | A 2 dp rule in the outline colour, text secondary |
| Alerts | A card with the tone's icon and colour (Note: accent, Tip: green, Important: blue, Warning: amber, Caution: red), the design system's tones |
| Tables | A grid that scrolls sideways; header bold; alignment from the separator row; cell lines follow Border Lines |
| Rule | 1 dp in the outline colour |
| HTML | A safe set drawn natively: `<br>`, `<b>`/`<strong>`, `<i>`/`<em>`, `<s>`/`<del>`, `<code>`, `<kbd>`, `<sub>`, `<sup>`, `<a href>`, `<img src alt width>`, `<details><summary>` (collapsible), `<p align>`/`<div align>` (centred), `<hr>`. Comments are hidden. Any other tag shows as its source, muted: nothing vanishes |
| Front matter (files only) | A collapsible "Front matter" code block (YAML) at the top |
| Footnotes | `[^1]` as a superscript link; the definitions gathered at the end under a rule, each with a link back |

### 3.6 Streaming and speed

- Parsing runs off the main thread.
- While an answer streams, only its last block is parsed again:
  - the blocks before it are kept, with their layouts and formulas;
  - each chunk costs the size of the last block, not of the whole answer;
  - once the turn ends, the whole answer is parsed once more, so references defined late resolve.
- The preview parses after edits stop for 150 ms, off the main thread.
- Blocks are stable values, so Compose skips every block that did not change.
- Tables measure themselves, so no row is ever one line tall for a frame.

### 3.7 Selection, zoom, theme, accessibility

- **Selection**: the answer is selectable across its blocks, inside one `SelectionContainer`. In the
  preview, selection spans the blocks on screen (a lazy list's limit).
- **Zoom**: the preview keeps its pinch and the editor's font size; the answers keep the chat's
  pinch (`LocalCodeTextScale`).
- **Theme**: type, colours, surfaces and lines from `IcodeType` and `IcodeColors`, so Dark, Light,
  AMOLED, the accent and Border Lines apply with nothing set by hand.
- **Accessibility**:
  - headings are headings to TalkBack;
  - a formula reads as its LaTeX source;
  - images read as their alt text;
  - checkboxes read as checked or not.

## 4. Where it goes

| Part | Files |
|---|---|
| Domain | `domain/markdown/MarkdownModel.kt` (blocks, inlines), `MarkdownParser.kt` (JetBrains → model; delimiters by source; front matter; footnotes; ```` ```math ````; HTML set), `MarkdownLinks.kt` (link and image targets, GitHub slugs) |
| UI | `ui/markdown/MarkdownContent.kt` (the two hosts, selection, zoom), `MarkdownBlocks.kt`, `MarkdownInlines.kt` (annotated strings, links, inline math), `MarkdownCode.kt` (TextMate colours), `MarkdownMath.kt` (`MathEngine`, cache), `MarkdownImages.kt` |
| Callers | `WorkspaceEditorScreen` (preview), `AssistantParts` (answers) |
| Removed | `MarkdownPreview.kt`, Markwon's four artifacts, the `TableRowSpan` R8 rule |
| Assistant | One line in `AssistantInstructions` (§3.2) |
| Build | `org.jetbrains:markdown:0.7.17`; the math engine of **Q1** |

## 5. Steps

1. **M1 Model**: the parser to the model, with the rules of §3. JVM tests on real input: the
   screenshot's file, each row of §2.1's table, links, slugs, and a streamed answer chunk by chunk
   against the same answer parsed whole.
2. **M2 Renderer**: blocks, inlines, theme, selection, zoom; TextMate code colouring.
3. **M3 Math**: the engine of Q1 behind `MathEngine`; inline and display; errors; cache.
4. **M4 Links and images** (§3.4).
5. **M5 Hosts**: the preview and the answers on the new renderer; streaming by the last block;
   Markwon removed.
6. **M6 HTML, front matter, footnotes, alerts** (§3.5).
7. **M7 Checks**:
   - the domain tests;
   - type-checks of every new screen file against Compose's desktop jars and the engine's classes;
   - the guards;
   - docs as built;
   - a phone checklist with the screenshot's file and a long streamed answer.

## 6. Owner's decision

**Q1. Which math engine.** The engines differ in licence, not in what they can draw for the
screenshot's file:

- **JLatexMath** draws everything checked, is pure Java and needs nothing else. It is GPL-2.0 as
  published, which the project's recorded position (no licence; no GPL code embedded) does not
  allow. It fits if iCode is to be published under a GPL-compatible licence.
- **RaTeX** is MIT, KaTeX-compatible and actively released, but young. It needs Kotlin 2.4.20 and
  Compose 1.12.2 first, or its MIT glue code vendored and compiled on today's toolchain with its
  native library.
