# Design and navigation

iCode currently uses a phone-first interface. One app page or workspace tool is dominant at a time, and global sheets and floating controls sit above that surface. This page documents the current product; future wider-screen layouts remain in the canonical roadmap.

## Status at a glance

| Area | Status | Scope |
|---|---|---|
| Phone information architecture and navigation model | Done | Implemented and covered by navigation tests. |
| Final visual, animation, touch, and TalkBack checks on a phone | Needs Verification | Source and JVM tests do not prove behavior on every device. |
| Tablet and landscape adaptive layouts | Not Started | The current phone shell is used at every width. See the [adaptive-layout roadmap](./roadmap.md#adaptive-tablet-landscape-foldable-and-multi-window-shell). |

## Information architecture

### Home

Home is the entry point outside a workspace. It provides:

- **Start** actions for Import, Clone Repository, and Workspaces.
- Up to five recently opened workspaces.
- Settings.
- Runtime status and installation entry points.
- Home Terminal, Assistant, and Notifications actions in the status area.

**Private** and **Shared** are places in the file explorer. Private is iCode-owned app storage and contains workspaces. Shared is primary phone storage and is where Import starts. They are not workspace types.

Terminal and Assistant can also open without a workspace. A Home terminal starts in the Linux login home directory. A Home assistant chat has no workspace and uses the login home directory for relative paths. Run, Problems, Output, language services, workspace MCP configuration, and workspace instruction files remain workspace features. After a workspace opens, the outside-workspace Terminal and Assistant pages are removed from the Back path.

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/ui/screens/entry/LauncherScreen.kt`
- `app/src/main/java/dev/icode/mobile/ui/screens/entry/FilesScreen.kt`
- `app/src/main/java/dev/icode/mobile/navigation/Navigation.kt`

### Workspace tools and panels

A workspace has six main tools:

1. Explorer
2. Editor
3. Search
4. Source Control
5. Assistant
6. Terminal

Terminal contains the **Terminal**, **Run**, **Problems**, and **Output** tabs. These tabs are panels within one tool, not separate pages in the navigation stack. Search similarly has two main faces, Search in Files and Quick Open.

Each tool keeps its own page history. Switching tools restores the selected tool where it was left. Opening a page that already exists in that tool's history returns to the existing entry instead of adding a duplicate. Tool recency is also unique, so repeatedly alternating between two tools does not create an endless Back loop.

Compose state is retained while a navigation entry remains live. Editor documents additionally own long-lived native editor sessions. Assistant scroll state is saveable for its live page. Terminal scroll is retained by the live terminal session, but text selection is intentionally not retained. Persisted navigation state has JVM coverage; restoration of every native view detail after process death still needs phone acceptance.

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/navigation/Route.kt`
- `app/src/main/java/dev/icode/mobile/navigation/Navigation.kt`
- `app/src/main/java/dev/icode/mobile/ui/NavigationHost.kt`
- `app/src/main/java/dev/icode/mobile/navigation/NavigationSaving.kt`
- `app/src/test/java/dev/icode/mobile/navigation/NavigationTest.kt`
- `app/src/test/java/dev/icode/mobile/navigation/NavigationSavingTest.kt`

## Floating controls

### Tool dock

The floating tool dock appears on a workspace tool's main screen. It is absent outside a workspace and on Notifications. A sheet over a tool's main screen does not change that ownership, while a sheet over an inner page does not expose the dock.

The closed dock is a tools-grid pill. Opening it fans out all six tools. Choosing a tool restores that tool's history and closes the dock. Android Back, an outside tap, the dock button, or six seconds of shown inactivity also closes it. A second tap within Android's double-tap interval switches to the previously used tool.

Badges summarize current state, including unsaved editor documents, Source Control changes, terminal problems or running work, and Assistant attention. Suggestions can visually point to a relevant tool, but they are deterministic hints and never switch tools automatically.

TalkBack exposes named actions for tool destinations and quick switch. The final focus order, announcements, and gesture behavior need physical-device verification.

### Floating pills

Undo, Notifications, and Tools share the floating stack. A user can drag them after touch slop and dock them to the nearest screen edge. Their normalized edge positions are stored as app appearance preferences. Placement avoids overlap and reserves the relevant system-gesture area while the pills are visible.

At rest, a pill sits partly into its edge and fades. Touch wakes it. These are implemented interaction rules, but their final appearance and movement on different phone sizes should not be treated as device-verified.

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/ui/dock/ToolDock.kt`
- `app/src/main/java/dev/icode/mobile/domain/ToolBadges.kt`
- `app/src/main/java/dev/icode/mobile/domain/ToolSuggestions.kt`
- `app/src/main/java/dev/icode/mobile/designsystem/IcodeFloatingPills.kt`
- `app/src/main/java/dev/icode/mobile/designsystem/IcodeDockedPill.kt`
- `app/src/main/java/dev/icode/mobile/domain/FloatingPills.kt`

## Sheets, overlays, and pill state

Bottom sheets are iCode's global in-app modal pattern. The root sheet host lets any composed screen present a sheet while retaining the caller underneath. Several sheets can be open; the newest is on top. Sheet routes also keep the preceding route composed and visible beneath the overlay.

A sheet blurs and dims what is below it. An outside tap, swipe-down where supported, a close action, or Android Back closes the top sheet.

The floating stack follows these rules:

- Once shown, it stays composed. Opening a sheet no longer disposes the pills.
- Pills are hidden while any sheet is open and while the Notifications page is visible. Hidden pills have no placement, touch target, semantics, or system-gesture exclusion.
- Pill-local state survives hiding. This includes an expanded dock, resting state, drag state, and saved edge position.
- Visible-duration timers pause while pills are hidden and resume with the remaining duration. The dock's six-second close timer, the Undo wake interval, and kept-notice wake or ring delays use this rule.
- The short touch-to-rest timer intentionally continues while hidden. A pill that opens its own sheet can therefore return in its resting state instead of restarting a full wake interval. Momentary confirmation timing also remains immediate because delaying it until a later context would be misleading.
- Nested sheets keep pills hidden until the final sheet closes.

Composition and timer arithmetic have JVM tests. The first rendered sheet frame, animation order, real touch handling, and TalkBack behavior remain **Needs Verification** on a phone.

Undo opens a sheet that explains the most recent explorer change. Looking at the sheet does not alter files; Undo and Redo run only from their actions. Notifications distinguish work that needs attention, completed information retained in history, and momentary confirmations. Needs-attention items ring and count. Completed informational items remain in Notifications without ringing or counting.

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/ui/IcodeApp.kt`
- `app/src/main/java/dev/icode/mobile/designsystem/IcodeSheetHost.kt`
- `app/src/main/java/dev/icode/mobile/designsystem/IcodeFloatingStack.kt`
- `app/src/main/java/dev/icode/mobile/designsystem/IcodeFloatingPills.kt`
- `app/src/main/java/dev/icode/mobile/ui/NoticeHost.kt`
- `app/src/test/java/dev/icode/mobile/designsystem/IcodeFloorsTest.kt`
- `app/src/test/java/dev/icode/mobile/designsystem/PillsShownTest.kt`

## Android Back

Back is handled from the most local visible layer outward:

1. A transient owner handles it first: the top sheet, an in-sheet page, menu, picker, selection mode, or an unsaved-settings confirmation.
2. If no transient layer handles it, an expanded tool dock closes. A sheet disables the dock's Back handler, so one Back closes the sheet without collapsing the preserved dock state.
3. An inner page pops from the current tool's history.
4. From a tool's main screen, Back returns to the previously used tool as it was left. The tool just left is removed from Back recency to prevent loops.
5. With no prior tool, Back returns to the app stack and Home.
6. Outside a workspace, Back pops app pages to Home. At Home, Android owns the final Back action.

Back does not close or discard an editor document. Screens with unsaved form values install their own confirmation before navigation can remove them.

## Design system and appearance

### Tokens

Feature UI consumes semantic tokens instead of choosing local colors and dimensions. The main token groups are colors, typography, spacing and metrics, shapes, sizes, and tones. `IcodeTheme` maps the same palette into Material 3 and exposes UI text scale, compact spacing, and border-line preferences through composition locals.

The implementation lives under `app/src/main/java/dev/icode/mobile/designsystem/`. Palette and appearance models live under `app/src/main/java/dev/icode/mobile/domain/` and the appearance settings screens under `app/src/main/java/dev/icode/mobile/ui/screens/settings/`.

### Themes and accent

The available theme families are Dark, Light, and AMOLED. The current catalogs contain 16 Dark variants, 13 Light variants, and 18 AMOLED variants. Each family remembers its selected variant. **Match Phone** follows the system light/dark mode while retaining the chosen night family. Missing or retired variant identifiers fall back to an iCode palette.

Accent color is app-wide. It can use the current variant's default, one of eight presets, or a custom six-digit RGB value. The picker reports contrast and warns below 3:1. Filled accents choose black or white foreground text based on readability. The accent reaches controls, editor state, terminal colors, and related semantic tokens; Android notification tint and the launcher icon keep the product brand color.

Palette tests check text and syntax readability and icon/accent contrast. Those checks do not replace a visual pass on every supported phone.

### Text scale and density

UI text scale ranges from 100% to 150% in 5% steps. It affects app interface typography. Editor, Markdown/code, and terminal text sizes remain independent.

Compact Layout is enabled by default. It changes spacing density, not navigation structure. In Compact mode, the shared spacing steps are 8, 12, 16, 20, and 24 dp; turning Compact off adds 4 dp to those steps. The smallest 4 dp step is unchanged. Touch targets remain at least 48 dp even in dense code-oriented surfaces.

The full text-scale matrix across all screens remains **Needs Verification** on a phone.

### Border lines

Border Lines is enabled by default. Turning it off removes one-dp lines that only mark a surface edge, such as card, menu, top-bar, sheet, and foot-bar boundaries. It does not remove control boundaries, focus and selection indicators, warning/error lines, or meaningful separators. Borderless palettes adjust surface lightness so adjacent surfaces remain distinguishable.

Palette and unit tests cover the rules. The complete Dark, Light, and AMOLED phone matrix remains **Needs Verification**.

## Loading and action vocabulary

User-started work makes the action itself busy immediately and blocks accidental repeat activation. The control keeps its label and tone. A spinner appears only after 150 ms, which avoids flashing for work that finishes quickly. Busy semantics announce “Working.” Coordinators clear busy state in `finally`, including failures and cancellation.

Other waits use a consistent vocabulary:

- Skeleton rows reserve the eventual list shape instead of showing a false empty state.
- Page waits use a loading line with concrete words and a polite live region.
- A single value uses “Checking…”.
- Measured long work uses one progress track; unknown progress is indeterminate.
- Confirmation sheets remain open and make the confirming action busy until work finishes.

See `app/src/main/java/dev/icode/mobile/designsystem/IcodeLoading.kt` and the shared action tracking under `app/src/main/java/dev/icode/mobile/domain/`.

## Notices and accessibility

A notice records its source, place, detail, raw output where relevant, available actions, and repeat identity. The place is captured when work starts, so a later completion still points to the workspace, repository, branch, or file where it began.

- **Needs attention** notices ring, count, stay in Notifications, and may become Android notifications.
- **Completed** notices remain in history without ringing or increasing the attention count.
- **Momentary** confirmations are transient.

The UI is expected to provide names for icon-only actions, state descriptions for busy controls, polite live-region updates, at least 48 dp targets, selected-state semantics, and non-color indicators for important state. The dock also provides TalkBack actions for destinations and movement. Color is not the only state signal.

These accessibility mechanisms are present in code and covered where practical by JVM tests. End-to-end TalkBack focus, announcements, drag actions, sheets, held notices, and busy controls remain **Needs Verification** on a phone.

## Wider-screen roadmap

**Status: Not Started**

Tablet and landscape adaptive layouts are not implemented. iCode currently keeps the same single-surface phone hierarchy and floating dock at wider sizes. Width classes, persistent regions, resizable panes, rails, and wider activity navigation are required by the [adaptive-shell roadmap](./roadmap.md#adaptive-tablet-landscape-foldable-and-multi-window-shell); they are not current product behavior.
