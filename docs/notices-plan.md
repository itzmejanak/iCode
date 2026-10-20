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

- At the bottom right, 88 dp above the screen's foot (`IcodeSizes.FabClearance`). That clears
  every foot bar (48 dp), the keys rows (64 dp, or 72 dp with Compact Layout off) and the file
  explorer's + (56 dp, 16 dp from the edge).
  Over the keyboard while it is up, as everything is.
- A pill: the bell in the tone's colour, then the notice's one line, cut with "…" to the width
  it has (at most 75% of the screen).
- Shown for 3 s for Done, 4 s for Heads-up and Info, and 6 s for a Problem. A new notice replaces
  the line, and a badge on the bell counts those that came while it showed.
- TalkBack reads it as it reads a toast (a polite live region).
- Not shown while the Notifications page or a notice's sheet is open: the notice is there already.
- A tap: see **Q1**.

### 2.3 A notice's sheet

An `IcodeBottomSheet` over the screen:

- the tone's icon and the title;
- when, and the tone;
- the detail, in full. A failure's own words are in the mono face, as the terminal's text is.
- **Copy** (title and detail, for a bug report or a search) and **Delete**;
- **All Notifications**, when the sheet was opened from the bell.

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
| **Messages** (new) | `LOW`: no sound, no pop-up | Every kept notice that is unread, up to the 20 newest, grouped under one "iCode · Messages" summary |
| **Problems** (new) | `HIGH`: pops up | A Problem that arrives while iCode is out of view (the same test `WorkNotifications` uses for task failures) |

- A tap opens iCode with that notice's sheet (a new `Arrival.Notice`).
- A notice read, deleted or cleared in iCode is withdrawn from the shade.
- Swiping one away in the shade leaves it unread in iCode, as a mail app does.
- Without the notification permission, the bell and the page still work.
- The user can change either channel in Android's settings.

### 2.6 Done

See **Q2**. Copying on Android 13 and later stays as it is: Android shows its own confirmation, so
iCode adds none.

## 3. Owner's decisions

| | Question | Recommended | Why |
|---|---|---|---|
| **Q1** | What a tap on the bell opens | **The notice's sheet**, over the screen, with **All Notifications** in it | One tap reaches the detail, and Back returns straight to the work. The page is a tap further, from the sheet or from the launcher's bell. The alternative, the page first, takes two taps in and two Backs out |
| **Q2** | Where Done goes | **The bell only**: not kept, not in the shade | "Saved" on every save would push real problems off the page, and would fill the shade with what the user just did. Android asks for notifications to be kept for what needs the user |
| **Q3** | What the bell shows | **The bell and the notice's line** | With the bell alone, every message ("Install the runtime first") needs a tap to be read, which a toast never did |

## 4. How

| Layer | Part |
|---|---|
| Domain | `NoticeTone`. `AppNotice.Message(id, at, read, tone, title, detail)`. `ICodeEffect.Notice(notice)` replaces `Message`, and the "Saved" and "Commit created" toasts. `ICodeIntent.Notify(tone, title, detail)` for what the UI raises. `ICodeIntent.ReadNotice(id)` |
| Data | `ICodeStateHolder.notify(tone, title, detail)` replaces `message`. `report` keeps fallback and words apart. Each of the 99 sites chooses its tone. `NoticeStore` stores the new kind (100). New `NoticeNotifications` holds the two channels, the group and withdrawal. `MainActivity` gets `ACTION_SHOW_NOTICE` |
| UI | `NoticeHost` at the app's root, beside `FileViewerHost`: the bell and the sheet. `NoticeRow` and `NoticeSheet` serve the page and the bell alike. The 9 toasts go |
| Guard | `tools/check-ui-consistency.sh` refuses `Toast.makeText` anywhere (rule `toast`, none allowed), so a toast cannot come back |

## 5. Steps

1. **N1 Domain and store**: tones, the notice kind, the store's new kind and its limit, with tests (`NoticeStoreTest`, the rules for which notices are kept and posted).
2. **N2 The 99 sites**: each with its tone; `report` with both parts.
3. **N3 UI**: the bell, the sheet, the page's rows; the toasts removed.
4. **N4 The shade**: the channels, the group, the arrival, withdrawal.
5. **N5 Checks**: the guard, type-checks, the tests touched, docs as built (`layout.md`), then
   push.
