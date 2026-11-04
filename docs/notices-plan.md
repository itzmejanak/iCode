# Notices: the Bell, the Notifications Page and Android's Shade, in Place of Toasts

Owner's request (2026-10-20): replace every toast. Whatever a toast said today is told three
ways:

1. it is kept on iCode's Notifications page, where a tap shows its details in a sheet;
2. Android's notification shade shows it;
3. a bell floats at the bottom right of whatever screen is open, for a few seconds, so the user
   can go to it.

## 1. What stands (verified)

### 1.1 What iCode says today

| Source | Sites | Shown as |
|---|---|---|
| `ICodeStateHolder.message(text)` | 62 in `data/` | `ICodeEffect.Message`, a long toast in `IcodeApp` |
| `ICodeStateHolder.report(subsystem, fallback, error)` | 37 in `data/` | The same toast: the error's own words, or the fallback without them. The fallback is lost whenever the error has words |
| `Toast.makeText` in `ui/` | 9 (`IcodeApp` 4; `CopyText`, `SharedFiles`, `FileViewers`, `AssistantPickers`, `AssistantScreen` 1 each) | Toasts: "Saved", "Commit created", "Copied" (below Android 13), "No browser is available", "… cannot be handed to another app", "… cannot be shown", "No app on this phone can do that", "Five files go with a message at most…" |

The 108 fall into four kinds:

- **Failures**: "Unable to save document", "Unable to start a terminal session" and every `report`.
- **What to do first**: "Install the Linux runtime before opening a terminal", "Open a workspace
  before searching", "Choose Save, Discard or Cancel before closing …".
- **Something happened**: "bash exited with code 1", "App.kt was deleted outside iCode; its text
  stays open here".
- **Done**: "Saved", "Commit created", "Copied", "<provider> saved".

A few sites say the outcome of work that can go either way (a Git transfer's end, a storage
transfer's summary, GnuPG's outcomes). These choose their kind by the outcome.

### 1.2 What already exists

- **The Notifications page** (`Route.Notifications`, the bell on the launcher, with its dot for
  unread). It holds `AppNotice`s from `NoticeStore` (SharedPreferences, at most 50, kept until
  deleted): terminal sessions that ended with the process, and Git transfers that never finished.
  Each is a card with ×; **Clear all** is in the top bar; **Unsaved work** heads the list.
- **Android's shade**: `WorkNotifications` (progress, "Task failures", "Needs your answer") and
  the terminal's channels. A tap reaches the app through `MainActivity` → `Arrival`. Onboarding
  already asks for the notification permission.

### 1.3 Android's own guidance (developer.android.com)

- Toasts on Android 12 and later: "limited to two lines of text and shows the application icon".
  Many of iCode's messages are longer, and get cut.
- "If your app is in the foreground, consider using a snackbar instead of using a toast… If your
  app is in the background, and you want users to take some action, use a notification instead."
- Notifications are not to be used "as the primary method of communication with users", nor "to
  announce error states the app may recover from without user interaction". Importance is chosen
  "with consideration for the user's time and attention": `HIGH` makes a sound and appears on
  screen; `LOW` makes no sound.

So the bell is the in-app message: what a snackbar is for. The shade keeps the same notices, quietly,
and interrupts only when iCode is out of view.

## 2. The design

Mockup: AMOLED, Border Lines off, Ember accent. It was shared with the owner and is not kept in
the repository, as with the terminal bar's.

### 2.1 Every message is a notice of one of four tones

| Tone | Colour, icon | For |
|---|---|---|
| **Problem** | Error red, error icon | Failures. A `report` keeps both parts: the fallback as its title ("Unable to save document"), the error's own words as its detail |
| **Heads-up** | Amber, warning icon | What to do first; what was refused |
| **Info** | Accent, info icon | Something happened |
| **Done** | Green, check icon | The user's own action, done |

### 2.2 The bell (on every screen)

- At the bottom right, in the floating stack it shares with the undo pill
  (docs/explorer-gaps-plan.md §3.8): above the highest + or foot bar on screen, each of which
  reports its top edge, and at least 88 dp above the screen's foot (`IcodeSizes.FabClearance`),
  which clears the keys rows (64 dp, or 72 dp with Compact Layout off). Over the keyboard while it
  is up, as everything is. Gone while a sheet is open.
- **When a notice comes**, the bell rings once, swinging from where it hangs (0.9 s), with the
  unread count on it, drawn as the Explorer's Source Control count is (`CountBadge`, one component
  for both). No line: the owner's request (2026-11-04) — the bell says nothing in words, so a long
  title never stretches it. It is docked flush on the screen's edge, round on its open side with a
  grip (`IcodeDockedPill`, shared with the undo pill; the owner's choices, 2026-11-04), and never
  goes: it tucks into the edge to its grip, and comes out again.
  - It takes the colour of the most serious unread notice (Problem, then Heads-up, then Info); a
    Done, never kept, rings it green with no count.
  - It stays **15 s** for a Problem, **10 s** for Heads-up and Info, and **3 s** for a Done (the
    most serious unread tone's time, else the ringing one's), then tucks to its grip, which
    keeps the most serious unread tone. Swiped into the edge — past half its way, or flung — it tucks
    at once; a tap on the grip, or a pull, brings it out (TalkBack: actions). The notices stay
    unread on the page, with the launcher bell's dot.
  - A tap on it opens the one unread notice's sheet; otherwise the Notifications page. Either way
    the bell tucks.
- TalkBack reads what rang it (a polite live region on the bell).
- Not shown while the Notifications page or a notice's sheet is open: the notice is there already.

### 2.3 A notice's sheet

An `IcodeBottomSheet` over the screen:

- the tone's icon and the title;
- when, and the tone;
- the detail, in full. A failure's own words are in the mono face, as the terminal's text is.
- **Copy** (title and detail, for a bug report or a search) and **Delete**;
- **All Notifications**, to the page.

Opening it marks the notice read.

### 2.4 The Notifications page

- Every notice is a row: the tone's icon, the title, the first line of the detail, the time, and
  the unread dot. A tap opens its sheet.
- The two kinds there today become rows too. What their cards say now moves into their sheets.
  One row shape for everything.
- **Unsaved work** stays first; **Clear all** stays.
- Up to 100 notices are kept (from 50), since the page now holds every message.

### 2.5 Android's shade

| Channel | Importance | Holds |
|---|---|---|
| **Messages** (new) | `LOW`: no sound, no pop-up | Every unread message notice, up to the 20 newest, grouped under one "iCode · Messages" summary |
| **Problems** (new) | `HIGH`: pops up | A Problem that arrives while iCode is out of view (the same test `WorkNotifications` uses for task failures) |

- A tap opens iCode with that notice's sheet (a new `Arrival.Notice`); the summary opens the page.
- The two kinds found at start-up (terminal sessions and Git transfers the previous process took
  with it) stay on the page and the launcher bell's dot, as now: the user is opening iCode when
  they are found.
- A notice read, deleted or cleared in iCode is withdrawn from the shade.
- Swiping one away in the shade leaves it unread in iCode, as a mail app does.
- Without the notification permission, the bell and the page still work.
- The user can change either channel in Android's settings.

### 2.6 Done

Done shows on the bell only. It is not kept and not posted, so "Saved" on every save never pushes
a problem off the page or fills the shade. Copying on Android 13 and later stays as it is:
Android shows its own confirmation, so iCode adds none.

## 3. Owner's decisions (2026-10-20)

From the mockup, the owner chose:

| | Question | Decision |
|---|---|---|
| **Q1** | What a tap on the bell opens | **The notice's sheet**, over the screen, with **All Notifications** in it |
| **Q2** | Where Done goes | **The bell only**: not kept, not in the shade |
| **Q3** | What the bell shows | **The bell and the line first** (mockup 1), then the line folds away smoothly from left to right. **The bell stays with the unread count and an × to put it away** |

The owner then set the bell's motion (2026-10-21), from the first build: once the line has slid
away, the bell does not vanish with it. It rings with the unread count on it, as the Explorer's Git
count is drawn, and stays 10 s, with × to put it away sooner.

The rest of §2 was decided here, from Android's guidance (§1.3) and the code as it stands.

## 4. How

| Layer | Part |
|---|---|
| Domain | `NoticeTone` (in order, the most serious first). `AppNotice.Message(id, at, read, tone, title, detail)`. `NoticeRules`: what is kept, how long the bell's line shows, the folded bell's tone, what the shade holds. `NoticeIntent`: `MarkAllRead`, `Read`, `Delete`, `Clear`, and `Notify` for what a screen raises. `ICodeEffect.Notice(notice)` replaces `Message`, `GitCommitted` and `DocumentSaved` |
| Data | `ICodeStateHolder.notify(tone, title, detail)` replaces `message`. `report(subsystem, fallback, error)` keeps the fallback as the title and the error's words as the detail, through `failed(title, words)`. Each of the 99 sites chooses its tone; an outcome that can go either way (a passphrase change) chooses by it. `NoticeBoard` (in `data/notices`, out of the gateway) keeps what is said, from before anything else starts, publishes the page's list, posts to the shade and takes `NoticeIntent`. `NoticeStore` keeps the new kind, up to 100. `NoticeNotifications` holds the two channels and the group, and posts each notice under its id as its tag, so a later process withdraws what an earlier one posted |
| UI | `NoticeHost` at the app's root, inside `FileViewerHost`: the bell, with `NoticeHostState` (what it last told, whether its line shows, whether it has gone, the sheet) and `LocalNotices`. The rail's count badge became `CountBadge` in the design system, drawn on the rail and the bell alike. `NoticeViews`: `NoticeRow` and `NoticeSheet` for every kind, the bell and the page alike. The two kinds' cards (`LostSessionsNotice`, `InterruptedTransferNotice`) became rows and sheets. `MainActivity.ACTION_SHOW_NOTICE` → `Arrival.Notice`. The 9 toasts go: `copyText`, `SharedFiles` and the pickers say what they have to through `dispatch` |
| Guard | `tools/check-ui-consistency.sh`, rule `toast`: any `Toast` anywhere fails |

## 5. As built

### 5.1 Tones, as given

- **Problem**: every `report`; a terminal session that could not start; a process's output that could not be shown; a failed runtime install (its reason the detail); Git's failures ("Git operation failed", Git's words the detail); GnuPG's and the SSH client's failures in their words; a language server that would not start or stop; a provider or the assistant's settings that could not be saved; a turn that failed in a chat not shown.
- **Heads-up**: everything that asks for something first or refuses ("Install the Linux runtime before opening a terminal", "Open a workspace before searching", "Choose Save, Discard or Cancel before closing …"), a storage transfer that left items behind, and the UI's own: no browser, no app for a picker, a file that cannot be handed out or shown, more than five attachments.
- **Info**: a terminal session that exited with an error code; a file deleted outside iCode while open.
- **Done**: Saved, Commit created, a Git transfer's end, a provider saved, a passphrase changed, the runtime cache cleared, and Copied below Android 13.

### 5.2 How it was checked

| Check | What it showed |
|---|---|
| Type-checks | The backend and domain (`check-assistant.sh`). The bell, the sheet, the page and every changed screen (`NoticeHost`, `NoticeViews`, `Arrival`, `NotificationsScreen`, the file viewer, the assistant's screens, the file explorer, `SharedFiles`, `CopyText`, the design system), on Compose's desktop jars |
| Tests | The backend and domain suite: 835 tests, every one passing but `TreeSizeTest`, which fails here only because this container runs as root (a directory without permissions can still be listed). New: `NoticeStoreTest` (a message kept and read back, read one at a time, the newest 100 kept) and `NoticeRulesTest` (what is kept, the line's times, the folded bell's tone, the shade's 20). The tests that read messages now read the notice's tone, title and detail (`Said`); `AssistantCoordinatorTest` expects the workspace mark the last cycle added |
| Guards | Backend and UI consistency, line budgets; the `toast` rule was shown to catch a toast put back |

`IcodeApp` and `MainActivity` are outside every type-check here; their changes were read line by line.

### 5.3 On the phone, open

- Save with the button: the bell, green, "Saved"; the line slides away, the bell rings without a count (nothing unread), stays 10 s with ×, then slides away.
- Open a terminal before the runtime is installed: amber, kept; the line slides away and the bell rings with 1 on it. Tap it: its sheet. Again, then ×: it goes at once.
- Leave the bell alone: it goes 10 s after the line.
- Switch to a branch while changes Git would overwrite are in the way: red, "Git operation failed"; the sheet holds Git's words in mono; Copy.
- Several in a row, then the folded bell: the Notifications page.
- The shade: the same notices, silent, under "Notifications · N unread"; a tap opens the sheet, and reading it in iCode clears it there.
- Turn notifications off for iCode: the bell and the page still work.

A Problem that comes while iCode is out of view pops up (the Problems channel). No step above causes one
on purpose. Git transfers (clone, fetch, pull, push) report their end as before, on their progress
screen and, out of view, in the "Task failures" notification.
