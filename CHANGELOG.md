# Changelog

All notable changes to iCode are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- The assistant asks you when a choice is yours ([docs/assistant-questions-plan.md](docs/assistant-questions-plan.md)).
  Mid-task it shows one to four questions above the message box, each with options and what each
  means, plus Other for your own words. Several questions go one at a time and are sent together;
  × skips them. The task then carries on with your answers. You can also type or say a reply
  instead. This works from the bubble too, where the question is read aloud on a spoken turn. It is
  asked in every mode, and the step keeps the questions with your answers.
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
- Theme variants ([docs/theme-variants-plan.md](docs/theme-variants-plan.md)). Dark, Light and
  AMOLED each have variants, chosen in Appearance from a sheet of live previews. A variant is used
  as soon as you tap it, and the sheet stays open so you can compare.
  - Dark has 16 variants: iCode's own, Catppuccin Mocha, Dracula, Gruvbox Dark, Nord, Rosé Pine,
    Solarized Dark, Tokyo Night, and antigem's Amber Night, Aurora, Deep Ocean, Forest, Graphite,
    Midnight Aurora, Obsidian and Sunset.
  - Light has 13: iCode's own, Catppuccin Latte, Gruvbox Light, Rosé Pine Dawn, Solarized Light,
    Tokyo Night Day, and antigem's Lavender, Linen, Mint, Paper, Porcelain, Sakura and Sky.
  - AMOLED has 18: iCode's own, every Dark variant on true black, and antigem's Midnight and
    High Contrast.
  - Each variant colours the app, the editor's code and the terminal its own way. Every colour still
    reads at the contrast iCode's own themes keep, with Border Lines on or off.
  - Each theme remembers its own variant. A theme without one looks exactly as before.
  - Match Phone, in Appearance, shows Light while the phone is light and Dark or AMOLED while it is
    dark, each in its own variant.
  - Settings search finds every variant by name.
- The tool dock ([docs/dock-navigation-plan.md](docs/dock-navigation-plan.md)). On the Explorer,
  Editor, Search, Source Control, Assistant and Terminal, a grip in the screen's edge under the bell
  opens one row of the six tools. A tap switches to a tool as you left it and tucks the row; Back or
  a touch elsewhere tucks it too. A swipe up or down along the grip switches straight to the tool
  you used before.
  - Each icon carries its tool's badge: unsaved files on the Editor, changed files on Source
    Control, errors and a dot while a command runs on Terminal, the accent while the assistant
    works and amber while it waits for you. The Explorer's rail shows the same badges.
  - When something happens out of sight, the dock suggests one tool by colouring the grip and that
    tool's icon: the assistant asks you something, a pull leaves conflicts, a build finds errors, a
    command that ran 30 seconds or more ends, or the assistant's turn changes files. A tap on it goes
    straight there, such as Terminal's Problems tab. It never switches by itself.
  - TalkBack reads each tool with its badge and the suggestion, and offers "Switch to …" for the
    tool you used before.
- The undo pill, the bell and the dock can each be moved. Hold one until the phone ticks, drag it,
  and let go: it goes to the nearer edge, left or right, at that height, and stays there after a
  restart. A keyboard coming up moves it down with the room, and pills never cover one another.
  TalkBack offers Move to the other edge, Move up and Move down.

### Changed
- Each workspace tool keeps its own history. Going to a tool shows it as you left it, never a
  second copy. Back first goes back inside the tool, then to the tool you used before, then to the
  Launcher, then leaves iCode. What a tool holds, such as a commit message being written, stays
  while you use the others. A file, a terminal or a new repository opened for you shows its tool's
  main screen, closing a page such as Find left over it.
- Inside a workspace the bell and the undo pill stand one step higher, above the dock.
- The floating pills are bigger and easier to open: 48 dp tall, with larger icons, and a tucked
  pill shows a wider strip with a longer grip.
- The editor's suggestion list marks the chosen row with a quiet lift instead of a reddish block,
  set apart from the rows beside it, so its highlighted letters stay clear.
- File and folder icons: each kind of file and folder has its own icon, from Material Icon Theme
  (5.40.0, the icon theme most used in VS Code). This covers 1391 extensions, 2156 file names and
  4674 folder names, such as TypeScript and its `.d.ts` declarations, `package.json`, `Dockerfile`,
  `src`, `node_modules`, `.git` and `.github`. A folder's icon changes when it is expanded. One icon
  set is drawn everywhere: the Explorer, the file explorer, sheets, Quick Open, Source Control,
  Problems and tab menus. Every icon is at least 3:1 against the background on every theme. The
  default folder takes the accent colour.
- SVG files show a picture of themselves in the file explorer's grid.
- One loading behaviour for the whole app ([docs/loading-plan.md](docs/loading-plan.md)). A tap is
  answered at once, even while iCode is still busy with something else: the button or row you tapped
  stops taking taps, keeps its label, and shows a spinner if the work takes longer than a moment
  (TalkBack says "Working"). A second tap does nothing until the first is done. This covers opening
  a workspace or a file, saving, closing a tab, staging, committing, branches, conflicts, Git
  credentials, language servers, Run, the terminal's +, runtime repair and reset, and clearing the
  cache.
- Questions stay open until their answer is carried out — discard changes, recover a draft,
  overwrite a newer file, a file changed on disk, new folder, new file, rename — with the chosen
  button working, then close.
- Nothing is shown as empty, or as the last thing, before it is read: the Workspaces list shows its
  rows' outline at launch instead of "No workspaces yet"; Diff and Conflict say they are reading
  instead of showing the previous file; settings pages wait for your settings before showing their
  controls.
- One spinner, one "waiting" line and one progress bar everywhere; a clone whose share Git has not
  given yet shows a moving bar instead of one stuck at zero.
- The editor's suggestion list ([docs/editor-completion-plan.md](docs/editor-completion-plan.md)).
  - Each row shows a badge for its kind (function, class, field, keyword…) in the editor's own
    colours, the name with the letters you typed highlighted, its parameters after it, and its type
    at the right.
  - The list is as tall as its rows. It opens just under the line, or above it near the bottom of
    the screen, starting under the word you are typing.
  - Suggestions match as in VS Code: `aw` lists `await` and `AudioWorklet`, not `DataView`, best
    match first.
  - The list no longer opens after a space or `;`.
  - The first row is shown as chosen, since Enter takes it. The key row's Tab takes the chosen row,
    and its arrows move the choice.
  - A server's suggestions past its 500th are no longer dropped before filtering, and a suggestion
    two servers give shows once.

### Fixed
- Going round in circles: the Assistant's Workspace button stacked a second Explorer, the empty
  editor's ← put a Launcher on top of the workspace, and Settings' "Open a Workspace" stacked a
  second Launcher. Workspace now switches to the Explorer, the empty editor's ← is Back, and "Open
  a Workspace" goes to the one Launcher.
- A terminal scrolled back, or a chat scrolled up to an earlier answer, jumped to its end when you
  came back to it. Both come back where you left them.
- The Explorer's rail said "Source control"; it says "Source Control" as everywhere else.
- A count on a floating pill's icon, such as the dock's Source Control changes or the bell's unread
  notices, was cut off at its corner, and the line under the dock's icons showed as a smudge. Both
  are drawn whole.
- `.ts` files were taken for videos: TypeScript filled Files › Videos, its Properties said "Video",
  and Open with offered video players. A `.ts` file is TypeScript unless its contents are an MPEG
  transport stream, as a TV recording is. A file with no known extension is recognised by its
  contents too: a picture, a video, a PDF or an archive. One table now decides what every file is,
  for the categories, thumbnails, viewers, icons and the type other apps are told.
- A file that gives no thumbnail is no longer decoded again each time it scrolls back into view.
- Large Manage storage views (thousands of files) no longer re-sort on every redraw.
- The assistant: when a provider error is retried after the model has already done something in
  the turn, the retry carries on from the chat. It no longer runs those steps again or asks
  questions that were already answered.

## [0.1.0]

First version: workspaces, editor, embedded Ubuntu runtime, terminal, Git, language services and the AI assistant.

[Unreleased]: https://github.com/itzmejanak/iCode/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/itzmejanak/iCode/releases/tag/v0.1.0
