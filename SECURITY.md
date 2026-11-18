# Security policy

## Supported versions

Only the latest version published on [GitHub Releases](https://github.com/itzmejanak/iCode/releases) receives security fixes. Older releases, development builds, and arbitrary source snapshots are not supported with backported fixes.

Because iCode is in early development, security-sensitive behavior and compatibility can change between releases. Verify the APK and SHA-256 from the same release before installation.

## Report a vulnerability privately

Do not open a public issue or discussion for a suspected vulnerability. Use the repository's private [GitHub Security Advisory form](https://github.com/itzmejanak/iCode/security/advisories/new).

Include, when applicable:

- the iCode version or commit, build channel, Android version, and device model;
- the affected feature and required configuration or permissions;
- the security impact and a minimal reproduction;
- a proof of concept, logs, screenshots, or sample files with credentials, tokens, personal data, and private workspace content removed; and
- whether you believe exploitation is active or public disclosure is imminent.

Please test only with devices, accounts, providers, repositories, and servers you own or are authorized to assess. Do not retain or access another person's data beyond what is necessary to demonstrate the issue.

The maintainer will validate the report, coordinate a fix and release when warranted, and discuss disclosure with the reporter. This project does not publish a response or remediation SLA. Allow time for a patched release and advisory before public disclosure.

## Scope

Reports are in scope when the weakness is in this repository's code or release process, including:

- Android application components, navigation, storage, exported activities/providers, URI grants, and the workspace DocumentsProvider;
- native code, PRoot integration, runtime path mapping, process/PTY handling, applied patches, runtime downloads, digest verification, and package installation;
- workspace, recovery, chat, attachment, report-draft, and log handling;
- Git HTTPS/SSH authentication, GPG signing, provider credentials, speech keys, MCP secrets, OAuth returns/tokens, and Android Keystore use;
- assistant tool approvals, prompt/context assembly, attachments, model-provider adapters, local and remote MCP integrations, and tool-result handling;
- phone control, accessibility, overlays, Shizuku authorization and shell actions, screenshots, and sensitive-screen handoff;
- microphone capture, remote speech recognition, speech synthesis, and audio/text routing;
- app-to-website update, notice, device-fact, report, signed Cloudinary upload, and runtime-manifest requests; and
- CI/release configuration, dependency and submodule integrity, APK signing, checksums, update-channel selection, and release/update supply chain.

The iCode website service, external AI or speech providers, Git hosts, remote MCP services, Cloudinary, Android services, Ubuntu repositories, and user-installed runtime tools are separate systems. A vulnerability solely in one of those systems should normally be reported to its operator. Report it here as well when iCode exposes data, grants access, validates a response, or composes those systems in an unsafe way.

## Current security boundaries and limitations

- **PRoot is not a sandbox or virtual machine.** It provides Linux userspace compatibility inside iCode's Android sandbox. Runtime commands, language servers, local MCP servers, and other installed tools execute with the app's access and can read or modify data available to them.
- **Coding agents are third-party programs.** iCode installs a build only when its SHA-256 matches the digest in the ACP registry's list, and an npm package only at the version the list names, with npm checking each package against the npm registry's record. iCode relies on that list, read over HTTPS, to name the right releases. An installed agent runs in the runtime with the reach described above.
- **Assistant access is consequential.** A configured model or MCP tool can receive prompts, files, attachments, screen context, commands, and tool output, and approved tools can change files or run processes. Review providers, endpoints, tools, and approval mode before use.
- **App-private does not mean application-encrypted.** Workspaces, chats, runtime files, and most settings are ordinary files protected primarily by Android's app sandbox. Supported stored secrets use Keystore-backed encryption, while SSH/GPG keys remain files in the private runtime.
- **Optional permissions increase impact.** All Files Access, accessibility, microphone access, package installation, and Shizuku are enabled only for their described features, but a compromise while they are active can expose more data or control.
- **A compromised device is outside the trust boundary.** Root access, malicious firmware, an unlocked debugging environment, or another privileged process can defeat assumptions provided by Android's application sandbox.
- **External services keep their own records.** iCode cannot guarantee the security, logging, retention, or deletion behavior of configured providers, remotes, servers, or upload infrastructure.

See [PRIVACY.md](PRIVACY.md) for data flows and retention, and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependency and runtime provenance.
