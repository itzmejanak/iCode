# iCode roadmap

## Purpose

This is the single source of truth for unfinished work in the Android repository and for external work required to accept an Android capability. It contains no completed implementation chronology. Historical records may provide test detail, but they do not own current status.

Each item records four things:

- **Status** — one of the four labels below.
- **Current implementation** — source-backed behavior that exists now.
- **Remaining scope** — implementation, decision, or integration still required.
- **Verification evidence needed** — evidence required before the item can become **Done**.

## Status labels

- **Done** — required scope and evidence are available. Finished items should normally leave this file.
- **Partially Done** — useful implementation exists, but required implementation scope is absent.
- **Not Started** — no production implementation exists for the intended capability.
- **Needs Verification** — production implementation exists, but required device, provider, network, performance, release, or integration evidence is absent.

## Product gaps

### Project-wide replace

**Status:** **Not Started**

**Current implementation:** `data/search/SearchCoordinator.kt` and `RipgrepService.kt` provide read-only workspace search. `ui/screens/tools/EditorFindScreen.kt` replaces only within the active Sora document.

**Remaining scope:** Add a workspace replacement owner over the existing search and guarded document/file write paths. It must provide a dry-run preview, affected-file and match counts, file/match exclusion, explicit confirmation, dirty/open-document handling, encoding and line-ending preservation, changed-on-disk protection, cancellation, partial-failure reporting, and Git/tree/search/LSP invalidation. It must not turn a read-only search action into an implicit write.

**Verification evidence needed:** Pure planner tests for literal/regex replacement, include/exclude, ignored files, binary/oversized files, links, dirty tabs, and partial failure; integration tests proving writes use the atomic fingerprint-checked owner; phone checks from preview through confirmation, cancellation, recovery, and Git decoration refresh.

### General Output channels

**Status:** **Partially Done**

**Current implementation:** `ui/screens/tools/OutputPanelScreen.kt` displays the latest clone/fetch/pull/push `GitOperationState` transcript. Language-server logs remain on language-service details, terminal output remains in terminal sessions, and managed-process output remains in run/process surfaces.

**Remaining scope:** Define one bounded and sanitized Output channel contract for Git, runtime/tool setup, search, builds, and language services. Add channel selection, source identity, deterministic retention, dropped-history indication, clear/follow/scroll behavior, cancellation/failure state, and restart semantics. Producers must publish through their owning coordinators rather than screens reconstructing logs.

**Verification evidence needed:** Unit tests for ordering, source isolation, bounds, credential redaction, long lines, dropped history, clear/follow, cancellation, and restart; integration runs for Git, runtime install, ripgrep, Android build, and LSP; device checks for selection, scrolling, rotation, scale, and notification/arrival routing.

### Adaptive tablet, landscape, foldable, and multi-window shell

**Status:** **Not Started**

**Current implementation:** `ui/IcodeApp.kt`, `ui/NavigationState.kt`, and `ui/dock/ToolDock.kt` implement a compact phone-oriented shell. `MainActivity.kt` handles configuration changes but no production source owns window width classes or compact/medium/expanded composition.

**Remaining scope:** Add window-size ownership to the shell; medium/expanded rails and sidebars; persistent editor and bottom panel where width warrants it; resizable panes; side-by-side diff; landscape keyboard handling; fold posture and multi-window transitions; and route/panel/editor state continuity without duplicate feature state.

**Verification evidence needed:** Screenshot and interaction matrix at compact, medium, and expanded widths; portrait/landscape, split screen, fold/unfold, software/hardware keyboard, pointer, and stylus checks; process recreation and tool-state retention; TalkBack order and touch targets; at least one representative physical tablet or foldable.

### New Android app template

**Status:** **Not Started**

**Current implementation:** `data/android/AndroidProjects.kt`, `AndroidApps.kt`, `ApkInstaller.kt`, and `data/run/RunCoordinator.kt` discover, build, install, and launch existing Android application modules. No production template generator, creation action, template assets, or Gradle wrapper asset exists.

**Remaining scope:** After the Android toolchain matrix identifies a supported AGP/Kotlin pair, add a “New Android app” flow for an Empty Activity Compose project. Generate a valid workspace/project, include a pinned Gradle wrapper JAR and distribution checksum, select versions compatible with the installed SDK/NDK, and hand the project to the existing Run flow. Keep the first dependency build online rather than adding an offline Maven mirror.

**Verification evidence needed:** Clean-device create, inspect, build, install, launch, edit, and rebuild; Repair Runtime followed by rebuild with the same debug key; invalid/taken names; offline and interrupted dependency downloads; low storage; wrapper checksum validation; representative supported AGP versions.

### Secondary SD-card and USB explorer places

**Status:** **Not Started**

**Current implementation:** `data/storage/PhoneStorage.kt` and the Shared explorer support primary shared storage at `/storage/emulated/0`. No separate SD-card or USB place is discovered or presented.

**Remaining scope:** Add stable place discovery and identity for intended removable media; define the permission or Storage Access Framework model; handle mount removal, read-only media, free-space reporting, persistence, and app restart; preserve path confinement; and define copy, move, Trash, and cross-volume behavior without claiming Unix link or executable-bit semantics that the media cannot provide.

**Verification evidence needed:** Representative physical SD-card and USB storage across supported Android versions and OEMs; insert/remove during listing and transfer; permission grant/revocation; read-only and full media; duplicate volume names; process death; path and symlink escape attempts; and copy, move, conflict, and Trash behavior across volumes.

## Runtime and Android toolchains

### Android SDK and existing-app Build and Run matrix

**Status:** **Needs Verification**

**Current implementation:** `data/android/AndroidSdkSetup.kt`, `SdkManager.kt`, `GradleSettings.kt`, `AndroidProjects.kt`, `AndroidApps.kt`, `ApkInstaller.kt`, and `data/run/RunCoordinator.kt` implement the pinned SDK setup and existing-app flow. Pins are in `app/src/main/assets/runtime/manifest.json`.

**Remaining scope:** Execute the real ARM64 phone matrix: command-line tools and licence flow; ARM64 `aapt2`; AGP 9.x with the pinned AOSP 35.0.2 host tools; AGP 8.13 with the Build-Tools override; clean Gradle dependency download; build diagnostics; package confirmation and launch on API 30–36; Repair then rebuild to prove the debug key survives. Record peak RAM, process count, download, disk, and timing under Android phantom-process pressure.

**Verification evidence needed:** A durable table keyed by device, OEM, API, RAM, AGP, and project; command output and artifact checksums; APK/package/launch result; offline, low-memory, low-storage, cancellation, signature mismatch, and retry evidence. Desktop CI does not prove ARM64 host tools run under PRoot on a phone.

### Android NDK/CMake matrix and immutable pin hosting

**Status:** **Partially Done**

**Current implementation:** `data/android/CmakeSetup.kt`, `AndroidNdkSetup.kt`, `GradleSettings.kt`, `AndroidProjects.kt`, and `AndroidApps.kt` install and guard ARM64 CMake 3.22.1, Ninja, and NDK 27.0.12077973/28.2.13676358. `app/src/main/assets/runtime/manifest.json` pins current archive URLs, sizes, and checksums. NDK extraction runs in the guest with GNU tar/xz rather than the Android app heap.

**Remaining scope:** Run fresh installation of CMake and both NDKs; AGP 9.4/r28c Native C++; this repository or an equivalent AGP 8.13/r27 native project; refusal of an NDK 26 project without downloading Google's x86 host package; default `ndkVersion` behavior; actual AGP behavior after the guard removes unsupported native paths; `ndk-build`; peak memory and process count. Decide and implement immutable hosting for ARM64 SDK host tools and NDK archives. Current NDK publisher URLs can replace bytes at the same URL, so checksum pinning fails safely but can break fresh installs until a new app release.

**Verification evidence needed:** Phone matrix with successful compiler/sysroot checks and APK output; immutable owner-controlled URLs or reproducible publication that never replaces bytes; recorded digests, sizes, upstream source, build scripts, and provenance; redistribution/licence approval; altered/missing asset failure tests.

### Runtime declarations, guard debt, and timezone

**Status:** **Partially Done**

**Current implementation:** `data/runtime/GuestContext.kt`, `GuestEnvironment.kt`, `GuestPaths.kt`, and `RuntimeLayout.kt` implement the shared login environment, one complete `/workspace` bind, runtime-wide path handling, visible iCode-managed guest files, login-shell behavior, and no iCode process cap. Some declaration-only design tokens and helpers remain, the guard allowlists still contain grandfathered debt, and the guest time zone remains its own Ubuntu setting.

**Remaining scope:** Recheck and remove only declaration-only candidates that remain unused, including `IcodeSizes.DotMd`, `HiddenInput`, `LineNumberGutter`, `MarkIcon*`, `IcodeType.TerminalBase`, and `KoogFailures.isTransient`. Reduce guard allowlists only where the underlying ownership debt is actually removed. Decide whether the guest time zone should remain explicitly user-managed or gain a supported synchronization path; document and test the chosen behavior without adding filesystem or process restrictions.

**Verification evidence needed:** Before/after symbol and resource searches; compile and full unit tests; `tools/check-backend-consistency.sh`, `tools/check-ui-consistency.sh`, and `tools/runtime-lab.sh`; debug and release builds; and a device check of the chosen time-zone behavior.

### Runtime installation, Repair, Reset, and terminal lifecycle acceptance

**Status:** **Needs Verification**

**Current implementation:** `data/runtime/RuntimeInstaller.kt`, `RuntimeCoordinator.kt`, `RuntimeHomeBackup.kt`, `RuntimePackageInstaller.kt`, `TerminalSessionManager.kt`, `RuntimeForegroundService.kt`, and `TerminalSessionLedger.kt` implement base-runtime setup, recovery, package serialization, real PTYs, background representation, and truthful lost-session reporting.

**Remaining scope:** Execute the complete lifecycle on supported devices: fresh install; health, low-storage, cancellation, and retry paths; interrupted Repair with home restoration; Reset; package mutation recovery; PTY input/output and interactive programs; foreground-service and wake-lock behavior; process pressure and OEM freezing; and restart, low-memory kill, crash, and ANR reporting. Keep the inability to restore a live PTY or guest process explicit.

**Verification evidence needed:** API 30–36 and representative-OEM matrix with storage and memory pressure; install/Repair/Reset artifacts and checksums; package-manager interruption; terminal keyboard, resize, background, notification, and wake-lock behavior; process-kill evidence; and confirmation that the next launch reports ended work without presenting it as resumed.

## Assistant, MCP, voice, and phone control

### Existing MCP transports, sign-in, and server acceptance

**Status:** **Needs Verification**

**Current implementation:** `data/assistant/mcp/` implements local stdio, remote Streamable HTTP and legacy HTTP+SSE fallback, workspace declarations and trust, encrypted values, browser sign-in, tools, prompts/resources, elicitation, progress, result images, cancellation, and idle shutdown for the supported protocol era.

**Remaining scope:** Validate current behavior with real local and remote servers before treating the implemented transport, authorization, and lifecycle paths as accepted. This item does not include the missing modern-protocol implementation tracked separately below.

**Verification evidence needed:** Localhost stdio and HTTP, representative remote servers, configuration import, trust changes, browser OAuth return, permission prompts, images/results, progress, Stop, form and URL questions, process death, killed-server restart, cancellation, timeouts, idle stop, and log/credential redaction on supported phones.

### MCP modern protocol support

**Status:** **Partially Done**

**Current implementation:** `data/assistant/mcp/` implements configured and workspace servers, trust, encrypted values, local stdio, remote Streamable HTTP and HTTP+SSE behavior, sign-in, tools, prompts/resources, questions, registry results, and idle shutdown. `app/build.gradle.kts` pins official Kotlin SDK client `0.16.0`, whose active client behavior is from the legacy protocol era.

**Remaining scope:** Keep the protocol seam inside `data/assistant/mcp/` and adopt modern protocol behavior only when the official SDK client supplies dual-era discovery, stateless request metadata/headers, subscriptions, and multi-round-trip requests. Do not build a parallel MCP client. Reassess deprecated dynamic client registration when the website's client-metadata document is deployed.

**Verification evidence needed:** Modern-only, legacy-only, and dual-era servers over stdio and HTTP; protocol/header conformance; discovery; stateless requests; subscriptions; multi-round-trip input; cancellation/timeouts; fallback; OAuth; and no regression of the separately tracked existing-server acceptance matrix.

### Built-in assistant providers and tool lifecycle

**Status:** **Needs Verification**

**Current implementation:** `data/assistant/AssistantCoordinator.kt`, `AssistantEngine.kt`, `data/assistant/koog/`, `AssistantChatStore.kt`, and `data/assistant/tools/` implement provider configuration, chats, attachments, tools, approvals, questions, background turns, managed work, retries, context handling, and persisted messages.

**Remaining scope:** Validate real provider/model combinations and network failures, streaming and whole responses, reasoning/output termination, context compaction, attachments, approval modes, unsaved-edit protection, cancellation, process death, no-workspace chats, and commit-message generation. Validate assistant terminal rendering, interactive input, command marks, timeout/Ctrl-C behavior, explicit use of user-owned tabs, and shell-environment continuity on a physical device. Confirm secrets and transmitted content match the Privacy policy.

**Verification evidence needed:** Provider matrix for OpenAI-compatible, OpenAI, Anthropic, Google, OpenRouter, Ollama, and representative custom endpoints; wrong key/model, rate limit, no network, TLS/plaintext, timeout, cancellation, background question, process death, image/PDF, tool failure, and log/redaction checks.

### Phone-control safety and capture completion

**Status:** **Partially Done**

**Current implementation:** Accessibility-node actions, gestures, coordinate input, optional Shizuku fallback, screenshot capture, stale-element checks, overlays, hand-over, and Stop paths exist. Numbered-element actions enforce more package and surface checks than coordinate actions.

**Remaining scope:** Apply the required sensitive-package and sensitive-surface refusals consistently to coordinate actions; define and implement safe behavior when Accessibility refuses an action before Shizuku fallback; define and enforce handling for sensitive screen text and screenshot pixels before either reaches a model provider; implement the intended Android 14 per-window capture path or deliberately remove that requirement; and preserve actionable secure-window, rate-limit, and platform screenshot failures.

**Verification evidence needed:** Focused policy and routing tests for every action path and fallback; secure, permission, payment, password, CAPTCHA, and withheld surfaces; API 30 and API 34+ screenshot behavior; overlay exclusion; typed failure results; stale coordinates; and proof that rejected content is neither acted on nor transmitted.

### Phone-control real-device safety

**Status:** **Needs Verification**

**Current implementation:** `data/phone/PhoneService.kt`, `PhoneTree.kt`, `PhoneReading.kt`, `AndroidPhoneControl.kt`, `PhoneCapture.kt`, `PhoneLauncher.kt`, `PhoneCoordinator.kt`, `PhoneForegroundService.kt`, and `data/phone/shizuku/` implement AccessibilityService reading/actions, numbered screen elements, stale-screen checks, screenshot bounds, bubble/glow overlays, hand-over, optional Shizuku actions, and Stop entry points.

**Remaining scope:** After [Phone-control safety and capture completion](#phone-control-safety-and-capture-completion), validate that the product never acts on iCode approvals, Android permission dialogs, or Accessibility settings; hands passwords, payments, CAPTCHAs, secure/withheld views to the user; never logs screen content; and clearly discloses provider transmission. Validate restricted-settings onboarding for sideloaded APKs, Shizuku permission/reboot states, secure-window behavior, API 30 versus API 34 capture paths, stale-node refusal, service teardown, and background/locked-screen limits.

**Verification evidence needed:** API 30–36 and multiple-OEM matrix; Accessibility-only and Shizuku modes; reboot, process death, permission revocation, stale/wrong nodes, sensitive apps, `FLAG_SECURE`, gesture fallback, overlay drag around keyboard/system bars, TalkBack, and one-tap Stop from bubble/notification/chat. Logcat and storage evidence must show that screen content is not logged, raw screenshots are not persisted, retained screen text matches the disclosed chat tool-result boundary, and rejected passwords, payment data, and other sensitive content are neither retained nor transmitted.

### Voice providers and devices

**Status:** **Partially Done**

**Current implementation:** `data/voice/VoiceCoordinator.kt` and `data/voice/speech/` provide shared provider storage, encrypted keys, Android listening/speaking, AssemblyAI, OpenAI/OpenAI-compatible, Deepgram and Google listening, and Android plus cloud speaking adapters. Azure and ElevenLabs speaking are implemented. No silent fallback is intended.

**Remaining scope:** Run the listening and speaking provider/device matrices. Azure listening remains unimplemented until the SDK's APK/native size and product cost are measured and accepted. ElevenLabs realtime listening remains unimplemented. Preserve one provider list with separate listening/speaking selections, the 120-second listening bound, shared microphone/player ownership, and provider-specific transmission without fallback.

**Verification evidence needed:** Owner-supplied-key checks for Android baseline, live and upload providers, wrong key, no credit/rate limit where practical, airplane mode, localhost compatible server, bubble while iCode is away, partial/final words, first-sentence latency, sentence gaps, immediate silence, duck/restore, mixed listener/speaker, and agent-driven provider changes. New Azure/ElevenLabs listeners need protocol fixtures, network/privacy tests, size measurements, and phone acceptance.

## Source control and editor quality

### Source Control requirement completion

**Status:** **Partially Done**

**Current implementation:** Repository discovery and process-local selection, ordinary porcelain status, inline diffs, staging, commits, branches, conflicts, history, remotes, and first push through `origin` exist.

**Remaining scope:** Persist the selected nested Git repository across Android process death; request and present ignored paths as a distinct Source Control group; implement the required wide side-by-side diff while retaining the compact inline view; and let first push select the chosen or sole non-`origin` remote instead of hard-coding `origin`.

**Verification evidence needed:** Restart with several nested repositories and confirm restored selection; ignored files and directories with unusual paths; compact inline and wide side-by-side diff through width changes; and first push through both `origin` and a non-`origin` remote, including cancellation, authentication refusal, and upstream-state refresh.

### Git temporary credential cleanup

**Status:** **Not Started**

**Current implementation:** App-managed HTTPS authentication writes one owner-only credential-store file in the guest's app-private `/tmp` and deletes it in a `finally` block when the Git command completes, fails, or is cancelled normally. A hard Android process kill can prevent that block from running.

**Remaining scope:** On process startup, find only iCode-owned stale credential files created by this flow, validate their names and location, delete them before any Git operation, and report a safe failure without exposing token content. Do not delete arbitrary guest temporary files.

**Verification evidence needed:** Unit tests for exact filename ownership, unrelated-file preservation, missing directory, deletion failure, and idempotence; a device hard-kill during authenticated Git followed by restart; confirmation that no plaintext token remains in the owned files or logs.

### Git remotes, conflicts, branches, and GPG signing

**Status:** **Needs Verification**

**Current implementation:** `data/git/` owns CLI status, diffs, stage/unstage, commits, branches, conflicts, history, clone/fetch/pull/push, HTTPS/SSH credentials, and transfer interruption reporting. `data/runtime/GuestGpg.kt`, `GpgBridge.kt`, `domain/GpgPrompts.kt`, and Git auth/signing code implement key management and pinentry.

**Remaining scope:** Validate conflict resolution, branch creation/switch/deletion, remote branches, real HTTPS and SSH transfers through implemented upstream paths, interruption/retry, identity persistence, offline/auth/host-key failures, non-fast-forward behavior, and repository state after failure. Validate GPG clean setup, key creation/import/use, generated key algorithm/subkeys/expiry, passphrase prompts, cache expiry, background prompt, terminal/assistant commits, signed Commit & Push, and provider-side signature verification.

**Verification evidence needed:** Disposable real remotes on representative hosts; token and SSH success/refusal; `origin` first push; offline/reconnect; conflict/non-fast-forward fixtures; generated-key property inspection; signed commits verified by local Git and the hosting provider; no passphrase/token in app state, ordinary command scratch, notification, or logs; and process-death and runtime-Repair behavior. First push through a non-`origin` remote enters this matrix after [Source Control requirement completion](#source-control-requirement-completion).

### Editor, syntax, semantic tokens, and Markdown

**Status:** **Needs Verification**

**Current implementation:** `data/workspace/DocumentSessionCoordinator.kt`, `WorkspaceFileSource.kt`, and `ui/editor/CodeEditorSessions.kt` provide document/editor behavior. `ui/syntax/SyntaxEngine.kt`, `app/src/main/assets/textmate/`, and `data/language/LanguageSemanticTokens.kt` provide grammar and semantic coloring. `domain/markdown/` and `ui/markdown/` provide native Markdown rendering with RaTeX math.

**Remaining scope:**

- Editor: root/nested creation, duplicate feedback, tab select/drag/close, in-file Find/Replace focus/reveal, pinch, dirty workspace switch, external-change choices, process-death recovery, hardware/software keyboard, and 100–150% UI-scale behavior.
- Syntax: cold first-file latency, long/minified/near-limit files, native Oniguruma tests, diffs, every theme, clangd/TypeScript semantic tokens, and `workspace/semanticTokens/refresh` updating only the right editors with integration coverage.
- Markdown: real-device/source-preview behavior, streaming answers, tables, task lists, alerts, links/images/anchors/footnotes, math JNI in minified release, pinch, themes/borders, and TalkBack.

**Verification evidence needed:** Focused JVM/instrumented tests for document conflicts, semantic refresh collection, grammar samples, Markdown parsing/math and release retention; performance measurements for representative large files; phone matrix covering all interactions, themes, scales, keyboard modes, process recreation, and accessibility.

### Language presets: Python, Go semantic tokens, and Dockerfile escape

**Status:** **Partially Done**

**Current implementation:** Generic semantic-token transport is in `data/language/LanguageSemanticTokens.kt` and `LspSemanticTokens.kt`. Current presets/configuration support Pyright/pylsp behavior and gopls only when the server is configured to advertise semantic tokens. `tools/import-grammars.py` imports a Dockerfile grammar whose continuation handling recognizes backslash but documents the `# escape=\`` gap.

**Remaining scope:** Decide whether Python keeps Pyright, switches to basedpyright, or offers basedpyright as another preset. Decide whether the gopls preset enables `semanticTokens`. Decide whether Dockerfile backtick escape is accepted or implemented. If implemented, derive a directive-aware backtick continuation rule in `tools/import-grammars.py`; do not maintain a hand-copied second grammar.

**Verification evidence needed:** Real initialize/capability captures and semantic coloring for each accepted Python/Go preset; preset migration/backward compatibility; Dockerfile samples for Linux shell backticks, PowerShell continuations, valid/late parser directives, heredocs, and shell command substitution; deterministic importer output and catalog/sample tests.

### Generic LSP device and provider acceptance

**Status:** **Needs Verification**

**Current implementation:** `data/language/LanguageServices.kt`, `LanguagePool.kt`, `LanguageServerSession.kt`, JSON-RPC support, document synchronization, capability-gated requests, diagnostics, workspace edits, semantic tokens, and lifecycle tests implement the generic stdio client.

**Remaining scope:** Validate real preset installation and startup under PRoot; startup time and memory on representative phones; inotify behavior and limits in large dependency trees; large-document typing; cleanup after Android and OEM termination; provider-specific capabilities and configuration; and native-editor semantic-token appearance and refresh.

**Verification evidence needed:** A device/provider matrix with command and initialize captures, negotiated capabilities, startup/resource measurements, filesystem-watch behavior, crash/restart/idle-stop/process-kill results, large-document interaction, semantic refresh correctness, and proof that unsupported capabilities remain unavailable rather than fabricated.

### Editor completion interaction

**Status:** **Needs Verification**

**Current implementation:** `ui/editor/EditorCompletion.kt` and `EditorCompletionWindow.kt` implement trigger handling, bounded parsing, fuzzy ranking, deduplication, resolve, text/additional edits, snippets, kind badges, details, and keyboard selection.

**Remaining scope:** Validate ranking/highlight for short prefixes, badges/signature/type display, above-caret placement near the software keyboard, trigger and dismissal behavior, Tab/Enter/up/down handling, deprecated items, two active servers, rotation, large results, themes, scaling, and TalkBack.

**Verification evidence needed:** LSP fixture integration tests for resolve/additional edits/snippets and simultaneous servers; physical-device keyboard/touch matrix with screenshots at edge positions and representative themes/scales.

## Workspaces, Files, and shared storage

### Workspace and file lifecycle

**Status:** **Needs Verification**

**Current implementation:** `data/storage/WorkspacePaths.kt`, `PhoneStorage.kt`, `WorkspacesProvider.kt`, `data/workspace/WorkspaceLibraryCoordinator.kt`, `WorkspaceSessionCoordinator.kt`, `WorkspaceLifecycleCoordinator.kt`, `WorkspaceFileSource.kt`, and `data/files/` implement app-private workspaces, Private/Shared browsing, imports/transfers, DocumentsProvider, file operations, trash/history, archives, views, favorites/recents, and storage analysis. `data/runtime/GuestPaths.kt` and `RuntimeLayout.kt` expose authorized shared storage to the guest.

**Remaining scope:** Complete full-lifecycle device acceptance: create/clone/import; Shared-to-Private and Private-to-Shared copy/move; repository round-trip preserving Git links and executable bits; Shared rename/delete; permission off/on; multi-select and conflict choices; trash/restore/undo/redo; batch rename; archive formats/passwords/path traversal; categories/largest/duplicates; thumbnails/favorites/recent; Shared-trash same-volume behavior and cross-volume fallback; workspace rename/delete refusal while in use.

**Verification evidence needed:** Android Files app browse/open/edit/save plus provider path/symlink escape tests; external edits reaching clean and dirty documents; representative third-party file manager; import cancellation with no partial workspace; Git repair; uninstall keep-data prompt; Clear storage warning; Repair/Reset preserving workspaces; process death, missing files, low storage, permission revocation, and shared-storage device matrices.

## Navigation and presentation acceptance

### Floating dock and navigation

**Status:** **Needs Verification**

**Current implementation:** `navigation/Route.kt`, `ui/NavigationState.kt`, navigation saving/model code, and `ui/dock/ToolDock.kt` provide six workspace tool histories, floating-pill placement, badges/suggestions, switching, timers, and sheet visibility. Pills remain composed but hidden while a sheet is open, preserve their open/rest state, pause visible-duration timers with the remaining time, and let a pill tapped to open a sheet finish its short rest delay while hidden.

**Remaining scope:** Complete physical-device acceptance for all-edge placement, gesture exclusion, keyboard interaction, animation scale, Back/history, first-frame sheet hiding, sheet-route behavior, and state restoration.

**Verification evidence needed:** Phone checks at bottom/left/right placements and near system gestures/IME; sheet open/close first frames; TalkBack and reduced-motion checks; process recreation and tool scroll/history restoration.

### Home and launcher

**Status:** **Needs Verification**

**Current implementation:** `ui/screens/entry/LauncherScreen.kt`, `navigation/Route.kt`, and home terminal/assistant routing provide Start, Recent, runtime state, notices, and no-workspace terminal/assistant entry.

**Remaining scope:** Validate zero/many/loading workspaces, healthy/missing/runtime-preparation states, recent workspace metadata, terminal and assistant with and without an open workspace, unread notices, process recreation, and accessibility. Decide and address terminal “Copy all output” wrapped-line joining and root prompt `$` versus Ubuntu `#` only if current behavior fails the product contract.

**Verification evidence needed:** Dark/Light/AMOLED screenshots and interaction checks across empty/loading/populated states; no-workspace and open-workspace combinations; runtime absent/failure/retry; TalkBack order/labels; process-death and notification-arrival routing.

### Theme variants and appearance

**Status:** **Needs Verification**

**Current implementation:** `designsystem/IcodeTheme.kt`, `designsystem/palettes/`, theme-variant domain/settings code, and `data/settings/AndroidWorkspacePreferencesRepository.kt` implement families/variants, system/dark/light/AMOLED behavior, accent, UI scale, compact layout, borders, and editor/terminal propagation.

**Remaining scope:** Validate update retention, every theme across app/editor/terminal/diff/Markdown/icons/system bars, remembered variants and AMOLED twin, Light editor readability, borders/compact/custom accent, restart, live system day/night, contrast, and accessibility selection state.

**Verification evidence needed:** Physical-device screenshot matrix at supported scales and system modes; contrast measurements for semantic states; restart/update persistence; TalkBack state and touch-target checks.

### Notices and arrivals

**Status:** **Needs Verification**

**Current implementation:** `domain/notices/`, `data/notices/NoticeBoard.kt`, `NoticeStore.kt`, `data/notify/`, and manifest receivers/services implement typed notices, persistence, in-app surfaces, Android channels, dismissal, folding/deduplication, and notification arrivals. Momentary confirmations are not held for later ringing; notices already visible on the Notifications page do not ring after leaving it; kept notices arriving under a sheet use the remaining visible wake time.

**Remaining scope:** Validate delete/undo page behavior, pull completion while away, workspace/action routing after a workspace switch, swipe dismissal without repost, runtime failure deduplication, notification permission/channel combinations, and credential-safe text.

**Verification evidence needed:** Device tests for foreground/background cases, permission denied/granted, process-started receivers, notification taps after workspace changes, sheet timing, and log/output redaction.

### Loading, command palette, and route capture

**Status:** **Needs Verification**

**Current implementation:** `data/workspace/ActionTracker.kt` and domain loading state provide common busy semantics; `navigation/Route.kt` and command-palette UI expose current destinations/actions; debug capture code and `tools/capture-figma-screens.sh` render route fixtures.

**Remaining scope:** Validate that actions publish busy state at the tap, reject duplicate work, keep questions open until completion, and never show stale/empty success while loading. Validate command search/execution, keyboard behavior, disabled reasons, arrival routes, and the current route capture count derived from `Route.kt` rather than old numeric claims.

**Verification evidence needed:** State tests for duplicate/cancel/failure/retry and stale results; phone checks for every long action and command-palette path; regenerate the current route capture and review all frames without using captures as implementation evidence.

## Website and server work

### Website, release metadata, device info, notices, and MCP client metadata

**Status:** **Needs Verification**

**Current implementation:** Android client code under `data/web/` is disabled when `BuildConfig.ICODE_WEB_URL` is empty. `.github/workflows/release.yml` can register release metadata when website URL/token configuration exists. The website and server are owned by the separate `itzmejanak/iCode-Web` repository and are outside this repository's implementation authority.

**Remaining scope:** In the owning repository, verify deployment of product/download/documentation pages and app configuration, release/update, notice, and opt-in device-info contracts. Serve `/oauth/mcp-client.json` with the correct stable/debug reverse-domain redirect URIs and HTTPS/cache behavior. Verify staging, preview, beta, and stable channel separation. Do not make the local editing path depend on these services.

**Verification evidence needed:** Linked iCode-Web revision and deployment; staging/production contract tests; consent/default-off/disable/delete/kill-switch/offline-cache behavior; release-channel and checksum end-to-end tests; MCP client-metadata contents and browser sign-in; Android builds with configured and empty `icodeWebUrl`. Until the external source and deployment are inspected, server behavior remains **Needs Verification**.

## Build, release, documentation, and compliance

### Code quality and licence-compliance automation

**Status:** **Partially Done**

**Current implementation:** Root `LICENSE` and `THIRD_PARTY_NOTICES.md` exist. `docs/editor-grammar-provenance.md` records grammar sources. `.github/workflows/ci.yml` runs debug/release unit tests and a debug build; `codeql.yml` provides static security analysis; `release.yml` builds, signs, verifies, hashes, and publishes channel-specific ARM64 APKs. `tools/check-backend-consistency.sh`, `check-ui-consistency.sh`, `runtime-lab.sh`, and Gradle's native alignment checks exist.

**Remaining scope:** Complete and automate the release and compliance gaps described by [Build and release](build-and-release.md#roadmap):

- **Not Started:** add an independent pre-publication release quality gate that proves the tagged commit passed the required unit, lint, consistency, release-family, security, and device checks.
- **Partially Done:** expand CI across required variants, lint, consistency guards, device coverage, dependency review, lock verification, SBOM generation, and native/runtime pin updates.
- **Needs Verification:** provision CMake 3.22.1 explicitly in CI, security analysis, and release jobs, then record reproducibility evidence instead of relying on hosted-runner state.
- **Not Started:** add applicable native C/C++ security analysis.
- **Not Started:** add an unconditional cleanup trap for the decoded release keystore so every failure path removes it before the ephemeral runner exits.
- **Not Started:** automate licence, notice, source-offer, relinking-material, inventory, checksum, and immutable-URL verification across Gradle dependencies, submodules, vendored sources/binaries/assets, grammars, and runtime downloads.

Establish the exact distribution obligations for PRoot GPL, talloc/Sora LGPL, Termux-derived code, RaTeX and other binaries, fonts/themes/grammars, runtime downloads, and any owner-hosted SDK/NDK artifacts. Package every required notice and accompanying material.

**Verification evidence needed:** Maintainer/legal disposition for every shipped or downloaded component; generated inventory with version, source, digest, licence, notice, and distribution treatment; signed minified APK inspection proving About/Licences and required materials survive shrinking; CI failure fixtures for missing metadata/notices; reproducible release record; current resource/process/thermal/soak audit and physical-device gate published separately from JVM success.

## Updating this roadmap

- Add an item when required product scope lacks implementation, evidence, a decision, or an external dependency.
- Update the current implementation from source, never from a historical completion note.
- Move **Not Started** to **Partially Done** only when production implementation exists.
- Move **Partially Done** to **Needs Verification** only when the required implementation scope exists and only evidence remains.
- Move an item to **Done** only when its stated evidence exists; then remove it once no public dependency needs the record.
- Keep completed chronology in release notes or version history, not here.
