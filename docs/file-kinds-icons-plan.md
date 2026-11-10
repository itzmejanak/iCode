# File kinds and file icons — plan

Status: K1–K3 and I1–I4 done in code (2026-11-10); the phone checks in §6 are the owner's. Owner's request, with a phone screenshot of Files › Videos listing
`lib.dom.d.ts`, `index.d.ts`, `typescript.d.ts`, `thrir-client.ts`… under `<>` icons:
- ".ts is not video… the identification logic is wrong; don't just fix what I show, improve the
  logic from the root, with no bugs or gaps".
- "iCode lacks icons for all files and folder types… include everything without gaps, the
  implementation forked from the root architecture instead of a new code dump".

Follows [backend-split-rules.md](./backend-split-rules.md),
[explorer-gaps-plan.md](./explorer-gaps-plan.md) §3.10 (categories) and §3.12 (thumbnails), and
[icons-plan.md](./icons-plan.md) (Material Outlined for the app's own glyphs: unchanged).

## 1. Measured (code, 2026-11-10)

**Four tables say what a file is, and they disagree.**

| Table | Where | Keys on | Says `.ts` is |
|---|---|---|---|
| `FileTypes` | `domain/FileTypes.kt` | whole name, pattern, longest extension, first line | TypeScript |
| `FileCategories` | `domain/files/FileCategories.kt` | its own `VIDEOS`, `AUDIO`, `DOCUMENTS`, `APPS` sets, checked **before** `FileTypes` | a video |
| `ArchiveFormats` | `domain/files/ArchiveFormats.kt` | name endings | — |
| Android's `MimeTypeMap` | `WorkspacesProvider.mimeTypeOf`, Open with, Share, the Files provider | last extension | `video/mp2t` |

`FileCategories` feeds four places:
- the scan behind Manage storage and its views;
- `ThumbnailKind`, which then runs a video decoder on every TypeScript file;
- Properties ("Video · .ts");
- the moved-file update.

**Why the screenshot shows what it shows.**
- `.mts` is Code but `.ts` is Video, which is inconsistent.
- `.m2ts`, `.ogv`, `.aif`, `.mka`, `.jfif` and `.dng` are in no table.
- Nothing ever reads a file's bytes for its kind, even though `.ts` and `.mts` really are two
  formats: TypeScript, and the MPEG transport stream that TV recorders and AVCHD cameras write.
  Neither the name alone nor the extension lists can tell them apart.

**Icons.**
- `fileTypeIcon` has three icons, one per family: `<>` for all code, a page for data and
  documents, a picture for images.
- Every folder is the same folder.
- The icon is drawn by three different paths:
  - `itemIcon`, used by sheets and the Files explorer;
  - `FileTypes.of` plus `fileTypeIcon`, used by the Explorer tree, Quick Open, Source Control and
    Problems;
  - the category icons, used by Manage storage.
- The Explorer tree has its own folder branch. Quick Open classifies each row twice.
- Colours are Seti's, one per language, held 3 : 1 on every theme (`FileTypeIconsTest`).

**Defects found on the way.**
1. Thumbnails cache only successes. A file that cannot be thumbnailed (every `.ts` today, a
   corrupt picture, AVIF on Android 11) is decoded again each time its row comes back into view,
   holding one of the four decode permits while it runs.
2. Files › a view sorts, filters and groups all its rows (3313 in the screenshot) on the main
   thread, on every recomposition.
3. Open with and Share hand TypeScript to video players, because Android says `.ts` is a video.

## 2. The reference

- **VS Code** keeps two things apart:
  - the *language* of a file: name, extension, first line (`contributes.languages`);
  - its *icon*, from an icon theme. The theme is data, a JSON of `fileNames`, `fileExtensions`
    (longest first), `folderNames` and `languageIds`, resolved in that order, then the default
    file or folder.

  iCode's `FileTypes` is already the first half, forked from VS Code's own language table at a
  pinned commit.
- **Material Icon Theme** (PKief, MIT) is VS Code's most-used icon theme. At release 5.40.0
  (Open VSX, 2026-10-26) it has:
  - 1256 SVG icons;
  - 1391 extensions, 2156 file names and 200 language ids;
  - 4674 folder names, each with an open variant;
  - light-theme overrides for the icons that need them.

  It covers every folder kind the request names (`src`, `node_modules`, `.git`, `.github`,
  `components`, `docs`, `test`…).
- **File signatures:** the first bytes of a format (PNG `89 50 4E 47`, PDF `%PDF-`, Matroska
  `1A 45 DF A3`, an MPEG transport stream's `0x47` sync byte every 188 bytes…). This is how
  `file(1)` and Android's own media scanner tell formats apart when a name cannot.

## 3. Decisions

**D1 — One table says what a file is: `FileTypes`.**
- Its rules gain the kinds that are not text:
  - video, audio, PDF, word-processor, spreadsheet, presentation and e-book documents;
  - archives, built from `ArchiveFormats`' own endings so the two are never two lists;
  - app packages, fonts, executables and databases.
- These come with new families: `Video`, `Audio`, `Archive`, `App`, `Binary`.
- Each type says whether it is text (`FileType.text`).
- `FileCategories` becomes a projection of the family, with no extension lists. Its two documented
  exceptions stay explicit: an SVG is a picture there while the editor reads it as XML, and a
  `.txt` file is a document.
- *Rejected:* fixing the `VIDEOS` set alone. The next shared extension would break the same way,
  and the lists would still disagree.

**D2 — The bytes decide only where the name cannot.** `FileTypes.of(name, head)` reads the first
bytes:
- for a name two formats share (`.ts`, `.mts`): TypeScript unless the bytes are a transport stream;
- for a name that says nothing (no extension, or one no rule knows): the signature table
  (`FileSignatures`).

Every other name is never read.

The data layer reads at most 512 bytes in three places:
- the scan, which records the kind with each file;
- Properties;
- Open with and Share.

Every other place classifies by name.
- *Rejected:* sniffing every file. It means an `open()` per file in a 3000-file scan, and it would
  overrule names that are right.

**D3 — The kind travels with the file.**
- `ScannedFile` keeps its type. A scanned view's `BrowsedItem` carries it, so a real `.ts` video
  in Videos gets the video icon and a video thumbnail, and a moved file keeps it unless its
  extension changed.
- `ThumbnailKind` comes from the type, not the category.

**D4 — MIME from iCode's table first.** A text type that Android names a video or audio file is
handed out as `text/plain`. Android's MIME stands otherwise, so an SVG stays `image/svg+xml` and
JSON stays `application/json`.

**D5 — Icons are a forked theme, as data: Material Icon Theme 5.40.0.**
- `tools/import-file-icons.py <vsix>` copies the icons the theme uses, with its mapping, into
  `app/src/main/assets/file-icons/`. The import is pinned and reproducible, and nothing in it is
  hand-written.
- `domain/files/FileIconTheme.kt` resolves as VS Code does:
  1. a `parent/name` path key, then the whole name;
  2. extensions from the longest;
  3. `FileTypes`' language id (through `LanguageServiceRules.languageIdOf`);
  4. the default.

  Folders resolve by name, with the `-open` icon when expanded. On a light palette the theme's
  `light` overrides come first.
- A type the bytes decided gets the icon of its own kind.
- *Rejected:*
  - hand-drawn icons, or a list of Material Outlined glyphs per language, which never covers
    "everything";
  - Seti's font, which has no folder icons;
  - converting the SVGs to Kotlin `ImageVector` code, the "code dump" the request rules out.

**D6 — Drawn by AndroidSVG 1.4 (Apache-2.0).**
- Each icon is parsed once into a `Picture` and drawn at any size by one `Painter`.
- AndroidSVG reads everything the theme's SVGs use: paths, groups and transforms, gradients,
  `use`, clip paths and fill rules.
- The theme is read once, off the main thread, from app start.
- SVG files in the Files grid get thumbnails by the same means, for files up to 1 MB.

**D7 — Every icon reads on every theme.**
- An icon's colours are moved together, by one lightness step in OKLCH (hue and chroma kept), until
  its strongest colour is 3 : 1 against the background, the surface and the raised surface. This
  is Seti's rule today, applied to a whole icon so the icon keeps its own contrasts.
- Measured before the rule: on the Light palette 673 of the theme's 1140 icons have no colour at
  3 : 1, against 5 on Dark.
- The default folder takes the accent, as folders do today.

**D8 — One composable draws every file and folder icon: `FileTypeIcon`.** It is used by:
- the Explorer tree (folders open and closed included) and the Files explorer;
- sheets, Quick Open, Source Control, Problems and the editor's tab sheet.

`fileTypeIcon`, `fileTypeTint`, `itemIcon`, `iconOf` and the per-language Seti table are removed.
The categories keep their Material glyphs and Seti tints in Manage storage.

## 4. Cycles

- **K1 — one kind.** `FileFamily` grows; `FileTypes` gains the kinds that are not text, `text`,
  `needsContent` and `of(name, head)`; `FileSignatures` is added. `FileCategories`, `FileViewer`,
  `ThumbnailKind` and the assistant's attachment kind are projected from them. Tests: every
  formerly listed extension keeps its category except `.ts`, the ambiguity rule, the signatures,
  and uniqueness across the new rules.
- **K2 — the bytes where needed.** `FileHeads` in data; the scan, Properties, Open with, Share and
  the Files provider; `ScannedFile`/`BrowsedItem` carry the type; moves keep it. Tests: a scanned
  transport stream is a video and a TypeScript file is code.
- **K3 — the defects.** The thumbnail negative cache; a view's rows remembered.
- **I1 — the theme as data.** The importer, the assets and the licence.
- **I2 — the resolver.** `FileIconTheme`. Tests run against the shipped theme: `.d.ts`, `.ts`,
  `Dockerfile`, `package.json`, `.github/workflows`, `node_modules`, open folders, light overrides,
  a sniffed video, and every mapped icon present.
- **I3 — drawing.** AndroidSVG, the colour rule (tested over every icon on every palette),
  `FileTypeIcon` at every call site, SVG thumbnails, and removing the replaced helpers.
- **I4 — docs as built.** This plan, the CHANGELOG and the notices.

## 5. As built

- **K1 — one kind.**
  - `domain/FileTypes.kt`:
    - `FileFamily` gains `Video`, `Audio`, `Archive`, `App`, `Binary`, and `FileType` gains `text`.
    - New rules for video, audio, PDF, word, spreadsheet, presentation, e-book, CSV, app packages,
      fonts, executables and databases. The archive rule is built from `ArchiveFormat`'s endings,
      minus the app packages. `jfif` and `dng` are added to pictures.
    - New functions: `extensionsOf` (the longest-first walk, shared with the icon theme), `byId`,
      `extensionOf`, `needsContent`, `of(name, head)` and `HEAD_BYTES`.
  - `domain/FileSignatures.kt` reads: the transport stream (188- and 192-byte packets, three in a
    row); PNG, JPEG, GIF, BMP, ICO and WEBP; ISO media by brand (HEIF/AVIF pictures, M4A sound, else
    video); Matroska, MPEG, FLV, ASF, AVI; WAVE, Ogg, FLAC, ID3, MPEG audio frames and MIDI; PDF; zip,
    gzip, 7z, RAR, XZ, zstd, bzip2 and tar; ELF, wasm and dex; SQLite; WOFF/WOFF2/OTF. A signature
    made of letters is taken only with the binary bytes its header continues with.
  - `FileCategories.of(type, name)` is the family's category, with the SVG and `.txt` exceptions.
    `FileViewer.of(type)` and the assistant's PDF check follow the type.
- **K2 — the bytes where needed.**
  - `data/io/FileHeads.kt` reads the first 512 bytes only when `needsContent` says so. It is used
    by `StorageScan`, Properties (`FileFacts.type`), `WorkspacesProvider.mimeTypeOf(file)`
    (the Files provider's rows, Open with and Share) and `SharedFiles`.
  - `ScannedFile.type`, with its category derived. A scanned view's `BrowsedItem.type`.
  - `StorageInsights.after` keeps the type while the extension stays.
- **K3 — the defects.**
  - `Thumbnails` remembers failures (up to 1000) and makes SVG thumbnails up to 1 MB.
    `ThumbnailKind.of(name, type)`.
  - `FilesViewScreen` remembers its filtered, sorted and grouped rows.
- **I1 — the theme as data.**
  - `tools/import-file-icons.py` imports Material Icon Theme 5.40.0 into
    `assets/file-icons/theme.json` (239 KB) and 1194 SVGs (887 KB, about 0.7 MB compressed). Keys
    are lower case. Each folder icon has its one open icon. Only the icons the theme names are
    shipped.
  - Licence: `resources/licenses/MIT-Material-Icon-Theme.txt`.
- **I2 — the resolver.** `domain/files/FileIconTheme.kt`, as D5 describes. A name no language
  claims gets the default file, as in VS Code.
- **I3 — drawing.**
  - `ui/FileIcons.kt`:
    - `FileTypeIcon` is the one composable.
    - `FileIcons` reads the theme once (`warm` from `ICodeApplication`) and keeps one `Picture`
      per icon per palette. An icon that cannot be drawn falls back to the default file.
    - `FileIconColours` applies the colour rule. It reads only the colour attributes, never an
      `id` or a `href`.
    - `parentName` supplies the folder name for path keys.
  - `Oklch.readableTogether` moves an icon's colours together.
  - `FileTypeIcon` is drawn by: the Explorer tree, the Files explorer rows and tiles (behind
    thumbnails), the item, Files and Trash sheets (`SheetIcon(name, folder, location)`), Quick
    Open, Source Control rows, folders and sheets, Problems, the tab sheet, and the assistant's file
    sheet.
  - Removed: `fileTypeIcon`, `fileTypeTint`, `itemIcon`, `iconOf`, `fileTypeColor` and
    `fileTypeColours`. `FileTypeIcons.kt` is now `FileCategoryIcons.kt`, holding the categories'
    glyphs and Seti tints with one contrast minimum.
- **Tests:**
  - `FileTypesTest`: families, `text`, archive endings, `needsContent`, bytes and extensions.
  - `FileKindsTest`: `.ts`, `.d.ts`, `.mts`, `.cts`, every new kind, and the bytes rule.
  - `FileSignaturesTest` (4): every signature, and text that begins like one.
  - `FileIconThemeTest` (5): shipped versus named, the file and folder order, bytes, and light.
  - `FileIconColoursTest` (3): every icon on every palette, relations kept, ids untouched, and
    the accent folder.
  - `FileCategoryIconsTest` (2).
  - `StorageInsightsTest` (moves keep the kind), `StorageScanTest` (a stream, a source and a
    declaration), and `FileHeadsTest` (2).
- **Gate:** 1533 unit tests run, and the only failure is `TreeSizeTest`'s root-only case, as on
  main. assembleDebug and assembleRelease build, with all 1194 icons in the minified APK (AndroidSVG
  uses no reflection on its own classes). Both consistency guards pass. Lint reports only main's
  existing error.

## 6. Phone checks (owner)

1. Files › Manage storage › Videos lists no TypeScript. A real `.ts` recording is there, with a
   thumbnail.
2. Explorer and Files: TypeScript, `.d.ts`, JSON, Markdown, `package.json`, `Dockerfile`,
   `.gitignore` each have their own icon. So do `src`, `node_modules`, `.git`, `.github`,
   `components` and `docs`, and they change when expanded.
3. Light theme: no icon fades into the background.
4. Open with on a `.ts` source offers text editors; on a `.ts` recording, video players.
5. Scroll a large Videos or Images view: no stutter, and thumbnails do not reload.
