# Card Borders: Lines On or Off, Every Theme Kept Readable

Owner's request (2026-10-19): a setting in Appearance to turn the cards' border lines on and off,
right in every theme, every screen and every case.

Owner's decisions (2026-10-19), from mockups of the Launcher and Settings in each theme:

| Question | Decision |
|---|---|
| How cards stand apart without lines | **Tonal 1.12**: each theme's tones are derived so a card and its page are at least 1.12 : 1 apart |
| What the toggle removes | **Card outlines only** |
| Default | **On**, as today |
| Where | Appearance › UI Density, a second row under Compact Layout: **Card Borders**, "Lines around cards, rows and tiles" |

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

## 2. What the toggle removes

Off removes the outline a card has for being a card. Every line that says something stays:

| Kept, always | Why |
|---|---|
| Text fields, the dropdown field, outlined buttons, the colour slider | A control's edge: without it a field is an unmarked box and a Secondary button reads as text |
| A selected row or theme, an open dropdown, a held CTRL, the command palette's field | State: the accent line is what says it |
| Warning and error notices, the runtime's failure card | Tone: the coloured line is the message |
| Popup menus | They float over cards of the same tones |
| Divider lines, the top bar's hairline | Separators between rows, not a card's edge |

`IcodeCard(borderColor = null)` — the default — is the card's own outline, drawn while Card Borders
is on; any colour given is drawn always. The accent line of a selected card is drawn over the same
place either way, so nothing moves when it comes or goes.

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
Dark 1.138, Light 1.147, AMOLED 1.140 (the prototype's figures; the build's are in §6).

## 4. Where it goes

| Part | Change |
|---|---|
| Domain | `AppearanceSettings.cardBorders` (default on); `setCardBorders` |
| Data | `appearance_card_borders` in the preferences store |
| Design system | `IcodePalette.of(theme, cardBorders)`, the one place a theme becomes a palette (the app and the theme swatches both read it); `IcodePalette.borderless()`; `IcodeTheme(cardBorders)`; `IcodeCard`'s line as §2 says |
| Screens | Appearance's row; the theme swatches show each theme as it will look; selected rows and swatches give only their accent line |
| Settings search | Appearance found by "border", "outline", "card"; Editor's terms follow its page (Format On Save is gone; Keys Row, Keyboard Suggestions, Auto-Close Brackets and Git Change Markers are there) |
| Tests | The derivation's rules in every theme; the setting's default; search terms |
| Docs | `layout.md` §4.2 and §4.4 |

## 5. As built

### 5.1 The tones without card borders

As the build derives them (`IcodePalette.borderless()`, printed from the Kotlin code); unlisted
colours are the theme's own.

| Theme | Page | Editor | Card | Chip | Track | Moved to stay readable |
|---|---|---|---|---|---|---|
| Dark | `0A0D0B` | `060807` | `131812` → `171D16` | `1C231B` → `212820` | `232B22` → `283027` | muted text `5F6C60` → `657266`, outline `2A332A` → `2F382F` |
| Light | `F5F7F3` → `EEF0EC` | `ECEFE9` → `E5E8E2` | `FFFFFF` | `E7EBE5` → `E0E4DE` | `DCE3DA` → `D5DCD3` | muted text `647467` → `5E6E61`; the comment grey `606D60` → `5D6A5D` (comments, tag punctuation, quotes); the bright terminal colours 9–15, a little darker (`D1402F` → `CA3929` …) |
| AMOLED | `000000` | `000000` | `0B0B0B` → `141414` | `151915` → `1C201C` | `1E241D` → `252B24` | nothing |

Card on page: Dark 1.085 → 1.138, Light 1.078 → 1.147, AMOLED 1.067 → 1.140.

### 5.2 What changed beyond the plan

Making every palette's tests cover both palettes of each theme found what one palette per theme
had let stand:

- **Syntax colours** are kept at 4.5 : 1 on the editor and its current line by construction: a
  colour that reads is the chosen one, exactly (the pinned values still hold), and one that would
  not — Light's comments on its darker editor, 4.40 : 1 — has only its lightness moved.
- **The editor's TextMate theme** was registered once per kind ("icode-light"), so turning Card
  Borders off kept the first theme's colours until a restart. It is named by what its colours
  follow — the kind, the editor and its current line — so each palette's theme is its own.
- **The terminal's sixteen colours** are part of the palette, so the same rule keeps them readable
  on the terminal's background.
- **File icon colours** were cached by theme kind: a borderless palette would have taken the
  bordered one's. They are cached by the surfaces they are checked against.
- **Settings search**: Appearance's subtitle says "borders"; Editor's says "keys", and its terms
  follow its page (Format On Save, removed, is no longer found there).

### 5.3 How it was checked

| Check | What it showed |
|---|---|
| `IcodePaletteTest` | With borders each theme is its own palette; without, a card 1.12 : 1 from its page in every theme, every other surface its own step, nothing drawn on a moved surface (text, status colours, outline, the terminal's colours) reading worse unless it meets WCAG AA, the accent untouched |
| `SyntaxColorsTest`, `EditorThemeTest`, `FileTypeIconsTest`, `EditorColours` | Every palette the app draws (`everyPalette`, six): syntax colours 4.5 : 1, each TextMate theme loading in tm4e, icons 3 : 1. `EditorColoursTest` needs joni and runs under Gradle and on the phone |
| `SettingsCatalogTest`, `SettingLimitsTest` | Borders found on Appearance; Editor's controls found on its page; on by default |
| `AndroidWorkspacePreferencesRepositoryTest` | The setting survives a restart (Gradle; DataStore is not on this machine) |
| Type-checks | Every screen check; Appearance, the accent dialog and the terminal palette, which no check compiled before; the editor against Sora |

These design-system tests had not run on this machine before: Compose's colour conversion needs
`androidx.collection`, which cannot be fetched here. A stand-in for the three members it calls,
outside the repository, runs them.

### 5.4 On the phone, open

- Appearance › Card Borders off, in each theme: the Launcher, Settings, Source Control, a dialog,
  the editor and the terminal; then on again, without a restart.
- Light without borders: a comment in the editor; the terminal's `ls --color`.
- A selected row, the open dropdown and a warning notice keep their lines with borders off.
