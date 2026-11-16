# Editor and language services

iCode combines a native text editor, offline TextMate syntax grammars, optional semantic tokens, workspace search, Markdown rendering, and a generic Language Server Protocol client. Editing and grammar coloring do not require a language server.

## Status at a glance

| Area | Status | Scope |
|---|---|---|
| Editor session, draft, external-change, and Git-gutter repository implementation | Done | Implemented with JVM coverage for the underlying session and data rules. Native-view and device acceptance remains separate. |
| Search in Files and Quick Open | Done | Implemented with cancellable ripgrep work and local file ranking. |
| Project-wide replace | Not Started | Only replacement within the open document is available. See the [project-wide replace roadmap](./roadmap.md#project-wide-replace). |
| Output panel | Partially Done | Output currently shows the latest bounded Git-operation transcript only. |
| TextMate syntax system and 357-language catalog repository implementation | Done | Implemented and covered by catalog, grammar, role, limit, and rendering tests. Device and performance acceptance remains separate. |
| Generic LSP protocol and capability implementation | Done | Implemented and tested against in-process servers. Real-server and device acceptance remains separate. |
| Real phone, provider, and performance acceptance | Needs Verification | Provider startup, memory, filesystem watches, semantic colors, native grammar timing, and some visual interactions still need device checks. |

## Editor sessions and tabs

The editor uses Sora Editor and keeps one `CodeEditorSession` per open document location. The Editor and in-document Find route reuse that same native view, text content, and undo owner. Switching tabs or tools therefore keeps caret, scroll, folds, and editor undo while the session remains live. Closing a document releases its native session.

The document owner:

- opens an already-open file by selecting its existing tab;
- keeps tab order and the active document consistent;
- supports tab reorder, Close Others, and Close Saved;
- chooses a neighboring tab after close;
- follows file and folder moves; and
- closes tabs whose files were removed by an iCode file operation.

Closing an unsaved tab requires an explicit Save or Discard decision. Persisted workspace sessions can restore workspace identity, open file identifiers, tab order, selection, and supported editor state. Dirty text is kept by the separate draft system rather than being trusted to navigation state. Native view restoration after Android process death remains **Needs Verification** on a phone.

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/ui/editor/CodeEditorSession.kt`
- `app/src/main/java/dev/icode/mobile/ui/editor/CodeEditorSessions.kt`
- `app/src/main/java/dev/icode/mobile/data/workspace/DocumentSessionCoordinator.kt`
- `app/src/main/java/dev/icode/mobile/data/workspace/WorkspaceSessionCoordinator.kt`
- `app/src/test/java/dev/icode/mobile/domain/DocumentTabsTest.kt`

## Editing and settings

Keystrokes update the document snapshot immediately. Dirty state is calculated against the last saved text. Enter, paste, and typed newlines follow the document's existing LF, CRLF, or CR convention.

Editor settings apply live to existing sessions and become defaults for new sessions. Current settings include:

- font size;
- Plex Mono or the system monospace face;
- word wrap;
- line numbers;
- bracket matching;
- minimap;
- Android keyboard suggestions;
- automatic bracket closing;
- semantic highlighting;
- Git change markers;
- tab width; and
- tabs or spaces.

The configurable key row supports Tab, Outdent, arrows, Home, End, Undo, Redo, and symbols. While completion is open, Tab accepts the selected item and Up/Down changes selection. Disabling Keyboard Suggestions updates the native input flags and restarts an open software keyboard.

In-document Find uses the active editor session rather than creating a second text buffer. It is separate from workspace Search in Files. Replacement within the open document is available; replacement across the workspace is not.

## Draft recovery and external changes

Dirty text is backed up in app-private draft storage one second after typing becomes quiet. A later edit replaces the pending timer. Leaving the editor flushes pending drafts, while saving, discarding, or returning to clean text removes the draft.

If a leftover draft differs from disk, the document opens read-only until the user chooses Restore or Keep Disk Version. If disk changed after the draft was recorded, saving the restored text requires explicit overwrite consent.

Every save compares against the exact disk version originally read. A known external change blocks manual and automatic save rather than silently overwriting the file. Reconciliation follows these rules:

- A clean open document reloads from disk automatically.
- Dirty text that conflicts with disk asks for Reload from Disk, Overwrite Disk, or Compare with Disk.
- An externally deleted file stays open with its buffer and is reported once.

Outside text, including reloads, draft restoration, and assistant edits, is applied as one minimal splice when possible. This protects caret context and makes the change undoable in the editor.

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/data/workspace/DocumentDraftStore.kt`
- `app/src/main/java/dev/icode/mobile/data/workspace/DocumentRecoveryCoordinator.kt`
- `app/src/main/java/dev/icode/mobile/data/workspace/DocumentSessionCoordinator.kt`
- `app/src/test/java/dev/icode/mobile/data/workspace/DocumentRecoveryCoordinatorTest.kt`
- `app/src/test/java/dev/icode/mobile/domain/EditorTextRulesTest.kt`

## Git gutter

The Git gutter compares the current buffer, including unsaved edits, with the stage-0 regular-file version in the Git index. It does not show markers for untracked, ignored, conflicted, symlink, or submodule entries. The index reader accounts for checkout attributes so CRLF conversion does not make every line look changed.

Added, modified, and deleted hunks appear beside line numbers. If line numbers are disabled, the marker gutter is also absent, while the document change count remains available. Selecting a marker opens a change sheet with Revert, Stage, Previous, and Next where applicable.

Revert applies an undoable editor edit. Stage writes the index with only the selected hunk and refuses stale hunk data. Markers recalculate after 200 ms of quiet and refresh when the index stamp changes. Disabling Git Change Markers prevents the index reads and diff work. Staged-change markers and scrollbar markers are not provided.

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/data/git/GitLineChanges.kt`
- `app/src/main/java/dev/icode/mobile/data/git/GitIndexVersions.kt`
- `app/src/main/java/dev/icode/mobile/domain/LineDiff.kt`
- `app/src/main/java/dev/icode/mobile/ui/editor/CodeEditorSession.kt`
- `app/src/test/java/dev/icode/mobile/domain/LineDiffTest.kt`

## Completion

Completion uses ready language servers that serve the active file and advertise `completionProvider`. iCode requests suggestions while a word is being typed or after a trigger character declared by the server. Spaces and unrelated punctuation close or avoid opening the list.

Matching favors contiguous and word-start matches, preserves server `sortText` as a later ordering key, and removes duplicates returned by several servers. The first typed character must match a word start. Parsing accepts at most 5,000 server items before filtering.

The native popup shows at most six 36 dp rows, is capped at 420 dp wide, and chooses space above or below the caret while remaining inside the editor. It can show kind, match emphasis, label or signature, detail, and deprecation. The first item is selected. Software-keyboard Enter or key-row Tab accepts it.

Ranking and placement have JVM tests. Caret-relative placement, IME interaction, touch selection, and TalkBack remain **Needs Verification** on a phone.

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/data/language/LanguageRequests.kt`
- `app/src/main/java/dev/icode/mobile/data/language/LspResults.kt`
- `app/src/main/java/dev/icode/mobile/ui/editor/EditorCompletion.kt`
- `app/src/test/java/dev/icode/mobile/ui/editor/EditorCompletionRulesTest.kt`
- `app/src/test/java/dev/icode/mobile/data/language/LspResultsTest.kt`

## Search and Quick Open

Search in Files and Quick Open are modes of the same workspace tool. Their query survives mode changes.

### Quick Open

Quick Open starts with no query and shows recent workspace files. Its workspace file index is loaded lazily. Ranking happens locally: exact and filename matches come before weaker path matches. Result limits are reported as limits rather than errors.

### Search in Files

Search waits 250 ms after input, then starts cancellable ripgrep work. It supports case sensitivity, whole-word matching, and regular expressions. A new query cancels the previous process, and generation checks prevent stale results from replacing a newer search. Blank input clears the results.

Selecting a result opens its exact file, one-based line, and column. Excerpts use the shared read-only syntax renderer. The ripgrep runtime tool is provisioned on first use; installation, indexing, and search failures appear as errors instead of empty results.

Ordinary saves do not rebuild the file index. Operations that may add, remove, or move entries invalidate it.

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/ui/screens/workspace/WorkspaceFindScreen.kt`
- `app/src/main/java/dev/icode/mobile/data/search/SearchCoordinator.kt`
- `app/src/main/java/dev/icode/mobile/data/search/RipgrepCommand.kt`
- `app/src/test/java/dev/icode/mobile/data/search/SearchCoordinatorTest.kt`
- `app/src/test/java/dev/icode/mobile/data/search/RipgrepJsonParserTest.kt`

### Project-wide replace

**Status: Not Started**

iCode does not currently replace across files. The roadmap calls for a preview of affected files and match counts, explicit scope, selectable exclusions where supported, confirmation before Replace All, and truthful partial-failure reporting. See [Project-wide replace](./roadmap.md#project-wide-replace).

## Output and logs

**Status: Partially Done**

The Output panel currently has one real channel: the latest bounded Git-operation transcript, with truthful idle, running, and failure states. It does not yet aggregate runtime, search, or language-service events.

Each language service has its own detail-page log. That log retains up to 200 timestamped lifecycle and server-message lines, truncates each retained line to 500 characters, and appends the server stderr log's last 100 lines. These logs are not Output channels.

Broader bounded and sanitized channels, selection, redaction, follow/scroll behavior, restart behavior, and workspace-switch handling remain on the [General Output channels roadmap](./roadmap.md#general-output-channels).

## Syntax highlighting

### Catalog and lazy loading

The current catalog contains **357 languages** in `app/src/main/assets/textmate/catalog.json`. Each entry records its language ID, root scope, grammar and configuration assets, include/embedding relationships, file rules, source, pinned upstream, and license. File classification uses the catalog's name, extension, prefix, and first-line rules.

TextMate is the offline base layer. Startup reads the catalog but parses no grammar. The first editable file or read-only block for a language loads that grammar and its include closure on the dedicated `syntax-grammars` thread. A failed grammar is cached as a failure for that language and leaves its files plain. Content remains readable while a grammar loads.

TextMate grammar objects are not shared across threads. Every editor session receives its own registry and grammar. Read-only rendering uses separate grammar instances confined to the `syntax-tokens` thread.

Maintainer entry points:

- `app/src/main/assets/textmate/catalog.json`
- `app/src/main/java/dev/icode/mobile/domain/syntax/Languages.kt`
- `app/src/main/java/dev/icode/mobile/ui/syntax/SyntaxEngine.kt`
- `app/src/test/java/dev/icode/mobile/ui/editor/LanguageCatalogTest.kt`
- `app/src/test/java/dev/icode/mobile/ui/editor/GrammarThreadsTest.kt`

### Roles and palettes

TextMate scopes resolve to semantic `SyntaxRole` values plus bold and italic flags. Cached spans do not store final colors. The active palette supplies those colors when drawing, so theme and accent changes recolor existing spans without retokenizing the document.

Scope-to-role precedence is centralized, and the editor's TextMate theme is generated from the same role table used by read-only rendering. This keeps editable files, Markdown fences, diffs, search excerpts, change sheets, conflict views, and assistant code or change views on one color vocabulary.

The read-only renderer tokenizes line by line and carries grammar state across lines. Its cache is an LRU bounded to 1,000,000 UTF-16 characters across entries.

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/ui/syntax/SyntaxScopes.kt`
- `app/src/main/java/dev/icode/mobile/ui/syntax/SyntaxRoles.kt`
- `app/src/main/java/dev/icode/mobile/ui/syntax/SyntaxTokens.kt`
- `app/src/main/java/dev/icode/mobile/designsystem/SyntaxColors.kt`

### Exact syntax limits

The limits are safeguards, not phone performance claims.

| Limit | Current behavior |
|---|---|
| 10,000 characters per line | The line is colored only up to this boundary; the remainder is plain. A surrogate pair is not split. |
| 100 ms per line | Tokenization stops at the time bound; the unprocessed remainder stays plain. |
| 2 MiB UTF-8 per editable file | A larger editable file opens without syntax coloring. |
| 50,000 lines per editable file | A file above this count opens without syntax coloring. |
| 200,000 characters per read-only block | A larger Markdown fence, diff, or other read-only block is shown plain. |

A separate editor read limit can still allow text to open when syntax coloring has been disabled by these thresholds.

Implementation: `app/src/main/java/dev/icode/mobile/ui/syntax/SyntaxLimits.kt`. Boundary tests are in `SyntaxLimitsTest.kt` and `LimitedGrammarTest.kt` under `app/src/test/java/dev/icode/mobile/ui/syntax/`.

### Semantic tokens

Semantic highlighting is an optional layer over TextMate and is enabled by default. iCode chooses the first ready server that serves the open document and advertises full semantic tokens. It requests a full result first, then delta updates when the server supports them. Range-only semantic-token providers are not used.

Server token types and modifiers map back into the same syntax-role palette. Semantic runs overlay grammar spans only for text that still matches the version answered by the server. Editing affected lines drops their stale semantic runs until a fresh response arrives. Turning the setting off immediately leaves the TextMate colors.

The request has a 5-second limit. Refresh requests, full/delta decoding, stale-edit behavior, line clipping, and setting changes have automated coverage.

Provider caveats:

- clangd and the installed TypeScript language server advertise semantic tokens;
- gopls advertises them only when its `semanticTokens` setting is enabled;
- Pyright 1.1.414 and pylsp do not advertise semantic tokens; and
- basedpyright implements Python semantic tokens, but it is not the current Pyright preset.

Whether to enable gopls semantic tokens by default or change/add a Python provider is an open [language-preset roadmap decision](./roadmap.md#language-presets-python-go-semantic-tokens-and-dockerfile-escape). Semantic coloring and refresh still need observation on a physical phone.

The bundled Dockerfile grammar uses backslash for instruction continuation. A Dockerfile that selects backtick with ``# escape=` `` can color a continued line as a new instruction. Track this in the [Dockerfile grammar roadmap](./roadmap.md#language-presets-python-go-semantic-tokens-and-dockerfile-escape).

## Markdown and RaTeX

Markdown Preview and Assistant answers share a native Compose renderer built on JetBrains Markdown's GFM parser. It does not use a WebView. Preview is a lazy block list. During a streamed assistant answer, stable blocks are retained while the unsettled tail is reparsed, followed by one full parse when the answer completes.

Supported presentation includes headings, paragraphs, nested and task lists, quotes and GitHub alerts, horizontal rules, tables with horizontal scrolling, links and images under explicit trust rules, a safe HTML subset, front matter, footnotes, details, and anchors. Unknown or unsafe HTML is displayed as source rather than executed. Fenced code uses the shared read-only TextMate path and the 200,000-character block limit.

Math uses the vendored RaTeX engine and KaTeX fonts. Files accept `$…$`, `$$…$$`, and `math` fences. Assistant answers additionally normalize `\(…\)` and `\[…\]` outside code. Layout runs off the main thread and is cached by source and display mode; color and scale apply when drawing.

A formula above 4,000 characters, 300 em wide, or 100 em tall is shown as source with “Too large to draw.” The newest 256 layouts are cached.

Parser, streaming, delimiter, layout, and link behavior have automated coverage. Native Android formula execution, hostile-input handling, final visual output, scrolling, selection, and TalkBack remain **Needs Verification** on a phone.

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/domain/markdown/`
- `app/src/main/java/dev/icode/mobile/ui/markdown/`
- `app/src/test/java/dev/icode/mobile/domain/markdown/`
- `app/src/androidTest/java/dev/icode/mobile/ui/markdown/MarkdownMathTest.kt`

## Generic Language Server Protocol client

### Configuration and architecture

iCode has one generic stdio LSP implementation rather than per-language adapters. A language-service configuration supplies a command, language IDs or file patterns, root markers, environment, initialization options, settings, and enabled state. Presets provide configuration and installation hints; compatible custom stdio servers can also be configured.

A server process runs inside iCode's Linux runtime. The client sends `initialize`, records only capabilities the server reports, sends `initialized` and configuration, then opens matching documents. Host and runtime paths map to `file:` URIs. Positions are zero-based UTF-16, matching Kotlin strings and the editor.

Editing remains available when a server is missing, starting, failed, stopped, or unsupported.

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/data/language/LanguageServices.kt`
- `app/src/main/java/dev/icode/mobile/data/language/LanguageServerSession.kt`
- `app/src/main/java/dev/icode/mobile/data/language/jsonrpc/`
- `app/src/main/java/dev/icode/mobile/domain/LanguageServiceContract.kt`

### Lifecycle

One server runs per enabled configuration in the active workspace. A server starts lazily when a matching document opens, or explicitly when the user or assistant requests it.

- At most three servers run at once. Starting a fourth stops the least recently used server.
- A server with no matching open document stops after 15 minutes.
- Initialization can wait up to five minutes for servers that index slowly, with a status update every 15 seconds.
- Graceful stop sends shutdown, waits up to three seconds, sends exit, and then closes the runtime process.
- Unexpected exits restart after 1, 2, 4, 8, and 16 seconds.
- Five exits within three minutes leave the service Failed until an explicit start.
- Switching workspaces or losing all owning screens stops the current servers and clears their document and diagnostic state.

Processes use kill-on-exit. Process death is not treated as a resumable language-server session.

### Document sync and diagnostics

Synchronization follows the server's declared None, Full, or Incremental mode. Changes coalesce after 150 ms of quiet and flush at least every 500 ms during continuous typing. A feature request flushes pending changes first so the server does not answer against older text.

Diagnostics retain canonical file, range, service, and version identity. Stale versions are discarded. Build and language-service diagnostics can coexist.

### Capability-gated features

iCode exposes a feature only when the server advertises the matching capability and iCode implements it. Current protocol support includes:

- completion and completion resolve;
- hover and signature help;
- definition and references;
- document and workspace symbols;
- formatting;
- rename and prepare-rename;
- code actions and code-action resolve;
- pull diagnostics; and
- full semantic tokens, including delta where advertised.

The client also handles workspace configuration, workspace folders, progress, show/log messages, diagnostic and semantic refresh, and `workspace/applyEdit`. Unknown server requests receive a method-not-found response.

Workspace edits can modify open or closed text files when their versions and paths are safe. File create, rename, and delete resource operations are not advertised; a server edit containing them is refused as a whole. Workspace symbols currently serve assistant tooling rather than a Command Palette query UI.

### Protocol and request limits

| Limit | Value |
|---|---|
| Protocol header | 8 KiB maximum |
| Protocol message body | 64 MiB maximum |
| Completion request | 3 seconds |
| Completion resolve | 1.5 seconds |
| Hover or signature help | 2 seconds |
| Definition, references, and other navigation | 10 seconds |
| Formatting, rename, or code-action execution | 15 seconds |
| Semantic-token request | 5 seconds |
| Parsed completion items | 5,000 maximum before filtering |
| Workspace-symbol results | 200 maximum |
| Assistant wait for a closed-file server | 30 seconds |

Newer completion and hover requests cancel older ones.

### Acceptance status

**Status: Needs Verification**

In-process fake-server tests cover framing, initialization, lazy start, server limits, idle stop, crash handling, document synchronization, versioned diagnostics, completion, results, workspace edits, workspace switching, semantic full/delta/refresh, and graceful shutdown. This proves the generic client contract, not every third-party server or Android device.

The canonical real-server, device, performance, inotify, process-lifetime, and native-editor semantic-token checks live only in [Generic LSP device and provider acceptance](./roadmap.md#generic-lsp-device-and-provider-acceptance) and [Editor, syntax, semantic tokens, and Markdown](./roadmap.md#editor-syntax-semantic-tokens-and-markdown). Public support means compatibility with generic stdio language servers, not certification of every preset or provider.
