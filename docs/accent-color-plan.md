# Accent Color — Plan and Record

Status: **done** for the in-app accent (2026-09-29); the launcher icon is §6. Owner's request: "add a color picker library so the user can
change the accent color from Settings → Appearance". Follows
[backend-split-rules.md](./backend-split-rules.md) and [ui-split-plan.md](./ui-split-plan.md).

## 1. What exists

- The accent is one field of the semantic palette (`IcodePalette.accent`, `onAccent` for text on
  it). `IcodeApp` picks Dark, Light or Amoled from the saved theme and provides it through
  `LocalIcodePalette`; every screen reads `IcodeColors.Accent`.
- The editor (caret line number, selection, handles) and the terminal cursor take `palette.accent`
  already, so a custom accent reaches them if it is applied where the palette is chosen.
- Settings → Appearance shows "Ember Accent #FF6B2C" as a fixed row.
- Four places tell palettes apart by identity (`palette == IcodePalette.Light`): status-bar icons,
  the Material scheme, the editor's TextMate theme and the terminal's ANSI colours. A palette with a
  replaced accent is a different value, so each would silently fall back to Dark.

## 2. Library

| Library | Status | Fit |
|---|---|---|
| [skydoves/colorpicker-compose](https://github.com/skydoves/colorpicker-compose) | Apache-2.0, maintained (pushed 2026-09-28, 760★) | HSV wheel, brightness slider, colour tiles, controller with `selectByColor`; Compose only |
| [godaddy/compose-color-picker](https://github.com/godaddy/compose-color-picker) | last push 2024-05 | classic and harmony pickers; unmaintained |
| [SmartToolFactory/Compose-Color-Picker-Bundle](https://github.com/SmartToolFactory/Compose-Color-Picker-Bundle) | large bundle | gradients and many models; far more than an accent needs |

**skydoves 1.1.4**, not the latest 1.3.0: 1.2.0 and later require Kotlin stdlib 2.4 and Compose
1.11–1.12, while this app builds with Kotlin 2.3.10 and Compose 1.11.0 (BOM 2026.04.01); 1.1.4
needs stdlib 2.3.21 (a patch in the same line) and Compose 1.10.3, which the BOM overrides.
Upgrading Kotlin is a separate, app-wide decision.

## 3. Design

- **Setting:** `AppearanceSettings.accentColor: Int?` (ARGB, opaque), `null` = the theme's own
  accent, app-wide (not a workspace override), in the existing DataStore
  (`appearance_accent_color`); removing the key resets it.
- **Applied once:** `IcodePalette.withAccent(color)` replaces `accent` and chooses `onAccent`
  (black or white, whichever contrasts more). Palettes carry their `kind`, so the four checks above
  read the kind instead of identity.
- **Choosing:** the Accent row opens a sheet: preset swatches (Ember is the default), the HSV wheel
  with brightness, a hex field (literal input), a preview of accent text and an accent button on
  the current theme's background, the contrast ratio with a warning below 3:1, "Theme default",
  Cancel, Apply. The row names a preset, or "Custom", and shows the hex.
- **Not changed:** the Android notification tint stays the brand colour (a resource outside
  Compose); capture fixtures keep the theme accent, so screenshots are unchanged.

## 4. Steps

1. Palettes know their kind (behaviour unchanged).
2. The accent setting: rules (presets, hex, contrast), storage, applied in `IcodeApp`; tests.
3. The picker sheet with the library, and the Appearance row.
4. Device check (when the owner's phone is back): pick, apply, reset, Light theme, editor caret,
   terminal cursor, restart keeps it.

## 5. Record

- **Step 1** (`f2d28e3`): `PaletteKind` on every palette; status bars, Material scheme, editor
  theme and terminal ANSI read the kind.
- **Step 2** (`fb09567`): `AccentColorRules` (eight presets, hex, WCAG contrast, black or white on
  top), `setAccentColor`, DataStore key, `withAccent` in `IcodeApp`; JVM tests for the rules, the
  palette and the repository across a restart.
- **Step 3:** `colorpicker-compose:1.1.4` (resolves Compose 1.11.0 from the BOM, stdlib 2.3.21);
  `AccentColorDialog` (150 lines): presets are exact, only a drag reads the wheel back, typed hex
  moves the wheel and brightness bar; Apply stores `null` when the choice equals the theme's own
  accent. The row reads "Theme accent" or "<preset|Custom> accent" with the hex. Lint's one new
  warning is the newer 1.3.0, pinned on purpose (§2).
- **Step 4, device** (2026-09-29, debug build): preset Sky → `#4FB3FF` exact; drag → Custom
  `#1E91FF`; typed `#303848` → contrast 1.7:1 warning, wheel and bar follow; Violet applied to the
  whole app (theme border, slider, toggle, home icons, branch labels) and kept after a force-stop;
  on Light, Violet warns at 2.7:1 and "Use theme default" gives Light's own `#B4470C`, stored as
  the theme accent. Owner's settings returned to Dark with the theme accent. Editor caret and
  terminal cursor were not re-driven on the device; they read the same `palette.accent`.
- Gate: debug and release compile, 414 JVM tests, lint 0 errors / 12 warnings, UI and backend
  guards pass.
