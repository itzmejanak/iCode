# Assistant permissions — three modes, and the workspace as the boundary

Status: **built** (2026-10-24), as the owner confirmed it (§7). Where the build differs from the plan, §8 says so.

## 1. The owner's intention

On the phone (2026-10-24), a chat in **Math** listed `/workspace`, `/workspace/Math` and
`/workspace/portofolio` without asking. The owner asked:

> "make archecture better of ai assistant to promot permission to user if he want to navigate before
> the workspace any (CRUD) just llike opencode behavior"

Then, on the permission choices:

> "we need to introduce and rename if needed the current permission type … and need to add one like
> Auto approve which will be the really all all and we need to plan for all type boundary … rename
> allow all as something which will for just for workspace … best for 3 type … properly aligned and
> Best with other harnesses"

**What this means, read as a design:**
- A chat works *in* its workspace. Going beyond it is the user's call: any read, list, search,
  write, edit or command that reaches outside asks first.
- There are three permission modes, from asking about everything to asking about nothing.
  - Today's "Allow all changes" becomes the middle mode: everything runs inside the workspace, and
    outside still asks.
  - A new third mode is the real "allow everything".
- Every mode's boundary is written down (§4.2), and lines up with how other assistants draw it (§2).

This does not undo the runtime contract ([runtime-contract-plan.md](./runtime-contract-plan.md) §4.3).
The assistant still reaches the whole runtime, and nothing becomes impossible. What changes is
that reaching outside is now one more question the user answers, like the approvals already there.

## 2. What other assistants do (read from their sources and docs, 2026-10-24)

| | Asks for nothing inside | Asks before | Never asks | "Always" covers |
|---|---|---|---|---|
| **opencode** ([permissions doc](https://github.com/sst/opencode/blob/dev/packages/web/src/content/docs/permissions.mdx); `packages/core/src/file-access.ts`) | Every tool, by default | `external_directory`, default `ask`: any tool that takes a path outside the project, read included (`read`, `edit`, `glob`, `grep`, the shell's `cd` targets and path arguments). It is asked *before* the tool's own approval | `--auto`: approves every `ask` (explicit `deny` stays) | The path's folder becomes `<folder>/*`. "Always" saves `<the folder's project root, or the folder>/*`, for the session |
| **Claude Code** ([permissions doc](https://code.claude.com/docs/en/permissions)) | Reads in the working directories | Edits, most commands, reads outside the working directories | `bypassPermissions` | Edits: until the session ends. `cd` outside gets its own read rule. `/add-dir` adds a working directory |
| **Codex** (`codex-rs/utils/approval-presets/src/lib.rs`) | **Read Only**: reads the workspace; asks to edit. **Default** ("auto"): "read and edit files in the current workspace, and run commands. Approval is required to … edit other files" | Per preset | **Full Access**: "edit files outside this workspace … without asking" | Its sandbox enforces the workspace |

All three draw the same line: **the workspace is the boundary; inside runs as the mode allows,
outside asks; one mode asks for nothing.** Codex's three presets are the closest match to the
owner's three modes.

**One difference iCode cannot remove.** Codex makes its line hard with an operating-system sandbox
(Landlock, seccomp). Under PRoot on Android there is none, so a command's reach can only be read
from what the command says (§4.5), as opencode and Claude Code read it.

## 3. What iCode does today

- **Two modes, one setting:**
  - `AssistantSettings.askBeforeChanges` (`AssistantContract.kt:102`), chosen in the composer's
    Permissions chip, either "Ask before changes" or "Allow all changes" (`AssistantComposer.kt:176-181`, `:289-290`).
  - It is set through `ICodeIntent.SetAssistantAskFirst` and is the same for every chat.
- **What asks:** a call asks when its tool's kind is Change or Command, the setting asks, and this
  chat has not answered "Allow All" (`AssistantTurns.kt:244-275`). Reads never ask, wherever they reach.
- **The card's answers:** Deny, Allow All, Allow (`AssistantAnswer`, `AssistantContract.kt:207`;
  `AssistantApprovalCard.kt:96-98`).
  - "Allow All" is per chat, kept in memory until iCode closes (`AssistantChats.allowingAll`, `:95-99`).
- **Ask and Plan modes** are offered the reading tools only (`AssistantTurns.kt:170`).
- **What a tool reaches:** since RC4, any path in the runtime (`AssistantWorkspace.resolve`).

## 4. The design

### 4.1 Three permission modes

| Mode (proposed name) | Like | Runs without asking | Asks |
|---|---|---|---|
| **Ask first** (was "Ask before changes") | Codex Read Only, Claude Code's default | Reads inside the workspace | Every change and command; anything outside |
| **Auto in workspace** (was "Allow all changes") | Codex Default, Claude Code accept-edits | Reads, changes and commands inside the workspace | Anything outside |
| **Full access** (new) | Codex Full Access, Claude Code bypass, opencode `--auto` | Everything, anywhere | Nothing |

- **Where it is chosen:** in the composer's Permissions chip, as today. The choice is iCode's
  setting and every chat starts in it.
- **The default:** Ask first, as today.
- **Full access** says what it means in its chip line: "Changes, commands and anything outside
  the workspace run without asking".
- **Agent modes:** Ask and Plan still get only the reading tools. Their reads outside the
  workspace ask like any other, except in Full access.

### 4.2 The boundary, action by action

"Inside" means the chat's workspace: `/workspace/<name>` and everything under it, with links
followed as the runtime follows them (`GuestPaths`).
- **Outside:** everything else. That includes `/workspace` itself, the other workspaces, `/root`,
  `/etc` and `/tmp`.
- **Followed links count:** a link inside the workspace that names `/etc/hosts` reaches outside.
  A link elsewhere that names a file in the workspace is inside.

| Action | Tools | Ask first | Auto in workspace | Full access |
|---|---|---|---|---|
| Read, list, search, Git view inside | `read_file`, `list_files`, `search_text`, `git_status`, `git_diff` | runs | runs | runs |
| The same, outside | the same tools | **asks** (outside) | **asks** (outside) | runs |
| Change inside | `write_file`, `edit_file` | **asks** (change) | runs | runs |
| Change outside | the same tools | **asks**, once (outside + change) | **asks** (outside) | runs |
| Command that shows nothing outside (§4.5) | `run_command`, `start_process` | **asks** (command) | runs | runs |
| Command that starts outside or names an outside path | the same tools | **asks**, once (outside + command) | **asks** (outside) | runs |
| This workspace's process | `process_status`, `send_process_input`, `stop_process` | status runs; input and stop **ask** (command) | runs | runs |
| Another workspace's process | the same tools, with its id | **asks** (outside) | **asks** (outside) | runs |
| Every process, listed | `process_status` without an id | runs (a list, as `ps` gives) | runs | runs |
| Run tab and Build & Run (this workspace's only) | `run_android_app`, `save_run_command`, `delete_run_command`; `list_run_commands` | **asks** (change or command); the list runs | runs | runs |
| The chat's own terminal tab ([agent-control-plan.md](./agent-control-plan.md) §4) | `run_command`, `send_terminal_input`; `list_terminals`, `read_terminal` | **asks** (command); reads run | runs | runs |
| A terminal tab of the user's | `run_command`, `read_terminal`, `send_terminal_input`, naming it | **asks** (the tab, a place); Allow in it answers the chat's later calls there | **asks** (the tab) | runs |
| The MCP servers ([agent-control-plan.md](./agent-control-plan.md) §5) | `save_mcp_server`, `remove_mcp_server`; `list_mcp_servers` | **asks**, every time, no Allow in; the list runs | **asks**, every time | **asks**, every time |
| Attachments | the user's own pick | never asks | never asks | never asks |

**One card per call, never two.** An outside change shows one card, and Allow answers both
questions, like opencode's external-then-edit.

### 4.3 The card

The card's three answers keep their places, with words that say what each covers:

| The call | Deny | Middle | Allow |
|---|---|---|---|
| A change or a command inside (Ask first) | Deny | **Allow in Math** (this chat runs as Auto in workspace) | Allow |
| Anything outside | Deny | **Allow `<root>` in this chat** (§4.4) | Allow once |

- An outside card names the paths, as written and as they resolve ("`../portofolio` → `/workspace/portofolio`"), and the root its middle answer would open.
- **Deny:** the tool answers the model "The user did not allow reaching /workspace/portofolio", or
  "… this change" or "… this command" as today. The turn goes on, so the model can ask or try another way.
- **Nothing on a card raises a chat to Full access.** Only the chip does.
- **What it will do, a tap away.** The card shows the command's or the change's first line. A tap on
  its question opens its step's page in the chat's sheet: all it will do, the paths it reaches, what
  the middle answer opens, and the same three answers
  ([assistant-sheets-plan.md](./assistant-sheets-plan.md) §2.3).

### 4.4 "Allow in this chat": what it opens, and for how long

- **What it opens: the path's project**, as opencode's "always" saves it.
  - For a path in another workspace, that workspace, e.g. `/workspace/portofolio`.
  - Otherwise, the Git repository holding the path, its top found by looking for `.git` upward,
    e.g. `/root/projects/api`.
  - Otherwise, the folder itself (a file's folder), e.g. `/etc`.
  - `/` is never opened whole: a path directly under it opens only its own folder.
- **How long: until iCode closes,** like today's "Allow All" and Claude Code's "this session".
  - It is kept per chat, in memory, beside the chat's other answers.
  - Nothing is saved, so permissions never pile up unseen.
  - Deleting a chat forgets its answers, and renaming a workspace carries them (`AssistantChats.moved`).

### 4.5 Commands: what a command shows

A command reaches outside when either holds:
- It **starts** outside: the chat's directory from its last command is outside (RC4's A5 lets it be anywhere).
- Its **text names** an outside path. A word in it is taken as a path when it starts with `/`,
  `~`, `./` or `../`, or is `.` or `..`.
  - Each such word resolves from the directory the command starts in, through `GuestPaths`.
  - `cd /root && ls`, `cat ../portofolio/package.json` and `> /tmp/out` are all seen.
  - The words are split as the shell would: quotes, `\` escapes, and `;`, `&&`, `|`, `>`.

What it **cannot** see, said plainly in the plan and in the README:
- a path in a variable (`$HOME/x`) or one a glob makes;
- what a script or a program it starts reaches on its own (`npm install -g`, `apt install`).

In Ask first every command asks anyway, and its card shows the whole command. In Auto in workspace,
such a command runs. Only an operating-system sandbox could tell more, and PRoot is not one (§2).

### 4.6 What the model is told

The instructions' paragraph on the workspace gains one sentence for the mode in force:
- **Ask first:** "The user approves each change and command, and anything outside “Math”."
- **Auto in workspace:** "Changes and commands in “Math” run without asking; anything outside it is
  asked of the user first."
- **Full access:** "Nothing is asked of the user."

So the model asks the user before it explores another workspace, rather than finding out from a card.

## 5. The architecture

One rule, one gate, and each tool saying what it reaches. No tool decides for itself.

- **Domain** (`domain/`):
  - `enum class AssistantPermission { AskFirst, AutoInWorkspace, FullAccess }` replaces
    `askBeforeChanges`. `ICodeIntent.SetAssistantPermission(mode)` replaces `SetAssistantAskFirst`.
  - `AssistantPermissions.ask(mode, kind, outside, chatAllows)` is a pure function returning
    `null`, `Change`, `Command` or `Outside`. Table §4.2 is its test.
  - `AssistantApproval` gains `reach: AssistantReach?`: the paths as written and resolved, and the
    root the middle answer opens.
  - `AssistantAnswer` becomes `Allow`, `AllowInChat` and `Deny`; `AllowAll` goes.
- **Tools** (`data/assistant/tools/`):
  - `AssistantTool.reach(arguments): List<String>` gives the runtime paths a call touches, `emptyList()` by default.
  - The file, list, search and Git tools give their path. The command tools give the start
    directory and the path words (§4.5, one parser beside `AssistantShell`). The process tools give
    their process's workspace.
  - `AssistantWorkspace.outside(paths)` and `rootOf(path)` (§4.4) both resolve through `GuestPaths`.
- **The gate** (`AssistantTurns.Asking`):
  - It computes what the call reaches outside, less what this chat allowed, and asks `AssistantPermissions`.
  - It shows one card, then applies the answer:
    - `AllowInChat` on an outside card adds the root.
    - On an inside card, it sets this chat to Auto in workspace.
- **Per chat** (`AssistantChats`): `allowingAll` becomes each chat's answers, its mode raised to
  Auto in workspace or not, and its opened roots. They stay in memory, carried by `moved` and
  forgotten by `delete`.
- **Settings:** `AssistantProviderStore` keeps the mode under a new key. With no compatibility code
  before the first release, a phone that had "Allow all changes" starts in Ask first once.
- **UI:**
  - The chip offers the three modes with their lines (§4.1).
  - The card gets its words (§4.3) and the paths line.
  - The chat's empty-state line (`AssistantChatView.kt:229`) says the mode.

**Removed:** `askBeforeChanges`, `SetAssistantAskFirst`, `AssistantAnswer.AllowAll`,
`AssistantChats.allowingAll`/`allowsAll`/`allowAll`, `setAskFirst`, and the "Allow All" caption.

## 6. Cycles

| Cycle | What | Done when |
|---|---|---|
| **P1** Rules | `AssistantPermission`, `AssistantPermissions.ask`, `AssistantReach`, `AssistantAnswer` | §4.2's table as tests, row by row, in each mode |
| **P2** Reach | `reach` on each tool; the command path parser; `outside`, `rootOf` | Tests: `../portofolio`, `/etc/hosts`, `~/x`, a link out, a link in; `cd /root && ls`, quoted and escaped words, `$HOME/x` unseen; roots for another workspace, a repository, `/etc`, a file under `/` |
| **P3** Gate and chats | `Asking` on the rule; one card per call; per-chat answers; the mode in settings and the instructions | Coordinator tests: an outside read asks in Ask first and in Auto in workspace, and not in Full access. An inside change runs in Auto in workspace. "Allow in this chat" stops the next ask under that root only. Deny tells the model. A rename carries the answers |
| **P4** Screens | The chip's three modes; the card's words and paths; the empty-state line | UI type-check; renders of the three cards |
| **P5** Docs and gate | This plan as built; `assistant-koog-plan.md` §4.3–4.4; `runtime-contract-plan.md` §4.3 pointer; README line on what a command's check can see | The full gate, then push the branch and `main` |

## 7. Confirmed by the owner (2026-10-24: "yes go ahead with all three")

1. **The names:** Ask first, Auto in workspace, Full access. They are close to Codex's and say
   where each one's line is.
2. **The default:** Ask first, as today.
3. **The middle answer on an inside card**, "Allow in Math", raises only this chat to Auto in
   workspace, until iCode closes, as "Allow All" does today.

## 8. As built

The rule, the gate and the tools are as §4–§5 describe. These are the differences:

- **The card's answers** (`AssistantApprovalCard.kt`):
  - Inside card: Deny · **Allow in Math** · Allow.
  - Outside card: Deny · **Allow in Chat** · **Allow Once**. A root does not fit on a phone-width
    button, so the folders it opens are in the caption shown when the card is opened. When nothing
    smaller than `/` would do (a file directly under `/`), the middle answer is not offered.
  - The card says the paths as they resolve in the runtime ("Reaches /workspace/portofolio"), not as written.
- **The rule's inputs:** `AssistantPermissions.ask(permission, kind, outside)` takes the chat's
  answers in two places, not as a fourth argument:
  - the gate drops the paths under the chat's opened roots before it asks;
  - "Allow in Math" raises the chat's mode through `AssistantPermissions.effective`, which
    `AssistantState.permission` and the instructions both use.
- **Deny's words** are the domain's (`AssistantPermissions.denied`), so the model's text and the tests read one source.
- **What a tool reaches** is files on the phone (`AssistantTool.reach(arguments): List<File>`), as
  the tools already resolve them; `AssistantWorkspace.outside` turns those outside into runtime paths.
- **Choosing a mode** in the chip forgets the shown chat's answers, both the raise and the opened
  roots, so the mode chosen is what that chat runs in. Other chats keep theirs.
- **The setting** is stored under `permission`. The old `ask_before_changes` value is not read, so
  every phone starts in Ask first.
- **The instructions' sentence** for Ask first ends "… before it runs".
- **The README** in the runtime says what a command's check cannot see (§4.5).
