# iCode Assistant on Koog — plan

Status: **A1–A6 done (2026-10-01)**: the backend, the Koog adapter, the screens and the build
wiring, each checked as far as this container allows (§11). Left for the owner: the Gradle build,
the release build and the device pass (A7, §9), and the decisions of §10.

Owner's request (2026-10-01): "make plan for koog integration, as is researched make sure to
design clean and best plan ever and implement it in clean and best way as needed to be". The
research (uploaded the same day) recommends Koog as iCode's native agent runtime behind an engine
boundary of iCode's own, with iCode owning the tools; ACP for external agents and MCP later,
behind iCode's own interfaces; Google's ADK as the contingency; a device bake-off before trusting
it. This plan keeps that shape, checks each claim it rests on against Koog's published artifacts
(§2), and follows [backend-split-rules.md](./backend-split-rules.md).

## 1. The decision, and what it changes

iCode gets **one built-in assistant**: a chat beside the workspace. It reads, searches and changes
the workspace's files and runs commands in the runtime, using a model provider the user configures.
Koog runs the agent loop; iCode owns everything around it.

This is a new architecture decision (architecture.md §25–26). It amends rules written when iCode
meant to have no agent of its own:

| Was | Now | Why it still holds together |
|---|---|---|
| Goal 3: no catalogue of supported agents **or providers** | The assistant's provider is chosen from Koog's clients (OpenAI, Anthropic, Google, OpenRouter, Ollama) plus any **OpenAI-compatible** endpoint | Any model reached through those APIs works: a model ID is typed, and Koog's catalogue only suggests some. iCode still keeps no list of **CLI** agents, and the runtime still runs any of them |
| Non-goal: "an agent-specific client" | One native assistant; the terminal agents (Claude Code, Codex, OpenCode…) are untouched and need nothing from it | The assistant is a feature like Source Control, not a client for someone else's agent |
| Invariant 4: no list of supported AI agents | Unchanged in meaning: iCode never detects, names or special-cases a CLI agent | — |
| PR-ENTRY-02: no provider set-up before a workspace opens | Unchanged: the assistant is off until the user sets a provider, and nothing asks for one | The Assistant page explains and links to its settings; nothing else mentions it |
| Goal 8 / §21: no per-command approvals | Unchanged for the user's own processes. The **assistant's** changes and commands ask first (an "Allow all in this chat" option and a setting turn that off) | A model, not the user, starts them, so the approval belongs to the assistant's own UI and is not a platform policy |

What it does not change: one copy of each workspace, saving through the file service, Git as the
authority, and the runtime's process contract. The assistant's edits are file writes like any
other. A tab with unsaved edits is never written under (§4.5). The Explorer, Git and open editors
see the change through the paths that already serve the terminal's agents.

## 2. What was verified, not assumed (2026-10-01)

Koog's artifacts were fetched from Maven Central and read, sources and bytecode both:

| Fact | Evidence | Consequence |
|---|---|---|
| Koog **1.3.0** (2026-09-23) publishes `androidJvm` variants of `agents-core`, `agents-tools`, the OpenAI, Anthropic, OpenRouter and Ollama clients, `http-client-ktor`; Google's client and DeepSeek's are `1.3.0-beta` | each module's Gradle `.module` file; every AAR declares `minSdkVersion 23` | iCode's minSdk 30 is fine; depend on the modules, not the `koog-agents` umbrella (which brings Bedrock's AWS SDK, OpenTelemetry and more) |
| The loop: `AIAgent(promptExecutor, AIAgentConfig, functionalStrategy { … }, toolRegistry)`; in the strategy `llm.writeSession { requestLLMStreaming() }` streams `StreamFrame`s (`TextDelta`, `TextComplete`, `ReasoningComplete`, `ToolCallComplete`, `End`); `environment.executeTool(call, metadata)` runs a tool and turns its exception into a failed result for the model | `AIAgentFactory.kt`, `AIAgentFunctionalContextBaseCommon.kt`, `StreamFrame.kt`, `GenericAgentEnvironment.kt` | iCode writes its own loop as a functional strategy: streamed text, sequential tools, a step limit. Koog's environment, pipeline and features still apply |
| A tool is a `ToolBase<TArgs, TResult>` with a hand-made `ToolDescriptor`; `Tool` drops the call's metadata, `ToolBase.execute(args, metadata)` keeps it | `Tool.kt`, `ToolBase.kt`, `ToolCallMetadata.kt` | One adapter class (`KoogTool`) wraps every iCode tool; the call's ID reaches iCode through the metadata, for approvals |
| Every client takes a `KoogHttpClient.Factory`; Koog finds one by `ServiceLoader` when none is given | `HttpClientFactoryResolver.kt` | iCode passes `KtorKoogHttpClient.Factory(HttpClient(OkHttp))`, so nothing depends on service files surviving R8 |
| Anthropic's client refuses a model that is not in `modelVersionsMap` ("Unsupported model"); the default map is `internal` | `AnthropicLLMClient.kt:449` | A typed model is added to `AnthropicClientSettings().modelVersionsMap` |
| Clients refuse tools unless the model declares `LLMCapability.Tools`; OpenAI's needs `OpenAIEndpoint.Completions` or `.Responses` | `AbstractOpenAILLMClient.kt:246`, `OpenAILLMClient.kt:1078` | A model from Koog's catalogue keeps Koog's capabilities; a typed one gets Completion, Tools, ToolChoice, Temperature (+ Chat Completions for OpenAI) |
| Koog's catalogues are plain lists: `OpenAIModels.models`, `AnthropicModels.models`, `GoogleModels.models`, `OpenRouterModels.models`, `OllamaModels.models` | `LLModelDefinitions.kt` | Model suggestions come from Koog, without reflection, and follow Koog's upgrades |
| **Android 11–14:** Koog's Android classes checked with Animal Sniffer against the API 30 signature (`gummy-bears-api-30` 0.16.0), together with the Ktor/OkHttp/kotlinx/Jackson jars they bring | `DeepSeekLLMClient` calls `java.util.List.removeLast()` (Android 15+) — the crash the research reported. All else: JVM-only code (`java.beans` in Typesafe Config via ktor-server-core, `java.lang.management`/`instrument` in the coroutines debug agent, Conscrypt/OpenJSSE shims in OkHttp) or the `MethodHandle.invokeExact` false positive in Jackson | **The DeepSeek module is not used**; DeepSeek works through the OpenAI-compatible provider. R8 gets `-dontwarn` for the JVM-only references (§4.9) |
| Koog logs through kotlin-logging → SLF4J; its Ktor client installs no request logging | `KtorKoogHttpClient.kt` (debug lines only: open/close) | iCode ships no SLF4J provider, so prompts, files and keys never reach logcat; iCode's own log names steps, never content |
| The AARs carry no consumer R8 rules | each AAR's listing | iCode keeps `ai.koog.**` until a release build is checked on a device (§9 D9) |
| New duplicate resources: `META-INF/versions/9/module-info.class` (17 jars) | resource listing of the whole closure | One packaging exclude |
| Plain `http://` is blocked: no network security config | `AndroidManifest.xml` | A config that allows clear text to `localhost`/`127.0.0.1` only (an Ollama in the runtime); a LAN `http://` server is an owner decision (§10) |

## 3. The user's lifecycle

1. **Finding it.** The workspace rail gets **Assistant** (after Source Control). Settings get an
   **Assistant** category: "Provider, model, API key".
2. **First open, nothing set.** The page says what the assistant does: it reads and changes this
   workspace and runs commands, asking first. It names what it needs: a provider and, for most,
   an API key. One button: **Set Up Assistant**. Nothing else in iCode asks.
3. **Set up** (Assistant settings): provider (dropdown); base URL (shown for Ollama and
   OpenAI-compatible, prefilled where there is a default); model (typed, with Koog's suggestions
   for that provider); API key (masked; "Saved" once stored, never shown again; Remove).
   **Ask before changes** (on by default). **Save** checks the form, not the network. The first
   message proves the connection, and a refusal says why (§7).
4. **Chatting.** The page belongs to the open workspace: "Assistant · RevPlayer". The user types,
   and the reply streams in as Markdown. Each step the assistant takes shows as a row with its
   verb and object, e.g. "Read src/App.kt", "Searched “useState”", "Ran `npm test`" (exit 1).
   A tap opens the step's output, bounded.
5. **Approvals.** A change or a command stops the turn on a card. It shows what it will do (the
   file and the text replaced, or the command) with **Allow**, **Allow All in This Chat** and
   **Deny**. Deny tells the model it was refused, so it can say so or try another way.
6. **Stop.** While it works, Send becomes **Stop**. Stopping keeps what was said and done. Steps
   left unanswered are recorded as stopped, so the next message continues cleanly (§4.6).
7. **Leaving.** A turn is background work (architecture §14.3): the service keeps it alive, and
   its notification reads "Assistant is working… · Running `npm test`", with Cancel. A tap opens
   the Assistant. A question while iCode is out of view pops up: "Assistant needs your answer".
   A failure while away pops up, as other work's does.
8. **Another workspace.** Opening another workspace shows that workspace's chat. A running turn
   goes on in its own workspace, and only one runs at a time. "The assistant is working in
   RevPlayer" is said when another is asked of it.
9. **New Chat** (top bar, confirmed) clears this workspace's chat. A chat outlives the process.
   It is kept per workspace in iCode's storage, follows a rename, and goes with a delete.
10. **Privacy.** The page's set-up text and settings say plainly that what the assistant reads is
    sent to the chosen provider. Keys are encrypted by the Android Keystore and never leave the
    device except to that provider.

## 4. Architecture

```text
ui/screens/tools/Assistant*.kt  ──dispatch(AssistantIntent)──►  AndroidWorkbenchGateway
ui/screens/settings/AssistantSettingsScreen.kt                         │ (lock-free branch)
        ▲ snapshot.assistant (domain types only)                       ▼
domain/AssistantContract.kt, AssistantRules.kt      data/assistant/AssistantCoordinator
                                                     ├─ AssistantProviderStore (+ data/io/KeystoreCipher)
                                                     ├─ AssistantChatStore      (files/assistant/chats)
                                                     ├─ AssistantTools          (files, search, git, commands)
                                                     │    └─ WorkspaceFileSource · WorkspaceSearchEngine · CliGitService · GuestCommand
                                                     ├─ AssistantInstructions   (system prompt, AGENTS.md)
                                                     └─ AssistantEngine  ◄── data/assistant/koog/KoogAssistantEngine
                                                                              (the only package importing ai.koog)
```

### 4.1 The engine boundary

`AssistantEngine` is iCode's own. Nothing outside `data/assistant/koog` sees a Koog type.

```kotlin
internal interface AssistantEngine {
    /** One turn: [turn.input] answered, tools called, until the model stops asking for them. */
    suspend fun run(turn: AssistantTurn, events: AssistantEvents)
    fun close()
}
```

`AssistantTurn` carries the connection (provider, base URL, model, key), the instructions, the
history as domain messages, the user's input, the tools and the step limit. `AssistantEvents`
receives text deltas, each complete reply (text + tool calls), and each round of results.
Cancelling the caller's job stops the turn. Ktor cancels the request, and `GuestCommand` kills a
running command's process tree.

A later engine (an external agent over ACP, or Google's ADK if Koog falters) implements the same
interface. The coordinator, tools, stores and screens do not change.

### 4.2 The Koog adapter (`data/assistant/koog`)

- **KoogAssistantEngine**: one `HttpClient(OkHttp)` per process. One client and executor
  (`MultiLLMPromptExecutor`) per connection, rebuilt when settings change and closed when
  replaced. A new `AIAgent` per turn, closed after it.
- **The loop** (functional strategy): stream; collect complete frames into a reply; publish it;
  run its tool calls one at a time through `environment.executeTool` (the call ID in the
  metadata); append the results; repeat until a reply has no calls, or until the step limit says
  so (the turn ends with "Stopped after 40 steps").
- **KoogHistory**: domain messages ↔ Koog `Message`s, with tool-call IDs kept exactly, because
  providers match a result to its call by ID. Reasoning parts stay within a turn and are not
  stored.
- **KoogModels**: provider kind + model ID → `LLModel` (Koog's own if catalogued, else typed
  capabilities), and the suggestions per provider.
- **KoogClients**: provider kind → `LLMClient` (OpenAI, Anthropic, Google, OpenRouter, Ollama;
  OpenAI-compatible = OpenAI's client with the given base URL and `chat/completions`).
- **KoogTool**: an iCode tool as a `ToolBase<JSONObject, String>`, its descriptor made from
  the tool's parameters, its arguments handed over as JSON text.

### 4.3 Tools (`data/assistant/tools`)

Each tool is iCode code over an existing owner, with no second file, search or Git
implementation. Paths are relative to the workspace (or its `/workspace/<name>/…` path), and none
may leave it. `.git` is never written.

| Tool | Kind | Through | Bounds |
|---|---|---|---|
| `list_files(path)` | read | `java.io` under the workspace, `.git` hidden | 500 entries |
| `read_file(path, start_line, line_count)` | read | `WorkspaceFileSource.readDocument` (encoding, 5 MB limit) | 2 000 lines, numbered |
| `search_text(query, regex, path)` | read | `WorkspaceSearchEngine` (ripgrep; installs on first use as Search does) | 200 matches |
| `git_status()` / `git_diff(path, staged)` | read | `CliGitService.run` | 64 KB |
| `write_file(path, content)` | change | `WorkspaceFileSource.createFile`/`writeDocument` (atomic, keeps encoding and line endings) | refused under unsaved edits |
| `edit_file(path, old_text, new_text, replace_all)` | change | as above; `old_text` must match once unless `replace_all` | refused under unsaved edits |
| `run_command(command, timeout_seconds)` | command | `GuestCommand` in the workspace, `/bin/sh -c` | 120 s default, 600 s at most; output's head and tail, 32 KB |

After a change, the workspace refreshes the way a terminal edit does: tree, Git and the search
index. Clean open tabs reload.

### 4.4 Approvals

The coordinator wraps each change or command tool in a gate before handing it to the engine; the
engine never knows. The gate publishes the pending call (`AssistantState.approval`), suspends on
the user's answer, and is cancelled with the turn. **Ask before changes** off, or **Allow All in
This Chat**, skips it for writes and commands alike. Reads never ask.

### 4.5 Edits and the editor

Before writing, a tool asks the document session whether the file has unsaved edits
(`DocumentSessionCoordinator.blockingEdits`). If it has, the tool fails with "The user has unsaved
changes in … — ask them to save or discard first". iCode never writes under the user's own edits.
The write itself is `writeDocument`'s: fingerprint-checked, atomic, and refused if the file changed
since it was read.

### 4.6 The conversation and its storage

The source of truth is the model-facing conversation in domain types:

```kotlin
sealed interface AssistantMessage {
    data class User(val text: String)
    data class Reply(val text: String, val calls: List<AssistantCall>)
    data class Results(val results: List<AssistantResult>)
}
```

The page is drawn from it plus the live state (streaming text, the step in hand, the question).
`AssistantRules.closed` answers any call left open (stopped, failed or killed) with a failed
result, "Stopped before it ran", so the next request is valid for every provider.

`AssistantChatStore` keeps one chat per workspace as JSON (`org.json`, as the rest of iCode) in
`files/assistant/chats/<sha256 of the location>.json`. Each output is capped at 16 KB and the
file keeps its newest 400 messages. The lifecycle's move and forget hooks carry it with a rename
and remove it with a delete. A chat is saved after each reply and each round of results, so a
killed process loses at most the reply in flight.

### 4.7 Provider settings and keys

`AssistantProviderStore` owns the preferences `icode.assistant`: provider, base URL, model and Ask
before changes. The key is stored encrypted (AES/GCM, a Keystore key of its own, alias
`icode.assistant.key`). The encryption moves out of `GitCredentialStore` into
`data/io/KeystoreCipher`, shared by both and unchanged in format, so saved Git tokens still
decrypt. The snapshot carries only `hasKey`. A key this device can no longer decrypt reads as
missing, and the page asks for it again.

### 4.8 Background work and notifications

`BackgroundWorkRules` gains the assistant's turn: key `assistant`, place `Assistant`, number 903,
title "Assistant is working…", detail = the step in hand, Cancel = Stop. A pending approval sets
the item's new `question`. `WorkNotifications` posts it once, on a new high-importance channel
"Needs your answer", while iCode is out of view, and withdraws it once answered. A tap opens the
Assistant (`Arrival` → `Route.Assistant`).

### 4.9 Build

- Dependencies: `ai.koog:agents-core`, `agents-tools`, `prompt-executor-openai-client`,
  `prompt-executor-anthropic-client`, `prompt-executor-google-client` (beta),
  `prompt-executor-openrouter-client`, `prompt-executor-ollama-client`, `http-client-ktor` (1.3.0),
  `io.ktor:ktor-client-okhttp` (3.3.3, the Ktor Koog was built with). **Not**
  `prompt-executor-deepseek-client` (§2).
- R8: keep `ai.koog.**` (no consumer rules; reflective schema and serializer paths) until D9.
  Add `-dontwarn` for `java.beans.**`, `java.lang.management.**`, `java.lang.instrument.**`,
  `org.slf4j.impl.**`, `ch.qos.logback.**`, `org.conscrypt.**`, `org.openjsse.**`.
- Packaging: exclude `/META-INF/versions/9/module-info.class`.
- `res/xml/network_security_config.xml`: clear text to `localhost` and `127.0.0.1` only.

### 4.10 Threads and locks

Assistant intents take the gateway's lock-free branch, as tasks and transfers do: a turn never
holds the mutation lock. A tool's write is an outside write, like a terminal's. It is
fingerprint-checked and atomic, and it refreshes through `requestRefresh`. One turn runs at a
time, on the gateway's scope.

## 5. Contract (domain)

- `AssistantProviderKind` — OpenAI, Anthropic, Google, OpenRouter, Ollama, OpenAICompatible:
  label, whether a key is needed, whether the base URL is asked and its default.
- `AssistantSettings` — provider kind, base URL, model, `hasKey`, `askBeforeChanges`.
- `AssistantState` — settings (or null), suggestions per provider, the shown workspace and its
  messages, streaming text, the running turn (workspace, step), the approval, Allow-all, the last
  failure.
- Intents (`AssistantIntent`, lock-free): `OpenAssistant`, `SendAssistantMessage(text)`,
  `StopAssistant`, `AnswerAssistant(callId, answer)`, `NewAssistantChat`,
  `SaveAssistantSettings(kind, baseUrl, model, key?, askBeforeChanges)`,
  `RemoveAssistantSettings`.
- `AssistantRules` (pure): the form's problems, `closed(messages)`, the steps the page lists.

## 6. Failure handling

| What | What the user reads |
|---|---|
| No provider set | The page's set-up explanation |
| 401/403 from the provider | "The provider refused the API key — check it in Assistant settings" |
| 404, unknown model | "The provider has no model “x”" |
| 429 | "The provider is rate-limiting requests — try again shortly" |
| Context too long | "This chat is longer than the model accepts — start a New Chat" |
| No network / DNS / TLS | "Unable to reach api.example.com" |
| Clear text refused (LAN `http://`) | "Android blocks plain http:// to 192.168.1.5 — use https:// or a server on this phone (127.0.0.1)" |
| Tool error (missing file, ambiguous edit, unsaved edits, command timeout, no runtime) | To the model as a failed result (it can recover); on the step's row for the user |
| Step limit | "Stopped after 40 steps — send a message to continue" |
| Process killed mid-turn | The chat as last saved; open calls closed as stopped |

## 7. Not now (phase 2+, each its own plan)

- **MCP servers** as tool sources: Koog's `agents-mcp` is beta, so it sits behind an
  `AssistantToolSource` of iCode's own.
- **External agents over ACP** (OpenCode and others) as a second `AssistantEngine`. Koog's ACP
  module is JVM-oriented and beta.
- History compression (Koog's `compressHistory`) for long chats; several chats per workspace;
  several provider profiles; a diff view inside the approval card; a "finished" notification;
  attachments (images); on-device models (Koog's LiteRT client, beta).
- Trimming R8's keep of `ai.koog.**` to what is reflective, after D9.

## 8. Cycles

| # | Step | Verified by |
|---|---|---|
| A1 | This plan; architecture §2–4, §8, §14.3, §21, §25–26, PRD, user stories, Todo | Review |
| A2 | Domain contract and rules; `KeystoreCipher` out of `GitCredentialStore` | Type-check; rules tests |
| A3 | Stores (provider, chat) and tools over the owners | Type-check; tests over a temp workspace |
| A4 | The Koog adapter, compiled against the real 1.3.0 jars | Type-check; a scripted executor runs the loop: stream, two tool rounds, approval, stop |
| A5 | Coordinator, gateway branch, background item, notifications, arrival | Type-check; coordinator tests with a fake engine |
| A6 | Screens (Assistant, Assistant settings), routes, rail, settings catalog; Gradle, R8, network config | UI guard; the owner's Gradle build |
| A7 | Device bake-off and acceptance (§9) | The owner's device |

## 9. Device acceptance (the bake-off)

- D1 Debug build on Android 11 (API 30) and on the newest: set up OpenAI, Anthropic, Google,
  OpenRouter; one turn each with a read and an edit.
- D2 OpenAI-compatible: DeepSeek (`https://api.deepseek.com/v1`), and one more (Groq or a local
  server).
- D3 Ollama in the runtime at `http://127.0.0.1:11434`.
- D4 Approvals: Allow, Allow All, Deny; Ask before changes off.
- D5 Stop mid-stream and mid-command; the next message continues.
- D6 Leave the app mid-turn: notification, Cancel from it, the question pop-up, a tap back.
- D7 Swipe from Recents mid-turn; reopen: chat as last saved.
- D8 An edit to an open clean tab reloads it; to a dirty tab it is refused.
- D9 **Release build** (R8): repeat D1 with one provider; then trim keep rules.
- D10 Wrong key, unknown model, airplane mode, LAN `http://`: the messages of §6.

## 10. Owner decisions open

1. LAN `http://` model servers (an Ollama on a PC). Allowing clear text everywhere is one line
   in the network config, but it weakens nothing *only* if every other iCode connection is
   HTTPS (it is today). Default here: localhost only.
2. Whether the assistant's approval default stays "Ask before changes" (chosen here).
3. Which Koog upgrades to take: pinned to 1.3.0; each upgrade repeats D1 and D9.

## 11. Record (2026-10-01)

What was checked here, and how. No Android Gradle build can run in this container (Google's Maven
is out of reach), so each check uses what Maven Central offers.

| Check | How | Result |
|---|---|---|
| Koog's API as used | `data/` and `domain/` compiled with Kotlin 2.3.10 against Koog 1.3.0's JVM jars (their `commonMain` + `jvmCommonMain` are the Android variant's), Ktor 3.3.3 with OkHttp, and android-all 16 | No errors |
| The loop, for real | `KoogAssistantEngineTest`: Koog's own `AIAgent` and tool runner with a scripted `LLMClient` — streamed text, a tool call and its result matched by ID, a refusal returned to the model, an ID made for a call without one, the step limit, a 401 explained, cancellation, the client kept and replaced | 8 tests pass |
| The rest of the backend | `AssistantCoordinatorTest` (Allow, Deny, Allow All, Ask off, Stop mid-question, a failure, one turn at a time with two Sends in the same instant, rename and delete, settings checks), `AssistantChatStoreTest`, `AssistantToolsTest` (paths out by `..`, `/`, `~` and a link refused; read windows; writes, folders, `.git`, unsaved edits; edits once, everywhere, CRLF), `AssistantRulesTest`, `BackgroundWorkRulesTest` | All pass, 25 runs of the timing-sensitive two without a failure; the whole `data/` and `domain/` suite: 534 tests, the one failure `TreeSizeTest`'s, which fails only as root |
| Android 11–14 | Animal Sniffer, API 30 signature: Koog's Android classes and the closure (§2); and iCode's own compiled backend, with a control class proving a `List.removeLast()` call is caught | Only the DeepSeek client (excluded) and JVM-only paths (`-dontwarn`); iCode's own code clean |
| The screens | `AssistantScreen`, `AssistantParts`, `AssistantSettingsScreen`, `SettingsCatalog`, `MarkdownPreview` (with Markwon), the design system, `domain/` and `navigation/` compiled with the Compose compiler against Compose Multiplatform's desktop jars (the same `androidx.compose` API), with stand-ins only for Android-only calls (`AndroidView`, `LocalContext`, `R`, resource fonts and painters) | No errors; `RouteTest` passes |
| Guards | `tools/check-backend-consistency.sh`, `tools/check-ui-consistency.sh` | Pass; the gateway is 356 lines, under its ratchet of 358 |
| Not checked here | The Gradle build (dependency resolution, packaging, R8), lint, the 39-route capture, a device | §9 |

