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
- Settings › Assistant › Phone control: off by default; says what is read and where it goes, walks
  through Allow restricted settings and Accessibility, or turns on in one tap with Shizuku; shows or
  puts away the bubble; turns off.
- Settings › Assistant › Phone control › Shizuku: its state read live, the one step each state needs,
  and what it adds.
- Settings › Assistant is a list of pages — Providers, Voice, Phone control; Providers lists the
  model providers, and + opens a sheet of provider kinds.
- Voice, with Android's engines: the microphone in the chat's composer and the bubble listens with
  Android's speech recognition and puts what is heard in the message; Settings › Assistant › Voice
  allows the microphone and chooses when answers are read aloud with Android's text-to-speech (Off,
  In the bubble — the default — or Always), reading only the answer's words, without code or links.

## [0.1.0]

First version: workspaces, editor, embedded Ubuntu runtime, terminal, Git, language services and the AI assistant.

[Unreleased]: https://github.com/itzmejanak/iCode/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/itzmejanak/iCode/releases/tag/v0.1.0
