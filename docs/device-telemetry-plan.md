# Device info and the iCode website — plan

Owner's request (2026-10-29): after onboarding, and only with the user's consent, iCode sends
anonymous device info to the iCode website so the owner sees real-time usage. The same change
wires Settings › Application (About iCode) to the website's links and update check.

This is the "future telemetry" the PRD (§ privacy) and architecture.md (§ telemetry) required to be
documented on its own, separable from core operation, and free of workspace content.

## 1. Rules

1. **Opt-in only.** Nothing is sent until the user taps *Share Device Info*. *Not Now* is offered
   first. No answer = no data.
2. **Never on the editing path.** Every call is best-effort, off the main thread, with a 10 s timeout.
   Offline, or with the site down, iCode behaves exactly as before (US-WEB-003).
3. **No workspace content, ever** (§3).
4. **Reversible.** Settings › Application › *Share Device Info* turns it off at any time; turning it
   off sends `DELETE /devices/{installId}` and forgets the ID.
5. **Server kill switch.** The website's App config can pause collection (`telemetry.enabled`); the
   app then stops scheduling its heartbeat.
6. **Builds without a website address send nothing.** `BuildConfig.ICODE_WEB_URL` is empty unless the
   build passes `-PicodeWebUrl=https://…`.

## 2. Consent text

Onboarding's last step, `Route.DeviceInfoConsent`, after the runtime, Set Up Tools and permissions:

- Title: *Help improve iCode*
- Body: *Share anonymous device info so we know which phones and Android versions iCode runs on, and
  how many people use it. iCode works the same either way.*
- Points: what is shared, what is never shared, the random ID, how to change it.
- Buttons: *Not Now* · *Share Device Info*

The text lives in `ui/screens/entry/DeviceInfoConsentScreen.kt`; keep it in step with §3.

## 3. Data

| Sent | Source | Why |
|---|---|---|
| `installId` | random UUID v4 made on opt-in | count installs without identifying anyone |
| `appVersionName`, `appVersionCode` | BuildConfig | which builds are in use, update reach |
| `androidRelease`, `sdkInt` | `Build.VERSION` | which Android versions to support |
| `manufacturer`, `model` | `Build` | device compatibility |
| `abi` | `Build.SUPPORTED_ABIS[0]` | runtime architecture |
| `locale` | `Locale.getDefault()` | languages to support |
| `runtimeInstalled` | `RuntimeLayout.isInstalled` | how many finish setup |

**Never sent:** workspace names, paths or files; Git remotes, branches, credentials or keys; terminal
input/output; assistant prompts, answers or provider keys; Android ID, IMEI, advertising ID, accounts,
phone number, IP-derived location (the server does not store IPs).

## 4. Flow

```text
onboarding ── DeviceInfoConsent ── Not Now ──▶ consent = NotShared, nothing sent
                    │
                    └─ Share ──▶ consent = Shared, installId = UUID
                                 POST /devices/register
                                 WorkManager periodic heartbeat (heartbeatMinutes, ≥ 15, network-constrained)
app comes to foreground ──▶ POST /devices/heartbeat     (so the admin's "active now" is current)
heartbeat answers 404    ──▶ POST /devices/register again
facts change (update, runtime installed) ──▶ register again (fingerprint differs)
Settings toggle off      ──▶ DELETE /devices/{installId}, forget ID, cancel heartbeat
```

The website counts a device "active now" when its last heartbeat is under 20 minutes old.

## 5. Settings › Application (About iCode)

| Row | Before | Now |
|---|---|---|
| Check for Updates | Not available yet | `GET /app/releases/latest`; shows up to date / available / required; tap opens the download |
| Export Diagnostics | Not available yet | unchanged (local-only; needs its own plan) |
| View Licenses, View Source | Not available yet | links from App config; "Not available yet" while unset |
| Documentation, Release Notes, Report Issue | Not available yet | links from App config |
| Privacy (new) | — | *Share Device Info* toggle; Privacy Policy link when set |

Links are cached in `icode_web` SharedPreferences, so the rows work offline after one successful read.

## 6. Code

```text
domain/WebContract.kt            DeviceInfoConsent, AppLinks, UpdateCheck, WebState, WebIntent
data/web/ICodeWebClient.kt       HTTP (HttpURLConnection + org.json), contracts/api-v1.md
data/web/WebJson.kt              JSON ⇄ types, the only place field names live
data/web/WebPreferences.kt       consent, installId, cached config
data/web/DeviceFacts.kt          §3, read once per report
data/web/DeviceInfoReporter.kt   register/heartbeat/forget + WorkManager scheduling + worker
data/web/WebCoordinator.kt       the gateway's part: WebIntent → state
navigation/OnboardingRoute.kt    home(deviceInfoPending): consent step before the launcher
ui/screens/entry/DeviceInfoConsentScreen.kt
ui/screens/settings/AboutScreen.kt
```

Screens use only `ICodeGateway` (`WebIntent`, `snapshot.web`), as every other feature.
New dependency: `androidx.work:work-runtime-ktx`.

## 7. Verification (not yet run — owner tests on device)

- `./gradlew test` — `OnboardingRouteTest`, `RouteTest` updated.
- Fresh install: consent shown once, last; *Not Now* → nothing in the website's Devices page.
- *Share* → device appears within seconds; "active now" ≥ 1; background for 20+ min → drops off,
  reopen → returns.
- Toggle off in Settings → device row gone from the website.
- Airplane mode: onboarding, About and everything else unchanged.
- Build without `-PicodeWebUrl`: no network calls.
