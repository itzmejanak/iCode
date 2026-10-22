# iCode Assistant on Koog — plan, as built

Status: **Round 1 (A1–A6) and round 2 (B1–B5) done (2026-10-01)**, each checked as far as this
container allows (§11); rounds 3 (§13) and 4 — the runtime's foundation and OpenRouter's streaming,
[runtime-foundation-plan.md](./runtime-foundation-plan.md) — done (2026-10-02); streaming for OpenAI
and compatible servers, and the chat's steps (§17), done 2026-10-08. Left for the owner: the Gradle
build, the release build and the device pass (§9), and the decisions of §10.

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
| **Quirk:** after tool results, `singleRunStrategy` checks the reply's *text* before its tool calls, so a reply with both ("Now the tests:" + a call) ends the run with its calls unrun. After the first request it checks calls first | the edge order in `AIAgentSimpleStrategies.kt`; reproduced by a test (§11) | iCode's strategy is Koog's three nodes with that one edge pair in the other order (`KoogTurn.strategy`) — the only thing rebuilt |
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
   works. Settings have an **Assistant** category: "Providers, models, API keys".
2. **First open, nothing set.** The iCode mark, what the assistant does (it reads and changes this
   workspace and runs commands, asking first), what it needs and that what it reads is sent to the
   provider. One button: **Set Up Assistant**. Nothing else in iCode asks.
3. **Set up** (§13): **Assistant** settings list the providers — each with its kind, its models'
   count, "In use" for the chat's, "No key" where one is missing — and **+** adds one; with none,
   the iCode mark and **Add Provider**. A **provider's page**: its kind (chosen when it is added),
   its name (the kind's label unless typed: "DeepSeek"), the server's address where one is asked,
   the API key (masked; "Saved, never shown again" once stored; Replace), and its **Models** —
   each with what is known of it ("200k context · reasons · images"), × to take it off, a tap for
   its options. **Add Models** asks the server for its real list with the address and key as typed
   (proving them, or saying why not), then shows the models iCode knows for the kind, ticked to
   add; any ID can be typed. A model iCode does not know has options: **Context Window**, **Output
   Limit**, **Reads Images**, **Reasons**. **Save** checks the form; Back with changes asks
   before discarding them; **Remove Provider** confirms.
4. **The page**, as VS Code's Chat view. The top bar names the chat ("New chat" until something is
   said) over the workspace's name, with **Chats**, **New chat** and ⋮ (**Compact Chat**,
   **Assistant Settings**). An empty chat is the home screen's: the mark over its letterpress,
   what the assistant will do, and three things to ask — a tap puts one in the composer.
5. **The composer**: the files going with the message (each with ×), the message ("Describe what
   to build", or in Ask "Ask about RevPlayer"), and a toolbar — **+** (attach), the mode
   (**Agent**, or **Ask**: it reads and changes nothing), the model with its effort ("o3 · High",
   "Auto" for the provider's own), Send or Stop. Under it: the workspace, the permissions — **Ask
   before changes** (default) or **Allow all changes** — and, at the right, always, the context
   meter ("0 / 200k" before the first message). A photo alone can be sent.
6. **Choosing.** The model chip opens **Model**: the reasoning levels where the model has them
   (Auto, then the provider's: Minimal–High for OpenAI, Low–Max for Anthropic, Low/High or
   Low/Medium/High for Gemini, On for Ollama), then every provider's models under its name, each
   with what it reads and whether it reasons, the chosen one ticked; searchable past eight;
   **Manage Providers** opens the settings.
7. **Attaching.** **+** opens **Attach**: **Camera**, **Photos** (the system Photo Picker) and
   **Files** (the system document picker: the phone's storage, Drive, Downloads…) across the top —
   no permission asked; no Camera tile on a phone without one — then the workspace's files, the
   open one first, searchable; up to five in all, picks past five said. A text file goes as its
   text then (an open tab's as shown, at most 64 000 characters); a photo as a JPEG and a PDF as
   itself, to a model that reads them — the composer says otherwise, and Send waits. Photos show
   as thumbnails in the composer and in the chat. A tap on a photo shows it in the image viewer; a
   tap on a workspace file opens it in the editor ([file-viewers-plan.md](./file-viewers-plan.md)).
8. **Chatting.** Each reply shows as Markdown when it comes; each step as a row with its verb and
   object ("Read src/App.kt", "Ran `npm test`"), its output a tap away; the answer a turn ends on
   has **Copy**. A turn that failed before any reply offers **Try Again**. Two fingers pinch the
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
    files included — is sent to the provider of the chat's model. Keys are encrypted by the Android
    Keystore, one per provider, and never leave the device except to that provider.

## 4. Architecture

```text
ui/screens/tools/Assistant{Screen,ChatView,Composer,Sheets,Parts}.kt ──dispatch(AssistantIntent)──► AndroidICodeGateway
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
  - the strategy `KoogTurn.strategy`: a request, Koog's `nodeExecuteTools`, then the results sent
    as Koog's `nodeLLMSendToolResults` sends them, tool calls checked before text after every
    request (§2's quirk) — no loop of iCode's. For OpenRouter, OpenAI's Chat Completions models
    and OpenAI-compatible servers (`KoogTurn.streams`) both requests stream (`KoogTurn.streamed`,
    put together by `KoogStream`): the page shows the reply as it is written; a server that refuses
    the stream is asked the same request whole, and not streamed again
    ([runtime-foundation-plan.md](./runtime-foundation-plan.md) §3.8, D8);
  - `maxAgentIterations = 2 × maxSteps + 1` (40 requests);
  - the `EventHandler` feature and the streamed requests mapped to `AssistantEvents`
    (`KoogTurn.Reporter`);
  - for a compaction, the same agent with no tools and three iterations, asked
    `AssistantInstructions.COMPACT_REQUEST`, never streamed.
- **KoogParams**: each request's parameters. Anthropic: `max_tokens` = the model's output limit
  (8 192 for one Koog does not know, unless the user set one), at most 32 000; a thinking budget of
  4 000 / 12 000 / 24 000 or, for Max, all but 4 096, at least 1 024. OpenAI: `reasoningEffort`
  (Chat Completions) or `reasoning` (a Responses-only model). Gemini 2.x: a budget of
  1 024 / 8 192 / 24 576; Gemini 3: LOW or HIGH. Ollama: `think`. Elsewhere, a typed model's output
  limit where the user set one — to an OpenAI-compatible server as `max_tokens` too.
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
| `run_command(command, timeout_seconds)` | command | `AssistantShell`: bash with the login environment, in the chat's directory, seeing only this workspace under `/workspace` | 120 s default, 600 s at most; output's head and tail, 32 KB; its status in words |
| `start_process(command, name, wait_seconds)` | command | `RuntimeProcesses`: a managed process, as `run_command` runs it, its input open | 8 at once; answers its ID, ports and first output |
| `process_status(id)` | read | `RuntimeProcesses` | the output since the last look, 16 KB |
| `send_process_input(id, text, newline)` | command | `RuntimeProcesses` | answers what it printed in the next second |
| `stop_process(id)` | command | `RuntimeProcesses` | SIGTERM, SIGKILL after 3 s |
| `run_android_app()` | command | `RunCoordinator`: Build & Run, as the Run tab's ▶ ([run-tab-plan.md](./run-tab-plan.md)) | answers the build's process ID |
| `list_run_commands()` | read | `RunCoordinator`: the Run tab's apps and saved commands ([run-tab-plan.md](./run-tab-plan.md) §2.5) | — |
| `save_run_command(command, name, id)` / `delete_run_command(id)` | change | `RunCoordinator`, as the Run tab's dialogs | an id the workspace has no command by is refused |

A write or an edit answers how many lines it added and removed, as `git diff --numstat` counts
them (`LineChanges.between`, Myers' shortest edit): "Created src/App.kt: +2 lines", "Edited a.kt in
2 places: +2 −2 lines". The tool keeps the count (`AssistantFileChanger.lastChanges`) and the turn
carries it to the call's result (`AssistantResult.changes`), so the page shows it too. Each tool
also names, from a call's arguments, the file it touches (`file`: `read_file`, `write_file`,
`edit_file`) and the command it runs (`command`: `run_command`, `start_process`); the call keeps
both (`AssistantCall.file`, `.command`), none for arguments that could not be read.

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
  the chats). Every file is written whole and renamed into place. Each output is kept up to
  48 000 characters — above what a command or a process answers (`run_command`: 32 000, and its
  stderr's 8 000), so such a step shows all the model read; a call keeps its file and command, a
  result its line counts. A
  chat keeps its newest 400 messages from something the user said; its title, once given, stays.
  An empty chat is never saved. A chat ID is iCode's own UUID, never a path.
- `AssistantChats` holds the chats read in this process and "Allow All in This Chat"; each change
  is saved under its lock, so two never reach a file in the other order. A killed process loses at
  most the reply in flight. The lifecycle's move and forget hooks carry the chats with a rename and
  remove them with a delete.

### 4.7 Provider settings and keys

`AssistantProviderStore` owns the preferences `icode.assistant`: `providers` (JSON: each one's ID,
kind, name, address and models — an ID, and for a model Koog does not know what the user said of
it), each provider's key (`key.<id>`), the chat's choice (`choice.provider`, `choice.model`, kept
on a listed model: the chosen provider's first, else the first provider's), the permissions
(`ask_before_changes`), effort and mode. Nothing reads round 2's keys (§13 P11).
Keys are stored encrypted (AES/GCM, a Keystore key of its own, alias `icode.assistant.key`, through
`data/io/KeystoreCipher`, shared with `GitCredentialStore`). The snapshot carries only `hasKey`. A
key this device can no longer decrypt reads as missing, and the page asks for it again.
`AssistantProviders` applies the settings' intents: saving (checked by `AssistantRules.providerProblem`
first), removing, choosing, and listing a server's models with a throwaway client.

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

- `AssistantScreen`: the page; its sheets (**Chats**, **Model**, **Attach**) are the page's own,
  since **Attach** edits the composer's draft, and are drawn as sheet routes are —
  `IcodeSheetOverlay` (dimmed, a tap outside or Back closes), the page blurred under it.
- `AssistantPickers`: the Photo Picker, the document picker and the camera (`TakePicture` into
  iCode's cache through the `FileProvider` `${applicationId}.files` (one for every file iCode hands out, [open-with-plan.md](./open-with-plan.md)), a day's photos
  kept), each picked file described by Android; `AssistantThumbnail` decodes an image off the main
  thread with the image viewer's `decodeImage`; `attachmentOpener` says what a tap on a file opens. `AssistantAttachSheet`: the tiles and the workspace's files.
- Settings: `AssistantSettingsScreen` lists the providers; `AssistantProviderScreen` is one
  provider's page (route `AssistantProvider`, the provider in `AssistantState.editing`, as Language
  Services keep theirs); `AssistantModelSheets` holds **Add Models** and a model's options.
- `AssistantChatView`: the chat inside `PinchToScaleText`, every row's text scaled by
  `LocalCodeTextScale`; the branded welcome (`IcodeWatermark`, `IcodeBrandMark`); the step line;
  the approval card. Each chat's list starts at its last row (§18); `JumpToLatestButton` — the
  terminal's ↓, now the design system's — brings it back to the end.
- `AssistantSteps`: a step's row (`AssistantStepRow`) — its state's icon, what it did, and for a
  write or an edit that is done, "+12 −3" in green and red. A tap opens all it answered, a
  command's `$ line` above its output, Copy at its corner (the line and the output); a tap on what
  opened closes it. A long press on a step about a file offers Open in Editor, Show Changes —
  where Source Control lists the file as changed and can show its diff
  (`AssistantRules.gitChangeOf`: not untracked, deleted or conflicted; the unstaged change first) —
  and Copy Path. `Disclosure` is the same row for a compaction's summary.
- `AssistantComposer`, `AssistantSheets`, `AssistantParts` as named.
- Design system, made once for every screen: `IcodeChoiceMenu` (the dropdown's menu, now shared by
  `IcodeDropdown`), `IcodeChoiceChip` (a toolbar choice), `IcodeSheetOverlay` (now
  `NavigationHost`'s too).

## 5. Contract (domain)

- `AssistantProviderKind` — OpenAI, Anthropic, Google, OpenRouter, Ollama, OpenAICompatible.
- `AssistantEffort` (Minimal, Low, Medium, High, Max, On), `AssistantMode` (Agent, Ask),
  `AssistantModel` (id, context window, output limit, efforts, images, documents, known),
  `AssistantModelSpec` (what a provider keeps of one), `AssistantProvider` (id, kind, name,
  address, `hasKey`, models), `AssistantChoice` (provider, model), `AssistantAvailable` (a
  server's models as listed, or why not), `AssistantAttachment` (name, kind — Text, Image,
  Document — text, or the kept file and its type).
- `AssistantSettings` — the providers, the choice, `askBeforeChanges`, effort, mode; the chosen
  provider and model derived.
- `AssistantState` — settings, catalogue, the provider being edited and its server's models, the
  workspace, its sessions, the open session and its messages, the last request's tokens, the turn
  (workspace, name, session, step), the approval, Allow All, the last failure.
- Intents (`AssistantIntent`, lock-free, in order): `OpenAssistant`, `OpenAssistantSession`,
  `DeleteAssistantSession`, `NewAssistantChat`, `SendAssistantMessage(text, attachments)`,
  `StopAssistant`, `AnswerAssistant`, `CompactAssistantChat`, `ChooseAssistantModel`,
  `ChooseAssistantEffort`, `ChooseAssistantMode`, `SetAssistantAskFirst`,
  `RetryAssistantMessage`, `EditAssistantProvider(id)`,
  `SaveAssistantProvider(id, kind, name, baseUrl, key?, models)` and
  `ListAssistantModels(provider, kind, baseUrl, key?)` (each key masked in its `toString`),
  `RemoveAssistantProvider(id)`.
- `AssistantRules` (pure): the page's entries, `closed`, `forModel`, the meter's share and label,
  a chat's title, the effort sent, a model's summary, the provider form's problems, a file's kind
  and what a model cannot read, token counts typed ("200k") and shown.

## 6. Failure handling

| What | What the user reads |
|---|---|
| No provider set | The page's set-up explanation; a message sent anyway: "Set up the assistant first: add a provider and choose a model" |
| 401/403 from the provider | "The provider refused the API key — check it in the provider's settings" |
| 404 when listing models | "api.example.com has no such API — check the address in the provider's settings" |
| 404, unknown model | "The provider has no model “x”" |
| 429 | "The provider is limiting requests — try again shortly" |
| Context too long | "This chat is longer than the model accepts — compact it or start a new chat" |
| No network / DNS / TLS | "Unable to reach api.example.com", and the like |
| Clear text refused (LAN `http://`) | "Android blocks plain http:// to 192.168.1.5 — use https://, or a server on this phone (127.0.0.1)" |
| A file that cannot be attached | "secret.txt is not in this workspace", "x is not a file", "x is not text — only text, images and PDFs can be sent", "x is larger than 10 MB", "x is not a PDF", "x is not an image this phone can read", "A file from the phone can no longer be read — attach it again" — nothing is sent, and nothing is kept |
| A file the model cannot read | "gpt-5 does not read images — choose a model that does" (or PDFs): beside the files, before anything is sent |
| Tool error (missing file, ambiguous edit, unsaved edits, command timeout, no runtime) | To the model as a failed result (it can recover); on the step's row for the user |
| Step limit (40 requests) | "Stopped at the step limit — send a message to continue" |
| A reply with nothing in it | "The model sent an empty reply — send a message to continue" |
| A reply that ended at the output limit (`length`, `max_tokens`, `MAX_TOKENS`; Ollama gives no reason) | Kept, and under it: "The reply was cut short at the model's output limit — ask it to continue", adding "or set a higher Output Limit for x in its provider's settings" for a model Koog does not know |
| A summary that ended at the output limit | "The model's summary was cut short at its output limit, so the chat was not compacted" |
| A reply or a summary stopped by a content filter; a stream that closed early or failed; a timeout; a connection that broke | As [runtime-foundation-plan.md](./runtime-foundation-plan.md) §3.11 says; a stream that broke after it began, by what broke (§3.13) |
| A call whose arguments are not a JSON object (a long call cut at the output limit) | Not a failure of the turn: the call is refused to the model with why, the reply kept ([runtime-foundation-plan.md](./runtime-foundation-plan.md) §3.12) |
| A reply that cannot be read at all | "The provider's reply could not be read — try again" |
| A compaction without a summary | "The model sent an empty summary — try again" |
| Process killed mid-turn | The chat as last saved; open calls closed as stopped |

## 7. Not now (phase 2+, each its own plan)

- **MCP servers** as tool sources: Koog's `agents-mcp` is beta, so it would sit behind an
  `AssistantToolSource` of iCode's own.
- **External agents over ACP** (OpenCode and others) as a second `AssistantEngine`.
- Streaming for Anthropic, Gemini, Ollama and OpenAI's Responses-only models: Koog 1.3.0's
  streams lose what a turn needs there — Anthropic's thinking signature, Gemini's thought
  signature, Ollama's end, a Responses reply cut short
  ([runtime-foundation-plan.md](./runtime-foundation-plan.md) §1.1).
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
   (if Koog fixes its edge order, `KoogTurn.strategy` can become `singleRunStrategy()` again).

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

## 13. Round 3 — providers and models, settings as pages, attachments from the phone (2026-10-01)

The owner, after round 2: "user can add providers but there is no way to add multiple models from
the same provider, so the chat can show the real list"; "reorganize the assistant settings according
to the layout — not everything on one page"; "the attach (+) button should offer, as ChatGPT does,
files, photos and others, from shared storage or from internal"; "fill all such gaps".

### 13.1 Facts it rests on (checked in Koog 1.3.0's sources)

| Fact | Evidence | Consequence |
|---|---|---|
| Every client lists its server's models: `models()` (OpenAI `v1/models`, Anthropic `v1/models`, Google — names as `models/<id>`, OpenRouter `api/v1/models`, an OpenAI-compatible server through OpenAI's client); a known ID comes back as Koog's model | `OpenAILLMClient.kt:634`, `AnthropicLLMClient.kt:794`, `GoogleLLMClient.kt:815`, `OpenRouterLLMClient.kt:220` | A provider's page lists the server's real models, Koog's knowledge where it has it; listing proves the key and address |
| Ollama lists **model cards** (`getModels()`, `/api/tags` + `/api/show`): tools, vision and context length per model | `OllamaClient.kt:430`, `OllamaManagementConverters.kt` | An Ollama's models are offered with what they can do; one that cannot call tools is not offered |
| A user message carries attachments: `MessagePart.Attachment(AttachmentSource.Image / File)` with bytes; every client sends images when the model has `Vision.Image`, and OpenAI, Anthropic and Google send documents (PDF) when it has `Document`; a client refuses one the model cannot read | `Attachment.kt`; `AbstractOpenAILLMClient.kt:357`, `AnthropicLLMClient.kt:550`, `GoogleLLMClient.kt:498`, `OllamaConverters.kt:83` | Photos go as images, PDFs as documents, text files as text (every model reads text); a model that cannot read one is said before anything is sent, and a chat's earlier image reaches such a model as a line of text |
| `nodeLLMRequest(text)` appends a text-only user message; `requestLLM()` sends the prompt as it is (Koog's `reActStrategy` uses a node that does only that) | `AIAgentNodes.kt:96`, `AIAgentStrategies.kt` | The strategy's first node only requests: the message, with its attachments, is already the prompt's last |
| The Photo Picker, the document picker and the camera are Activity results (`PickMultipleVisualMedia`, `OpenMultipleDocuments`, `TakePicture`) in `activity-compose` 1.12.4; none needs a permission; the camera needs a `FileProvider` URI (`androidx.core`) | the app's dependencies; `SystemPrompts.kt` hosts launchers already | No new permission; one `FileProvider` for the camera's photo |

### 13.2 Every gap, its decision

| # | Gap | Decision |
|---|---|---|
| P1 | One provider at a time | **Providers**: any number, each with its kind, a name (its label by default, "DeepSeek" for a compatible server), its address where asked, its own key |
| P2 | No way to choose a provider's models; the chat listed Koog's whole catalogue | Each provider has **its models**, in the user's order: added from the server's real list, Koog's catalogue, or a typed ID. The chat's model sheet lists exactly these, by provider |
| P3 | Everything on one settings page | Category → list → detail (layout §3.3, §19): **Assistant** lists the providers and adds one; a **provider's page** holds its identity, key, models and removal; a model Koog does not know has its **options** (context window, output limit, reads images, reasons) |
| P4 | A typed model could not reason or read images | Its options say so: efforts and images follow |
| P5 | Nothing checked a key until a message | Listing a provider's models checks its address and key, and says why it failed |
| P6 | **+** attached workspace files only | **Attach**: Camera, Photos (the system Photo Picker), Files (the system document picker), then the workspace's files, searchable — up to five in all |
| P7 | Only text could be sent | Images (to a model that reads them: downscaled to 1 568 px, JPEG, EXIF orientation applied), PDFs (to one that reads documents, up to 10 MB), text files from anywhere (64 000 characters); anything else is refused by name. An image or PDF is kept in its chat's folder, never inside the chat's file |
| P8 | A model that cannot read an attachment failed at the provider | The composer says so beside the files and Send waits; a chat's earlier images reach such a model as a line of text |
| P9 | A failed message could only be retyped | **Try Again** on a failure before any reply: the message is sent again, its files as they were |
| P10 | Replies could only be selected | **Copy** under the answer each turn ends on |
| P11 | Round 2's single-provider settings | Became the first provider, until the owner's rule of 2026-10-01 — no compatibility before the first release — removed the migration, and with it round 1's chat file and round 2's attachments |
| P12 | The context meter showed only once a request had been made (owner: "show the current context size below the input box, bottom right, in real time") | Always shown under the composer, at the right: "0 / 200k" before the first request, then the last request's tokens after every step; "12k tokens" where the window is unknown |
| P13 | A message needed words | A photo or a file alone is a message; its chat is named after it |
| P14 | Back on a provider's page lost what was typed | Back with changes asks: **Discard**, or stay |
| P15 | Picks past five were dropped without a word; a phone without a camera offered one | Said ("2 not attached"); the Camera tile shows only where there is a camera |

### 13.3 Cycles

| # | Step | Verified by |
|---|---|---|
| C1 | Contract and provider store: providers, models (specs), the choice, migration | Type-check; store and rules tests |
| C2 | Engine: a model from its spec, a provider's model list, `TURN` requesting the prompt as built, attachments as Koog parts, what a model cannot read | Engine and model tests against Koog |
| C3 | Attachments: sources (workspace, `content://`), kinds, images prepared, kept per chat; Try Again | Store and coordinator tests |
| C4 | Coordinator: provider intents, model listing, the choice kept valid | Coordinator tests |
| C5 | Screens: Assistant settings as pages, the model sheet by provider, Attach with the pickers and the camera, thumbnails, warnings, Copy, Try Again | Compose type-check; UI guard; RouteTest |
| C6 | This plan as built; PRD, user stories; guards; commit | Review |

### 13.4 Reasoning on the page (2026-10-01)

The owner: "we can't see the real reasoning on our UI". iCode dropped every reply's reasoning.
Koog had handed it over (`MessagePart.Reasoning`), but iCode kept only text and calls.

- **Kept and shown.** A reply keeps its reasoning (`AssistantMessage.Reply.reasoning`, at most
  32 000 characters, its start and end), saved with the chat. The page shows it above the reply's
  answer and steps as one quiet line, "Reasoning · 1.2k words", that opens on the thinking in
  muted text beside a rule.
- **Never sent back.** Later turns send the answer and the calls alone.
- **Where it comes from.** It is what the provider sends as reasoning:
  - Anthropic's thinking, with a reasoning level chosen;
  - Gemini's thoughts, now asked for (`includeThoughts`) whenever a level is chosen;
  - Ollama's thinking, with reasoning on;
  - an OpenAI-compatible server's `reasoning_content` (DeepSeek, vLLM and the like);
  - a leading `<think>…</think>` that some models write into their text instead, taken out of
    the answer — an unclosed one, as in a reply cut short, is all reasoning.

  Encrypted reasoning has no text and is not shown.
- **OpenRouter's, since round 4.** OpenRouter sends reasoning in a field named `reasoning`, which
  Koog 1.3.0 reads only while streaming (`OpenRouterStreamDelta`): OpenRouter's replies now stream,
  and the reasoning and the answer appear as they are written
  ([runtime-foundation-plan.md](./runtime-foundation-plan.md) D8). An OpenAI-compatible provider
  at OpenRouter's address is pointed to the OpenRouter type. OpenAI's own Chat Completions sends
  no reasoning text at all.
- **The output limit's name.** Koog's OpenAI client sends a user's Output Limit as
  `max_completion_tokens`; since round 4 a compatible server is sent `max_tokens` too (D9).

### 13.5 Record (2026-10-01)

- Built as decided. The assistant's eight test classes pass, 93 tests — among them the provider
  store (five), the attachments (five), the coordinator's providers, phone photo, photo alone and
  Try Again, the meter's label and a model's summary; of the 591 `data/` and `domain/` tests, only
  `TreeSizeTest`'s unreadable-directory case fails, as before, because the container runs as root.
- The screens type-check against Compose's desktop jars with the Activity-result, `FileProvider`
  and `Bitmap` stand-ins; RouteTest and SettingsCatalogTest pass; the UI and backend guards pass;
  every class, backend and UI, is checked against Android 11's API (minSdk 30) — none reaches past
  it (`InputStream.readNBytes`, Android 13's, was replaced).
- Not seen on a device: Google's Maven is closed to this environment, so no APK is built and no
  screenshot is taken. The device pass of §9 covers the pickers, the camera, thumbnails and each
  provider's model list.

## 14. Found on the phone (2026-10-02)

- **Add Models on an OpenAI-compatible server failed** with "The assistant stopped: Expected
  response body of the type '…OpenAIModelsResponse' but was '…SourceByteReadChannel'" (the owner's
  provider at `https://inference.dahl.global/v1`). Measured: `GET …/v1/models` answers the list as
  JSON; `GET …/v1/v1/models` answers the website's page, 200, `text/html`. iCode had put only the
  chat path under the address given; Koog's other paths kept their defaults, each starting with
  `v1/`, so the list was asked at `…/v1/v1/models`, and Ktor cannot read a page as the list.
  - **Every path is under the address given** (`KoogClients.CompatibleClient`: chat, responses,
    embeddings, moderations, models).
  - **Its models are read by their IDs alone** (`{"data": [{"id": …}]}`): Koog's own reading
    requires `object`, `created` and `owned_by` of each, which a server that is not OpenAI need not
    send — one missing would have failed the whole list. A model of OpenAI's that Koog knows still
    comes with what it can do, as before.
  - **An answer that is not the API's is said so**: "<host> did not answer as this API does — check
    the server address in the provider's settings" — for a page or another service's JSON on
    listing, for Ktor's own refusals to read one, and for a stream refused with a success status
    (which had read "The provider answered 200").
  - **A failed listing no longer says "The assistant stopped"** — no turn was running: "Unable to
    list the models: …".
  - A server's models not known to iCode are listed by name whatever their case
    (`MiniMaxAI/…` no longer before `deepseek-ai/…`).
  - `KoogServerTest`, 6 tests: the real stack (Koog's client on Ktor and OkHttp) against a server
    on the machine that answers as the owner's does — the path asked, a list of IDs only, a web
    page, other JSON, a refused key, an address ending in a slash.
  - With §3.12's fix of a call cut inside its arguments (`1ef68f6`): the compatible server's client
    is `ReadingOpenAIClient`'s, so it gets both — its paths and listing here, its whole replies'
    arguments made readable there. A reply the API's reader cannot read at all stays "could not be
    read"; only Ktor's refusals of a page or another shape say to check the address.
  - The tests' local server is one, on plain sockets (`LocalHttpServer`): `KoogClientsTest` used the
    JDK's `com.sun.net.httpserver`, which Android's unit tests do not have, so under Gradle it did
    not compile and no unit test ran.

## 15. The approval, redrawn (owner, 2026-10-02)

The owner, on the phone: the approval card was too big, not aligned, its box and colour heavy —
"collapsed by default, clean, aligned to the input box, no background". `AssistantApprovalCard`
(its own file now) is a box as the composer's — its surface, outline and corner, its text where
the composer's is — above it, at its edges, one gap between them (owner: no transparent design): an amber icon and
"Run this command?" or "Make this change?", a chevron, the command's — or the file's — first line,
and Deny, Allow All and Allow as compact buttons at the end (`PrimaryButton`/`SecondaryButton`
`compact`: a bar choice's height, Material keeping the touch target whole). Each new question
starts closed; a tap on it opens all it will do, scrolling past a few lines
(`IcodeSizes.ApprovalDetail`), with what Allow All means.

## 16. Found on the phone (2026-10-02, the owner)

- **The workspace under the composer opens its files.** "Tests" (its folder icon and name) is a
  tap away from the open workspace's tree — the Explorer, the workspace the assistant works in — a
  step forward: Back is the chat again. (A first version made a step to the screen just below a step
  back to it; from a chat the Explorer had opened, Back then went past the chat to the home screen.
  The owner found it on the phone; navigation is again as it was.)
- **A Markdown table follows a pinch.** Text, headings and lists read their size as they are drawn;
  Markwon's `TableRowSpan` (4.6.2) lays its cells out once, again only when the width it is drawn at
  changes — so a pinch, which changes the size and not the width, left tables as they were, on the
  assistant's page and in the editor's preview alike. `ScalableMarkdownTextView.show` sets the size,
  and where the Markdown holds a table and the size changed, renders it anew: new rows, laid out at
  the new size. One method for both renderers. (Markwon has since gone: in the renderer of
  [markdown-rendering-plan.md](./markdown-rendering-plan.md), a table's cells take their size when they are drawn.)

## 17. Replies as they are written, and the chat's steps (owner, 2026-10-08)

The owner, with four OpenAI-compatible providers: no reply appeared as it was written; an opened
command's output was cut; and asked for a Copy, each write's real line counts, a tap on the open
output to close it, and a long press on a file to open it or see its changes. Decided and built in
[runtime-foundation-plan.md](./runtime-foundation-plan.md) §7: OpenAI's Chat Completions and
compatible servers stream (D8); the step row of §4.11; the line counts and the calls' files and
commands of §4.3; the chat file's bound of §4.6.

## 18. The chat opens at its end (owner, 2026-10-08)

The owner, on the phone: opening a chat was not smooth — the chat seemed to hang while it scrolled;
wanted: an arrow to the bottom, as the terminal has. Found: the list started at its first row and
then glided to the last (`animateScrollToItem`), laying out and drawing every reply on the way — a
Markdown view each — and it glided to a long last row's start, not its end. It also pulled the
user down at every step of a turn, even scrolled up to read.

- **Laid out at the end:** each chat's list starts at its last row (`LazyListState(last)`, one per
  workspace and chat), then jumps to that row's end, with no glide; nothing between is drawn.
- **Followed only at the end:** a step, a reply or the reply being written moves the list only while
  it is at its end. Scrolled up, it stays; a message the user sends is shown wherever they were, and
  what answers it followed. At rest at the end, whatever brought it there, it follows again.
- **↓** (`JumpToLatestButton`, fading in and out; the terminal's, moved into the design system and
  used by both) shows while the user is away from the end: a glide when the last row is in view, a
  jump from further up — where a glide would draw everything between.
