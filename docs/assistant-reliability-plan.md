# Assistant Reliability — Rate Limit & Connection (Architecture Fix)

Status: **plan → in progress (2026-10-14)**
Owner's ask: "focus on rate limit as well as on connection better behavior that exactly need to be for better user experience and also make sure this is architecture fix so don't just try to bend scope for some providers only"
Evidence: screenshots 10:51–10:53 — raw tool logs leaked to chat, red `The assistant stopped` card on transient `Token rate limit exceeded` and `Unable to reach radoxai.com`, manual `Try Again` at 221k→261k tokens, `Thinking...` with no retry feedback.

## 1. Goal (architecture, not provider)

One resilience path for **every** provider kind (OpenAI, Anthropic, Google, OpenRouter, Ollama, OpenAI-compatible). No `if (radox == ...)` branches. Classification by **HTTP status + exception type + message**, not by `AssistantProviderKind`.

```
Koog exception chain → KoogFailures (provider-agnostic) → AssistantRetryPolicy (pure, domain) → AssistantTurns (orchestrates, shows progress) → UI (one tone)
```

## 2. Problem now

- `AssistantTurns.Turn.run` catches `AssistantTurnException` and immediately sets `failure` → red `NoticeCard("The assistant stopped")`. No distinction transient vs permanent.
- `KoogFailures.explain` already distinguishes `429`, `401/403`, `UnknownHost`, `ConnectTimeout`, `IOException` + `COMPLETION_ENDED`, but caller never retries — user must tap `Try Again`.
- Screenshots: `Token rate limit exceeded` is transient 429 → should auto-retry, not stop. `Unable to reach radoxai.com` / `Unable to connect` is transient network → should retry with backoff, not fatal.

## 3. Classification (pure, testable)

**Transient → auto-retry (max 3)** — nothing provider-specific:

| Signal | Example | Why transient |
|---|---|---|
| `KoogHttpClientException.status == 429` or body contains `rate limit` / `too many requests` | `Token rate limit exceeded. Please slow down` | quota window |
| `status in 502, 503, 504` | overloaded gateway | usually recovers |
| `ConnectTimeoutException`, `HttpRequestTimeoutException`, `SocketTimeoutException` | `did not answer in time` | network hiccup |
| `ConnectException`, `UnknownHostException`, `IOException` with `fromServer` (KoogFailures) | `Unable to reach radoxai.com`, `Connection reset` | offline / flaky |
| `IncompleteStreamException` / `CONNECTION_ENDED` | stream broke mid-reply | retry the request whole (Koog already does for stream refusal; this is whole turn) |
| `SSLException` (once) | handshake blip | transient, retry once then fail |

**Permanent → fail fast, show action (no retry):**

`401/403` key, `404` no such model/API, `context length` exceeded, `content_filter`/`refusal`, `output limit`, `empty reply`, validation (`providerProblem`), `UnknownServiceException` (cleartext blocked) — all require user action.

Detection lives in `domain/AssistantRetryPolicy` + `KoogFailures.isTransient(throwable)` — both work on the **cause chain**, not on `connection.kind`.

## 4. Backoff & UX (per design: fixed 30s × 5 — simple and best)

- Attempts: **5 × 30s** — fixed `30s` each retry, interruptible by `Stop`. Countdown ticks every 1s: `retrying in 30s … 1s (1/5)`.
- Respect `Retry-After` header/body when present: `min(parsed, 180s)` otherwise `30s`.
- While retrying: `AssistantTurn.step` updates each second via `AssistantRetries.countdown`. Existing `IndeterminateProgressTrack` keeps moving.
- After 5 fails: soft failure card `IcodeTone.Warning` (amber) for rate-limit, `IcodeTone.Error` (red) for permanent, with `Try Again` + `Open Assistant settings` where relevant. Conversation stays resumable (`RetryAssistantMessage`).

Ask approvals never auto-retry — `CancellationException` and `Deny` bypass retry.

## 5. Changes (small, scoped)

1. **New** `domain/AssistantRetryPolicy.kt` — pure: `isTransient(failure)`, `kind(failure) → RateLimited | Connection | Server`, `delayMillis(attempt, failure)`, `MAX_RETRIES = 3`.
2. **Extend** `data/assistant/koog/KoogFailures.kt` — expose `isTransient`, `retryAfterMillis(body)`, keep `explain` as single message source (no forked strings).
3. **Modify** `data/assistant/AssistantTurns.kt` — `Turn.talk()`/`Turn.compact()` wrap `engine.run` in `withRetry { }` loop, update `page.state { turn.step }`, `delay`, honor `job.isCancelled`.
4. **Tweak** `ui/screens/tools/AssistantChatView.kt` — failure `NoticeCard` tone by kind (warning vs error), keep `Try Again` secondary.
5. Tests: `domain/AssistantRetryPolicyTest`, `KoogFailuresTest` for 429 parsing + connection mapping; `AssistantTurnsTest` fake-engine that throws transient twice then succeeds → verifies 2 retries then success, and non-retry on 401.

## 6. What is NOT in scope (keep architecture clean)

- No per-provider `if` for radoxai/openrouter — they flow through the same status path.
- No background queue / WorkManager — retry lives in the turn's job, kept by `BackgroundWork`/`RuntimeForegroundService` already.
- No token-bucket limiter — server's 429 is source of truth; client-side throttle is follow-up.

## 7. Validation

- `tools/check-backend-consistency.sh` + `tools/check-ui-consistency.sh` pass (new file ≤250 lines).
- Unit: 429 body variants (`Token rate limit exceeded`, `rate_limit`, `429`), 502/503, timeout chain, `UnknownHost` → transient; `401`, `context length` → not transient.
- Manual device: airplane on → `Connection lost — retrying...` 3× then amber card; rapid 4 sends → rate-limit retry line, no manual tap needed.

## 8. Rollout

Commit 1: domain policy + `KoogFailures` (no behavior change) + tests.
Commit 2: `AssistantTurns` retry loop + UI tone (behavior change) + tests.
Both behind no feature flag — architecture fix, not experiment.

