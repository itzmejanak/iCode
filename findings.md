# iCode — Findings

Review date: 2026-10 (working tree on `main`, HEAD `c05f2eb`, clean status)
Prepared for: owner review
Method: static review of the source tree, the project's own guard scripts, file and
commit history, and a Gradle build attempt in this sandbox. **No unit test, lint, or
device run was possible here** (see F2). Each finding below cites its evidence so it can
be re-checked.

---

## 1. Summary

| Severity | Count | Meaning |
|---|---|---|
| High | 2 | Blocks release or verification |
| Medium | 2 | A check is failing or a claim is unverified |
| Low | 4 | Stale docs, leftover naming, known debt |

No confirmed functional bug was found by static reading. The cancellation paths, the
Git credential storage, the autosave-versus-assistant write guard and the cleartext
network policy all looked correct on the paths I read. Behaviour still needs a device
pass, which the project's own backlog already lists.

---

## 2. Findings

### F1 — Backend consistency guard fails (Medium)

- **Symptom:** `tools/check-backend-consistency.sh` exits 1:
  `new violation: backend-lines app/src/main/java/dev/icode/mobile/domain/ICodeIntent.kt (301)`
- **Cause:** The file is 301 lines against a 300-line budget. It was created at exactly
  300 lines in `d18d540`, then grew by 8 and 2 lines in `0742f3a` and `f536e23`
  (assistant commit-message and export intents).
- **Why it matters:** The guard is the project's anti-regression gate, and the Koog plan
  records it as passing. This commit broke that record. The rule is not on the allowlist,
  so the ratchet correctly rejects it.
- **Fix options:** Split `ICodeIntent` into one file per scope (assistant, git, workspace,
  runtime), as the rulebook suggests for scope-based contracts. Do not raise the
  allowlist count.

### F2 — The Gradle build cannot run in this sandbox; latest slices never compiled (High)

- **Symptom:** `./gradlew --offline :app:compileDebugKotlin` fails in
  `:app:processDebugResources` with `AAPT2 ... Daemon startup failed`.
- **Cause:** This runtime is aarch64. The AAPT2 that Gradle resolves
  (`aapt2-8.13.2-14304508-linux`) and the SDK's `build-tools/35.0.0/aapt2` are both
  **x86-64 ELF** binaries (`e_machine 0x3e`). The SDK copy also needs
  `/lib64/ld-linux-x86-64.so.2`. Neither can execute here.
- **Not a code defect**, but it has two consequences:
  1. Nothing compiled through the real Android toolchain in this review, so compilation,
     unit tests (`:app:testDebugUnitTest`), and lint were not verified.
  2. Per the project's own records, the workspace-storage (W1–W8), long-operations (B1–B6)
     and Koog assistant slices were checked against JVM jars and desktop Compose stand-ins,
     not the real Gradle build (`docs/assistant-koog-plan.md` §11). That is the largest
     verification gap in the project.
- **Action:** Run the full gate on a machine with an x86-64 SDK (or an arm64 AAPT2 build):
  `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`, both guards,
  then the capture script.

### F3 — No LICENSE for iCode itself (High, release blocker)

- `LICENSE*` does not exist at the repository root. `Todo.md` priority 4 already records
  this, and the GPL-2.0 PRoot and LGPL Sora obligations are also still open.
- This is known, not new. It is listed here so it is not lost. It needs an owner decision
  before any release.

### F4 — Documentation route count disagrees with the capture script (Low)

- `Todo.md` lines 26 and 51 say "39-route capture". `tools/capture-figma-screens.sh`
  line 36 sets `expected_routes=37`, with a comment explaining the 37 figma-node routes.
- `Route.kt` declares 52 routes: 37 with a design node, 10 with `null`, and 5 further
  entries with no design frame. The "40 frames / 39 screen files" statement in `Todo.md`
  line 20 was not checked against code.
- **Action:** Make `Todo.md` say 37 routes, and reconcile the frame and route counts in
  one place.

### F5 — The `workbench` rename is incomplete (Low)

- `docs/workbench-to-icode-rename-plan.md` (status: **plan**, dated 2026-10-06) asks for
  all "workbench" wording to be removed.
- `workbench` still appears as an identifier and in comments in 11 Kotlin files under
  `app/src`, for example `GitAuthCoordinator.kt` (`private val workbench: ICodeStateHolder`)
  and `CliGitService.kt:98`.
- `Todo.md` contains no "Workbench" text, so the docs side is done. The code side is not.

### F6 — Known ratchet debt on two oversized backend files (Low)

- `data/workspace/AndroidICodeGateway.kt` is 356 lines and `DocumentSessionCoordinator.kt`
  is 314 lines. Both are on the allowlist at their current size.
- The allowlist cannot grow, so this is not a regression. The gateway is the central
  dispatcher, which makes it the likeliest place for future merge conflicts.

---

## 3. Checked and found acceptable

| Area | What was checked | Result |
|---|---|---|
| Cancellation | Five `catch (Exception)` sites in suspend code (`GitTransferCoordinator` ×2, `GitWorkspaceCoordinator`, `WorkspaceImportCoordinator`, `LanguageServiceProcessManager`) | Each rethrows `CancellationException` first, or is non-suspending. No swallowed cancellation found |
| Concurrency | `GlobalScope`, `runBlocking` in `app/src/main` | None found |
| Placeholders | `TODO()`, `FIXME`, `HACK` in Kotlin source | None found |
| Hard-coded secrets | Literal key/token/password strings | Only the preference key name `"https.token"`. No values |
| Git token storage | `GitCredentialStore` | Token is encrypted with a Keystore cipher (`KeystoreCipher`) before it is written to preferences |
| Git credential helper file | `GitCredentialFile.create` | Uses `Files.createTempFile`, which is owner-only on POSIX. The helper file is removed after use per `docs/git-separation-plan.md` S5.8b. **Removal not re-verified here** |
| Assistant writes | `WorkspaceChangeTools` | Refuses to write a file with unsaved editor edits |
| Network policy | `network_security_config.xml` | Cleartext only for `localhost` and `127.0.0.1`. Everything else is HTTPS-only |
| Backup | `AndroidManifest` | `allowBackup="false"` |
| Exported components | Manifest | Only `MainActivity` and `WorkspacesProvider` are exported, both intentionally |
| UI guard | `tools/check-ui-consistency.sh` | **Passes** |
| Demo leftovers | `DemoICodeGateway`, `staticDemo`, `demo-fork`, "demo" in product code | None found |
| Test skips | `@Ignore` / `assume` | Only environment assumptions (git, bash, POSIX shell). No disabled tests |
| Build hygiene | `isMinifyEnabled`, `isShrinkResources`, keep rules | Configured for release |

---

## 4. Not verified (needs owner or device)

1. **Unit tests and lint** — not run (F2).
2. **Assistant default approval mode.** `docs/assistant-koog-plan.md` §10 says the default
   is "Ask before changes". I did not confirm the value in code, so this is unverified.
3. **The 7 non-null assertions (`!!`)** in `app/src/main` were counted but not reviewed.
4. **Device behaviour** — everything the `Todo.md` device backlog lists, including
   interrupted repair, session restore, two dirty tabs, and the editor and terminal
   acceptance checks.
5. **Language services.** The status message says "LSP capabilities not yet verified",
   which is accurate. There is no JSON-RPC client (`Todo.md` priority 8). This is a known
   gap, not a bug.

---

## 5. Recommended order

1. **F1** — split `ICodeIntent.kt` so the backend guard passes. Small, low risk.
2. **F2** — run the full Gradle gate on an x86-64 machine before anything else. Until then,
   treat the last three large slices as unbuilt.
3. **F4, F5** — fix the doc route count and finish the `workbench` identifier rename.
4. **F3** — owner decision on the licence and the GPL and LGPL notices.
5. **F6** — split `AndroidICodeGateway` only when next touched.

---

## 6. Open questions for the owner

- Should I split `ICodeIntent.kt` now (F1), or do you want that alongside the next feature?
- Is an x86-64 build machine available for F2? If not, I can prepare a list of what to run
  and what output to bring back.
- Confirm the assistant's default approval mode (item 4.2), so I can verify it in code.
