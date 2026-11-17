# Changelog

Notable user-visible changes to iCode are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and version numbers follow [Semantic Versioning](https://semver.org/).

[Unreleased]: https://github.com/itzmejanak/iCode/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/itzmejanak/iCode/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/itzmejanak/iCode/releases/tag/v0.1.0

## [Unreleased]

### Added

- External coding agents in the Assistant through the Agent Client Protocol: Claude Code, Codex, Gemini CLI, OpenCode, Cline, and Factory Droid, installed as runtime tools from pinned sources and chosen in the chat's model sheet. Their chats keep the Assistant's page, approvals with each agent's own options, questions, a plan row, the agent's settings as composer chips and its slash commands under "/", sign-in, the user's MCP servers, and the agent's own CLI in the chat's terminal tab.

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
