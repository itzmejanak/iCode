# Environment icon prompts v2 (recolored brand)

Owner direction (2026-11-09): the iCode mark keeps its layout and shape, but each
environment gets its own **color branding of the whole mark** plus a clean label.
Production (stable/latest) keeps the original icon with **no label**.

## Template (attach `design/brand/icon.png`, then send)

> Attached is the app icon for "iCode", a code editor. IMPORTANT: generate the
> image directly as a model — do NOT write or run Python code. Keep EXACTLY the
> same layout, shapes, geometry and proportions of the logo mark — do not
> redesign it, do not move or resize its parts, do not add or remove shapes.
> RECOLOR the mark: replace every warm orange/amber tone (about #ED7306,
> #FC8A07, #E16402, #F9A10E) with <ENV_WARM>; replace every blue tone (about
> #6DB3F5, #4695E9, #147DE4) with <ENV_COOL>; keep the black shapes, white
> highlights and shadows as they are. Then add a solid rounded pill, bottom-center,
> filled <ENV_PILL>, containing bold white uppercase text "<TEXT>". Output a
> single square 1024x1024 PNG with a fully transparent background (artwork alone,
> no background fill, nothing outside the artwork).

## Per-environment values

Labels appear only on production pre-releases: **preview** and **beta**. Dev and
staging are recolored with no label; stable keeps the original icon with no label.
(Owner decision, 2026-11-09.)

Final decisions (owner, 2026-11-09): beta and preview keep the **original mark
colors** and carry only the vertical full-word label; dev and staging are
recolored with no label; stable keeps the original icon.

| env | mark | label |
|---|---|---|
| dev | recolored: warm grey #B0A99A + slate blue #7B93A7 | none |
| staging | recolored: lavender #9C85C9 + violet #6E5AA0 | none |
| preview | original orange/blue | "PREVIEW" vertical, right side, no pill |
| beta | original orange/blue | "BETA" vertical, right side, no pill |
| stable | original | none (original icon) |

## Deliverables

- `design/icons/<env>/icode-<env>-icon.png` per non-stable environment
  (transparent background, recolored mark, label).
- Stable unchanged: `design/brand/icon.png`.
- A copy of each result in the phone's `Download/icode-icon-options/` for browsing.
- Verify each pull: transparent corners + expected pill color pixels present.

## As built (2026-11-09, Meta AI)

Generated in the Meta AI app (icon attached per request), saved to the phone,
pulled into the workspace:

| env | file | result |
|---|---|---|
| development | `design/icons/development/icode-development-icon.png` | warm-grey + slate-blue mark, no label |
| staging | `design/icons/staging/icode-staging-icon.png` | lavender + violet mark, no label |
| preview | `design/icons/production/preview/icode-preview-icon.png` | original mark, "PREVIEW" vertical inside the right side, no pill |
| beta | `design/icons/production/beta/icode-beta-icon.png` | original mark, "BETA" vertical inside the right side, no pill |
| stable | `design/icons/production/stable/icode-stable-icon.png` | original, unchanged (copy of `design/brand/icon.png`) |

All are 1024×1024 with transparent backgrounds. Copies for browsing:
`Download/icode-icon-options/env-{dev,staging,preview,beta}.png`.
