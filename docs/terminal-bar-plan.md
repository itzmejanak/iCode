# The Terminal's Bar: Keys While Typing, What Matters Otherwise

Owner's request (2026-10-20): the terminal's foot should behave as the editor's. While the
keyboard is up it shows the keys row, which the owner likes. While the keyboard is down it shows a
bar, holding the navigation and actions that matter most from the terminal.

Owner's decision (2026-10-20), from mockups of three bars over the AMOLED theme:

| Question | Decision |
|---|---|
| What the bar holds | **Editor · Paste · Stop · Clear**: option A (Editor · Paste · Stop), with Clear added after Stop |
| A running session's tab icon | **The accent**, as the rest of the app's state of "on" (it was green) |

## 1. What stood (verified)

- The editor's foot (`WorkspaceEditorScreen`) holds the keys row while the keyboard is up
  (`WindowInsets.isImeVisible`) and `BottomPanelBar` otherwise: Terminal, Problems, then the file's
  change count and the Markdown toggle.
- The terminal's foot (`TerminalScreen`) showed the extra keys row whenever a session existed,
  keyboard or not. It had no bar.
- The panel's header already holds Back, the Terminal, Run, Problems and Output tabs, and +.
- Paste was reached only through a long press, Termux's selection bar and Paste. Ctrl+C and
  Ctrl+L needed the keyboard and the row's CTRL.

## 2. The bar

| Entry | Does | How |
|---|---|---|
| **Editor** | Back to the code: it stands where the editor's bar has Terminal, so one spot goes back and forth | `NavigationActions.returnTo(Route.WorkspaceEditor)`. If the editor is the screen under the panel, it goes back to it. Otherwise the editor takes the panel's place. The two never stack up |
| **Paste** | The clipboard's text at the cursor | As Termux's own Paste does it (`TerminalView`'s middle click): the clip's first item, `coerceToText`, then `TerminalEmulator.paste`. Newlines become Enter, and the text is bracketed when the program asked for it |
| **Stop** | Ctrl+C to the program in the foreground. At the prompt it drops the line typed | `TerminalView.inputCodePoint('c', ctrl)`: the path a typed key takes, so the view follows the output to its end |
| **Clear** | Ctrl+L: the shell clears the screen, its prompt on the top line | The same, with `'l'` |

Paste, Stop and Clear are muted while no shell runs. Stop and Clear send exactly their keys: a
CTRL or ALT held on the keys row is let go, not added to them.

Run, Problems and Output are the header's, so the bar does not repeat them. A tap on the terminal
raises the keyboard, as before, and the keys row comes with it. With a hardware keyboard Android
keeps the soft one down by default, so the bar stays. With every extra key hidden in Terminal Settings, the bar stays
while typing too, as the editor's does with an empty keys row.

The bar keeps to the panel's foot whatever stands above it: the terminal, the runtime's gate or
"No terminal sessions".

## 3. Where it goes

| Part | Change |
|---|---|
| Navigation | `NavigationActions.returnTo`, `NavigationState` |
| Terminal | `TerminalKeys.paste`, `interrupt`, `clear`; `TerminalView.followOutput`, shared with the view's callbacks |
| Screens | `TerminalBar` in `TerminalControls.kt`; `TerminalScreen` swaps it with the keys row; `TerminalTabStrip`'s running icon in the accent |
| Tests | `NavigationPanelTest`: the editor and the terminal take turns; reached from elsewhere, the terminal gives way to the editor |
| Docs | `layout.md` §17.3 |

## 4. As built

### 4.1 How it was checked

| Check | What it showed |
|---|---|
| `NavigationPanelTest`, `RouteTest` | 11 tests, the two new ones among them |
| `TerminalKeys`, the view's callbacks, `TerminalUrls` | Type-checked against stubs carrying termux-app v0.118.3's own signatures (`TerminalView`, `TerminalViewClient`, `TerminalSession`, `TerminalEmulator`, `TerminalBuffer`), taken from its sources. No check had compiled these files before |
| Guards | Backend and UI consistency; every changed file within its line budget |

The screen checks and the whole suite were stopped before they finished, at the owner's word
(2026-10-20), so `TerminalScreen` and `TerminalBar` were not type-checked here.

### 4.2 On the phone, open

- The terminal with the keyboard down shows the bar. A tap on the terminal shows the keys row
  above the keyboard; Back hides the keyboard and the bar returns.
- Editor, Terminal, Editor, Terminal from the bars, then Back: back to the screen before the
  editor, not through each switch.
- `npm run dev` (or `sleep 100`), then Stop. Text typed at the prompt, then Clear. A command
  copied elsewhere, then Paste: with bash it waits for Enter.
- No session, and a runtime not installed: the bar stays at the foot, Editor live, the rest muted.
