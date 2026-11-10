# Theme variants — plan

Status: **decided** (2026-11-10). The owner approved every recommendation in §8 and the architecture:
keep iCode's colour system, settings store and theme engine, and keep today's look for existing users
after the update. T1 is the first cycle. Auto is its own cycle, T6, after T5.

Owner's request: port antigem's appearance themes into iCode, adjusted to what suits iCode. Dark,
Light and AMOLED stay the primary themes. Each of the three has its own variants, and every new
theme becomes a variant of one of them. A sheet chooses the variant of each primary. Finish this
cycle before the dock shortcut, which is a separate discussion.

Follows [layout.md](./layout.md) §4 and §20 (semantic tokens), [border-lines-plan.md](./border-lines-plan.md)
(the tone rule), [editor-colors-plan.md](./editor-colors-plan.md) (syntax colours),
[accent-color-plan.md](./accent-color-plan.md), [environments-plan.md](./environments-plan.md) and
[settings-backend-split-plan.md](./settings-backend-split-plan.md) §3.1 and §4.6 (a theme change reaches
every surface at once).

## 1. Measured

### 1.1 iCode today (main 9847ab3)

- **Model.** `AppTheme { Dark, Light, Amoled }` (`domain/AppSettings.kt`) is stored as `appearance_theme`
  in the `workspace_settings` DataStore. An unknown value reads as Dark. The accent is
  `appearance_accent_color`, where null means the theme's own accent.
- **Palettes.** `IcodePalette` (`designsystem/IcodeColors.kt`) has 20 semantic colours plus 16 ANSI
  colours. It has three hand-made instances, one each for Dark, Light and Amoled, and carries its
  `kind`. Everything else is derived from those 20 colours:
  - The borderless tones, in OKLCH lightness only (`BorderlessPalette.kt`, card step 1.12).
  - Syntax colours, one OKLCH hue per role (`SyntaxColors.kt`, 4.5:1 on the editor).
  - File icon colours (`ui/FileIcons.kt`, 3:1).
  - The editor's TextMate theme and Sora colours (`ui/editor/EditorTheme.kt`).
  - The terminal scheme (`ui/terminal/TerminalPalette.kt`).
  - The Material scheme (`IcodeTheme.kt`) and the system bars (`IcodeApp.kt`).
- **One entry point.** `appearancePalette(appearance)` is the only function that picks a palette.
  The app (`IcodeApp`) and the phone overlays (`PhoneOverlays`) both call it.
- **Branches on the family.** Seven places test `kind == Light`:
  - the Material scheme;
  - the system bars;
  - which tone moves when Border Lines is off;
  - the syntax lightness;
  - the TextMate `isDark` flag;
  - the file and category icon colours.

  All of them still work for a variant that carries its family's `kind`.
- **The page.** Appearance › Color Theme shows three cards, each with a 28 dp strip of its
  background. The Accent row opens `AccentColorSheet`, which is built on `IcodeConfirmSheet`.
- **Tests that loop over themes.** One shared list, `everyPalette` (`app/src/sharedTest/.../EveryPalette.kt`),
  holds each theme with and without border lines. `SyntaxColorsTest`, `EditorThemeTest`,
  `FileCategoryIconsTest`, `FileIconColoursTest` and the engine test `EditorColours` (on the JVM and
  on the phone) all use it. `IcodePaletteTest` loops over `AppTheme.entries` itself. If that one list
  holds every variant, all of these tests cover the variants with no change of their own.
- **Gaps that variants would expose:**
  - The TextMate theme is named `icode-<kind>-<editor>-<line>`. Tm4e caches themes by name, so two
    variants that share those three values but have different syntax colours would get the first
    one's colours.
  - The splash and the window background are always Dark's `#0A0D0B`. Light already starts dark
    for a moment today.

### 1.2 antigem (Santoshkurmi/antigem 2a04d61, GPL-3.0, the same licence as iCode)

- **Themes.** There are 25 built-in themes (`theme/AppTheme.kt`):
  - 8 light: Porcelain, Lavender, Sky, Sakura, Mint, Linen, Paper and Solarized Light.
  - 17 dark: Midnight Aurora, Obsidian, Graphite, Midnight, High Contrast, Amber Night, Mocha, Neon
    City, Aurora, Nord, Dracula, Velvet, Deep Ocean, Ember, Forest, Sunset and Solarized Dark.

  Midnight and High Contrast are the only ones on pure black. Four are classic editor themes under
  other names:
  - Mocha is Catppuccin Mocha.
  - Neon City is Tokyo Night.
  - Velvet is Rosé Pine.
  - Ember is Gruvbox Dark (hard).
- **What a theme is.** Every theme except three is `seeded(...)` from 14 colours plus 8 syntax
  colours. The 14 are:
  - page, surface, card and raised;
  - text and muted;
  - primary, secondary and tertiary;
  - success, warning, danger and info;
  - the code background.

  The rest of its 96 tokens (32 Material, 48 of antigem's own, 16 ANSI) are derived by sRGB `lerp`. Midnight Aurora, Porcelain and Amber
  Night set every token by hand.
- **Engine.** `ThemeManager` is a process-wide singleton:
  - The light and dark choice is stored in SharedPreferences `app_themes`.
  - Colours are read through getters outside composition.
  - Resolved themes are cached by object identity.
- **UI.** Light, Dark and Auto mode cards, then a two-column gallery for each side
  (`ThemeGallery.kt`). Each `ThemeCard` is a live mini screen: the page, a card with two text lines
  and a primary pill, the user's message bubble, a code strip with three syntax colours, and four
  status dots. `ThemeQuickPanel` is a floating row of the same cards for the side on screen.
- **Custom themes.** These exist only through the model-facing `theme_mcp` tools: list, get, tokens,
  apply, set_mode, create, update, duplicate, delete, export and import. There is no in-app editor.
- **Faults visible in the code:**
  - Chat code blocks cache their colours by code only, so they keep the old theme's colours.
  - `Color.White` is hard-coded on light themes (IDE tabs, code blocks, dialogs).
  - The terminal theme setting is saved but never read.
  - `GeminiTheme` writes preferences during composition.

### 1.3 antigem's palettes under iCode's rules

These were measured with the WCAG ratio, using antigem's seed colours mapped as in D4. The floors
are iCode's existing ones:
- text, secondary text and the four status colours: 4.5:1 on the page, the card and the raised tone;
- the accent: 3:1;
- syntax: 4.5:1 on the editor.

| Variant | Under a floor | Syntax under 4.5:1 |
|---|---|---|
| Lavender, Sakura, Mint, Linen | none | all 8 (see below) |
| Sky | green 3.09, blue 3.84 | all 8 |
| Paper | green 4.43, amber 4.46 | all 8 |
| Solarized Light | secondary 4.39, green 2.97, amber 2.98, error 4.29, blue 3.41 | all 8 |
| Obsidian, Graphite, Midnight, Aurora, Velvet (Rosé Pine), Deep Ocean, Sunset | none | comment only (3.17–4.35) |
| High Contrast, Mocha (Catppuccin), Forest | none | none |
| Neon City (Tokyo Night) | secondary 4.45 | comment 3.88 |
| Nord | secondary 3.83, error 2.46, blue 2.50 | comment 3.25 |
| Dracula | secondary 4.15, error 4.03 | comment 4.19 |
| Ember (Gruvbox) | error 4.29 | none |
| Solarized Dark | secondary 4.00, green 4.06, amber 4.05, error 2.81, blue 4.12 | number 3.69, comment 4.14, annotation 3.64 |

- **Light themes' syntax.** Every light theme pairs a light page with a **dark** code block, and
  its syntax colours are pastels made for that dark block. On their own page they read at 1.13–2.48:1.
  In iCode the editor is the main screen, and a light theme has a light editor (editor-colors-plan).
- **Card steps.** Card-on-page steps are 1.04–1.24 in antigem. iCode's own are 1.067–1.085.
- **iCode's families, measured the same way:**

| Family | text | secondary | muted | accent | card | outline |
|---|---|---|---|---|---|---|
| Dark | 16.71 | 7.71 | 3.53 | 6.87 | 1.085 | 1.49 |
| Light | 15.51 | 7.70 | 4.60 | 5.06 | 1.078 | 3.63 |
| AMOLED | 19.22 | 9.88 | 7.16 | 7.39 | 1.067 | 4.13 |

## 2. What is ported, adapted or left

| antigem | iCode | Why |
|---|---|---|
| The 25 palettes | **Ported** as variant seeds (D4) | They are the request. They are GPL-3.0 like iCode, and antigem is credited in the notices. |
| The live mini-screen preview card | **Adapted** to iCode's tokens: page, card, accent, editor strip and status dots (D8) | A strip of background cannot tell Nord from Dracula. |
| Classic theme names | **Adapted** (D6) | Developers search for the original names, and iCode's accent preset is already called Ember. |
| Light themes' syntax colours and dark code block | **Adapted**: a light variant keeps its syntax hues on a light editor (D5) | 1.13–2.48:1 on their own page (§1.3). |
| Status colours under 4.5:1 | **Adapted**: only their lightness moves (D4) | They are drawn as text: warnings, Git states and diagnostics (layout.md §20). |
| ThemeManager, SharedPreferences store, identity caches, getters outside composition | **Left** | iCode already has DataStore, an immutable palette passed through a CompositionLocal, and value caches. The singleton is the source of antigem's stale-colour faults. |
| 96 tokens and sRGB `lerp` derivation | **Left** | iCode's 20 semantic tokens and OKLCH lightness-only derivation are its design system (layout.md §20), and every iCode screen is built on them. |
| Auto (follow the system) | **Its own cycle, T6**, after T5 (owner) | Each family remembering its own variant (D1) means Auto needs no variant of its own. |
| Custom themes and `theme_mcp` | **Not now** | Not requested. The seed format (D4) is what a later import or assistant tool would write. |
| `ThemeQuickPanel` | **Not now** | It belongs to the dock shortcut discussion. |

## 3. Decisions

**D1 — Dark, Light and AMOLED stay; each remembers its own variant.**
- `AppTheme` stays the family and is stored as today.
- Three new keys hold a variant id each: `appearance_dark_variant`, `appearance_light_variant` and
  `appearance_amoled_variant`.
- A missing key means the family's **iCode** variant, which is today's palette exactly. An unknown
  id, such as a variant removed in a later version, also means iCode. Nobody's look changes on update.
- Switching family brings back that family's own choice.

**D2 — One palette builder, one entry point.**
- `IcodePalette.of(theme, variant, borderLines)` builds every palette, and `appearancePalette` passes
  it the family's variant. So both roots, and every consumer listed in §1.1, follow a variant with
  no change of their own.
- A variant carries its family's `kind`, so the seven `kind == Light` branches stay correct.
- Built palettes are cached by value, as the borderless ones are.

**D3 — AMOLED offers every Dark variant on true black, plus the black-only designs.** (Owner, 2026-11-10.)
- An AMOLED twin is derived, not kept by hand:
  - The page and the editor are black.
  - Each surface keeps its Dark variant's hue and chroma. Its lightness is set so it is as far from
    black as iCode AMOLED's own: card 1.067, outline 4.13, and the raised and track tones likewise.
  - Text, accent, status, syntax and ANSI colours are the Dark variant's, checked again on black.
- AMOLED then offers iCode AMOLED (hand-made, as today), Midnight, High Contrast and the 15 Dark
  variants, 18 in all. A Dark variant added later gets its AMOLED twin with no more data.

**D4 — A variant gives its colours; its family gives the structure.**
- Seeds map to iCode's tokens:

| iCode token | From the variant's seed |
|---|---|
| background | page |
| surface | card |
| surfaceElevated | raised (antigem's `surface_variant`) |
| backgroundDeep (editor, terminal) | code background on Dark; on Light, derived from the page at Light's step (D5) |
| textPrimary, textSecondary | text, muted |
| accent | primary |
| green, amber, error, blue | success, warning, danger, info |
| ANSI magenta | tertiary (antigem's secondary has no role in iCode) |

- Tokens no seed gives are derived at the **family's own measured step** (§1.3), in the variant's
  hue: the track tone, outline, muted text, the text on the accent, and the diff and conflict tints.
  Every variant of a family therefore behaves like that family with Border Lines on or off, and the
  existing borderless rule applies unchanged.
- A seed is kept exactly unless it is under a floor (§1.3). Then only its OKLCH lightness moves until
  it meets the floor (`Oklch.readable`), and the T2 test lists every colour that moved. This changes,
  for example, Nord's red and blue, and five Solarized colours.
- **ANSI colours:**
  - The classic themes take their project's own 16 terminal colours, with the source recorded.
  - Midnight Aurora keeps antigem's hand-set 16.
  - The rest are derived from the status colours, as iCode's own ANSI lists are built.

**D5 — Syntax colours are the variant's own, made readable on its editor.** (Owner, 2026-11-10: each
theme has its own matching editor colours.)
- The iCode variants keep the one-hue rule, unchanged.
- A Dark or AMOLED variant keeps its 8 syntax colours where they reach 4.5:1 on the editor and the
  current line. Otherwise only their lightness moves, which is today's `onEditor` rule.
- A Light variant keeps each syntax colour's **hue** and takes iCode's Light lightness (0.47). Its
  editor is light, like iCode Light's.
- Roles map as antigem's own fallbacks do:
  - keyword: Keyword, Control, Tag and Heading;
  - type: Type, Escape and Attribute;
  - string: String, Inserted and Regex;
  - number: Number and Constant;
  - function: Function, List marker and Changed;
  - comment: Comment, Quote and tag punctuation.
- Text and error stay the palette's own. antigem's annotation and punctuation colours have no role
  in iCode: decorators are Attribute and punctuation is plain.
- The TextMate theme is named from a hash of its rules, so no two variants share a cached theme
  (§1.1).

**D6 — Names.**
- The classics take their projects' names: Catppuccin Mocha, Tokyo Night, Rosé Pine and Gruvbox
  Dark, plus Nord, Dracula, Solarized Dark and Solarized Light. (Owner, 2026-11-10.)
- Light also gets the official light siblings of four classics: **Catppuccin Latte, Gruvbox Light,
  Rosé Pine Dawn and Tokyo Night Day** (owner, 2026-11-10). antigem has none of them, so their seeds
  come from each project's own published palette, with the source recorded.
- antigem's own designs keep their names: Midnight Aurora, Obsidian, Graphite, Midnight, High
  Contrast, Amber Night, Aurora, Deep Ocean, Forest, Sunset, Porcelain, Lavender, Sky, Sakura, Mint,
  Linen and Paper.
- Descriptions are rewritten for iCode, without "AntiGem's look".
- Each family's first variant is **iCode**.

**D7 — Accent.**
- A variant's accent is its theme accent. "Use theme default" in the accent sheet returns to it.
- The user's own accent still wins in every variant, as today.
- The build's environment accent (Development grey, Staging lavender) stays the accent of the iCode
  variants only, because a named variant's accent is part of what it is. The launcher icon still
  tells the builds apart.
- Every variant's accent must be at least 3:1 on its page.

**D8 — Choosing.** See §4.

**D9 — Built-in variants are Kotlin data, one file per family.**
- The palette is needed for the first frame, so there is no asset read and nothing to fail parsing.
  The compiler checks every seed, and the tests loop over them.
- The UI guard's raw-colour scope is widened on purpose, from `IcodeColors.kt` to
  `designsystem/palettes/`, as settings-backend-split-plan §3.1 asks.
- A later import or assistant tool would read JSON into the same `ThemeSeed` type.

## 4. The page and the sheet

**Appearance › Color Theme**
- The three family cards stay. Each card now shows a mini preview of **its remembered variant** and
  names it, for example "Dark · Nord". You see what Light will look like before you switch.
- Tapping a card switches family, as today. Tapping the card already in use opens its sheet.
- One row under the cards, "Variant", shows the current variant's name and a › and opens the sheet.
  The row is the visible way in; tapping the card in use is a shortcut to the same sheet.

**The variant sheet** (`IcodeBottomSheet`)
- **Title** "Dark variants", "Light variants" or "AMOLED variants", with the count as subtitle.
- **Sections:**
  - **iCode**;
  - **Editor classics**, A to Z. Dark and AMOLED: Catppuccin Mocha, Dracula, Gruvbox Dark, Nord,
    Rosé Pine, Solarized Dark and Tokyo Night. Light: Catppuccin Latte, Gruvbox Light, Rosé Pine
    Dawn, Solarized Light and Tokyo Night Day;
  - **More**: the rest, A to Z.
- **Cards:** two columns on a phone, more where the sheet is wider.
- **Each card** is drawn from that variant's palette, exactly as the app would draw it with the
  current Border Lines and accent settings:
  - the page;
  - a card with two text lines and an accent pill;
  - an editor strip with the keyword, string and function colours;
  - the green, amber, error and blue dots;
  - the name underneath.

  The variant in use has the accent outline and ✓. TalkBack reads each card as "Nord, selected".
- **Tapping a card** uses it at once and saves it. The app and the sheet recolour in place, and the
  sheet stays open so variants can be compared. × or a swipe down closes it. A tap is reversible by
  tapping the previous card, so there is no Apply step.
- **With a custom accent set**, one line says: "Your accent replaces each variant's own. Reset it in
  Accent Color." The previews are then drawn with that accent, as the app would show it.
- **Settings search** gains every variant's name, so "dracula" finds Appearance.

## 5. Where the code goes

| Layer | File | What |
|---|---|---|
| domain | `AppSettings.kt` | `AppearanceSettings.variants: Map<AppTheme, String>` (no entry = iCode), `variantOf(theme)`, and `ThemeVariantRules` (what an id may be; iCode's own is stored as no id) |
| domain | `WorkspacePreferences.kt` | `setThemeVariant(theme, id: String?)` |
| data | `AndroidWorkspacePreferencesRepository.kt` | The three keys, and a value longer than 40 characters reads as null |
| debug | `CapturePreferences.kt` | The new setter, as a no-op |
| designsystem | `palettes/ThemeSeed.kt` | The seed type and the D4 and D5 derivation |
| designsystem | `palettes/DarkVariants.kt`, `LightVariants.kt`, `AmoledVariants.kt` | The seeds. AMOLED holds Midnight and High Contrast, and the twins are derived |
| designsystem | `ThemeVariants.kt` | The catalogue: each family's variants in sheet order, iCode's first, and `find(theme, id)`, where an unknown id is iCode |
| designsystem | `IcodeColors.kt` | `IcodePalette.of(theme, variant, borderLines)`, the variant required so no caller forgets it. The iCode variants stay the three palettes as they are now |
| designsystem | `SyntaxColors.kt` | A palette's optional syntax seeds, used before the hue rule |
| ui | `AppearancePalette.kt` | Passes the family's variant |
| ui/editor | `EditorTheme.kt` | The theme name from a hash of its rules |
| ui/screens/settings | `AppearanceScreen.kt`, new `ThemeVariantSheet.kt`, new `ThemePreview.kt` | §4 |
| ui/screens/settings | `SettingsCatalog.kt` | Variant names as keywords |
| tools | `check-ui-consistency.sh` | The raw-colour scope (D9) |
| notices | `THIRD_PARTY_NOTICES.md` | antigem, and each classic palette's project and licence |

Every file stays at or under 300 lines.

## 6. Cycles

| Cycle | What | Exit gate |
|---|---|---|
| **T1** — model and storage | The variant setting, its keys, the setter, the capture fake, and `of(theme, variant, …)` with only the iCode variants | Unit tests: a missing or unknown id gives iCode; the iCode variants are the same objects as today's palettes; the setting survives a restart. Nothing on screen changes |
| **T2** — derivation | Seeds to palette (D4), AMOLED twins (D3), syntax per variant (D5), ANSI, the TextMate name; `everyPalette` grows to every variant | Tests over every variant × family × Border Lines on and off, through `everyPalette` and the existing tests that use it: every existing floor holds (text 4.5, accent 3, card step 1.12 borderless, syntax 4.5, icons 3, terminal); seeds are kept unless listed as moved; each TextMate theme parses; no two names collide |
| **T3** — the palettes | The 25 seeds from antigem 2a04d61; the four Light classics from their projects' palettes; the classics' ANSI from each project's own source; names and descriptions; notices | The T2 tests over the real data; the licence of each source recorded |
| **T4** — the page and the sheet | §4 | Both guards; captures of the sheet for each family on Light and Dark |
| **T5** — docs as built | This plan, CHANGELOG, layout.md §20 | — |
| **T6** — Auto (separate) | Follow the phone's light or dark setting (§6.1) | Its own plan section and owner check before it is built |

### 6.1 T6 — Auto, in outline

Auto is a fourth choice beside the three families. The phone's setting picks Light by day and a dark
family by night, and each family draws its own remembered variant (D1), so Auto adds no variant of
its own. Points to settle when T6 starts:

- Which family Auto uses at night: Dark, AMOLED, or the one last chosen.
- Reading the phone's night mode live (`Configuration.uiMode`), and the system bars following it.
- The splash and window background, which are Dark's `#0A0D0B` today (§1.1).

## 7. Phone checks (owner)

1. After the update, Appearance looks as before. Dark, Light and AMOLED each show "iCode".
2. Dark › Variant › Nord: the app, the editor, the terminal, a diff, a Markdown code block, file
   icons and the system bars change together. The sheet stays open, and tapping iCode brings it back.
3. Switch to Light and back: Dark is still Nord. AMOLED › Nord shows Nord on black.
4. A Light variant: the editor is light and its syntax colours read.
5. Border Lines off and Compact Layout work with a variant. So do a custom accent and "Use theme
   default".
6. Restart: the variant stays.

## 8. Owner's decisions (2026-11-10)

| Question | Decision |
|---|---|
| AMOLED | Every Dark variant on true black as well, 18 in all (D3) |
| Syntax colours | Each variant's own, made readable (D5) |
| Names | The original names: Catppuccin Mocha, Tokyo Night, Rosé Pine and Gruvbox Dark (D6) |
| Light balance | Add Catppuccin Latte, Gruvbox Light, Rosé Pine Dawn and Tokyo Night Day: Light has 13 variants, Dark 16, AMOLED 18 (D6) |
| Auto | Yes, as its own cycle after the others (T6, §6.1) |
| Architecture | Keep iCode's colour system, settings store and theme engine; existing users keep today's look |

## 9. Record

- **T1 — model and storage** (done 2026-11-10). Nothing on screen changes yet: each theme has only
  iCode's own variant until T3.
  - `domain/AppSettings.kt`:
    - `AppearanceSettings.variants`, which maps each theme to its variant id (no entry means
      iCode's own), and `variantOf(theme)`.
    - `ThemeVariantRules`: an id is lower-case letters, digits and `_`, up to 40 characters, and
      iCode's own is kept as no id.
  - `setThemeVariant(theme, id)` stores the id in one of three DataStore keys,
    `appearance_dark_variant`, `appearance_light_variant` or `appearance_amoled_variant`.
    - Choosing iCode's own removes the key.
    - A stored value that is not an id reads as iCode, and the other keys are left as they are.
    - The setter refuses a value that is not an id.
  - `designsystem/ThemeVariants.kt` is the catalogue. Each theme lists iCode's own first, and
    `find` returns iCode's own for an unknown id. A variant's palette is built the first time it is
    drawn.
  - `IcodePalette.of(theme, variant, borderLines)` requires the variant, so no caller can forget it.
    `appearancePalette` passes the theme's own variant, and Appearance's cards preview their own
    theme's variant.
  - `everyPalette`, and the tone tests in `IcodePaletteTest`, loop over every variant. The syntax,
    editor-theme and icon-colour tests therefore cover each variant added in T3.
  - Tests:
    - `ThemeVariantRulesTest` (3).
    - `AppearancePaletteTest` (4): before settings are read, a theme's own variant, an unknown id,
      the user's accent.
    - `IcodePaletteTest`, 2 new: iCode's own however it is named, and the catalogue's rules.
    - The repository test: each theme's variant survives a restart on its own, and a stored value
      that is not an id is read as iCode.
  - Gate:
    - 1546 unit tests. The only failure is `TreeSizeTest`'s root-only case, as on main.
    - assembleDebug and assembleRelease build, and both consistency guards pass.
    - Lint has nothing new. Its one error, `SpeechProviderParts.kt:80`, is already on main.
