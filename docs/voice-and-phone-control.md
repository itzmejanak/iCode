# Voice and phone control

This document describes iCode's current voice and Android phone-control behavior. The [privacy policy](../PRIVACY.md) and [security policy](../SECURITY.md) are the policy sources for data handling and vulnerability reporting.

## Status labels

This page uses the four canonical labels defined in the documentation index: [Done, Partially Done, Not Started, and Needs Verification](README.md#status-labels). A **Done** provider cell below is limited to repository implementation and automated coverage; the separate acceptance column records the required real-provider and device evidence.

All real cloud-provider checks, real-device checks, and phone-control safety checks described below are **Needs Verification** unless a missing implementation keeps the broader capability **Partially Done** or **Not Started**.

## Voice capabilities

Listening and speaking are separate capabilities. One provider is selected for speech recognition and another can be selected for text-to-speech (TTS). Both selections default to Android. The provider list, encrypted key store, configuration owner, and assistant management tools are shared, but recognition and TTS use different engine interfaces.

| Provider | Listening implementation | Speaking implementation | Real provider/device status | Acceptance note |
|---|---|---|---|---|
| Android | **Done** | **Done** | **Needs Verification** | Check supported Android versions, installed recognizers, TTS engines, languages, and OEMs. |
| AssemblyAI | **Done** | **Done** | **Needs Verification** | Check a real account and device. |
| OpenAI | **Done** | **Done** | **Needs Verification** | Check a real account and device. |
| OpenAI-compatible server | **Done** | **Done** | **Needs Verification** | Check each chosen compatible server. |
| Deepgram | **Done** | **Done** | **Needs Verification** | Check a real account and device. |
| Google Cloud | **Done** | **Done** | **Needs Verification** | Check a real account and device. |
| Azure AI Speech | **Not Started** | **Done** | **Needs Verification** | Listening has no adapter; check speaking with a real account and device. |
| ElevenLabs | **Not Started** | **Done** | **Needs Verification** | Listening has no adapter; check speaking with a real account and device. |

Azure and ElevenLabs cannot currently be selected for listening. Their implemented capability is speaking only.

Provider failures do not silently fall back to another provider. iCode reports the selected provider's failure and leaves the other selection unchanged.

## Speech recognition

**Status: Partially Done**

Recognition adapters exist for Android, AssemblyAI, OpenAI, OpenAI-compatible servers, Deepgram, and Google Cloud. Azure and ElevenLabs listening remain unimplemented, and all real-provider and device evidence remains outstanding.

`SpeechEngine` gives every recognizer the same request and event flow. `SpeechEngines.of` selects the adapter from the provider kind:

- Android uses `SpeechRecognizer`, including on-device recognition where Android makes it available.
- AssemblyAI, OpenAI, and Deepgram use streaming connections.
- OpenAI-compatible and Google Cloud recognition upload the completed audio.
- Azure and ElevenLabs listening are rejected because no listening adapter exists.

One process-scoped `VoiceCoordinator` owns listening for the chat composer and bubble. `SpeechHearings` permits one active hearing and stops the previous hearing before beginning another. Android's recognizer owns its own microphone path. Other providers share `SpeechMicrophone`, which opens Android `AudioRecord` only while its audio flow is collected.

Upload recognition can buffer raw PCM in memory for up to 120 seconds before sending it. Streaming adapters send frames as they arrive. iCode does not intentionally write raw microphone audio to disk.

## Text-to-speech

**Status: Needs Verification**

`VoiceEngine` is the speaking interface. `SpeechEngines.voiceOf` selects Android TTS or a provider adapter. Cloud adapters request PCM or WAV audio and share the playback path. Sentences can be sent while the model is still writing, so speaking can begin before the whole answer is complete.

`VoiceCoordinator` owns one speech session at a time. Starting a listening session hushes current speech. Stopping, replacing, or failing a turn also stops its speech path.

While speech is playing, `VoiceFocus` requests transient audio focus with ducking and releases it afterward. The intended result is that other audio lowers while iCode speaks and returns afterward. Actual focus, ducking, interruption, and restoration behavior is **Needs Verification** on real devices and media apps.

## Microphone, audio focus, and background ownership

**Status: Needs Verification**

The microphone has two separate ownership concepts:

1. A recognizer or `SpeechMicrophone` opens the actual audio input only during a hearing.
2. `PhoneForegroundService` can hold Android's microphone foreground-service eligibility while iCode is away from the screen.

The foreground service does not continuously record audio. It publishes whether the background microphone type was successfully acquired. `VoiceCoordinator` checks the `RECORD_AUDIO` permission and requires either a visible iCode screen or that foreground-service state before listening.

Android restricts starting microphone foreground work from the background. iCode requests the microphone service while the app is visible and can request it again from the bubble. If Android recreates the service while iCode is away, it may run without microphone eligibility; the user is then told to open iCode once.

Real microphone contention, permission revocation, privacy indicators, background-start restrictions, provider switching, audio focus, and OEM behavior are all **Needs Verification**.

## Retention and data flow

**Status: Needs Verification**

The local media-retention behavior is implemented. Real-provider retention and end-to-end no-audio-persistence evidence remain outstanding.

The important distinction is between audio and chat text:

- iCode does not intentionally persist raw microphone PCM. Streaming frames are passed to the provider; upload recognition holds PCM in memory only until the hearing ends or reaches its limit.
- iCode does not intentionally persist synthesized speech audio. It is streamed or held for playback and then released.
- Partial recognized words can appear in the UI while listening. Cancelling a hearing drops its unfinished recognition state.
- Recognized text that the user sends from the composer, or that the bubble sends after a completed hearing, becomes an ordinary assistant message. It is retained in the app-private chat store like typed text and is included in later model context until retention or compaction removes or summarizes it.
- The listening provider receives microphone audio according to that provider's protocol and terms. Android recognition may use an on-device recognizer or the phone's configured recognition service.
- A non-Android speaking provider receives the answer text that it must synthesize. That provider's terms apply.
- Model-facing screen text is stored as assistant tool-result text and follows the chat's retention rules. A screenshot image is passed into the current model step but is not copied into the chat's attachment store by the phone-tool path.

Local chat storage is app-private but not content-encrypted. Speech-provider keys are Keystore-encrypted. See the [privacy policy](../PRIVACY.md) for the policy-level description of these transfers.

## Android phone control

### Accessibility service

**Status: Needs Verification**

Phone control is off until the user enables it. Its base is a non-exported Android `AccessibilityService` that declares window-content, gesture, and screenshot capabilities. iCode declares that it is not an accessibility tool.

`PhoneService` owns the live Android connection and hosts the phone overlays. `PhoneTree` converts Android windows and nodes into platform-independent screen data. `AndroidPhoneControl` performs reads and actions, preferring accessibility node actions, then accessibility gestures, then the optional Shizuku input fallback.

The service can improve access and automation, but Android, other apps, OEM policy, restricted settings, secure windows, and sensitive accessibility views can still refuse or omit data and actions.

### What screen text is read

**Status: Partially Done**

The model-facing text view:

- includes app and system windows;
- numbers readable or actionable elements in reading order;
- reports roles, labels, selected or checked state, editing state, and available actions;
- omits hidden and off-screen nodes;
- omits pure layout nodes unless their text belongs to an actionable parent;
- omits iCode's accessibility overlays;
- reports that the keyboard is open without reading keyboard keys;
- replaces a password field's value with a `password` state;
- limits traversal to 128 levels, 300 elements, and 100 characters per label.

These rules do not identify every kind of sensitive information. Ordinary visible text can include one-time codes, account information, health data, private messages, payment details, or notification content. Android can withhold sensitive accessibility nodes, but iCode has no general semantic redactor for all sensitive text.

Element numbers are valid only for the screen snapshot that produced them. iCode refreshes the node before acting and refuses a missing or stale numbered element.

### Allowed actions

**Status: Partially Done**

The assistant has a finite set of phone tools:

- read the screen as text;
- look at the screen with a screenshot;
- open an installed app;
- open supported web, telephone, SMS, email, map, store, or Settings links;
- tap or long-press a numbered element or screenshot coordinate;
- replace or append text in a numbered field and optionally submit it;
- scroll a numbered element or the screen;
- press Back, Home, Recents, Notifications, or Quick Settings; and
- hand a step to the user and wait for Done.

There is no model-facing arbitrary Shizuku shell tool. App launching and supported links use Android intents. Field, tap, and scroll actions prefer accessibility APIs before coordinate input.

The assistant instructions reserve passwords, payments, CAPTCHAs, permission dialogs, and withheld views for user hand-over. This is an instruction and UI path, not a complete enforced classifier. Numbered-element actions have package and surface refusals, but coordinate taps do not apply all of those element checks. Shizuku can also be tried after an accessibility action or gesture fails. Those gaps make direct-coordinate, sensitive-view, and fallback safety **Needs Verification**.

## Shizuku

**Status: Partially Done**

Shizuku is optional. When installed, running, and allowed by the user, iCode binds a non-daemon Shizuku UserService with shell identity. It can:

- help enable iCode's own accessibility service, including the restricted-settings step where supported;
- inject a restricted set of internal `input` commands for taps, swipes, text, and keys when accessibility fails; and
- take a `screencap` screenshot when the accessibility screenshot path fails.

Accessibility remains the primary screen-reading and action engine. Shizuku is a fallback, not a separate model-facing command system.

The Shizuku binder, setup flow, shell permissions, reboot behavior, restricted-settings behavior, input fallback, and screenshot fallback are **Needs Verification** on real devices. The automatic fallback after an accessibility refusal also needs an explicit safety rule because shell input can reach views that accessibility withholds. Completing that rule is tracked in [Phone-control safety and capture completion](roadmap.md#phone-control-safety-and-capture-completion).

## Bubble, glow, and system overlays

**Status: Needs Verification**

The floating bubble and edge glow use `TYPE_ACCESSIBILITY_OVERLAY` windows owned by the accessibility service. They do not use Android notification bubbles or `TYPE_APPLICATION_OVERLAY`, and iCode does not request the general “draw over other apps” permission for them.

The bubble can show idle, listening, muted, thinking, acting, and speaking states. It can open a text bar, start or stop listening, send a message, silence speech, stop active assistant work, open the assistant, and be dragged to an edge or dismissal target. Putting it away removes the glow; the foreground-service notification can show it again while phone control remains enabled.

The bubble sends to the chat iCode shows; while that chat works, the message waits in its queue, as one sent from the composer does. With several chats at work, it follows one: a chat waiting for the user first, then the one acting on the phone, then the chat shown, then the last to begin. Its step, question, Stop, and Open in iCode are that chat's, and dragging the bubble away stops it. One chat at a time acts on the phone: its turn has the screen from its first phone tool until it ends, and a phone tool another chat calls meanwhile is refused, telling the model to try again later. A reply read aloud keeps being read while another chat starts; only a message sent aloud takes the voice.

The phone-control notification offers Show bubble when needed and Turn off. Each chat at work has its own notification with its step, its Stop, and its question; tapping it opens that chat. These are separate notifications.

Trusted-window behavior, touch pass-through, keyboard focus, rotation and insets, drag placement, process reconnection, and OEM overlay policy are **Needs Verification**.

## Screenshots

**Status: Partially Done**

`phone_look` requests a screenshot when the chosen model accepts images. `phone_screen` adds one only when the accessibility tree has no readable elements. Screenshots are converted to JPEG and reduced so the longest side is at most 1,280 pixels. Screenshot coordinates are mapped back to display coordinates for taps.

On Android versions below 14, iCode hides its bubble and glow for one frame before the accessibility screenshot and restores them afterward. On Android 14 and later, the current implementation still uses full-display `takeScreenshot`; it does not yet use the per-window capture API intended to guarantee capture beneath accessibility overlays. Completing or deliberately retiring that requirement is tracked in [Phone-control safety and capture completion](roadmap.md#phone-control-safety-and-capture-completion).

If the accessibility screenshot fails, `AndroidPhoneControl` can try Shizuku `screencap`. Accessibility failures are currently reduced to no picture; the app does not preserve a distinct secure-window, rate-limit, or platform error for the model. Typed failures are required by [Phone-control safety and capture completion](roadmap.md#phone-control-safety-and-capture-completion).

Password redaction applies to the accessibility text tree, not screenshot pixels. A screenshot can contain visible passwords or other sensitive information. It is not semantically redacted before being sent to the selected AI provider. Secure-window behavior, overlay exclusion, Shizuku fallback, stale-coordinate handling, and sensitive-pixel safety all **Needs Verification**.

## Permissions and approvals

**Status: Needs Verification**

Android permission boundaries remain user-controlled:

- the user grants `RECORD_AUDIO`;
- the user enables iCode's accessibility service;
- the user grants Shizuku access separately;
- Android may require Allow restricted settings for an APK installed from a file; and
- phone storage access follows Android's storage permission path.

Phone reads are read tools and do not ask per read after phone control is enabled. An action is assigned to the app place it affects. Under **Ask first** and **Auto in workspace**, acting in an unopened app place asks the user. “Allow in app” remembers that app for the current chat. Under **Full access**, ordinary phone actions run without per-call approval.

Do not assume the assistant asks before every phone action. Approval mode and per-chat grants can intentionally authorize broader access. OS permission screens are still Android-controlled, and iCode's model instructions direct the assistant to hand permission dialogs to the user.

## Foreground service and process lifetime

**Status: Needs Verification**

While phone control is on, `PhoneForegroundService` runs as ongoing foreground work and raises the process's importance. On supported Android versions it uses the special-use service type, adding the microphone type only when Android permits it. The service starts foreground work immediately to meet Android's startup deadline.

The service returns `START_NOT_STICKY`. Android may stop the service or kill the process, and iCode does not claim automatic restart or guaranteed survival. A recreated service can return without microphone eligibility while iCode is away. Reboot, force-stop, notification denial, process death, service recreation, and restoration of active work are **Needs Verification**.

## Implementation paths

The primary implementation is in:

- `app/src/main/java/dev/icode/mobile/domain/voice/` — provider capabilities, voice state, spoken-turn rules, and shared failure words
- `app/src/main/java/dev/icode/mobile/data/voice/` — process-scoped voice coordination
- `app/src/main/java/dev/icode/mobile/data/voice/speech/` — recognition engines, TTS engines, microphone, player, audio focus, provider store, and provider owner
- `app/src/main/java/dev/icode/mobile/domain/phone/` — screen models, text formatting, place and refusal rules, screenshot coordinate rules, setup state, and overlay state
- `app/src/main/java/dev/icode/mobile/data/phone/` — accessibility service, Android control, screenshot capture, launcher, foreground service, and coordinator
- `app/src/main/java/dev/icode/mobile/data/phone/shizuku/` — Shizuku client and shell UserService
- `app/src/main/java/dev/icode/mobile/data/assistant/tools/PhoneTools.kt` — model-facing phone-tool allowlist and app-place integration
- `app/src/main/java/dev/icode/mobile/ui/phone/` — bubble, panel, edge glow, and accessibility-overlay windows

## Automated coverage and verification gaps

**Status: Partially Done**

Current JVM coverage includes:

- speech provider storage, selection, key handling, and capability splits;
- fake WebSocket and HTTP protocol behavior for recognition providers;
- fake HTTP and socket behavior for TTS, including Azure SSML/WAV and ElevenLabs streaming and listings;
- sentence ordering, playback helpers, WAV parsing, silence, and session ownership;
- screen-tree filtering, numbering, password handling, bounds, and labels;
- phone-tool routing, settled-screen results, image coordinate scaling, app-place approvals, and hand-over;
- phone setup decisions and overlay state rules.

The main test locations are:

- `app/src/test/java/dev/icode/mobile/data/voice/`
- `app/src/test/java/dev/icode/mobile/data/voice/speech/`
- `app/src/test/java/dev/icode/mobile/domain/phone/`
- `app/src/test/java/dev/icode/mobile/data/assistant/tools/PhoneToolsTest.kt`
- `app/src/test/java/dev/icode/mobile/data/assistant/AssistantGateTest.kt`

Real cloud providers, Android speech and playback services, accessibility behavior, sensitive and secure windows, screenshots and overlay exclusion, Shizuku, background microphone use, bubble windows, OEM policy, and process pressure remain outside automated proof. Their canonical requirements and evidence live only in [Voice providers and devices](roadmap.md#voice-providers-and-devices), [Phone-control safety and capture completion](roadmap.md#phone-control-safety-and-capture-completion), and [Phone-control real-device safety](roadmap.md#phone-control-real-device-safety).

## Security and privacy boundaries

The [privacy policy](../PRIVACY.md) explains what audio, recognized text, screen text, screenshots, and TTS text can leave the device. The [security policy](../SECURITY.md) explains supported versions, reporting, and scope.

Keep these boundaries in mind:

- Enabling phone control gives iCode's accessibility service broad visibility and action capability on the unlocked phone.
- Screen text can go to the selected chat provider during phone work and can remain in retained tool-result text. Screenshots can contain more than the text tree and are not semantically redacted.
- Raw microphone audio goes to the selected listening provider unless Android performs recognition on device. TTS answer text goes to the selected speaking provider.
- Submitted recognized text becomes retained assistant chat text. It is not treated as transient raw audio.
- Full access and remembered app grants can remove ordinary action prompts.
- Shizuku grants shell-level input and screenshot capabilities beyond ordinary accessibility behavior.
- Android and app restrictions remain security boundaries; current fallback behavior does not prove that every sensitive surface is protected.
- Use phone control only while the device is unlocked and under the user's supervision. Locked-screen operation is not supported.
