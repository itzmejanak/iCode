## AI Commit Message Generator

 Add a **magic ✨ button inside the commit message input field** on the Source Control screen ensuring that no duplicay no inconsistency if needed any third-party native integration for better functionality without comparmising the scalability and follow the best practices of abstraction and without overengenering make exactly what and how it need to be.

 When the user has staged changes, they can tap the magic button and the AI will:

 1. Look at the currently staged code changes.
2. Understand what was changed and why.
3. Generate a concise, meaningful commit message.
4. Automatically place that message into the commit message field.
5. Let the user review or edit it before committing.

### User Experience

 - **No staged changes:** Magic button is disabled.
- **Staged changes available:** Magic button is active.
- **Generating:** Button shows a small loading animation.
- **Generation complete:** The generated commit message appears directly in the input field.
- **Generation fails:** Show a clear, friendly error message and keep the existing text unchanged.
- **AI provider/model:** Use whichever AI assistant the user has currently configured and Active by default but from source control settings user can assign any provider model for default overide.

 ### Example

 The user stages changes such as:

 - Added a login screen.
- Added password validation.
- Updated authentication handling.

 They tap **✨ Generate**.

 The input automatically becomes:

 > `feat(auth): add login validation and authentication flow`

 The user can then edit it if needed and commit normally.

 ### Overall Goal

 Make creating commit messages **one tap instead of a manual task** while keeping the experience fast, unobtrusive, and consistent with the existing Source Control UI.
---

## Plan, decided (2026-10-08)

The owner's brief above, taken as written; each decision rests on iCode's code as it is.

### Decisions

| # | Decision | Why |
|---|---|---|
| M1 | **One owner:** `AssistantCommitMessages` (`data/assistant`), driven by assistant intents — `WriteCommitMessage`, `CommitMessageTaken` — on the assistant's lane, each writing its own job | The assistant owns the providers, keys and engine; its intents are lock-free and in order, and a slow model never holds a chat, a commit or the workspace lock |
| M2 | **What the model reads:** the repository Git works in for the open workspace (as Commit does: `CliGitService.run(workspace, …)`) — `git diff --cached --numstat --find-renames` (each file's lines added and removed, its path never cut short as `--stat` cuts it; 8 000 characters), `git diff --cached --find-renames` (bounded: 24 000 characters, head and tail), and its last 10 commit subjects (`git log -n 10 --no-merges --format=%s`; none in a repository without commits) | The staged changes are what will be committed. The subjects make the message follow the repository's style — Conventional Commits where it uses them, as in the brief's example — without a setting |
| M3 | **What it is asked:** one subject line in the imperative, at most 72 characters, no period; a body after a blank line only when the change needs its *why*; the message alone. What comes back is cleaned (`CommitMessageRules.cleaned`): a `<think>` block some compatible servers put in the text, a code fence, quotes around it | A message to commit as is |
| M4 | **The engine:** one request answered with no tools and nothing streamed — `AssistantEngine.answer`, sharing compaction's code path (`KoogAssistantEngine.written`) — no effort sent (the provider's default) | No second copy of the one-shot request. The provider's errors are said in the same words as a chat's |
| M5 | **The model:** the assistant's model by default; a model chosen in **Settings › Source Control › Commit Message Model** instead (`commit.provider`, `commit.model` in the assistant's preferences). A choice whose model is no longer listed reads as the default | The brief: "whichever AI assistant the user has currently configured … from source control settings user can assign any provider model" |
| M6 | **The result is state, taken once:** `AssistantState.commitMessage` — the workspace it was written for, writing or not, the message or the failure. The Source Control screen puts a written message into the field, replacing what was there, and says `CommitMessageTaken`; a failure leaves the field as it was and is said under it until the next tap or an edit | Kept as state, a message written while the user was on another page is there when they come back; taken once, it never overwrites what they typed since |
| M7 | **The button** is in the field, at its end (`IcodeTextField`'s trailing slot): ✨ (`AutoAwesome`, the assistant's icon in Settings), disabled with nothing staged, a small spinner while writing. Without a provider and model, a tap says where to add one | The brief's states, in the field as asked |
| M8 | **Settings › Source Control** becomes a page: **Git Credentials** (the wizard, as before) and **Commit Message Model** | One place for Source Control's settings; Source Control's own "Set Git Identity" still goes straight to the wizard |
| M9 | Nothing logged of a diff, a message or a model's answer: a failure's type only | The assistant's rule (PR-ASSIST-03's spirit; chats never reach the log) |

### Cycles

| # | Step | Verified by |
|---|---|---|
| M-1 | Domain: `AssistantCommitMessage`, `CommitMessageRules` (prompt, cleaning), intents, `AssistantSettings.commitChoice` | Unit tests |
| M-2 | Engine `answer`; store's commit choice; `AssistantCommitMessages`; coordinator and gateway wiring | Unit tests: the engine over a scripted client; the owner over a fake Git and engine (nothing staged, not set up, written, failed, taken, the override, its fallback) |
| M-3 | The ✨ button; the Source Control settings page and its route | Compose type-check; UI guard |
| M-4 | PRD, user stories, this plan as built; guards; API-30 check; commit | Review |

### As built (2026-10-08)

- **Domain:** `AssistantCommitMessage` (workspace, message, failure; `writing` while neither),
  `AssistantState.commitMessage`, `AssistantSettings.commitChoice` and `commitWriter` (the chosen
  model while its provider lists it, else the chat's), `CommitMessageRules` (`INSTRUCTIONS`,
  `request`, `cleaned`), intents `WriteCommitMessage`, `CommitMessageTaken`, `ChooseCommitModel`.
- **Engine:** `AssistantEngine.answer` — `KoogAssistantEngine.written`, now compaction's path too;
  its words: "The model's answer was cut short at its output limit — try again", "The provider's
  content filter stopped the answer", "The model sent an empty answer — try again".
- **Owner:** `AssistantCommitMessages`, made by `AssistantCoordinator` with the gateway's
  `CliGitService`. A second tap stops the first writing; only the latest shows. "Set up the
  assistant first: add a provider and a model in Settings › Assistant"; "Nothing is staged: stage
  the changes to describe first"; Git's own words when it cannot read them.
- **Store:** `commit.provider`, `commit.model`; refused when not listed; read only while listed.
- **Screens:** the field (`IcodeTextField`'s new `trailing` slot) is multi-line, so a body shows;
  `WriteMessageButton`; the failure under the field in red, gone at the next tap or edit.
  `SourceControlSettingsScreen` (route `SourceControlSettings`): Git Credentials, Commit Message
  Model (the assistant's, "Now <model>", then every provider's models, each under its provider's
  name), a line on what is sent where, and Add a Model Provider when there is none.
- **Tests:** `CommitMessageRulesTest` (the request; thinking, code blocks and quotes taken off, a
  message's own quotes kept); the engine's one-shot answer, with no tools, and cut short; the
  store's choice (kept, refused, dropped with its model, cleared); the coordinator end to end over a
  fake Git and engine (written by the chat's model from the subjects, files and diff, then taken;
  the chosen model; nothing staged; a refusal; a repository without commits); the route and the
  catalogue (Settings search "commit message" and "token" open the new page).
