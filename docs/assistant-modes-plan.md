# Assistant modes — Code, Plan, Ask, Orchestrator

**Status:** Plan only. No code changed.

## 1. What we have now (two modes)

| Mode | Tools | System prompt | Welcome text | Composer placeholder |
|------|-------|---------------|-------------|---------------------|
| `Agent` | All 12 (Read + Change + Command) | "Look before you change… change with edit_file/write_file… run tests…" | "Build with the assistant" | "Describe what to build" |
| `Ask` | 5 Read-only (list_files, read_file, search_text, git_status/git_diff, process_status) | "You can list, search and read… not change it or run commands" | "Ask about {workspace}" | "Ask about {workspace}" |

The mode is:
- Defined in `domain/AssistantContract.kt` (enum)
- Persisted in `AssistantProviderStore` (SharedPreferences, key `mode`)
- Used in `AssistantTurns.talk()` to **filter tools** (line 169)
- Used in `AssistantInstructions.of()` to **change the system prompt**
- Used in `AssistantChatView` and `AssistantComposer` for **welcome text + placeholder**
- Changed via `ICodeIntent.ChooseAssistantMode` → coordinator → `providers.chooseMode()`

## 2. Research — what the best coding agents do

System prompts from **RooCode, Cline, OpenCode / Kilo Code** and **Continue** were studied. Key patterns extracted:

### 2.1 RooCode (most mature mode system)

| Mode | Role definition | Tool groups | Custom instructions |
|------|----------------|-------------|-------------------|
| **Code** | "highly skilled software engineer with extensive knowledge in many programming languages, frameworks, design patterns, and best practices." | `read, edit, browser, command, mcp` — full access | — (generic) |
| **Architect** | "experienced technical leader who is inquisitive and an excellent planner. Your goal is to gather information and get context to create a detailed plan." | `read, edit(.md only), browser, mcp` — reads everything, writes only plans | Gather info, ask questions, create todo list, present plan, use `switch_mode` to delegate to Code |
| **Ask** | "knowledgeable technical assistant focused on answering questions and providing information." | `read, browser, mcp` — no edits, no commands | Analyze code, explain concepts, never implement unless asked |
| **Debug** | "expert software debugger specializing in systematic problem diagnosis and resolution." | `read, edit, browser, command, mcp` — full access | Reflect on 5-7 possible sources, distill to 1-2, add logs, confirm before fixing |
| **Orchestrator** | "strategic workflow orchestrator who coordinates complex tasks by delegating to appropriate specialized modes." | `[]` — no tools of its own | Break down complex tasks, delegate via `new_task`, track progress, synthesize results |

### 2.2 Cline (Plan / Act)

- **Plan mode:** Only Read tools + `plan_mode_respond` tool. No file writes, no commands. Goal: gather info, ask clarifying questions, architect a detailed plan, present it, then ask user to switch to Act mode.
- **Act mode:** All tools + `attempt_completion`. Execute the plan.
- User toggles between them manually.

### 2.3 OpenCode / Kilo Code (single mode, very polished)

Key patterns in their system prompt that make it effective:

| Pattern | What it does |
|---------|-------------|
| **Professional objectivity** | "Prioritize technical accuracy and truthfulness over validating the user's beliefs. Disagree when necessary." |
| **Tone and style** | "Only use emojis if asked. Output short and concise. Use GitHub-flavored markdown." |
| **Task management** | "Use TodoWrite very frequently to plan and track tasks. Mark todos as completed as soon as done." |
| **Tool policy** | "Parallel calls for independent operations. Sequential for dependent ones. Never use placeholders or guess missing parameters." |
| **Doing tasks** | "First analyze file structure, then think about which tool is most relevant, then check if all required parameters are present or can be inferred. If missing, ask the user." |

### 2.4 What we can adopt for iCode's system prompts

1. **Role definition per mode** (from RooCode) — each mode gets a clear identity
2. **Professional objectivity** (from OpenCode/Kilo) — tell the model to prioritize truth, disagree when needed
3. **Tool use discipline** (from OpenCode/Kilo) — parallel where independent, sequential where dependent
4. **Plan-first architecture** (from RooCode's Architect + Cline's Plan mode) — Plan mode reads and plans but never executes
5. **Orchestrator delegating to modes** (from RooCode) — not applicable to us since all modes run in the same engine, but the structured approach to multi-step work is valuable
6. **Step-by-step verification** (from RooCode's Debug) — Orchestrator verifies after each step

## 3. The four target modes

| Mode | Tools | What the model does | Asks before? |
|------|-------|---------------------|-------------|
| **Code** | All 12 | Reads, changes files, runs commands — the full agent | Yes (configurable) |
| **Plan** | 5 Read-only (like Ask) | Reads everything, analyzes the codebase, produces plans; **never changes anything, never runs commands**. Suggests changes as code blocks for the user. | N/A (no changes to approve) |
| **Ask** | 5 Read-only | Reads and answers questions about the workspace | N/A |
| **Orchestrator** | All 12 | Breaks work into steps, plans first, then executes methodically, reviews its own output, runs checks. **Acts as a lead developer coordinating its own tasks.** | Yes (configurable) |

## 3. Exact changes, layer by layer

### 3.1 Domain — `AssistantContract.kt` (enum)

```kotlin
enum class AssistantMode(val label: String, val description: String) {
    Code("Code", "Reads, changes files and runs commands"),
    Plan("Plan", "Reads and analyzes; plans changes without making them"),
    Ask("Ask", "Reads and answers; changes nothing"),
    Orchestrator("Orchestrator", "Plans, implements and verifies step by step"),
}
```

- Remove `Agent`
- Add `Code`, `Plan`, `Orchestrator`
- `Code` has the same semantics as the old `Agent`
- `Plan` is read-only like `Ask`, but with a different system prompt
- `Orchestrator` has all tools like `Code`, but with a different system prompt

**Migration:** stored `mode = "Agent"` → read as `Code` (backward-compatible fallback).

### 3.2 Storage — `AssistantProviderStore.kt`

- `KEY_MODE` stays the same
- Default changes from `AssistantMode.Agent` to `AssistantMode.Code`
- Fallback: `AssistantMode.entries.firstOrNull { it.name == name } ?: AssistantMode.Code`
- The stored string `"Agent"` is not a valid name in the new enum → falls through to `Code`

### 3.3 Instructions — `AssistantInstructions.kt`

The `of()` function gains four branches, inspired by the best patterns from RooCode, Cline, OpenCode/Kilo Code and Continue. Each mode has:

- **Role definition** — who the model is in this mode
- **How it works** — what it can/cannot do
- **Professional objectivity** — truth over politeness
- **Tool discipline** — read first, use parallel calls where independent
- **Output format** — Markdown, concise, phone-friendly

**Common prefix** (before the mode-specific section):

```kotlin
appendLine("You are iCode's built-in assistant on an Android phone.")
appendLine()
appendLine("## Your workspace")
appendLine(
    "You work in the workspace \"${workspace.name}\". Its files are at ${workspace.guestPath} in an Ubuntu " +
    "Linux runtime on the user's phone (ARM64, under PRoot: uid 0 without root's powers, no systemd; " +
    "\`apt\` installs packages). File tool paths are relative to the workspace folder.",
)
appendLine()
appendLine("## Style")
appendLine("- Be concise. The user reads you on a phone. Use Markdown, name files by their paths.")
appendLine("- Prioritize technical accuracy over validating the user's beliefs. Disagree when necessary.")
appendLine("- Only use emoji if the user does first.")
appendLine("- Never say you ran or checked something you did not.")
```

**Mode-specific section:**

```kotlin
when (mode) {
    AssistantMode.Code -> {
        appendLine("## Your role")
        appendLine("You are a skilled software engineer. Your job is to write, modify and refactor code.")
        appendLine()
        appendLine("## How you work")
        appendLine("- First explore: list, search and read what a change touches before making it.")
        appendLine("- Change existing files with edit_file, write new ones with write_file.")
        appendLine("- After a change, verify: run tests, a build or a linter when the project has them.")
        appendLine("- For long-running processes (servers, watchers), use start_process. Check with process_status. Stop when done.")
        appendLine("- When you need more information, ask the user rather than guessing.")
    }
    AssistantMode.Plan -> {
        appendLine("## Your role")
        appendLine("You are an experienced technical leader and planner. Your job is to understand, analyze and design — never to implement.")
        appendLine()
        appendLine("## How you work")
        appendLine("- Explore thoroughly: list directories, read key files, search for patterns across the codebase.")
        appendLine("- Analyze architecture, identify issues, evaluate different approaches.")
        appendLine("- Produce a clear plan with concrete steps. Show suggested code as diffs or code blocks.")
        appendLine("- You CANNOT change files or run commands. Only read and propose.")
        appendLine("- Ask clarifying questions when the request is ambiguous.")
    }
    AssistantMode.Ask -> {
        appendLine("## Your role")
        appendLine("You are a knowledgeable technical assistant focused on answering questions about the codebase.")
        appendLine()
        appendLine("## How you work")
        appendLine("- Read files, search and explore the workspace to answer the user's questions.")
        appendLine("- Explain concepts, analyze existing code, point to relevant files and patterns.")
        appendLine("- Show suggested changes as diffs or code blocks — the user will make them.")
        appendLine("- You CANNOT change files or run commands. Only read and answer.")
    }
    AssistantMode.Orchestrator -> {
        appendLine("## Your role")
        appendLine("You are a strategic technical lead who plans, implements and verifies complex work step by step.")
        appendLine()
        appendLine("## How you work")
        appendLine("- Start by understanding the full scope: read, search and explore to plan the work.")
        appendLine("- Break the work into clear, independent steps. List them using todo tracking.")
        appendLine("- Execute each step: change files with edit_file or write_file. Run the project's checks after each change.")
        appendLine("- Verify: after every change, run tests, a build or a linter. Fix what fails before moving on.")
        appendLine("- For servers or watchers, use start_process. Check with process_status. Stop when done.")
        appendLine("- Report what you did, what each step produced, and what remains open.")
    }
}
```

**Common suffix** (appended after the mode section):

```kotlin
appendLine()
appendLine("## Tool use")
appendLine("- Use multiple independent tools in parallel (e.g. read several files at once).")
appendLine("- When one result is needed for the next, use tools sequentially.")
appendLine("- Never guess parameters or use placeholders. If information is missing, ask the user.")
```

Then `projectNotes()` and `summary` as before.

### 3.4 Tool filtering — `AssistantTurns.kt`

The filter at line 169 changes from:

```kotlin
val offered = tools(setup.place, session).filter { 
    setup.settings.mode == AssistantMode.Agent || it.kind == AssistantToolKind.Read 
}
```

To:

```kotlin
val offered = tools(setup.place, session).filter { 
    setup.settings.mode == AssistantMode.Code || 
    setup.settings.mode == AssistantMode.Orchestrator || 
    it.kind == AssistantToolKind.Read 
}
```

Plan and Ask get only Read tools. Code and Orchestrator get all tools.

### 3.5 UI — `AssistantComposer.kt`

**Placeholder text** (line 95) changes from:
```kotlin
if (settings.mode == AssistantMode.Ask) "Ask about ..." else "Describe what to build"
```
To:
```kotlin
when (settings.mode) {
    AssistantMode.Ask -> "Ask about ${AssistantRules.workspaceName(...)}"
    AssistantMode.Plan -> "Plan what to build"
    AssistantMode.Code -> "Describe what to build"
    AssistantMode.Orchestrator -> "What would you like to build?"
}
```

**The mode selector chip** (line 114) stays generic — it already uses `AssistantMode.entries`, `it.label` and `it.description`, so it picks up new values automatically.

### 3.6 UI — `AssistantChatView.kt`

**Welcome screen** (lines 203–217) changes behaviour:

```kotlin
val asking = state.settings.mode == AssistantMode.Ask
val planning = state.settings.mode == AssistantMode.Plan
val orchestrating = state.settings.mode == AssistantMode.Orchestrator

title = when {
    asking -> "Ask about $workspace"
    planning -> "Plan for $workspace"
    orchestrating -> "Build with orchestration"
    else -> "Build with the assistant"
}

description = when {
    asking -> "It reads this workspace to answer, and changes nothing."
    planning -> "It reads everything to understand and plan. It never changes files or runs commands."
    orchestrating -> "It plans, implements and verifies step by step, reporting what it did."
    else -> "It reads and changes $workspace's files and runs commands in its terminal${if (state.settings.askBeforeChanges) ", asking first" else ""}."
}
```

**Suggestions** — each mode gets its own set:

```kotlin
private val CODE_SUGGESTIONS = listOf(
    "Explain how this project is organized",
    "Review my uncommitted changes",
    "Add a README for this project",
)

private val PLAN_SUGGESTIONS = listOf(
    "Analyze the architecture and suggest improvements",
    "Plan how to add authentication",
    "What would it take to add dark mode?",
)

private val ASK_SUGGESTIONS = listOf(
    "Explain how this project is organized",
    "Where does the app start?",
    "What do my uncommitted changes do?",
)

private val ORCHESTRATOR_SUGGESTIONS = listOf(
    "Add a new settings screen with search",
    "Refactor the gateway into smaller coordinators",
    "Set up CI with GitHub Actions",
)
```

### 3.7 Tests

| File | What changes |
|------|-------------|
| `AssistantCoordinatorTest.kt` | Change `AssistantMode.Agent` → `AssistantMode.Code` references. Add test for Plan mode → only Read tools reachable. Add test for Orchestrator mode → all tools reachable. |
| `AssistantRulesTest.kt` | No change unless mode-specific rules are added. |
| `AssistantProviderStoreTest.kt` | Test migration of old `"Agent"` string → `Code`. |

## 4. Migration

The only migration concern: the old `"Agent"` value in SharedPreferences.

```kotlin
// In AssistantProviderStore.settings():
mode = preferences.getString(KEY_MODE, null)?.let { name ->
    // "Agent" was renamed to "Code"
    if (name == "Agent") AssistantMode.Code
    else AssistantMode.entries.firstOrNull { it.name == name }
} ?: AssistantMode.Code
```

This is a one-line change in `AssistantProviderStore.kt` line 93.

## 5. File change summary

| # | File | Change |
|---|------|--------|
| 1 | `domain/AssistantContract.kt` | Replace `Agent` enum entry with `Code`, add `Plan` and `Orchestrator` |
| 2 | `data/assistant/AssistantProviderStore.kt` | Add `name == "Agent"` → `Code` migration; change default to `Code` |
| 3 | `data/assistant/AssistantInstructions.kt` | Add branches for `Code`, `Plan`, `Orchestrator` in `of()` |
| 4 | `data/assistant/AssistantTurns.kt` | Update tool filter to include `Code` and `Orchestrator` |
| 5 | `ui/screens/tools/AssistantComposer.kt` | Add `when` for placeholder text per mode |
| 6 | `ui/screens/tools/AssistantChatView.kt` | Add `when` for welcome title, description, suggestions per mode |
| 7 | Tests | Update references and add Plan/Orchestrator coverage |

## 6. What each mode ultimately provides to the model

| Mode | Read tools | Change tools | Command tools | System prompt focus |
|------|-----------|-------------|--------------|-------------------|
| **Code** | ✅ | ✅ | ✅ | Direct implementation, test after change |
| **Plan** | ✅ | ❌ | ❌ | Analyze, research, propose — never execute |
| **Ask** | ✅ | ❌ | ❌ | Answer questions from what is read |
| **Orchestrator** | ✅ | ✅ | ✅ | Plan → implement → verify, step by step |

## 7. Open decisions for the owner

1. **Mode order in the selector chip.** Proposed: Code, Plan, Ask, Orchestrator (most-used first).
2. **Orchestrator step limit.** Same 40-step max as Code, or a higher limit? Proposed: same.
3. **Plan mode approvals.** Since Plan never changes anything, the "Ask before changes" toggle is irrelevant in Plan mode. Hide it? Proposed: keep it shown but disabled with a tooltip "Plan mode does not change files."
4. **Whether to keep "Agent" as an alias** for migration. Proposed: one-line migration only, no alias.