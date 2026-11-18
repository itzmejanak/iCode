# Changelog

Notable user-visible changes to iCode are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and version numbers follow [Semantic Versioning](https://semver.org/).

[Unreleased]: https://github.com/itzmejanak/iCode/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/itzmejanak/iCode/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/itzmejanak/iCode/releases/tag/v0.1.0

## [Unreleased]

### Added
- Each chat keeps the model it answers with, and switching models in a chat hands the whole
  conversation to the new one. A new chat starts with the model chosen last.
- Each reply is named by the model that wrote it, with its provider's mark, and the model sheet
  lists every provider under its own mark.
- Several chats can work at once, each with its own step, questions, Stop, and notification;
  tapping a chat's notification opens that chat. One chat at a time acts on the phone.
- A message sent while a chat works waits in a queue above the composer, saved with the chat,
  and goes when the work ends. A waiting message can be edited, removed, or sent now: the same
  model takes it after its current step and goes on with it; another model takes over.
- Undo on a message puts the files and the chat back to before it, or only the files, or only the
  chat. Everything since is undone, and files changed outside the chat since are listed first so
  you can decide.
- Settings › Assistant › Coding Agents installs, updates, and removes Claude Agent, Codex, Gemini
  CLI, OpenCode, Cline, and Factory Droid from the ACP registry, installing Node.js first where an
  agent needs it. Updates are checked when the page opens and once a day, with Update All for
  several; removing asks whether the agent's sign-in and settings stay.
- Coding agents answer chats: choose one in the model sheet's new Agents tab. A card in the chat
  shows each way the agent signs in, by itself, in the terminal, or with keys kept encrypted. Its
  replies, reasoning, and steps show as the assistant's do, with what each reply cost when the
  agent says so; its permission requests show its own choices. Open files are saved before each
  message, and Try again, Edit, and Undo start the agent afresh from the chat as it stands.
- An agent chat shows the agent's mode, model, and thinking as chips, ready as the chat opens. Every
  chat starts in the agent's mode that asks before changes, and modes that act without asking say
  so. Typing / lists the agent's commands and other settings, and while the agent works through a
  to-do list, the jump-to-latest button shows its progress, such as 2/5, with the list a tap away.
- A chat iCode was closed in while it worked says so and offers Continue, which picks the work up
  where it stopped. An agent that has stopped picks up its own session again where it can.
- A coding agent's questions show on the same card as an MCP server's: a short form, or a page
  the agent wants opened, answered or declined there.
- Deleting a chat, or starting it over with Try again, Edit, or Undo, also deletes the coding
  agent's own copy of the conversation, where the agent can delete it.
- Find Agent Chats in the chat list shows conversations started with Claude Agent, Codex, or OpenCode
  outside iCode in that place, by date. Opening one brings in everything said so far as a chat.
- Coding agents answer from the bubble and by voice. A message sent aloud asks the agent for a short
  answer that reads well, as the built-in assistant is asked.
- Updating a coding agent while it works in a chat downloads at once and switches to the new release
  when its work ends, instead of replacing the program under it.
- A chat too long for a coding agent to take in whole goes to it as a summary of the older part,
  written by your chat model, and the newest part in full.
- An agent chat's menu opens the agent's own program in the chat's terminal tab. Claude Agent,
  Codex, and OpenCode carry on the chat's own conversation there, and the chat picks up what was done.

### Changed
- A turn no longer stops at 40 steps to ask whether to keep going. It works until the task is done
  or you stop it, and when the chat nears its model's window mid-task, it compacts the chat and goes
  on by itself.
- Chats keep every message and every command's output in full, however long they grow. A long
  chat saves only what is new instead of writing the whole chat again, and a model request still
  sends at most the newest 400 messages after the last compaction.
- Sharing a chat sends its whole transcript as a Markdown file instead of text cut off at 100,000
  characters.

### Security
- What the assistant's libraries log no longer reaches Android's system log. A logging library
  that came with the model clients printed their messages there; it is replaced by one that
  drops them.

## [0.2.0] - 2026-11-16

### Added

- End-to-end language-server support with presets for common servers, diagnostics in the editor and Problems, completion, hover, signatures, definitions, references, symbols, code actions, formatting, rename, and assistant language tools.
- Stable, beta, preview, staging, and development environments with separate packages, data, and branding; stable, beta, and preview use separate update channels, chosen by release tag.
- Assistant questions, phone-storage access after asking, chats outside any workspace, editing sent messages, Continue at the step limit, and terminal-backed commands.
- Optional phone control with an assistant bubble, Android accessibility integration, Shizuku support, sensitive-screen handoff, and foreground controls.
- Configurable speech recognition and synthesis through Android services or supported remote providers.
- Local and remote MCP servers, tool approval controls, OAuth return handling, prompts/resources, encrypted secrets, and assistant integration.
- Runtime tool setup for Node.js and uv/uvx.
- Dark, light, and AMOLED theme variants, per-theme selection, phone-theme matching, and settings search for every variant.
- Workspace tool dock, badges, contextual suggestions, and movable edge controls for tools, notices, and undo/redo.

### Changed

- Replaced every dialog with a sheet that swipes closed.
- Replaced overlapping plans and implementation journals with a verified product contract, current architecture, focused subsystem guides, and one roadmap for unfinished work.
- Expanded syntax highlighting to 357 bundled TextMate language and format definitions, loaded on demand and shared by the editor, Markdown, chat, diffs, conflicts, search results, and assistant change views.
- Added bounded behavior and clear fallback messages for large files, long lines, and individual grammar failures.
- Redesigned the launcher, recent workspaces, file and folder icons, workspace navigation, runtime status, empty editor, search, About, and loading states.
- Kept home terminal/assistant sessions separate from workspace sessions and preserved each workspace tool's navigation and scroll state.
- Reworked notifications and background-work reporting with clearer locations, outcomes, actions, filtering, and finished-work alerts.
- Improved editor completion ranking and presentation, language-aware file detection, semantic highlighting, and loading/confirmation behavior.

### Fixed

- Prevented floating controls from resetting or drawing above sheets.
- Preserved terminal and chat scroll position and corrected duplicate navigation stacks and terminal-session context.
- Stopped Quick Open from matching the folders that hold workspaces and made long recent-file lists in the empty editor scroll.
- Distinguished TypeScript `.ts` files from MPEG transport streams and improved content-based media detection.
- Avoided repeated thumbnail decoding and unnecessary sorting in large storage views.
- Continued assistant retries from completed tool steps instead of running those steps or answered questions again.

## [0.1.0] - 2026-11-01

### Added

- Native Android workspace management for creating, importing, cloning, renaming, and deleting projects in app-private storage, with access through Android's Files app.
- A native editor with syntax highlighting, tabs, autosave and draft recovery, external-change handling, find and replace, Markdown preview, Quick Open, and workspace search.
- A checksum-verified Ubuntu ARM64 userspace under PRoot, a Termux-based terminal with sessions retained while iCode's process remains alive, background process controls, and installable Linux tools.
- Git status, diffs, staging, commits, branches, history, conflicts, fetch, pull, push, HTTPS/SSH authentication, and GPG signing.
- Language-service settings that save, start, and stop language-server processes.
- Android project Build and Run through the project's Gradle wrapper and Android's package installer.
- A bring-your-own-provider AI assistant with local per-workspace chat history, file and image attachments, retries, approval modes, workspace file tools, commands, and Git integration.
- Runtime, storage, editor, terminal, language, Git, appearance, and application settings with dark, light, and AMOLED themes.
- Android notifications for long-running runtime, terminal, Git, build, and assistant work.

### Security

- Keystore-backed encryption for supported stored credentials and provider secrets.
- Digest verification for pinned runtime and tool downloads and SHA-256 publication for release APKs.
