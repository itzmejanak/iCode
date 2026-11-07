# Speech recognition with any provider

Status: **built** (2026-11-07), the owner's request: S0–S6 and D4. The owner took every
recommendation in §9; Azure (S7) waits, as D2 says. The phone checks SP1–SP7 (§7) are still to be run
on a phone, with keys the owner gives.

> "Design and develop a provider-agnostic speech recognition architecture … multiple speech
> recognition providers … through a common abstraction layer … select their preferred speech
> recognition provider dynamically without modifying the application's core business logic."
>
> "ICode assistant can do CRUD with same backend so user and assistant can set it up."
>
> "For eg one provider can be: https://www.assemblyai.com"

What it asks for, and where this plan answers it:

| The request | Here |
|---|---|
| A common interface for every provider | `SpeechEngine` (§4.1) |
| One adapter per provider (Adapter pattern) | one class per protocol (§4.3) |
| Choosing a provider at run time (Factory pattern) | `SpeechEngines.of` (§4.2) |
| The same input and output for every provider | `SpeechRequest`, `SpeechEvent` and `Heard` (§4.1) |
| A new provider without touching the rest | a kind, an adapter and one factory branch (§4.2) |
| Configuration and dependency injection | `SpeechProviders`, the store and constructor injection (§4.4, §4.5) |
| The same errors from every provider | `SpeechFailure` (§4.6) |
| Provider keys kept safe | sealed by the Keystore, never read back (§4.5) |
| The user and the assistant set it up through the same backend | `SpeechIntent` and three tools over one owner (§1, §6) |

## 1. The rule: one owner, two callers

This is the rule of [agent-control-plan.md](./agent-control-plan.md) §1, the "environment" the
owner refers to: every capability has **one owner** in the data layer, with typed operations.
- The user's screens reach the owner through intents.
- The agent reaches it through tools.
- Both are thin adapters over the same operations, so what the agent changes shows on the page at once.

Speech recognition gets one more row in that table:

| Area | Owner | The user | The agent | Gap |
|---|---|---|---|---|
| **Speech providers** | `SpeechProviders` (new) | Settings › Assistant › Voice › Speech Recognition (`SpeechIntent`) | `list_speech_providers`, `save_speech_provider`, `remove_speech_provider` | everything: Android's recognizer is the only one, wired in directly |

`VoiceCoordinator` remains the one that listens. It no longer knows any engine: it asks
`SpeechProviders` which one is in use and `SpeechEngines` to make it. The bubble and the chat's
composer stay as they are: they read `VoiceState.heard`, whichever provider filled it.

## 2. Facts this plan rests on (checked 2026-11-06)

This session's network policy blocks the providers' documentation sites (assemblyai.com,
platform.openai.com, learn.microsoft.com, developers.deepgram.com, docs.cloud.google.com). Every fact
below comes from the providers' own sources on GitHub, read at their latest commit, or from probing a
live API. A point marked *to read in its cycle* is one that only the blocked sites state.

### 2.1 Android (in iCode today)

`VoiceListener` uses `SpeechRecognizer`:
- On the phone itself where Android offers it (API 31+), else the user's recognition service.
- Partial results while the user speaks; the last words, final, once they stop.
- It owns the microphone itself. Android's recognition service may itself send the audio to a
  server. The privacy page already says so.

### 2.2 AssemblyAI

Source: [AssemblyAI/assemblyai-python-sdk](https://github.com/AssemblyAI/assemblyai-python-sdk),
`assemblyai/streaming/v3`.

- **Streaming (v3):** `wss://streaming.assemblyai.com/v3/ws?…`, with the key in `Authorization` as it
  is (no `Bearer`).
  - **Query:** `sample_rate` (required for PCM), `encoding` (`pcm_s16le`, `pcm_mulaw`, `opus`,
    `ogg_opus`, `aac`), `speech_model`, `language_codes`, `language_detection`, `format_turns`,
    `keyterms_prompt`, `end_of_turn_confidence_threshold`, `min_turn_silence`, `max_turn_silence`.
  - **`speech_model` values:** `universal-streaming-english`, `universal-streaming-multilingual`,
    `u3-rt-pro`, `whisper-rt`, `universal-3-5-pro`, `universal-3-6`, `universal-3-6-pro`.
- **Messages:**
  - Server: `Begin` (`id`, `expires_at`); `Turn` (`transcript`, `end_of_turn`,
    `turn_is_formatted`, `words`, `language_code`); `Termination`; `SpeechStarted`; `Error`
    (`error_code`, `error`); `Warning`.
  - Client: binary audio, `{"type":"Terminate"}`, `ForceEndpoint` (ends the turn now), `KeepAlive`,
    `UpdateConfiguration`.
- **Close codes, among them:**
  - Key and account: 4001 Not Authorized, 4002 Insufficient Funds, 4003 paid-only feature.
  - Limits: 3009 Too many concurrent sessions, 4102 the account's streams exceeded.
  - Audio: 3007 audio chunk duration violation, 4029 audio sent too fast.
  - Session: 3008 and 4008 session expired, 4031 idle too long.
  - Server: 3005.

  The bounds of 3007's chunk duration are *to read in its cycle*.
- **Batch:**
  - Jobs: `https://api.assemblyai.com/v2/upload` and `/v2/transcript`, polled.
  - Single request: `https://sync.assemblyai.com/v1/transcribe`, up to 120 s and 40 MB, one round trip.
  - Dictation: `https://dictation.assemblyai.com/v1/transcribe/live`, WAV or raw 16-bit PCM uploaded
    as it is spoken, up to 120 s, with an optional LLM rewrite.

  Temporary tokens (`/v3/token`) are for browsers. iCode holds the key itself, so it does not need them.

### 2.3 OpenAI

Sources: [openai/openai-openapi](https://github.com/openai/openai-openapi) (`openapi.yaml`) and
[openai/openai-python](https://github.com/openai/openai-python) (`resources/realtime`).

- **`POST /v1/audio/transcriptions`**, multipart:
  - `file`: flac, mp3, mp4, mpeg, mpga, m4a, ogg, wav or webm.
  - `model`: `gpt-transcribe`, `gpt-4o-transcribe`, `gpt-4o-mini-transcribe`,
    `gpt-4o-mini-transcribe-2025-12-15`, `whisper-1` or `gpt-4o-transcribe-diarize`. **Every one
    but `gpt-transcribe` has an announced shutdown on 2027-02-26.**
  - `language` (ISO-639-1); `languages` and `keywords` for `gpt-transcribe`; `prompt`.
  - `stream=true`: server-sent `transcript.text.delta` (`delta`) and then `transcript.text.done`.
    Not for `whisper-1`.
- **Realtime transcription:**
  - The URL: the API's base URL with `wss` and `/realtime`, and `intent=transcription`, so
    `wss://api.openai.com/v1/realtime?intent=transcription`.
  - The session's `type` is `transcription`; its input audio is `audio/pcm` at **24 kHz only** (or
    `audio/pcmu`, `audio/pcma`).
  - Its transcription models: `gpt-transcribe`, `gpt-live-transcribe`, `gpt-realtime-whisper`, and
    the five with the 2027 shutdown.
  - It sends `conversation.item.input_audio_transcription.delta` and `.completed`.

### 2.4 Deepgram

Source: [deepgram/deepgram-api-specs](https://github.com/deepgram/deepgram-api-specs) (`asyncapi.yml`,
`openapi.yml`).
- **Live:** `wss://api.deepgram.com/v1/listen`, with `Authorization: Token <key>` (or `Bearer <JWT>`).
  - **Query:** `model` (`nova-3` first, then `nova-2`…), `encoding` (`linear16`…), `sample_rate`,
    `channels`, `interim_results`, `endpointing` (default 10), `utterance_end_ms`, `smart_format`,
    `punctuate`, `language` (default `en`), `keyterm`.
  - **Messages:** the server sends `Results` (`is_final`, `speech_final`), `Metadata`, `UtteranceEnd`,
    `SpeechStarted` and `Error`. The client sends audio, `Finalize`, `CloseStream`, `KeepAlive` and
    `Configure`.
- **Batch:** `POST /v1/listen`.

### 2.5 Google Cloud Speech-to-Text

Sources: the discovery documents (`speech.googleapis.com/$discovery/rest?version=v1` and `v2`,
revision 20261001), and one probe of each API with a made-up key.
- **v1 takes an API key:** `POST https://speech.googleapis.com/v1/speech:recognize?key=…` answered
  "API key not valid", so it reads keys.
- **v2 does not:** its `recognize` answered "API keys are not supported by this API. Expected OAuth2
  access token…". It needs a service account, which iCode will not hold.
- **v1 `recognize` is synchronous:** "receive results after all audio has been sent and processed".
  - `config`: `encoding` (`LINEAR16`…), `sampleRateHertz` (8000–48000, "16000 is optimal"),
    `languageCode` (BCP-47, required), `model` (`latest_short`: "Best for short form content like
    commands or single shot directed speech"), `enableAutomaticPunctuation`.
  - `audio.content`: base64.
- **No streaming over REST:** its REST recognition methods are `recognize` and
  `longrunningrecognize`; streaming is not among them. Its length limits are *to read in its cycle*.

### 2.6 Azure AI Speech

Source: [Azure-Samples/cognitive-services-speech-sdk](https://github.com/Azure-Samples/cognitive-services-speech-sdk),
the Android samples.
- **The SDK:** `com.microsoft.cognitiveservices.speech:client-sdk:1.53.0`, a native library.
- **Setup:** `SpeechConfig.fromSubscription(key, region)`, and
  `AudioConfig.fromStreamInput(PullAudioInputStreamCallback)` with
  `AudioStreamFormat.getWaveFormatPCM(rate, 16, 1)`.
- **Results:** `recognizing` events carry partial words and `recognized` events final ones;
  `recognizeOnceAsync` and `startContinuousRecognitionAsync` start it.
- **Not read here:** the REST endpoints, and what the SDK adds to the APK. Both are *to read in its
  cycle*.

### 2.7 iCode before this plan

| Part | Where |
|---|---|
| Listening, the microphone's checks, the background microphone through phone control's foreground service | `data/voice/VoiceCoordinator.kt` |
| Android's recognizer | `data/voice/VoiceListener.kt` |
| What the screens read: `Heard`, `VoiceState`, `VoiceFrom` | `domain/voice/Voice.kt` |
| Listen, FinishListening, CancelListening | `domain/VoiceIntents.kt` |
| Microphone, Read Aloud, Android's settings | `ui/screens/settings/VoiceSettingsScreen.kt` |
| Keys sealed by a Keystore key of iCode's own, never read back | `AssistantProviderStore`, `McpServerStore` (`data/io/KeystoreCipher.kt`) |
| Network failures in plain words | `data/assistant/NetworkWords.kt` |
| Typed operations and three agent tools over one owner, changes asked about every time | `McpServers`, `McpServerTools` |
| A WebSocket client: the WebSockets plugin is in `ktor-client-core` 3.5.2, which iCode already ships with OkHttp | — |
| A WebSocket server for tests: `ktor-server-websockets`, a test dependency to add at the version of the `ktor-server-cio` the tests already use (3.5.2) | — |

## 3. Decisions

| # | Decision | Why |
|---|---|---|
| S1 | **Listening gets providers; speaking stays Android's.** Android remains the default and cannot be removed. This reopens the owner's "Android's engines only" (2026-11-04) for listening alone, and only for what the user adds | The request is about recognition. Reading aloud is not asked for |
| S2 | **One interface, `SpeechEngine`**, with one input (`SpeechRequest`) and one output (`SpeechEvent`: words so far, final words, or a failure). The coordinator turns events into the `Heard` the screens already read | The core (coordinator, bubble, composer) never meets a provider |
| S3 | **Adapters by protocol, not by brand:** Android, AssemblyAI streaming, OpenAI realtime, Deepgram live, Azure's SDK, and one **upload** adapter for providers that answer after the user stops (OpenAI-compatible servers and Google v1) | Two providers on one protocol share one adapter: no duplication |
| S4 | **One factory**, `SpeechEngines.of(provider)`: one exhaustive `when` over the kind | A kind without an adapter does not compile |
| S5 | **One microphone for every engine but Android's:** `SpeechMicrophone` (AudioRecord, 16-bit mono PCM at the rate the adapter takes), opened after the coordinator's existing checks, and never written to disk | One recorder and one place for the background-microphone rule |
| S6 | **Standard failures,** `SpeechFailure`, said in one place in plain words naming the provider. Each adapter maps its own codes into it in one table | The same failure reads the same from every provider |
| S7 | **Keys:** sealed with `KeystoreCipher` under an alias of their own, as the AI providers' and MCP servers' are. Never logged, never shown (only "set"), never read back by the agent. A key goes with the kind it was made for | The existing rules, unchanged |
| S8 | **No silent fallback.** A provider that fails says why, and nothing is sent to another one | The user chose where their voice goes |
| S9 | **The agent sets it up through the same owner** (list, save, remove), and every change asks first, in every mode (`asksAlways`) | A provider outlasts the chat and decides where the user's voice goes; same as MCP (agent-control-plan A9) |
| S10 | **https and wss only;** plain traffic only to this phone (127.0.0.1), for a local OpenAI-compatible server | The existing network rule |
| S11 | **Nothing heard is logged:** audio, words and a socket's close reason stay out of the logs. A failure logs its kind and code | The existing voice rule |

## 4. Architecture

```
 Settings pages ── SpeechIntent ──┐
                                  ├── SpeechProviders ── SpeechProviderStore (keys sealed)
 Agent tools (list/save/remove) ──┘        │ in use
                                           ▼
 VoiceCoordinator ── SpeechEngines.of(provider) ──► SpeechEngine
   Listen / Finish / Cancel                          ├ AndroidSpeech       SpeechRecognizer (its own microphone)
   │                                                 ├ AssemblyAiSpeech    wss streaming v3
   │          SpeechMicrophone (AudioRecord, PCM16) ─┤ OpenAiSpeech        wss realtime, intent=transcription
   │                                                 ├ DeepgramSpeech      wss /v1/listen
   │                                                 ├ UploadSpeech        OpenAI-compatible /audio/transcriptions,
   │                                                 │                     Google v1 speech:recognize
   │                                                 └ AzureSpeech         Speech SDK
   ▼
 VoiceState.heard: Heard ──► the bubble, the chat's composer (unchanged)
```

### 4.1 The interface and the standard models

```kotlin
// data/voice/speech/SpeechEngine.kt
internal interface SpeechEngine {
    /**
     * Hears once: the words as they come, then the final ones — or a failure — and the flow ends.
     * [done] completes when the user is done talking; cancelling the collection stops the hearing.
     */
    fun hear(request: SpeechRequest, done: Deferred<Unit>): Flow<SpeechEvent>
}

internal data class SpeechRequest(
    /** A BCP-47 tag (en-US); null: the language the phone speaks. Each adapter takes what its API takes. */
    val language: String?,
    /** The model to ask for; null where the provider has none to choose. */
    val model: String? = null,
)

internal sealed interface SpeechEvent {
    data class Words(val text: String, val final: Boolean) : SpeechEvent
    data class Failed(val failure: SpeechFailure, val detail: String? = null) : SpeechEvent
}
```

In `domain/voice/Speech.kt`:
- **`SpeechProviderKind`**: label, needs a key, what it asks for (an address or a region), whether
  words come while the user speaks (`live`), the models it suggests (the user may type another), and
  the host the audio goes to, which the page says.
- **`SpeechProvider`**: id, kind, name, address or region, model, language, whether a key is set.
- **`SpeechFailure`**.
- **`VoiceState`** gains the providers, the one in use, and each one's last failure.

`Heard` stays as it is: it is already the one output the screens share. `SpeechHearings` runs one
hearing at a time for the coordinator, whichever engine hears it: it ends a hearing at its final words
or its failure, and stops the engine; an engine that ends without final words makes what it heard final.

| Kind | Live | Asks | Models suggested (first is the default) |
|---|---|---|---|
| Android | yes | — | — |
| AssemblyAI | yes | key | `universal-3-5-pro` (what AssemblyAI's own SDK streams with in its examples), then the rest of the §2.2 list |
| OpenAI | yes | key, or the assistant's (D4) | `gpt-live-transcribe` (in the realtime sessions' list, not in `/audio/transcriptions`'), `gpt-transcribe`. `gpt-realtime-whisper` is left out: its sessions take no turn detection, and the hearing ends by it |
| OpenAI-compatible | no | address, key if the server wants one | typed: the server's own |
| Deepgram | yes | key | `nova-3`, `nova-2` |
| Google Cloud | no | key | `latest_short`, `latest_long` |
| Azure | yes | key, region | — |

**Language:**
- iCode keeps a BCP-47 tag: the provider's own, else the phone's.
- OpenAI takes its ISO-639-1 part (`en`), Google and Azure the whole tag, AssemblyAI
  `language_codes`, Deepgram `language`.
- "Automatic" sends none where the API allows it. Google requires one, so it gets the phone's.

### 4.2 The factory

`SpeechEngines.of(provider, key)` is one `when (provider.kind)`; the microphone and the one HTTP client (OkHttp, with WebSockets and per-request timeouts for uploads) are the factory's own.

To add a provider:
1. A `SpeechProviderKind` entry, with its fields and capabilities.
2. An adapter, or an existing one when the protocol is the same.
3. One branch in the factory.

The coordinator, the pages, the tools and the store do not change: they read the kind's
capabilities, not its name.

### 4.3 The adapters

| Adapter | One hearing |
|---|---|
| `AndroidSpeech` | Today's `VoiceListener`, moved behind the interface, behaviour unchanged |
| `AssemblyAiSpeech` | Opens `/v3/ws` with `sample_rate=16000`, `encoding=pcm_s16le`, the model, `format_turns=true` and the language. It sends PCM chunks and shows each `Turn` as words. The end of turn (formatted) is final; then it sends `Terminate`. Done sends `ForceEndpoint`. A close code or `Error` becomes a failure |
| `OpenAiSpeech` | Opens the realtime URL with `Bearer` and sets a `transcription` session: `audio/pcm` at 24 kHz, the model, the language, the server's turn detection. It sends audio and shows the deltas as words; `.completed` is final. Done commits the buffer. The exact client events are taken from the spec in S4 |
| `DeepgramSpeech` | Opens `/v1/listen` with `Token`, `model`, `encoding=linear16`, `sample_rate=16000`, `channels=1`, `interim_results=true`, `smart_format=true` and the language. The words are the final segments so far plus the current interim one; `speech_final` is final. Done sends `Finalize`, then `CloseStream` |
| `UploadSpeech` | Records until the user stops (§4.4), then sends it once. OpenAI-compatible (`OpenAiTranscription`): multipart `/audio/transcriptions`, a WAV file, `response_format=json`; the server's `text` is final. Google v1 (`GoogleRecognition`): `speech:recognize`, `LINEAR16` at 16 kHz in base64, `latest_short` by default, punctuation on, the key in `X-Goog-Api-Key` (a probe showed Google reads it there) and never in the address; Google answers a key it cannot read with 400 `API_KEY_INVALID`, read as a refused key |
| `AzureSpeech` | Not built (D2). The SDK, fed from `SpeechMicrophone` through a pull stream. `recognizing` is words; `recognized` is final |

`SpeechSocket` runs every streaming hearing: it opens the socket (the adapter may speak first, as
OpenAI's `session.update`), sends the microphone's audio as it comes, hands each frame to the adapter,
and ends at the first final words or failure, or when a deadline the adapter set passes (AssemblyAI's
unformatted end of turn, a done with no answer yet). A handshake refused is read by its HTTP status; a
socket the provider closed takes no more audio. The adapters keep only their protocol.

### 4.4 The microphone

`SpeechMicrophone` opens `AudioRecord` (source `VOICE_RECOGNITION`, mono, 16-bit) at the rate the
adapter takes: 16 kHz, or 24 kHz for OpenAI. Where the phone refuses that rate, it records at 48 kHz
and converts to it.
- **Frames:** 100 ms each (3 200 bytes at 16 kHz). AssemblyAI's bounds on a chunk's duration (its
  close code 3007) are on its documentation site, which this session could not reach: 100 ms is what
  iCode sends, and a 3007 would say so in the shared words.
- **Where the audio lives:** frames go to the adapter as they come. An upload adapter keeps them in
  memory and nowhere else, at most **120 s** (§9 D6), and they are gone once the hearing ends.
- **When an upload hearing ends:** live providers say when the user stopped (AssemblyAI's end of
  turn, OpenAI's turn detection, Deepgram's `speech_final`). An upload hearing ends:
  - after 1.5 s of quiet that follows speech;
  - after 8 s with no speech at all ("Nothing was heard");
  - or at the user's tap, as today.

  Speech is a frame louder than an RMS of 500 (of 32 767) and than three times the room: the quietest
  frame heard so far, so a hearing that starts with speech finds the room at its first pause
  (`SpeechQuiet`). The level, the factor and both times are named constants, tuned in the phone checks.
- **Running in the background:** the same rule as today. Away from iCode, the microphone works only
  through phone control's foreground service. The coordinator checks this before any engine starts.

### 4.5 Configuration and keys

- **`SpeechProviderStore`** keeps the providers as JSON in the preferences `icode.speech`. Each key is
  sealed by `KeystoreCipher` (alias `icode.speech`), as in `AssistantProviderStore`:
  - `providers()` never carries a key, only whether one is set.
  - A key this phone can no longer decrypt is removed, and the provider's next refusal asks for it again.
  - Changing a provider's kind drops its key.
- **`SpeechProviders`** is the owner:
  - **Operations:** `save`, `remove`, `use`, `list`, and `connection(id)`, the key decrypted only for
    the engine.
  - **Callers:** `SpeechIntent` and the tools, one operation at a time.
  - **On failure:** a failed operation is the user's notice and the model's words.
  - **Removing the provider in use** goes back to Android.
- **Wiring:** dependencies are passed in through constructors, as everywhere in iCode.
  - The store takes `encrypt` and `decrypt` functions.
  - The factory takes the HTTP client and the microphone.
  - JVM tests swap every one of them for fakes.

### 4.6 Errors

`SpeechFailure` is one of the following, each said once, naming the provider (`SpeechRules.words`):

| Failure | Said |
|---|---|
| `NoKey` | "Enter AssemblyAI's key in Settings › Assistant › Voice" |
| `NotAllowed` | "iCode may not use the microphone" |
| `Unavailable` | "This phone has no speech recognition" (Android's) |
| `KeyRefused` | "AssemblyAI refused the key: check it in Settings › Assistant › Voice" |
| `NoCredit` | "AssemblyAI says the account has no credit left" |
| `Busy` | "AssemblyAI is busy: try again" |
| `Unreachable` | the shared `networkWords` |
| `Language` | "AssemblyAI cannot hear this language" |
| `TooLong` | "That was longer than AssemblyAI takes at once" |
| `Server` | "AssemblyAI did not answer: try again" |
| `Microphone` | "The microphone could not be read" |
| `NothingHeard` | "Nothing was heard" |
| `Failed` | "AssemblyAI failed: " and the provider's own words, or "…: try again" |

Android's is named "Speech recognition", as it always was: "Speech recognition is busy: try again".

- **What maps to what:** each adapter has one table from its codes to the list above:
  - HTTP 401 and 403, AssemblyAI 4001: `KeyRefused`.
  - HTTP 402, AssemblyAI 4002: `NoCredit`.
  - HTTP 429, AssemblyAI 3009 and 4102: `Busy`.
  - 5xx, AssemblyAI 3005: `Server`.
  - Android's errors: as `VoiceListener` maps them today.
- **The provider's own words:** the provider's message is added when there is one; a key is never
  part of it.
- **Where the user sees it:** the failure is the hearing's `problem`, so the bubble and the composer
  show it where they show problems today.

## 5. The pages

- **The Voice page** gets a **Listening** section above Read Aloud: one row, "Speech Recognition",
  with the provider in use ("Android, on this phone"). It opens:
- **Speech Recognition**, built like the Providers page:
  - The providers, the one in use marked, each with its host ("Audio goes to
    streaming.assemblyai.com").
  - **Add Provider** opens the kind sheet (as `ProviderKindSheet`), then the form: name, key
    (masked, held in `remember`), address or region, model, language. Under the form are **Test**
    and **Use for Listening**:
    - **Test** listens once with that provider and shows the words, or the failure in its words.
    - **Use for Listening** makes it the one used.
  - Android's own page keeps the link to Android's speech recognition settings.
- **Privacy:** PRIVACY.md's Voice line will say that what you say goes to the provider you chose, or
  to your phone's recognition service.

## 6. The agent's tools

| Tool | Kind | What it does |
|---|---|---|
| `list_speech_providers()` | Read | Each provider's id, name, kind, model, language, whether a key is set (never the key), its last failure, and which one listens |
| `save_speech_provider(id?, kind, name, key?, key_from?, address?, model?, language?, use?)` | Change, asks always | Adds a provider, or changes the one with that id; `use: true` makes it the one that listens. The question names a key "given", never shows it |
| `remove_speech_provider(id)` | Change, asks always | Removes it; Android cannot be. Removing the one in use goes back to Android |

- **Where the tools live:** `AssistantToolName` gains the three names, and they join the agent's own
  tools beside MCP's.
- **Keys given in chat:** the agent may save a key the user wrote in the chat, as with MCP values.
  The key is then in that chat's history, so the page remains the place the tool's description
  recommends.
- **No Test tool:** a test needs the user's voice, so the agent cannot run one. The list's last
  failure tells it how the provider did.

## 7. Cycles

| # | What | Tests |
|---|---|---|
| S0 | The domain models, `SpeechEngine`, `SpeechEngines`, `AndroidSpeech` (`VoiceListener` behind the interface), and the coordinator using them. Nothing changes for the user | the coordinator with a fake engine: words, final, failure, cancel, a later tap left alone |
| S1 | `SpeechProviderStore`, `SpeechProviders`, `SpeechIntent`, the Speech Recognition pages with Android | the store: keys sealed, never in `providers()`, dropped with the kind, removed when undecryptable; the owner: removing the one in use; the guards |
| S2 | `SpeechMicrophone` and `AssemblyAiSpeech`, the owner's example. First read 3007's chunk bounds and the model page | a WebSocket fake (`ktor-server-websockets`): turns to words, the end of turn final, `ForceEndpoint` on done, every close code mapped |
| S3 | The three tools | as `McpServerToolsTest`: listed without keys, asks always, refusals in the page's words, the page's state after each |
| S4 | `OpenAiSpeech` (realtime) and `UploadSpeech` for OpenAI-compatible servers, with the end of speech | a fake realtime server; a fake `/audio/transcriptions`, streamed and not; the silence rules on recorded levels |
| S5 | `DeepgramSpeech` | a fake `/v1/listen`: interim and final segments joined, `speech_final`, `Finalize` |
| S6 | Google v1 through `UploadSpeech` | a fake `speech:recognize`: the body, and the results joined |
| S7 | `AzureSpeech`, if D2 keeps it: its APK size measured first | the event mapping over the SDK's types |
| S8 | Docs as built (this plan, phone-agent-plan §5.8, agent-control-plan §1, PRIVACY.md, user story US-ASSIST-016, the changelog), guards, CI | — |

Each cycle compiles, passes the tests and the guards, and goes to `main` before the next. Built:
S0 (9b8b456), S1–S2 (7747f0b), S3 (329077a), S4–S6 (5c67d51), D4 (429977c), S8; S7 waits (D2).

**Phone checks**, with keys the owner provides. They are kept only in the scratchpad, never committed:

| # | Do | See |
|---|---|---|
| SP1 | Speak with Android (the default) | as today, nothing changed |
| SP2 | Add AssemblyAI, Test, Use for Listening; speak in the bubble | the words as they come; sent when you stop |
| SP3 | A wrong key | "AssemblyAI refused the key…", and nothing sent elsewhere |
| SP4 | Airplane mode | the network words, naming the provider |
| SP5 | "Add Deepgram with this key and use it" in a chat | it asks; the page shows it in use; the list says "key set" |
| SP6 | A local OpenAI-compatible server at 127.0.0.1 | words once you stop; nothing from the internet |
| SP7 | The bubble while iCode is away | the microphone through phone control's service, as today |

## 8. Not now

- Speaking with other providers' voices (OpenAI, AssemblyAI and others have text-to-speech).
  `SpeechEngine`'s shape would serve a `VoiceEngine` the same way, but it is not asked for.
- Google v2 and Chirp: they need a service account, not a key.
- Words from the workspace (file and symbol names) sent as key terms (`keyterms_prompt`, `keyterm`,
  `keywords`).
- A provider per place (one for the bubble, another for the chat).

## 9. Owner decisions (taken 2026-11-06: all as recommended)

| # | Decision | Recommended |
|---|---|---|
| D1 | Listening may use providers the user adds; speaking stays Android's; Android stays the default (S1) | Yes |
| D2 | The first build covers Android, AssemblyAI, OpenAI, OpenAI-compatible, Deepgram and Google v1. Azure waits until its SDK's size in the APK is measured (S7) | Yes |
| D3 | No silent fallback to another provider (S8) | Yes |
| D4 | An OpenAI speech provider may use the key of the assistant's OpenAI provider, kept as a link to it, never a copy | Yes |
| D5 | The agent may save a key given in the chat, never reading it back (§6) | Yes, as for MCP |
| D6 | At most 120 s per hearing, for every provider | Yes: AssemblyAI's sync and dictation take 120 s; a spoken message is shorter |
