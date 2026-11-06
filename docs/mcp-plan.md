# MCP servers for the assistant — plan

Status: **built** (2026-11-06): R1 and M0–M8, as §0 records; M9 waits on the SDK. Planned
2026-11-05, the owner's request. It replaces the
one line [assistant-koog-plan.md](./assistant-koog-plan.md) §7 gave MCP ("MCP servers as tool
sources … behind an `AssistantToolSource` of iCode's own"). It keeps the boundaries of that plan
and of [assistant-permissions-plan.md](./assistant-permissions-plan.md), and the reference the
owner named, AntiGem, read at `santoshkurmi/antigem@f084832`.

> "Now let's plan for the mcp support for the ICode you can take ref from
> https://github.com/santoshkurmi/AntiGem. Make sure to be 100% accurate and let's plan in such a
> way that exactly icdode need to have without any duplicay inconsistency and bug and any future
> limitations"

## 0. As built (2026-11-06)

The owner took every recommendation in §12. Where the build refined this plan, it is said here; the
sections below keep the plan's reasoning.

- **The agent manages the servers too** ([agent-control-plan.md](./agent-control-plan.md) §5):
  `list_mcp_servers`, `save_mcp_server` and `remove_mcp_server` call `McpServers`' own operations,
  the ones the pages' intents call, and every change asks, in every mode. The connections — started
  once, stopped, idled, and their events — are `McpConnections`'.

- **One schema model, by another name.** §6.2's `AssistantSchema` is `AssistantParameterType`
  (`data/assistant/AssistantEngine.kt`): `Text`, `Integer`, `Boolean`, `Number`, `Null`, `Choice`,
  `Items`, `Fields(fields, more)`, `OneOf`. Built-in tools kept their `AssistantParameter` lists;
  each parameter carries its type, so no tool changed. `KoogTool.type` maps each to one Koog type.
- **The contract** is `domain/McpContract.kt` (§5.1 shows the plan's first draft): a value is
  `McpValue(name, value, secret, saved, hint)`, its secret never in the snapshot; a workspace's
  server carries its `workspace`. The SDK does not expose the negotiated protocol version, so
  `McpServerState` shows the server's name and version instead.
- **Intents** (§5.2) are `McpIntent`, a kind of `AssistantIntent`: the assistant's coordinator hands
  them to `McpServers`, which applies them in order on a queue of its own. What reaches the network
  for the user (sign-in, the Registry, prompts, resources) runs beside it, and an answer to a
  server's question is applied at once. `ReadLog` became `Edit(server)`: the page opens on a server
  and reads its log. The gateway is unchanged.
- **Tool names** (§6.1): a long or clashing name gets `_` and eight hex characters of the tool
  name's `String.hashCode()`, not SHA-256: the name needs to be stable, not secret, and SHA-256 has
  one owner in iCode (`data/io/Sha256.kt`).
- **A turn's tools** come through `AssistantTools` (iCode's own, then the servers'). A tool set to
  Runs has no place to ask about; one set to Off is never offered; a reading mode is offered what
  the server marks `readOnlyHint`. The tools left out by the cap are counted in the chat's step and
  on the MCP Servers page.
- **The SDK's limits found:** its `ToolSchema` keeps `$schema`, `properties`, `required` and
  `$defs` only, so a top-level `definitions` is not there to inline. Its `StreamableHttpError`
  carries the status and body but not `WWW-Authenticate`, and is wrapped in its own
  `McpException`: `McpFailures` reads the status wherever it is in the cause chain, and the
  sign-in reads the challenge with a request of its own.
- **Sign-in** (§8.2): the browser comes back to `<applicationId>:/mcp-sign-in` (RFC 8252's
  reverse-domain scheme), which `SignInReturnActivity` hands to the open iCode. With
  `ICODE_WEB_URL` set, a server that takes Client ID Metadata Documents is given
  `$ICODE_WEB_URL/oauth/mcp-client.json`, but only when that document names the redirect: until
  icode-web serves it, registration is Dynamic Client Registration (`application_type: native`),
  then the user's client ID. The sign-in on its way through the browser is kept encrypted, so a
  process restarted meanwhile still finishes it. A 403 `insufficient_scope` reads as refused: Sign
  In again asks for the challenge's scope, else every scope the server lists.
- **Storage** (§5.4), preferences `icode.mcp`, Keystore alias `icode.mcp.key`: `servers` (no
  secret values), `secret.<id>.<name>`, `signin.<id>` (the tokens with their issuer, encrypted),
  `signin.pending`, `choices.<id>` and `trust.<id>` (a workspace's server: the user's choices, and
  the SHA-256 of the configuration trusted). A workspace's server's ID is `<workspace>#<name>`.
- **A workspace's server** (M6) starts in the workspace's folder unless it names one; `${VAR}` in
  its command, arguments, folder or address must be set in the login environment, or it does not
  start.
- **The Registry** (M7) offers a remote first (a phone runs nothing for it), else an npm package
  by `npx -y` or a PyPI one by `uvx`, over stdio; Docker, .NET and downloads are not offered. A
  server found opens its page filled in, for the user to check before adding it.
- **Prompts and resources** (M8) are offered on the composer's attach sheet and taken into the
  message as text, for the user to read before sending.
- **Tests:** `McpConnectionTest` runs the client against the SDK's own server over Streamable
  HTTP on this machine (Ktor's CIO engine, test only), through OkHttp as on the phone. The sign-in
  is tested against a local authorization server. The phone checks (K1–K9) are the owner's to run.

## 1. What MCP is to iCode

iCode runs its own agent (Koog, behind `AssistantEngine`). So iCode is the MCP **client**: the user
adds servers; each server's tools join iCode's own tools in every chat, behind the same approvals,
on the same chat page, in the bubble too.

AntiGem is the other way round. Its agents — Antigravity's `agy` daemon and the Claude Code CLI —
are the MCP clients. AntiGem only edits their configuration and shows what they report (§2.6). Its
code shows the user-facing parts worth having. It gives no client to copy.

**Not in this plan:** iCode as an MCP *server*, for agents run in iCode's terminal (§10).

## 2. Facts this plan rests on (checked 2026-11-05)

### 2.1 The protocol

Read from the specification's own sources (`modelcontextprotocol/modelcontextprotocol@3c4d91b`,
`docs/specification/2026-07-28/`). The website itself could not be reached from here.

| Fact | Where |
|---|---|
| The current revision is **2026-07-28**. Before it: 2025-11-25, 2025-06-18, 2025-03-26, 2024-11-05 | `docs/specification/` |
| **Stateless core.** No `initialize` handshake and no sessions. Each request carries `_meta` `io.modelcontextprotocol/protocolVersion` and `…/clientCapabilities`, and SHOULD carry `…/clientInfo`. Servers MUST implement `server/discover` (supported versions, capabilities, identity, `instructions`) | `changelog.mdx` 1–3, `server/discover.mdx` |
| **Eras.** "Modern" is 2026-07-28 and later. "Legacy" is 2025-11-25 and earlier, with the handshake. A "dual-era" client speaks both. On stdio it probes with `server/discover`: a `DiscoverResult` or a recognised modern error means modern; any other error, or no answer, means legacy, so it falls back to `initialize`. On HTTP it tries a modern request first and reads the body of a `400` before falling back. The era is cached per server process (stdio) or origin (HTTP) | `basic/versioning.mdx`, `basic/transports/stdio.mdx`, `…/streamable-http.mdx` |
| A modern-only server "SHOULD name the protocol versions it supports in any error it returns to an `initialize` request" | `basic/versioning.mdx` |
| **Streamable HTTP (modern).** Every message is a POST. `Accept` lists both `application/json` and `text/event-stream`. Headers: `MCP-Protocol-Version` (must equal the `_meta` version), `Mcp-Method`, and `Mcp-Name` (on `tools/call`, `resources/read`, `prompts/get`). Parameters marked `x-mcp-header` MUST be mirrored as `Mcp-Param-{name}`. No GET stream; change notifications come over `subscriptions/listen`. Closing a request's response stream cancels the request. No resumability | `…/streamable-http.mdx`, `changelog.mdx` 4, 9 |
| **Older HTTP.** 2025-03-26 to 2025-11-25 had `Mcp-Session-Id`, a GET stream and resumability. The 2024-11-05 HTTP+SSE transport is Deprecated. A client that supports older servers POSTs first; on 400, 404 or 405 without a modern error body, it GETs and expects an `endpoint` event | `…/streamable-http.mdx` § Backward Compatibility |
| **stdio.** Newline-delimited JSON-RPC on stdin and stdout. The server MAY log to stderr, and the client SHOULD NOT treat stderr as an error. Cancelling means sending `notifications/cancelled`. Shutdown: close stdin, wait, then SIGTERM, then SIGKILL. If the server dies, the client SHOULD restart it | `basic/transports/stdio.mdx` |
| **Deprecated** (twelve-month minimum window; "new implementations should not adopt them"): Roots, Sampling, Logging; the HTTP+SSE transport; Dynamic Client Registration | `changelog.mdx` § Deprecated |
| **Multi Round-Trip Requests.** In 2026-07-28 a server no longer sends requests to the client. `tools/call`, `resources/read` and `prompts/get` MAY answer `resultType: "input_required"`, with `inputRequests` (elicitation, sampling or roots) and/or an opaque `requestState`. The client asks the user, then retries the original request with a new id, `inputResponses`, and `requestState` echoed back unchanged | `basic/patterns/mrtr.mdx` |
| **Elicitation.** `form` mode takes flat objects of primitive properties only (string with `email`/`uri`/`date`/`date-time` formats, number, boolean, enum). `url` mode sends the user to a page. The answer is `accept` (with content for a form), `decline` or `cancel`. An empty capability object means form mode only | `client/elicitation.mdx` |
| **Tools.** Names SHOULD be 1–128 characters of `A–Z a–z 0–9 _ - .`, unique within one server only. Clients that aggregate servers SHOULD prefix names with a server identifier. `serverInfo.name` "SHOULD NOT be relied upon for disambiguation". `inputSchema` is JSON Schema, 2020-12 by default. Annotations (`readOnlyHint`, `destructiveHint`, `idempotentHint`, `openWorldHint`, `title`) are hints that "clients MUST consider … untrusted unless they come from trusted servers". There SHOULD always be a human able to deny a call, and the client SHOULD show a tool's inputs before calling it | `server/tools.mdx` |
| **Tool results.** Content items: `text`, `image`, `audio`, `resource_link`, `resource` (embedded). `structuredContent` can be any JSON, and the server SHOULD also send it as text. `isError: true` is a failure the model should read; JSON-RPC errors are protocol errors. Content annotations carry `audience` (`user`, `assistant`) | `server/tools.mdx` |
| **JSON Schema.** `$ref` resolving to a network URI MUST NOT be dereferenced automatically. Composition keywords SHOULD have bounds (depth, number of subschemas) | `basic/index.mdx` § JSON Schema Usage |
| **Progress** comes through a `progressToken` in `_meta`. **Timeouts** SHOULD be set on every request; an implementation MAY reset one on progress but SHOULD keep a maximum | `basic/patterns/progress.mdx`, `cancellation.mdx` |
| **Authorization** is optional and HTTP only; a stdio server takes credentials from its environment. Discovery uses `WWW-Authenticate: … resource_metadata=` (RFC 9728) or the well-known URIs. PKCE is required, and so is `resource` (RFC 8707) set to the server's canonical URI. A present `iss` (RFC 9207) MUST be checked against the recorded issuer. Registration, in order: a pre-registered client → a Client ID Metadata Document (when the server metadata says `client_id_metadata_document_supported`) → Dynamic Client Registration (deprecated; `application_type: "native"` for a mobile app) → ask the user. Client credentials are keyed by issuer and never reused with another | `basic/authorization/*.mdx` |

### 2.2 The Kotlin SDK

`io.modelcontextprotocol:kotlin-sdk-client`, the official SDK, built with JetBrains. Read from its
sources at tag `0.16.0`, its release notes, and Maven Central's POMs.

- **Latest release: 0.16.0** (late October 2026). Before it: 0.15.0 (late July), 0.14.0 (around
  1 July) and 0.13.0 (2 June).
- **Protocol:** `LATEST_PROTOCOL_VERSION = "2025-11-25"`. `SUPPORTED_PROTOCOL_VERSIONS` are
  2025-11-25, 2025-06-18, 2025-03-26 and 2024-11-05, so the SDK is a **legacy-era client**. Its
  2026-07-28 types (`DiscoverRequest`, `DiscoverResult`, …) are `@ExperimentalMcpApi`, and the
  0.16.0 notes say "the client and server do not use them yet". The main branch carries the
  `server/discover` types.
- **Runtime dependencies** (POM, compile scope): `kotlin-stdlib` 2.4.21, `ktor-client-core` 3.5.2,
  `kotlinx-serialization-json` 1.11.0, `kotlinx-coroutines-core` 1.11.0, `kotlinx-io-core` 0.9.1,
  `kotlinx-collections-immutable` 0.5.2. Runtime scope: `kotlin-logging` 8.0.4 and, from the core
  module, `ktor-server-websockets` 3.5.2. It adds no Ktor engine.
- **Client:** `Client(clientInfo, ClientOptions(capabilities, …))`. `connect(transport)` runs the
  handshake. `listTools`, `callTool(…, RequestOptions(timeout, onProgress))`,
  `serverCapabilities`, `serverVersion`, `serverInstructions`. `setRequestHandler` (a suspend
  block) answers server requests; `setNotificationHandler` handles notifications. The default
  request timeout is `DEFAULT_REQUEST_TIMEOUT = 60.seconds`.
- **Cancelling** the calling coroutine sends `notifications/cancelled` (`Protocol.kt`), except for
  `initialize`.
- **Transports:** `StdioClientTransport(input: Source, output: Sink, error: Source? = null, …)`
  (kotlinx-io); `StreamableHttpClientTransport(client: HttpClient, url, reconnectionOptions,
  maxInlineSseEventSize, requestBuilder)`, which sends `Mcp-Method` and `Mcp-Name` since 0.15.0;
  `SseClientTransport`, deprecated in 0.16.0. **No OAuth:** headers go through `requestBuilder`.
- **iCode today:** Kotlin 2.3.10 and Ktor 3.3.3. Ktor is pinned to the version Koog 1.3.0 was built
  with ([assistant-koog-plan.md](./assistant-koog-plan.md) §4.9).

### 2.3 Koog 1.3.0

- 1.3.0 is Koog's latest release.
- Its tool parameters (read from `agents-tools-jvm-1.3.0.jar`) are `ToolParameterType` values:
  `String`, `Integer`, `Float`, `Boolean`, `Null`, `Enum(entries)`, `List(itemsType)`,
  `Object(properties, requiredProperties, additionalProperties, additionalPropertiesType)` and
  `AnyOf(types)`. There is **no raw JSON Schema**: no `$ref`, no `oneOf`/`allOf`, no constraints.
- Koog's own `agents-mcp` is **not used**, for two reasons. It would run calls through Koog's tool
  registry, past `AssistantGate`. And it pins its own SDK: the 1.0.0 notes say "MCP kotlin-sdk
  upgraded from 0.8.1 to 0.11.1".

### 2.4 Tool names at the providers

| Provider | Rule | Source |
|---|---|---|
| Anthropic | `^[a-zA-Z0-9_-]{1,128}$` | platform.claude.com, Messages API |
| OpenAI | letters, digits, `_` and `-`, at most 64; at most 128 tools per request | OpenAI's spec as client libraries quote it (dart_openai, LangChain.js). OpenAI's own page could not be read from here |
| Gemini | starts with a letter or `_`; then letters, digits, `_ . : -`; at most 64 in older references and the API's errors, 128 on Google Cloud's current page | Google Cloud's `FunctionDeclaration` reference |

The narrowest rule all three accept: **starts with a letter, only `A–Z a–z 0–9 _ -`, at most 64.**

### 2.5 iCode as it is

- `AssistantTool` describes its input as flat `AssistantParameter`s (Text, Integer, Boolean).
  `KoogTool` turns them into Koog's descriptor. An MCP tool needs nested objects, arrays, enums,
  numbers and unions.
- `AssistantGate` asks by reach: paths outside the workspace, or — through `AssistantAppActor` —
  the phone app an act is in ("Act in WhatsApp?", "Allow in WhatsApp", root `app:<package>`). An
  "Allow in" answer lasts for the chat until iCode closes.
- `AssistantStep` holds **one** picture a step leaves for the model.
- A runtime process starts in PRoot through `RuntimeLayout.prootCommand` and `RuntimeProcess`.
  `RuntimeProcess` has one output pipe ("the process bridge would mix stderr into stdout",
  `GuestCommand`).
- The Runtime tools catalog has Python 3 but no Node.js
  ([runtime-tools-plan.md](./runtime-tools-plan.md) D1: Ubuntu 24.04's `nodejs` is v18). The
  reference server `@modelcontextprotocol/server-everything` 2.2.1 needs Node.js 24 or later.
- Clear text goes to `localhost` and `127.0.0.1` only (`network_security_config.xml`).
- Keys are encrypted through `KeystoreCipher`, with one alias per store.
- `ICODE_WEB_URL` is a build property; empty turns every web feature off.

### 2.6 AntiGem, read at f084832

What it does: `AgyMcpManager` reads and writes `mcp_config.json`'s `mcpServers`. A local server
has `command`, `args`, `env` and `cwd`; a remote one has `serverUrl` and `headers`; either can have
`disabled` and `disabledTools`. The manager lists each server's live state from the daemon (status,
error, tools, instructions), toggles servers, refreshes them, and installs from Antigravity's plugin
catalogue. For Claude Code it adds and removes servers through its Go bridge (`POST` and `DELETE
/mcp`). Its own MCP servers
(Browser, Terminal, Android, Notes & To-dos, plugins) run on `127.0.0.1:8765`, behind an
`X-Device-Id` header, and are offered as one-tap presets.

| From AntiGem | iCode |
|---|---|
| The `mcpServers` shape most server READMEs give (and its `serverUrl` spelling) | **Take**: pasting it is one way to add servers (§4.1) |
| A server's switch, a tool's switch, the live state with tools and error, refresh | **Take**, on iCode's pages, as iCode's own state |
| Approvals naming the server and the tool ("Use create_issue?") | **Take**, through iCode's one gate (§6.4) |
| Header values and env (`X-Goog-Api-Key`, keys) written in plain JSON in `mcp_config.json` | **Not**: every value is encrypted unless the user marks it plain (§5.4) |
| `LD_PRELOAD` and `PATH` hard-coded into a local server's env when it sets neither | **Not**: a local server starts with the user's login environment, as `start_process` does (§7.1) |
| `delay(600)` after a refresh, then a re-read; states as strings (`"MCP_SERVER_STATUS_STOPPED"`) | **Not**: states are events and types (§5.2) |
| Its own servers answering `initialize` with `"2024-11-05"`, whatever was asked; no argument validation (its docs: "The host does not perform JSON-schema validation") | Not relevant: iCode serves nothing. Its client validates what the SDK validates |
| Built-in Browser, Terminal, Android and Notes servers | **Not needed**: iCode's own agent has these as native tools (files, commands, processes, phone control) |

## 3. Decisions

| # | Decision | Why |
|---|---|---|
| D1 | iCode is an MCP client only | §1 |
| D2 | One package, `data/assistant/mcp`, is the only importer of `io.modelcontextprotocol`, as `data/assistant/koog` is of `ai.koog`. Koog's `agents-mcp` is not used | §2.3; one gate, one tool path |
| D3 | **Eras:** iCode speaks the legacy era (2024-11-05 to 2025-11-25) through the SDK now. It speaks 2026-07-28 when the SDK's client does, as a dual-era client by the spec's rules. A modern-only server is told apart by its `initialize` error and named in plain words (§9). Nothing outside `mcp` changes when the era does | §2.1, §2.2. Writing iCode's own client would copy a spec-tracking SDK |
| D4 | **Transports:** stdio for local servers, in the Linux runtime. Streamable HTTP for remote ones, falling back to HTTP+SSE as the spec describes, while the spec keeps it. Not WebSocket, which is not an MCP transport | §2.1 |
| D5 | **Not adopted:** Roots, Sampling and Logging (`logging/setLevel`), as the spec asks of new clients. The only capability iCode declares is `elicitation` (form and url, M4). A local server's stderr is kept in its own log (§7.2) | §2.1 Deprecated |
| D6 | **Tools** join each turn's tool list as `AssistantTool`s, through `AssistantGate`. A server is a *place* outside the workspace, as a phone app is: it asks, "Allow in GitHub" opens it for the chat, Full access runs it (§6.4) | §2.5; one gate |
| D7 | **Tool input schemas:** one model of iCode's own, `AssistantSchema`, for every tool. Built-in tools build theirs from their parameters; MCP tools convert their JSON Schema to it; `KoogTool` maps it to Koog's types one to one | §2.3, §2.5; one path from tool to provider |
| D8 | **Secrets** (env and header values, OAuth tokens) are stored encrypted, shown masked, never logged and never in the snapshot | The security rules |
| D9 | The **reference server** for tests on the phone is `@modelcontextprotocol/server-everything` | It serves both eras, images, progress, elicitation, URL elicitation and structured content (its `tools/`) |

## 4. The user's lifecycle

### 4.1 Adding a server

**Settings › Assistant › MCP Servers** (a row under Model Providers: "N servers · M tools", or
"Tools from other servers" with none). **Add Server** opens a sheet with three ways:

- **Paste configuration** — the JSON a server's README gives. It accepts:
  - the `mcpServers` map;
  - a `servers` map;
  - a map of names to servers;
  - one server.

  Fields read: `command`, `args`, `env`, `cwd`; `url`, `serverUrl` or `httpUrl`; `headers`;
  `disabled`. `type` is read as a hint only, since the transport is detected (§8.1). A `${VAR}` or
  `${VAR:-default}` value is left empty and marked *needs a value*. Unknown fields are named in
  one line, not kept. Several servers in one paste are added together, each by its name.
- **Local command** — command, arguments (one per line), working folder, environment.
- **Remote URL** — the address, headers, and (M5) sign-in.

Each is checked before it is saved (`McpRules.problem`): a name, a command or an `https://` URL (or
`http://localhost` / `127.0.0.1`), no duplicate name, no empty required value. Saved, a server
connects at once and its page shows what happened.

### 4.2 The pages

- **MCP Servers** — one row per server. The row shows its name, its kind and where it is ("Local ·
  npx -y @modelcontextprotocol/server-everything", "Remote · mcp.example.com"), its state
  ("13 tools", "Starting…", "Sign in needed", "Failed: …", "Off") and its switch.
- **A server** — its fields as on adding. Secret values show as ••••, with Replace.
  - **Tools**: each with its title, two lines of description, *Read-only* or *Destructive* as the
    server says, and its switch. Under the switch, **Asks first** (the default) or **Runs**, the
    user's own choice per tool, never taken from a hint (§6.4).
  - **Instructions**: what the server tells models, collapsed.
  - **Log**: a local server's stderr, the last 200 lines; a remote server's last failure.
  - **Restart**, **Sign in** / **Sign out** (M5), and **Remove**, which asks in a confirmation sheet.
- A page shows the tools' total across servers. Over the cap (§6.1) it says how many are left out
  until some are turned off.

### 4.3 In a chat

- A call's step reads **"GitHub · Create issue"** (the tool's `title`, else its name). While it
  runs, it shows the server's progress ("Uploading — 3 of 10").
- The card asks **"Use GitHub?"** and shows the tool's title and its arguments, as JSON, before
  anything is sent. The answers are **Deny · Allow in GitHub · Allow Once**: the app card's words,
  with the server as the place.
- The result is the tool's text. An image goes to a model that sees pictures (§6.3).
- **A server's question** (M4): a card **"GitHub asks"** with its message and fields, or with its
  web page's address and **Open**. The answers are Decline · Submit, or Decline · Open. Closing it
  is Cancel.
- In the bubble, approvals show as today; a server's question shows its first line and **Open in
  iCode**. While iCode is away, both go on the "Needs your answer" channel.

## 5. Architecture

```text
ui/screens/settings/McpServers{Screen,ServerScreen,Sheets}.kt ──dispatch(McpIntent)──► AndroidICodeGateway
        ▲ snapshot.mcp (domain types only)                                              │
domain/McpContract.kt, McpRules.kt                       data/assistant/mcp/McpServers (one per process)
                                                          ├─ McpServerStore (+ KeystoreCipher, alias icode.mcp.key)
                                                          ├─ McpConnection ── one server: client, era, tools, calls
                                                          │    ├─ McpLocal   stdio in PRoot (§7)
                                                          │    └─ McpRemote  Streamable HTTP, HTTP+SSE fallback (§8)
                                                          ├─ McpTool        one server tool as an AssistantTool (§6)
                                                          ├─ McpSchema      JSON Schema → AssistantParameterType
                                                          ├─ McpResults     content → text, picture
                                                          ├─ McpFailures    every failure in plain words (§9)
                                                          └─ McpSignIn      OAuth (M5)
data/assistant/AssistantTurns ── tools = toolbox.forWorkspace(…) + mcp.tools(chat) ── AssistantGate ── KoogTool
```

### 5.1 Contract (domain)

```kotlin
data class McpServer(
    val id: String,                       // iCode's UUID
    val name: String,                     // "GitHub": what the user named it
    val key: String,                      // "github": the tool-name prefix (§6.1), unique
    val transport: McpTransport,
    val enabled: Boolean,
    val tools: Map<String, McpToolChoice>,  // by the server's tool name; absent = on, asks first
    val toolTimeoutSeconds: Int = 120,
)
sealed interface McpTransport {
    data class Local(val command: String, val args: List<String>, val folder: String?, val env: List<McpValue>) : McpTransport
    data class Remote(val url: String, val headers: List<McpValue>, val clientId: String? = null) : McpTransport
}
data class McpValue(val name: String, val secret: Boolean, val hasValue: Boolean, val plain: String? = null) // plain: never for a secret
enum class McpToolChoice { Off, AsksFirst, Runs }
data class McpServerState(
    val id: String,
    val status: McpStatus,                // Off, Starting, Ready, SignIn, Failed
    val failure: String? = null,          // McpFailures' words
    val protocolVersion: String? = null,  // as negotiated
    val tools: List<McpToolInfo> = emptyList(),
    val instructions: String? = null,
)
data class McpToolInfo(val name: String, val title: String?, val description: String, val readOnly: Boolean, val destructive: Boolean)
data class McpState(val servers: List<McpServer> = emptyList(), val states: Map<String, McpServerState> = emptyMap(), val log: McpLog? = null)
```

`McpRules` (pure, tested): `import(json)`, `problem(server)`, `key(name, taken)`, `toolName(key,
tool, taken)` (§6.1), `capped(servers)` (§6.1).

### 5.2 Intents and state

`McpIntent` (`domain/McpIntents.kt`, §0): `Connect`, `SaveServer`, `ImportServers(json)`,
`RemoveServer`, `SetServerEnabled`, `SetToolChoice`, `RestartServer`, `Edit`, `SignIn`/`SignOut`/
`SignedIn` (M5), `Answer` (M4), `Trust` (M6), `SearchRegistry` (M7), `UsePrompt`/`UseResource`/
`DraftTaken` (M8).

`McpServers` publishes `McpState` as each connection changes: started, failed, tools changed. It
never polls and never waits a fixed time.

### 5.3 Threads

Connections live in `McpServers`' scope (`Dispatchers.IO`). Turns ask it for tools with a suspend
call. A connection's state changes are published in order through the gateway, as the assistant's
are.

### 5.4 Storage

Preferences `icode.mcp`, as built in §0:
- `servers`: the JSON list, without secret values.
- `secret.<id>.<name>`: encrypted.
- `signin.<id>`, `signin.pending`: M5's tokens with their issuer, and the sign-in under way, encrypted.
- `choices.<id>`, `trust.<id>`: a workspace's server (M6).

A value is secret unless the user marks it plain. A value from the Registry (M7) takes the
registry's `isSecret`. A key that can no longer be decrypted reads as missing, and the page asks for
it again, as provider keys do ([assistant-koog-plan.md](./assistant-koog-plan.md) §4.7).

## 6. Tools in a turn

### 6.1 Names and the cap

- A server's `key` comes from its name: lower case, characters outside `a–z 0–9` become `_`, it
  starts with a letter (`s` added otherwise), at most 16 characters, and `_2`, `_3` … on a clash.
- A tool's name for the model is `<key>__<tool>`: characters outside `A–Z a–z 0–9 _ -` become `_`,
  at most 64 (§2.4). A longer or clashing name is cut and given `_` and eight hex characters of the
  tool name's hash (§0). `McpTool` keeps the server's real name for the call. iCode's own tools never
  contain `__`, so they never clash.
- **The cap:** 128 tools a request (OpenAI's count, as §2.4 records it). Built-in tools come first,
  then each server's in the servers' order. What is left out is named on the pages (§4.2) and once
  in the chat.

### 6.2 Schemas — `AssistantParameterType` (planned as `AssistantSchema`, §0)

```kotlin
sealed interface AssistantSchema {
    data class Text(val description: String = "") : AssistantSchema
    data class Integer(val description: String = "") : AssistantSchema
    data class Number(val description: String = "") : AssistantSchema
    data class Bool(val description: String = "") : AssistantSchema
    data class Choice(val values: List<String>, val description: String = "") : AssistantSchema
    data class Items(val of: AssistantSchema, val description: String = "") : AssistantSchema
    data class Fields(val fields: List<Pair<String, AssistantSchema>>, val required: Set<String>, val more: AssistantSchema? = null, val description: String = "") : AssistantSchema
    data class OneOf(val options: List<AssistantSchema>, val description: String = "") : AssistantSchema
    object Null : AssistantSchema
}
```

- `AssistantTool.parameters` becomes `AssistantTool.input: AssistantSchema.Fields`. Built-in tools
  build theirs with today's `AssistantParameter` list (one helper, so no tool changes its own
  code). `KoogTool` maps each case to one `ToolParameterType`: `Number` → `Float`, `OneOf` →
  `AnyOf`, `Null` → `Null`.
- `McpSchema.convert(inputSchema)`, bounded at a depth of 16 and 512 subschemas:
  - **References:** a local `$ref` (`#/$defs/…`, `#/definitions/…`) is inlined. A network one is
    refused, as is a cycle.
  - **Types:** `type` lists such as `["string","null"]` → `OneOf(…, Null)`. `enum` of strings →
    `Choice`. `const` → a `Choice` of one. Any other `enum` → `Text`, with its values in the
    description.
  - **Composition:** `oneOf`/`anyOf` → `OneOf`. `allOf` of objects → one merged `Fields`.
    `if`/`then`/`else` → its `then` and `else` as `OneOf`.
  - **Objects:** a missing `type` with `properties` → `Fields`; an object without `properties` →
    `Fields` with `more`.
  - **Constraints** with no field in Koog (`format`, `pattern`, `minimum`/`maximum`, lengths,
    `default`, `examples`) are written into the description in plain words, so the model still
    reads them.
- A schema that cannot be converted leaves its tool out, with the reason on the server's page.
  Arguments reach the server as the JSON the model wrote; `KoogTool` already passes them
  untouched.

### 6.3 Results — `McpResults`

| Content | To the model |
|---|---|
| `text` | its text |
| `image` | the step's picture, if the model sees pictures (`AssistantStep.leave`); otherwise "[image: image/png, 34 KB — this model does not read images]". A step keeps one picture (§2.5); more images are named in the text |
| `audio` | "[audio: audio/wav, 120 KB — not passed to the model]" |
| `resource_link` | "Resource: name — uri" |
| `resource` | its text, or "[resource: uri, mime, size]" for a blob |
| `structuredContent` | its JSON as text, when no text item came with it |
| `audience: ["user"]` | on the page only |
| `isError: true` | a refusal the model reads (`AssistantToolException`), so the step shows as failed |

The text is bounded at 32 000 characters (head and tail), as `run_command`'s output is, through the
same helper. The chat keeps a result up to its 48 000 characters
([assistant-koog-plan.md](./assistant-koog-plan.md) §4.6).

### 6.4 Approvals

- `AssistantAppActor` becomes `AssistantPlaceActor`, and `AssistantReach.app` a named place with a
  kind (App or Server). There is one path for both: a phone app and an MCP server are each a place
  outside the workspace. Its root is `app:<package>` for an app and `mcp:<id>` for a server.
- The question is "Use GitHub?" for a server (and "Act in WhatsApp?" for an app, unchanged). The
  answers are "Allow in GitHub" for the chat and "Allow Once"; denied, the model reads "The user did
  not allow using GitHub".
- By mode:
  - **Ask first** and **Auto in workspace**: an MCP call asks, as anything outside does, until the
    chat allows the server.
  - **Full access**: it runs.
  - A tool the user set to **Runs**: it runs in every mode. A tool set to **Off**: never offered.
- The card's detail is the tool's title and its arguments, pretty-printed and bounded, as the spec
  asks.
- **Ask** and **Plan** modes are offered only the tools whose server says `readOnlyHint: true`. The
  hint picks what a reading mode is offered; it never skips a question (§2.1).

### 6.5 Instructions, timeouts, cancellation, progress

- **Instructions:** for each server whose tools a turn offers, `serverInstructions` goes into the
  system prompt under "Tools from MCP servers". It is bounded at 2 000 characters a server and
  marked as the server's words.
- **Tool timeout:** 120 s by default, per server. This is iCode's own clock, paused while the user
  is asked something (M4). The SDK's request timeout is set to a 30-minute ceiling, so a question
  the user takes time over never fails the call. When iCode's clock runs out, the call's coroutine
  is cancelled, the SDK sends `notifications/cancelled`, and the model reads "GitHub did not answer
  in 120 s".
- **Stop** cancels the turn's job, and with it every MCP call in flight (§2.2).
- **Progress:** `RequestOptions.onProgress` writes the step ("GitHub · Create issue — Uploading, 3
  of 10"). The step reaches the page, the bubble and the notification, as it does today.

## 7. Local servers — the Linux runtime

### 7.1 Starting one

- The server starts in a PRoot of its own (`RuntimeLayout.prootCommand`, `killOnExit = true`). It
  gets the user's login environment (`GuestEnvironment`, as `start_process`'s), with the server's env
  over it.
- Its working folder is the one given, else `/root`.
- The command and its arguments are passed as argv, never re-parsed by a shell. A fixed wrapper only
  sends stderr to the server's log: `bash -c 'exec 2>>"$0"; exec "$@"' <log> <command> <args…>`
  (`$0` is the log, `"$@"` the command and its arguments).
- stdout and stdin go to `StdioClientTransport` through kotlinx-io (`asSource()` / `asSink()` over
  `RuntimeProcess`'s streams).
- Before starting, `command -v <command>` runs in the same environment. If the command is missing,
  the server fails with words that say what to install: `npx` or `node` → Node.js, `uvx` or `uv` →
  uv, `python3` → Python (all three in Settings › Runtime tools, §11 R1).

### 7.2 Its log

stderr goes to the server's own file in the runtime's `/tmp/icode-mcp/<id>.log`. The file is
emptied at each start, and the last 200 lines are shown. It never reaches `BackendLog`, the model,
or a chat. The spec says stderr is not an error, so a failure quotes its last line only when the
process ended.

### 7.3 Lifetime

- A server **connects**:
  - when it is saved or switched on;
  - when the Assistant page opens (enabled servers), so a first message rarely waits;
  - at a turn's start, if it is not connected yet. The working line then reads "Starting GitHub…",
    bounded at 60 s. Past that, the turn goes on without it and the chat says so once.
- Its **tool list** is read at connect and again on `notifications/tools/list_changed`. A turn uses
  the list as it was when the turn started.
- **Idle** for 10 minutes after the last turn ends, a local server **stops** as the spec says:
  close stdin, then SIGTERM after 3 s, then SIGKILL after 3 s more. Its next need starts it again.
- **Died** (the process ended): a call in flight fails with the log's last line, and the next need
  restarts the server (§2.1).
- iCode's own process ending ends every server (`killOnExit`). MCP servers are not background work:
  they never hold iCode's foreground service by themselves. A turn does, as today.
- With the runtime not installed, a local server reads "Needs the Linux runtime".

## 8. Remote servers

### 8.1 HTTP

- One Ktor `HttpClient(OkHttp)` for MCP per process, with the SSE plugin. The address must be
  `https://`, or `http://localhost` / `127.0.0.1` (the network policy, §2.5).
- `StreamableHttpClientTransport` comes first. On a 400, 404 or 405 without a modern error body, it
  falls back to `SseClientTransport` (§2.1). The transport found is kept in memory for the server's
  address.
- Headers are the user's values, decrypted when a request is built, plus M5's `Authorization`.

### 8.2 Sign-in (M5)

- **Trigger:** a 401 with `WWW-Authenticate` sets the state to "Sign in needed". **Sign in**, on the
  server's page or its chat notice, starts the flow below. Nothing opens a browser on its own.
- **Discovery:** the `resource_metadata` URL, or the well-known URIs, then the authorization
  server's metadata.
- **Registration**, in the spec's order:
  1. the client ID the user entered (Remote URL › Advanced);
  2. a Client ID Metadata Document, when the server supports one and iCode has one (D-M1);
  3. Dynamic Client Registration with `application_type: "native"`;
  4. otherwise, ask the user for a client ID.
- **Request:** PKCE S256; `state`; `resource` = the server's canonical URI; scope from the
  challenge, else `scopes_supported`. It opens in a Custom Tab. The redirect is iCode's own (D-M1).
- **Back:** `state` is checked, and `iss` against the issuer recorded before the redirect. The code
  is exchanged, and tokens are stored encrypted under the issuer.
- **After:** refreshed on expiry or a 401. On a 403 `insufficient_scope`, it asks again with the
  wider scope. Credentials are never reused with another issuer; a changed issuer means signing in
  again.

## 9. Failures, in plain words — `McpFailures`

One place, as `KoogFailures` is for providers.

| Situation | The page says |
|---|---|
| The command is not in the runtime | "npx is not in the Linux runtime — install Node.js in Settings › Runtime tools" |
| The process ended at start | "GitHub stopped: <its last stderr line>" |
| The server speaks only newer versions | "GitHub speaks MCP 2026-07-28 only; iCode speaks MCP up to 2025-11-25 until its MCP library does" (§2.1, D3) |
| Nothing answered within 60 s | "GitHub did not start within 60 s" |
| 401 | "Sign in needed" |
| 403 | "GitHub refused: <its words>" |
| 404 with no MCP error | "No MCP server at this address" |
| `http://` to another host | "Remote servers need https:// (http only to this phone)" |
| TLS or network failure | the words iCode already uses for providers |
| A JSON-RPC error on a call | the server's message, read by the model |
| A schema that cannot be converted | "<tool> left out: <why>" on the server's page |

## 10. Not in this plan, and why

- **iCode as an MCP server** (phone control and the editor for agents run in iCode's terminal) is a
  separate feature with its own security questions: a local endpoint and who may call it. Nothing
  here prevents it.
- **Sampling, Roots, Logging levels**: deprecated (D5).
- **MCP Apps** (`io.modelcontextprotocol/ui`, servers' HTML in the client) and **Tasks**
  (`io.modelcontextprotocol/tasks`): extensions negotiated in capabilities. They come with the
  modern era (M9) if the owner wants them.
- **Icons**: optional in the spec. The server's name is enough on these pages.

## 11. Cycles

Each cycle ends green on CI and the guards, with its docs, and is pushed.

| # | What | Tests |
|---|---|---|
| **M0** | **Toolchain:** Kotlin 2.4.21 (with the Compose plugin), Ktor 3.5.2 (`ktor-client-okhttp`), `kotlin-sdk-client` 0.16.0. Excluding `ktor-server-websockets` is tried; R8 rules added for what the SDK needs. Koog 1.3.0 on Ktor 3.5.2 repeats [assistant-koog-plan.md](./assistant-koog-plan.md) §9 D1 and D9 on the phone | the whole suite; the app built and run; a chat on each provider |
| **R1** | **Node.js and uv in Runtime tools**: pinned downloads with SHA-256 (`PinnedDownload`), the version recorded when built. With them, `npx` and `uvx` servers run | Runtime tools' tests; `npx --version`, `uvx --version` in the terminal |
| **M1** | **`AssistantSchema`**: `KoogTool` and every built-in tool on it; nothing visible changes | `KoogToolTest` (each case to Koog's type); the assistant suites unchanged |
| **M2** | **Servers and pages**: contract, `McpRules`, store, `McpServers`, `McpLocal`, `McpRemote`, `McpSchema`, `McpFailures`; the pages (§4.1, §4.2) | `McpRulesTest` (import of each shape, problems, keys, names, cap); `McpSchemaTest`; `McpConnectionTest` against the SDK's own server (`kotlin-sdk-server`, test only) over in-memory streams: connect, list, call, a server ending |
| **M3** | **Tools in chats**: `McpTool`, the place gate, words, results with pictures, instructions, timeouts, cancellation, progress, modes, the bubble | `McpResultsTest`; `AssistantGate` tests for a server (each mode, Allow in, Runs, Off); `AssistantCoordinatorTest` with a fake server's tools |
| **M4** | **A server's questions**: elicitation, form and URL, cards, bubble, notification; iCode's clock paused while asking | `McpConnectionTest` (accept, decline, cancel; URL shown, not opened); the coordinator with a question |
| **M5** | **Sign-in** (§8.2) | discovery, registration order, `iss`, refresh, step-up against a fake authorization server |
| **M6** | **A workspace's `.mcp.json`**: its servers listed on the workspace's MCP section, each started only once the user trusts it; trust is stored with a hash of its configuration, and a changed configuration asks again; `${VAR}` filled from the login environment or iCode's secret values | `McpRulesTest` (trust, hash, placeholders) |
| **M7** | **The MCP Registry**: search, and add from a `server.json` (`packages` → a local command, `remotes` → a URL, `isSecret`, required values). Only on an API version the registry calls stable: v0.1 was frozen on 2025-10-24, and GA was not announced at this check | parsing tests on published `server.json` examples |
| **M8** | **Resources and prompts** (user-controlled): resources attached from the composer's attach sheet as `AssistantAttachment`s; prompts as templates in the composer | — |
| **M9** | **Modern era 2026-07-28**, with the SDK version that speaks it, dual-era by the spec: the `server/discover` probe, MRTR questions, `subscriptions/listen`, `x-mcp-header`, `ttlMs` caching. Only `mcp` changes | the SDK's server in both eras |

**On the phone**, with server-everything (D9; Node.js 24+ from R1). The user adds
`{"mcpServers":{"everything":{"command":"npx","args":["-y","@modelcontextprotocol/server-everything"]}}}`:

| # | Do | See |
|---|---|---|
| K1 | Paste it | "Starting…", then "N tools" |
| K2 | "Use the echo tool to say hi" | "Use Everything?" with its arguments; Allow Once; the echo |
| K3 | `get-tiny-image` | the picture, to a model that sees; the note to one that does not |
| K4 | `trigger-long-running-operation`, then Stop | progress in the step and the notification; the call cancelled |
| K5 | `trigger-elicitation-request` (M4) | the form card; Submit; the result names the fields |
| K6 | `trigger-url-elicitation` (M4) | the address shown; Open in a Custom Tab only when tapped |
| K7 | The same server from its source in the terminal, over Streamable HTTP (`npm run start:streamableHttp`; added as `http://localhost:3001/mcp`) | a remote server works on this phone |
| K8 | Kill its process from the terminal mid-chat | the call fails with the log's last line; the next one restarts it |
| K9 | Idle 10 minutes | the process is gone; the next message starts it |

## 12. Owner decisions open

| # | Decision | Recommended |
|---|---|---|
| D-M0 | SDK 0.16.0 with Kotlin 2.4.21 and Ktor 3.5.2 (M0), or an older SDK on today's toolchain | **0.16.0**: an older release lacks the fixes 0.14 to 0.16 made (stdio shutdown hang, `Mcp-Method`/`Mcp-Name`, URL elicitation, typed capabilities), and M9 needs the newest anyway |
| D-M1 | Where sign-in comes back to: a Client ID Metadata Document and an HTTPS App Link on icode-web (needs `ICODE_WEB_URL` fixed), or a private-use scheme from the application ID with Dynamic Client Registration | **icode-web** where it is set, the scheme otherwise |
| D-M2 | Per-tool **Runs** (a tool that never asks), or only "Allow in GitHub" per chat | **Per-tool Runs**, set by the user only |
| D-M3 | Workspace `.mcp.json` (M6) | **Yes**, trust first |
| D-M4 | Node.js and uv in Runtime tools (R1) | **Yes**: without them most local servers cannot start |
