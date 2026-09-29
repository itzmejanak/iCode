# Where projects live — a Linux-native home, with today's experience — plan

Status: **plan, for the owner's review** (2026-09-30). Owner: "npm install in RevPlayer never ends,
even on fast 5G … if dependencies cannot be installed the IDE is not worth it — not just npm; an
IDE is an IDE; look from a broader sense and be accurate." Compared with
[Mobile-Harness](https://github.com/techjarves/Mobile-Harness) (Ubuntu under PRoot, like iCode, but
its workspaces live in app-private storage, shared through Android's Storage Access Framework).
Owner's decision: **A — projects in iCode's own storage**, and "how do we keep the user's current
experience".

## 1. Measured on the owner's phone (2026-09-29/30)

| Fact | Phone storage (`/storage/emulated/0`, where projects are today) | iCode's own storage (`files/`, ext4) |
|---|---|---|
| 1,000 small files created / deleted | **13.0 s / 6.1 s** | **0.07 s / 0.04 s** |
| symbolic link (`node_modules/.bin`, pnpm, virtualenvs) | **refused** ("Permission denied") | works |
| `chmod 755` (`./gradlew`, scripts) | **ignored** (`-rw-rw----`) | kept (`-rwxr-xr-x`) |
| `A.txt` next to `a.txt` | **the same file** — the second write replaced the first, no error | two files |
| a real `npm install` (semver + lodash, 1,116 files, cold) through iCode's PRoot | — (the shell used for the test cannot reach phone storage) | **17 s**, `.bin/semver` linked |

Network inside iCode's runtime: DNS 160 ms, a registry request 356 ms, then 46–56 ms.

**The owner's own `npm install` (RevPlayer), read from npm's log and `/proc`:** 30 requests in
hours; median 67 s, worst 10,021,051 ms (2 h 47 min). While the launcher was in front the process
was frozen (`State: D`, `wchan: __refrigerator`) — though iCode's foreground service was running,
iCode was on the battery whitelist and background running was allowed. It never reached writing
`node_modules`. Two causes, then: **the process is stopped while iCode is out of sight**, and, next,
**phone storage makes the writing slow and refuses the links**.

**Who freezes it** (watched every 10 s, 2026-09-30 01:44–01:59): npm ran (`S`, CPU rising) while
iCode was in front; at 01:51:19 — iCode still in front, screen still awake, then dozing 30 s later
— npm, its shell, PRoot **and iCode's own process** were all `D` in `__refrigerator`. Android's
own freezer is off on this phone (`use_freezer=false`); the maker's is on: Transsion's **Fast
Freezer** (`mFastFreezerEnabled=true`, `FEATURE_FAST_FREEZER_SUPPORTED=true`, Infinix-X6881),
with a package whitelist iCode is not on and a `noFreezeModeAppList` that is empty. It freezes
iCode whatever its foreground service (`procState 4`) and battery exemption say. Not yet known:
what it spares — a held wake lock (iCode's notification already offers one per session), audio,
or only its lists — which W0 measures before anything is built on it.

## 2. Design — one rule: sources where the user wants them, everything a toolchain makes in fast storage

1. **Two places, the same words.** Open Folder's browser shows **iCode Projects** first, then
   **Internal storage** as today. New Folder and Clone create in iCode Projects by default. Recents
   read "iCode Projects/RevPlayer" or "Internal storage/Subtitles/RevPlayer"; everything else —
   Explorer, editor, Git, terminal at `/workspace/<name>` — is the same.
2. **Other apps still reach the files.** iCode publishes iCode Projects as a document provider: it
   appears as "iCode" in Android's Files app and in every app's Open/Save picker, as Termux's home
   does. (To check on the phone: which of the owner's apps — MT Manager included — browse through
   the picker rather than raw paths.)
3. **A folder on phone storage still opens in place**, as today, and its generated folders —
   `node_modules`, `.next`, `.venv`, `target`, `build`, `.gradle`, `.dart_tool`… (a list per
   ecosystem, editable per workspace) — are kept in iCode's storage and appear inside the project in
   the runtime. So `npm install` there runs at iCode-storage speed with its links, while the sources
   stay where other apps see them. What stays true of phone storage is said once, plainly, with the
   way out: scripts are not executable (`sh gradlew`, not `./gradlew`), names are not case-sensitive.
4. **Moving is one action**, both ways — "Move to iCode Projects", "Move to Internal storage" — run
   as a task (the progress dialog, Hide, the notification: [long-operations-plan](./long-operations-plan.md)).
   The terminal path `/workspace/<name>` does not change.
5. **Work goes on out of sight.** A command, an install, a build keeps running when iCode is not in
   front — the freeze above is found and fixed first; everything else rests on it.
6. **Nothing is lost by accident.** Android deletes an app's storage when it is uninstalled. iCode
   declares its data fragile (`android:hasFragileUserData`), so uninstalling asks whether to keep it;
   Settings shows where each project lives; a project with a Git remote is pushed-state aware; and
   "Move to Internal storage" is always there.

## 3. Steps (each: gate, commit; device where it touches a screen)

| Step | What |
|---|---|
| W0 | The freeze: observe who stops iCode's processes out of sight, fix it, prove a long command finishes with the screen off |
| W1 | iCode Projects: the place, the browser entry, New Folder and Clone into it; `hasFragileUserData` |
| W2 | The document provider: iCode Projects in the Files app and pickers; checked with the owner's apps |
| W3 | Generated folders of a phone-storage workspace kept in iCode's storage (per-ecosystem list, per-workspace setting), bound into the runtime |
| W4 | Move between the two places, as a task |
| W5 | Device: a clean `npm install` of RevPlayer to the end, in place and in iCode Projects; the same for a Python and a Gradle project |

## 4. For the owner

- New projects in iCode Projects by default (recommended), or asked each time?
- RevPlayer and the other existing projects: stay in place with fast generated folders (W3), or
  move into iCode Projects (W4)?
