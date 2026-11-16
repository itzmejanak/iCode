# iCode public documentation

This directory describes iCode as a product, the Android application that exists today, and the work still required to reach the intended scope. It is written for users, contributors, reviewers, and release maintainers.

## Reading paths

### Users and evaluators

1. Start with [Product](product.md) for the audience, supported platform, capabilities, limitations, and current status.
2. Read the specialist page for the workflow you care about.
3. Check [Roadmap](roadmap.md) before relying on a capability marked **Partially Done**, **Not Started**, or **Needs Verification**.
4. Read the root [Privacy](../PRIVACY.md) and [Security](../SECURITY.md) policies before configuring network providers, phone control, shared storage, or credentials.

### Contributors

1. Read [Architecture](architecture.md) for current ownership, process lifetime, storage, runtime, and security boundaries.
2. Read [Engineering Rules](engineering-rules.md) for package ownership, guarded writes, lifecycle, persistence, design-system, and testing rules.
3. Read [Product](product.md) for the intended outcome and acceptance boundary.
4. Read the relevant specialist page and [Roadmap](roadmap.md) for remaining implementation and evidence.
5. Follow the root [Contributing guide](../CONTRIBUTING.md), [licence](../LICENSE), and [third-party notices](../THIRD_PARTY_NOTICES.md).

### Maintainers and release reviewers

1. Use [Roadmap](roadmap.md) as the only status ledger for unfinished work.
2. Use [Build and release](build-and-release.md) for release mechanics.
3. Use source, tests, generated artifacts, device matrices, and provider/network results as evidence. A plan or screen design is not implementation evidence.

## Sources of truth

The sources have distinct jobs:

1. **Current implementation:** production source, build configuration, manifests, assets, and tests are authoritative. In particular, `settings.gradle.kts`, `app/build.gradle.kts`, `app/src/main/`, `runtime/`, `third_party/`, `tools/`, and `.github/workflows/` establish what the repository builds and owns.
2. **Intended product scope:** [product.md](product.md) owns the product vision, supported platform, required capability scope, non-goals, and acceptance boundaries.
3. **Current technical design:** [architecture.md](architecture.md) owns the architecture implemented by the current source. It does not describe a hypothetical module split or future feature as if it exists.
4. **Unfinished work:** [roadmap.md](roadmap.md) is the single source of truth for implementation gaps, unresolved decisions, external dependencies, and missing verification.
5. **Specialist contracts:** the canonical pages below own detail for their subject. They must agree with Product, Architecture, Roadmap, and the current source.
6. **Policy:** root policy files remain canonical. Documentation in `docs/` must not restate or weaken them.

When documents disagree about current behavior, source and tests win. Documents outside this canonical set do not define current behavior or status. Historical versions may still provide context through version control, but every current authority and link must resolve within this set.

## Status labels

These are the only status labels used by the canonical public documentation:

- **Done** — the required scope is implemented and the evidence required for its stated acceptance boundary is available.
- **Partially Done** — useful implementation exists, but part of the required scope is still absent.
- **Not Started** — the intended capability has no production implementation.
- **Needs Verification** — production implementation exists, but required device, provider, network, performance, release, or integration evidence is still missing.

A status applies only to the row or roadmap item that carries it. It does not imply that a broader feature, release, or product is ready.

## Canonical documentation set

| Document | Purpose |
|---|---|
| [product.md](product.md) | Vision, audience, principles, supported platform, scope, non-goals, capability status, and acceptance boundaries. |
| [architecture.md](architecture.md) | Current Android architecture, ownership, lifetimes, data paths, persistence, security, and build variants. |
| [engineering-rules.md](engineering-rules.md) | Package ownership, dependency direction, guarded writes, lifecycle, persistence, security, design-system, consistency-guard, and testing rules. |
| [design-and-navigation.md](design-and-navigation.md) | Phone-first information architecture, navigation, interaction, accessibility, visual system, and the current wider-screen boundary. |
| [workspaces-and-files.md](workspaces-and-files.md) | App-private workspaces, Private and Shared explorers, file lifecycle, DocumentsProvider, recovery, and storage limits. |
| [editor-and-language-services.md](editor-and-language-services.md) | Editor behavior, syntax, Markdown, LSP, completion, diagnostics, search, and replacement. |
| [source-control.md](source-control.md) | Git state, diffs, staging, commits, branches, remotes, conflicts, credentials, and GPG signing. |
| [runtime-and-terminal.md](runtime-and-terminal.md) | Ubuntu/PRoot installation, guest environment and paths, runtime tools, processes, terminal, Android SDK/NDK, and Build and Run. |
| [assistant-and-mcp.md](assistant-and-mcp.md) | Assistant providers, chats, tools, permissions, managed work, MCP servers, and protocol limits. |
| [voice-and-phone-control.md](voice-and-phone-control.md) | Speech input/output, provider handling, AccessibilityService, overlays, Shizuku, privacy, and safety. |
| [build-and-release.md](build-and-release.md) | Build types, CI, signing, channels, artifacts, release metadata, and release evidence. |
| [roadmap.md](roadmap.md) | The single current ledger for all unfinished implementation, verification, external work, and decisions. |
| [editor-grammar-provenance.md](editor-grammar-provenance.md) | Sources, versions, licences, transformations, and reproducibility of bundled TextMate grammars. |

Each specialist page owns its subject's durable detail. [Roadmap](roadmap.md) remains the only status ledger for unfinished scope; documents outside this set cannot change status.

## Root policy documents

The following files remain authoritative and outside this documentation set:

- [PRIVACY.md](../PRIVACY.md) — data collection, network transmission, device information, assistant, phone control, and voice privacy.
- [SECURITY.md](../SECURITY.md) — supported versions and private vulnerability reporting.
- [CONTRIBUTING.md](../CONTRIBUTING.md) — contribution workflow and repository rules.
- [LICENSE](../LICENSE) — project licence.
- [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) — bundled, vendored, and downloaded third-party material.
- [CODE_OF_CONDUCT.md](../CODE_OF_CONDUCT.md) — contributor conduct.
