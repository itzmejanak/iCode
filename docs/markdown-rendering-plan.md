# Markdown Rendering: a Native Renderer from the Parser Up

Owner's request (2026-10-21), with screenshots of a `.md` file in the editor's preview and of an
assistant answer: math shows as raw `$$ \frac{1}{20} … $$`. Find the limits of how Markdown is
rendered, research the best native renderer, and plan a fix from the root for every kind of content,
ready to implement.

## 1. What stood (verified; replaced, §7)

One renderer served two places: the editor's **preview** (`MarkdownPreview`) and the assistant's
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
| [RaTeX](https://github.com/erweixin/RaTeX) through [RaTeX-CMP](https://github.com/darriousliu/RaTeX-CMP) | MIT (its KaTeX fonts: SIL OFL 1.1, §2.4) | KaTeX's syntax, the syntax models write; **every formula of the screenshot's file lays out and draws as KaTeX draws it** (§2.4) | `ratex-android` 0.1.15 (2026-10-16; first release 2026-04-19): native Rust `libratex_ffi.so`, 3.2 MB for arm64. As published it needs **Kotlin 2.4.20, Compose 1.12.2** and pulls Material 3 **1.10.0-alpha05** (it uses only `LocalContentColor` from it), so it is vendored instead (§3.2) |
| [AndroidMath](https://github.com/gregcockroft/AndroidMath) v1.1.0 | MIT | Its own source has **no `\dfrac`, `\tfrac`, `\boxed`, `\checkmark`, `\operatorname`**: the screenshot's file would fail | JitPack only (refused by this environment's proxy) |

The licence decided between the first two. The project records that iCode has no LICENSE (all
rights reserved) and that GPL code cannot be embedded in it (`storage-explorer-plan.md` §2). The
owner chose **RaTeX, vendored** (Q1, §6).

### 2.4 RaTeX, checked here (2026-10-21)

What ran: release 0.1.15's Rust core, the same source as the Android library, built for desktop
Linux (`ratex-native-linux-x86-64` 0.1.15). It was called through its C entry point
(`ratex_parse_and_layout`), the one its Android JNI functions call. Every download's SHA-1
matched Maven Central's.

**Your formulas.** Each one lays out in 0.03–2.7 ms:

- `\text{A's rate} = \frac{1}{20} \quad \text{(work per day)}`;
- `\dfrac{78}{35}`, inline;
- the `\boxed{… \approx 9.23 \ \text{days} \; \left(= 9\frac{8}{35} …\right)}` line;
- `W_A + W_B = … = 1 \quad \checkmark`;
- `2\left(\frac{16 + 20 + 15}{320}\right)`;
- `\qquad`, `\times`;
- `$1$`, `$W_A + W_B$`, `$x^2$` inline.

So do 17 constructs models commonly write:

- the quadratic formula, `\sum`, `\int`, `\lim`;
- `pmatrix`, `bmatrix`, `aligned`, `cases`, `array` with `\hline`;
- `\operatorname`, `\mathcal`, `\mathbb`, `\mathbf`, `\vec`;
- `\tfrac`, `\binom`, `\overline`, `\underbrace`, accents;
- Greek letters and relations;
- `\color`, `\textcolor`;
- sized delimiters, `\lVert`, `\langle`;
- `\displaystyle` with `\prod`, `\iint`, `\oint`;
- `\pmod`, `\gcd`, `\log_2`, `\max_i`;
- `\cancel`, `\xrightarrow`, `\overset`, `\underset`;
- accented and CJK text in `\text{}`.

**Drawn beside KaTeX 0.19.0.** Each display list was drawn with the KaTeX fonts and put next to KaTeX's
own rendering of the same source, in Chromium. They match, glyph for glyph and in placement. The one
difference seen: around `\underbrace{…}` RaTeX leaves out the thin spaces KaTeX puts on each side.

**Errors.** Malformed input is refused with a message and a position, worded as KaTeX words it:

- `\frac{1}{`: "Unexpected end of input in a macro argument";
- `\notacommand`: "Undefined control sequence";
- `\left( x`: "Expected '\right', got 'EOF'".

**Hostile input.** 34,797 inputs, each in display and inline mode, ran in a child process. A crash or
hang would have been caught. None happened (5 s in all). The inputs:

- every prefix and suffix of every formula above, and each with one character dropped, which is what
  a streamed answer holds mid-way;
- nesting up to 20,000 deep;
- `\def` loops and doubling macros;
- 200,000-character input and 2,000-row matrices;
- invalid UTF-8 (a lone surrogate). A null byte cannot pass through the C entry point; through JNI
  the core refuses it ("latex contains null byte", `jni.rs`);
- `\href{javascript:…}`, `\htmlStyle`, `\includegraphics`;
- 30,000 random strings of commands.

The core stops what would run away: "Recursion limit exceeded", "Too many expansions", "Layout
resource limit exceeded" (200,000 characters, in 196 ms).

**Cost grows with length.**

| Source | Time here | Display list (JSON) |
|---|---|---|
| 1,000 characters | 1.3–4.8 ms | 41–143 KB |
| 4,000 characters | 4.7–21 ms | 164–572 KB |
| 16,000 characters | 37–95 ms | 0.6–2.3 MB |
| 40,000 characters (`x+` repeated) | 830 ms | 5.9 MB |

Your longest formula has 102 characters. Sizes are legal but unbounded: `\rule{1000em}{1000em}`
lays out a square a thousand lines tall.

**The Android library** (`jni/arm64-v8a/libratex_ffi.so` in the AAR):

- 3,195,976 bytes; SHA-256 `1f89089504c96e0fc098e165f0419af14ec3fbd07956835c6b033b5ddfcde84a`;
- **16 KB page aligned**, which the build's `checkPageAlignment` requires of every library;
- built for API 21 with NDK r27d; links only `libc` and `libdl`;
- binds by JNI name to `io.ratex.RaTeXEngine`:
  - `nativeParseAndLayout(String, boolean, float[4] rgba)`, which returns the display list as JSON, or
    null with the message for `nativeGetLastError()`;
  - its Kotlin side loads it with `System.loadLibrary("ratex_ffi")`.
- The AAR's SHA-1 (`54476b7a…`) matches Maven Central's. Its `.asc` signature was not checked here.

**The display list.** It is what gets drawn:

- the formula's width, height and depth, in ems;
- glyphs (font id, code point, position, scale, colour);
- rules, rectangles, and paths (move, line, quad, cubic, close).

So font size applies at draw time: one layout serves every zoom level. The core emits 19 KaTeX font
ids, and three more for characters KaTeX lacks (`CJK-Regular`, `CJK-Fallback`, `Emoji-Fallback`). Its
Android painter draws those three in `Typeface.DEFAULT`, whose system fallback covers CJK and emoji.

**Fonts.** The AAR's 20 TTFs are byte for byte KaTeX 0.19.0's (npm, 2026-10-01). 19 are used
(`KaTeX_Caligraphic-Bold` is not), 501,296 bytes. Their licence is **not MIT**. RaTeX's
`THIRD_PARTY_NOTICES.txt` and each font's own metadata give it as:

- "Copyright (c) 2009-2010, Design Science, Inc.";
- "Copyright (c) 2014-2018 Khan Academy … with Reserved Font Name KaTeX_Main" (each face names its own);
- "licensed under the SIL Open Font License, Version 1.1".

The OFL lets the unmodified fonts be bundled with any software, closed included, if the copyright
notice and the licence go with them.

**Licences.**

- RaTeX core: MIT, "Copyright (c) erweixin", at the release's core commit `5f7dc48`.
- RaTeX-CMP: MIT, "Copyright (c) 2026 RaTeX-CMP Contributors", tag `v0.1.15` (`d7bf13a`).
- The crates in the Android build (`cargo tree --target aarch64-linux-android`) are all permissive:
  `serde_json`, `regex`, `fancy-regex`, `unicode-normalization`, `jni`, `memchr`, `aho-corasick` and
  the rest. Licences: MIT, Apache-2.0, Unlicense, Zlib, Unicode-3.0, mostly dual.
- iCode's About page has a "View Licenses" row that is "Not available yet" for every library. That gap
  is older than this plan and is not closed here. The notices travel in the APK (§3.2).

**Accepted risk.** The core is built with `panic = "abort"`, and its FFI catches nothing. A panic in the
parser or layout would end iCode's process, not just fail one formula.

- Against it: the 34,797 inputs above, a pinned release, and the same check on a phone (M3).
- Upgrading RaTeX means re-running all three (`third_party/ratex/README.md`).

## 3. Decisions

### 3.1 One model, two hosts

- **Parse**: JetBrains markdown 0.7.17, GFM flavour.
- **Model**: pure Kotlin in `domain/markdown`, so it is tested on the JVM with the rest of the domain.
  - Blocks: heading (level), paragraph, list (its first number, loose or tight, items with an
    optional check), quote, alert (note, tip, important, warning, caution), code (language), display
    math, table (alignments, header), rule, aligned (HTML `align`), details (summary, open, blocks),
    front matter, and source (HTML that is not drawn, shown as written).
  - Inlines: text, styled (emphasis, strong, strike, underline, subscript, superscript, mark), code
    (`<kbd>` a key), link, image (width), math, line break, footnote reference, source.
  - The document: its blocks, its footnotes numbered by first reference, and its anchors (a heading's
    slug to its block). Reference links are resolved as the document is assembled; an undefined one
    stays as written.
- **Render**: Compose in `ui/markdown`, one renderer for both hosts:
  - the **preview**: a lazy list of blocks, so a long file composes only what shows;
  - the **answers**: a column per answer, inside the chat's list.
- Markwon goes, with `ScalableMarkdownTextView`, the reflection on `TableRowSpan` and its R8 rule
  (gone, §7).

### 3.2 Math

- **Delimiters**:
  - files read as GitHub reads them: `$…$`, `$$…$$`, and ```` ```math ````;
  - the assistant's answers also accept `\(…\)` and `\[…\]`, which models write. They are turned
    into `$`/`$$` before parsing, outside code spans and fences only.
  - The assistant's instructions gain one line: write math as `$…$` inline and `$$…$$` on lines of
    its own.
- **Layout**:
  - Display math is centred, in a row that scrolls sideways when wider than the screen.
  - Inline math is laid out in the text through Compose's inline content, on the text's baseline:
    - Compose aligns a placeholder only by its edges or centre. So the placeholder is sized around
      the text's centre (`PlaceholderVerticalAlign.TextCenter`, the centre taken from the text font's
      metrics), reaching the formula's height above and its depth below.
    - The formula's baseline then falls on the line's baseline, as KaTeX puts it.
    - RaTeX-CMP's own example centres the formula's box instead, which leaves fractions a little off
      the baseline.
  - Both take the text's size, pinch zoom included, at draw time (§2.4).
- **Colour**: the text's colour, at draw time.
  - Formulas are laid out in black. What is drawn black takes the text's colour, so a theme change
    lays nothing out again.
  - `\color{…}` keeps its own colour.
- **Copy**: a selection that includes a formula copies its LaTeX source (`$…$`, `$$…$$`), the
  formula's alternate text, on one line (§7.1).
- **Errors**: LaTeX the engine refuses shows as its source in the mono face, amber, its message
  given to TalkBack.
- **Limits**: a formula over 4,000 characters, or one that lays out wider than 300 em or taller than
  100 em, shows as its source with "Too large to draw". This bounds the cost (§2.4: 4,000 characters
  cost at most 21 ms and 0.6 MB here).
- **Cache**: one layout per (source, display or inline), since size and colour apply at draw time.
  - The newest 256 are kept.
  - Unchanged blocks reuse them; a streamed answer lays out only its new formulas.
- **Engine: RaTeX 0.1.15, vendored** (owner, 2026-10-21):
  - **The library**: `libratex_ffi.so` for arm64-v8a, the one ABI iCode ships, copied unchanged from
    the AAR into `third_party/ratex/jniLibs/`. Its README records the release, commits, checksums
    and JNI contract (§2.4), and how to upgrade.
  - **The binding**: `io.ratex.RaTeXEngine`, the class the library binds to by name.
    - Copied from RaTeX-CMP's `RaTeXEngine.android.kt` with its MIT notice.
    - Its two `external` functions are kept exactly. Its Compose `Color`, coroutines and
      kotlinx-serialization go: it returns the JSON, or throws with the core's message.
    - Called off the main thread.
  - **The layout**: iCode's own model of the display list (`domain/markdown/MathLayout.kt`).
    - Read from the JSON with `org.json`, as 26 of iCode's files read JSON, so no serialization
      plugin is added.
    - Tested on the JVM against JSON captured from the same release on this machine.
  - **Drawing**: adapted from RaTeX-CMP's `RaTeXCanvas.kt` and Android glyph painter (MIT, credited).
    - Rules and rectangles with `drawRect`, paths with `drawPath`, glyphs with `Paint.drawText` in
      their KaTeX typeface.
    - The three fallback ids are drawn in `Typeface.DEFAULT`.
  - **Fonts**: the 19 used KaTeX TTFs in `assets/ratex/fonts/`, each loaded once with
    `Typeface.createFromAsset`. RaTeX-CMP copies each to a temporary file first; that step goes.
  - **Notices in the APK**, in `app/src/main/resources/licenses/` beside the OFL texts of Chakra
    Petch and IBM Plex Mono:
    - `OFL-KaTeX.txt`: the OFL text and the fonts' copyright notice;
    - `RaTeX.txt`: RaTeX's and RaTeX-CMP's MIT licences, then the crates linked into the library
      with their licences.

    The About page's "View Licenses" will list them when it exists (§2.4).
  - **Seam**: one function, from (source, display) to the engine's JSON. The cache and the layout are
    tested on the JVM through it, with no native code. No other engine stands behind an interface.

### 3.3 Code

- Fenced code is coloured exactly as the editor colours it, by its TextMate grammar:
  - `rememberSyntax(language, text)` in `ui/syntax/`, the read-only path every view of code shares
    since [syntax-highlighting-plan.md](./syntax-highlighting-plan.md) S4: role spans from the
    grammar's scopes, cached, coloured from the palette when drawn; a block over 200 000 characters
    stays plain;
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
| A relative path, `./src/App.kt`, `docs/guide.md#L10` | Resolved against the file's folder (preview) or the chat's workspace (answers), then `ICodeIntent.OpenFile` (editor or image viewer), with a line number from `#L10`. Never out of the workspace; a file that is not there says so in a notice |
| `#anchor` | Scrolls to the heading with that slug (GitHub's rules) |
| Anything else (`javascript:`, `intent:`, `file:`) | Not followed: drawn as text |

| Image source | Preview (files) | Answers |
|---|---|---|
| A workspace path (relative or absolute) | Shown, decoded by the image viewer's `decodeImage`; a tap opens the viewer | The same |
| `data:image/…;base64` | Shown | Shown |
| `https://` | Loaded (15 s, at most 10 MB); a file is the user's own. (VS Code's default preview level, Strict, blocks `http` images and scripts) | **Not loaded**: alt text and host, tap to open in the browser. An answer can be steered to leak data through an image's URL; loading it unasked would send it |
| `http://` | Not loaded (alt text) | Not loaded |
| SVG | Not decodable by Android: alt text | Same |

A paragraph of images alone shows them, each a tap to open (a linked image, its link). An image
inside a line of text is its alt text, a link to it: a picture's size is not known until it is
decoded, and a line's inline content needs it before layout.

### 3.5 Everything else

| Kind | Rendering |
|---|---|
| Headings | Chakra Petch bold on the type scale (H1 22, H2 20, H3 16, H4–H6 14 scaled), a rule under H1 and H2 as GitHub draws it, in the edge line's colour |
| Paragraph, lists | Body type; nested lists indented by the gutter; task items with read-only checkboxes |
| Quote | A 2 dp rule in the outline colour, text secondary |
| Alerts | A card with the tone's icon and colour (Note: accent, Tip: green, Important: blue, Warning: amber, Caution: red), the design system's tones |
| Tables | A grid that scrolls sideways; a column as wide as its widest cell, at most three quarters of the page; a row as tall as its tallest cell, the others centred in it; header bold on the elevated surface, every other row shaded; alignment from the separator row; cell lines follow Border Lines |
| Rule | 1 dp in the outline colour |
| HTML | A safe set drawn natively: `<br>`, `<b>`/`<strong>`, `<i>`/`<em>`, `<s>`/`<del>`, `<code>`, `<kbd>`, `<sub>`, `<sup>`, `<a href>`, `<img src alt width>`, `<details><summary>` (collapsible), `<p align>`/`<div align>` (centred), `<hr>`. Comments are hidden. Any other tag shows as its source, muted: nothing vanishes |
| Front matter (files only) | A collapsible "Front matter" code block (YAML) at the top |
| Footnotes | `[^1]` as a superscript link; the definitions gathered at the end under a rule, each with a link back |

### 3.6 Streaming and speed

- Parsing runs off the main thread.
- While an answer streams, only its unsettled tail is parsed again (JetBrains'
  `StreamingMarkdownFile`; the top-level blocks it reports stable are read into the model once):
  - the blocks before it are kept, with their layouts and formulas;
  - each chunk costs the size of the tail, not of the whole answer;
  - once the turn ends, the whole answer is parsed once more and kept, the same document a stream
    gives (§7).
- The preview parses after edits stop for 150 ms, off the main thread.
- A block that did not change is the same object in the new document, so Compose skips it.
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
| Domain | `domain/markdown/`: `MarkdownModel.kt` (the document, blocks, inlines), `MarkdownParser.kt` (a file read whole; `MarkdownStream`, an answer as it streams; front matter), `MarkdownBlockReader.kt` and `MarkdownInlineReader.kt` (JetBrains' tree → the model; footnotes; ```` ```math ````; reference definitions), `MarkdownHtml.kt` and `MarkdownHtmlBlocks.kt` (the HTML set), `MarkdownAssembly.kt` (open and close tags to blocks; references resolved; footnotes numbered; anchors), `MathDelimiters.kt` (an answer's `\(…\)` and `\[…\]`), `MarkdownLinks.kt` (where a document's links lead, link and image targets, GitHub slugs), `MathLayout.kt` (the display list, read from RaTeX's JSON; the limits; the cache); `FileTypes.ofLanguage` (a fence's language) |
| UI | `ui/markdown/`: `MarkdownContent.kt` (the two hosts, selection, zoom, where a tap goes), `MarkdownType.kt` (type and colours), `MarkdownBlocks.kt`, `MarkdownInlines.kt` (annotated strings, links, inline math), `MarkdownTable.kt`, `MarkdownCode.kt` (a code block), `MarkdownMath.kt` (display math, drawing a layout), `MathGlyphs.kt` (the KaTeX typefaces, a glyph on Android's canvas), `MarkdownImages.kt`; `ui/syntax/RememberSyntax.kt` (code coloured as the editor colours it); `ui/ImageViewer.decodeImage` (also from bytes) |
| Math engine | `app/src/main/java/io/ratex/RaTeXEngine.kt` (the JNI binding, §3.2); `third_party/ratex/` (`README.md`, `jniLibs/arm64-v8a/libratex_ffi.so`); `app/src/main/assets/ratex/fonts/KaTeX_*.ttf`; `app/src/main/resources/licenses/` (`OFL-KaTeX.txt`, `RaTeX.txt`) |
| Callers | `WorkspaceEditorScreen` (preview), `AssistantParts` and `AssistantChatView` (answers; the last one streams while its turn runs) |
| Removed | `ui/editor/MarkdownPreview.kt` and its instrumented test, Markwon's four artifacts, the `TableRowSpan` R8 rule |
| Assistant | One line in `AssistantInstructions` (§3.2); `WorkspaceLifecycleCoordinator.reveal` says when a linked file is not there |
| Build | `org.jetbrains:markdown:0.7.17`; `third_party/ratex/jniLibs` added to the main source set's `jniLibs`. No dependency for math |

## 5. Steps

1. **M1 Model**: the parser to the model, with the rules of §3. JVM tests on real input: the
   screenshot's file, each row of §2.1's table, links, slugs, and a streamed answer chunk by chunk
   against the same answer parsed whole.
2. **M2 Renderer**: blocks, inlines, theme, selection, zoom; TextMate code colouring.
3. **M3 Math**: RaTeX vendored (§3.2):
   - the library and its README, the binding, the fonts and notices;
   - the layout model and its JVM tests on JSON captured here, your formulas among them;
   - inline and display math; errors; limits; the cache;
   - on a phone, an instrumented test running your formulas and the hostile inputs of §2.4 through
     the real library;
   - the release build's dex still names `io.ratex.RaTeXEngine.nativeParseAndLayout`. R8's default
     rules keep native methods' names, as `NativeProcess` already relies on, but AGP's rules file
     could not be read from this environment (its host is blocked), so the build proves it.
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

**Q1. Which math engine.** Answered 2026-10-21: **RaTeX, vendored**.

- The choice was between JLatexMath (GPL-2.0 as published, which iCode's recorded position does not
  allow) and RaTeX (MIT, KaTeX's syntax, young).
- RaTeX was then run here on the screenshot's formulas before anything was built on it (§2.4).
- It is vendored as §3.2 sets out, rather than upgrading Kotlin and Compose to take the published
  artifact.

## 7. As built (2026-10-21)

M1–M7 are built as §3 and §4 describe; Markwon is gone. Checked in this environment:

| Check | Result |
|---|---|
| Unit tests | The Markdown tests (parser, stream, delimiters, links, math layout, `FileTypes.ofLanguage`) pass, with all of `data/` and `domain/`: 867 of 868. The other one, `TreeSizeTest`'s unreadable folder, fails only when run as root, which can list it anyway |
| Stream against whole read | 70 Markdown files (the repository's and §2's research files, 1.1 MB), each fed in 3 random chunkings, come out as the same document as when read whole. 20,000 random strings of Markdown punctuation: nothing thrown |
| Type-checks | Every new file, with the editor and assistant screens hosting it, against Compose's desktop jars, Sora's and tm4e's classes and Android's; `data/` and `domain/` against Android's; the instrumented `MarkdownMathTest` against Android's and JUnit's |
| API 30 | Animal Sniffer over the new classes and markdown-jvm: nothing above API 30. As a control, it does flag `readNBytes` (API 33) |
| Guards | UI and backend consistency checks pass |

Decided while building:

- **References resolve as the document is assembled.** A block that did not change keeps its object,
  so Compose skips it while an answer streams.
- **A pinch reaches tables.** A cell's text takes its size when it is drawn, so tables follow a
  pinch like everything else. That replaces the redraw in
  [assistant-koog-plan.md](./assistant-koog-plan.md) §16.
- **A quote's rule is drawn behind its blocks**, not as a row of intrinsic height. An intrinsic
  measurement would reach the `BoxWithConstraints` of an image or table inside, which cannot
  answer it.
- **Footnotes** are numbered by first reference. Their definitions come after the last block, each
  with ↩ back to where it was first referred to.
- **A link to a file that is not there** (moved since it was written) gives a notice, "`name` is not
  there", instead of an Explorer selecting nothing.

Left for a phone, since this environment has neither a device nor Android's build tools:

1. `MarkdownMathTest`, instrumented: the owner's formulas through the real library, refused LaTeX
   and its reasons, the hostile inputs of §2.4, and every KaTeX font loading from the APK.
2. The release build's dex still names `io.ratex.RaTeXEngine.nativeParseAndLayout` (§5, M3).
3. A walk-through:
   - the screenshot's file in the preview: inline and display math, `$\dfrac{78}{35}$` in a heading;
   - a long answer as it streams: no jumps, each formula laid out once;
   - a pinch in both places;
   - Dark, Light and AMOLED, with Border Lines on and off (tables);
   - TalkBack on a heading, a formula and a checkbox;
   - taps on a web link, a relative file, `#L10`, an anchor, and a footnote and back;
   - an answer's `https` image: its alt text and host, and a tap opens the browser.

### 7.1 Found on the phone (2026-10-22, the owner)

Screenshots of the owner's time-and-wage file, in the preview and as an answer:

- **Display math was blank**: a tall empty space where each `$$…$$` formula should be. Inline
  math and the table's formulas were drawn.
  - **Cause**: a formula is a placeholder in a line of text, and its alternate text was
    `$$⏎latex⏎$$`. Those line breaks put the placeholder's text on several lines. Compose counts a
    placeholder whose text runs past its line's end as cut off (`AndroidParagraph.placeholderRects`,
    `end > layout.getLineEnd(line)`) and places nothing there, while the lines keep their height.
    Inline LaTeX that runs over lines in its paragraph had the same flaw.
  - **Fix**: `formulaText` writes every formula's text on one line, `$…$` or `$$…$$`.
  - **Test**: `MarkdownMathDrawnTest` (instrumented) checks that a display formula, and an inline
    one running over lines, are on screen.
- **Table cells sat at the top of their rows**: "A" and "Total" sat beside fractions twice their
  height. Cells are now centred vertically in their row, as a browser centres them.

