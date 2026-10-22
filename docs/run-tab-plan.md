# Run — the Terminal Panel's Run Tab

Status: **built (2026-10-18)**, as the owner decided it the same day; the phone check is §3. It moves Build & Run out of the Explorer ([android-toolchain-plan.md](./android-toolchain-plan.md) §4.4) into a tab of its own, for more than Android.

Owner ask (2026-10-18, verbatim): "Alo put the build icon on explorer left side bar I think we can make build page not just for Android instead of we can make one icon on left sidebar and inside that page there will be user command for now for Android there will be build and state management of that will be there instead of dumpling things if explorer. Okay more good idea instead of putting run icon there we can put it inside the terminal page one new tab I think make sure everything is perfect and 100% accurate. Regarding the explorer ui run icon placement and design I think you need to ask me about that will options and design"

## 1. The owner's answers (asked with mockups, 2026-10-18)

| Question | Answer |
|---|---|
| Where Build & Run lives | **A Run tab in the Terminal panel**, beside Terminal, Problems and Output. It is reached through the Terminal icon on the Explorer's rail. The Explorer header carries nothing for it |
| What it is called | **Run**, Android Studio's word |
| What it lists | **What iCode detects, and the user's own commands, from the start**: each Android app in the workspace, and commands the user saves (a name and a command line) |
| How a run looks | **Flat rows, with output in the Terminal**: name and kind, the command, the state line. A trailing ▶, or ■ while it runs. A tap opens the live output as a Terminal tab |

## 2. Decisions

### 2.1 One model: run configurations

- **`RunConfiguration`** (domain) is what the Run tab starts:
  - **`AndroidApp(project)`** is found by iCode: every application module of the workspace's Gradle build. `app` comes first, then the rest by name. Its id is `android:<module>` and its command is `./gradlew :<module>:assembleDebug`.
  - **`Command(id, name, command)`** is saved by the user.
- **Every run is a managed process** (`RuntimeProcesses`), as before. Its output is that process's Terminal tab, its Stop is the process's Stop, and its ports are the ones its output names. Nothing else runs builds or commands.
- **`RunRecord`** is a configuration's last run in this iCode process: its process, what followed an Android build (`Installing`, then `Opened`), and why it stopped short. **`RunState`** holds the open workspace's configurations and the records, and replaces `AndroidAppsState`.
- **`RunRules.status`** is the row's state, pure:

  | State | Shown as |
  |---|---|
  | Never run | "Never run" |
  | Running | "Building · 1m" for an Android app, "Running · 1m · port 5173" for a command |
  | Installing | "Installing · Android asks you to confirm", with Install |
  | Opened | "Opened" |
  | Ended | "Exited 0" in green, "Exited 1" in red, "Stopped" muted: from `ProcessRules.stateLabel`. "Ended" when its process was removed elsewhere (the Assistant's process list) |
  | Failed | "Failed · " and the reason: why it did not start, Gradle's, or Android's |

- **Running again** forgets the configuration's previous ended process and its log first. One configuration keeps one output.

### 2.2 Who does what (data)

- **`RunCoordinator`** (`data/run`) owns `RunState`.
  - It follows the open workspace and lists its configurations.
  - It starts, stops and records runs.
  - It saves and deletes commands, and follows workspace renames and deletes.
  - The Run tab and the assistant's `run_android_app` both start runs here. Both save and delete
    commands here too (§2.5).
- **`AndroidApps`** is the Android kind, with no state of its own. It starts the Gradle build as a managed process. When the build ends, it lists Gradle's problems in Problems, then installs and opens the APK, and reports each step back. Its failures include Gradle's own "What went wrong" line.
- **`RunCommandStore`** keeps the saved commands, per workspace, in iCode's own storage (`files/run-commands.json`), not in the project. It is iCode's settings for that workspace, as Android Studio keeps run configurations in its own workspace files unless shared. A rename moves them and a delete forgets them, with the workspace's other settings.
- **A saved command runs in bash at the workspace's top**, with the login environment, as `start_process` does.

### 2.3 The Run tab (UI)

- The panel's header reads **Terminal · Run · Problems · Output**. On Run, the header's **+** adds a command; it is disabled while no workspace is open. Opening Run never starts a shell.
- **Rows** are flat, divided by hairlines:
  - the name and its kind ("Android app", "Command"), the command in mono, then the state line;
  - a trailing ▶, or ■ while it runs or waits on Android's install confirmation;
  - a tap opens the run's output, once it has run: the Terminal tab of its process;
  - a long press on a saved command offers Edit and Delete, and Delete asks first.
- **Before an Android app runs, ▶ asks for what is missing,** as the Explorer's ▶ did:
  - without the Android SDK, a dialog offers Open Runtime Tools;
  - without "Install unknown apps", the tab shows the shared permission explanation. Once Android's page grants it, the run starts.
- **Empty:**
  - no workspace open: "Open a workspace to run its apps and commands";
  - nothing found and nothing saved: "Android apps in this workspace appear here. Save a command with +."
- **Removed from the Explorer:** the header's ▶, its notice, its permission explanation and its SDK dialog.

### 2.4 Not done, and why

| | Why |
|---|---|
| Kinds beyond Android apps detected (npm scripts, Gradle tasks) | One `RunConfiguration` kind each, later; a saved command covers them now |
| Commands shared through the project (`.run/`, `.vscode/tasks.json`) | The owner chose iCode's own commands; a project file is a separate decision |
| Arguments, environment, working folder per command | A command line can set them (`cd sub && PORT=3000 npm run dev`) |

### 2.5 The assistant manages the commands too (owner, 2026-10-22)

The owner: the user adds, edits and deletes the Run tab's commands; the assistant should do the
same on the user's word, with nothing built beside what the tab already has.

- **One path.** `RunCoordinator.saveCommand` and `deleteCommand` are what the tab's dialogs
  (`SaveRunCommand`, `DeleteRunCommand`) and the assistant's tools both call. A command the
  assistant saves or deletes is on the open Run tab at once.
- **The store refuses** a blank command, and an id its workspace has no command by. The tab's
  dialog never sends either, and a model's call cannot slip one through.
- **Three tools** (`data/assistant/tools/RunTools.kt`, beside `run_android_app`):

  | Tool | Kind | What it does |
  |---|---|---|
  | `list_run_commands()` | read | The tab as the user sees it: each Android app found (never changed or deleted), then each saved command with its id, name and command line |
  | `save_run_command(command, name, id)` | change | Without `id`, adds a command; with one, changes it, keeping its name or command line where not given. A blank name is the command's first word, as in the dialog. Saving does not run it |
  | `delete_run_command(id)` | change | Deletes it. A run still going keeps running, in its Terminal tab, as when the user deletes one |

- **Asked first, as a change.** With Ask Before Changes on, the approval reads "Run tab: add
  “dev” · npm run dev" (or change, or delete). Plan and Ask modes offer only the list.
- **Not added:** a tool to start a saved command. `run_command` and `start_process` already run any
  command, and ▶ stays the user's.

## 3. As built

| Where | What |
|---|---|
| `domain/RunConfigurations.kt` | `RunConfiguration`, `RunRecord`, `RunState`, `RunStatus`, `RunRules` |
| `domain/AndroidApps.kt` | `AndroidProject`, `AndroidAppStep`, `GradleIssues` (problems, and `failure`: "What went wrong") |
| `domain/ICodeIntent.kt` | `RunIntent`: `StartRun`, `StopRun`, `ConfirmAndroidAppInstall`, `SaveRunCommand`, `DeleteRunCommand` |
| `data/run/RunCoordinator.kt`, `RunCommandStore.kt` | §2.2, §2.5 |
| `data/assistant/tools/RunTools.kt` | `run_android_app`, `list_run_commands`, `save_run_command`, `delete_run_command` (§2.5) |
| `data/android/AndroidApps.kt` | The Android kind behind `AndroidBuilds`; `AndroidProjects.apps` lists every app module |
| `ui/screens/tools/RunPanel.kt`, `TerminalScreen.kt` | §2.3; `ToolPanelTab.Run` |
| Removed | `ui/screens/workspace/AndroidBuildAndRun.kt`, and the Explorer's ▶, notice, permission explanation and SDK dialog |

Tests: `RunRulesTest`, `RunCommandStoreTest`, `RunCoordinatorTest` (with `RuntimeProcesses` and a fake Android kind), `RunToolsTest` (the assistant's tools over the same coordinator), `GradleIssuesTest` (the owner's phone log), `AndroidProjectsTest`, `TerminalPanelLifecycleTest`.

**On the phone, open:** a saved `npm run dev` runs, shows its port and stops; an Android app builds, Android's confirmation opens, Install opens it again after leaving iCode, and ■ abandons it; a rename keeps the commands; asked to, the assistant adds, changes and deletes a command, and the open Run tab shows each at once.
