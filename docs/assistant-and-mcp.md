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
| Repository implementation of external coding agents through ACP | **Done** | The ACP client, the six listed agents' installs, chats bound to an agent and read back from it, turns, approvals, questions, settings, slash commands, plans, sign-in, MCP passthrough and the terminal fallback exist with automated coverage against an in-process ACP agent. |
| Coding agents on a phone | **Needs Verification** | No listed agent has been installed, signed in, or run under iCode's PRoot yet; see [External coding agents through ACP](roadmap.md#external-coding-agents-through-acp). |

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

A provider can expose catalogued models or user-entered models. For a model that Koog does not know, iCode stores the limits and capabilities the user enters, such as context window, output limit, image input, and reasoning. Available reasoning choices depend on the provider and model.

Provider metadata, model choices, assistant mode, approval mode, and the commit-message model are stored in app-private preferences. Each provider API key is stored separately and encrypted with an Android Keystore-backed key. A key is decrypted only when iCode creates a provider connection. It is not returned in UI state or included in connection descriptions. If the device can no longer decrypt a saved key, iCode removes the unusable encrypted value and treats the key as missing.

This encryption claim applies to provider keys. It does not mean all assistant data is content-encrypted.

## Chats, places, and context

**Status: Done**

A chat belongs either to:

- a workspace, where relative paths start at that workspace; or
- no workspace, where the assistant starts from the Linux runtime user's home directory.

Chats are listed separately for each place. One assistant turn can run at a time across the app. A running turn remains tied to the place and chat where it began, even if the user views another workspace or chat.

### Local persistence

Chats are stored as app-private files. iCode writes each chat through a temporary file and atomic rename, and can rebuild a damaged chat index from the individual chat files. Image and PDF attachments are copied into the chat's private folder and move or disappear with that chat.

Local chat files are **app-private, but their contents are not separately encrypted**. Provider keys, MCP secrets, and MCP sign-in material have separate Keystore-backed encryption; ordinary chat text does not.

Persistent history is bounded. iCode keeps at most the newest 400 messages, starting at a user message, and bounds a stored tool result to 48,000 characters. A live in-memory chat can temporarily be longer before it is saved or reloaded.

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

The assistant can ask structured questions with selectable answers instead of guessing. A server can also ask an MCP elicitation question while one of its tools is running. At the assistant's step limit, iCode asks whether to continue. Phone-control work can pause for a user hand-over.

Approvals, pauses, and unanswered structured questions are live turn state. Completed messages, tool calls, and results are persisted, but a pending question is not restored as an active wait after process death.

### Background turns

**Status: Partially Done**

The turn runs in a process-scoped coroutine rather than a screen-owned job. iCode uses foreground work and notifications while work continues away from the assistant page, including notices when an answer is needed. Android can still kill the process; foreground execution improves survival but does not guarantee it. The canonical lifecycle acceptance scope is in the [assistant roadmap](roadmap.md#built-in-assistant-providers-and-tool-lifecycle).

### Retry

**Status: Done**

Transient provider connection, rate-limit, and overload failures use provider-independent retry rules with visible countdown state. A retry resumes from the chat as already persisted. Calls and results recorded before the failure are not deliberately repeated. Cancellation remains cancellation and does not become a retry.

### Compaction

**Status: Done**

The context meter uses the token count of the last request and the chosen model's context window. When the automatic threshold is reached, iCode asks the model for a continuation summary, saves a compaction marker, resets context accounting, and sends the summary plus later messages on following turns. Manual compaction uses the same path.

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

## External coding agents (ACP)

**Status: Done in the repository; Needs Verification on a phone**

A chat can be answered by another vendor's coding agent instead of the built-in assistant. iCode speaks the [Agent Client Protocol](https://agentclientprotocol.com) (ACP) v1 to it through the official Kotlin SDK (`com.agentclientprotocol:acp`, pinned in `app/build.gradle.kts`). Only `data/assistant/acp/` imports the SDK; the `acp-owner` rule of `tools/check-backend-consistency.sh` enforces it. There is no per-agent adapter: every agent goes through the same client, and the Koog assistant is unchanged.

### The listed agents

`assets/agents/registry.json` pins the agents iCode lists, as entries of the ACP registry's own `agent.json` format (registry commit `ccec929`, 2026-11-17). `data/assistant/acp/AgentRegistry.kt` is its one parser; `domain/CodingAgents.kt` names each agent and its own CLI, and a test keeps the two in step.

| Agent | Registry ID | Version | Distribution | Licence |
|---|---|---|---|---|
| Claude Agent (Claude Code) | `claude-acp` | 0.107.2 | `linux-aarch64` binary, SHA-256 pinned; bundles the `claude` CLI | proprietary |
| Codex | `codex-acp` | 2.7.0 | npm `@agentclientprotocol/codex-acp@2.7.0`, which brings `@openai/codex` | Apache-2.0 |
| Gemini CLI | `gemini` | 0.68.0 | npm `@google/gemini-cli@0.68.0 --acp` | Apache-2.0 |
| OpenCode | `opencode` | 2.3.0 | `linux-aarch64` binary, SHA-256 pinned, `opencode acp` | MIT |
| Cline | `cline` | 3.0.74 | npm `cline@3.0.74 --acp` | Apache-2.0 |
| Factory Droid | `factory-droid` | 1.12.1 | npm `droid@1.12.1 exec --output-format acp-daemon` | proprietary |

The parser takes a `linux-aarch64` binary with a SHA-256 first, else an npm package at an exact version. An entry with neither, or an npm package without a version, is left out: what runs could change under the user. Other registry agents, and agents the user names by command, are a later step that reuses the same parser and launcher.

### Installs

Each agent is a runtime tool (`agent-<id>`), listed in its own **Coding agents** section of Runtime Tools and on Settings › Assistant › Coding Agents. `AgentSetup` implements the `RuntimeToolSetup` contract: a binary is downloaded with `PinnedDownload`, checked against its SHA-256 and unpacked; an npm package is installed with the runtime's Node.js (`npm install --prefix … <package>@<version>`), which the tool requires. Everything lands in `~/.local/share/icode/agents/<id>`; the receipt names the version and the checksum or package, so a build pinning another reads as not installed. Download sizes are not pinned by the registry, so the tools page says "Size unknown" for them.

### Processes

`AcpConnections` starts one process per agent with `GuestStdio`, as local MCP servers are started: the login environment, then the agent's own variables, then the keys saved for it. Its sessions share the connection. Standard output is protocol traffic and is never logged; standard error goes to `/tmp/icode-agents/<id>.log`, whose end the agent's settings page and failures show. A process idle for 10 minutes with no turn and no chat being read back is stopped; one that ends on its own is reported with the end of its log.

iCode advertises neither the ACP file-system nor the terminal methods. An agent uses its own tools in the runtime, and its file changes reach the editor and the tree as any outside change does; iCode refreshes the workspace when an agent's change step ends.

An agent's `xdg-open` and `BROWSER` are a small script of iCode's that records the link instead of opening it. The Assistant page then shows "<agent> asks to open <host>" with Open and Not Now; nothing opens without a tap.

### Chats

A chat records what answers it: the built-in assistant, or an agent with its ACP session ID (`"agent": {"id", "session"}` in the chat file and the index). The model sheet lists installed agents beside the providers; choosing one makes new chats go to it, and a shown chat answered by something else gives way to a new chat. An agent's chat keeps its agent.

The agent owns the conversation. iCode saves an agent chat's title and binding only, never its messages. Opening the chat the first time in a process reads it back through `session/load`, and `AcpTranscript` turns the replay into the chat shown. An agent that cannot load sessions cannot reopen its chats, and says so.

### Turns

A message in an agent's chat runs in the assistant's own one-turn job (`AssistantTurns`), with its page state, notices and clean-up. It is sent as `session/prompt`, and `AcpTranscript` maps the session updates onto the built-in assistant's shapes:

- message and thought chunks are the reply's text and reasoning, streamed as it is written;
- each `tool_call` is a step: `read`, `search`, `fetch` and `think` as reads, `edit`, `delete` and `move` as changes, `execute` as a command; its end is the step's result, a `diff` counted in lines;
- a call started while another of the reply waits joins that reply, so each result stays right after its reply;
- `plan` is the plan row above the composer and its sheet;
- `usage_update` feeds the context meter with the agent's own window.

Stop sends `session/cancel` and answers any question still asked as cancelled. An agent that stops at its output limit, its step limit, or by refusing says so as the turn's failure. Retry sends the unanswered message again. Compact and Edit are the agent's own: iCode explains that and points to its commands.

### Approvals and questions

`session/request_permission` is shown on the approval card with the agent's own options (allow once, allow always, reject). The agent's permission mode decides what it asks; iCode never answers for the user, and the built-in permission modes do not apply to agent chats. A request with no turn of its chat in hand is answered cancelled. Questions (`elicitation/create`, form and URL modes) use the MCP question card; only where the answer goes differs.

### Composer, slash commands, and settings

In an agent's chat the model chip names the agent and its model. The agent's config options in the `mode`, `model` and `thought_level` categories are chips beside it, set with `session/set_config_option`; an agent that offers only ACP's older modes or models gets the same chips through `session/set_mode` and `session/set_model`. Typing `/` opens one menu with the agent's slash commands (`available_commands_update`), typed into the message by a tap, and its other config options, toggled in place.

### Sign-in

When the agent answers "authentication required", the page shows each way it offers: an `agent` method calls `authenticate`; a `terminal` method types the agent's own login into the chat's terminal tab, and Done starts the agent again to read the new credentials; a keys method (`env_var`) takes its keys on the agent's settings page, kept with a Keystore key of their own (`icode.agents.key`) and given only to that agent's process. Keys are never typed into a terminal, logged or shown again.

### MCP passthrough

`session/new` and `session/load` pass the user's own enabled MCP servers from `McpServers`, with their values as saved, so a server is configured once for every assistant. A remote server goes only to an agent that supports its transport, with its configured headers but not iCode's own OAuth sign-in. A workspace's `.mcp.json` is not passed: an agent reads it itself in that workspace. A local server's working folder is not carried by ACP.

### Terminal fallback

The chat menu's "Open in <agent> CLI" types the agent's own CLI into the chat's terminal tab and shows it, so anything ACP does not carry is still reachable. Claude Agent and Codex resume the chat's session (`--resume <session>` and `resume <session>`); the others open a new CLI session. The agent's settings page opens the same CLI in a new tab.

### Limits

- An agent is third-party code with the full reach of the PRoot guest: every workspace, the guest home, and shared storage once All Files Access is granted. iCode's workspace boundary and place grants do not apply to its own tools; only its own permission mode and requests do.
- What a chat sends goes to the agent's vendor under that vendor's terms; iCode does not proxy it.
- Agents keep their own credentials in their configuration under the guest home: app-private, not Keystore-encrypted.
- Deleting a chat in iCode removes iCode's record of it; the agent's own session history stays in its storage.
- Android ending iCode ends every agent and its turn. The chat reads back afterwards; the interrupted turn is not resumed.
- The SDK logs through kotlin-logging, which on Android goes to SLF4J unless the `kotlin-logging-to-android-native` system property is set. iCode sets no such property and ships no SLF4J provider (only `slf4j-api` is on the classpath), so SLF4J's no-op logger drops everything the SDK logs, its traces of message content among them; the MCP SDK's logging is dropped the same way.

## Security and privacy boundaries

The [privacy policy](../PRIVACY.md) explains what leaves the device. The [security policy](../SECURITY.md) defines supported versions, reporting, and scope.

Important boundaries are:

- The selected AI provider receives the retained model context, selected attachments, and tool traffic needed for the turn.
- Assistant chats stay in app-private local storage, but ordinary chat content is not separately encrypted.
- Provider keys, MCP secret values, and MCP OAuth material use separate Keystore-backed encryption.
- An MCP server is a separate trust boundary. Local servers execute third-party code in PRoot; remote servers receive approved MCP requests.
- A coding agent is a separate trust boundary: third-party code in PRoot with the guest's full reach, governed by its own permission mode, sending what it is given to its own vendor ([External coding agents](#external-coding-agents-acp)).
- MCP output and instructions are untrusted external content even when the server itself is trusted to start.
- Full access intentionally removes ordinary per-call approvals. Per-tool Runs choices and per-chat place grants can also widen access.
- Approval parsing cannot turn PRoot into an OS sandbox or prove everything a shell command or third-party server will do.
- Avoid putting credentials in chat messages. If a key is typed into a chat so the assistant can save it, that original message remains part of the local chat and may be sent in later retained context.
