# Native Mobile Development Workbench — Product Requirements

**Version:** 1.0  
**Status:** Product contract  
**Product name:** iCode  
**Primary platform:** Android phones and tablets  

## 1. Document Purpose

This document defines the product problem, vision, users, required capabilities, quality expectations, constraints, and success criteria for a native mobile development workbench.

It defines what the product must accomplish without prescribing delivery order. Technical structure is defined in `architecture.md`, interaction and responsive layout rules are defined in `layout.md`, and observable user behavior is defined in `user-stories.md`. Implementation sequencing belongs in a separate dependency-aware plan.

## 2. Product Summary

The product is a native Android development environment for working directly with real directories and repositories on a mobile device. It combines a native code editor, filesystem explorer, Git workflow, workspace search, language intelligence, terminal, and persistent Linux userspace.

Developers retain control of their tools. They may install any compatible compiler, interpreter, language server, CLI, automation tool, or AI agent inside the Linux runtime. The platform does not maintain a provider catalogue or require tool-specific integrations.

The application is designed as a mobile product rather than a desktop interface reduced to fit a phone.

## 3. Problem Statement

Existing mobile development environments commonly suffer from one or more of the following limitations:

- desktop-oriented interfaces are embedded inside WebViews and remain difficult to use with touch;
- projects sit where Linux tools cannot work on them — shared storage refuses links and executable bits, folds letter case, and runs many-file operations more than a hundred times slower — or are silently mirrored and synchronised, so two copies drift;
- editor, terminal, Git, preview, and agent functions are fragmented into unrelated screens;
- the application restricts users to a curated list of languages, tools, agents, or providers;
- language intelligence and source control are incomplete;
- configuration is concentrated into long, crowded pages that cannot scale;
- initial onboarding forces tool or account decisions before the user can open a directory;
- local development depends unnecessarily on accounts, hosted services, or proprietary backends;
- application updates or runtime repair may destroy installed tools or user state;
- broad claims of desktop compatibility hide Android, architecture, and native-binary limitations.

The product must remove these restrictions without replacing them with an overly complex security, packaging, or extension system.

## 4. Vision

> Give developers a capable, native, local-first workbench on Android where their projects live in a real Linux workspace that every tool reaches the same way, where they edit files in place, understand and control Git changes, run any compatible tool, and shape the environment to their workflow.

The product should feel intentionally designed for phones and tablets while preserving familiar development concepts such as workspaces, editor tabs, diagnostics, source control, terminals, language services, and project search.

## 5. Product Goals

### 5.1 Direct workspace ownership

A workspace is one folder in the application's storage, and the only copy of it. The user creates, clones or imports it, edits it in place, and reaches it from other applications through Android's Files app. The application never mirrors or synchronises it, never copies a folder in without the user's Import, and never deletes a workspace without the user's confirmed Delete.

### 5.2 Complete everyday development loop

The product must support the recurring loop of:

```text
Open → understand → edit → search → run → inspect changes → commit → synchronize
```

### 5.3 Native mobile interaction

The application must own its interface through native Android components, responsive hierarchy, touch-aware interactions, software-keyboard support, and hardware-keyboard shortcuts.

### 5.4 Tool freedom

The runtime must allow users to install and execute compatible command-line tools without the application recognizing each tool by name.

### 5.5 Strong Git awareness

Changed files, staged state, diffs, conflicts, branches, commits, and remote synchronization must be visible through native workflows rather than requiring the terminal for routine operations.

### 5.6 Language-neutral intelligence

The application must use generic language-service contracts so developers can connect compatible language servers without requiring a bespoke application release for every language.

### 5.7 Scalable product structure

Navigation and settings must grow through category, list, and detail hierarchies rather than adding root-level tabs for every capability.

### 5.8 Local independence

Editing, searching, Git operations, terminal execution, and language services must not require a permanent connection to a hosted backend.

### 5.9 Community-ready foundation

The source structure, contribution boundaries, tests, and documentation must let community contributors improve built-in features without understanding the entire codebase.

## 6. Non-Goals

The product is not intended to provide:

- a VS Code distribution or visual clone;
- VS Code extension compatibility;
- an application extension marketplace;
- a browser-hosted workbench;
- an internal project-preview browser;
- a curated AI-agent marketplace;
- a provider-specific agent configuration system;
- a new package manager for developer tools;
- a cloud workspace requirement;
- a mandatory account system;
- access to root-only Android paths on unrooted devices;
- access to other applications' protected private data;
- a per-command approval workflow;
- a replacement for the user's external Android browser;
- ForgeKit product architecture, packages, or domain concepts.

## 7. Target Users

### 7.1 Mobile-primary developer

A developer who performs meaningful coding work on an Android phone or tablet and requires more than a basic text editor.

Needs:

- reliable editing;
- project-wide navigation and search;
- Git visibility;
- terminal access;
- tool installation;
- state restoration after interruptions.

### 7.2 Developer away from a workstation

A developer who normally uses a desktop but needs to inspect, fix, test, or commit a repository while mobile.

Needs:

- familiar workspace concepts;
- fast repository access;
- accurate diffs and source-control state;
- hardware-keyboard support when available;
- confidence that changes affect the real repository.

### 7.3 Terminal-oriented power user

A developer comfortable with shell workflows who wants the application to provide native editing and Git context without limiting command-line freedom.

Needs:

- full PTY behavior;
- persistent runtime home;
- package managers;
- arbitrary CLI execution;
- multiple terminal sessions;
- no tool allowlist.

### 7.4 AI-assisted developer

A developer who installs an AI agent as a CLI or uses scripts that modify the workspace.

Needs:

- the agent and editor to operate on the same workspace files;
- external modifications to appear reliably;
- Git changes to reveal agent edits;
- freedom to choose and configure the agent independently.

### 7.5 Open-source contributor

A contributor improving the application itself.

Needs:

- clear module ownership;
- stable contracts;
- reproducible tests;
- documented architecture decisions;
- a manageable native codebase without a large upstream workbench fork.

## 8. Core User Jobs

Users hire the product to:

1. Open a real repository stored on the device.
2. Find and understand relevant code quickly.
3. Edit files safely with useful code intelligence.
4. run commands, builds, tests, servers, and automation tools.
5. See exactly what changed and why.
6. Prepare and commit a coherent change.
7. Pull and push repository updates.
8. Install the tools required by an unfamiliar project.
9. return to the same work after Android interrupts or closes the application.
10. use a phone, tablet, touch input, or hardware keyboard without switching products.

## 9. Product Principles

### 9.1 Open before configuring

The first meaningful action is opening, creating, or cloning a workspace. Account, provider, and agent setup must not block entry.

### 9.2 Show state, not assumptions

The product displays actual filesystem, Git, process, and language-server state. A clicked button is not treated as proof that an operation succeeded.

### 9.3 Preserve user control

The application explains destructive effects, external changes, permission limits, and repair consequences. It does not silently copy, overwrite, reset, or discard user data.

### 9.4 Prefer existing tools

Git performs Git operations, `ripgrep` performs project search, language servers provide code intelligence, and the user's browser opens web output. The application provides coherent native workflows around these tools.

### 9.5 Progressive disclosure

Frequently used actions stay close to the editor. Infrequent configuration appears in hierarchical detail pages. Advanced runtime information does not crowd the primary workspace.

### 9.6 Capability without curation

The platform provides execution, files, processes, environment variables, package managers, and protocols. Users decide which tools use those capabilities.

## 10. Functional Requirements

### 10.1 Application entry

| ID | Requirement |
|---|---|
| PR-ENTRY-01 | The application shall open on the recently opened workspaces with Import, Clone Repository and Workspaces; the Workspaces page shall present every workspace in its storage, with New Folder. |
| PR-ENTRY-02 | The application shall not require account, provider, agent, or language configuration before a workspace can be opened. |
| PR-ENTRY-03 | Initial runtime installation shall show truthful progress, failure, retry, and storage information. |
| PR-ENTRY-04 | Returning users shall be able to reopen the last or a recent workspace. |

### 10.2 Storage and workspaces

| ID | Requirement |
|---|---|
| PR-WS-01 | Workspaces shall live in the application's own storage and need no storage permission; All Files Access is asked only where phone storage is read (Import), never at onboarding. |
| PR-WS-02 | A folder from phone storage shall come in only by the user's Import — chosen in the application's own browser of phone storage, and copied once, never writing the original — with a repository's executable bits and links restored from Git's own record. |
| PR-WS-03 | New Folder, Clone and Import shall name the workspace; it is created at `/workspace/<name>`, and a taken name is refused inline. |
| PR-WS-04 | Rename and Delete shall act on a whole workspace, refused while a terminal session or task runs in it; Delete shall be confirmed and cannot be undone. |
| PR-WS-05 | Other applications shall reach workspaces through the application's document provider (Android's Files app and pickers), never through a link that leads out of the workspaces. |
| PR-WS-06 | The runtime, editor, Git service, search service, language services and document provider shall resolve the same workspace contents through one path model. |
| PR-WS-07 | Uninstalling shall offer to keep the application's data. App info keeps Android's Clear storage (owner, 2026-09-30). |

### 10.3 Files and Explorer

| ID | Requirement |
|---|---|
| PR-FILE-01 | The Explorer shall display a lazily loaded directory hierarchy. |
| PR-FILE-02 | Users shall be able to create, rename, move, copy, and delete files and directories. |
| PR-FILE-03 | Consequential file operations shall identify the exact target and request confirmation where recovery is uncertain. |
| PR-FILE-04 | The Explorer shall refresh after native, terminal, Git, or external application changes. |
| PR-FILE-05 | File rows shall communicate Git and diagnostic state without requiring the file to be opened. |
| PR-FILE-06 | Binary, unsupported, and oversized files shall open through an appropriate safe state instead of corrupting content. |

### 10.4 Editing

| ID | Requirement |
|---|---|
| PR-EDIT-01 | The editor shall support multiple open documents and visible dirty state. |
| PR-EDIT-02 | The editor shall support save, configurable autosave, undo, redo, selection, indentation, folding, bracket matching, line numbers, and word wrapping. |
| PR-EDIT-03 | The editor shall preserve supported encodings and line endings across normal edits. |
| PR-EDIT-04 | The editor shall provide file-level find and replace, go to line, and previous/next match navigation. |
| PR-EDIT-05 | Syntax highlighting shall function independently of a language server. |
| PR-EDIT-06 | Dirty documents shall have recoverable text after application interruption. |
| PR-EDIT-07 | External modifications shall produce reload, compare, or explicit-overwrite choices when local unsaved text exists. |
| PR-EDIT-08 | Cursor, selection, scroll, folds, tab ordering, and selected document shall be restorable per workspace. |
| PR-EDIT-09 | Editor actions shall work with software and hardware keyboards. |
| PR-EDIT-10 | The editor shall render Git gutter state and language diagnostics without coupling those services to the renderer implementation. |

### 10.5 Search

| ID | Requirement |
|---|---|
| PR-SEARCH-01 | Quick Open shall find workspace files by name and path. |
| PR-SEARCH-02 | Search in Files shall search workspace content with case, regular-expression, include, and exclude controls. |
| PR-SEARCH-03 | Search results shall be grouped by file and open the exact matching location. |
| PR-SEARCH-04 | Search operations shall be cancellable and stream results without blocking the interface. |
| PR-SEARCH-05 | Replace operations shall preview scope and require explicit confirmation for broad replacement. |

### 10.6 Source control

| ID | Requirement |
|---|---|
| PR-GIT-01 | The application shall detect whether the workspace or a relevant parent is a Git repository. |
| PR-GIT-02 | Source Control shall distinguish staged, unstaged, untracked, renamed, deleted, ignored, and conflicted paths. |
| PR-GIT-03 | Explorer and open-document surfaces shall reflect repository change state. |
| PR-GIT-04 | Users shall be able to inspect staged and unstaged diffs. |
| PR-GIT-05 | Diff presentation shall support phone-friendly inline rendering and wider side-by-side rendering. |
| PR-GIT-06 | Users shall be able to stage, unstage, discard, and restore eligible files with accurate confirmation. |
| PR-GIT-07 | Users shall be able to enter a commit message and create a commit. |
| PR-GIT-08 | Users shall be able to see, create, and switch branches. |
| PR-GIT-09 | Users shall be able to fetch, pull, and push using runtime-managed Git credentials. |
| PR-GIT-10 | The application shall report ahead, behind, detached-head, and in-progress repository states where available. |
| PR-GIT-11 | Conflicted files shall be identifiable, editable, resolvable, and markable as resolved. |
| PR-GIT-12 | Git failures shall preserve repository state and show actionable command errors. |

### 10.7 Terminal and processes

| ID | Requirement |
|---|---|
| PR-TERM-01 | The terminal shall connect to a real PTY in the Linux runtime. |
| PR-TERM-02 | Users shall be able to create, name, switch, and close multiple terminal sessions. |
| PR-TERM-03 | New workspace terminals shall start in the mapped workspace directory. |
| PR-TERM-04 | The terminal shall support ANSI/VT rendering, scrollback, selection, copy, paste, resize, signals, and configurable extra keys. |
| PR-TERM-05 | Users shall be able to install and execute compatible tools through ordinary package managers. |
| PR-TERM-06 | The application shall not require tool-specific registration before a command can run. |
| PR-TERM-07 | Eligible long-running processes shall continue through an Android foreground service while the UI is backgrounded. |
| PR-TERM-08 | The application shall distinguish running, exited, failed, cancelled, and lost processes. |
| PR-TERM-09 | HTTP and HTTPS links shall open in the user's external browser. |

### 10.8 Language services

| ID | Requirement |
|---|---|
| PR-LANG-01 | The application shall implement a generic LSP client. |
| PR-LANG-02 | Users shall be able to configure a language service using file matching, command, arguments, environment, root markers, and initialization options. |
| PR-LANG-03 | A configured server shall expose only capabilities it reports. |
| PR-LANG-04 | Supported surfaces shall include completion, hover, signatures, definitions, references, symbols, rename, formatting, diagnostics, and code actions. |
| PR-LANG-05 | Language-server failure shall not prevent file editing. |
| PR-LANG-06 | Users shall be able to inspect server state and restart a failed server. |
| PR-LANG-07 | The product shall not require a compiled application adapter for each language server. |

### 10.9 Problems and diagnostics

| ID | Requirement |
|---|---|
| PR-PROB-01 | Diagnostics shall appear in the editor, Explorer, and Problems surface. |
| PR-PROB-02 | Problems shall group by file and severity and navigate to the affected range. |
| PR-PROB-03 | Stale diagnostics shall be removed after document, server, or workspace state invalidates them. |

### 10.10 Settings and personalization

| ID | Requirement |
|---|---|
| PR-SET-01 | Settings shall use category, list, and detail hierarchy. |
| PR-SET-02 | Settings shall support application and workspace scope where appropriate. |
| PR-SET-03 | The default visual system shall use the approved charcoal, ember, amber, green, Chakra Petch, and IBM Plex Mono direction. |
| PR-SET-04 | Theme architecture shall allow additional themes without changing feature code. |
| PR-SET-05 | Editor, terminal, Git, language, runtime, storage, appearance, and application settings shall remain separate categories. |
| PR-SET-06 | Settings requiring restart shall state the affected component before applying. |
| PR-SET-07 | Unknown future settings shall fit under registered categories without expanding root navigation. |

### 10.11 Persistence and recovery

| ID | Requirement |
|---|---|
| PR-STATE-01 | Normal application updates shall preserve settings, runtime home, installed tools, and workspace metadata. |
| PR-STATE-02 | Application restart shall restore the previous workspace and document session when possible. |
| PR-STATE-03 | Dirty-document recovery shall not silently overwrite a newer file on disk. |
| PR-STATE-04 | Runtime repair and reset shall be distinct operations with explicit consequences. |
| PR-STATE-05 | Removing the application shall not delete workspaces stored outside application-private storage. |

### 10.12 Runtime

| ID | Requirement |
|---|---|
| PR-RUN-01 | The application shall install and verify a compatible Ubuntu ARM64 PRoot runtime. |
| PR-RUN-02 | Runtime installation shall validate architecture, storage, manifest, and checksums. |
| PR-RUN-03 | One runtime coordinator shall own bootstrap, health, start, stop, update, and repair state. |
| PR-RUN-04 | The runtime home and user-installed tools shall persist across application restarts and normal updates. |
| PR-RUN-05 | Runtime unavailability shall not corrupt workspace files or recovered documents. |
| PR-RUN-06 | Diagnostics shall expose actionable runtime, storage, process, and version facts. |

### 10.13 Public website

| ID | Requirement |
|---|---|
| PR-WEB-01 | A Next.js website shall present product information, documentation, downloads, and release notes. |
| PR-WEB-02 | The website may publish signed application and runtime release metadata. |
| PR-WEB-03 | Local editing and execution shall not require website availability. |
| PR-WEB-04 | Hosted account, synchronization, and remote execution capabilities shall not be assumed by the native foundation. |

## 11. User Experience Requirements

### 11.1 Responsive behavior

The same information architecture serves compact phones, landscape devices, foldables, and tablets. Compact layouts use pushed pages, drawers, and one dominant editor surface. Wider layouts use persistent rails, sidebars, editor panes, and resizable bottom panels.

### 11.2 Hierarchy

Root navigation is intentionally small. Lists lead to details, and details may lead to focused sub-details. Settings, runtime facts, language services, Git changes, and workspace information must not be flattened into one page.

### 11.3 Feedback

Every long-running operation provides:

- visible state;
- progress where measurable;
- cancellation where safe;
- completion or failure result;
- a recovery or retry action when meaningful.

### 11.4 Destructive actions

Destructive confirmation identifies the target and consequence. Repetitive harmless actions do not receive unnecessary confirmations.

### 11.5 Empty states

Empty states explain why no content exists and provide one relevant next action. They do not fabricate sample repositories, fake diagnostics, or fake process activity.

## 12. Non-Functional Requirements

### 12.1 Reliability

- Workspace files must never be silently replaced by stale in-memory content.
- A reported Git operation success must correspond to a successful Git exit.
- Dirty text must be recoverable after normal process recreation.
- Partial runtime installation must not be treated as healthy.
- Application state must tolerate Android lifecycle recreation.

### 12.2 Performance

- The UI thread shall not perform filesystem enumeration, Git commands, project search, diff parsing, or language protocol work.
- Large directory trees shall load incrementally.
- Search and Git results shall stream or update incrementally.
- Language servers and expensive parsers shall start lazily.
- Large files shall trigger safe degradation rather than interface failure.
- Feature state shall avoid unbounded terminal, log, diagnostic, and search-result growth.

### 12.3 Accessibility

- Interactive controls shall meet Android touch-target expectations.
- Text and status shall not rely on color alone.
- Screen-reader labels and logical focus order shall be provided.
- Font scaling shall preserve access to primary actions.
- Hardware keyboard focus and shortcuts shall be predictable.
- Reduced motion preferences shall be respected.

### 12.4 Maintainability

- Feature ownership and dependency direction shall be documented.
- Parsers shall be tested against fixtures and unusual filenames.
- Runtime and protocol changes shall be versioned.
- Product state shall not be concentrated in one global view model.
- Native editor implementation details shall not leak across feature boundaries.

### 12.5 Privacy

- Workspace content stays local unless a user-invoked tool transmits it.
- No hosted account is required for local capabilities.
- Diagnostics export shall be user-initiated and sanitized where the application controls formatting.
- Any future telemetry shall be separable from core operation and documented independently.

## 13. Android and Distribution Constraints

- Workspaces live in app-private storage: other applications reach them only through the document provider, and they have no `/sdcard` path.
- Uninstalling deletes app-private storage unless the user keeps the application's data when Android asks.
- Protected Android directories and other applications' private data remain inaccessible without operating-system privileges.
- All Files Access (`MANAGE_EXTERNAL_STORAGE`) is requested for Import alone; a store listing must declare that use.
- Foreground processes require a visible Android notification.
- Device vendors may terminate long-running processes despite application lifecycle handling.
- Native dependencies and runtime binaries must support the device architecture.
- External removable storage can disappear while a workspace is open.

The product must communicate these constraints accurately rather than hide them behind generic errors.

## 14. Data Ownership and Removal

| Data | Owner | Removal behavior |
|---|---|---|
| External workspace | User | Remains after application removal |
| Repository `.git` data | User | Remains with the workspace |
| App-private runtime | User through application | Removed with application data |
| Installed runtime tools | User through runtime | Removed with application data or explicit runtime reset |
| Settings and recent workspaces | Application/user | Removed with application data |
| Recovery buffers | Application/user | Removed after save/discard or application data removal |
| Website release metadata | Project maintainers | Independent of local workspace data |

## 15. Success Measures

The product is successful when validation shows that users can reliably:

- open a real existing repository without duplication;
- find and edit code using touch or hardware keyboard;
- understand every changed file through native Git state and diffs;
- stage, commit, pull, and push without leaving the application;
- run builds, tests, servers, agents, and arbitrary scripts in the terminal;
- connect language intelligence without an application-specific integration;
- recover their workspace and unsaved work after interruption;
- open development-server URLs in their chosen browser;
- install tools that were not known when the application was released;
- contribute to the application without modifying unrelated feature modules.

Operational measures should track correctness and usability rather than vanity:

- successful workspace-open rate;
- save and recovery correctness;
- Git status/diff consistency with the CLI;
- runtime installation and health success;
- crash-free editing sessions;
- time from selecting a recent workspace to editable state;
- search and Git refresh responsiveness on representative repositories;
- language-server connection and restart reliability;
- unresolved data-loss or workspace-corruption incidents;
- accessibility and responsive-layout defects.

## 16. Key Risks and Product Responses

| Risk | Product response |
|---|---|
| Native editor library stagnates | Keep it behind a replaceable editor contract and own document state outside the renderer |
| Native workbench scope expands indefinitely | Center requirements on the everyday development loop and place specialized workflows behind later product decisions |
| Language servers vary widely | Use capability negotiation and generic configuration; do not promise unsupported server behavior |
| PRoot performance varies by device | Validate representative devices, start services lazily, and expose process/resource diagnostics |
| Git parsing breaks on unusual paths | Use porcelain and NUL-delimited formats with fixture coverage |
| External tools alter open files | Observe filesystem state and provide compare/reload decisions |
| Runtime repair removes tools | Separate repair from reset and state consequences before execution |
| Broad storage permission affects distribution | Keep distribution strategy explicit and explain permission purpose before system settings |
| Community additions crowd navigation | Enforce hierarchical route and settings registration rules |
| Users assume root access | Describe Android's real filesystem boundary in product copy and documentation |

## 17. Product Boundaries

The four foundation documents have distinct ownership:

| Document | Owns |
|---|---|
| `prd.md` | Product problem, users, requirements, quality, constraints, success |
| `architecture.md` | Components, contracts, data flow, persistence, runtime, dependency rules |
| `layout.md` | Information architecture, responsive regions, screens, components, interaction rules |
| `user-stories.md` | User intent and observable acceptance behavior |

Implementation phases, task steps, dependency groups, effort, and completion tracking are deliberately excluded from these documents.

## 18. Glossary

| Term | Product meaning |
|---|---|
| Workbench | Native environment combining editor, files, Git, search, terminal, and diagnostics |
| Workspace | One folder in the application's storage, opened as the active project root |
| Runtime | Ubuntu ARM64 userspace in which commands and developer tools execute |
| Language service | User-installed LSP-compatible process providing code intelligence |
| Source Control | Native presentation and control of Git repository state |
| Recovery buffer | App-private protection for unsaved document content |
| Import | The user's one-time copy of a phone-storage folder into a new workspace; the original is never written |
| External browser | Browser selected by Android to open an activated HTTP or HTTPS link |
