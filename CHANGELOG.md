# Changelog

All notable changes to iCode are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Language servers, end to end. Settings › Languages adds one from a known server (pylsp, Pyright,
  clangd, gopls, TypeScript, Bash, HTML, CSS, JSON, YAML — each with the command that installs it) or
  your own, with its languages, file patterns, root markers, environment, initialization options and
  settings. It starts when a file it serves opens, at most three at once, stops after 15 minutes
  without one, and starts again after a crash, until five crashes in three minutes stop it with its
  log. Its page shows what it offers and its log.
- Problems from the language servers: under their text in the editor (its message when the caret is
  on one), counted on the Explorer's files and folders, and in Problems, where a tap opens the exact
  place — a build's problems too.
- A language server whose program is not installed says so on its row and page, with Install in
  Terminal; it is a notice only when you start it. A slow server gets up to 5 minutes to start, and
  shows how long it has waited.
- Completion from the language servers as you type, with their snippets and the imports an item needs.
- Command Palette: Go to Definition, Find References, Go to Symbol in File, Show Hover, Show Signature
  Help, Code Actions, Format Document and Rename Symbol, each listed only when the file's server offers
  it. A change a server makes is applied whole or not at all.
- The assistant sets up language servers as the Languages page does (asking every time) and asks them
  what you would: a file's problems after its edit, definitions, references, symbols, renames and
  formatting.
- Environments: development, staging, and production as preview, beta and stable, each its own app
  that installs beside the others, with its own icon and its icon's colour as the theme accent (grey
  for development, lavender for staging, Ember for production). Preview and beta are released from
  `v…-preview.N` and `v…-beta.N` tags as pre-releases and update only from their own channel; stable is
  the original icon and GitHub's Latest. Staging is built by hand from the release workflow.
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
  Spoken replies: Off, or In the bubble (the default: what is asked from the bubble); the chat is
  read, not heard, and an answer there plays from ▶ beside Copy. A new message, Stop, or a tap on
  the bubble silences it.
- Phone control keeps a notification with Show bubble and Turn off, and holds the microphone for the
  bubble while iCode is away. A turn in hand shows once, in its own notification with Stop.
- One bubble on the screen's edge, in six states — idle, listening, muted, thinking, acting,
  speaking. A tap opens a bar to type in, which grows with what is typed; a tab rising from its end
  holds, for long text, Expand into an editor, and ⋮, which opens New chat and Open in iCode in the
  tab itself. Listening, it becomes a voice bubble: the words heard, Mute and Stop. The bar and the
  voice bubble open at the bottom, by the keyboard, in a window of their own, so the bubble never
  moves for them. Dragged, it follows the finger and glides to the nearest of the bottom, left and
  right edges where it is let go, and shows there the next time; idle, it tucks half behind that edge. Android's own accessibility button is no longer asked for, so there is one icon,
  not two; Phone Control says how to remove one an earlier build left.
- Speech recognition with any provider: Settings › Assistant › Voice › Speech Recognition lists
  Android's recognizer, always there, and the providers you add — AssemblyAI, OpenAI, an
  OpenAI-compatible server, Deepgram or Google Cloud — with their key (encrypted, never shown again),
  model and language, Test and Use for Listening. An OpenAI one may use the assistant's OpenAI key.
  The bubble and the chat's microphone work the same with each; a provider that fails says why in its
  name, and your voice never goes to another instead. The assistant sets them up when asked, asking
  every time.
- Answers read aloud by any provider: the same providers read aloud too — AssemblyAI, OpenAI,
  Deepgram, Google Cloud and OpenAI-compatible servers, and two that only speak, Azure AI Speech and
  ElevenLabs. One provider listens and one speaks, each chosen on the Voice page in a sheet from the
  bottom with Providers at its foot (the page is now Speech Providers). A provider's page has a
  Speaking part: its voice (its own list, or its API's), model and speed where it has them, Hear It
  and Use for Speaking; Android's opens its text-to-speech settings. Each sentence plays as soon as it
  is written, the next made while one plays; other sound is lowered while iCode speaks. A provider that
  fails says why, the answer stays on screen, and nothing goes to another instead. OpenAI reads with
  its realtime model, as its Speech API shuts down on 2027-01-06, and its page says it may stray.
- iCode has no dialogs: every confirmation, question and long operation is a sheet, and every sheet
  closes with a swipe down as well as ×, Back or a tap outside.
- The bell and the undo pill say nothing in words: a notice rings the bell with the unread count, and
  the undo pill shows ↶ and ↷. Both are docked flush on the screen's edge and never go: they tuck
  to their grip, and a tap or a pull brings them out. The explorer's ⋮ has no Undo or Redo now.
- Assistant: a long press on your message offers Edit, and Retry when its turn failed; a message's
  files ride a tab rising from its card, small.
- Assistant: a model that turns out not to see pictures goes on with the screen's text and is set to
  read none; every provider refusal is said plainly with what to do, never as the server's JSON.
- Assistant: Reasons, set on a model of OpenRouter or an OpenAI-compatible server, stays on, and its
  reasoning is chosen Low, Medium or High there too (Auto sends none).
- Assistant: MCP servers. Settings › Assistant › MCP Servers adds one by pasting the configuration a
  README gives, as a local command in the Linux runtime, as a remote https:// address, or from the
  MCP Registry. Their tools join the chats: each asks first ("Use GitHub?", Allow in GitHub, Allow
  Once) unless set to Run, or Off; a reading mode is offered only what reads. A server's question
  shows as a card in the chat, its progress in the step, its pictures to a model that sees them.
  Remote servers sign in with OAuth in the browser; a workspace's .mcp.json is listed once trusted,
  and asks again when it changes. Its prompts and resources go into a message from the attach sheet.
  Secrets are kept encrypted and never shown again.
- Runtime Tools: Node.js and uv, pinned downloads, for MCP servers that start with npx or uvx.
- Assistant: its commands run in a terminal tab of its own in the Terminal panel ("Assistant"), typed
  at a real bash prompt, so `cd` and `export` carry over and colours show. A running command's step
  is that terminal, live: type an answer there — the whole sheet becomes the black terminal, the
  extra keys flush on the keyboard as in the Terminal panel, and ⓘ shows the command — or Open in
  Terminal, on that tab; once it ends
  the step replays what it printed as the terminal drew it. At its time limit, or when the turn is
  stopped, it gets Ctrl-C. It can open another tab, read and type into tabs, and use one of yours
  once you allow it ("Use your terminal bash 2?"). Deleting a chat closes its tabs.
- Assistant: ⋮ › Terminal opens the chat's own terminal tab as it is now, the shell its commands run
  in, for your own commands: a black, edge-to-edge sheet with Open in Terminal beside ×. Compact Chat
  is the context meter's alone, under the composer.
- Assistant: it lists, adds, changes and removes MCP servers as the MCP pages do — from a pasted
  configuration or the fields — asking every time, Full access included. A value given in the chat
  is kept encrypted and never read back.
- Terminal: every bash tab carries iCode's shell integration (`/etc/icode/bashrc`, OSC 133 and OSC 7
  marks, after your own profile), so iCode knows exactly when a command ends.

## [0.1.0]

First version: workspaces, editor, embedded Ubuntu runtime, terminal, Git, language services and the AI assistant.

[Unreleased]: https://github.com/itzmejanak/iCode/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/itzmejanak/iCode/releases/tag/v0.1.0
