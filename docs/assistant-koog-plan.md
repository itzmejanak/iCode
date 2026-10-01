# iCode Assistant on Koog — plan, as built

Status: **Round 1 (A1–A6) and round 2 (B1–B5) done (2026-10-01)**, each checked as far as this
container allows (§11). Left for the owner: the Gradle build, the release build and the device pass
(§9), and the decisions of §10.

Owner's request (2026-10-01): "make plan for koog integration, as is researched make sure to
design clean and best plan ever and implement it in clean and best way as needed to be". The
research (uploaded the same day) recommends Koog as iCode's native agent runtime behind an engine
boundary of iCode's own, with iCode owning the tools; ACP for external agents and MCP later,
behind iCode's own interfaces; Google's ADK as the contingency; a device bake-off before trusting
it. This plan keeps that shape, checks each claim it rests on against Koog's published artifacts
(§2), and follows [backend-split-rules.md](./backend-split-rules.md).

Round 2 is the owner's review of round 1 (§12): the empty chat branded as the home screen is,
pinch to zoom, a history of chats per workspace, the context size handled and configurable, files
attached, the model and its reasoning effort chosen on the chat page, the permissions there too, a
page that works like VS Code's Chat view — and "don't over-implement the Koog agent loop: Koog's
standard agent already does LLM → tool → result → LLM; rebuild only what really matters".

## 1. The decision, and what it changes

iCode gets **one built-in assistant**: chats beside the workspace. It reads, searches and changes
the workspace's files and runs commands in the runtime, using a model provider the user configures.
Koog runs the agent loop; iCode owns everything around it.

This is a new architecture decision (architecture.md §25–26). It amends rules written when iCode
meant to have no agent of its own:

| Was | Now | Why it still holds together |
|---|---|---|
| Goal 3: no catalogue of supported agents **or providers** | The assistant's provider is chosen from Koog's clients (OpenAI, Anthropic, Google, OpenRouter, Ollama) plus any **OpenAI-compatible** endpoint | Any model reached through those APIs works: a model ID can be typed, and Koog's catalogue only offers some. iCode still keeps no list of **CLI** agents, and the runtime still runs any of them |
| Non-goal: "an agent-specific client" | One native assistant; the terminal agents (Claude Code, Codex, OpenCode…) are untouched and need nothing from it | The assistant is a feature like Source Control, not a client for someone else's agent |
| Invariant 4: no list of supported AI agents | Unchanged in meaning: iCode never detects, names or special-cases a CLI agent | — |
| PR-ENTRY-02: no provider set-up before a workspace opens | Unchanged: the assistant is off until the user sets a provider, and nothing asks for one | The Assistant page explains and links to its settings; nothing else mentions it |
| Goal 8 / §21: no per-command approvals | Unchanged for the user's own processes. The **assistant's** changes and commands ask first ("Allow All in This Chat", and the chat page's permissions, turn that off) | A model, not the user, starts them, so the approval belongs to the assistant's own UI and is not a platform policy |

What it does not change: one copy of each workspace, saving through the file service, Git as the
authority, and the runtime's process contract. The assistant's edits are file writes like any
other. A tab with unsaved edits is never written under (§4.5). The Explorer, Git and open editors
see the change through the paths that already serve the terminal's agents.

## 2. What was verified, not assumed (2026-10-01)

Koog's artifacts were fetched from Maven Central and read, sources and bytecode both, and the
behaviour iCode relies on was run (§11).

| Fact | Evidence | Consequence |
|---|---|---|
| Koog **1.3.0** (2026-09-23) publishes `androidJvm` variants of `agents-core`, `agents-tools`, `agents-features-event-handler`, the OpenAI, Anthropic, OpenRouter and Ollama clients, `http-client-ktor`; Google's client and DeepSeek's are `1.3.0-beta` | each module's Gradle `.module` file; every AAR declares `minSdkVersion 23` | iCode's minSdk 30 is fine; depend on the modules, not the `koog-agents` umbrella (Bedrock's AWS SDK, OpenTelemetry and more) |
| Koog's standard agent: `singleRunStrategy()` = `nodeLLMRequest` → `nodeExecuteTools` → `nodeLLMSendToolResults`, repeated; `maxAgentIterations` counts every node run (start and finish too) | `AIAgentSimpleStrategies.kt`, `AIAgentSubgraph.kt:290` | iCode runs these nodes, not a loop of its own. `2 × steps + 1` iterations allow exactly `steps` requests |
| **Quirk:** after tool results, `singleRunStrategy` checks the reply's *text* before its tool calls, so a reply with both ("Now the tests:" + a call) ends the run with its calls unrun. After the first request it checks calls first | the edge order in `AIAgentSimpleStrategies.kt`; reproduced by a test (§11) | iCode's strategy is Koog's three nodes with that one edge pair in the other order (`KoogAssistantEngine.TURN`) — the only thing rebuilt |
| A reply with neither text nor a call matches no edge: `AIAgentStuckInTheNodeException`; the limit: `AIAgentMaxNumberOfIterationsReachedException` | `AIAgentSubgraph.kt:321`, `AIAgentException.kt` | Each is said plainly (§6) |
| The `EventHandler` feature reports each reply (`onLLMCallCompleted`, with its `ResponseMetaInfo` token counts), each tool call starting, completed, failed, or refused before it ran (`onToolValidationFailed` — also for arguments that are not JSON, with no starting event) | `EventHandlerConfigCommon.kt`, `ContextualAgentEnvironment.kt` | The page's events come from Koog's own; a call is matched by ID, or as the first still open (Koog runs a reply's calls one at a time, in order) |
| A request's tools are its registry's; calls and results of tools **not** in it are written into the prompt as text (`MissingToolsConversionStrategy.Missing`, the default) | `AIAgentLLMReadSessionCommon.kt:70` | Ask mode offers the reading tools only, and a compaction none, over the same history, with no provider refusing an unknown tool |
| **Bug in round 1:** Anthropic's client sends `max_tokens = 2048` unless told otherwise | `AnthropicMessageRequest.MAX_TOKENS_DEFAULT`, `AnthropicLLMClient.kt:451` | Anthropic's requests carry the model's output limit; elsewhere the provider's own stands, unless the user set one for a model Koog does not know (`KoogParams`, §4.2) |
| Reasoning: `OpenAIChatParams.reasoningEffort` / `OpenAIResponsesParams.reasoning` (MINIMAL, LOW, MEDIUM, HIGH — no xhigh), `AnthropicThinking.Enabled(budget)`, `GoogleThinkingConfig` (a budget for Gemini 2.x, a level LOW/HIGH for 3, never both), `OllamaParams.think`; nothing for OpenRouter or a compatible server | `OpenAIDataModels.kt:470`, the params classes | Efforts are offered only for a model declaring `Thinking`, and only these (§4.2) |
| Tool-call IDs: OpenAI's and Anthropic's are the provider's; Google's client makes a UUID; Ollama's is a hash of name, arguments and index — the same read twice in a chat has the same ID | `GoogleLLMClient.kt:719`, `OllamaConverters.kt:146` | A result is matched to its call within its own reply, never across the chat (§4.6) |
| `LLMCapability.Audio` marks Gemini's audio *input* and OpenAI's audio models alike | the catalogue, run | Not a filter: the catalogue lists every model that chats and calls tools |
| Every client takes a `KoogHttpClient.Factory`; Koog finds one by `ServiceLoader` when none is given | `HttpClientFactoryResolver.kt` | iCode passes `KtorKoogHttpClient.Factory(HttpClient(OkHttp))`, so nothing depends on service files surviving R8 |
| Anthropic's client refuses a model that is not in `modelVersionsMap`, and finds it by `LLModel` equality | `AnthropicLLMClient.kt:449` | A typed model is added to the map, and an `LLModel` carries no user limits, so it is equal wherever it is made |
| Clients refuse tools unless the model declares `LLMCapability.Tools`; OpenAI's needs `OpenAIEndpoint.Completions` or `.Responses` | `AbstractOpenAILLMClient.kt:246`, `OpenAILLMClient.kt:1078` | A catalogued model keeps Koog's capabilities; a typed one gets Completion, Tools, ToolChoice, Temperature (+ Chat Completions for OpenAI) |
| Koog's own history compression (`Prompts.summarizeInTLDR`, `nodeLLMCompressHistory`) is internal to a run | `prompt-*` sources | iCode asks for a summary with Koog's standard agent and no tools, and keeps it in the chat (§4.6) |
| **Android 11–14:** Koog's Android classes, the event handler included, checked with Animal Sniffer against the API 30 signature | `DeepSeekLLMClient` calls `java.util.List.removeLast()` (Android 15+). All else: JVM-only code or Jackson's `MethodHandle.invokeExact` false positive | **The DeepSeek module is not used**; DeepSeek works through the OpenAI-compatible provider. R8 gets `-dontwarn` for the JVM-only references (§4.9) |
| Koog logs through kotlin-logging → SLF4J; its Ktor client installs no request logging | `KtorKoogHttpClient.kt` | iCode ships no SLF4J provider, so prompts, files and keys never reach logcat; iCode's own log names steps, never content |
| The AARs carry no consumer R8 rules; 17 jars carry `META-INF/versions/9/module-info.class` | each AAR's listing | Keep `ai.koog.**` until D9; one packaging exclude |
| Plain `http://` is blocked: no network security config | `AndroidManifest.xml` | Clear text to `localhost`/`127.0.0.1` only (an Ollama in the runtime); a LAN server is an owner decision (§10) |

## 3. The user's lifecycle

1. **Finding it.** The workspace rail has **Assistant** (after Source Control), accented while it
   works. Settings have an **Assistant** category: "Provider, model, API key".
2. **First open, nothing set.** The iCode mark, what the assistant does (it reads and changes this
   workspace and runs commands, asking first), what it needs and that what it reads is sent to the
   provider. One button: **Set Up Assistant**. Nothing else in iCode asks.
3. **Set up** (Assistant settings): provider; the server's address where one is asked; the model —
   typed, or a tap on one of Koog's for that provider, whose limits are then said ("200k tokens of
   context, up to 64k in a reply"); for a model Koog does not know, its **Context Window** and
   **Output Limit**, optional ("128k"); the API key (masked; "Saved" once stored, never shown
   again; Replace). **Save** checks the form, not the network; the first message proves the
   connection, and a refusal says why (§6).
4. **The page**, as VS Code's Chat view. The top bar names the chat ("New chat" until something is
   said) over the workspace's name, with **Chats**, **New chat** and ⋮ (**Compact Chat**,
   **Assistant Settings**). An empty chat is the home screen's: the mark over its letterpress,
   what the assistant will do, and three things to ask — a tap puts one in the composer.
5. **The composer**: the files going with the message (each with ×), the message ("Describe what
   to build", or in Ask "Ask about RevPlayer"), and a toolbar — **+** (attach), the mode
   (**Agent**, or **Ask**: it reads and changes nothing), the model with its effort ("o3 · High",
   "Auto" for the provider's own), the context meter, Send or Stop. Under it: the workspace, and
   the permissions — **Ask before changes** (default) or **Allow all changes**.
6. **Choosing.** The model chip opens **Model**: the reasoning levels where the model has them
   (Auto, then the provider's: Minimal–High for OpenAI, Low–Max for Anthropic, Low/High or
   Low/Medium/High for Gemini, On for Ollama), the models chosen before, the provider's catalogue
   with each one's context, "Use “x”" for a typed ID, and the provider's settings.
7. **Attaching.** **+** opens **Attach Files**: the open file first, then the workspace's files as
   Quick Open ranks them; up to five. Each goes with the message as its text then — an open tab's
   as shown, unsaved edits and all — at most 64 000 characters (its start and end), and stays with
   the message in the chat.
8. **Chatting.** Each reply shows as Markdown when it comes; each step as a row with its verb and
   object ("Read src/App.kt", "Ran `npm test`"), its output a tap away. Two fingers pinch the
   chat's text larger or smaller, as in the editor.
9. **Approvals.** A change or a command waits on a card: the file and the text replaced, or the
   command, with **Deny**, **Allow All** (this chat) and **Allow**. Deny tells the model it was
   refused, so it can say so or try another way.
10. **Stop.** While it works, Send becomes **Stop**. What was said and done stays; steps that had
    not run are recorded as stopped, so the next message continues cleanly (§4.6).
11. **Context.** The meter is the last request's share of the model's window (amber from 80 %); a
    tap shows its tokens and **Compact Chat**. Before a message is sent to a chat at 80 % or more,
    the model summarizes the chat first ("Compacting the chat…"); later turns send the summary and
    what came after it. A divider marks the place, the summary a tap away; the page keeps
    everything.
12. **Chats.** **Chats** lists the workspace's chats newest first — title (the first message),
    "3w ago" — the open one marked, the one working "Working…". A tap opens one; the bin deletes
    one after a confirmation (not the one working). **New chat** opens an empty one, listed once
    something is said in it. The page reopens on the chat it showed; in another workspace, on the
    one working there, else the newest.
13. **Leaving.** A turn is background work (architecture §14.3): the service keeps it alive, its
    notification reads "Assistant is working… · Running `npm test`", with Cancel; a question while
    iCode is out of view pops up; a tap opens the Assistant.
14. **Another workspace, another chat.** One turn runs at a time, in its own chat; another chat or
    workspace shows "Working in another chat of RevPlayer: …", and Send waits. A workspace the
    assistant works in is not renamed or deleted until it stops (`WorkspaceUsage`); its chats
    follow a rename and go with a delete.
15. **Privacy.** The set-up and the settings say plainly that what the assistant reads — attached
    files included — is sent to the chosen provider. Keys are encrypted by the Android Keystore and
    never leave the device except to that provider.

## 4. Architecture

```text
ui/screens/tools/Assistant{Screen,ChatView,Composer,Sheets,Parts}.kt ──dispatch(AssistantIntent)──► AndroidWorkbenchGateway
ui/screens/settings/AssistantSettingsScreen.kt                                                         │ (lock-free branch)
        ▲ snapshot.assistant (domain types only)                                                       ▼
domain/AssistantContract.kt, AssistantRules.kt              data/assistant/AssistantCoordinator  (intents, in order; settings; lifecycle hooks)
                                                             ├─ AssistantPage        what is shown
                                                             ├─ AssistantTurns       the turn: job, events, approvals, compaction
                                                             │    ├─ AssistantAttachments, AssistantInstructions
                                                             │    └─ tools/ (files, search, git, commands) over the owners
                                                             ├─ AssistantChats ─ AssistantChatStore   (files/assistant/chats)
                                                             ├─ AssistantProviderStore (+ data/io/KeystoreCipher)
                                                             └─ AssistantEngine ◄── data/assistant/koog/KoogAssistantEngine
                                                                                     (the only package importing ai.koog)
```

### 4.1 The engine boundary

`AssistantEngine` is iCode's own. Nothing outside `data/assistant/koog` sees a Koog type.

```kotlin
internal interface AssistantEngine {
    val catalogue: Map<AssistantProviderKind, List<AssistantModel>>
    fun model(kind: AssistantProviderKind, id: String, contextWindow: Long?, maxOutput: Long?): AssistantModel
    suspend fun run(turn: AssistantTurnRequest, events: AssistantEvents)
    suspend fun compact(connection: AssistantConnection, model: AssistantModel, instructions: String, history: List<AssistantMessage>): String
}
```

`AssistantTurnRequest` carries the connection (provider, address, model, key — never in its
`toString`), the model with its limits, the effort, the instructions (with a compacted chat's
summary), the history since the last compaction, the user's message with its files, the tools and
the step limit. `AssistantEvents` receives each reply, each call as it starts, each result, and the
tokens each request used. Cancelling the caller's job stops the turn: Ktor cancels the request, and
`GuestCommand` kills a running command's process tree.

A later engine (an external agent over ACP, or Google's ADK if Koog falters) implements the same
interface. The coordinator, tools, stores and screens do not change.

### 4.2 The Koog adapter (`data/assistant/koog`)

- **KoogAssistantEngine**: one `HttpClient(OkHttp)` per process; one client per connection, kept
  between turns, closed when replaced. Per turn, Koog's `AIAgent` with
  - the strategy `TURN`: Koog's `nodeLLMRequest`, `nodeExecuteTools`, `nodeLLMSendToolResults`,
    tool calls checked before text after every request (§2's quirk) — no loop of iCode's;
  - `maxAgentIterations = 2 × maxSteps + 1` (40 requests);
  - the `EventHandler` feature mapped to `AssistantEvents`;
  - for a compaction, the same agent with no tools and three iterations, asked
    `AssistantInstructions.COMPACT_REQUEST`.
- **KoogParams**: each request's parameters. Anthropic: `max_tokens` = the model's output limit
  (8 192 for one Koog does not know, unless the user set one), at most 32 000; a thinking budget of
  4 000 / 12 000 / 24 000 or, for Max, all but 4 096, at least 1 024. OpenAI: `reasoningEffort`
  (Chat Completions) or `reasoning` (a Responses-only model). Gemini 2.x: a budget of
  1 024 / 8 192 / 24 576; Gemini 3: LOW or HIGH. Ollama: `think`. Elsewhere, a typed model's output
  limit where the user set one.
- **KoogModels**: the catalogue per provider (every model that chats and calls tools, with Koog's
  limits), a model by ID (Koog's instance, or a typed one with the user's limits), and the efforts
  each can take (§2).
- **KoogHistory**: domain messages ↔ Koog `Message`s, tool-call IDs kept exactly, an ID made for a
  call that came without one; a message's files written into its text
  (`AssistantInstructions.message`); the tokens of a reply (input + output).
- **KoogClients**: provider kind → `LLMClient` (OpenAI-compatible = OpenAI's client with the given
  address and `chat/completions`).
- **KoogTool**: an iCode tool as a `ToolBase<String, String>`, its descriptor made from the tool's
  parameters, its arguments the JSON text the model wrote, its result the text itself.
- **KoogFailures**: what stopped a turn, in the page's words (§6).

### 4.3 Tools (`data/assistant/tools`)

Each tool is iCode code over an existing owner, with no second file, search or Git
implementation. Paths are relative to the workspace (or its `/workspace/<name>/…` path), and none
may leave it. `.git` is never written. **Ask** offers the reading tools only.

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

Attachments (`AssistantAttachments`) pass through the same path check (`AssistantWorkspace`): a
file outside the workspace, a folder or a binary file is refused before anything is sent.

### 4.4 Approvals and permissions

`AssistantTurns` wraps each change or command tool in a gate before handing it to the engine; the
engine never knows. The gate publishes the pending call (`AssistantState.approval`), suspends on
the user's answer, and is cancelled with the turn. The permissions on the chat page ("Allow all
changes"), or **Allow All in This Chat**, skip it for writes and commands alike. Reads never ask.

### 4.5 Edits and the editor

Before writing, a tool asks the document session whether the file has unsaved edits
(`DocumentSessionCoordinator.blockingEdits`). If it has, the tool fails with "The user has unsaved
changes in … — ask them to save or discard first". The write itself is `writeDocument`'s:
fingerprint-checked, atomic, and refused if the file changed since it was read.

### 4.6 The chats and their storage

The source of truth is the model-facing conversation in domain types:

```kotlin
sealed interface AssistantMessage {
    data class User(val text: String, val attachments: List<AssistantAttachment> = emptyList())
    data class Reply(val text: String, val calls: List<AssistantCall> = emptyList())
    data class Results(val results: List<AssistantResult>)   // right after its reply
    data class Compacted(val summary: String)                 // the model goes on from here
}
```

- `AssistantRules.closed` answers any call left open (stopped, failed or killed) with "Stopped
  before it ran", in its reply's order, and drops results that follow no reply: what is saved and
  sent is valid for every provider. Results are matched within their reply (§2: Ollama's IDs).
- `AssistantRules.forModel` is what a turn sends: the last `Compacted` summary (into the system
  instructions) and the messages after it. A marker made for a message still waiting for its
  answer goes before that message (`AssistantChats.compacted`).
- `AssistantChatStore`: `files/assistant/chats/<sha256 of the location>/` holds `index.json`
  (id, title, time — what **Chats** lists) and one `<uuid>.json` per chat (its messages, the last
  request's tokens, and its own title and time, so an index that cannot be read is made again from
  the chats). Every file is written whole and renamed into place. Each output is capped at 16 KB;
  a chat keeps its newest 400 messages from something the user said; its title, once given, stays.
  An empty chat is never saved. The round-1 file (`<sha256>.json`) becomes the workspace's first
  chat. A chat ID is iCode's own UUID, never a path.
- `AssistantChats` holds the chats read in this process and "Allow All in This Chat"; each change
  is saved under its lock, so two never reach a file in the other order. A killed process loses at
  most the reply in flight. The lifecycle's move and forget hooks carry the chats with a rename and
  remove them with a delete.

### 4.7 Provider settings and keys

`AssistantProviderStore` owns the preferences `icode.assistant`: provider, address, model, the
permissions (`ask_before_changes`), effort, mode, each model's limits (`limits.<PROVIDER>.<model>`)
and the models chosen before per provider (`recent_models.<PROVIDER>`, eight). The key is stored
encrypted (AES/GCM, a Keystore key of its own, alias `icode.assistant.key`, through
`data/io/KeystoreCipher`, shared with `GitCredentialStore`). The snapshot carries only `hasKey`. A
key this device can no longer decrypt reads as missing, and the page asks for it again.

### 4.8 Background work and notifications

`BackgroundWorkRules` has the assistant's turn: key `assistant`, place `Assistant`, number 903,
title "Assistant is working…", detail = the step in hand, Cancel = Stop. A pending approval sets
the item's `question`; `WorkNotifications` posts it on the "Needs your answer" channel while iCode
is out of view. A tap opens the Assistant (`Arrival` → `Route.Assistant`).

### 4.9 Build

- Dependencies: `ai.koog:agents-core`, `agents-tools`, `agents-features-event-handler`,
  `prompt-executor-openai-client`, `prompt-executor-anthropic-client`,
  `prompt-executor-google-client` (beta), `prompt-executor-openrouter-client`,
  `prompt-executor-ollama-client`, `http-client-ktor` (1.3.0), `io.ktor:ktor-client-okhttp`
  (3.3.3, the Ktor Koog was built with). **Not** `prompt-executor-deepseek-client` (§2).
- R8: keep `ai.koog.**` until D9; `-dontwarn` for `java.beans.**`, `java.lang.management.**`,
  `java.lang.instrument.**`, `org.slf4j.impl.**`, `ch.qos.logback.**`, `org.conscrypt.**`,
  `org.openjsse.**`.
- Packaging: exclude `/META-INF/versions/9/module-info.class`.
- `res/xml/network_security_config.xml`: clear text to `localhost` and `127.0.0.1` only.

### 4.10 Threads and order

Assistant intents take the gateway's lock-free branch, as tasks and transfers do. The coordinator
applies them **in the order they were made**, one channel, one consumer — "New chat" then Send is
never sent in the chat before — and nothing on that lane waits on a turn: a turn is its own job,
so Stop and an answer are never held up. One turn runs at a time (the job decides, not the page).
A tool's write is an outside write, like a terminal's: fingerprint-checked, atomic, refreshed
through `requestRefresh`. A workspace's rename or delete stops a turn started there since the
lifecycle's check, and waits for its chat to be saved, before the chats move.

### 4.11 The screens

- `AssistantScreen`: the page; its sheets (**Chats**, **Model**, **Attach Files**) are the page's
  own, since **Attach Files** edits the composer's draft, and are drawn as sheet routes are —
  `IcodeSheetOverlay` (dimmed, a tap outside or Back closes), the page blurred under it.
- `AssistantChatView`: the chat inside `PinchToScaleText`, every row's text scaled by
  `LocalCodeTextScale`; the branded welcome (`IcodeWatermark`, `IcodeBrandMark`); the step line;
  the approval card.
- `AssistantComposer`, `AssistantSheets`, `AssistantParts` as named.
- Design system, made once for every screen: `IcodeChoiceMenu` (the dropdown's menu, now shared by
  `IcodeDropdown`), `IcodeChoiceChip` (a toolbar choice), `IcodeSheetOverlay` (now
  `NavigationHost`'s too).

## 5. Contract (domain)

- `AssistantProviderKind` — OpenAI, Anthropic, Google, OpenRouter, Ollama, OpenAICompatible.
- `AssistantEffort` (Minimal, Low, Medium, High, Max, On), `AssistantMode` (Agent, Ask),
  `AssistantModel` (id, context window, output limit, efforts).
- `AssistantSettings` — provider, address, model, `hasKey`, `askBeforeChanges`, effort, mode, the
  model's limits as the user set them.
- `AssistantState` — settings, catalogue, the model as known now, recent models, the workspace, its
  sessions, the open session and its messages, the last request's tokens, the turn (workspace,
  name, session, step), the approval, Allow All, the last failure.
- Intents (`AssistantIntent`, lock-free, in order): `OpenAssistant`, `OpenAssistantSession`,
  `DeleteAssistantSession`, `NewAssistantChat`, `SendAssistantMessage(text, attachments)`,
  `StopAssistant`, `AnswerAssistant`, `CompactAssistantChat`, `ChooseAssistantModel`,
  `ChooseAssistantEffort`, `ChooseAssistantMode`, `SetAssistantAskFirst`,
  `SaveAssistantSettings(kind, baseUrl, model, key?, contextWindow, maxOutput)` (the key masked in
  its `toString`), `RemoveAssistantSettings`.
- `AssistantRules` (pure): the page's entries, `closed`, `forModel`, the meter's share, a chat's
  title, the effort sent, the form's problems, token counts typed ("200k") and shown.

## 6. Failure handling

| What | What the user reads |
|---|---|
| No provider set | The page's set-up explanation; a message sent anyway: "Set up the assistant first: its settings name a provider and a model" |
| 401/403 from the provider | "The provider refused the API key — check it in Assistant settings" |
| 404, unknown model | "The provider has no model “x”" |
| 429 | "The provider is limiting requests — try again shortly" |
| Context too long | "This chat is longer than the model accepts — compact it or start a new chat" |
| No network / DNS / TLS | "Unable to reach api.example.com", and the like |
| Clear text refused (LAN `http://`) | "Android blocks plain http:// to 192.168.1.5 — use https://, or a server on this phone (127.0.0.1)" |
| A file that cannot be attached | "secret.txt is not in this workspace", "x is not a file", "x is a binary file", "Unable to read x" — nothing is sent |
| Tool error (missing file, ambiguous edit, unsaved edits, command timeout, no runtime) | To the model as a failed result (it can recover); on the step's row for the user |
| Step limit (40 requests) | "Stopped at the step limit — send a message to continue" |
| A reply with nothing in it | "The model sent an empty reply — send a message to continue" |
| A compaction without a summary | "The model sent an empty summary — try again" |
| Process killed mid-turn | The chat as last saved; open calls closed as stopped |

## 7. Not now (phase 2+, each its own plan)

- **MCP servers** as tool sources: Koog's `agents-mcp` is beta, so it would sit behind an
  `AssistantToolSource` of iCode's own.
- **External agents over ACP** (OpenCode and others) as a second `AssistantEngine`.
- Token-by-token text: Koog's standard agent asks for whole replies; the page shows the step in
  hand meanwhile. Streaming would need Koog's streaming nodes in `TURN`, worth it only if the
  device pass finds whole replies slow to read.
- xhigh reasoning (not in Koog 1.3.0's `ReasoningEffort`); efforts for OpenRouter and compatible
  servers (no field in Koog's clients).
- Several provider profiles; a diff view inside the approval card; a "finished" notification;
  images as attachments; on-device models (Koog's LiteRT client, beta); renaming a chat.
- Trimming R8's keep of `ai.koog.**` to what is reflective, after D9.

## 8. Cycles

| # | Step | Verified by |
|---|---|---|
| A1 | This plan; architecture §2–4, §8, §14.3, §21, §25–26, PRD, user stories, Todo | Review |
| A2 | Domain contract and rules; `KeystoreCipher` out of `GitCredentialStore` | Type-check; rules tests |
| A3 | Stores (provider, chat) and tools over the owners | Type-check; tests over a temp workspace |
| A4 | The Koog adapter, compiled against the real 1.3.0 jars | Type-check; Koog's agent run with a scripted client |
| A5 | Coordinator, gateway branch, background item, notifications, arrival | Type-check; coordinator tests with a fake engine |
| A6 | Screens, routes, rail, settings catalog; Gradle, R8, network config | UI guard |
| B1 | Engine on Koog's standard nodes and events; output limits; reasoning; usage; compaction | Type-check; engine and model tests; API 30 audit of the event handler |
| B2 | Contract and stores: sessions (index, rebuild, migration), attachments, compaction marker, usage, model limits, effort, mode, recent models | Type-check; store and rules tests |
| B3 | Coordinator, page and turns: sessions, attachments, Ask, model and effort, auto and manual compaction, ordered intents, the lifecycle hooks | Coordinator tests, 20 runs |
| B4 | Screens: chat page, composer, sheets, branded welcome, pinch zoom, settings limits; design-system choice menu, chip and sheet overlay | Compose type-check; UI guard |
| B5 | This plan as built; PRD, user stories, architecture | Review |
| — | Device bake-off and acceptance (§9) | The owner's device |

## 9. Device acceptance (the bake-off)

- D1 Debug build on Android 11 (API 30) and on the newest: set up OpenAI, Anthropic, Google,
  OpenRouter; one turn each with a read and an edit; a long answer from Anthropic is not cut short.
- D2 OpenAI-compatible: DeepSeek (`https://api.deepseek.com/v1`), and one more (Groq or a local
  server), with its limits set.
- D3 Ollama in the runtime at `http://127.0.0.1:11434`, Think on.
- D4 Approvals: Allow, Allow All, Deny; the chat page's permissions both ways.
- D5 Stop mid-request and mid-command; the next message continues.
- D6 Leave the app mid-turn: notification, Cancel from it, the question pop-up, a tap back.
- D7 Swipe from Recents mid-turn; reopen: the chat as last saved, listed in Chats.
- D8 An edit to an open clean tab reloads it; to a dirty tab it is refused.
- D9 **Release build** (R8): repeat D1 with one provider; then trim keep rules.
- D10 Wrong key, unknown model, airplane mode, LAN `http://`: the messages of §6.
- D11 Chats: two chats, switch, delete one, New chat; rename the workspace: they follow.
- D12 Attach the open file with unsaved edits and two more: the model reads them; a sixth is not
  offered.
- D13 Ask mode: no change offered; a reasoning model at each of its efforts.
- D14 A long chat: the meter climbs, compaction happens at 80 %, the chat goes on; Compact Chat by
  hand.
- D15 Pinch the chat larger and smaller; the welcome's mark and letterpress in each theme; the
  composer on a 360 dp phone with a long model ID.

## 10. Owner decisions open

1. LAN `http://` model servers (an Ollama on a PC). Allowing clear text everywhere is one line in
   the network config. Default here: localhost only.
2. Whether the permissions default stays "Ask before changes" (chosen here).
3. Which Koog upgrades to take: pinned to 1.3.0; each upgrade repeats D1, D9 and §2's quirk test
   (if Koog fixes its edge order, `TURN` can become `singleRunStrategy()` again).

## 11. Record (2026-10-01)

No Android Gradle build can run in this container: Google's Maven is out of reach (403), so each
check uses what Maven Central offers.

| Check | How | Result |
|---|---|---|
| Koog's API as used | `data/` and `domain/` compiled with Kotlin 2.3.10 against Koog 1.3.0's JVM jars (their `commonMain` + `jvmCommonMain` are the Android variant's), Ktor 3.3.3 with OkHttp, android-all 16 | No errors |
| The engine, for real | `KoogAssistantEngineTest` (13): Koog's own agent, tool runner and events with a scripted `LLMClient` — a call run and its result matched by ID; **a reply with text and a call after results has its call run** (fails on `singleRunStrategy`); calls without IDs, in order; an unknown tool and arguments that are not JSON answered without running; the step limit at exactly its requests; an empty reply; attachments in the message and a tool not offered sent as text; compaction with no tools; Anthropic's output limit and thinking; a refused key; cancellation; the client kept and replaced. `KoogModelsTest` (7): the catalogue, Anthropic's equality, typed models, efforts and every provider's parameters | All pass |
| The rest of the backend | `AssistantCoordinatorTest` (15: approvals, permissions, Stop, failures, one turn at a time, chats opened and deleted, a chat in use kept, attachments with an open tab's text and one from outside refused, Ask, model and effort, Compact, compaction at 80 %, rename and delete during a turn, settings and limits), `AssistantChatStoreTest` (10: sessions, titles, order, mid-turn close, bounds, delete, move and forget, an index rebuilt, round 1's file migrated, nothing of a chat in the log, a chat ID never a path), `AssistantRulesTest` (15), `AssistantToolsTest`, `BackgroundWorkRulesTest`, `RelativeTimeTest` | All pass; the coordinator's 20 runs without a failure; the whole `data/` and `domain/` suite: 567 tests, the one failure `TreeSizeTest`'s, which fails only as root |
| Android 11–14 | Animal Sniffer, API 30 signature: Koog's Android classes and closure (§2), the event handler added in round 2 | Only the DeepSeek client (excluded) and JVM-only paths (`-dontwarn`) |
| The screens | The assistant's five screens and its settings, `SettingsCatalog`, `NavigationHost`, Quick Open's ranking, the design system, `domain/` and `navigation/`, compiled with the Compose compiler against Compose Multiplatform's desktop jars, with `MarkdownPreview` (Markwon) and stand-ins only for Android-only calls (`AndroidView`, `LocalContext`, `R`, resource fonts and painters, `BackHandler`) | No errors, no warnings in the assistant's files |
| Guards | `tools/check-backend-consistency.sh`, `tools/check-ui-consistency.sh` | Pass; every assistant file under 300 lines; the gateway at its ratchet, 356 |
| Not checked here | The Gradle build (dependency resolution, packaging, R8), lint, screenshots (Compose's desktop renderer needs `androidx.collection`, only on Google's Maven), a device | §9 |

## 12. Round 2 — the owner's review (2026-10-01)

### 12.1 Every gap, its decision, as built

| # | Gap | Decision, as built |
|---|---|---|
| R1 | The loop was iCode's own functional strategy (streaming, frames, a step counter) | Koog's standard nodes and its `EventHandler`; its iteration limit. One edge pair reordered, for §2's quirk, and nothing else rebuilt. Token-by-token text given up (§7) |
| R2 | Anthropic's replies cut at 2 048 tokens | Anthropic's requests carry the model's output limit; a typed model's, set by the user, goes to any provider (`KoogParams`) |
| R3 | No reasoning level | Offered where Koog can send one, for a model that reasons; "Auto" sends nothing; chosen with the model |
| R4 | Context neither shown nor handled | The window and output limit (Koog's, or the user's for a typed model); the meter; compaction at 80 % before the next message and on request, the summary kept in the chat |
| R5 | One chat per workspace | Chats per workspace: listed, opened, deleted, new; round 1's chat migrated |
| R6 | No attachments | **+** on the composer: up to five files, the open one first, Quick Open's ranking; their text, bounded, stays with the message |
| R7 | The page was a list and a field | VS Code's Chat view on a phone: the chat's title, Chats, New chat; the composer's toolbar and footer |
| R8 | — | Mode: Agent, or Ask (the reading tools only; the instructions say so) |
| R9 | No model choice on the page | The model sheet: efforts, recent models, the catalogue with each one's context, any typed ID; the provider stays in settings |
| R10 | Empty chat unbranded | The mark over the letterpress, as the home screen and the empty editor |
| R11 | No pinch zoom | `PinchToScaleText` around the chat; every row's text scaled |
| R12 | Times read "60d ago" | `RelativeTime` gains weeks, months and years everywhere |
| R13 | Permissions were in the settings (owner, mid-round) | On the chat page's footer: "Ask before changes" / "Allow all changes"; gone from the settings form |
| R14 | Found while building: Ollama's call IDs repeat across replies; a "New chat" then Send could be applied out of order | Results matched within their reply; the coordinator applies intents in order |
