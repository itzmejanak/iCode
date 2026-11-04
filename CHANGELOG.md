# Changelog

All notable changes to iCode are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- GPL-3.0 licence, contributing guide, security policy, privacy page and code of conduct.
- Issue and pull request templates.
- CI (unit tests and debug build), CodeQL scanning, Dependabot, and a tag-driven release workflow.
- Assistant: chats belong only to workspaces. The bubble and "Open in iCode" stay in the chat the
  user was in (the open workspace's, else the one shown last, else the most recently opened).
- Assistant: phone storage, at `/sdcard` in the runtime, for every tool and command (asked first, as
  anything outside the chat's place; read with All Files Access).
- Assistant: at its step limit a turn asks "Keep going?" instead of stopping.
- Assistant on the whole phone: iCode's accessibility service reads the screen as numbered
  elements and operates it (tap, type, scroll, keys, apps, links and Settings pages by name),
  asking before it acts in an app ("Allow in WhatsApp for this chat") and handing passwords,
  payments and permission dialogs to the user.
- Shizuku: one tap turns phone control on, and real touches reach what accessibility is refused.
- The bubble over every app — the question waiting, a field, Send and Stop, Open in iCode — and
  the screen's edges glowing in the accent while the assistant acts; dragged onto ✕, both go away.
- Settings › Assistant is a list of pages — Providers, Voice, Phone Control — laid out as the rest of
  Settings; Providers lists the model providers, and + opens a sheet of provider kinds.
- Settings › Assistant › Phone Control: off by default; the steps through Allow restricted settings
  and Accessibility, or one tap with Shizuku; the bubble; Turn Off. Its Shizuku page shows the state
  read live, the one step each state needs, and what it adds.
- Voice: the microphone in the chat's composer and the bubble shows the words as they are heard and
  sends them once you stop talking, with Android's speech recognition (on the phone where Android
  can). Answers and questions are read aloud with Android's text-to-speech sentence by sentence as
  they are written, and a turn to be read aloud asks the assistant for a short spoken answer.
  Spoken replies: Off, In the bubble (the default: what is sent from the bubble or by voice), or
  Always; a new message, Stop, or a tap on the bubble silences it.
- Phone control keeps a notification with Stop, Show bubble and Turn off, and holds the microphone
  for the bubble while iCode is away.
- One bubble at the bottom of the screen, in six states — idle, listening, muted, thinking, acting,
  speaking. A tap opens a bar to type in, which grows with what is typed and expands into an editor;
  ⋮ above it holds New chat and Open in iCode. Listening, it becomes a voice bubble: the words heard,
  Mute and Stop. It tucks half away when idle. Android's own accessibility button is no longer asked
  for, so there is one icon, not two; Phone Control says how to remove one an earlier build left.

## [0.1.0]

First version: workspaces, editor, embedded Ubuntu runtime, terminal, Git, language services and the AI assistant.

[Unreleased]: https://github.com/itzmejanak/iCode/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/itzmejanak/iCode/releases/tag/v0.1.0
