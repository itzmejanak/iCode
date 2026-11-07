# Reading aloud with any provider

Status: **plan** (2026-11-07), the owner's request. Nothing is built yet; the decisions in §9 wait
for the owner.

> "Now same like that let's plan for TTS provider agnostic … whether we can use same backend of stt or
> same providers … so ui looks consistent for voice page and make things clean and best and 0 gaps
> and 0 limitations … 100% accurate."

**The answer:** yes. Speaking uses the same backend as listening ([speech-plan.md](./speech-plan.md)),
built as listening is:
- **One list of providers.** A provider is an account (its kind, its key, its address), entered once.
  Each kind says whether it hears, speaks, or both.
- **Two choices.** One provider listens and one speaks, chosen separately. Android's is the default
  for both.
- **The same owner** (`SpeechProviders`), the same store and sealed keys, the same failure words.
- **The same three agent tools.** They gain the speaking fields; there are no new tools.
- **The same pages.** A provider's page has a Listening part and a Speaking part.

| The request | Here |
|---|---|
| A common interface for every provider | `VoiceEngine` (§4.1): the read-aloud engine's own shape today |
| One adapter per provider | by protocol: Android, three sockets, four request-per-sentence services (§4.3) |
| Choosing one at run time | `SpeechEngines.voiceOf` (§4.2), beside the listening factory |
| The same input and output for every provider | sentences in; 16-bit PCM out, played by one player (§4.4) |
| Adding a provider without touching the rest | a kind, an adapter, one branch (§4.2) |
| Configuration, credentials, errors | the listening ones, shared (§4.5, §4.6) |
| The user and the assistant set it up through one backend | the same owner and tools (§6) |
| A consistent Voice page | one row for each way, each opening the same kind of chooser sheet (§5) |
| 0 gaps | each sentence asked for while the one before it plays (§4.3) |

## 1. Facts this plan rests on (checked 2026-11-07)

The providers' documentation sites stay blocked from this session. Every fact below comes from the
providers' own sources on GitHub, read at their latest commit, or from probing a live API.

### 1.1 Android (in iCode today)

`VoiceSpeaker` wraps Android's `TextToSpeech`:
- It uses the engine and voice chosen in Android's settings.
- `speak(sentences: ReceiveChannel<String>, playing: (Boolean) -> Unit)`: each sentence is queued
  (`QUEUE_ADD`) as it comes, with the audio attributes `USAGE_ASSISTANT` and `CONTENT_TYPE_SPEECH`.
- `VoiceCoordinator` opens one speech session per turn. Sentences come from `SpokenTurn`, the chat's ▶,
  and Play Sample.

### 1.2 OpenAI

Source: [openai/openai-openapi](https://github.com/openai/openai-openapi), `openapi.yaml`.

- **Its Speech API is ending.** `POST /v1/audio/speech` and every model it takes (`tts-1`, `tts-1-hd`,
  `gpt-4o-mini-tts` and both dated versions) are "deprecated and scheduled to shut down on January 6,
  2027". The spec says: "for new integrations, migrate to `gpt-realtime-2.1-mini` through the Realtime
  API". So after that date OpenAI has no text-to-speech model of its own.
- **What the Realtime API offers instead:**
  - A `realtime` session with `output_modalities: ["audio"]` (the default), where `audio.output`
    takes `format`, `voice` and `speed`.
  - Voices `alloy` (the default), `ash`, `ballad`, `coral`, `echo`, `sage`, `shimmer`, `verse`, `marin`
    and `cedar`: "We recommend `marin` and `cedar` for best quality". Speed defaults to 1.
  - The audio comes back in `response.output_audio.delta`; its PCM is 24 kHz.

  This is a conversational model: it says what it is instructed to say. Word-for-word reading is
  something it is asked to do, not something the API guarantees (§9 D3).
- **The Speech API's own shape** (still served, and copied by other servers): JSON `model`, `input`
  (at most 4 096 characters), `voice`, `instructions`, `speed` (0.25–4), and `response_format`
  (`mp3`, `opus`, `aac`, `flac`, `wav`, `pcm`).

### 1.3 AssemblyAI

Source: [AssemblyAI/assemblyai-python-sdk](https://github.com/AssemblyAI/assemblyai-python-sdk),
`assemblyai/tts`.

- **Hosts:** `https://tts.us.assemblyai.com`, or `tts.eu.assemblyai.com` for the EU data zone. The
  same key goes in `Authorization`, as for listening.
- **Streaming:** `wss://…/v1/ws?voice_id=…&sample_rate=…&encoding=…`.
  - The client sends `{"type":"Generate","text":…}`, `{"type":"Flush"}`, `{"type":"Cancel"}` and
    `{"type":"Terminate"}`.
  - The server sends `Begin`, `Audio` (`flush_id` and base64 `audio`), `FlushDone` (`flush_id`,
    `audio_duration_ms`), `Cancelled`, `Termination` and `Error`.
- **One request:** `POST /v1/speech` with JSON `text`, and `stream` for chunked audio.
- **Voices and models:**
  - Eleven built-in voices: `aai-jane` (the default), `aai-alba`, `aai-anna`, `aai-charles`,
    `aai-eve`, `aai-george`, `aai-jean`, `aai-mary`, `aai-michael`, `aai-paul` and `aai-vera`. They
    speak English, Spanish, German, French, Italian and Portuguese. An account's own cloned voices are
    named by their slug.
  - `model_id`, such as `assemblyai-tts-1`.
- **Audio:** raw `pcm_s16le` at 24 kHz by default; 8 000, 16 000, 22 050, 24 000, 44 100 or 48 000 Hz.
- **Speed:** "only 1 is supported".
- **Errors:** they carry a stable `code`: `invalid_parameter`, `voice_not_found`, `rate_limited`,
  `at_capacity`, `auth_unavailable`, `biometric_terms_not_accepted`.

### 1.4 Deepgram

Source: [deepgram/deepgram-api-specs](https://github.com/deepgram/deepgram-api-specs).

- **Streaming:** `wss://api.deepgram.com/v1/speak`, with the key as for listening (`Token`).
  - **Query:** `model`, `encoding` (`linear16`, the default), `sample_rate` (8 000–48 000; 24 000 by
    default), `speed`.
  - The client sends `{"type":"Speak","text":…}`, `Flush`, `Clear` and `Close`.
  - The server sends audio chunks, then `Metadata`, `Flushed`, `Cleared` and `Warning`.
- **One request:** `POST /v1/speak`.
- **Voices** are models. The spec lists 90 `aura-2` voices in seven languages (English 41, Spanish 17,
  Dutch 9, Italian 9, German 7, Japanese 5, French 2), and 13 older `aura-*-en`. Its own default is
  `aura-asteria-en`.

### 1.5 Google Cloud Text-to-Speech

Source: the discovery document (`texttospeech.googleapis.com/$discovery/rest?version=v1`, revision
20261103), and a probe with a made-up key.

- **It takes an API key:** `POST https://texttospeech.googleapis.com/v1/text:synthesize` with
  `X-Goog-Api-Key` answered 400 `API_KEY_INVALID`, so it reads the key there. That is the same key
  handling as its speech recognition.
- **Request:** `input.text`; `voice` (`languageCode`, `name`, `modelName`); `audioConfig`
  (`audioEncoding`, `sampleRateHertz`, `speakingRate` 0.25–2.0).
- **`PCM` is raw 16-bit little-endian**, "not wrapped in a WAV (or any other) header". `LINEAR16`
  carries one. The answer is `audioContent`, base64.
- **Voices:** `GET v1/voices?languageCode=…` returns the real list, each voice with `name`,
  `languageCodes` and `naturalSampleRateHertz`.

### 1.6 Azure AI Speech

Source: [Azure-Samples/cognitive-services-speech-sdk](https://github.com/Azure-Samples/cognitive-services-speech-sdk),
`skills/azure-tts-integrate/references` (its own note: "Docs checked 2026-10-19").

- **REST, with no SDK:** `POST https://<region>.tts.speech.microsoft.com/cognitiveservices/v1`.
  - **Headers:** `Ocp-Apim-Subscription-Key` (key auth), `Content-Type: application/ssml+xml`, and
    `X-Microsoft-OutputFormat: riff-24khz-16bit-mono-pcm` (a WAV file).
  - **Body:** SSML: `<speak …><voice name="en-US-JennyNeural">…</voice></speak>`. The text is
    escaped into an XML text node.
- **Not read here:** the voice-list endpoint. Voices are typed by name until it is read in its cycle.

### 1.7 ElevenLabs

Source: [elevenlabs/elevenlabs-python](https://github.com/elevenlabs/elevenlabs-python).

- **Request:** `POST https://api.elevenlabs.io/v1/text-to-speech/{voice_id}/stream?output_format=pcm_24000`
  with `xi-api-key`.
  - **Body:** JSON `text`, `model_id`, `language_code` (ISO-639-1), and `previous_text` / `next_text`
    for continuity between requests.
  - **Formats:** `pcm_8000` to `pcm_48000`, and `mp3_*`.
- **Other hosts:** `api.us.elevenlabs.io`, and the EU, India and Singapore residency hosts.
- **Lists:** voices from `GET v1/voices` (and `v2/voices`), models from `GET v1/models`.
- Its SDK also has speech-to-text, realtime included: listening could be added later (§8).

## 2. Decisions

| # | Decision | Why |
|---|---|---|
| V1 | **One list of providers for both ways.** Each kind says whether it hears, speaks, or both. A provider holds one key, used for each | One account, one key, entered once: no duplication |
| V2 | **Two choices,** `inUse` (listens) and `speaker` (speaks), each Android's until the user chooses another. Removing one in use gives that way back to Android | The best listener need not be the best voice |
| V3 | **One speaking interface,** `VoiceEngine`, with the shape `VoiceSpeaker` has today | The coordinator's speech sessions, `SpokenTurn`, ▶ and Play Sample do not change |
| V4 | **Adapters by protocol.** Android; one socket runner for AssemblyAI, Deepgram and OpenAI; one request-per-sentence runner for Google, ElevenLabs, Azure and OpenAI-compatible servers | Each provider keeps only its protocol |
| V5 | **One player,** `VoicePlayer`: `AudioTrack`, 16-bit mono PCM at the rate the engine asked for, with the attributes Android's voice uses today. Every cloud provider is asked for PCM or WAV, so iCode decodes nothing | Fewer moving parts; the same sound path for all |
| V6 | **No gaps between sentences.** A socket sends each sentence as it comes, and its audio plays as it arrives. A per-sentence service is sent the next sentence while the one before it plays | The first words are heard while the model still writes |
| V7 | **Voices from the provider:** the live list where its API has one (Google, ElevenLabs), the spec's list where the spec fixes it (OpenAI, Deepgram, AssemblyAI), typed where neither (Azure, OpenAI-compatible servers). Filtered by the provider's language | Nothing invented, and no voice that is not there |
| V8 | **Failures in the shared words** (`SpeechFailure`): "The answer was not read aloud", with the provider's reason. The answer stays on screen. Nothing goes to another provider | As for listening (speech-plan S8) |
| V9 | **The agent uses the same three tools,** which gain `voice`, `voice_model`, `speed` and `use: listening`, `speaking` or `both`; still asked about every time | One owner, two callers (agent-control-plan §1) |
| V10 | **What is read aloud goes only to the provider that speaks;** it is never logged | The voice rule today |
| V11 | **Speed only where the API says it can be set:** OpenAI, Deepgram and Google. AssemblyAI says only 1 is supported. Azure and ElevenLabs are left at their default until their own speed fields are read | No control that does nothing |

## 3. iCode before this plan

| Part | Where |
|---|---|
| Android's voice | `data/voice/VoiceSpeaker.kt` |
| Speech sessions, sentence by sentence; ▶; Sample; Silence | `data/voice/VoiceCoordinator.kt` |
| What a turn says aloud | `domain/voice/SpokenTurn.kt`, `SpokenText.kt` |
| Providers, keys, the owner, the tools, the pages | `data/voice/speech/*`, `ui/screens/settings/Speech*` |
| Shared WebSocket and HTTP plumbing, failure words | `SpeechSocket`, `SpeechUploads`, `SpeechRules` |

## 4. Architecture

```
 Voice page ── Listening sheet ─┐                 ┌─ hears:  SpeechEngine (speech-plan)
           └── Speaking sheet ──┼─ SpeechProviders ┤
 Agent tools (list/save/remove) ┘   (one list,     └─ speaks: VoiceEngine
                                     two choices)        ├ AndroidVoice      TextToSpeech
 VoiceCoordinator ── SpeechEngines.voiceOf(speaker) ─────┤ AssemblyAiVoice   wss /v1/ws  Generate · Flush
   one session per turn: Begin · Say · End · Hush        ├ DeepgramVoice     wss /v1/speak  Speak · Flush
                                                         ├ OpenAiVoice       wss /v1/realtime, audio out
                                                         ├ GoogleVoice       text:synthesize, PCM
                                                         ├ ElevenLabsVoice   /v1/text-to-speech/{voice}/stream
                                                         ├ AzureVoice        cognitiveservices/v1, SSML
                                                         └ CompatibleVoice   /audio/speech, wav
                                                                  │
                                       VoicePlayer (AudioTrack, 16-bit PCM) ◄┘
```

### 4.1 The interface

```kotlin
// data/voice/speech/VoiceEngine.kt
internal interface VoiceEngine {
    /**
     * Says each of [sentences] as it comes, and returns once the channel is closed and all of it is
     * said; [playing] follows whether it sounds now. Cancelled, it stops at once. Throws the
     * provider's failure, in the shared words, when it cannot.
     */
    suspend fun speak(sentences: ReceiveChannel<String>, request: VoiceRequest, playing: (Boolean) -> Unit)
}

internal data class VoiceRequest(val voice: String?, val model: String?, val language: String?, val speed: Float?)
```

`SpeechProvider` gains `voice`, `voiceModel` and `speed`; its `model` and `language` stay listening's.
A kind gains `speaks` (and `hears`), its fixed voices or how it lists them, its speaking models, and
whether it takes a speed.

| Kind | Hears | Speaks | Voices | Default voice | Audio asked for |
|---|---|---|---|---|---|
| Android | yes | yes | Android's settings | the phone's | — |
| AssemblyAI | yes | yes | the 11 built-in, or a slug typed | `aai-jane` | `pcm_s16le`, 24 kHz |
| OpenAI | yes | yes (D3) | the 10 realtime voices | `marin` ("We recommend `marin` and `cedar` for best quality", its spec; its default is `alloy`) | PCM, 24 kHz |
| OpenAI-compatible | yes | yes | typed | — (the server's) | `wav` |
| Deepgram | yes | yes | the `aura-2` voices for the language | `aura-asteria-en` (its spec's default) | `linear16`, 24 kHz |
| Google Cloud | yes | yes | `v1/voices` for the language | the first it lists | `PCM`, 24 kHz |
| Azure (new) | not yet (speech-plan D2) | yes | typed (`en-US-JennyNeural`) | — | `riff-24khz-16bit-mono-pcm` |
| ElevenLabs (new) | not yet (§8) | yes | `v1/voices` | the first it lists | `pcm_24000` |

### 4.2 The factory

`SpeechEngines.voiceOf(provider, key)` is one `when` over the kinds that speak, beside `of` for
listening. A new provider follows the same steps as for listening:
1. A kind, with its capabilities.
2. An adapter, or one already there that speaks its protocol.
3. One branch.

### 4.3 The adapters

| Adapter | One speech session |
|---|---|
| `AndroidVoice` | `VoiceSpeaker`, behind the interface, unchanged |
| `AssemblyAiVoice` | Opens `/v1/ws` with `voice_id`, `sample_rate=24000`, `encoding=pcm_s16le` and `language` (its English name: `english`, `spanish`… from the provider's tag). Each sentence: `Generate`, then `Flush`; `Audio` plays as it comes; `FlushDone` ends the sentence. `Terminate` at the end, `Cancel` on Silence |
| `DeepgramVoice` | Opens `/v1/speak` with the voice as `model`, `encoding=linear16`, `sample_rate=24000` and `speed`. Each sentence: `Speak`, then `Flush`; its chunks play; `Flushed` ends the sentence. `Close` at the end, `Clear` on Silence |
| `OpenAiVoice` | A `realtime` session on `gpt-realtime-2.1-mini`, audio out: PCM at 24 kHz, the voice, the speed. Instructions: read the text it is given exactly, word for word, adding and leaving out nothing. Each sentence is one response; `response.output_audio.delta` plays. Its exact client events are taken from the spec in V4 |
| `GoogleVoice` | `text:synthesize` per sentence, `PCM` at 24 kHz, the voice by name and language, `speakingRate`; the key in `X-Goog-Api-Key` |
| `ElevenLabsVoice` | `/v1/text-to-speech/{voice}/stream?output_format=pcm_24000` per sentence, `model_id`, `language_code`, and `previous_text` set to the sentence before, for continuity; chunks play as they come |
| `AzureVoice` | `cognitiveservices/v1` per sentence, the text escaped into SSML with the voice and language, `riff-24khz-16bit-mono-pcm` |
| `CompatibleVoice` | `/audio/speech` per sentence: `model`, `voice`, `input`, `response_format=wav` (the rate is read from the file) |

- **The socket runner** shares `SpeechSocket`'s connection, handshake-status and close-code handling.
- **The per-sentence runner** sends sentence N+1 while N plays. Each request's audio is played as it
  streams where the API streams it (ElevenLabs, AssemblyAI's `stream`), or whole where it does not.
- **A sentence too long** for a provider's input is cut at word boundaries (`SpokenText.parts`): at
  OpenAI's 4 096 characters, and at an iCode cap of 1 000 for those whose limit is not read here.

### 4.4 The player

`VoicePlayer` plays 16-bit mono PCM through `AudioTrack` in streaming mode:
- **Rate and attributes:** at the rate the engine asked for, with `USAGE_ASSISTANT` and
  `CONTENT_TYPE_SPEECH`.
- **Playing:** `playing(true)` at the first audio, `playing(false)` once drained. Silence stops it
  and empties it at once.
- **Odd chunks:** a byte left over at a chunk's end (AssemblyAI's SDK warns chunks are not aligned
  to samples) waits for the next chunk.
- **WAV:** read by its chunks (`fmt `, `data`), never by a fixed header length.
- **Audio focus** (D5): the player asks for transient focus with ducking while it speaks, and lets
  go after.

### 4.5 Configuration and keys

These are listening's (speech-plan §4.5):
- **The store** keeps the same providers. It gains the speaking fields and the `speaker` choice; the
  keys are untouched.
- **The owner** gains `use(id, way)`, where the way is listening or speaking. A refusal names the way:
  "Azure cannot listen yet: choose it for speaking".
- **D4's link to the assistant's OpenAI key** serves speaking too.

### 4.6 Errors

The same `SpeechFailure`s and words, with these mapped:
- **AssemblyAI:** `voice_not_found` is Failed ("AssemblyAI has no voice …"); `rate_limited` and
  `at_capacity` are Busy; `biometric_terms_not_accepted` is Failed with its words; `auth_unavailable`
  is Server.
- **Every provider:** HTTP 401 and 403 are KeyRefused, 402 NoCredit, 429 Busy, 5xx Server.
- **Google's** 400 `API_KEY_INVALID` is KeyRefused.

A failed speech session gives the notice "The answer was not read aloud", with the provider's reason.
The bubble's Speaking look ends with it.

## 5. The pages

- **The Voice page** has one row per way, each opening a **chooser sheet** at the bottom of the
  screen (the owner's "bottom bar", §9 D4):
  - **Listening:** "Speech Recognition · Android". The sheet lists the providers that hear, the one
    in use checked, and "Providers ›" at its foot.
  - **Speaking**, in Read Aloud: "Voice · Android". The sheet lists the providers that speak, the
    same way.
  - Answers and Play Sample stay where they are; Play Sample uses the provider that speaks.
- **The Speech Providers page** (today's Speech Recognition page, renamed): every provider, each marked
  "Listens" or "Speaks" where it is in use.
- **A provider's page:**
  - **Account:** name, key or Key From, address.
  - **Listening** (kinds that hear): model, language, Test, Use for Listening.
  - **Speaking** (kinds that speak): voice (the list, or a field), model, speed where it applies,
    Hear It (says a sample with these settings), Use for Speaking.
  - Remove at the end.
- **Android's page** gains "Voice and Language", the link to Android's text-to-speech settings
  (moved from the Voice page), beside its voice input settings.
- **Privacy:** PRIVACY.md's Voice line gains that what is read aloud goes to the provider that
  speaks.

## 6. The agent's tools

They are the same three tools:
- **`list_speech_providers`** also says, for each provider: whether it speaks, its voice, voice model
  and speed, and which provider speaks now.
- **`save_speech_provider`** takes `voice`, `voice_model` and `speed`, and `use` becomes `listening`,
  `speaking` or `both` (`true` still means listening). A field a kind does not take is refused in its
  words.
- **`remove_speech_provider`:** removing a provider in use gives each way it served back to Android.

## 7. Cycles

| # | What | Tests |
|---|---|---|
| V0 | `VoiceEngine`, `AndroidVoice` (`VoiceSpeaker` behind it), `voiceOf`, the coordinator through them; the `speaker` choice stored, Android's by default. Nothing changes for the user | the coordinator's sessions with a fake voice: sentences in order, Silence, End, a failure's notice |
| V1 | The kinds' speaking capabilities, the speaking fields, `use(id, way)`; the Voice page's two rows and their chooser sheets; the provider page's Speaking part with Hear It; Android's TTS link | the store and owner (fields, both choices, removal giving back a way, refusals); the guards |
| V2 | `VoicePlayer`, the WAV reader, the per-sentence runner; Google, ElevenLabs, Azure (the kind is new) and OpenAI-compatible servers. First read Azure's voice list | a fake player: order, no gap (N+1 asked before N ends), Silence; fake HTTP servers for each; WAV by chunks |
| V3 | The socket runner; AssemblyAI and Deepgram | fake sockets: `Generate`/`Speak` then `Flush` per sentence, the audio in order, `Cancel`/`Clear` on Silence, the codes mapped |
| V4 | `OpenAiVoice` on the Realtime API (D3) | a fake realtime socket: the session, one response per sentence, deltas in order |
| V5 | The tools' speaking fields | as `SpeechProviderToolsTest`: the fields, `use` both ways, refusals |
| V6 | Docs as built (this plan, speech-plan, phone-agent-plan §5.8, PRIVACY.md, PR-ASSIST-24, US-ASSIST-017, the changelog), guards, CI | — |

**Phone checks**, with keys the owner provides (scratchpad only, never committed):

| # | Do | See |
|---|---|---|
| VP1 | Ask in the bubble with Android speaking | as today |
| VP2 | Choose Google for speaking, a voice from its list; Hear It; ask in the bubble | the voice chosen, first sentence while the model writes, no gap between sentences |
| VP3 | The same with AssemblyAI, Deepgram, ElevenLabs, Azure, OpenAI | the same, each with its own voice |
| VP4 | Tap the bubble while it speaks | silence at once |
| VP5 | A wrong key | "The answer was not read aloud", the key named; the answer on screen; nothing elsewhere |
| VP6 | Music playing, then an answer read aloud | the music lowers while it speaks, and comes back |
| VP7 | "Make Deepgram read my answers in a Spanish voice" | it asks; the Voice page shows it speaking, the voice chosen |
| VP8 | Listening with AssemblyAI and speaking with Google | each does its own way |

## 8. Not now

- Listening with ElevenLabs (its SDK has speech-to-text and realtime) and Azure (speech-plan D2).
- Word timings for highlighting the words as they are said (AssemblyAI's `WordBoundaries`).
- Cloned or designed voices made from iCode. A cloned voice the account already has can be typed by
  its slug or ID.
- SSML or markup written by the user.

## 9. Owner decisions

| # | Decision | Recommended |
|---|---|---|
| D1 | One list of providers, two choices: one listens, one speaks (V1, V2) | Yes |
| D2 | Two new kinds for speaking: Azure (REST, no SDK) and ElevenLabs. Listening with them later (§8) | Yes |
| D3 | OpenAI speaks through the Realtime API's `gpt-realtime-2.1-mini`: OpenAI's own way after its Speech API shuts on 2027-01-06. It is told to read word for word, but a conversational model can stray, and its page says so | Yes, with its page saying it; or leave OpenAI out of speaking |
| D4 | The Voice page chooses each way in a sheet from the bottom of the screen, with Providers › at its foot (§5) | Yes |
| D5 | Speaking lowers other sound (transient focus with ducking) for every engine, Android's included | Yes |
| D6 | No silent fallback for speaking either: a provider that fails says why, and the answer stays on screen | Yes |
