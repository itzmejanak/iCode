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
| **Shizuku / Shizuku-API** | Shell identity on the phone: input and capture past accessibility's limits | The user starts it after each reboot | Apache-2.0 / MIT |
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
| L1 | A chat must belong to a workspace | A chat's **place** is a workspace or **Phone**. Phone chats are kept under their own key, work from the runtime's home (`/root`), and are listed on the assistant page when no workspace is open |
| L2 | A tool can answer only text | A phone tool's answer may carry a **screen**: its text, and a picture for models that see. The turn places the picture where each client takes it (§3.6) |
| L3 | Seeing what an action did costs another step | After a reply's calls run, the turn adds **one screen**: what the phone shows once it has settled. A reply that taps, types and submits sees the result in the same step |
| L4 | Questions wait on the chat page or a notification | The **bubble** shows the question and its answers, on any screen |
| L5 | 40 steps, then a hard stop | At 40 steps the turn **pauses** and asks "Keep going?" (Continue, Stop); Continue gives 40 more |
| L6 | The modes speak only of a workspace | **Apps are places too.** Reading never asks. Acting in an app asks under Ask first and Auto in workspace, with "Allow in *app* for this chat" — the same "outside" rule as a folder. Full access asks nothing |
| L7 | The assistant lives only in iCode | **The bubble** over every app, and the accessibility button as a second way in |

## 5. Decisions

### 5.1 The phone service

- **`PhoneService : AccessibilityService`** (`data/phone`), declared with:
  - `canRetrieveWindowContent`, `canPerformGestures`, `canTakeScreenshot`;
  - `flagRetrieveInteractiveWindows`, `flagReportViewIds`, `flagIncludeNotImportantViews`;
  - `flagInputMethodEditor`, used from API 33;
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
| `phone_type` | Change | Text into an element: set whole, or at the cursor (API 33); `submit` presses the keyboard's action (`ACTION_IME_ENTER`) |
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

- **While a phone turn acts**, the screen's edges glow in the user's accent, faded towards the
  middle, breathing slowly. Waiting on the user, it dims; idle, it is gone.
- **Drawn on four thin `TYPE_ACCESSIBILITY_OVERLAY` strips**, not touchable, so the middle of the
  screen is never covered.
- **Hidden for a capture below API 34;** from 34 the capture is taken beneath it (§3.3).
- **Reduced motion:** when the system's animations are off, it does not breathe.

### 5.7 The bubble

- **Collapsed:** the iCode mark on a round button at the screen's edge.
  - A ring round it while the agent works, and a dot when a question waits.
  - Dragged anywhere; it snaps to the nearer edge.
  - Hidden while iCode is on screen.
- **Expanded:** a small panel over the app:
  - what the agent is doing now ("Opening Settings…");
  - the question waiting, with its answers;
  - a text field, the microphone, Send, and Stop;
  - **Open in iCode**, for the whole chat.
- **One mark, one look:** it is drawn with iCode's design system, in a `TYPE_ACCESSIBILITY_OVERLAY`
  window.
- **The keyboard, proved first** (P5): the expanded panel takes focus so the keyboard can type
  into it. If a phone's keyboard will not attach to an accessibility overlay, the field opens a small
  floating iCode activity instead, the same panel.
- **The accessibility button** (Android's own, in the navigation bar or floating) opens the panel
  too.

### 5.8 Voice

- The microphone in the panel listens through `SpeechRecognizer`: on the phone itself where
  available (API 31+), else the user's recognition service. Words appear in the field as they are
  heard; the user sends them, or says nothing for a moment to send.
- **Microphone access** comes from the foreground service, started with type `microphone` while
  iCode is on screen — when the user turns phone control on (§3.4). The microphone indicator shows
  only while listening.
- **Restarted while iCode is away** (after a reboot, or when Android stopped it), the service runs
  without the microphone type, as Android requires. The bubble's microphone then says "Open iCode
  once to use the microphone", and typing works as before.

### 5.9 Phone control: on, off, alive

- **Settings › Assistant › Phone control**, off by default. Turning it on:
  1. says what is read and where it goes;
  2. walks through Allow restricted settings when Android asks for it (§3.2);
  3. opens Settings › Accessibility for iCode's service;
  4. asks for the microphone (optional).
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
| The phone tools | `data/assistant/tools/PhoneTools.kt` |
| The glow and the bubble | `ui/phone` |
| Phone chats, the pause at 40 steps, the screen in a step | `data/assistant` (L1–L5) |

### 5.11 Optional, later

- **Shizuku privileged mode** (Apache-2.0 / MIT): taps and typing as ordinary input, past the
  sensitive-view block. It also turns on iCode's accessibility service by `settings`, without the
  restricted-settings walk. It is offered only when Shizuku is running.
- **iCode as the default assistant** (`VoiceInteractionService`): the assist gesture opens the
  panel, and the microphone's foreground service may start from any screen (§3.4). It ships only
  once phones are shown to list iCode.

## 6. Not done, and why

- **AppFunctions:** the caller permission is a system role's (§3.1).
- **A wake word:** always listening drains the battery and needs a third-party model (Blurr uses
  Porcupine, which needs a vendor key). The bubble's microphone covers voice.
- **Acting while the screen is locked:** Android's lock screen is not the agent's.
- **Claiming to be an accessibility tool:** untrue for iCode, and blocked by Play Protect (§3.2).

## 7. Cycles

| Cycle | What | Done when |
|---|---|---|
| **P1** Places | L1: chats of a workspace or Phone; the page without a workspace; phone chats from `/root` | Tests: places, the store's keys, phone chats listed; UI type-check |
| **P2** Turns | L2, L3, L5: a screen in a step, placed per client; one screen per reply; the pause at 40 | Tests with a scripted provider: one screen after three calls; the picture where each client takes it; Continue after 40 |
| **P3** The phone service | `PhoneService`, the screen's text, actions, the settle wait, intents and Settings pages | JVM tests of the screen's text and numbering from recorded trees; on a phone: read, tap, type, submit, scroll, back |
| **P4** Tools and approvals | `PhoneTools`; L6 per-app approvals; the refusals; hand-over | Tests: each tool's arguments and summaries; what each mode asks; refusals |
| **P5** The bubble and the glow | Overlays on trusted windows; collapsed and expanded; the keyboard proved; the accessibility button | Renders; on a phone: drag, expand, type, answer a question, Stop, the glow on and dimmed |
| **P6** Voice | `SpeechRecognizer` in the panel; the service's microphone type | On a phone: speak from another app; the indicator only while listening |
| **P7** Setup | Settings › Assistant › Phone control, the walk through restricted settings, the service's description | On a phone installed from a downloaded APK: from off to working |
| **P8** Optional | Shizuku mode; the default-assistant entry | On a phone with Shizuku; on two vendors' phones for the assistant list |
| **P9** Gate | Docs as built (PRD, user stories, layout); the timings of §5.4 measured on three tasks | Push the branch and `main` |

## 8. On the phone (the owner's walk-through)

1. "Open Settings and turn on Wi-Fi": by intent, one toggle, done in two steps.
2. "Reply 'on my way' to Alex in WhatsApp": asks before acting in WhatsApp; types and sends.
3. A login page: the agent hands the password to the user.
4. From the home screen, the bubble: speak a task, watch the glow, answer a question in the
   bubble, Stop.

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
  - RikkaApps/Shizuku (Apache-2.0) and Shizuku-API (MIT).
- Papers, as search results report them:
  - MobileUse (the M3A ablation);
  - AndroidDaily (seconds per step);
  - MobileExplorer (on-device inference).
- Android 17 Advanced Protection: Android Authority (Canary 2602) and The Hacker News (Beta 2), as
  search results report them. Google has no page on it yet.
- Not reachable from this session, so read only through search results: arxiv.org,
  androidauthority.com, android-developers.googleblog.com.
