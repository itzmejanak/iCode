# iCode

A native Android IDE built with Kotlin and Jetpack Compose. Workspaces live in an embedded Linux
runtime (Ubuntu, ARM64, PRoot), which every part of iCode reaches uniformly: the file tree, the
editor with autosave and draft recovery, Git (clone, diff, commit, push, pull, branches,
credentials), a terminal with background sessions, language servers, an AI assistant, and
AI-generated commit messages.

## Features

- **Workspaces** — create, clone, import from phone storage; accessible to other apps via Android's Files app
- **Editor** — syntax highlighting, autosave, draft recovery, external-change detection
- **Git** — full workflow: stage, commit, push, pull, branch, merge conflicts, SSH and HTTPS credentials
- **AI assistant** — chat with your codebase using any OpenAI-compatible, Anthropic, or Ollama provider; streaming replies; tool use for reading files, running commands, and editing code
- **AI commit messages** — one-tap conventional commit messages generated from staged changes (configurable model)
- **Terminal** — background sessions, alerts, managed processes
- **Language services** — pluggable LSP servers installed through the runtime
- **Search** — workspace-wide content search powered by ripgrep
- **Design system** — semantic tokens, dark/light/AMOLED themes, user-selectable accent color

## Build

Requirements: JDK 17, Android SDK 36.

```bash
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

On first launch iCode installs its Linux runtime, then offers optional permissions (notifications,
battery) one at a time through Android's own dialogs.

## Tests

```bash
./gradlew test
```

## Project structure

```text
app/src/main/java/dev/icode/mobile/
├── MainActivity.kt       # Single activity host
├── designsystem/         # Semantic tokens (colors, type, metrics, shapes) + shared UI kit
├── domain/               # Immutable state, intent/effect contract, rules
├── data/                 # Android implementations
│   ├── assistant/        # AI assistant engine, Koog integration, tools, chat store
│   ├── git/              # Git operations, auth, transfers, conflicts
│   ├── runtime/          # Linux runtime, terminal, package installer
│   ├── workspace/        # Gateway, session, lifecycle, documents, tree
│   ├── search/           # Ripgrep-backed content search
│   ├── settings/         # Preferences
│   └── ...               # Language services, notices, storage, IO, logging
├── navigation/           # Routes and navigation actions
└── ui/                   # Compose screens
    └── screens/{entry,workspace,tools,settings,recovery}/

app/src/debug/            # Capture fixtures (debug builds only)
app/src/test/             # Unit tests mirroring the main source tree
docs/                     # Architecture, plans, and design decisions
tools/                    # Consistency guards and screen capture scripts
runtime/                  # Linux runtime rootfs build
third_party/              # Vendored dependencies
design/                   # Design assets
```

## Architecture

`ICodeGateway` is the single seam between Compose UI and backend implementations. It exposes:

- **`ICodeSnapshot`** — immutable state, observed as a `StateFlow`
- **`ICodeIntent`** — user actions dispatched from screens
- **`ICodeEffect`** — one-shot events (toasts, navigations, confirmations)

`AndroidICodeGateway` builds one coordinator per feature and routes each intent to it. A debug
capture launch uses `CaptureGateway`, which draws fixtures and ignores every intent so no fixture
reaches real storage. Structural consistency is enforced by two guard scripts with shrinking
allowlists: `tools/check-ui-consistency.sh` and `tools/check-backend-consistency.sh`.

## Screen capture

Connect an authorized device, build the debug APK, then run:

```bash
tools/capture-figma-screens.sh
```

It installs the APK, opens every route from `Route.kt` with capture extras that draw fixed fixtures
instead of live data, and writes screenshots to `artifacts/screens/` plus labeled review sheets to
`artifacts/review/`. Fixtures live in `app/src/debug` only; a release build ignores them.

## Documentation

| Document | What it covers |
|----------|---------------|
| [plan.md](plan.md) | Overall development plan |
| [docs/prd.md](docs/prd.md) | Product requirements |
| [docs/architecture.md](docs/architecture.md) | Architecture decisions |
| [docs/user-stories.md](docs/user-stories.md) | User stories |
| [docs/layout.md](docs/layout.md) | Layout and interaction design |
| [docs/assistant-koog-plan.md](docs/assistant-koog-plan.md) | AI assistant design |
| [docs/runtime-foundation-plan.md](docs/runtime-foundation-plan.md) | Linux runtime design |
| [Todo.md](Todo.md) | Implementation tracker |

The `docs/` folder also contains implementation plans for individual features (workspace storage,
terminal, Git separation, UI splitting, etc.) — useful as historical design records.
</content>