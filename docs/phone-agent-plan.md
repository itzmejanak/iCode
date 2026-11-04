# The assistant on the whole phone (Plan)

Status: **plan** (2026-11-03). It follows [assistant-koog-plan.md](./assistant-koog-plan.md),
[assistant-permissions-plan.md](./assistant-permissions-plan.md) and
[assistant-sheets-plan.md](./assistant-sheets-plan.md), and widens the assistant from one workspace
to the phone.

## 1. The owner's request (2026-11-03)

> iCode's assistant today works only inside an isolated Linux sandbox (PRoot): it can read and edit
> workspace files, run commands in that sandbox, and use Git. It **cannot interact with the rest of
> the phone** — other apps, the screen, system UI, or input. The user cannot ask the assistant to
> complete real-world phone tasks (e.g., "open Settings and turn on Wi-Fi", "fill this form", "reply
> in WhatsApp"), because the assistant has no access to anything outside its own sandbox.
>
> The intended experience: when the agent starts working on the phone, the **phone's edge glows with
> the faded accent color** to signal it is active; a **floating bubble — the iCode icon, holding tools
> like voice and input** — appears so that after leaving iCode, the user can instruct the agent from
> **any screen, via an overlay type**, without switching back into the app.
>
> Research is needed on **fast performance** so the agent can operate things very quickly. If
> third-party tools are needed (native ones preferred, though not mandatory), proper research must be
> done rather than rebuilding things from scratch. Any **limitations by design** should be **fixed
> first in the correct direction** — rather than piling on more limitations.

Then: "Make sure don't reject third-party most best you found just because of license stuff."
So §3.7 ranks every candidate on what it does; a licence is a line saying what it requires, for the
owner to accept or not, never a reason to drop it.

Then, on this plan (2026-11-03), with "Let's implement end to end":

> Shizuku will be part of our first implementation, so users can use it as per their need. We'll
> include a one-page Shizuku setup & state guide inside the app. As for iCode's UI state outside the
> app: the core behavior is as described above — when the user doesn't need the agent, they can
> simply hide the icon from the edge of the screen, and the edge's accent glow goes away with it.
> That's the best UI/UX behavior.
>
> The agent should also be able to **access shared storage** — something iCode currently lacks.
>
> **Speech recognition and voice speaking** are also key. "Voice speaking" means the agent responds
> via **Text-to-Speech (TTS)** — a human-like voice — so it isn't just reading text. If any
> open-source AI model supports this in **any language**, we should consider implementing it; this
> improves **accuracy** and **user interaction**.

So Shizuku moves from "later" into the first cut (§5.11), the bubble can be put away (§5.7), shared
storage becomes the agent's too (L8), and voice goes both ways (§3.4, §5.8). Open-source models on
the phone were built (P6), and on 2026-11-04 the owner chose Android's own engines alone: "for the
first time iCode will focus on Android".

## 2. What is there now (read in the code, 2026-11-03)

| Part | Where | Today |
|---|---|---|
| A chat's place | `AssistantState.workspace`, `AssistantChatStore`, `AssistantTurns` | Every chat belongs to a workspace; "Open a workspace to ask the assistant" otherwise. The system prompt says "You work in *name*" |
| Tools | `AssistantToolbox.forWorkspace` | Sixteen, all over the workspace and the runtime: files, search, Git, commands, processes, the Run tab |
| A tool's result | `AssistantTool.run(): String` | Text only. A picture reaches the model only as the user's attachment |
| A step | `KoogTurn.strategy` | The model replies; its calls run one at a time; the results go back as one message; the model is asked again. Seeing what a call did is another call |
| The step limit | `AssistantTurns.MAX_STEPS = 40` | A hard stop |
| Approvals | `AssistantPermissions`, the chat page, `WorkNotifications` | On the chat page; while iCode is away, a notification on "Needs your answer" |
| Modes | `AssistantPermission` | Ask first, Auto in workspace, Full access: written in terms of a workspace and what is "outside" it |
| Background | `RuntimeForegroundService` | One foreground service, type `specialUse` |
| Permissions | the manifest | No accessibility service, no `RECORD_AUDIO`, no overlay |
| Distribution | README, the release workflow | An APK from GitHub Releases, installed from a downloaded file. Android 11 (API 30) to 16 (target 36). GPL-3.0 |

## 3. Research (read, not assumed)

### 3.1 How an app can see and act on the phone

| Channel | What it gives | Who may use it | Verdict |
|---|---|---|---|
| **`AccessibilityService`** | The windows and their node trees (`getWindows`, API 21; `getRootInActiveWindow` with a prefetching strategy, API 33); actions on nodes (click, long click, set text — API 21; `ACTION_IME_ENTER`, `ACTION_PRESS_AND_HOLD` — API 30; `ACTION_SCROLL_IN_DIRECTION` — API 34); gestures (`dispatchGesture`, API 24); system keys (`performGlobalAction`: back, home, recents, notifications, quick settings, lock screen…); screenshots (`takeScreenshot`, API 30; `takeScreenshotOfWindow`, API 34, "if your target window might be visually underneath an accessibility overlay"); typing at the cursor as a keyboard does (`getInputMethod`, `FLAG_INPUT_METHOD_EDITOR`, API 33); faster window events with animations off (`setAnimationScale`, API 33); its own trusted overlay windows (`TYPE_ACCESSIBILITY_OVERLAY`) | Any app the user enables in Settings › Accessibility | **The base.** Native, on every version iCode runs on, needs no computer and no root |
| Intents, deep links, Settings pages | Open an app, a link, a Settings page or panel, a share, a dial, in one call | Any app | **The fast path** (§5.4) |
| **AppFunctions** (Android 16) | Apps' own functions as tools: "Android MCP" | Callers need `EXECUTE_APP_FUNCTIONS`: `internal|role`, "currently only granted to preinstalled / system apps having the ASSISTANT role" (frameworks/base `AndroidManifest.xml`) | **Not callable by iCode.** Revisit when it opens (§9) |
| **Shizuku** (shell identity) | Input injected as the shell's (`INJECT_EVENTS`), `screencap`, `settings`, `cmd` | Any app, once the user starts Shizuku through wireless debugging (Android 11+, no computer), again after each reboot | **Optional privileged mode** (§5.11): it reaches what accessibility is denied |
| The ASSISTANT role (`VoiceInteractionService`) | The system's assist gesture opens iCode over any screen; its foreground services keep microphone access from the background | An app the user picks as the default digital assistant. Blurr's own code notes "many OEMs only list apps with a VoiceInteractionService" | **Optional entry** (§5.11), to be proven on phones |
| MediaProjection | Screen video | Any app, with the user's consent | **Not needed:** accessibility screenshots exist from API 30, iCode's minimum |

### 3.2 What the platform withholds (limits by design of Android, not of iCode)

- **Restricted settings.** An app installed from a local or downloaded file
  (`PACKAGE_SOURCE_LOCAL_FILE`, `PACKAGE_SOURCE_DOWNLOADED_FILE`) is guarded: its accessibility
  service cannot be turned on until the user taps **Allow restricted settings** in its App info
  (`InstallPackageHelper`, frameworks/base). Newer releases decide guardedness lazily
  (`extendEcmToAllSettings`). iCode is installed this way, so setup must walk the user through it.
- **Sensitive views.** An app can mark a view `accessibilityDataSensitive` (Android 14; implied by
  `filterTouchesWhenObscured`). A service that is not a declared accessibility tool cannot read
  it or act on it, and from Android 16 its `dispatchGesture` touches on it are blocked too
  (developer.android.com, Tapjacking guide). Login and payment screens use this.
- **`isAccessibilityTool` cannot be claimed.** It is for services that help disabled users. "If an
  app is not an accessibility tool but requests accessibility permissions and sets
  `isAccessibilityTool=true`, Play will reject it and Google Play Protect will block it on user
  devices" (Android Developers blog, 2025-12-11). iCode declares `false`.
- **Secure windows.** `takeScreenshot` fails with `ERROR_TAKE_SCREENSHOT_SECURE_WINDOW` over a
  `FLAG_SECURE` window. Its tree is still read.
- **One screenshot per 333 ms** (`ACCESSIBILITY_TAKE_SCREENSHOT_REQUEST_INTERVAL_TIMES_MS`).
- **Android 17 Advanced Protection** (reported for beta 2): with that mode on, accessibility
  services that are not tools are revoked. It is the user's opt-in mode.
- **These are not worked around.** Where the platform says no, the agent stops and hands the step
  to the user (§5.5), or the user chooses Shizuku (§5.11), which acts as ordinary input.

### 3.3 Overlays: the glow and the bubble

- **Accessibility overlays are trusted.** Android 12 blocks touches passing through other apps'
  overlays — `TYPE_APPLICATION_OVERLAY` windows are "not trusted", and only pass touches when their
  combined opacity is at most 0.8 — but "accessibility windows" are trusted
  (developer.android.com, Android 12 behaviour changes). An accessibility service also needs no
  `SYSTEM_ALERT_WINDOW` for them.
- **Over a screenshot.** `takeScreenshotOfWindow` (API 34) captures a window from beneath overlays;
  below 34, iCode's own overlays are hidden for the capture (§5.6).

### 3.4 Voice from any screen

- **`SpeechRecognizer`:** `createSpeechRecognizer` (API 8) uses the user's recognition service;
  `createOnDeviceSpeechRecognizer` and `isOnDeviceRecognitionAvailable` (API 31) keep audio on the
  phone. "The application must have `RECORD_AUDIO`." A `<queries>` entry for
  `android.speech.RecognitionService` is required from API 30.
- **Android's own voice out:** `TextToSpeech` speaks through the engine the user installed (Google's
  speaks dozens of languages with neural voices on most phones). Nothing to download in iCode.
- **Open-source models on the phone: sherpa-onnx** (k2-fsa, Apache-2.0) — built in P6, removed on
  2026-11-04 (§5.8). One Android library runs
  speech recognition, voice activity detection and speech synthesis on ONNX Runtime, offline. It is
  published for Android through JitPack (`com.github.k2-fsa.sherpa-onnx:sherpa-onnx`, as its own
  demo app takes it), and its models are files on its GitHub releases, downloaded when the user
  chooses one. Read in its sources and release lists (2026-11-03):
  - **Version.** 2.0.0 (2026-10) removed espeak-ng and piper-phonemize: "Language is determined by
    the lexicon files you provide", so its voices speak only English and Chinese without an outside
    phonemizer. **v1.13.8** (2026-09-10) is the last with espeak-ng inside, and it was the one taken:
    with it one Kokoro model speaks eight languages and Piper voices speak dozens more.
  - **Speaking, Kokoro-82M v1.0** (Apache-2.0), `kokoro-int8-multi-lang-v1_0.tar.bz2`, 132 MB:
    English (US and UK), Mandarin, Spanish, French, Hindi, Italian and Brazilian Portuguese
    (`kokoro-lang`: "en, es, fr, hi, it, pt-br"), 54 voices. Among the most natural open voices.
  - **Speaking, Piper voices** (VITS, `vits-piper-<language>-<voice>-<quality>.tar.bz2`, about 20 to
    70 MB each): one voice per language for German, Russian, Arabic, Turkish, Dutch, Polish,
    Ukrainian, Vietnamese and some 30 more. Each voice carries its own dataset's licence.
  - **Listening, Whisper base** (OpenAI, MIT), `sherpa-onnx-whisper-base.tar.bz2`, 208 MB (its int8
    encoder and decoder, 160 MB, are what is kept): 99 languages, the language found by itself.
  - **Listening, SenseVoice** (FunAudioLLM, its own model licence),
    `sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09.tar.bz2`, 166 MB: Chinese, English,
    Japanese, Korean and Cantonese, faster and more accurate than Whisper base in those.
  - **The end of speech, Silero VAD** (MIT), `silero_vad.onnx`, 644 KB: when the user stops talking.
  - **Size.** The library's AAR carries four ABIs (47 MB for 2.0.1); iCode shipped `arm64-v8a` only.
- **The microphone in the background.** A foreground service that needs a while-in-use permission
  must be started "while your app has a visible activity", unless an exemption applies; from
  Android 14 the start itself throws otherwise. The exemptions are: a system component, a widget,
  a notification, another app's visible PendingIntent, a device owner, **the app providing the
  `VoiceInteractionService`**, and a privileged permission (developer.android.com, foreground
  service restrictions). Neither an accessibility service nor a tap on an overlay is among them.

### 3.5 Speed: where the time goes

- **The model's thinking dominates a step.** On AndroidDaily, general models take 7.9 s to 14.7 s
  per step; MobileExplorer finds on-device model inference the main delay, and screenshot capture
  much cheaper (as their papers report them).
- **Text beats pictures.** In MobileUse's ablation of the M3A agent on AndroidWorld, the
  accessibility tree alone took 150 s per task at 30.6 % success; tree and screenshot together took
  234 s at 25.4 %.
- **On the phone itself:**
  - acting on a node (`ACTION_CLICK`) needs no coordinates and no gesture animation;
  - animations off (`setAnimationScale(0)`, API 33) let the window events come sooner;
  - prefetching (API 33) reads a tree in fewer round trips to the app.
- **So the agent is fast by taking fewer, cheaper steps** (§5.4): read text, act on numbered
  elements, see the result without asking, go straight by intent, and send a picture only when
  text cannot tell.

### 3.6 Pictures in a turn (Koog 1.3.0, read in its sources)

| Client | A picture inside a tool's result | A picture beside the results, in the same message |
|---|---|---|
| OpenAI Chat Completions (OpenAI, compatible servers, OpenRouter) | Dropped, with a warning | Sent as a user message after the tool messages |
| OpenAI Responses | Sent as `input_image` in the output | — |
| Anthropic | Sent inside the `tool_result` | Sent as an image block after the results |
| Google | Sent as the function response's `parts` | Sent as `inlineData` |
| Ollama | — | Sent with the user message, before the tool messages |

Koog's `MessagePart.Tool.Result` holds parts, and iCode's own `sendResults` node builds the message.
So a screenshot goes where each client takes it (§4, L2).

### 3.7 Third-party work, judged on what it does

| Candidate | What it does well | What it lacks for iCode | Licence: what using its code requires |
|---|---|---|---|
| **Mobilerun Portal** (DroidRun's on-phone app) | A mature accessibility service: numbered elements, a depth guard (128), its own keyboard for typing, screenshots, gestures | A separate app driven from a computer (ADB, WebSocket, cloud); 58,700 lines with cloud, WebRTC and SMS; declares `isAccessibilityTool="true"` (§3.2) | AGPL-3.0: can be combined with GPL-3.0; adds the AGPL's network-use term to that code |
| **DroidRun** | The agent loop over the Portal, MIT | Python on a computer: not on the phone | MIT |
| **Blurr / Panda** | The closest to the owner's picture: an edge glow, a floating button, voice in and out, the assistant role, intents | Gemini only; `TYPE_APPLICATION_OVERLAY` windows (untrusted, need `SYSTEM_ALERT_WINDOW`); a proprietary wake word (Porcupine); accounts and a freemium wall | Personal-use licence: commercial use needs a licence from Panda AI. iCode is GPL-3.0 and free; taking its code needs Panda AI's permission |
| **Shizuku / Shizuku-API** (13.1.5, Maven Central) | Shell identity on the phone: real touches past accessibility's limits; turning iCode's own service on without the restricted-settings walk | The user starts it after each reboot. `Shizuku.newProcess` is private since API 13: commands run in a **UserService**, iCode's own class in a shell process | Apache-2.0 / MIT. **In the first cut** (§5.11) |
| **sherpa-onnx** (v1.13.8, JitPack; removed 2026-11-04) | Speech in and out on the phone, offline, in many languages (§3.4) | Native code (ONNX Runtime) in the APK; models of 130 to 210 MB to download; the owner chose Android's engines (§5.8) | Apache-2.0; each model its own (Kokoro Apache-2.0, Whisper MIT, Silero MIT, Piper voices per dataset, SenseVoice its own) |
| **AppFunctions** | Apps' own functions, no screen at all | Not callable by iCode (§3.1) | Platform |

**Decision.** Android's own `AccessibilityService` is the engine: native, on every supported version,
trusted overlays, any model. What the candidates do better is taken as design:
- the Portal's numbered elements and depth guard;
- Blurr's glow, floating button and assistant entry, drawn on trusted windows.

No candidate is dropped for its licence: if the owner wants a candidate's code itself — the
Portal's keyboard, Blurr's glow — that file is adopted under its terms (AGPL-3.0 as is; Blurr with
Panda AI's permission). The table above is the owner's to act on.

## 4. Limits by design, fixed first (in iCode)

Each is fixed where it is, not worked around beside it.

| | Limit | Fix |
|---|---|---|
| L1 | A chat must belong to a workspace | Kept: chats belong only to workspaces. The separate Phone chats were removed (Nov 2026); phone tools are offered to any chat while Phone control is on, and the bubble uses the chat iCode shows |
| L2 | A tool can answer only text | A phone tool's answer may carry a **screen**: its text, and a picture for models that see. The turn places the picture where each client takes it (§3.6) |
| L3 | Seeing what an action did costs another step | After a reply's calls run, the turn adds **one screen**: what the phone shows once it has settled. A reply that taps, types and submits sees the result in the same step |
| L4 | Questions wait on the chat page or a notification | The **bubble** shows the question and its answers, on any screen |
| L5 | 40 steps, then a hard stop | At 40 steps the turn **pauses** and asks "Keep going?" (Continue, Stop); Continue gives 40 more |
| L6 | The modes speak only of a workspace | **Apps are places too.** Reading never asks. Acting in an app asks under Ask first and Auto in workspace, with "Allow in *app* for this chat" — the same "outside" rule as a folder. Full access asks nothing |
| L7 | The assistant lives only in iCode | **The bubble** over every app: one icon, iCode's own (§5.7) |
| L8 | The assistant cannot reach phone storage: the runtime binds only workspaces and the system's trees; the explorer alone reads it | **Phone storage is in the runtime**, at its own path (`/storage/emulated/0`) with `/sdcard` naming it, as on Android. Every tool and command reaches it the way it reaches any folder outside the workspace: through the gate, "Allow in *folder* for this chat". Without All Files Access the tool says so, and what the user taps to give it |
| L9 | The assistant only writes | **It speaks:** its answers and questions are read aloud, in a natural voice, when the user asks for that (§5.8) |

## 5. Decisions

### 5.1 The phone service

- **`PhoneService : AccessibilityService`** (`data/phone`), declared with:
  - `canRetrieveWindowContent`, `canPerformGestures`, `canTakeScreenshot`;
  - `flagRetrieveInteractiveWindows`, `flagReportViewIds`, `flagIncludeNotImportantViews`;
  - `flagRequestAccessibilityButton`;
  - `isAccessibilityTool="false"`;
  - events `typeWindowStateChanged|typeWindowContentChanged|typeWindowsChanged`, notification
    timeout 100 ms.
- **It reads, acts and hosts the overlays.** It holds no chat and no model: the assistant calls it
  through a `PhoneControl` interface, as the tools call the runtime.
- **Its description** in Settings says, in plain words, that iCode's assistant reads and operates
  the screen for the user's own requests, and sends what it reads to the model provider the user
  chose.

### 5.2 What the model reads: the screen

- **Text first.** The windows on screen, as the model reads them: the app and its title, then one
  line per element that shows text or can be acted on, numbered in reading order. Each line: number,
  role, its text or label, state (checked, selected, disabled, focused, editable), and what it can
  do (tap, type, scroll). Hidden and off-screen nodes and pure layouts are left out. The depth guard
  stops at 128 levels.
- **Each screen has a number.** An element number is valid for that screen. Acting on an old one is
  refused, with the screen as it is now.
- **Never sent:** a password field's text (its role says "password"); iCode's own windows — the
  bubble, the glow and iCode's approvals are not on the agent's screen.
- **A picture when text cannot tell**, for models that see images:
  - when the model asks (`phone_look`);
  - when the screen has no readable elements — a game, a canvas, a web view without accessibility.

  It is JPEG at the phone's aspect, its longest side at most 1,280 px. Its taps use its own
  coordinates (`phone_tap` with x and y).

### 5.3 The tools

| Tool | Kind | Does |
|---|---|---|
| `phone_screen` | Read | The screen now (the first look of a turn) |
| `phone_look` | Read | The screen with its picture |
| `phone_open_app` | Change | An app by its name, through its launch intent |
| `phone_open` | Change | A link, or a Settings page by name (Wi-Fi, Bluetooth, Display, App info of *app*…) |
| `phone_tap` | Change | An element (click; long press with `hold`), or a point of the picture |
| `phone_type` | Change | Text into an element: in place of what it holds, or after it; `submit` presses the keyboard's action (`ACTION_IME_ENTER`). A field that refuses the text is typed as keys through Shizuku (ASCII) |
| `phone_scroll` | Change | An element, or the screen, up, down, left or right |
| `phone_key` | Change | Back, Home, Recents, Notifications, Quick settings |
| `phone_hand_over` | Read | Asks the user to do a step — a password, a payment, a CAPTCHA, a view the platform withholds — and waits for **Done** in the bubble |

- **Each tool is offered only while phone control is on** (§5.9). Workspace chats get them too: the
  assistant can build an app, install it with the Run tab and tap through it.
- **They run on node actions first**, gestures only where a node has no action or for a point of the
  picture.

### 5.4 Speed

1. **One screen per step** (L3): the calls of a reply run in order, and the screen is read once
   after the last. A reply can tap a field, type and submit in one step.
2. **Settled, not slept.** The screen is read when window events have been quiet for 300 ms, and at
   most 3 s after the action: never a fixed wait.
3. **Straight there.** Opening an app, a link or a Settings page goes by intent, never through the
   launcher's pages.
4. **Text first, pictures when needed** (§5.2).
5. **The phone's own speed-ups**, while the agent acts and undone when it stops:
   - animations off (`setAnimationScale(0)`, API 33);
   - prefetching and the node cache (API 33).
6. **The model's speed is the provider's.** The plan measures it rather than promises it (§7 P9):
   tap to first action, and seconds per step, against the step count of the same task.

### 5.5 Approvals and safety

- **Reading never asks.** Acting in an app asks under Ask first and Auto in workspace (L6):
  - Allow once;
  - Allow in *app* for this chat;
  - Deny.

  Full access asks nothing.
- **Refused always:**
  - acting on iCode's own windows: its approvals are the user's alone;
  - acting on the system's permission dialogs and Settings › Accessibility. The agent hands those
    to the user.
- **Hand-over:** passwords, payments, CAPTCHAs and withheld views (§3.2) are the user's.
  `phone_hand_over` says why, the glow dims, and the bubble shows **Done** and **Stop**.
- **Stop is always one tap away:** on the bubble, in the notification, and on the chat page.
- **What is read leaves the phone** for the provider the user chose. Turning phone control on says so
  first; nothing of a screen is logged (the rule for chats).

### 5.6 The edge glow

- **While a phone turn acts**, the screen's edges glow in Claude's blue and orange — one continuous
  light, blue at the top left warming to orange at the bottom right, faded towards the middle,
  breathing slowly. Waiting on the user, it dims; idle, it is gone. *(Redesigned 2026-11-04, after
  Claude: the glow was the user's accent alone; the pair it wears now is the same the bubble's states
  share — blue for what goes into the agent, orange for what comes out of it.)*
- **Drawn on four thin `TYPE_ACCESSIBILITY_OVERLAY` strips**, not touchable, so the middle of the
  screen is never covered.
- **Hidden for a capture below API 34;** from 34 the capture is taken beneath it (§3.3).
- **Reduced motion:** when the system's animations are off, it does not breathe.

### 5.7 The bubble

The owner's design (2026-11-03; **redesigned 2026-11-04, after Gemini**: the bubble anchored at the
bottom, expanding in place): three things on screen — the **bubble**, a **bar** it expands
into when text can be typed, and the **edge glow** — and everything else only when it is needed. No
header, no cards of actions, no dashboard.

- **One icon.** iCode does not ask for Android's accessibility button (`flagRequestAccessibilityButton`):
  Android assigns a service that asks for it to its own floating button when the service is turned on,
  a second icon beside the bubble that it draws itself and no app can animate or restyle. A phone
  that still lists iCode there from an earlier build (`accessibility_button_targets`, readable by
  apps) is told so on Phone Control, whose row opens iCode's accessibility page to turn it off: once
  the flag is gone, a tap on that button would turn the service off.
- **The bubble**, anchored at the bottom of the screen, centred unless dragged aside along it —
  Gemini's place, not Android's floating button: the window is centred on its own middle, so the bar
  it expands into grows from that middle and the bubble stays put under it, clear of the gesture
  area. Dragged anywhere, it drops back to the bottom; idle for three seconds it sinks half behind
  the bottom edge and fades; anything new brings it out. Hidden while iCode is on screen. Its six
  states (`PhoneOverlay.Look`), the agent's before the microphone's — muting never hides that the
  agent works, and it shows listening only while it listens:

  | State | Shows | A tap |
  |---|---|---|
  | Idle | The iCode mark | Expands it into the bar |
  | Listening | The voice bubble: the words heard so far after a waveform in the accent, with a microphone and a Stop | The microphone mutes; Stop throws the hearing away |
  | Muted | The microphone off, the bar back | Resumes listening |
  | Thinking | A ring in the accent turning round the mark | Opens the bar (Stop is there) |
  | Acting | A ring in the accent turning round a pointer; the glow on | Opens the bar (Stop is there) |
  | Speaking | A waveform in the accent | Stops the reading |

  A dot shows while a question waits. The bar's microphone carries the same states, so what the
  agent does never hides behind the keyboard.
- **The bar**, the bubble expanded into it where it sat — one floating pill holding the keyboard:
  the mark that collapses it back (or, muted, resumes listening), the field, the microphone, Send
  (Stop while the agent works, Stop reading while it speaks). What leads the pill sits as far in
  from its end as the last icon from the other. The field grows with what is typed up to four
  lines, its controls staying on its last line. At the pill's end a **tab** rises from its top, as
  the owner drew it on paper (2026-11-04): of the pill's own surface and edge line, a compact
  action tall (32 dp, its icons 16 dp), the pill's top edge curving up into it
  (`IcodeFloatingPill`'s `tab` and `menu`), each icon in a touch target's column so it stands over
  the control below. On it, past four lines, **Expand**, which makes the field an editor of twelve
  lines (Collapse brings it back), and **⋮**. ⋮ opens New chat and Open in iCode in the tab itself,
  named, the tab growing toward the start with its curve — the owner's choice over a dropdown, and
  the bar's window's need: a popup is a window of its own, and a touch on it would be a touch
  outside the bar, which closes it. One chosen, or ⋮ again, and the tab shrinks back. Above, in one
  card and only when there is something to say: what the agent is doing, the question with its answers, why the microphone heard nothing, or why the last turn
  failed, with Retry. Anchored at its bottom, all of it grows upward and the pill stays put. A touch
  outside, or on the mark, closes it; sending closes it, so it covers nothing the agent may tap.
- **Voice.** The bar's microphone hides the bar and shows the voice bubble: the words heard so far
  in it, after an animated waveform, with a microphone and a Stop beside them — the waveform as far
  in from one end as Stop's icon from the other. Once the user stops
  talking, what was heard is sent, answered aloud. Muted, the words wait in the bar to be edited or
  sent — and what the agent does runs on. Mute, Stop, Close and Cancel are never one control: muting
  stops only the microphone, Stop in the voice bubble throws the hearing away, Stop in the bar stops
  the agent, closing the bar stops nothing, and dragging onto ✕ puts everything away.
- **Put away** (the owner's word): dragging the bubble shows a ✕ at the top of the screen — away
  from where the bubble lives at the bottom; dropped there, the bubble goes and the glow goes with
  it, and the voice is silenced. While a turn acts on the phone, the target reads "Stop and hide",
  and dropping stops it. Phone control stays on: the bubble comes back from the notification's
  **Show bubble**, or Phone Control's Bubble switch.
- **One mark, one look:** drawn with iCode's design system, in `TYPE_ACCESSIBILITY_OVERLAY` windows.
- **The keyboard, proved first:** the bar's window takes focus so the keyboard can type into it.

### 5.8 Voice

- **Android's own engines, each way** — the owner's decision, 2026-11-04: iCode focuses on Android.
  - **Listening:** `SpeechRecognizer`, on the phone itself where available (API 31+), else the
    user's recognition service; the words appear as they are heard, and it ends when the user stops
    talking.
  - **Speaking:** `TextToSpeech`, with the engine and voice the user chose in Android's settings;
    Settings › Assistant › Voice opens them, and Android's speech recognition settings.
  - Open-source models on the phone (sherpa-onnx with Whisper, SenseVoice, Kokoro and Piper voices)
    were built in P6 and removed on 2026-11-04, with the library. What an earlier build downloaded,
    and its settings, are deleted when iCode starts.
- **Read as it is written.** A turn to be read aloud is read while the model writes it
  (`SpokenTurn`, `SpokenText.ready`): each sentence is said once it is whole, never from inside a
  code block, and the rest of a reply once it is recorded. One speech session per turn: Android's
  engine is made ready as the turn begins, while the model still thinks, and each sentence is queued
  (`QUEUE_ADD`) the moment it is whole, so the first is heard while the rest is written. A question
  the turn waits on is said in turn; a turn stopped or failed hushes it, and another chat shown is
  not read.
- **Short when spoken.** A turn read aloud adds one section to its instructions, everything else as
  before: one to three short sentences, the answer or outcome first; no Markdown, lists, tables,
  code or links unless asked; while it works, what it is about to do in a few words; code or long
  detail only when asked, saying first that it is on the screen.
- **Spoken replies:** *Off*, *In the bubble* (the default: a turn started by voice or from the bubble
  is answered aloud), or *Always*. What is spoken is the answer and the questions, without Markdown,
  code blocks or links; a new message, Stop, or a tap on the bubble silences it.
- The microphone in the bubble and on the chat page's composer listens; the user sends what was
  heard, or says nothing for a moment to send.
- **Microphone access** comes from the foreground service, started with type `microphone` while
  iCode is on screen — when the user turns phone control on (§3.4) — and asked again by the bubble
  before it listens. The microphone indicator shows only while listening.
- **Restarted while iCode is away** (after a reboot, or when Android stopped it), the service runs
  without the microphone type, as Android requires. The bubble's microphone then says "Open iCode
  once to use the microphone", and typing works as before.

### 5.9 Phone control: on, off, alive

- **Settings › Assistant › Phone control**, off by default. Turning it on:
  1. says what is read and where it goes;
  2. walks through Allow restricted settings when Android asks for it (§3.2);
  3. opens Settings › Accessibility for iCode's service;
  4. asks for the microphone (optional).
  With Shizuku ready (§5.11), steps 2 and 3 are one tap: iCode turns its own service on.
- **Shizuku, one page** (Settings › Assistant › Phone control › Shizuku): its state, read live —
  *not installed*, *installed, not running*, *running, iCode not allowed*, *ready* — the one step
  that state needs (install it, start it with Wireless debugging, allow iCode), and what it adds.
- **While it is on:**
  - the foreground service carries `specialUse|microphone` and keeps the agent alive;
  - the bubble shows whenever iCode is not on screen;
  - the notification has Stop and Turn off.
- **Off:** the service's overlays go, the agent's tools go, and nothing is read.

### 5.10 Where it lives in the code

| Part | Package |
|---|---|
| Screen and element models, the screen's text, the approval rules for apps | `domain/phone` |
| `PhoneService`, `PhoneControl`, the settle wait, intents and Settings pages | `data/phone` |
| Shizuku: its state, the UserService and its client | `data/phone/shizuku` |
| The phone tools | `data/assistant/tools/PhoneTools.kt` |
| Voice: Android's engines, what of a turn is read aloud and when | `domain/voice`, `data/voice` |
| The glow and the bubble | `ui/phone` |
| The pause at 40 steps, the screen in a step | `data/assistant` (L2–L5) |
| Phone storage in the runtime | `data/runtime` (`GuestPaths`, `RuntimeLayout`) (L8) |

### 5.11 Shizuku, in the first cut

- **What it adds**, whenever it is ready, with nothing to switch on:
  - iCode's accessibility service turned on, and restricted settings allowed, by one tap
    (`appops set … ACCESS_RESTRICTED_SETTINGS allow`, `settings put secure
    enabled_accessibility_services …`);
  - taps, swipes, typing and keys as real input (`input`), where accessibility is refused: a
    view the platform withholds (§3.2), or a gesture Android blocked;
  - a screenshot (`screencap`) when the accessibility one fails for its rate.
- **How:** `dev.rikka.shizuku:api` and `provider` 13.1.5. Commands run in a Shizuku **UserService**:
  iCode's own class, started by Shizuku in a shell process, answering one call — a command line,
  its exit code and its output — over a hand-written `Binder` (no AIDL), and the reserved destroy
  call (`16777115`).
- **Not a second engine:** the screen is still read through accessibility, which is fast and
  complete; Shizuku covers what accessibility is refused.

### 5.12 Optional, later

- **iCode as the default assistant** (`VoiceInteractionService`): the assist gesture opens the
  panel, and the microphone's foreground service may start from any screen (§3.4). It ships only
  once phones are shown to list iCode.

## 6. Not done, and why

- **AppFunctions:** the caller permission is a system role's (§3.1).
- **A wake word:** always listening drains the battery, and Blurr's (Porcupine) needs a vendor key.
  The bubble's microphone covers voice.
- **Open-source voice models on the phone:** built in P6 and removed on 2026-11-04 — the owner chose
  Android's own speech recognition and text-to-speech alone (§5.8).
- **Acting while the screen is locked:** Android's lock screen is not the agent's.
- **Claiming to be an accessibility tool:** untrue for iCode, and blocked by Play Protect (§3.2).
- **Android's accessibility button as the bubble:** Android draws it and gives the app only its tap;
  it cannot show listening, thinking, acting or speaking, which the owner's design asks of the one
  icon (§5.7).

## 7. Cycles

Each cycle ends as every cycle here does: `main` fetched and taken in, a commit, the branch pushed,
then `main`. CI on `main` builds the APK and runs every test (this container cannot reach Google's
Maven, §10).

| Cycle | What | Done when |
|---|---|---|
| **P1** Places and storage | L1: chats of a workspace or Phone; the page without a workspace, and its place switch; phone chats from `/root`. L8: phone storage in the runtime, through the gate | Tests: places, the store's keys, phone chats listed, phone storage's paths and the gate; UI type-check |
| **P2** Turns | L2, L3, L5: a screen in a step, placed per client; one screen per reply; the pause at 40 | Tests with a scripted provider: one screen after three calls; the picture where each client takes it; Continue after 40 |
| **P3** The phone service and Shizuku | `PhoneService`, the screen's text, actions, the settle wait, intents and Settings pages; Shizuku's state, its UserService and client | JVM tests of the screen's text and numbering from recorded trees; of Shizuku's states and commands |
| **P4** Tools and approvals | `PhoneTools`; L6 per-app approvals; the refusals; hand-over | Tests: each tool's arguments and summaries; what each mode asks; refusals |
| **P5** The bubble and the glow | Overlays on trusted windows; collapsed, expanded, put away; the accessibility button | UI type-check; the bubble's state rules tested |
| **P6** Voice | L9; both engines each way; the models' catalogue, download and removal; spoken replies | Tests: the catalogue, what is spoken of a reply, the download's checks |
| **P7** Setup | Settings › Assistant › Phone control and Voice; the Shizuku page; the walk through restricted settings; the service's description | UI type-check; the setup's steps tested per state |
| **P8** Gate | Docs as built (PRD, user stories, layout, CHANGELOG); the guards; CI green | Push the branch and `main` |

On a phone (the owner's, §8): read, tap, type, submit, scroll, back; the bubble dragged, expanded,
put away; voice both ways; Shizuku from not installed to ready; the timings of §5.4 on three tasks.

## 8. On the phone (the owner's walk-through)

1. "Open Settings and turn on Wi-Fi": by intent, one toggle, done in two steps.
2. "Reply 'on my way' to Alex in WhatsApp": asks before acting in WhatsApp; types and sends.
3. A login page: the agent hands the password to the user.
4. From the home screen, the bubble: speak a task, watch the glow, hear the answer, answer a
   question in the bubble, Stop.
5. "Find the PDF I downloaded yesterday and summarize it": phone storage, asked once.
6. Shizuku: from not installed to ready on its page; the service turned on by one tap.
7. Put the bubble away: the glow goes with it; Show bubble brings it back.

## 9. Sources

- Android source (frameworks/base, via its GitHub mirror):
  - `AccessibilityService.java`: the 333 ms interval, `takeScreenshotOfWindow`, `setAnimationScale`
    and overlay attachment;
  - `AccessibilityServiceInfo.java`;
  - `AndroidManifest.xml`: `EXECUTE_APP_FUNCTIONS`;
  - `PackageInstaller.java`, `InstallPackageHelper.java`: restricted settings;
  - `AccessibilityNodeInfo.java`.
- developer.android.com:
  - `AccessibilityService` reference (API levels);
  - `AccessibilityNodeInfo.AccessibilityAction`;
  - `SpeechRecognizer`;
  - `RoleManager`;
  - Android 12 behaviour changes (untrusted touches);
  - Tapjacking guide (`accessibilityDataSensitive`);
  - background starts of foreground services;
  - AppFunctions;
  - "Enhancing Android security" (2025-12-11);
  - "Agentic and Flexible" (Android Dev Summit, 2026-10-28).
- Koog 1.3.0 sources: `AbstractOpenAILLMClient`, `OpenAILLMClient`, `AnthropicLLMClient`,
  `GoogleLLMClient`, `OllamaConverters`.
- Repositories:
  - droidrun/droidrun (MIT);
  - droidrun/droidrun-portal (AGPL-3.0);
  - ayush0chaudhary/blurr (personal-use licence);
  - RikkaApps/Shizuku (Apache-2.0) and Shizuku-API (MIT): `Shizuku.java` (`newProcess` private),
    the UserService demo and its destroy code;
  - k2-fsa/sherpa-onnx (Apache-2.0): `jitpack.yml`, the Kotlin API (`Tts.kt`), the 2.0.0
    changelog, the Kokoro config at v1.13.8; the model archives' contents, listed from their
    release downloads.
- Papers, as search results report them:
  - MobileUse (the M3A ablation);
  - AndroidDaily (seconds per step);
  - MobileExplorer (on-device inference).
- Android 17 Advanced Protection: Android Authority (Canary 2602) and The Hacker News (Beta 2), as
  search results report them. Google has no page on it yet.
- Not reachable from this session, so read only through search results: arxiv.org,
  androidauthority.com, android-developers.googleblog.com.

## 10. Record (as built)

The checks that run in this container: Google's Maven redirects to a host it cannot reach, so no
Android Gradle build runs here. Each cycle compiles what it changed with Kotlin 2.3.10 against
Android 16's `android.jar` (its framework classes and resources, from a public mirror) and Maven
Central's libraries, runs the unit tests it touches, and leaves the Android build to CI on `main`.

| Cycle | As built | Checked here |
|---|---|---|
| **P1** (phone chats removed since, see L1) | `AssistantPlace` (a chat's place: a workspace's location, or `phone`); `ShowAssistantPlace` and the Chats sheet's place rows; the home screen's Assistant button; phone chats from `/root` (`AssistantWorkspace.phone`), with no Run tab tools and their own instructions; their processes grouped under the phone. Phone storage bound at its own path in every launch, `/sdcard` linked to it (`GuestPaths.shared`, `RuntimeLayout`), refused with what the user can do when All Files Access is off | `AssistantCoordinatorTest` (phone chats on the home screen; kept shown in a workspace), `AssistantToolsTest` (storage paths and the refusal; the phone place), `GuestPathsTest`, `AssistantRulesTest`; the app compiled; the guards |
| **P2** | `AssistantStepLimitException`; `AssistantPause` and `ContinueAssistant`, the pause card, the notification's question; `AssistantQuestions` (approvals and pauses); a turn going on from the chat as it is (`input = null`); `AssistantTurnRequest.picture`, sent with a step's results where each client takes one (`KoogTurn.PicturePlace`), only the latest screen kept in the prompt | `KoogAssistantEngineTest` (going on with no message; the screen beside the results, and only the latest; where each client takes one), `AssistantCoordinatorTest` (Continue and Stop at the limit); the whole suite: 1,220 tests, the 58 that fail here fail for this container (assets read from another folder, Compose's colour spaces without `androidx.collection`, Android stubs, root) |
| **P3** | `PhoneService` (thin: events, connection, the overlays' host), `PhoneTree` (windows → `PhoneNode`, prefetching from API 33), `PhoneReading` (numbered elements, merged rows, passwords never read, overlays and keyboards not read, depth 128, 300 elements), `AndroidPhoneControl` (node actions, then a gesture, then Shizuku's `input`; the settle wait: a change after the act, then 300 ms quiet, 3 s at most), `PhoneCapture` (JPEG ≤ 1280 px, overlays hidden below API 34), `PhoneLauncher` (apps, links, Settings pages by intent), `ShizukuShell` and `ShellUserService` (state, permission, one-tap service, `input`, `screencap` shrunk in the shell process), `PhoneCoordinator` and `PhoneIntent`, `ICodeApplication`. `flagInputMethodEditor` left out: typing needs none | `PhoneReadingTest`, `PhoneRulesTest` (apps, Settings pages, links, refusals, strokes, pictures); the app compiled against Shizuku 13.1.5's classes |
| **P4** | `PhoneTools` (nine tools), `PhoneSummaries`, `AssistantStep` (the reply's calls, the picture, the hand-over), `AssistantGate` (an act asks about its app: "Act in WhatsApp?", "Allow in WhatsApp"), the instructions' phone section, the shared approval words (`AssistantPermissions.question`, `allowIn`, `allowOnce`) | `PhoneToolsTest`, `AssistantGateTest`; 533 domain and assistant tests pass |
| **P5** | `PhoneGlow` (four strips, breathing, dimmed while waiting), `PhoneBubble` (mark, ring, dot; panel with the question, field, Send/Stop, New chat, Open in iCode; the ✕ target), `PhoneOverlays` (trusted windows, drag and snap, put away, the accessibility button, hidden for captures), `PhoneOverlay` rules, `appearancePalette` shared with the app | `PhoneRulesTest` (what shows when); the UI compiled; on a phone: the keyboard in the panel, the glow over secure apps |
| **P7** | `PhoneSetup` (the steps per state: restricted settings from API 33, then the service; one tap with Shizuku ready; the status line), Phone Control (the steps opening App info and Accessibility, Turn On With Shizuku, the bubble, Turn Off), Shizuku (state, its one step: Get, Open, Allow iCode; what it adds), routes `PhoneControl` and `ShizukuSetup`; both read again on resume. Settings › Assistant as pages: Providers, Voice, Phone Control | `PhoneSetupTest`, `RouteTest`; the guards |
| **P6** | `VoiceCoordinator` (one per process, its state in the snapshot, `VoiceIntent`): Android's recognizer (on the phone where it can, API 31) and text-to-speech; on this phone, sherpa-onnx v1.13.8 — Whisper base or SenseVoice cut by Silero at each pause (`ModelListener`, recording at once while the models load), Kokoro or a Piper voice played sentence by sentence (`ModelSpeaker`). `VoiceCatalog`: 36 models pinned by size and SHA-256, read from the files; only kept files taken from each archive (`VoiceModels`). `SpokenText`: the answer and the questions without Markdown, a new turn hushes, a stopped or failed one says nothing; a turn is answered aloud by its `aloud` flag, set by the bubble and the microphone. `PhoneForegroundService` (`specialUse|microphone`): the microphone while iCode is away, started again with it each time iCode is on screen; Stop, Show bubble, Turn off. The microphone (`MicButton`, `HeardLine`, `OnHeard`) shared by the composer and the bubble: words as they are heard, sent once the user stops talking | `SpokenTextTest`, `VoiceCatalogTest`, `VoiceModelsTest` (kept files only, nothing outside the folder), `SherpaConfigsTest` (every file given is kept), `AssistantCoordinatorTest` (aloud); the app compiled against sherpa-onnx's classes; its arm64 libraries checked 16 KB aligned |
| **Redesign** (owner's request) | The new pages as Settings › Terminal's: `SettingRow`s with an icon, a one-line subtitle and a state (`AccessState`) or control, `IcodeSelect` for choices, no paragraphs. Voice: microphone; listening and speaking, each Android's or a model here, its size said before Download, then Cancel or Remove; Play Sample; spoken replies; what else is on the phone. Shared settings helpers in `SystemPages.kt` | The UI guard (no Toasts, no screens reaching `data/`), the backend guard |
| **The bubble's states** (owner's design) | One icon: Android's accessibility button no longer asked for, and a leftover one named on Phone Control with the way to remove it (`PhoneAccess.systemButton`, `PhoneIntent.OpenAccessibility` opening iCode's own accessibility page). `PhoneOverlay.Look`'s six states, the agent's before the microphone's; `BubblePlacement` (docked at an edge, tucked and faded when idle, dragged and snapped); `PhoneBubble` (the states, the waveform, the words heard), `PhoneBubbleBar` (the floating pill, and its one card of what there is to say); `PhoneOverlays` (Closed, Typing, Voice: tap to mute, resume, silence; sent once the user stops talking; muted, the words wait) | `PhoneRulesTest` (the states and which comes first); the guards |
| **Android's voice, read as written** (owner's decision and design, 2026-11-04) | The open-source voice removed end to end: sherpa-onnx and its rule, `VoiceCatalog`, `VoiceModels`, `SherpaConfigs`, `ModelListener`, `ModelSpeaker` and their tests; `VoiceCoordinator.forgetModels` deletes what an earlier build downloaded and its settings. `SpokenTurn` turns the assistant's changes into `VoiceCue`s (Begin, Say, End, Hush): sentences said as they are written (`SpokenText.ready`, never inside code), the rest once recorded, the questions in turn; `VoiceSpeaker` queues each into one session per turn, the engine readied at Begin. `AssistantInstructions`: a turn read aloud asks for a short spoken answer, nothing else changed. Voice settings: Microphone, Read Aloud (answers, Play Sample), Android (Voice and Language, Speech Recognition). The bar (after the owner's bottom-anchored redesign): a field growing to four lines; the owner's paper design, a tab rising from the pill's end (`IcodeFloatingPill`'s `tab` and `menu`, its outline `TabbedPill` following the tab's width in the frame it is measured) with, past four lines, Expand to a twelve-line editor, and ⋮, which opens New chat and Open in iCode in the tab itself; the notes above it; `IcodeFloatingPill` grows taller with its corners kept; the voice bubble (`PhoneVoiceBubble`) and the bar with their ends inset alike | `SpokenTextTest` (7), `SpokenTurnTest` (4: a streamed turn, a turn not read, stopped and failed, questions and another chat); the domain, assistant and navigation suites; the app compiled; the guards |
