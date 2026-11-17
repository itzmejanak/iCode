# Privacy

iCode is local-first and has no iCode account or sign-in. Its core workspace, editor, local Git, search, runtime, and terminal features do not require an iCode service. Networked features are optional or occur when you ask iCode to contact a service.

This document describes the Android app in this repository. A configured AI provider, Git host, MCP server, speech service, Cloudinary, package repository, or other external service applies its own privacy and retention terms.

## Data stored on the device

### Workspaces and app state

Workspaces are stored as ordinary, unencrypted files in iCode's Android app-private storage. The Linux runtime, terminal state, preferences, drafts, recovery data, logs, and cached metadata also use app-private storage. Android backup and device transfer are disabled for this data, but iCode does not add application-level encryption to workspace files or most app state.

The Android DocumentsProvider can expose workspace files to the system Files UI and to another app only through Android's document and URI-grant mechanisms. Import copies a selected phone folder into iCode's workspace storage.

### Assistant chats and attachments

Assistant chats are stored locally per workspace, with a separate area for chats that have no workspace. Stored chat data can include:

- prompts, speech transcripts sent as messages, assistant replies, and reasoning text;
- compacted context summaries;
- tool names, call IDs, arguments, commands, file references, change summaries, and success or failure state;
- bounded tool results, including file content and terminal, Git, language-server, MCP, or build output returned to the chat; and
- text attachments in chat data plus local copies of attached images and PDFs.

The app keeps every message of a chat and each stored tool-result output in full. Sharing a chat writes its whole transcript to a file in the app's cache for the app you share it with; only the latest one is kept. Deleting a chat removes its retained attachment directory. Deleting a workspace removes chats associated with that workspace.

When a model request is made, the provider can receive the active retained context, including earlier messages and tool traffic needed for the turn. Compacted history can be represented by a summary. Do not attach or expose data you do not want that provider to process.

### Secrets

Provider API keys, speech-provider keys, Git HTTPS tokens, MCP secret values, and MCP OAuth tokens are encrypted with AES-GCM using keys held by Android Keystore. The Keystore key material does not leave the Keystore.

That protection does not cover workspace files, chat history, report drafts, ordinary settings, or all files inside the Linux runtime. SSH and GPG private keys are files in the app-private runtime. A compromised or unlocked device, a privileged attacker, or a tool running with iCode's app access may still reach app data.

## Data sent outside the device

### AI providers

The configured model provider receives the instructions and context required for an assistant or AI commit-message request. Depending on the request, this can include prompts, prior chat context, selected workspace files, text/image/PDF attachments, screenshots, tool calls and arguments, and tool results such as command, Git, build, language-server, or MCP output. Provider model-list and connection tests also contact the configured endpoint.

You select the provider or custom endpoint and supply its credentials. The provider's logging, training, and retention rules apply.

### MCP servers

A local MCP server runs as a process in the Linux runtime. A remote MCP server receives protocol and OAuth traffic at the address you configure. Tool arguments, prompts, and resource requests go to that server; returned text, resources, and images can be stored in the chat and sent onward to the configured AI provider as model context.

A local MCP process can make its own network requests. iCode cannot describe or control the destinations or retention behavior of software you install or configure.

### Phone control

Phone control is off until you enable it. Android's accessibility service provides screen text and actionable elements to iCode. When needed for your request, iCode can also capture a screenshot. That screen context is sent to the chat's AI provider.

Raw screenshot bytes used for a phone-control turn are not written to the chat store, but screen text, assistant replies, and resulting tool-call records can remain in local chat history and provider records. Phone control can operate other apps. Password, payment, and permission surfaces are intended to be handed back to you, but you should review the screen and the configured provider before enabling this feature.

### Speech

For speech recognition, raw microphone audio goes to the listening provider you select. Android's recognizer may run on-device when available or may use the device's configured recognition service. Configured remote recognizers receive streamed or uploaded audio under their own terms.

iCode does not persist raw microphone audio. A recognized transcript becomes normal chat data once you send it: it is stored in local chat history and sent to the configured AI provider.

For speech synthesis, answer or question text goes to the selected Android text-to-speech engine or remote speech provider. Returned audio is played and is not stored in chat history; the source text remains part of the chat. Android recognition and text-to-speech engines may themselves use network services.

### Git and user-installed tools

Clone, fetch, pull, push, authentication, and related connection tests contact the Git remotes you configure. Git protocol data and the credentials needed for authentication reach those remotes. Local status, diff, staging, history, and commit operations do not contact the iCode website.

App-managed HTTPS authentication decrypts the saved token into an owner-only file in the guest's app-private `/tmp` for one Git command. Normal completion, failure, and cancellation delete it. A hard Android process kill can prevent that cleanup; startup cleanup is not currently implemented. Git runs as a guest process and is not governed by Android's Network Security Config, so a configured `http://` remote uses cleartext transport. Prefer HTTPS or SSH.

Commands and developer tools installed in the Linux runtime can make arbitrary network requests, including cleartext requests allowed by those tools. Their behavior is outside iCode's data-flow guarantees.

### Runtime and tool downloads

When you approve runtime or tool installation, iCode downloads the Ubuntu root filesystem and selected tools or SDK components from the sources recorded in the bundled runtime manifest. [Runtime and terminal](docs/runtime-and-terminal.md#exact-role-of-the-runtime-manifest) documents that manifest's role and integrity boundary. Ubuntu `apt` contacts configured package repositories. Pinned direct downloads are checked against their recorded digest where the manifest provides one.

### Website, updates, and notices

Website-backed features are disabled in builds without an `ICODE_WEB_URL`. When configured, iCode can request app configuration and links, release/update metadata, notices, report policy, and a runtime manifest. Update checks include the app's update channel, ABI, and version code. An accepted update downloads an APK from the release URL, verifies its SHA-256, and sends it to Android's package installer.

These requests expose normal network metadata, such as an IP address and user agent, to the destination and its infrastructure. This Android repository does not establish server log or IP-retention policy.

### Reports and Cloudinary media

Sending an in-app report is explicit. The iCode website receives the report kind, title, description, email address you enter, source (`app`), and any device facts you choose to attach. A report draft, including attachment references, stays in app-private preferences until it is sent or replaced; a successful send clears the draft.

When report media is enabled, selected images or videos are uploaded directly to Cloudinary using upload fields signed by the iCode website. The website then receives the Cloudinary public ID and resource type with the report. The app repository does not define website or Cloudinary retention or deletion periods.

## Optional device facts

Device-fact sharing is opt-in. **Not Now** is the default, and leaving the question unanswered does not create an install ID or send device-fact telemetry.

If you opt in, iCode creates a random install ID and can send:

| Data | Purpose |
|---|---|
| Random install ID | Distinguish an installation without using a hardware or advertising identifier |
| App version name and code | Measure version use |
| Android release and SDK level | Plan Android compatibility |
| Device manufacturer and model | Investigate device compatibility |
| Primary ABI | Confirm runtime architecture |
| Locale | Plan language support |
| Runtime-installed state | Measure setup completion |

Periodic heartbeats contain the install ID, app version code, and runtime-installed state. Registration is updated when the full facts change.

Device-fact telemetry does not include workspace names, paths, or files; Git remotes, branches, credentials, or keys; terminal input or output; assistant prompts, answers, attachments, or provider keys; Android ID, IMEI, advertising ID, accounts, or phone number.

You can turn sharing off in **Settings › Application › Share Device Info**. The app makes a best-effort request to delete the server record, then removes the random install ID locally even if the request fails. The Android repository cannot guarantee that a failed request is retried or that server-side deletion succeeds. This action does not delete reports or data held by Git hosts, providers, MCP servers, speech services, or Cloudinary.

## Permissions and privileged integrations

- **All Files Access:** requested at the point of use for importing phone folders or allowing assistant tools to access phone storage. It is not needed for ordinary workspaces in app-private storage.
- **Accessibility:** enabled only when you turn on phone control. It can read screen content and perform actions in other apps for your requests.
- **Shizuku:** separately installed and authorized by you. It can simplify phone-control setup and provide shell-level actions that accessibility cannot perform.
- **Microphone:** used only while iCode is listening. Phone control's foreground service can keep listening available from the assistant bubble while iCode is not in front.
- **Install packages:** used by Build and Run. Android asks whether iCode may install unknown apps and confirms each APK installation.
- **Notifications, foreground services, wake lock, and battery exemption:** support user-started long-running work. They do not grant workspace or account access.

## Deletion

- Delete a chat to remove its local messages and retained attachment copies.
- Delete a workspace to remove its files and workspace-associated chats.
- Use Android's **Clear storage** to remove iCode's app-private workspaces, runtime, chats, settings, drafts, keys, and credentials.
- On uninstall, Android may offer to retain app data because iCode marks workspace data as important; the choice and result are controlled by Android.
- Contact each external provider about data already sent to that service.

Privacy-impacting changes should update this document in the same pull request. Security concerns should be reported privately under [SECURITY.md](SECURITY.md).
