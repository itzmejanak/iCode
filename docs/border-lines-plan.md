# Border Lines and One Rhythm: Every Theme Kept Readable, Every Page Aligned

Owner's requests (2026-10-19):

1. A setting in Appearance to turn the cards' border lines on and off, right in every theme, every
   screen and every case.
2. Then: not just cards — the whole UI, across the pages, every aspect addressed so it fits best
   and is consistent: everything aligned.

Owner's decisions (2026-10-19), from mockups of the Launcher and Settings in each theme, then of
the lines and the spacing across pages:

| Question | Decision |
|---|---|
| How cards stand apart without lines | **Tonal 1.12**: each theme's tones are derived so a card and its page are at least 1.12 : 1 apart |
| What the toggle removes | First **card outlines only**; then **every edge** (§2) |
| Default | **On**, as today |
| Name and place | Appearance › UI Density, a second row under Compact Layout: **Border Lines**, "Lines around cards, menus and bars" |
| Line weight | **All 1 dp**: the top bar's line was 0.5 dp |
| Spacing | **One rhythm**: every page 16 dp from the screen's edge; 8 dp between cards and under a section label; 20 dp between sections (§5) |

## 1. What stands today (verified)

- Every card is `IcodeCard`: the surface tone, a 1 dp line in `Outline`, the small corner. 39 calls;
  list rows (`IcodeListRow`), setting rows (`SettingRow`), text fields (`IcodeTextField`), the
  dropdown field (`IcodeSelect`) and notices (`NoticeCard`) are drawn with it.
- Without its line, a card is told from its page by tone alone, and the three themes keep them
  close (WCAG contrast; Material 3 and iOS for comparison):

| | Card on page | Chip on card | Outline on page |
|---|---|---|---|
| Dark | 1.085 | 1.119 | 1.49 |
| Light | 1.078 | 1.206 | 3.63 |
| AMOLED | 1.067 | 1.108 | 4.13 |
| Material 3 dark, page → container | 1.141 | | |
| iOS light, grouped page → cell | 1.116 | | |
| iOS dark, black page → cell | 1.234 | | |

  AMOLED's cards (`#0B0B0B` on black) and Light's (white on `#F5F7F3`) nearly vanish without the
  line; the line is what draws them.
- Beyond cards: popup menus drew a 1 dp outline; the top bar a 0.5 dp line under it; the
  clipboard, folder-choice and Runtime Tools bars a 1 dp line over them. Document Recovery's foot
  bar, the same band as Runtime Tools', had none, nor the terminal panel's header.
- A dialog over a scrim (`DialogScaffold`, the palette's `scrim`) had no caller: dialogs are
  Android's, dimmed by the platform. `IcodeDropdown.kt` held three deprecated shims with no caller.
- Cards on a sheet were the sheet's own tone unless a screen raised them by hand (the processes
  list, the attach tiles).
- Spacing, page by page: Settings, Problems, file search and the chat stood 12 dp from the screen's
  edge; Runtime Tools, Clone Repository, Git Credentials, Source Control's gates, the pull progress
  and the terminal's runtime gate 20 dp; the assistant's set-up, the clone and runtime progress
  pages and the permission pages 24 dp; the rest 16 dp. The top bar's title stood 12 dp in, its
  icons 24 dp. Items stood 8 to 20 dp apart; 25 section labels in 15 files carried their own
  padding; side-by-side actions stood 10 dp apart, off the 4 dp scale.

## 2. What the toggle removes

Off removes every line that marks where a surface ends, because tone now marks it:

| Goes while off | Where |
|---|---|
| A card's outline, a dialog's | `IcodeCard` without a `borderColor`; `IcodeDialogCard` is one |
| A popup menu's | The overflow and action menus, the dropdown field's menu: one surface, `IcodeDropdownMenu`; their shadow stays |
| The top bar's line | `IcodeTopBar`, and the terminal panel's own header, which had none |
| The line over a bar at the foot | The clipboard bar, the folder-choice bar, and the setup pages' and Runtime Tools' bar (`IcodeActionBar`, since [onboarding-polish-plan.md](./onboarding-polish-plan.md)); Document Recovery's foot (`IcodeFootBar`) |

Every line that says something stays:

| Kept, always | Why |
|---|---|
| Text fields, the dropdown field, outlined buttons, the colour slider | A control's edge: without it a field is an unmarked box and a Secondary button reads as text |
| A selected row or theme, an open dropdown, a held CTRL, the command palette's field | State: the accent line is what says it |
| Warning and error notices, the runtime's failure card | Tone: the coloured line is the message |
| Separators between rows, and before a menu's destructive actions | They divide what is inside a surface, not where it ends |

`edgeLine` is the one place this is decided: the outline while Border Lines is on, nothing while it
is off. `IcodeCard(borderColor = null)` — the default — draws it; any colour given is drawn always.
The accent line of a selected card is drawn over the same place either way, so nothing moves when it
comes or goes. `EdgeLine()` is a bar's line; `Divider()` stays the separator.

A card stands a step above what it is on: on a page the surface tone, on a sheet the raised one
(`LocalIcodeCardRaised`, given by `IcodeBottomSheet`). Without lines a card on a sheet of its own
tone would vanish; with lines it is the same rule the processes list and the attach tiles already
followed by hand.

## 3. The tones without lines

Off, the palette is derived from the theme's own, by one rule (`IcodePalette.borderless()`):

1. A card and its page at least **1.12 : 1** apart. Dark and AMOLED move their cards lighter; Light's
   cards are white already, so its page moves down, and the editor's deeper background with it,
   keeping its own step.
2. Every other surface keeps its own step from what it sits on: chips and selected rows (elevated)
   from the card and the page, switch and progress tracks from the chip and the card.
3. Every colour drawn on a moved surface — text, muted text, amber, green, blue, error, outline —
   reads at least as well as before, or at least as WCAG AA asks (4.5 : 1 for text, 3 : 1 for the
   outline, a control's edge).
4. A tone moves only in lightness (OKLCH, in steps of 0.01), its hue and chroma kept, as
   `Oklch.readable` already moves file icon colours.

The accent is the user's or the theme's, never moved. Steps of 0.01 land just past the minimum:
Dark 1.138, Light 1.147, AMOLED 1.140 (the prototype's figures; the build's are in §6.1).

## 4. Where it goes

| Part | Change |
|---|---|
| Domain | `AppearanceSettings.borderLines` (default on); `setBorderLines` |
| Data | `appearance_border_lines` in the preferences store |
| Design system | `IcodePalette.of(theme, borderLines)`, the one place a theme becomes a palette (the app and the theme swatches both read it); `IcodePalette.borderless()`; `IcodeTheme(borderLines)`; `edgeLine`, `EdgeLine()`, `LocalIcodeCardRaised`; `IcodeDropdownMenu`; `IcodeFootBar` |
| Screens | Appearance's row; the theme swatches show each theme as it will look; selected rows and swatches give only their accent line; the terminal header's line |
| Settings search | Appearance found by "border", "border lines", "outline", "card"; Editor's terms follow its page (Format On Save is gone; Keys Row, Keyboard Suggestions, Auto-Close Brackets and Git Change Markers are there) |
| Tests | The derivation's rules in every theme; the setting's default; search terms |
| Docs | `layout.md` §4.1, §4.4 and §4.5 |

## 5. One rhythm

The decision's dp are Compact Layout's, the default; with it off, every step from 8 dp up is 4 dp
wider, the rhythm with them. Each rule is a token, so no page states a number of its own:

| Token | Compact | Rule |
|---|---|---|
| `Gutter` | 16 dp | A page's content from the screen's edges |
| `ItemGap` | 8 dp | Between the cards and items of a group; under a group's label; between side-by-side actions |
| `SectionGap` | 20 dp | Between a page's groups |
| `SectionBreak` | 12 dp | Above a group's label inside a run of items `ItemGap` apart (a list, a sheet), making its gap a `SectionGap` |
| `IconButtonGutter` | 4 dp | A bar's edge beside an icon button: its 48 dp target is 12 dp wider each side than its 24 dp icon, so the icon lines up with the gutter, as Material's top app bar has it |
| `RowGutter` | 8 dp | Beside a list of rows: their text lines up with the gutter, their selection drawn `RowInset` wider |

Two components carry them:

- `IcodePageColumn`: a page's content under its top bar, scrolling, the gutter around it, its
  groups a `SectionGap` apart.
- `IcodeSection`: a group — its label, if it has one, then its items an `ItemGap` apart. A labeled
  field (`LabeledTextField`) is a section of one; a field with a note under it is a section of two.

Where each page went:

| Pages | Now |
|---|---|
| Appearance, Editor, Terminal, Storage, Workspace, About, Language Services and a service, Assistant and a provider, Source Control's settings, Commit and Push, Clone Repository, Git Credentials, Runtime Tools, Source Control's gates, the assistant's set-up | `IcodePageColumn` of `IcodeSection`s; Storage and About were lazy lists of a fixed few items |
| Runtime Environment | The same rhythm without scrolling, so Repair and Reset stay at its foot |
| Settings, the Launcher, Notifications, Document Recovery, Source Control, Problems, file search, Output, the chat, its composer | Lists on the gutter; where they hold cards, an `ItemGap` apart. The chat's entries keep 12 dp — a conversation, not a page of groups |
| The Explorer, the Command Palette | Rows on `RowGutter`: their text on the gutter |
| The clone and runtime progress pages, the permission pages, the pull progress, the terminal's runtime gate | The gutter |
| The top bar, the terminal's header, the clipboard bar | Icons on the gutter's keyline (`IconButtonGutter`); a title without Back starts at the gutter, one after Back at 52 dp (was 60) |
| Sheets | A label that starts a new group takes `SectionBreak` |

Kept as they are: code, terminal and diff surfaces; the dense trees and lists (the Explorer, Source
Control's changes, the Command Palette), whose group labels keep their own spacing; the inside of
cards and dialogs; a centered empty state's 24 dp.

## 6. As built

### 6.1 The tones without border lines

As the build derives them (`IcodePalette.borderless()`, printed from the Kotlin code); unlisted
colours are the theme's own.

| Theme | Page | Editor | Card | Chip | Track | Moved to stay readable |
|---|---|---|---|---|---|---|
| Dark | `0A0D0B` | `060807` | `131812` → `171D16` | `1C231B` → `212820` | `232B22` → `283027` | muted text `5F6C60` → `657266`, outline `2A332A` → `2F382F` |
| Light | `F5F7F3` → `EEF0EC` | `ECEFE9` → `E5E8E2` | `FFFFFF` | `E7EBE5` → `E0E4DE` | `DCE3DA` → `D5DCD3` | muted text `647467` → `5E6E61`; the comment grey `606D60` → `5D6A5D` (comments, tag punctuation, quotes); the bright terminal colours 9–15, a little darker (`D1402F` → `CA3929` …) |
| AMOLED | `000000` | `000000` | `0B0B0B` → `141414` | `151915` → `1C201C` | `1E241D` → `252B24` | nothing |

Card on page: Dark 1.085 → 1.138, Light 1.078 → 1.147, AMOLED 1.067 → 1.140.

### 6.2 What changed beyond the plan

Making every palette's tests cover both palettes of each theme found what one palette per theme
had let stand:

- **Syntax colours** are kept at 4.5 : 1 on the editor and its current line by construction: a
  colour that reads is the chosen one, exactly (the pinned values still hold), and one that would
  not — Light's comments on its darker editor, 4.40 : 1 — has only its lightness moved.
- **The editor's TextMate theme** was registered once per kind ("icode-light"), so turning Border
  Lines off kept the first theme's colours until a restart. It is named by what its colours
  follow — the kind, the editor and its current line — so each palette's theme is its own.
- **The terminal's sixteen colours** are part of the palette, so the same rule keeps them readable
  on the terminal's background.
- **File icon colours** were cached by theme kind: a borderless palette would have taken the
  bordered one's. They are cached by the surfaces they are checked against.
- **Settings search**: Appearance's subtitle says "borders"; Editor's says "keys", and its terms
  follow its page (Format On Save, removed, is no longer found there).

Then, carrying the lines and the rhythm across every page:

- **Menus** drew their outline on the list inside the menu; `IcodeDropdownMenu` gives it to the
  menu's own surface, and the overflow menu and the dropdown field's menu share it, as they shared
  every other setting already.
- **Removed**, having no caller: `DialogScaffold` and the palette's `scrim` it alone used; the
  three deprecated shims of `IcodeDropdown.kt`; the top bar's `showDivider`, which no page turned
  off.
- **The top bar's alignment**: its title stood 12 dp in, its icons 24 dp, while pages stood 16 dp.
  Its icons now sit on the gutter's keyline and its title, without Back, on the gutter — Material's
  top app bar's own measures.
- **One foot bar**: Runtime Tools and Document Recovery each drew the same band at their foot;
  Document Recovery's lacked the line. Both are `IcodeFootBar`. (Since 2026-10-24 Runtime Tools'
  foot is the explorer's one-row bar, `IcodeActionBar`: [onboarding-polish-plan.md](./onboarding-polish-plan.md).)
- **Source Control's three gates** each repeated their page; one `GatePage` holds it.
- **Runtime Environment** did not scroll, its rebuilds pinned to its foot: it keeps both, on the
  rhythm.
- **Terminal Settings** set `editingShell` twice on Edit; once.

### 6.3 How it was checked

| Check | What it showed |
|---|---|
| `IcodePaletteTest` | With lines each theme is its own palette; without, a card 1.12 : 1 from its page in every theme, every other surface its own step, nothing drawn on a moved surface (text, status colours, outline, the terminal's colours) reading worse unless it meets WCAG AA, the accent untouched |
| `SyntaxColorsTest`, `EditorThemeTest`, `FileTypeIconsTest`, `EditorColours` | Every palette the app draws (`everyPalette`, six): syntax colours 4.5 : 1, each TextMate theme loading in tm4e, icons 3 : 1. `EditorColoursTest` needs joni and runs under Gradle and on the phone |
| `SettingsCatalogTest`, `SettingLimitsTest` | Border Lines found on Appearance; Editor's controls found on its page; on by default |
| `AndroidWorkspacePreferencesRepositoryTest` | The setting survives a restart (Gradle; DataStore is not on this machine) |
| The whole suite | 825 tests; the one failure is `TreeSizeTest`'s unreadable directory, which root can read |
| Type-checks | The backend; every screen check; the editor against Sora; and a check of the thirteen changed screens no other check compiled (About, Language Services and a service, Notifications, Document Recovery, the permission, clone, clone progress and pull progress pages, the Command Palette, Conflict Resolution, the empty editor, Find) |
| `DropdownMenu`'s `border` | Present in Material 3's `DropdownMenu` (checked in the 1.9.0 desktop classes, the same `commonMain`) |
| Guards | Backend and UI consistency; every changed file within its line budget |

The design-system tests run against Compose's desktop jars: Compose's colour conversion needs
`androidx.collection`, which cannot be fetched here, and a stand-in for the three members it
calls, outside the repository, runs them. No screen was drawn: the Android build's dependencies
cannot be fetched here.

### 6.4 On the phone, open

- Appearance › Border Lines off, in each theme: the Launcher, Settings, Source Control, a menu, a
  dialog, a sheet with cards (the model's options, Processes), the clipboard bar, Document
  Recovery's foot, the editor and the terminal; then on again, without a restart.
- Light without lines: a comment in the editor; the terminal's `ls --color`.
- A selected row, the open dropdown and a warning notice keep their lines with lines off.
- Every page's left edge against its top bar's title and Back arrow; Settings' search with its
  results; a settings page's groups (8 dp within, 20 dp between); Compact Layout off, the same
  rhythm 4 dp wider.
