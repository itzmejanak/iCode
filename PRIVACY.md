# Privacy

iCode is local-first. It has no account and no sign-in. Your workspaces, Git credentials, keys and
assistant chats stay on your device.

## What leaves your device

**Only what you choose to send:**

- **Git:** clone, pull and push talk to the remotes you configure.
- **AI assistant and AI commit messages:** the prompt and the workspace content the assistant needs
  are sent to the AI provider you configured, using your own key. That provider's privacy policy
  applies.
- **Phone control** (off until you turn it on): while the assistant works on your request, the text of
  the screen in front, and a picture of it when the text is not enough, are sent to the chat's AI
  provider. Nothing of the screen is kept or logged.
- **Voice:** with Android's speech recognition, what you say goes to the recognition service your
  phone uses; with a model on the phone, it stays on the phone. The microphone is on only while
  iCode listens. Voice models are downloaded from sherpa-onnx's GitHub releases when you choose one.
- **Runtime and tools:** the Ubuntu base image and optional tools are downloaded from the sources
  listed in `app/src/main/assets/runtime/manifest.json`.
- **iCode website** (only in builds that set a website address): iCode reads app config, release
  info, notices and the runtime manifest. These requests carry no personal data.

## Device info (opt-in)

At the end of onboarding, iCode asks whether to share anonymous device info. **Not Now** is the
default, and no answer means nothing is sent.

If you choose **Share Device Info**, iCode sends:

| Data | Why |
|---|---|
| A random install ID, created when you opt in | Count installs without identifying anyone |
| App version name and code | Know which builds are in use |
| Android release and SDK level | Know which Android versions to support |
| Device manufacturer and model | Device compatibility |
| Primary ABI | Runtime architecture |
| Locale | Languages to support |
| Whether the runtime is installed | How many people finish setup |

**Never sent:** workspace names, paths or files; Git remotes, branches, credentials or keys; terminal
input or output; assistant prompts, answers or provider keys; Android ID, IMEI, advertising ID,
accounts or phone number. The website does not store IP addresses.

## Turning it off

Settings › Application › **Share Device Info**. Turning it off asks the website to delete your
install record and forgets the ID on the device.

Full technical detail: [docs/device-telemetry-plan.md](docs/device-telemetry-plan.md).
