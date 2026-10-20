# Open With and Share: Every File Reaches an App That Shows It

Owner's request (2026-10-20): tapping a PDF or an image in the Explorer said "Binary files cannot be
opened in the text editor" and stopped there. A file the editor cannot show should go to whatever
app the user picks, with no limit on kind, kept simple.

## 1. What stood (verified)

- Opening a file reads it as text (`WorkspaceFileSource.readDocument`, `DocumentCodec.decode`).
  The read stopped with a toast for three kinds of file: binary (a NUL byte), text in no encoding
  it reads, and anything over 5 MB.
- In the file explorer, a file on phone storage had nothing to open in. A tap showed its menu.
- No file could be handed to another app. The one `FileProvider` served only the assistant's camera
  photo.

## 2. What it does

| Where | What happens |
|---|---|
| A tap on a file the editor cannot show, wherever it is opened from (the Explorer, the workspaces' file explorer, Find, Source Control, recent files) | Android's **Open with**: the apps that show its kind (by extension; an unknown kind goes to every app that takes any file) |
| A tap on a file on phone storage | **Open with**, the same way |
| A long press on any file, in the Explorer or the file explorer | **Open with…** and **Share…** after Open. Share is Android's share sheet with the file attached |

A text file still opens in the editor, as before. Reopening the last session never pops up a
chooser: only a file the user asked to open is handed out.

## 3. How

- `DocumentCodec` and `readDocument` throw `NotTextException` for those three cases (an
  `IOException`, so every other reader, the assistant's among them, reads the same message).
  `DocumentSessionCoordinator` turns it into `ICodeEffect.OpenInOtherApp`, and `IcodeApp` hands
  the file over.
- `SharedFiles` (UI) is the one place a file leaves iCode: a `FileProvider` URI, read permission
  granted for that one hand-off, then Android's chooser.
- One `FileProvider`, `${applicationId}.files`, replaces the camera's own. Its roots are the
  camera's cache folder, the workspaces and phone storage (`res/xml/shared_file_paths.xml`).
  Nothing under them is reachable without a grant for that one file.
- The kind of file comes from one lookup, `WorkspacesProvider.mimeTypeOf`, which the Files app's
  listing uses too.

## 4. As built

### 4.1 How it was checked

| Check | What it showed |
|---|---|
| Type-checks | The backend; the file explorer, its actions, the assistant's pickers and `SharedFiles` with `WorkspacesProvider` |
| Guards | Backend and UI consistency, line budgets |

`DocumentCodecTest` now expects `NotTextException` for binary data, and for bytes no encoding reads
(a new case). The test suite was not run for this change.

### 4.2 On the phone, open

- A PDF, a JPG and a PNG in a workspace: tap each to get Open with; long press for Share… (to a
  chat app, to Drive).
- A file on phone storage: tap it to open with another app.
- The assistant's camera: a photo is still taken and attached.
