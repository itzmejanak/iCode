# File Viewers: Pictures in iCode's Own Sheet

Owner's request (2026-10-20): pictures should open in a viewer of iCode's own, built so that more
kinds can follow without gaps or bugs. In the chat, a tap on an attached picture opens it in a
sheet, and a tap on an attached workspace file opens it in the editor.

## 1. What stood (verified)

- A picture tapped in the Explorer went to Android's Open with
  ([open-with-plan.md](./open-with-plan.md)): the editor reads files as text, and a picture is not text.
- `FileTypes` had three kinds of file: code, data and documents. Pictures were plain text by name,
  so they showed the document icon.
- The assistant had its own list of picture extensions (`AssistantRules.IMAGE_EXTENSIONS`) and its
  own decoder for thumbnails (`AssistantPickers.thumbnail`).
- In the chat, attached files were drawn but did nothing on a tap. A sent attachment kept its name
  but not whether it came from the workspace.

## 2. What it does

| Where | A tap on a picture | A tap on another file |
|---|---|---|
| The Explorer, the file explorer's workspaces, Find, Source Control, recent files, the chat's file paths | The image viewer | As before: the editor, or Open with for a file that is not text |
| Phone storage, in the file explorer | The image viewer | As before: Open with |
| A photo sent in the chat | The image viewer, the photo as it was sent | — |
| A file sent in the chat from the workspace | — | The editor (a PDF: Open with), the file as it is now |
| A file going with a draft (the composer) | The image viewer | A workspace file: the editor |

A file sent from the phone, other than a photo, keeps only what was sent, so a tap does nothing.

**The image viewer** is a sheet over the screen, the picture's name for its title, the screen
blurred under it as under any sheet. The picture is shown whole, in the height the sheet leaves.
Two fingers pinch it up to 5× larger and move it while it is larger. A double tap zooms to 2.5×,
and a second double tap goes back to the whole picture. Back, ×, or a tap outside closes the sheet.
The picture is decoded as large as the screen's longer side, off the main thread. An animated GIF
or WebP shows its first frame.

A picture the viewer cannot read (a damaged file, or AVIF before Android 12) goes to Open with, as
a file the editor cannot read does. A picked file that cannot be read is only reported, because
Android lent it to iCode alone.

Pictures are `png jpg jpeg gif webp bmp heic heif avif ico`. SVG stays XML: its source is what is
edited. Pictures are drawn with the image icon, in Seti's purple (Seti's colour for `png`, `jpg`,
`gif`, `webp`).

## 3. How: one seam for every viewer

| Layer | Part | Holds |
|---|---|---|
| Domain | `FileFamily.Image`, the image rule in `FileTypes` | What a picture is: one list, read by the Explorer's icons, the viewers and the assistant's attachments |
| Domain | `FileViewer` | The viewers, and `FileViewer.of(name)`: which viewer, if any, opens a file. Its `when` over `FileFamily` is exhaustive, so a new kind of file must say whether it has one |
| Domain | `ICodeEffect.ViewFile(location, viewer)` | The backend's request to show a file in its viewer |
| Data | `WorkspaceLifecycleCoordinator.reveal` | Every "open this file" (`ICodeIntent.OpenFile`) passes here. A file with a viewer emits `ViewFile`, the workspace left as it is; any other opens in the editor |
| UI | `FileViewerState`, `LocalFileViewer` | The one file shown, over every screen. Screens show what they hold themselves (the chat's photos, a phone file) |
| UI | `FileViewerHost` | Draws the shown file. Its `when` over `FileViewer` is exhaustive, so a viewer without a screen does not compile. It also hands an unreadable file to Open with, for every viewer |
| UI | `ImageSheet`, `decodeImage` (`ImageViewer.kt`) | The image viewer, and the one decoder, which the chat's thumbnails use too |
| UI | `attachmentOpener` | The chat's one rule for what a tap on an attachment opens, for sent files and drafts alike |

A new viewer, say for PDFs, needs:

1. an entry in `FileViewer`;
2. its kind in `FileViewer.of` (and in `FileTypes`, if the kind is new);
3. its screen, with its branch in `FileViewerHost`.

Nothing else changes: every place that opens files reaches it through `reveal` or `LocalFileViewer`.

Reopening the last session never shows a viewer: only `reveal`, which answers a user's request,
emits `ViewFile`.

`IcodeBottomSheet` takes `scroll = false` for content that fits the height left rather than
scrolling in it, which a picture needs.

A sent attachment now records `inWorkspace` (`AssistantAttachment`, kept in the chat's file), so a
tap can find the file in the chat's workspace.

## 4. As built

### 4.1 How it was checked

| Check | What it showed |
|---|---|
| Type-checks | The backend and domain. The viewer host, the image sheet, the chat's sent files, its composer and pickers, the file explorer and the design system (Compose desktop jars, Android's own stubs) |
| `FileViewersTest`, `FileTypesTest`, `AssistantRulesTest`, `AssistantAttachmentsTest`, `AssistantChatStoreTest` | 51 tests pass. New: which names open in a viewer; a picture's family, SVG still code; a workspace file is sent as one, and a chat keeps it so |
| `FileTypeIconsTest` and the design system's tests | 23 tests pass: the picture's Seti colour, at 3 : 1 or more on every theme |
| Guards | Backend and UI consistency, line budgets |

`IcodeApp`'s three lines (the state, the `ViewFile` branch, the host around the screens) are
outside every type-check here. The whole suite was not run, at the owner's word on short cycles.

### 4.2 On the phone, open

- A PNG and a JPG in the Explorer: the sheet, pinch, move, double tap, Back.
- A HEIC photo on phone storage, from the file explorer.
- A PDF in the Explorer: still Open with.
- In a chat, attach a photo from the camera and a workspace file, then tap each in the composer.
  Send, then tap each in the chat: the photo shows, and the file opens in the editor.
- An AVIF on Android 11: Open with.
