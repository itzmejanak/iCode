# iCode product

## Purpose

This document defines the intended product and reports the current capability status of the Android repository. The implementation references are ownership evidence, not an exhaustive file list. [roadmap.md](roadmap.md) owns every unfinished item.

## Vision

Give developers a native, local-first development environment on Android where one real workspace is shared by the editor, Git, search, language services, the terminal, user-installed tools, and the built-in assistant. The product should preserve normal development concepts without embedding a desktop IDE or requiring an iCode account.

## Audience

- **Mobile-primary developers** who do substantial work on an Android device.
- **Developers away from a workstation** who need to inspect, fix, build, commit, or synchronize a repository.
- **Terminal-oriented developers** who want a persistent Linux home and ordinary package managers alongside native editing and Git views.
- **AI-assisted developers** who want local files and commands exposed through explicit, reviewable tool actions and their chosen model provider.
- **Contributors and maintainers** who need clear ownership, reproducible builds, and honest release evidence.

## Product principles

1. **One workspace copy.** A managed workspace is one app-private directory. Editor, runtime, Git, search, LSP, Files integration, and assistant operations resolve that same tree.
2. **Native mobile interface.** Compose and native Android views own the product UI. Mobile interaction is not a browser-hosted desktop UI.
3. **Local first.** Opening, editing, local Git, search, language services, and terminal execution require no iCode account or hosted workspace.
4. **User-selected tools.** PRoot provides a familiar Linux userspace. Compatible CLI tools and language servers are not restricted to a product marketplace.
5. **Existing tools remain authoritative.** Git owns repository state, ripgrep owns project search, LSP owns language intelligence, and Android owns permissions and package installation.
6. **Explicit boundaries.** Destructive operations, external changes, credential use, provider transmission, phone control, and runtime reset must state their effects.
7. **Evidence before status.** Source proves implementation. Device, provider, network, performance, and release behavior remain **Needs Verification** until their required matrices exist.
8. **Progressive disclosure.** The workspace stays focused on editing while settings and advanced details use list-and-detail navigation.

## Supported platform

| Constraint | Current contract | Implementation |
|---|---|---|
| Android | Android 11 or newer, API 30 minimum; compile and target API 36. | `app/build.gradle.kts` sets `minSdk = 30`, `compileSdk = 36`, and `targetSdk = 36`. |
| CPU architecture | `arm64-v8a` only. | `app/build.gradle.kts` sets the sole ABI filter; `app/src/main/cpp/CMakeLists.txt` and `app/src/main/assets/runtime/manifest.json` enforce ARM64. |
| Runtime | Ubuntu 24.04.5 LTS ARM64 under PRoot 5.1.107.92, without Android root. | Pins and checksums live in `app/src/main/assets/runtime/manifest.json`; installation and launch are owned by `data/runtime/RuntimeInstaller.kt` and `data/runtime/RuntimeLayout.kt`. |
| Workspaces | Managed workspaces live under the app's private `files/workspaces` directory and appear to guests under `/workspace`. | `data/storage/WorkspacePaths.kt`, `data/workspace/WorkspaceLibraryCoordinator.kt`, and `data/runtime/RuntimeLayout.kt`. |
| Product identity | Local-first and no iCode account. | No account subsystem exists. `data/web/WebCoordinator.kt` is optional, and `BuildConfig.ICODE_WEB_URL` defaults to empty in `app/build.gradle.kts`. |
| Build language | Native Android app using Kotlin, Compose, Java 17, and bundled native code. | `settings.gradle.kts` includes only `:app`; `app/build.gradle.kts` owns the Android, Kotlin, Compose, CMake, and dependency configuration. |

A device outside these Android or ABI boundaries is unsupported. Emulator support is limited by the same ARM64 requirement.

## Capability scope and status

The status labels are defined in [README.md](README.md). The “final acceptance” column preserves intended scope even when current implementation is incomplete.

| Logical area | Status | Current implementation and ownership | Final acceptance |
|---|---|---|---|
| Entry, launcher, and local-first use | **Needs Verification** | Native launcher, recent workspaces, Private/Shared entry, notifications, runtime tools, home terminal, and home assistant are routed in `navigation/Route.kt` and composed by `ui/IcodeApp.kt`; website integration is optional in `data/web/WebCoordinator.kt`. | A new or returning user can begin without an account or provider, sees truthful preparation state, and resumes the requested action after runtime setup. Phone, process-recreation, loading, and accessibility checks must pass. |
| Managed workspaces and files | **Needs Verification** | App-private workspace lifecycle is owned by `data/workspace/WorkspaceLibraryCoordinator.kt`, `WorkspaceSessionCoordinator.kt`, `WorkspaceLifecycleCoordinator.kt`, and `data/files/FileOperationsCoordinator.kt`. `data/storage/WorkspacesProvider.kt` exposes workspace content through Android's Storage Access Framework. | Create, clone, import, rename, delete, restore, move, copy, archive, inspect, and safely share workspaces/files without hidden mirrors or path escape. Files-app, shared-storage, permission-loss, interruption, and process-death matrices must pass. |
| SD-card and USB explorer places | **Not Started** | The explorer supports primary shared storage only; it has no separate removable-storage places. | Define discovery, identity, permission, mount-removal, read-only, cross-volume, confinement, persistence, and device behavior for intended secondary storage. |
| Editor and document safety | **Needs Verification** | Sora sessions live in `ui/editor/CodeEditorSessions.kt`; document state, atomic fingerprint-checked writes, external-change handling, autosave, and draft recovery are owned by `data/workspace/DocumentSessionCoordinator.kt`, `WorkspaceFileSource.kt`, and `DocumentDraftStore.kt`. | Multiple tabs, undo/redo, keyboard and touch editing, encoding/line-ending preservation, dirty recovery, conflict-safe save, and state restoration work on supported devices and scales. |
| Syntax highlighting and Markdown | **Needs Verification** | TextMate/Oniguruma syntax is owned by `ui/syntax/SyntaxEngine.kt` using `app/src/main/assets/textmate/catalog.json`; Markdown parsing/rendering is under `domain/markdown/` and `ui/markdown/`. | Offline grammar coloring and native Markdown source/preview remain correct under large-file, theme, release-shrinking, accessibility, and physical-device checks. |
| Quick Open and project search | **Done** | `data/search/SearchCoordinator.kt` and `RipgrepService.kt` provide bounded, cancellable ripgrep file indexing and content search. | Users can find files and content, use case/whole-word/regex controls, navigate to exact results, and receive truthful limits and regex failures. |
| In-file find and replace | **Needs Verification** | `ui/screens/tools/EditorFindScreen.kt` operates on the active Sora document with previous/next, regex, case, whole-word, Replace, and Replace All. | Replacement is undoable, respects read-only state, affects only the active document, and passes touch, keyboard, focus, reveal, and device checks. |
| Project-wide replace | **Not Started** | No workspace replacement planner or writer exists; project search is read-only and `EditorFindScreen.kt` is document-scoped. | Preview every affected file and match, support exclusions, require confirmation, preserve document encoding and line endings, protect dirty/external changes, report partial failure, and refresh Git/editor/search state. |
| Problems and Output | **Partially Done** | LSP and Gradle diagnostics feed Problems through `data/language/LanguageServices.kt` and `domain/AndroidApps.kt`. `ui/screens/tools/OutputPanelScreen.kt` displays only the latest Git transfer transcript. | Problems aggregate current diagnostics. Output provides bounded, sanitized, selectable channels for Git, runtime/tools, search, builds, and language services. Output remains Git-only today. |
| Git and GPG | **Partially Done** | CLI Git, diffs, staging, commits, branches, history, conflicts, remotes, transfers, HTTPS/SSH credentials, and signing are split across `data/git/`; GPG guest and bridge ownership is in `data/runtime/GuestGpg.kt` and `GpgBridge.kt`. | Persist selected nested-repository choice, present ignored paths, provide the required wide side-by-side diff, and support first push through a selected non-`origin` remote. Then native Git workflows must agree with Git on unusual paths and failure states, and real HTTPS/SSH remotes, interruption, conflicts, branch operations, provider verification, GPG prompting/cache, and secret handling must pass phone/network checks. |
| Ubuntu runtime and terminal | **Needs Verification** | Runtime installation/execution is under `data/runtime/`; real PTYs and terminal sessions are owned by `TerminalSessionManager.kt` and `TerminalCoordinator.kt`; `RuntimeForegroundService.kt` represents eligible background work. | Runtime setup, Repair, Reset, packages, shells, PTY interaction, long-running work, device-vendor behavior, low-storage/memory cases, and truthful lost-process reporting pass the supported-device matrix. |
| Language services and completion | **Needs Verification** | `data/language/LanguageServices.kt` owns generic JSON-RPC LSP configuration, process lifecycle, document synchronization, diagnostics, requests, semantic tokens, and assistant tools. Completion UI is in `ui/editor/EditorCompletion.kt` and `EditorCompletionWindow.kt`. | Compatible user-installed servers expose only negotiated capabilities; diagnostics, navigation, formatting, rename, code actions, completion, crash/restart, semantic refresh, and phone interaction pass the language/device matrix. |
| Existing Android project Build and Run | **Needs Verification** | Runtime tool setup is in `data/android/AndroidSdkSetup.kt`, `CmakeSetup.kt`, and `AndroidNdkSetup.kt`; discovery/build/install/launch is in `AndroidProjects.kt`, `AndroidApps.kt`, `ApkInstaller.kt`, and `data/run/RunCoordinator.kt`. | A clean supported phone installs the pinned tools, builds representative AGP/SDK/NDK projects, asks Android to install, launches, rebuilds after Repair, and reports resource use and failures. |
| New Android app template | **Not Started** | No production template generator, creation action, template assets, or pinned Gradle wrapper exists. `RunCoordinator.kt` handles existing projects only. | Create an Empty Activity Compose project with a verified AGP/Kotlin pair, pinned wrapper and checksum, then hand it to the existing Build and Run flow. |
| Built-in assistant | **Needs Verification** | `data/assistant/AssistantCoordinator.kt`, `AssistantToolbox`, app-owned permission rules, JSON chat storage, and `data/assistant/koog/KoogAssistantEngine.kt` implement providers, chats, tools, questions, attachments, background work, and commit-message generation. | Provider behavior, streaming and non-streaming models, context handling, retries, approvals, cancellation, process death, attachments, and secret/privacy rules pass provider, network, and device checks. |
| MCP servers | **Partially Done** | `data/assistant/mcp/` implements configured and workspace servers, trust, local stdio, remote HTTP/SSE, OAuth return, tools, prompts/resources, questions, registry discovery, and idle shutdown using the SDK pinned in `app/build.gradle.kts`. | Existing protocol-era servers pass phone tests. Modern protocol support is adopted through the official SDK when available, without a second client implementation. |
| External coding agents (ACP) | **Needs Verification** | `data/assistant/acp/` connects the Assistant page to Claude Agent, Codex, Gemini CLI, OpenCode, Cline, and Factory Droid through ACP, installed as runtime tools from `assets/agents/registry.json`; see [Assistant and MCP](assistant-and-mcp.md#external-coding-agents-acp). | Each listed agent installs from its pinned source, signs in, and works through the existing Assistant page on supported phones, with its permission requests, questions, slash commands, config options, session reload, and the terminal fallback. Other ACP agents use the same path. |
| Voice | **Partially Done** | `data/voice/VoiceCoordinator.kt` and `data/voice/speech/` implement Android and configured network listeners/speakers, shared encrypted provider storage, microphone/player ownership, and no-silent-fallback behavior. | Provider/device checks pass. Azure and ElevenLabs speaking remain supported, but Azure listening and ElevenLabs listening are not implemented and remain in scope. |
| Phone control | **Partially Done** | `data/phone/PhoneService.kt`, `PhoneCoordinator.kt`, `AndroidPhoneControl.kt`, `PhoneForegroundService.kt`, and `data/phone/shizuku/` implement opt-in AccessibilityService control, bubble/glow UI, screen actions, hand-over, and optional Shizuku assistance. | Complete coordinate-action and Shizuku-fallback safety, intended Android 14 capture behavior, and actionable screenshot failures. Then safety, privacy, restricted-settings onboarding, stale-node refusal, secure views, Stop behavior, API/OEM differences, permission revocation, and background behavior must pass real-device tests. |
| Navigation, home, dock, themes, notices | **Needs Verification** | Routes and histories are in `navigation/` and `ui/NavigationState.kt`; the dock is `ui/dock/ToolDock.kt`; launcher is `ui/screens/entry/LauncherScreen.kt`; themes are under `designsystem/`; notices are under `domain/notices/` and `data/notices/`. | Compact-phone behavior, all themes/scales, timers, sheets, arrival routes, TalkBack, process recreation, and visual capture checks pass. |
| Adaptive tablet and landscape UI | **Not Started** | Production source has no window-width-class owner or compact/medium/expanded shell. `MainActivity.kt` handling configuration changes is not adaptive layout. | Implement width-class-driven navigation, rails/sidebars, persistent and resizable panes, landscape keyboard behavior, fold/multi-window transitions, state continuity, and accessibility. |
| Build, release, and compliance | **Partially Done** | Build types and package constraints are in `app/build.gradle.kts`; CI, CodeQL, and release workflows are in `.github/workflows/`; `LICENSE`, `THIRD_PARTY_NOTICES.md`, and `docs/editor-grammar-provenance.md` exist. | Release checks cover required static, unit, integration, artifact, notice/source-offer, dependency inventory, and physical-device evidence. Current automation does not establish all of these. |
| Public website and server contracts | **Needs Verification** | Android integration is isolated in `data/web/` and disabled when `BuildConfig.ICODE_WEB_URL` is empty. The server implementation is in a different repository. | Product/download/release/config/notices/telemetry and MCP client-metadata endpoints are verified in their owning repository and end to end. The local editing path remains usable when the website is absent. |

## Capability boundaries

These limits are part of the current product behavior:

- Workspaces are app-private. Android **Clear storage** can remove them, the runtime, settings, drafts, and chats. `android:hasFragileUserData="true"` in `app/src/main/AndroidManifest.xml` asks about retaining data during uninstall but does not protect data from Clear storage.
- Shared phone storage is separate from managed workspaces. It becomes usable by the explorer and guest processes only after Android grants All Files Access. The permission is app-wide, not a PRoot sandbox control.
- Every guest process can see all managed workspaces under `/workspace`; a workspace association chooses a working directory and lifecycle owner, not an access boundary.
- PRoot supplies a userspace and path translation. Guest `root` is not Android root, and PRoot is not a security boundary.
- Terminal, managed, LSP, Git, search, and local MCP processes end when Android kills the iCode process. The app can report lost work but cannot restore a live process, PTY, or protocol connection.
- iCode sets no global process-count cap. Android memory, process pressure, and device-vendor background policy remain the effective limits.
- The editor rejects binary or undecodable files and text files larger than 5 MiB through `data/workspace/WorkspaceFileSource.kt`.
- Syntax highlighting works without LSP. LSP requires a compatible server installed or configured by the user.
- Output is currently Git-only. Terminal output stays with terminal/process surfaces, and LSP logs stay with language-service details.
- Build and Run discovers existing Android application modules. It does not create a new Android project.
- Network access is needed for the initial runtime download, package/tool installation, remote Git, cloud model or voice providers, remote MCP, and optional website features.
- Assistant, voice, MCP, and phone-control data leaves the device only through user-selected operations and providers, subject to the canonical [Privacy policy](../PRIVACY.md).
- Android HTTP clients reject cleartext except for `localhost` and `127.0.0.1` through `app/src/main/res/xml/network_security_config.xml`. Guest commands, including Git and user-installed tools, are separate Linux processes and follow their own URL and transport configuration.
- Development-server URLs open in the user's external Android browser. iCode does not contain a project-preview browser.

## Non-goals

The intended product does not include:

- a VS Code, Code OSS, Monaco, or browser-rendered IDE distribution;
- VS Code extensions, `.vsix` packages, an extension host, or an extension marketplace;
- mandatory iCode accounts, hosted workspaces, synchronization, or remote execution;
- Android root access or access to other apps' protected private data;
- editing shared-storage folders as managed workspaces in place or silently mirroring them;
- a proprietary developer-tool package format, or a marketplace of CLIs or agents (the coding agents iCode lists are a short list verified on its runtime and pinned in the ACP registry's own format, not a store);
- per-command policy for commands the user launches in the terminal;
- an internal browser, preview proxy, or port-forwarding dashboard;
- bundled universal language servers or support guarantees for every third-party server/tool;
- automatic action on passwords, payments, CAPTCHAs, Android permission dialogs, or Accessibility settings;
- a promise that Android foreground services defeat OEM suspension or process killing.

## Acceptance rules

A capability is accepted only within an explicit boundary:

1. **Repository acceptance** requires implementation ownership, focused tests, no competing state/write path, and successful applicable builds and static checks.
2. **Device acceptance** requires the stated Android/API/ABI/OEM, orientation, keyboard, accessibility, lifecycle, storage, and resource-pressure matrix.
3. **Network/provider acceptance** requires real success and failure cases without exposing credentials or silently changing provider.
4. **Release acceptance** requires inspection of the signed, minified artifact and its notices, checksums, ABI, API floor, update channel, and installation behavior.
5. **External-service acceptance** requires evidence from the repository that owns the service plus an end-to-end Android test. Android client code alone is not server evidence.
6. A UI route, fixture, plan, or unit test does not by itself prove device behavior. Missing evidence is **Needs Verification**, not **Done**.
7. The final product scope remains required even when the current status is **Partially Done** or **Not Started**. Any scope removal must change this product contract and the roadmap deliberately.
