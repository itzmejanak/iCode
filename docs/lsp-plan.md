# Language services (LSP) end to end

Status: **built** (2026-11-09), the owner's request: L1–L9 and the assistant's tools (L4a, L6a, L8a),
every decision in §11 as recommended, except where §0 says the build went another way. L0's
measurements and the phone checks LP1–LP10 (§9) need the owner's phone and are still to be run.

> "Go with correct direction and decision so no gaps and issue in future and we are 100% accurate
> and let's implement"

> "Let's focus and do research and plan for LSP end to end without being too wrong architecture and
> design, make sure speed/performance, scalability, maintainability, durability, stability … if you
> found anything wrong in architecture or design … take it to the right direction."
>
> "Also ensure like voice tools, run tools, iCode assistant is also capable to use same backend to
> configure LSP when needed without duplicacy and inconsistency."

What it asks for, and where this plan answers it:

| The request | Here |
|---|---|
| LSP end to end: servers, protocol, editor, Problems, Explorer, palette | §5, §7 |
| The architecture checked, and what is wrong today put right | §3 |
| Speed and performance | §5.6, L0 measurements (§8) |
| Scalability: many files, several servers, large projects | §5.3, §5.6, D5–D7 |
| Maintainability: one owner, no per-language code, small files | §1, §5.1, D1 |
| Durability and stability: crashes, restarts, workspace switch, process death | §5.4, §5.5, D6 |
| The assistant sets up and uses language services through the same backend | §1, §6 |

## 0. As built (2026-11-09)

| Cycle | Commit | What it holds |
|---|---|---|
| L1 | e9948d4 | `data/runtime/GuestStdio.kt` (`StdioPrograms`, `StdioProgram`), taken from `McpLocal` unchanged; MCP's tests pass as before |
| L2 | 3e86736 | `data/language/jsonrpc/LspFraming.kt`, `JsonRpcConnection.kt`; `kotlinx-serialization-json:1.11.0` declared |
| L3–L5 | 960a6c5 | Configuration v2 and presets (`domain/LanguageServiceRules.kt`, `LanguageServicePresets.kt`), `LanguageIntent`, the owner `LanguageServices` with the pool, sessions, documents and problems; the coordinator and process manager deleted |
| L6–L9 | f7a295b | Problems in the editor (`ui/editor/EditorProblems.kt`) and the Explorer, completion (`ui/editor/EditorCompletion.kt`), the palette's language commands and their sheet, edits, the assistant's eleven tools |
| Fixes | c89144b | `stopAll` takes only the servers running when it is called; a server stopped with the screens starts again when its file is edited; imports resolved off the main thread |

**Checked here.** This sandbox is x86-64 Linux. The Android SDK 36, NDK 27.0.12077973 and CMake 3.22.1
were installed for the work, so for the first time an agent's container ran the whole Gradle gate:
- `compileDebugKotlin` passes.
- `testDebugUnitTest` runs 1,495 tests. The only failure is `TreeSizeTest`'s "a directory that cannot
  be listed": it fails on `main` too, because the sandbox runs as root and root can list anything.
- `assembleDebug` and `assembleRelease` (R8) both build.
- Both consistency scripts pass. The gateway's allowance went down from 338 lines to 337.
- `lintDebug` reports one error, `NonObservableLocale` in `ui/screens/settings/SpeechProviderParts.kt:79`.
  That file is not touched by this work, and `main` has the same error.

The language tests are 59 in `app/src/test/.../data/language/`, `domain/LanguageServiceRulesTest.kt` and
`ui/editor/EditorCompletionRulesTest.kt`, plus two of the palette's. They use in-process fake servers
(`FakeLanguageServer.kt`): the handshake, shutdown and exit, the crash
loop leading to Failed, cap eviction, lazy start, coalesced changes, version-checked problems, the
assistant's wait for a report, formatting, refusing a stale edit, the workspace-switch race, and a
restart after the screens were gone.

**Where the build went past or away from the plan:**
- **D8, watched files.** iCode does not advertise `workspace/didChangeWatchedFiles`, so servers watch
  for themselves. PRoot translates the path of `inotify_add_watch` (`third_party/proot`
  `src/syscall/enter.c:2522`, `PR_inotify_add_watch`), so a server's watches reach the real files,
  iCode's writes and the terminal's alike. Whether Android's inotify watch limit holds for large trees
  (`node_modules`) is an L0 question.
- **D11, hover.** Hover and signature help are Command Palette entries (Show Hover, Show Signature
  Help), not a long press: Sora's long press selects a word, and taking it away would break selection.
- **A problem's message** shows in Sora's own diagnostic tooltip when the caret is on it, coloured from
  the palette, rather than in a sheet. Its code actions are the palette's Code Actions.
- **Diagnostics** keep `file` and `location` (display) and gain `range`, `code` and `serviceId`. A build's
  problems gained their range too (`GradleIssues`), so Problems opens either at its place. The language
  servers' problems are a list of their own (`ICodeSnapshot.languageDiagnostics`), so a build and a
  server never overwrite each other's; `ICodeSnapshot.problems` is both.
- **Roots (D5).** `rootUri` is always the workspace's top. `workspaceFolders` are the marker folders,
  as planned. There is no fallback to "the first document's marker folder".
- **Lines.** `LspDocuments` does not keep a line index. A flush finds positions by one scan of the text
  last sent, which is fast enough since it runs at most every 150–500 ms.
- **The log** is on the service's page (what iCode saw, then stderr's last 100 lines). The Output panel
  still has only Git's channel.
- **Files.** The new domain types are in `domain/` beside the old ones (`LanguageFeatures.kt`,
  `LanguageServicePresets.kt`), not in a `domain/language/` package. Moving them would be a move of its
  own (code-separation-plan §2).
- **Edits.** `resourceOperations` is not advertised. A server edit that creates, renames or deletes a
  file is refused whole.
- **Workspace symbols** are the assistant's (`workspace_symbols`). The palette has no entry, because it
  would need a query field.
- **Editing a running server's configuration** is allowed. Saving restarts the server with the change,
  so the user and the assistant both edit without stopping it first.
- **Restart policy** is iCode's own (D6) for every server. architecture.md §10.8 lists it as a
  configuration field; it is not one.
- **The keystroke copy (A8, D13)** is unchanged, waiting on L0's numbers.
- **After the owner's first phone run (2026-11-09).** Four "… stopped" notices arrived at once: YAML,
  Bash and JSON had been added from presets without being installed, and Kotlin did not answer
  `initialize` in 60 s. What changed:
  - A server whose program is missing shows **Not installed** on its row and its page. Its Server
    page has **Install in Terminal**, which types the preset's install command in a new terminal tab
    (`LanguageIntent.Install`, `TerminalCoordinator.type`). Configuration has the same button.
  - It is said as a notice only when someone asked to start it ("Python is not installed"). Opening a
    file it serves is not asking.
  - A server that fails its handshake says "… could not start". "… stopped" is kept for five crashes.
  - `initialize` waits up to 5 minutes (JVM servers such as kotlin-lsp index under PRoot before they
    answer). Every 15 s the row says how long it has waited.

**Not run (needs the owner's phone):** L0 (server start times and memory under PRoot, inotify limits,
`UpdateDocumentText` at 100 KB and 1 MB) and LP1–LP10.

## 1. The rule: one owner, three callers

[agent-control-plan.md](./agent-control-plan.md) §1 made "one owner, two callers" the rule. Language
services have **one owner**, `LanguageServices` in `data/language`. Three callers reach it, and
each is a thin adapter over the same typed operations:

| Caller | Reaches the owner through | Example |
|---|---|---|
| The user's pages (Settings › Languages, Problems, Output) | `LanguageIntent`, handled by `LanguageServices.handle` | Save a server, Restart |
| The editor | documents it already sends (`UpdateDocumentText`, save, close) and the `LanguageFeatures` contract | squiggles, completion, hover |
| The assistant | tools in `data/language/LanguageServiceTools.kt` | `save_language_service`, `language_diagnostics` |

This is how speech providers work today (`SpeechProviders`, `SpeechIntent`,
`SpeechProviderTools.kt`) and how MCP servers work (`McpServers`, `McpIntent`, `McpServerTools.kt`).
The same order applies (agent-control-plan §1):
1. Add the typed operation to `LanguageServices`.
2. Have the intent handler call it, turning a failure into a notice.
3. Have the tool call it, turning a failure into the model's words.
4. Give the tool a kind for the gate. Use `asksAlways` where the change outlasts the chat.

A tool never reaches past the owner: no store, process or JSON-RPC connection of its own. The
assistant installs a server the way the user does, with `run_command` in its terminal or through
Runtime Tools. This plan adds no installer.

## 2. Facts this plan rests on (checked 2026-11-09)

### 2.1 iCode today

Read from the code at `550aaf5`. `M` below is `app/src/main/java/dev/icode/mobile`.

- **Configuration only, no protocol.** `M/data/language/` holds three files:
  - `LanguageServiceStore` saves recipes in SharedPreferences `language_services`, key
    `configured_servers_v1`: id, name, command, patterns, root markers, initialization options.
  - `LanguageServiceCoordinator` validates, saves, and starts, stops and restarts processes. It
    allows at most 16 services.
  - `LanguageServiceProcessManager` says of itself: "Owns only guest-process lifecycle. No LSP
    protocol claims are made here."
- **Nothing speaks LSP.** There is no JSON-RPC code, no lsp4j and no Sora `editor-lsp`.
  `DiagnosticsContainer`, `DiagnosticRegion` and `requireAutoComplete` are used nowhere.
- **Process start.** `RuntimeProcess.start(prootCommand(argv, …))` runs the command's argv
  directly, with no `--kill-on-exit`. Nothing ever writes to the process's stdin. Its output is
  drained into a 2 KB tail used only for the exit message.
- **stderr is merged into stdout.** `app/src/main/cpp/icode_spawn.c:121` calls
  `dup2(stdout_pipe[1], STDERR_FILENO)`. Over this pipe, a server's log lines would land in the
  middle of protocol frames.
- **The MCP path already solves this.** `M/data/assistant/mcp/McpLocal.kt` starts
  `bash -c 'exec 2>>"$0"; exec "$@"' /tmp/icode-mcp/<id>.log <command> <args…>`. It checks the
  command with `command -v` first, starts with `killOnExit = true`, and stops in three steps: close
  stdin, wait 3 s, SIGTERM, wait 3 s, then `destroy()`.
- **Lifecycle.**
  - `WorkspaceSessionCoordinator` calls `languages.stopAll()` on workspace switch and on close.
  - `AndroidICodeGateway.screensGone()` also calls it.
  - `stopAll()` sends SIGKILL at once.
  - Start is only manual. Patterns and root markers are saved but never matched.
- **Diagnostics.** `domain/DiagnosticsContract.kt`:
  `Diagnostic(severity, file, message, location: String, source)`. `location` is display text such
  as `"line 42:15"`. The only producer is the Android build parser (`domain/AndroidApps.kt:28`).
  `ProblemsScreen` filters by severity and groups by file, and cannot navigate.
- **Documents.**
  - `OpenDocument(name, location, text, encoding, lineEnding, dirty, readOnly)` has no version.
  - On every keystroke, `CodeEditorSession` calls `editor.text.toString()` and dispatches
    `UpdateDocumentText(location, text)`. `DocumentSessionCoordinator.updateText` copies the whole
    text into the snapshot, outside the mutation lock.
  - Outside text (reload, assistant edits) reaches the view through `applyOutside`, using
    `EditorTextRules.splice`, which finds the smallest changed span.
- **Paths.** `WorkspacePaths` maps `files/workspaces/<name>` to `/workspace/<name>`, and phone
  storage to the same path. No code builds `file://` URIs.
- **Languages.** `domain/FileTypes.kt` is the single answer to "what kind of file is this?". Its
  ids follow VS Code's (`typescriptreact`, `cpp`, `python` …), with one exception: it says `shell`
  where LSP's identifier is `shellscript` (spec 3.17, "Text Document Item" table).
- **File watching.** `WorkspaceWatcher` is a FileObserver on the workspace top, the expanded
  folders and the folders of open files. It is not recursive.
- **Pages.**
  - `LanguageServicesScreen` and `LanguageDetailScreen` are real forms. They say "They do not
    enable editor completion or diagnostics yet" and "A running process has not completed an LSP
    handshake."
  - The Output panel has only the Git channel.
  - The Command Palette has no language entries.
- **Tests.** `LanguageServiceRulesTest` (7 tests) and `ProblemsFilterTest`. The coordinator, the
  store and the process manager have none.
- **Docs.** `architecture.md` §10.8, §11.4 and §16, and `prd.md` PR-LANG-01…07 and
  PR-PROB-01…03, require:
  - a generic client;
  - no compiled adapter per language;
  - only the capabilities the server reports;
  - editing that works with no server;
  - diagnostics in the editor, Explorer and Problems;
  - stale diagnostics removed;
  - servers started lazily.

  `Todo.md` #8 and B-05/B-06 items 4, 5 and 7 add:
  - diagnostics with real file/range/version identity;
  - a bounded Output channel;
  - restart with backoff;
  - cancellation.

### 2.2 The protocol

From the [LSP 3.17 specification](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/):

- **Positions** are zero-based line and character. Character offsets count UTF-16 code units unless
  the two sides agree otherwise in `general.positionEncodings`. `utf-16` is the only encoding every
  server must support. Line ends are `\n`, `\r\n` and `\r`.
- **Text sync.** A client may not opt out of open, change and close notifications. The server says
  whether it wants changes `Full` or `Incremental`.
- **File watching.** The spec recommends that the client watch files and send
  `workspace/didChangeWatchedFiles`, with servers registering glob watchers dynamically, rather
  than every server watching on its own.
- **Unknown values.** A client must ignore enumeration values it does not know. A missing
  capability means "not supported".
- **3.18.** LSP4J's changelog lists "Finalized support for LSP 3.18.0" for its next version and
  implemented 3.18 in 1.0.0 (Feb 2026). This plan negotiates 3.17 features and reads 3.18 fields
  loosely (for example `Diagnostic.message` as markup).

### 2.3 Libraries

- **[LSP4J](https://github.com/eclipse-lsp4j/lsp4j)** 1.0.0 (Feb 2026) is the Java reference
  implementation. It maps with Gson by reflection and returns `CompletableFuture`. Gson by
  reflection needs R8 keep rules for every protocol class
  ([Gson troubleshooting](https://github.com/google/gson/blob/main/Troubleshooting.md)).
- **Sora's [`editor-lsp`](https://github.com/Rosemoe/sora-editor/tree/main/editor-lsp)** is built
  on LSP4J (`implementation(libs.lsp4j)`).
  - Its `LspProject` holds server wrappers keyed by extension, and an `LspEditor` per open file
    that is bound to a `CodeEditor` view.
  - It has its own events for completion, diagnostics, hover, signature, formatting, rename, code
    actions and inlay hints.
  - Users report trouble sharing one server across editors
    ([issue #637](https://github.com/Rosemoe/sora-editor/issues/637)).
- **Sora's editor** already has what rendering needs:
  - `DiagnosticRegion(startIndex, endIndex, severity)` with a `DiagnosticDetail`, set through
    `DiagnosticsContainer`;
  - completion through `Language.requireAutoComplete` and `CompletionPublisher`;
  - snippet items (`lang/completion/snippet`, `SimpleSnippetCompletionItem`).
- **kotlinx.serialization JSON** is already in the app, through Koog and the MCP SDK.
  `data/assistant/koog/*` and `data/assistant/mcp/*` build `JsonElement` by hand. The serialization
  compiler plugin is not applied. `app/build.gradle.kts` does not declare
  `kotlinx-serialization-json` itself: it arrives only transitively. L2 declares it explicitly, at
  the version Koog and the MCP SDK already resolve, so the language code doesn't depend on another
  library's dependency list.

### 2.4 Language servers on Ubuntu 24.04 ARM64

| Server | How it installs in the runtime | Checked |
|---|---|---|
| clangd 18 (C, C++) | `apt install clangd` | [packages.ubuntu.com/noble/clangd](https://packages.ubuntu.com/noble/clangd), arm64 |
| gopls 0.18 (Go) | `apt install gopls` | [packages.ubuntu.com/noble/gopls](https://packages.ubuntu.com/noble/gopls), arm64 |
| pylsp 1.10 (Python) | `apt install python3-pylsp` | [packages.ubuntu.com/noble/python3-pylsp](https://packages.ubuntu.com/noble/python3-pylsp), arch all |
| pyright, typescript-language-server, bash-language-server, yaml-language-server, vscode-langservers-extracted | `npm i -g …` on Runtime Tools' Node.js 26.13.0 | npm packages; not yet run under PRoot (L0) |
| rust-analyzer, jdtls, kotlin-lsp, lua-language-server | their own installers | not checked; not in the first presets |

How they perform under PRoot on a phone (start-up time, memory, inotify) is **not measured**. L0
measures it before anything depends on it.

## 3. What is wrong today, and the fix

| # | Finding | Why it matters | Fix (cycle) |
|---|---|---|---|
| A1 | `LanguageServiceCoordinator.save` takes `ICodeIntent.SaveLanguageService`, not a typed value. Errors become notices inside it. | A tool can't call it without faking an intent. That breaks "one owner, two callers" and invites a second save path. | Typed operations returning `Result`, as `SpeechProviders.save/remove/use` do. `handle(LanguageIntent)` calls them (L3) |
| A2 | stderr is merged into stdout (§2.1). | A single log line from a server corrupts LSP framing. | Start through the same wrapper MCP uses, extracted once into `GuestStdio` and shared by both (L1) |
| A3 | No `--kill-on-exit`. | If iCode dies, the server and its children can be left running inside PRoot. | `killOnExit = true`, as MCP does (L1) |
| A4 | `stopAll()` sends SIGKILL at once, with no `shutdown`/`exit`. | Servers lose their caches and indexes, and may leave lock files. | `shutdown` request, then `exit`, with a 3 s deadline, then TERM, then KILL (L4) |
| A5 | Start is manual. Patterns and root markers are never used. One process per configuration, bound to whichever workspace was open. | It doesn't follow §11.4 ("a document matches a configured language service; the supervisor starts or reuses the server"). | A pool keyed by (configuration, workspace), started lazily on the first matching document, with a cap and an idle stop (L4) |
| A6 | `Diagnostic.location` is display text. | It can't be navigated, can't be expired by version, and can't come from two servers (Todo item 4). | `Diagnostic` gains a range, a URI identity, the service id and the document version. Display text is derived from them (L6) |
| A7 | The configuration lacks language ids, environment, settings and restart policy (architecture §10.8). | Matching and `workspace/configuration` have nothing to work with. | Configuration v2, read from v1 by a migration: v0.1.0 is public, so saved v1 recipes exist (L3) |
| A8 | Documents have no version. The whole text is copied into the snapshot on every keystroke. | LSP needs versions. The copy grows with file size and its cost is unmeasured. | Versions live in the LSP document mirror (`LspDocuments`), not in `OpenDocument`, which keeps the editor contract clean. L0 measures the keystroke path at 100 KB and 1 MB. If it is too slow, a scoped fix replaces it (§5.6) |
| A9 | The settings pages advertise nothing, and Problems/Output have no language channel. | The UI must show only what the server reports (PR-LANG-03). | Capabilities on the detail page, an Output channel per server, and palette entries only when advertised (L4, L6, L8) |
| A10 | The watcher is not recursive. | It can't feed `didChangeWatchedFiles` for a whole project. | iCode reports its own writes exactly. Changes made from the terminal are handled per D8 (L5) |
| A11 | The capture fixtures carry fake `LanguageService` rows and `"line 42:15"` diagnostics. | They would drift from the new types. | Fixtures follow the new types in the same cycle that changes them (L3, L6) |

None of these change the decisions the owner holds fixed (`architecture.md` §25–26). They bring
the code in line with them.

## 4. Decisions (the owner's are in §11)

- **D1 — Protocol code: iCode's own small JSON-RPC client over kotlinx.serialization.** Not LSP4J,
  and not Sora's `editor-lsp`.
  - `editor-lsp` ties protocol state to editor views (`LspEditor` per `CodeEditor`). In iCode,
    documents belong to the data layer: the assistant edits and reads diagnostics with no view open,
    and Problems lists files nobody has opened. Architecture §16 says the language supervisor owns
    the protocol state and the editor only renders.
  - LSP4J adds Gson reflection with R8 keep rules and a second JSON stack beside kotlinx.
  - The app already ships kotlinx JSON. The protocol subset iCode uses is about twenty methods.
  - The cost is the framing, the connection and hand-written mappers for that subset, all
    unit-tested against recorded server traffic.
- **D2 — Positions in UTF-16.** iCode advertises `positionEncodings: ["utf-16"]`. Sora's text and
  Kotlin strings are UTF-16, so offsets map one to one, with no conversion and no off-by-one on
  emoji.
- **D3 — Text sync: coalesced and incremental.** A change is not sent per keystroke.
  1. The mirror keeps the last text sent.
  2. A flush, after 150 ms of quiet and at most every 500 ms while typing goes on, computes one
     `EditorTextRules.splice` between that text and the current text.
  3. It sends one range change (or the full text if the server asked for `Full`).
  4. Every request flushes first, through the same single writer, so a request never sees text
     older than what the user has.
- **D4 — Generic only.** There are no per-language classes (PR-LANG-07). Presets (D9) are data:
  name, command, language ids, root markers, install hint.
- **D5 — One server per (configuration, workspace).**
  - `rootUri` is the workspace top.
  - `workspaceFolders` holds the folders that contain a root marker, found inside the workspace, or
    the top if there are none.
  - A server that doesn't support workspace folders gets the nearest marker folder of the first
    document as its root.
- **D6 — Crash and restart.**
  - A server that exits unasked is restarted after 1 s, 2 s, 4 s, 8 s and 16 s. After 5 exits within
    3 minutes it is Failed and waits for the user.
  - Open documents are opened again after a restart.
  - Pending requests fail with "The language server stopped". The editor keeps working (PR-LANG-05).
- **D7 — Requests are bounded.**
  - Each request has a timeout: completion 3 s, hover 2 s, navigation 10 s, format and rename
    15 s, `initialize` 60 s.
  - A newer completion or hover cancels the older one with `$/cancelRequest`.
  - A frame larger than 64 MB, or a header larger than 8 KB, ends the connection as a protocol
    failure.
- **D8 — Watched files.** See §11.
- **D9 — Presets.** See §11.
- **D10 — Server requests iCode answers:**
  - `workspace/configuration`, from the configuration's settings JSON, by section;
  - `client/registerCapability` and `unregisterCapability`;
  - `window/workDoneProgress/create` and `$/progress`, shown as the server's status;
  - `window/showMessage`, as a notice;
  - `window/logMessage`, to the Output channel;
  - `workspace/applyEdit`, through the document owner (§5.7).
  - Anything else gets `MethodNotFound`, which the spec allows.

## 5. Architecture

### 5.1 Files

```text
domain/language/
  LanguageServiceConfiguration.kt  v2: id, name, command, languages, patterns, rootMarkers,
                                   environment, initializationOptions, settings, enabled
  LanguageServiceRules.kt          validation, command words (kept), matching a file to services
  LanguageIds.kt                   FileTypes id → LSP languageId (shell → shellscript; the rest equal)
  LanguageContract.kt              LanguageService row, status, capabilities, LanguageIntent
  LanguageFeatures.kt              the interface the editor and pages call: complete, hover,
                                   signature, definition, references, symbols, format, rename,
                                   codeActions — results as domain types, never JSON
domain/DiagnosticsContract.kt      Diagnostic v2 (§5.4), kept in place: Build & Run uses it too

data/runtime/GuestStdio.kt         a guest process with clean stdout, stderr in a guest log,
                                   killOnExit, `command -v` check, graceful stop: extracted from
                                   McpLocal, used by MCP and LSP alike
data/language/jsonrpc/
  LspFraming.kt                    Content-Length frames over bytes, bounded header and body
  JsonRpcConnection.kt             ids, pending requests, timeouts, $/cancelRequest, one writer,
                                   one reader, server-to-client requests and notifications
data/language/
  LanguageServices.kt              the owner: configurations (typed CRUD), matching, the pool,
                                   document routing, LanguageFeatures, handle(LanguageIntent)
  LanguageServerSession.kt         one server: initialize → initialized → running → shutdown → exit,
                                   capabilities, dynamic registrations, restart policy
  LspDocuments.kt                  per session: uri, languageId, version, last text sent, flush
  LspMessages.kt / LspResults.kt   JSON ↔ domain mappers for the subset used
  LanguageDiagnostics.kt           the workspace's diagnostics: by uri and service, versioned,
                                   throttled into the snapshot
  LanguageFailures.kt              every failure in plain words (§5.8)
  LanguageServiceStore.kt          v2 key `configured_servers_v2`, reading v1 once
  LanguageServiceTools.kt          the assistant's tools (§6)
ui/editor/
  LanguageEditorBridge.kt          diagnostics → DiagnosticsContainer; completion through
                                   FileLineBreaksLanguage.requireAutoComplete → LanguageFeatures
ui/screens/…                       pages in §7
```

**Dependency rules:**
- `ui` depends on `domain/language` contracts only. `LanguageFeatures` reaches the editor through a
  CompositionLocal set by the composition root, as `LocalTerminalSessions` does.
- `data/language` never imports `ui`.
- Only `data/language/jsonrpc` reads or writes frames.
- `LanguageServiceProcessManager` and `LanguageServiceCoordinator` are removed once
  `LanguageServices` replaces them (L4). There is no second path.

Every file stays under the 300-line guard. `check-backend-consistency.sh` gains one rule:
`lsp-frame-owner`, meaning `Content-Length` appears only in `LspFraming.kt`.

### 5.2 Process and connection

1. **Start.**
   - `GuestStdio` runs the argv through `bash -c 'exec 2>>"$0"; exec "$@"' /tmp/icode-lsp/<id>.log …`
     in its own PRoot, with `--kill-on-exit` and `GuestContext.LanguageServer` (the login
     environment) plus the configuration's environment.
   - The working folder is the workspace top. The program must pass `command -v`, otherwise
     `LanguageFailures.Missing` says what is missing and how its preset installs it.
2. **Reading and writing.**
   - One IO-dispatcher coroutine per server reads frames and decodes JSON off the main thread.
   - A single writer coroutine drains one channel, so notifications and requests keep their order.
3. **Initialize.**
   - iCode sends its client capabilities (§5.5) and waits up to 60 s.
   - It sends `initialized`, then `workspace/didChangeConfiguration` if settings are set.
   - It then opens every matching open document.
4. **Stop.** `shutdown`, then `exit`. Then the `GuestStdio` stop: close stdin, TERM after 3 s, KILL
   after 3 more.
5. **Logs.** stderr goes to the guest log. The Output channel shows its last 200 lines plus
   lifecycle events (started, initialized in N ms, exited with code, restarted). Message tracing is
   off by default and can be turned on per server for a session.

### 5.3 The pool

- A server starts when a document matching its configuration opens in a workspace (by language id,
  or by pattern when the configuration names patterns), or when the user taps Start.
- **Cap.** At most **3** servers run at once. The proposed default is in D7 (§11). Opening a fourth
  stops the one unused longest, after `shutdown`.
- **Idle stop.** A server with no matching open document for **15 minutes** stops.
- **Workspace switch.** Every server of the old workspace gets `shutdown`/`exit` in parallel.
  Switching does not wait for them, and their diagnostics are cleared at once.
- **Screens gone.** The behaviour is unchanged ("only what serves a screen stops"), but the stop is
  graceful.
- **Process death.** `--kill-on-exit` ends the servers. The next matching open starts them again.

### 5.4 Diagnostics

```kotlin
data class Diagnostic(
    val severity: Severity,          // Error, Warning, Info, Hint
    val location: String,            // the host file (canonical), as documents use
    val range: TextRange?,           // zero-based UTF-16 lines and columns; null for whole-file
    val message: String,
    val source: String,              // "pyright", "Build"
    val code: String?,
    val origin: DiagnosticOrigin,    // Language(serviceId, version?) or Build
)
```

- **Push and pull.** `textDocument/publishDiagnostics` replaces that server's list for that URI.
  When the server offers `diagnosticProvider`, iCode pulls `textDocument/diagnostic` after each
  flush of the active document instead.
- **Stale results.**
  - A list carrying a version older than the version last sent is dropped.
  - A closed document keeps the server's diagnostics only if the server keeps sending them.
  - A server stop or restart clears its lists.
  - A workspace switch clears them all (PR-PROB-03).
- **Into the snapshot.** At most once every 250 ms, capped at 2,000 entries for the workspace with
  errors first. The editor draws at most 1,000 regions per file.
- **Reads.** Problems, the Explorer badges, the editor and `language_diagnostics` all read this one
  list.

### 5.5 Client capabilities iCode advertises

Only what is built in the same cycle is advertised. The list grows L5 → L9.

- `general.positionEncodings: ["utf-16"]`
- `textDocument.synchronization`: `didSave`
- `publishDiagnostics` with `versionSupport`; `diagnostic` (pull)
- `completion` with `snippetSupport`, `resolveSupport` (`documentation`, `detail`,
  `additionalTextEdits`) and `insertReplaceSupport: false`
- `hover` and `signatureHelp` (markdown and plaintext)
- `definition`, `references`, `documentSymbol` (hierarchical), `workspace.symbol`
- `formatting`, `rangeFormatting`, `rename` (`prepareSupport`), `codeAction` (literal support)
- `workspace.applyEdit`, `workspaceEdit.documentChanges`, `configuration`, `workspaceFolders`
- `didChangeWatchedFiles.dynamicRegistration`, as decided by D8
- `window.workDoneProgress`

### 5.6 Speed

| Path | Rule |
|---|---|
| Keystroke | `UpdateDocumentText` hands `LanguageServices` the text reference and returns. The splice and position conversion run on `Dispatchers.Default` at flush time. |
| Requests | Each completion or hover cancels the last. A result that arrives after the document has moved on is dropped. |
| Results | Completion, hover and navigation results are returned by `LanguageFeatures` suspend calls. They never enter `ICodeSnapshot`, so typing doesn't recompose the app. |
| Diagnostics | Throttled to 250 ms, capped (§5.4). |
| Lines | `LspDocuments` keeps a line-start index of the last text sent, updated by the splice, so converting an offset to a position doesn't rescan the file. |
| Start-up | Lazy, capped and idle-stopped (§5.3). L0 measures the real cost. |
| Existing keystroke copy (A8) | L0 times `UpdateDocumentText` with 100 KB and 1 MB files. If a 1 MB file takes more than 16 ms per keystroke on the owner's phone, a scoped follow-up debounces the snapshot copy. The editor stays the truth while typing, and save and autosave read the latest text through a flush. That is its own cycle, outside LSP. |

### 5.7 Edits that come from a server

Formatting, rename, code actions and `workspace/applyEdit` produce a `WorkspaceEdit`. It is applied
by the owners that already exist:
- **Open documents:** through `DocumentSessionCoordinator`, as outside text. It arrives in the view
  through `applyOutside` and is one undo step.
- **Closed files:** through `WorkspaceWriter`, the same path `edit_file` uses. It refuses tabs with
  unsaved edits.
- **Creates, renames and deletes:** through `FileOperationsCoordinator`, so they join file undo.
- **Outside the workspace:** refused. Only the edit's URIs are checked against
  `WorkspacePaths.workspaceOf`.

An edit whose document version no longer matches is refused whole, with "The file changed; try
again". It is never applied in part.

### 5.8 Lifecycle and failures (backend-split-rules §5)

| Moment | Behaviour |
|---|---|
| Enter | Configurations load. A load error shows on the page, and saving is refused until it is fixed (kept). |
| Load | The first matching document starts its server. Its row shows Starting, then Running with its capabilities. |
| Act | Typing, saving and requests as above. Each feature appears only when the server reported it. |
| React | Diagnostics, progress and server messages arrive and are throttled into state. |
| Interrupt | A workspace switch, screens gone or Stop each run `shutdown`/`exit`, then the stop. |
| Recover | A crash restarts per D6. A load failure leaves editing untouched. |
| Failures, in plain words | Missing ("pylsp is not in the runtime. Install it: `sudo apt install python3-pylsp`"); initialize timeout; exited (code and the log's last lines); protocol error; request timed out. |

## 6. The assistant's tools

They sit in `data/language/LanguageServiceTools.kt` and call only `LanguageServices`. The names go
into `AssistantToolName`.

| Tool | Kind | Asks | What it does |
|---|---|---|---|
| `list_language_services` | Read | never | Each configuration: id, name, command, languages, status, capabilities, last failure, the log's tail |
| `save_language_service` | Change | **always** (`asksAlways`) | Adds or changes one, as the detail page does, from fields or a preset id. A saved command runs later in the runtime, so it outlasts the chat, as MCP and speech changes do. |
| `remove_language_service` | Change | **always** | Removes one and stops its server |
| `control_language_service` | Command | by the chat's permission mode | `start`, `stop` or `restart`, through the same operations as the buttons |
| `language_diagnostics` | Read | never | Problems for a file or the workspace. After the assistant's own edit, it waits up to 10 s for the server's diagnostics for the version that edit made, so "check for errors after editing" is exact |
| `find_definition`, `find_references` | Read | never | By file, line and column. Answers are `path:line:column` with the line's text |
| `document_symbols`, `workspace_symbols` | Read | never | The outline of a file, or symbols matching a query |
| `rename_symbol`, `format_file` (L9) | Change | as `edit_file` | The server's `WorkspaceEdit`, applied through §5.7. Their `reach` lists every file the edit touches, for the gate |

A read tool's `reach` is its file, so the gate treats a path outside the workspace as it already
does.

**A typical turn.** The user asks "set up Python support":
1. `list_language_services` finds none.
2. `run_command` runs `sudo apt install -y python3-pylsp`, asked as any command is.
3. `save_language_service` saves preset `pylsp`, asked every time.
4. The server starts when the next `.py` file opens, or through `control_language_service start`.

Every step shows on the user's pages at once, because both callers go through the same owner.

## 7. The pages

- **Settings › Languages.**
  - The list shows each service's name, languages and status.
  - **+** opens a sheet of presets and Custom. Each preset says how to install its server, with
    one-tap **Install in Terminal**, which types the command into a terminal tab.
  - The detail page has every v2 field, the advertised capabilities after the handshake,
    Start/Stop/Restart, and **Open Log**, which opens the Output channel.
  - The two "not yet" captions go.
- **Editor.**
  - Squiggles, from `DiagnosticsContainer`.
  - A tap on one opens a sheet with its messages and any code actions. It is a sheet, not a dialog
    (the 2026-11-04 rule).
  - Completion appears in Sora's popup, drawn as iCode's own since 2026-11-10 ([editor-completion-plan.md](./editor-completion-plan.md)).
  - Hover and signature open from the editor's ⋮ and from a long press on a word, as a small sheet.
  - A chip in the top bar shows the active document's server state: none, starting, running, or
    failed with Restart.
- **Problems.** Groups by file, with filters for severity, active file and source (layout §18.1).
  A tap opens the exact range through `ICodeIntent.OpenFile(path, lineNumber, column)`. Its line
  number is one-based and LSP's is zero-based, so the conversion is in one place, in
  `LanguageServiceRules`.
- **Explorer.** Error and warning counts on files and their folders (PR-PROB-01).
- **Command Palette.** Go to Definition, Find References, Go to Symbol, Rename Symbol, Format
  Document. Each is listed only when the active document's server advertises it. Results open in a
  list sheet whose rows match Workspace Search's result rows.
- **Output.** A channel per running server, bounded at 200 lines (layout §18.2).

## 8. Cycles

Every cycle passes the gate from `code-separation-plan.md` §6 before the next one starts:
- `compileDebugKotlin`, `testDebugUnitTest`, `lintDebug`;
- both consistency scripts, whose allowlists may only shrink;
- `assembleRelease` (R8);
- the phone check named for that cycle.

A move never shares a step with a change in behaviour.

| Cycle | Goal | Main files | Checked by |
|---|---|---|---|
| **L0** | Facts on the phone, before design depends on them: initialize time and memory of pylsp, clangd, gopls, pyright and typescript-language-server under PRoot; whether inotify works inside PRoot (D8); the cost of `UpdateDocumentText` at 100 KB and 1 MB (A8) | `tools/runtime-lab.sh` additions; a debug timing log | Numbers recorded in this plan |
| **L1** | `GuestStdio` extracted from `McpLocal`. MCP uses it with behaviour unchanged (a move) | `data/runtime/GuestStdio.kt`, `McpLocal.kt` | MCP tests unchanged and passing; an MCP server on the phone |
| **L2** | JSON-RPC core: framing, connection, cancellation, timeouts, server requests | `data/language/jsonrpc/*` | Unit tests over piped streams: split, merged and oversized frames, a reply out of order, a server request, cancel, timeout, EOF |
| **L3** | Configuration v2 and typed owner operations (A1, A7); `LanguageIntent` replaces the six `ICodeIntent` entries; v1 read once; fixtures updated (A11) | `domain/language/*`, `LanguageServiceStore`, `LanguageServices` (configurations only), the two settings screens | Migration test from a saved v1 JSON; rules tests; the page on the phone |
| **L4** | Sessions and pool: initialize, capabilities, registrations, shutdown/exit, restart/backoff, lazy start, cap, idle stop, workspace switch, Output channel (A3–A5, A9); the old coordinator and process manager deleted | `LanguageServerSession`, `LanguageServices`, Output | Tests with an in-process fake server: crash loop → Failed; switch mid-initialize; cap eviction. Phone: pylsp starts on opening a `.py` file |
| **L4a** | The assistant's configuration tools: `list`, `save`, `remove`, `control` | `LanguageServiceTools.kt`, `AssistantToolName`, toolbox | Tool tests over the owner; on the phone, the "set up Python" turn of §6 |
| **L5** | Documents: open, coalesced change, save, close; rename, delete and external reload; the assistant's writes to open files; watched files from iCode's own writes (D8) | `LspDocuments`, `DocumentSessionCoordinator` hooks | Tests: splice → range events for multi-line, CRLF and surrogate pairs; ordering against requests; versions only grow |
| **L6** | Diagnostics end to end (A6): push and pull, stale rules, snapshot throttle, Problems navigation, Explorer badges, editor squiggles and their sheet | `LanguageDiagnostics`, `DiagnosticsContract`, `LanguageEditorBridge`, Problems, Explorer | Tests with recorded pyright/pylsp output; on the phone, an error appears, is fixed, and disappears |
| **L6a** | `language_diagnostics` with the wait-for-version rule | `LanguageServiceTools.kt` | Tool test; on the phone, the assistant edits and then checks errors |
| **L7** | Completion: trigger characters, text edits, snippets through Sora's snippet items, resolve, `isIncomplete`, cancellation | `LanguageEditorBridge`, `LspResults` | Tests on recorded items; typing speed on the phone with completion on |
| **L8** | Hover, signature help, definition, references, document and workspace symbols; palette entries by capability | `LanguageFeatures`, palette, result sheets | Tests on recorded results; navigation on the phone |
| **L8a** | The read tools: `find_definition`, `find_references`, `document_symbols`, `workspace_symbols` | `LanguageServiceTools.kt` | Tool tests |
| **L9** | Formatting (and Format On Save, now available when the server formats), rename with prepare, code actions, `workspace/applyEdit` through §5.7; `rename_symbol` and `format_file` | `LanguageServices`, document owner hooks, tools | Tests: a stale version is refused whole; a multi-file rename is one undoable change |
| **L10** | Presets with install hints, docs as built, CHANGELOG, user stories US-LANG-001…008 checked off, capture fixtures | assets, docs | The phone checks below |

## 9. Phone checks (LP)

| Check | What to do, and what should happen |
|---|---|
| LP1 | Install pylsp from its preset. Open a `.py` file: Running within the time L0 measured, with its capabilities listed |
| LP2 | Type an error: a squiggle within about 1 s of stopping. Fix it: gone. Problems navigates to the exact range |
| LP3 | Completion in Python and TypeScript, including a snippet and an auto-import edit |
| LP4 | Kill the server (`pkill pylsp` in the terminal): restarted, documents reopened, diagnostics back. Kill it five times: Failed, with its log |
| LP5 | Switch workspace while a server initializes: no stale diagnostics, no orphan process (`ps` in the new workspace's terminal) |
| LP6 | Open four languages: the cap holds and the oldest stops gracefully. Leave one idle for 15 minutes: it stops |
| LP7 | A 1 MB file: typing stays smooth (L0 numbers), and diagnostics cap without freezing |
| LP8 | Rename a symbol across files: one undo restores everything. A rename while a file is dirty in another tab is refused |
| LP9 | The assistant sets up Python, edits a file, and checks errors with `language_diagnostics`. Every change asked as §6 says |
| LP10 | Force-stop iCode: no server is left running (checked after the next start) |

## 10. Not now

- Debugging (DAP).
- Semantic tokens: TextMate stays the highlighter (§16).
- Inlay hints, code lens, call and type hierarchy, folding ranges from the server.
- Notebooks.
- Servers over TCP or remote machines.
- Server configurations read from a workspace (`.vscode/settings.json`): a later plan with the same
  trust rule as `.mcp.json`.
- Bundling server binaries in the APK.

## 11. Owner decisions

| # | Question | Recommendation |
|---|---|---|
| D1 | Protocol library | iCode's own small client on kotlinx JSON (§4 D1). Alternatives: LSP4J (Gson, R8 rules, a second JSON stack) or Sora `editor-lsp` (protocol tied to editor views) |
| D5 | Monorepos | One server per workspace, with marker folders as `workspaceFolders` |
| D6 | Restart policy | 1–16 s backoff, Failed after 5 exits in 3 minutes |
| D7 | Resource limits | 3 servers at once, 15-minute idle stop. Revisit after L0's memory numbers |
| D8 | Watched files | Advertise dynamic `didChangeWatchedFiles`. Report iCode's own writes exactly (editor, Explorer, assistant, Git operations). For changes from the terminal, send a rescan of the registered globs when a terminal command ends (iCode already reads its OSC 133 end mark), if L0 shows inotify does not work under PRoot. If it does work, let servers that watch on their own do so, and advertise nothing for them |
| D9 | Presets | Ship a small data-only list: pylsp, pyright, clangd, gopls, typescript-language-server, bash-language-server, vscode-langservers-extracted (HTML/CSS/JSON), yaml-language-server. Each has its install command; nothing installs without the user's tap or the assistant's asked command |
| D11 | Hover on a phone | Long press on a word, and the editor's ⋮, opens a small sheet. No hover-on-tap, which would fight caret placement |
| D12 | Snippets | Advertise snippet support from L7, using Sora's snippet items |
| D13 | The keystroke copy (A8) | Measure in L0. Fix in its own cycle only if it exceeds 16 ms per keystroke at 1 MB |

## 12. Docs to update when built

- `architecture.md` §10.8: the v2 configuration fields, and the owner `LanguageServices`.
- `architecture.md` §11.4: lazy start, the pool and coalesced sync.
- `Todo.md` #8 and B-05/B-06 items 4, 5 and 7: link here, then check them off as built.
- `integration-plan.md` Cycle 8: points here.
- `agent-control-plan.md` §1 table: a row for language services.
- `CHANGELOG.md` Unreleased: what the user can do, written as the rest of it is.
