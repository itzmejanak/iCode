# Assistant and MCP

This document describes the assistant and Model Context Protocol (MCP) support that iCode currently ships. The [privacy policy](../PRIVACY.md) and [security policy](../SECURITY.md) are the policy sources for data handling and vulnerability reporting. Exact unfinished requirements and acceptance evidence live only in [Built-in assistant providers and tool lifecycle](roadmap.md#built-in-assistant-providers-and-tool-lifecycle), [Existing MCP transports, sign-in, and server acceptance](roadmap.md#existing-mcp-transports-sign-in-and-server-acceptance), [MCP modern protocol support](roadmap.md#mcp-modern-protocol-support), and [External coding agents through ACP](roadmap.md#external-coding-agents-through-acp).

## Status labels

This page uses the four canonical labels defined in the documentation index: [Done, Partially Done, Not Started, and Needs Verification](README.md#status-labels).

| Area | Status | Scope |
|---|---|---|
| Repository implementation of the built-in Koog assistant, providers, chats, four modes, native tools, approvals, retry, compaction, attachments, and commit messages | **Done** | The stated code paths and automated coverage exist. Real providers and Android lifecycle acceptance remain separate. |
| Repository implementation of local MCP, legacy remote MCP, workspace declarations, trust, secrets, sign-in, prompts, resources, elicitation, and tools | **Done** | The supported legacy protocol implementation and automated coverage exist. Real-server and device acceptance remain separate. |
| Modern MCP protocol compatibility | **Partially Done** | iCode uses the official Kotlin SDK's active legacy client. Support for newer protocol behavior remains blocked until that SDK's client implements it. |
| Real-provider assistant acceptance | **Needs Verification** | Provider, network, lifecycle, privacy, and process-death checks remain. |
| Existing MCP transport, sign-in, and server acceptance | **Needs Verification** | Local stdio, remote HTTP, browser sign-in, lifecycle, and real-server checks remain. |

## Architecture

The assistant has two layers:

1. iCode owns chat state, persistence, provider settings, instructions, tool definitions, permission checks, background coordination, and UI state.
2. Koog runs the model → tool call → tool result → model loop behind iCode's `AssistantEngine` interface.

Only `data/assistant/koog` imports Koog. The rest of the app uses iCode's domain models and interfaces. This keeps provider-library details out of the coordinator, tools, stores, and screens. iCode uses a small adjustment to Koog's standard graph so a response containing both text and tool calls executes the calls before treating the text as the final answer.

The engine reports streamed text where the provider path supports it, reasoning where the provider returns it, tool calls and results, and token use. Stopping the turn cancels the model request and the active tool path.

## Providers, models, and keys

**Status: Done**

iCode supports these assistant provider types:

- OpenAI
- Anthropic
- Google Gemini
- OpenRouter
- Ollama
- an OpenAI-compatible endpoint, including a user-entered server address and model ID

Each chat keeps the model it answers with, chosen from the chat's model sheet, where every provider is listed under its own mark. Choosing another model in a chat applies from that chat's next message: the new model receives the whole conversation so far, and a new chat starts with the model chosen last. Each reply is named, above it, by the model that wrote it, with its provider's mark. A chat whose model is no longer offered by its provider answers with the model a new chat starts with.

A provider can expose catalogued models or user-entered models. For a model that Koog does not know, iCode stores the limits and capabilities the user enters, such as context window, output limit, image input, and reasoning. Available reasoning choices depend on the provider and model.

Provider metadata, model choices, assistant mode, approval mode, and the commit-message model are stored in app-private preferences. Each provider API key is stored separately and encrypted with an Android Keystore-backed key. A key is decrypted only when iCode creates a provider connection. It is not returned in UI state or included in connection descriptions. If the device can no longer decrypt a saved key, iCode removes the unusable encrypted value and treats the key as missing.

This encryption claim applies to provider keys. It does not mean all assistant data is content-encrypted.

## Chats, places, and context

**Status: Done**

A chat belongs either to:

- a workspace, where relative paths start at that workspace; or
- no workspace, where the assistant starts from the Linux runtime user's home directory.

Chats are listed separately for each place. Several chats can work at once, in one place or in several; each chat answers one message at a time. A running turn remains tied to the place and chat where it began, even if the user views another workspace or chat. Each working chat shows its own step, questions, and Stop, the chats list marks it as working, and a workspace with a chat at work cannot be renamed, moved, or deleted until that chat stops. MCP servers serve every chat: a server's question shows in each chat at work, and local servers idle once the last chat ends.

A message sent while its chat works waits in the chat's queue, shown above the composer and saved with the chat. When the work ends, whether answered, stopped, or failed, the first waiting message is sent. Tapping a waiting message takes it back into the composer, with its files, to edit; × removes it. Send now sends it at once. While the same model works, the turn takes it after the step in hand, adds it to the chat, and goes on with it in mind. For a different model, the work stops and that message goes next with the new model. A chat not at work sends it straight away. Waiting messages kept when iCode was closed wait until the user sends one; those of a workspace being renamed, moved, or deleted are not sent.

### Local persistence

Chats are stored as app-private files. iCode writes each chat through a temporary file and atomic rename, and can rebuild a damaged chat index from the individual chat files. Image and PDF attachments are copied into the chat's private folder and move or disappear with that chat.

Local chat files are **app-private, but their contents are not separately encrypted**. Provider keys, MCP secrets, and MCP sign-in material have separate Keystore-backed encryption; ordinary chat text does not.

A chat keeps every message and every tool result in full, however long it grows. Each chat file holds one message a line: a change appends what is new and cuts back only what changed at its end, so a long chat is never written again, and a line half written when the process was killed is dropped when the chat is read. Only a model request is trimmed: it carries at most the newest 400 messages after the last compaction, starting at a user message. Chats an earlier build saved are read and kept in the new form from their next change.

Sharing a chat writes its whole transcript, as Markdown, to a file in iCode's cache and hands that file to Android's share sheet, so nothing is cut off. Only the latest shared transcript stays in the cache.

### What the model receives

A model request includes the retained conversation context used for that turn, not only the newest user message. This can include:

- user and assistant messages;
- tool names, arguments, calls, and results;
- the current system instructions and applicable project instructions;
- text from attachments selected for the message;
- selected images and PDFs when the model supports them; and
- MCP prompt, resource, and tool traffic that becomes part of the turn.

This full retained model context goes to the provider selected for the chat. Tool output may contain workspace or external-service data, so approving a tool can also cause its result to be sent to that provider on the next model request.

After compaction, the model receives the saved summary in place of the earlier messages, plus the messages after the compaction marker. The chat page can still show retained earlier entries even though the summary replaces them in later model requests.

## Modes

**Status: Done**

| Mode | Current behavior |
|---|---|
| **Code** | Reads and changes files, runs commands, and verifies work. |
| **Plan** | Reads and analyzes, then proposes a plan. Change and command tools are not offered. |
| **Ask** | Reads and answers questions. Change and command tools are not offered. |
| **Orchestrator** | Plans, implements, and verifies complex work step by step. |

Plan and Ask are tool-restricted reading modes, not merely different prompts. An MCP tool is offered in those modes only when the server marks it read-only. That annotation controls availability only; it is not trusted as an approval or safety decision.

## Native tools and their owners

**Status: Done**

The user interface and the assistant call the same typed data-layer owners. The assistant does not maintain a second implementation of these features.

| Capability | Shared owner or path |
|---|---|
| Workspace files | `WorkspaceFileSource` and workspace writers |
| Search | `WorkspaceSearchEngine` |
| Git reads | `CliGitService` |
| Run commands and Build & Run | `RunCoordinator` |
| Managed long-running processes | `RuntimeProcesses` |
| Terminal tabs and terminal commands | `TerminalSessions` and `TerminalRuns` |
| MCP configuration and connections | `McpServers` and `McpConnections` |
| Speech providers | `SpeechProviders` |
| Language services | `LanguageServices` |
| Phone control | `PhoneControl` |

`run_command` uses a real PTY-backed terminal tab. The assistant can use its chat's terminal, open a new assistant terminal, or use a user terminal only when that terminal is explicitly named. Shell integration marks identify command start, completion, and exit status. The terminal preserves working directory and exported variables between commands. A timeout sends Ctrl-C rather than destroying the terminal.

Use a managed process, rather than a terminal background job, for servers and watchers that need process status, ports, persistent output, and explicit stop controls.

Terminal rendering, interaction, user-tab access, and shell-integration acceptance are tracked only in the [assistant provider and tool lifecycle roadmap](roadmap.md#built-in-assistant-providers-and-tool-lifecycle).

## Approval modes and boundaries

**Status: Done**

| Approval mode | Runs without asking | Asks before |
|---|---|---|
| **Ask first** | Reads inside the workspace | Changes, commands, and anything outside the workspace |
| **Auto in workspace** | Reads, changes, and commands inside the workspace | Anything outside the workspace |
| **Full access** | Ordinary offered tools in any reachable place | Only operations explicitly marked to always ask |

A chat with no workspace has no workspace area to auto-approve. There, Ask first and Auto in workspace both ask before changes and commands. Full access still skips ordinary approvals.

The boundary includes more than files. iCode represents these as separate places:

- folders and other workspaces;
- apps on the phone;
- MCP servers; and
- terminal tabs owned by the user.

An approval can allow one call or open the relevant place for the current chat. For an outside path, iCode opens the other workspace, nearest Git repository, or containing folder. It never opens all of `/`. Per-chat grants are held in memory and are forgotten when iCode closes; changing the selected approval mode also clears the shown chat's temporary grants.

Some configuration changes outlast a chat. Saving or removing an MCP server or speech provider is marked to ask every time, including in Full access, and cannot be granted for the rest of the chat.

The assistant does **not** ask before every action. Full access intentionally grants wider access, Auto in workspace grants work inside the workspace, and a chat can remember a place grant. Structured questions from the model are separate from permission approvals and can appear in every approval mode.

### Reach is advisory, not an OS sandbox

The Linux runtime uses PRoot. PRoot is not an operating-system security sandbox. iCode can detect explicit path-like words in a shell command, including quoted paths and `cd`, but it cannot reliably infer paths hidden in variables, globs, scripts, or subprocess behavior. Ask first remains the safer command mode because every command asks regardless of inferred reach.

## Turn behavior

### Questions and pauses

**Status: Done**

The assistant can ask structured questions with selectable answers instead of guessing. A server can also ask an MCP elicitation question while one of its tools is running. Phone-control work can pause for a user hand-over. A turn has no step limit and never stops to ask whether to go on: it takes as many steps as the task needs, until the model answers, the user stops it, or it fails.

Approvals, pauses, and unanswered structured questions are live turn state. Completed messages, tool calls, and results are persisted, but a pending question is not restored as an active wait after process death.

### Background turns

**Status: Partially Done**

The turn runs in a process-scoped coroutine rather than a screen-owned job. iCode uses foreground work and notifications while work continues away from the assistant page, including notices when an answer is needed. Android can still kill the process; foreground execution improves survival but does not guarantee it. The canonical lifecycle acceptance scope is in the [assistant roadmap](roadmap.md#built-in-assistant-providers-and-tool-lifecycle).

### Retry

**Status: Done**

Transient provider connection, rate-limit, and overload failures use provider-independent retry rules with visible countdown state. A retry resumes from the chat as already persisted. Calls and results recorded before the failure are not deliberately repeated. Cancellation remains cancellation and does not become a retry.

### Compaction

**Status: Done**

The context meter uses the token count of the last request and the chosen model's context window. When the automatic threshold of 80% is reached, iCode asks the model for a continuation summary, saves a compaction marker, resets context accounting, and sends the summary plus later messages on following turns. The threshold is checked before each new message and, while a turn works, after each step's calls have run: the engine then hands the turn back, iCode compacts the chat and goes on from it by itself, sending the message being answered first so the model keeps its task. If the first request after such a compaction is still over the threshold, that turn is not compacted again. Manual compaction uses the same path.

### Attachments

**Status: Done**

iCode supports bounded text attachments, images, and PDFs from the workspace or Android's pickers. Text is included in the message. Images and PDFs are sent as provider attachments only when the selected model supports them. Binary attachments are stored with the local chat. A retry uses the saved attachment copy rather than rereading a changed source file.

### Commit messages

**Status: Done**

The Source Control page can ask the selected commit-message model to draft a message from staged change statistics, a bounded staged diff, and recent commit subjects. This request uses the same assistant engine but no tools. The user can review and edit the result before committing. If no separate commit-message model is configured, iCode uses the current chat model. The staged diff and related context go to that provider as described in the [privacy policy](../PRIVACY.md).

## MCP

### Local servers

**Status: Done**

A local MCP server runs inside iCode's Ubuntu/PRoot runtime and communicates over newline-delimited stdio. It inherits the login environment with configured values overlaid, starts in its configured folder, keeps stderr in a local log, and shows the last 200 log lines on the server page. Closing the connection closes protocol input and then follows the local process shutdown path.

A local server is third-party code running with the reach of the PRoot environment. PRoot does not isolate it as a security sandbox.

### Remote servers

**Status: Done**

iCode first uses the official SDK's Streamable HTTP transport and falls back to the deprecated HTTP+SSE transport for compatible older servers. WebSocket is not used as an MCP transport. Remote addresses must use HTTPS, except `http://localhost` and `http://127.0.0.1` for a service on the same phone.

Configured headers and an OAuth Authorization value are added only to that server's requests. The remote server receives the arguments and content sent in MCP operations. Its returned content can then enter the assistant context and go to the selected AI provider.

### Workspace declarations and trust

**Status: Done**

A workspace can declare servers in `.mcp.json`. iCode accepts the same common server shapes as pasted configuration, including local command, arguments, environment, working folder, remote URL, and headers. A local declaration starts in the workspace unless it names another folder.

`${VAR}` and `${VAR:-default}` placeholders are resolved from a saved override first and then the runtime login environment. A missing required value prevents the server from starting.

A workspace declaration does not run until the user trusts it. Trust is stored against a SHA-256 fingerprint of the current declaration. Changing the command, URL, arguments, folder, environment, or headers invalidates that trust.

The `.mcp.json` file is an ordinary workspace file. iCode can encrypt secret overrides saved through its MCP settings, but it cannot encrypt a literal token committed to the declaration. Use environment placeholders instead of literal secrets.

### Secrets and sign-in

**Status: Done**

Secret environment values, secret headers, OAuth tokens, and an in-progress browser sign-in are encrypted with an MCP-specific Android Keystore-backed key. Snapshots and assistant-facing server lists report only whether a secret is set. Values explicitly marked plain remain plain configuration.

Remote MCP sign-in is user-initiated. iCode supports discovery, PKCE, state and issuer checks, resource binding, entered client IDs, client metadata documents where configured, dynamic registration fallback, token refresh, and sign-out. This is third-party MCP sign-in, not an iCode account. The [existing MCP acceptance roadmap](roadmap.md#existing-mcp-transports-sign-in-and-server-acceptance) owns the browser and authorization-server checks.

### Tools, prompts, resources, and questions

**Status: Done**

Enabled MCP tools join iCode's native tools for each turn. Built-in and MCP-management tools take the first slots. A model request has a hard cap of 128 total tools; extra MCP tools are counted and left out. Tools with an unreadable input schema are skipped and shown with a problem.

Each server tool can be Off, Asks first, or Runs. By default, use of a server is a place approval. A user-selected Runs choice removes that server-place prompt for the specific tool, and Full access skips ordinary server-use approval. Saving or removing server configuration still asks every time.

Server read-only and destructive annotations are untrusted hints. Read-only controls whether a tool can be offered in Plan or Ask; it does not auto-approve the call. Server instructions are labeled as the server's own words before entering the model prompt.

MCP prompts and resources can be inserted into the composer for the user to inspect before sending. Legacy elicitation can pause a tool and ask the user a form or URL question; its tool timeout pauses while the question is open.

### Modern protocol support

**Status: Partially Done**

iCode currently uses version 0.16.0 of the official Kotlin MCP SDK. Its active client supports protocol versions through 2025-11-25 and uses the initialize/session flow. Its 2026-07-28 types are experimental and are not active in the SDK client. iCode therefore does not yet implement modern discovery, stateless request metadata, modern subscription listening, or modern multi-round-trip `input_required` handling.

iCode will add dual-era behavior when the official SDK's client supports it instead of maintaining a second protocol client. Legacy stdio, Streamable HTTP, HTTP+SSE fallback, prompts, resources, tools, sign-in, and elicitation remain available. A modern-only server may not connect today. The [modern MCP roadmap](roadmap.md#mcp-modern-protocol-support) is the sole owner of the missing protocol scope and acceptance evidence.

## Security and privacy boundaries

The [privacy policy](../PRIVACY.md) explains what leaves the device. The [security policy](../SECURITY.md) defines supported versions, reporting, and scope.

Important boundaries are:

- The selected AI provider receives the retained model context, selected attachments, and tool traffic needed for the turn.
- Assistant chats stay in app-private local storage, but ordinary chat content is not separately encrypted.
- Provider keys, MCP secret values, and MCP OAuth material use separate Keystore-backed encryption.
- An MCP server is a separate trust boundary. Local servers execute third-party code in PRoot; remote servers receive approved MCP requests.
- MCP output and instructions are untrusted external content even when the server itself is trusted to start.
- Full access intentionally removes ordinary per-call approvals. Per-tool Runs choices and per-chat place grants can also widen access.
- Approval parsing cannot turn PRoot into an OS sandbox or prove everything a shell command or third-party server will do.
- Avoid putting credentials in chat messages. If a key is typed into a chat so the assistant can save it, that original message remains part of the local chat and may be sent in later retained context.
